package com.wuxianggujun.tinaide.ui.compose.viewer

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Test

class HexBinaryAnalysisTest {

    @Test
    fun detectFileKind_shouldRecognizeCommonBinaryFormats() {
        assertThat(detectFileKind(File("demo.so"), byteArrayOf(0x7F, 0x45, 0x4C, 0x46)))
            .isEqualTo(HexFileKind.ELF)
        assertThat(detectFileKind(File("classes.dex"), "dex\n035\u0000".toByteArray()))
            .isEqualTo(HexFileKind.DEX)
        assertThat(detectFileKind(File("demo.apk"), byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
            .isEqualTo(HexFileKind.APK)
        assertThat(detectFileKind(File("demo.zip"), byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
            .isEqualTo(HexFileKind.ZIP)
        assertThat(detectFileKind(File("demo.png"), byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
            .isEqualTo(HexFileKind.PNG)
    }

    @Test
    fun analyzeHexBinaryFile_shouldCalculateFileFingerprint() {
        runBlocking {
            val file = tempBinaryFile("fingerprint.bin", "abc".toByteArray())

            val fingerprint = analyzeHexBinaryFile(file).fingerprint!!

            assertThat(fingerprint.sha256)
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
            assertThat(fingerprint.sha1).isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d")
            assertThat(fingerprint.md5).isEqualTo("900150983cd24fb0d6963f7d28e17f72")
            assertThat(fingerprint.crc32).isEqualTo(0x352441C2L)
            assertThat(fingerprint.byteCount).isEqualTo(3L)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldCalculateByteFrequencySummary() {
        runBlocking {
            val bytes = byteArrayOf(
                0x00,
                0x00,
                0x20,
                0x41,
                0x41,
                0x7F,
                0xFF.toByte(),
                0xFF.toByte()
            )
            val file = tempBinaryFile("frequency.bin", bytes)

            val frequency = analyzeHexBinaryFile(file).byteFrequency!!

            assertThat(frequency.totalBytes).isEqualTo(8L)
            assertThat(frequency.uniqueByteValues).isEqualTo(5)
            assertThat(frequency.zeroBytes).isEqualTo(2L)
            assertThat(frequency.ffBytes).isEqualTo(2L)
            assertThat(frequency.printableAsciiBytes).isEqualTo(3L)
            assertThat(frequency.controlBytes).isEqualTo(3L)
            assertThat(frequency.topBytes.take(5).map { it.byteValue })
                .containsExactly(0x00, 0x41, 0xFF, 0x20, 0x7F)
                .inOrder()
            assertThat(frequency.topBytes.first().count).isEqualTo(2L)
            assertThat(frequency.topBytes.first().ratio).isWithin(0.0001).of(0.25)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldTrackRepeatedByteRunsAcrossBuffers() {
        runBlocking {
            val prefixLength = 64 * 1024 - 8
            val prefix = ByteArray(prefixLength) { index ->
                if (index % 2 == 0) 0x01 else 0x02
            }
            val crossingRun = ByteArray(24) { 0xAA.toByte() }
            val tailRun = ByteArray(18) { 0x00 }
            val shortRun = ByteArray(15) { 0x7F }
            val file = tempBinaryFile("runs.bin", prefix + crossingRun + tailRun + shortRun)

            val runs = analyzeHexBinaryFile(file).repeatedByteRuns

            assertThat(runs)
                .containsExactly(
                    HexRepeatedByteRun(
                        byteValue = 0xAA,
                        startOffset = prefixLength.toLong(),
                        length = 24L
                    ),
                    HexRepeatedByteRun(
                        byteValue = 0x00,
                        startOffset = prefixLength + crossingRun.size.toLong(),
                        length = 18L
                    )
                )
                .inOrder()
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldDetectMagicSignaturesAcrossBuffers() {
        runBlocking {
            val prefixLength = 64 * 1024 - 2
            val prefix = ByteArray(prefixLength) { index ->
                if (index % 2 == 0) 0x33 else 0x44
            }
            val bytes = prefix +
                byteArrayOf(0x7F, 0x45, 0x4C, 0x46) +
                byteArrayOf(0x00, 0x11) +
                "dex\n035\u0000".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(0x50, 0x4B, 0x03, 0x04) +
                "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
            val file = tempBinaryFile("magic-signatures.bin", bytes)

            val matches = analyzeHexBinaryFile(file).magicSignatures

            assertThat(matches)
                .containsExactly(
                    HexMagicSignatureMatch(
                        kind = HexMagicSignatureKind.ELF,
                        offset = prefixLength.toLong(),
                        signatureLength = 4
                    ),
                    HexMagicSignatureMatch(
                        kind = HexMagicSignatureKind.DEX,
                        offset = prefixLength.toLong() + 6L,
                        signatureLength = 4
                    ),
                    HexMagicSignatureMatch(
                        kind = HexMagicSignatureKind.ZIP_LOCAL_FILE,
                        offset = prefixLength.toLong() + 14L,
                        signatureLength = 4
                    ),
                    HexMagicSignatureMatch(
                        kind = HexMagicSignatureKind.SQLITE,
                        offset = prefixLength.toLong() + 18L,
                        signatureLength = 16
                    )
                )
                .inOrder()
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseDexHeaderStringsMapAndSignals() {
        runBlocking {
            val file = tempBinaryFile("classes.dex", minimalDex())

            val analysis = analyzeHexBinaryFile(file)
            val dex = analysis.dex!!

            assertThat(analysis.fileKind).isEqualTo(HexFileKind.DEX)
            assertThat(dex.version).isEqualTo("035")
            assertThat(dex.fileSizeFromHeader).isEqualTo(0x240L)
            assertThat(dex.headerSize).isEqualTo(0x70L)
            assertThat(dex.stringIdsSize).isEqualTo(5)
            assertThat(dex.typeIdsSize).isEqualTo(2)
            assertThat(dex.protoIdsSize).isEqualTo(1)
            assertThat(dex.fieldIdsSize).isEqualTo(1)
            assertThat(dex.methodIdsSize).isEqualTo(1)
            assertThat(dex.classDefsSize).isEqualTo(1)
            assertThat(dex.nativeMethodCount).isEqualTo(0)
            assertThat(dex.stringEntries.map { it.value })
                .containsExactly("Lcom/example/MainActivity;", "nativeEntry", "II", "counter", "I")
                .inOrder()
            assertThat(dex.typeEntries)
                .containsExactly(
                    HexDexTypeEntry(
                        index = 0,
                        typeIdOffset = 0x84L,
                        descriptorStringIndex = 0L,
                        descriptor = "Lcom/example/MainActivity;"
                    ),
                    HexDexTypeEntry(
                        index = 1,
                        typeIdOffset = 0x88L,
                        descriptorStringIndex = 4L,
                        descriptor = "I"
                    )
                )
                .inOrder()
            assertThat(dex.protoEntries)
                .containsExactly(
                    HexDexProtoEntry(
                        index = 0,
                        protoIdOffset = 0x8CL,
                        shortyStringIndex = 2L,
                        shorty = "II",
                        returnTypeIndex = 1L,
                        returnTypeDescriptor = "I",
                        parametersOffset = 0x200L,
                        parameterTypeDescriptors = listOf("I"),
                        signature = "(I)I"
                    )
                )
            assertThat(dex.fieldEntries)
                .containsExactly(
                    HexDexFieldEntry(
                        index = 0,
                        fieldIdOffset = 0x98L,
                        classIndex = 0,
                        classDescriptor = "Lcom/example/MainActivity;",
                        typeIndex = 1,
                        typeDescriptor = "I",
                        nameStringIndex = 3L,
                        name = "counter"
                    )
                )
            assertThat(dex.methodEntries)
                .containsExactly(
                    HexDexMethodEntry(
                        index = 0,
                        methodIdOffset = 0xA0L,
                        classIndex = 0,
                        classDescriptor = "Lcom/example/MainActivity;",
                        protoIndex = 0,
                        protoShorty = "II",
                        protoSignature = "(I)I",
                        nameStringIndex = 1L,
                        name = "nativeEntry"
                    )
                )
            assertThat(dex.classDefEntries.single()).isEqualTo(
                HexDexClassDefEntry(
                    index = 0,
                    classDefOffset = 0xA8L,
                    classIndex = 0L,
                    classDescriptor = "Lcom/example/MainActivity;",
                    accessFlags = 1L,
                    superclassIndex = null,
                    superclassDescriptor = null,
                    interfacesOffset = 0L,
                    sourceFileIndex = null,
                    sourceFile = null,
                    annotationsOffset = 0L,
                    classDataOffset = 0x208L,
                    staticValuesOffset = 0L
                )
            )
            assertThat(dex.classDataMethodEntries)
                .containsExactly(
                    HexDexClassDataMethodEntry(
                        index = 0,
                        classDefIndex = 0,
                        classDescriptor = "Lcom/example/MainActivity;",
                        kind = HexDexClassDataMethodKind.DIRECT,
                        methodIndex = 0L,
                        methodName = "nativeEntry",
                        methodClassDescriptor = "Lcom/example/MainActivity;",
                        protoSignature = "(I)I",
                        accessFlags = 1L,
                        classDataOffset = 0x208L,
                        entryOffset = 0x20CL,
                        codeOffset = 0x218L,
                        executionKind = HexDexClassDataMethodExecutionKind.CODE
                    )
                )
            assertThat(dex.codeItemEntries)
                .containsExactly(
                    HexDexCodeItemEntry(
                        index = 0,
                        methodIndex = 0L,
                        methodName = "nativeEntry",
                        methodClassDescriptor = "Lcom/example/MainActivity;",
                        protoSignature = "(I)I",
                        codeOffset = 0x218L,
                        registersSize = 2,
                        insSize = 1,
                        outsSize = 1,
                        triesSize = 0,
                        debugInfoOffset = 0L,
                        insnsSize = 1L,
                        firstOpcode = 0x0F,
                        firstOpcodeName = "return",
                        previewCodeUnitsHex = "000F"
                    )
                )
            assertThat(dex.mapEntries.map { it.typeName })
                .containsAtLeast("string_id_item", "class_data_item", "map_list", "code_item")
            assertThat(analysis.signals.map { it.type })
                .containsAtLeast(
                    HexAnalysisSignalType.DEX_FILE,
                    HexAnalysisSignalType.DEX_HEADER,
                    HexAnalysisSignalType.DEX_TYPE_IDS,
                    HexAnalysisSignalType.DEX_PROTO_IDS,
                    HexAnalysisSignalType.DEX_FIELD_IDS,
                    HexAnalysisSignalType.DEX_METHOD_IDS,
                    HexAnalysisSignalType.DEX_CLASS_DEFS,
                    HexAnalysisSignalType.DEX_CLASS_DATA,
                    HexAnalysisSignalType.DEX_CODE_ITEMS,
                    HexAnalysisSignalType.DEX_MAP_LIST
                )
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldClassifyDexNativeClassDataMethods() {
        runBlocking {
            val file = tempBinaryFile("classes.dex", minimalDexWithNativeMethod())

            val analysis = analyzeHexBinaryFile(file)
            val dex = analysis.dex!!
            val nativeMethod = dex.classDataMethodEntries.single()

            assertThat(dex.nativeMethodCount).isEqualTo(1)
            assertThat(nativeMethod.methodName).isEqualTo("nativeEntry")
            assertThat(nativeMethod.accessFlags).isEqualTo(0x101L)
            assertThat(nativeMethod.codeOffset).isEqualTo(0L)
            assertThat(nativeMethod.executionKind).isEqualTo(HexDexClassDataMethodExecutionKind.NATIVE)
            assertThat(dex.codeItemEntries).isEmpty()
            assertThat(filterDexClassDataMethodEntries(dex.classDataMethodEntries, query = "acc_native"))
                .containsExactly(nativeMethod)
            assertThat(filterDexClassDataMethodEntries(dex.classDataMethodEntries, query = "jni"))
                .containsExactly(nativeMethod)
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.DEX_NATIVE_METHODS)
            assertThat(analysis.signals.first { it.type == HexAnalysisSignalType.DEX_NATIVE_METHODS }.offset)
                .isEqualTo(nativeMethod.entryOffset)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseDexInvokeCallReferences() {
        runBlocking {
            val file = tempBinaryFile("classes.dex", minimalDexWithInvokeReference())

            val analysis = analyzeHexBinaryFile(file)
            val dex = analysis.dex!!

            assertThat(dex.codeItemEntries.single().firstOpcode).isEqualTo(0x71)
            assertThat(dex.callReferenceEntries)
                .containsExactly(
                    HexDexCallReferenceEntry(
                        index = 0,
                        callerMethodIndex = 0L,
                        callerClassDescriptor = "Lcom/example/MainActivity;",
                        callerMethodName = "nativeEntry",
                        callerProtoSignature = "(I)I",
                        targetMethodIndex = 0L,
                        targetClassDescriptor = "Lcom/example/MainActivity;",
                        targetMethodName = "nativeEntry",
                        targetProtoSignature = "(I)I",
                        opcode = 0x71,
                        opcodeName = "invoke",
                        instructionOffset = 0x228L,
                        codeOffset = 0x218L,
                        targetMethodIdOffset = 0xA0L
                    )
                )
            assertThat(analysis.signals.map { it.type })
                .contains(HexAnalysisSignalType.DEX_CALL_REFERENCES)
            assertThat(analysis.signals.first { it.type == HexAnalysisSignalType.DEX_CALL_REFERENCES }.offset)
                .isEqualTo(0x228L)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseDexStringAndFieldReferences() {
        runBlocking {
            val file = tempBinaryFile("classes.dex", minimalDexWithDataReferences())

            val analysis = analyzeHexBinaryFile(file)
            val dex = analysis.dex!!

            assertThat(dex.codeItemEntries.single().firstOpcode).isEqualTo(0x1A)
            assertThat(dex.stringReferenceEntries)
                .containsExactly(
                    HexDexStringReferenceEntry(
                        index = 0,
                        callerMethodIndex = 0L,
                        callerClassDescriptor = "Lcom/example/MainActivity;",
                        callerMethodName = "nativeEntry",
                        callerProtoSignature = "(I)I",
                        stringIndex = 3L,
                        value = "counter",
                        opcode = 0x1A,
                        opcodeName = "const-string",
                        instructionOffset = 0x228L,
                        codeOffset = 0x218L,
                        stringIdOffset = 0x7CL,
                        stringDataOffset = 0x158L
                    )
                )
            assertThat(dex.fieldReferenceEntries)
                .containsExactly(
                    HexDexFieldReferenceEntry(
                        index = 0,
                        callerMethodIndex = 0L,
                        callerClassDescriptor = "Lcom/example/MainActivity;",
                        callerMethodName = "nativeEntry",
                        callerProtoSignature = "(I)I",
                        fieldIndex = 0L,
                        fieldClassDescriptor = "Lcom/example/MainActivity;",
                        fieldName = "counter",
                        fieldTypeDescriptor = "I",
                        opcode = 0x60,
                        opcodeName = "staticop",
                        instructionOffset = 0x22CL,
                        codeOffset = 0x218L,
                        fieldIdOffset = 0x98L
                    )
                )
            assertThat(analysis.signals.map { it.type })
                .containsAtLeast(
                    HexAnalysisSignalType.DEX_STRING_REFERENCES,
                    HexAnalysisSignalType.DEX_FIELD_REFERENCES
                )
            assertThat(analysis.signals.first { it.type == HexAnalysisSignalType.DEX_STRING_REFERENCES }.offset)
                .isEqualTo(0x228L)
            assertThat(analysis.signals.first { it.type == HexAnalysisSignalType.DEX_FIELD_REFERENCES }.offset)
                .isEqualTo(0x22CL)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseApkArchiveEntriesAndSignals() {
        runBlocking {
            val nativeLibraryBytes = minimalArchiveNativeLibrary()
            val apkBytes = minimalApk(nativeLibraryBytes)
            val file = tempBinaryFile("demo.apk", apkBytes)

            val analysis = analyzeHexBinaryFile(file)
            val archive = analysis.archive!!

            assertThat(analysis.fileKind).isEqualTo(HexFileKind.APK)
            val manifestBytes = minimalBinaryAndroidManifest()
            val manifest = archive.manifest!!
            assertThat(manifest.name).isEqualTo("AndroidManifest.xml")
            assertThat(manifest.crc32).isEqualTo(crc32Of(manifestBytes))
            assertThat(manifest.dataOffset).isEqualTo(apkBytes.zipLocalDataOffset(manifest.localHeaderOffset))
            assertThat(manifest.dataEndOffset).isEqualTo(manifest.dataOffset!! + manifest.compressedSize)
            assertThat(manifest.dataRangeStatus).isEqualTo(HexArchiveEntryDataRangeStatus.OK)
            assertThat(manifest.localHeaderName).isEqualTo(manifest.name)
            assertThat(manifest.localHeaderCompressionMethod).isEqualTo(manifest.compressionMethod)
            assertThat(manifest.localHeaderGeneralPurposeBitFlag).isEqualTo(manifest.generalPurposeBitFlag)
            assertThat(manifest.localHeaderConsistency).isEqualTo(HexArchiveEntryLocalHeaderConsistency.OK)
            assertThat(manifest.usesDataDescriptor).isTrue()
            val manifestSummary = archive.manifestSummary!!
            assertThat(manifestSummary.entryName).isEqualTo("AndroidManifest.xml")
            assertThat(manifestSummary.rootElementName).isEqualTo("manifest")
            assertThat(manifestSummary.packageName).isEqualTo("com.example.demo")
            assertThat(manifestSummary.permissions).containsExactly("android.permission.INTERNET")
            assertThat(manifestSummary.stringCount).isEqualTo(6)
            assertThat(manifestSummary.elementCount).isEqualTo(2)
            assertThat(manifestSummary.localHeaderOffset).isEqualTo(manifest.localHeaderOffset)
            assertThat(archive.dexFiles.map { it.name }).containsExactly("classes.dex")
            assertThat(archive.embeddedDexFiles.map { it.entryName }).containsExactly("classes.dex")
            assertThat(archive.embeddedDexFiles.single().dex.methodIdsSize).isEqualTo(1)
            assertThat(archive.embeddedDexFiles.single().dex.classDefsSize).isEqualTo(1)
            assertThat(archive.embeddedDexFiles.single().dex.stringEntries.map { it.value })
                .contains("Lcom/example/MainActivity;")
            assertThat(archive.embeddedDexFiles.single().dex.protoEntries.single().signature)
                .isEqualTo("(I)I")
            assertThat(archive.embeddedDexFiles.single().dex.fieldEntries.single().name)
                .isEqualTo("counter")
            assertThat(archive.embeddedDexFiles.single().dex.methodEntries.single().name)
                .isEqualTo("nativeEntry")
            assertThat(archive.embeddedDexFiles.single().dex.classDefEntries.single().classDescriptor)
                .isEqualTo("Lcom/example/MainActivity;")
            assertThat(archive.embeddedDexFiles.single().dex.classDataMethodEntries.single().codeOffset)
                .isEqualTo(0x218L)
            assertThat(archive.embeddedDexFiles.single().dex.codeItemEntries.single().firstOpcodeName)
                .isEqualTo("return")
            assertThat(archive.nativeLibraries.map { it.name }).containsExactly("lib/arm64-v8a/libdemo.so")
            val nativeSummary = archive.nativeLibrarySummaries.single()
            assertThat(nativeSummary.entryName).isEqualTo("lib/arm64-v8a/libdemo.so")
            assertThat(nativeSummary.abi).isEqualTo("arm64-v8a")
            assertThat(nativeSummary.fileName).isEqualTo("libdemo.so")
            assertThat(nativeSummary.localHeaderOffset).isEqualTo(archive.nativeLibraries.single().localHeaderOffset)
            assertThat(nativeSummary.dataOffset).isEqualTo(archive.nativeLibraries.single().dataOffset)
            assertThat(nativeSummary.compressionMethod).isEqualTo(ZipEntry.DEFLATED)
            assertThat(nativeSummary.loadMode).isEqualTo(HexArchiveNativeLoadMode.NEEDS_DECOMPRESSION)
            assertThat(nativeSummary.pageAlignmentRemainder)
                .isEqualTo(archiveNativePageAlignmentRemainder(nativeSummary.dataOffset))
            assertThat(nativeSummary.uncompressedSize).isEqualTo(nativeLibraryBytes.size.toLong())
            assertThat(nativeSummary.analyzedBytes).isEqualTo(nativeLibraryBytes.size.toLong())
            assertThat(nativeSummary.truncated).isFalse()
            assertThat(nativeSummary.isElf).isTrue()
            assertThat(nativeSummary.is64Bit).isTrue()
            assertThat(nativeSummary.endian).isEqualTo(HexEndian.LITTLE)
            assertThat(nativeSummary.machineName).isEqualTo("AArch64")
            assertThat(nativeSummary.obfuscationMarkers.map { it.type })
                .containsAtLeast(
                    HexObfuscationFindingType.OLLVM_MARKER,
                    HexObfuscationFindingType.CONTROL_FLOW_FLATTENING_MARKER,
                    HexObfuscationFindingType.BOGUS_CONTROL_FLOW_MARKER,
                    HexObfuscationFindingType.INSTRUCTION_SUBSTITUTION_MARKER,
                    HexObfuscationFindingType.PROTECTOR_PACKER_MARKER
                )
            assertThat(nativeSummary.obfuscationMarkers.first().relativeOffset).isNotNull()
            assertThat(archive.resources.map { it.name }).containsAtLeast("resources.arsc", "res/raw/payload.bin")
            val resourcesSummary = archive.resourcesSummary!!
            assertThat(resourcesSummary.entryName).isEqualTo("resources.arsc")
            assertThat(resourcesSummary.packageCountFromHeader).isEqualTo(1)
            assertThat(resourcesSummary.globalStringCount).isEqualTo(1)
            assertThat(resourcesSummary.typeSpecCount).isEqualTo(1)
            assertThat(resourcesSummary.typeChunkCount).isEqualTo(1)
            val resourcePackage = resourcesSummary.packages.single()
            assertThat(resourcePackage.id).isEqualTo(0x7F)
            assertThat(resourcePackage.name).isEqualTo("com.example.demo")
            assertThat(resourcePackage.typeStringCount).isEqualTo(1)
            assertThat(resourcePackage.keyStringCount).isEqualTo(1)
            assertThat(resourcePackage.typeSpecCount).isEqualTo(1)
            assertThat(resourcePackage.typeChunkCount).isEqualTo(1)
            assertThat(archive.signatureFiles.map { it.name }).contains("META-INF/CERT.RSA")
            assertThat(archive.entries.first().localHeaderOffset).isEqualTo(0L)
            archive.entries.forEach { entry ->
                assertThat(entry.dataOffset).isEqualTo(apkBytes.zipLocalDataOffset(entry.localHeaderOffset))
                assertThat(entry.dataEndOffset).isEqualTo(entry.dataOffset!! + entry.compressedSize)
                assertThat(entry.dataRangeStatus).isEqualTo(HexArchiveEntryDataRangeStatus.OK)
                assertThat(entry.localHeaderName).isEqualTo(entry.name)
                assertThat(entry.localHeaderCompressionMethod).isEqualTo(entry.compressionMethod)
                assertThat(entry.localHeaderGeneralPurposeBitFlag).isEqualTo(entry.generalPurposeBitFlag)
                assertThat(entry.localHeaderConsistency).isEqualTo(HexArchiveEntryLocalHeaderConsistency.OK)
                assertThat(entry.nameRisks).isEmpty()
            }
            val zipStructure = archive.zipStructure!!
            assertThat(zipStructure.entryCount).isEqualTo(6)
            assertThat(zipStructure.centralDirectoryOffset).isEqualTo(archive.entries.first().centralDirectoryOffset)
            assertThat(zipStructure.centralDirectorySize)
                .isEqualTo(zipStructure.eocdOffset - zipStructure.centralDirectoryOffset)
            assertThat(zipStructure.commentLength).isEqualTo(0)
            assertThat(zipStructure.zip64LocatorOffset).isNull()
            val signingBlock = archive.signingBlockEntries.single()
            assertThat(signingBlock.id).isEqualTo(0x7109871AL)
            assertThat(signingBlock.idName).isEqualTo("APK Signature Scheme v2")
            assertThat(signingBlock.valueSize).isEqualTo(4L)
            assertThat(signingBlock.blockSize).isEqualTo(48L)
            assertThat(signingBlock.blockOffset).isEqualTo(zipStructure.centralDirectoryOffset - signingBlock.blockSize)
            assertThat(signingBlock.pairOffset).isEqualTo(signingBlock.blockOffset + 8L)
            assertThat(signingBlock.valueOffset).isEqualTo(signingBlock.blockOffset + 20L)
            assertThat(analysis.signals.map { it.type })
                .containsAtLeast(
                    HexAnalysisSignalType.APK_FILE,
                    HexAnalysisSignalType.APK_MANIFEST,
                    HexAnalysisSignalType.APK_DEX_FILES,
                    HexAnalysisSignalType.APK_EMBEDDED_DEX_SUMMARIES,
                    HexAnalysisSignalType.APK_NATIVE_LIBRARIES,
                    HexAnalysisSignalType.APK_ZIP_STRUCTURE,
                    HexAnalysisSignalType.APK_SIGNING_BLOCK
                )
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldReportArchiveLocalHeaderNameMismatch() {
        runBlocking {
            val apkBytes = minimalApk().replaceCentralDirectoryEntryName(
                from = "classes.dex",
                to = "classed.dex"
            )
            val file = tempBinaryFile("demo.apk", apkBytes)

            val archive = analyzeHexBinaryFile(file).archive!!
            val mismatchedEntry = archive.entries.first { entry -> entry.name == "classed.dex" }

            assertThat(mismatchedEntry.localHeaderName).isEqualTo("classes.dex")
            assertThat(mismatchedEntry.localHeaderConsistency)
                .isEqualTo(HexArchiveEntryLocalHeaderConsistency.NAME_MISMATCH)
            assertThat(filterArchiveEntries(archive.entries, query = "local mismatch"))
                .contains(mismatchedEntry)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldReportArchivePathTraversalNameRisk() {
        runBlocking {
            val apkBytes = minimalApk().replaceCentralDirectoryEntryName(
                from = "classes.dex",
                to = "../evil.dex"
            )
            val file = tempBinaryFile("demo.apk", apkBytes)

            val archive = analyzeHexBinaryFile(file).archive!!
            val riskyEntry = archive.entries.first { entry -> entry.name == "../evil.dex" }

            assertThat(riskyEntry.nameRisks).contains(HexArchiveEntryNameRisk.PATH_TRAVERSAL)
            assertThat(filterArchiveEntries(archive.entries, query = "path traversal"))
                .containsExactly(riskyEntry)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldReportArchiveDuplicateNameRisk() {
        runBlocking {
            val apkBytes = minimalApk().replaceCentralDirectoryEntryName(
                from = "res/raw/payload.bin",
                to = "AndroidManifest.xml"
            )
            val file = tempBinaryFile("demo.apk", apkBytes)

            val archive = analyzeHexBinaryFile(file).archive!!
            val duplicateEntries = archive.entries.filter { entry -> entry.name == "AndroidManifest.xml" }

            assertThat(duplicateEntries).hasSize(2)
            duplicateEntries.forEach { entry ->
                assertThat(entry.nameRisks).contains(HexArchiveEntryNameRisk.DUPLICATE_NAME)
            }
            assertThat(filterArchiveEntries(archive.entries, query = "duplicate name"))
                .containsExactlyElementsIn(duplicateEntries)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldClassifyStoredPageAlignedNativeLibrariesAsMmapReady() {
        runBlocking {
            val nativeLibraryBytes = minimalArchiveNativeLibrary()
            val apkBytes = minimalApkWithStoredAlignedNativeLibrary(nativeLibraryBytes)
            val file = tempBinaryFile("aligned-native.apk", apkBytes)

            val archive = analyzeHexBinaryFile(file).archive!!
            val nativeSummary = archive.nativeLibrarySummaries.single()

            assertThat(nativeSummary.entryName).isEqualTo("lib/arm64-v8a/libaligned.so")
            assertThat(nativeSummary.compressionMethod).isEqualTo(ZipEntry.STORED)
            assertThat(nativeSummary.dataOffset).isEqualTo(4096L)
            assertThat(nativeSummary.loadMode).isEqualTo(HexArchiveNativeLoadMode.DIRECT_MMAP_READY)
            assertThat(nativeSummary.pageAlignmentRemainder).isEqualTo(0L)
            assertThat(nativeSummary.isElf).isTrue()
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseElfSummarySectionNamesAndSignals() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64())

            val analysis = analyzeHexBinaryFile(file)

            assertThat(analysis.fileKind).isEqualTo(HexFileKind.ELF)
            assertThat(analysis.elf).isNotNull()
            assertThat(analysis.elf!!.is64Bit).isTrue()
            assertThat(analysis.elf!!.endian).isEqualTo(HexEndian.LITTLE)
            assertThat(analysis.elf!!.machineName).isEqualTo("AArch64")
            assertThat(analysis.elf!!.entryPoint).isEqualTo(0x400000L)
            assertThat(analysis.elf!!.entryFileOffset).isEqualTo(0L)
            assertThat(analysis.elf!!.loadSegments).hasSize(1)
            assertThat(analysis.elf!!.programHeaders).hasSize(4)
            assertThat(analysis.elf!!.programHeaderCount).isEqualTo(4)
            assertThat(analysis.elf!!.sectionHeaderCount).isEqualTo(11)
            assertThat(analysis.elf!!.sectionNames)
                .containsAtLeast(
                    ".shstrtab",
                    ".init_array",
                    ".rodata",
                    ".dynstr",
                    ".dynsym",
                    ".rela.plt",
                    ".got.plt",
                    ".dynamic",
                    ".note.gnu.build-id",
                    ".plt"
                )
            val rodataSection = analysis.elf!!.sections.first { it.name == ".rodata" }
            assertThat(rodataSection.index).isEqualTo(3)
            assertThat(rodataSection.fileOffset).isEqualTo(0x3D0L)
            assertThat(rodataSection.virtualAddress).isEqualTo(0x4003D0L)
            assertThat(rodataSection.size).isEqualTo(16L)
            assertThat(rodataSection.flags).isEqualTo(0x2L)
            assertThat(analysis.elf!!.sectionSegmentMappings).hasSize(10)
            val rodataMapping = analysis.elf!!.sectionSegmentMappings.first { it.sectionName == ".rodata" }
            assertThat(rodataMapping).isEqualTo(
                HexElfSectionSegmentMapping(
                    index = 2,
                    sectionIndex = 3,
                    sectionName = ".rodata",
                    sectionFileOffset = 0x3D0L,
                    sectionSize = 16L,
                    sectionVirtualAddress = 0x4003D0L,
                    segmentIndex = 0,
                    segmentTypeName = "LOAD",
                    segmentFileOffset = 0L,
                    segmentFileSize = 0x900L,
                    segmentVirtualAddress = 0x400000L,
                    segmentMemorySize = 0x900L,
                    segmentFlags = 0x5,
                    isExecutable = true,
                    isWritable = false,
                    isReadable = true
                )
            )
            val pltMapping = analysis.elf!!.sectionSegmentMappings.first { it.sectionName == ".plt" }
            assertThat(pltMapping.sectionFileOffset).isEqualTo(0x200L)
            assertThat(pltMapping.sectionVirtualAddress).isEqualTo(0x400200L)
            assertThat(pltMapping.isExecutable).isTrue()
            assertThat(analysis.elf!!.sectionEntropyEntries).hasSize(10)
            val rodataEntropy = analysis.elf!!.sectionEntropyEntries.first { it.sectionName == ".rodata" }
            assertThat(rodataEntropy.fileOffset).isEqualTo(0x3D0L)
            assertThat(rodataEntropy.size).isEqualTo(16L)
            assertThat(rodataEntropy.sampleSize).isEqualTo(16L)
            assertThat(rodataEntropy.entropy).isWithin(0.001).of(0.0)
            assertThat(rodataEntropy.level).isEqualTo(HexEntropyLevel.LOW)
            assertThat(analysis.signals.map { it.type })
                .containsAtLeast(
                    HexAnalysisSignalType.ELF_PROGRAM_HEADERS,
                    HexAnalysisSignalType.ELF_SECTION_SEGMENTS,
                    HexAnalysisSignalType.ELF_SECTION_ENTROPY,
                    HexAnalysisSignalType.ELF_INIT_ARRAY,
                    HexAnalysisSignalType.ELF_DYNAMIC_SYMBOLS,
                    HexAnalysisSignalType.ELF_DYNAMIC_DEPENDENCIES,
                    HexAnalysisSignalType.ELF_NOTES,
                    HexAnalysisSignalType.ELF_BUILD_ID,
                    HexAnalysisSignalType.ELF_RELOCATIONS,
                    HexAnalysisSignalType.ELF_RODATA
                )
            assertThat(analysis.signals.first { it.type == HexAnalysisSignalType.ELF_SECTION_SEGMENTS }.offset)
                .isEqualTo(0x500L)
            assertThat(analysis.signals.first { it.type == HexAnalysisSignalType.ELF_SECTION_ENTROPY }.offset)
                .isEqualTo(0x500L)
            assertThat(analysis.obfuscationFindings).isEmpty()
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseElfProgramHeadersAndHardeningChecks() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64())

            val analysis = analyzeHexBinaryFile(file)
            val elf = analysis.elf!!

            assertThat(elf.programHeaders.map { it.typeName })
                .containsExactly("LOAD", "DYNAMIC", "GNU_RELRO", "GNU_STACK")
                .inOrder()
            assertThat(elf.programHeaders[0].isLoad).isTrue()
            assertThat(elf.programHeaders[0].isExecutable).isTrue()
            assertThat(elf.programHeaders[0].programHeaderFileOffset).isEqualTo(0x40L)
            assertThat(elf.programHeaders[1].fileOffset).isEqualTo(0x580L)
            assertThat(elf.programHeaders[2].typeName).isEqualTo("GNU_RELRO")
            assertThat(elf.programHeaders[3].isExecutable).isFalse()
            assertThat(elf.hardeningChecks).containsExactly(
                HexElfHardeningCheck(
                    type = HexElfHardeningType.PIE,
                    enabled = true,
                    evidenceFileOffset = null
                ),
                HexElfHardeningCheck(
                    type = HexElfHardeningType.NX,
                    enabled = true,
                    evidenceFileOffset = 0xE8L
                ),
                HexElfHardeningCheck(
                    type = HexElfHardeningType.RELRO,
                    enabled = true,
                    evidenceFileOffset = 0xB0L
                ),
                HexElfHardeningCheck(
                    type = HexElfHardeningType.BIND_NOW,
                    enabled = true,
                    evidenceFileOffset = 0x5B0L
                )
            ).inOrder()
            assertThat(analysis.signals.map { it.type }).doesNotContain(HexAnalysisSignalType.ELF_HARDENING_WARNING)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseElfGnuPropertyAndCetHardening() {
        runBlocking {
            val file = tempBinaryFile("demo-x86_64.so", minimalElf64X86_64(includeGnuProperty = true))

            val analysis = analyzeHexBinaryFile(file)
            val elf = analysis.elf!!

            assertThat(elf.noteEntries)
                .hasSize(2)
            val gnuPropertyNote = elf.noteEntries.first { it.properties.isNotEmpty() }
            assertThat(gnuPropertyNote.name).isEqualTo("GNU")
            assertThat(gnuPropertyNote.type).isEqualTo(5L)
            assertThat(gnuPropertyNote.properties).hasSize(1)
            assertThat(gnuPropertyNote.properties.single().typeName).isEqualTo("X86_FEATURE_1_AND")
            assertThat(gnuPropertyNote.properties.single().value).isEqualTo(0x3L)
            assertThat(gnuPropertyNote.properties.single().valueHex).isEqualTo("00000003")
            assertThat(gnuPropertyNote.properties.single().features)
                .containsExactly(HexElfNotePropertyFeature.X86_IBT, HexElfNotePropertyFeature.X86_SHSTK)
                .inOrder()
            assertThat(elf.hardeningChecks.map { it.type })
                .containsAtLeast(
                    HexElfHardeningType.PIE,
                    HexElfHardeningType.NX,
                    HexElfHardeningType.RELRO,
                    HexElfHardeningType.BIND_NOW,
                    HexElfHardeningType.IBT,
                    HexElfHardeningType.SHSTK
                )
            assertThat(elf.hardeningChecks.first { it.type == HexElfHardeningType.IBT }.enabled).isTrue()
            assertThat(elf.hardeningChecks.first { it.type == HexElfHardeningType.SHSTK }.enabled).isTrue()
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.ELF_GNU_PROPERTY)
            assertThat(analysis.signals.first { it.type == HexAnalysisSignalType.ELF_GNU_PROPERTY }.offset)
                .isEqualTo(0x484L)
            assertThat(filterElfNotes(elf.noteEntries, query = "ibt")).containsExactly(gnuPropertyNote)
            assertThat(filterElfNotes(elf.noteEntries, query = "shadow stack")).containsExactly(gnuPropertyNote)
            assertThat(filterElfNotes(elf.noteEntries, query = "0xc0000002")).containsExactly(gnuPropertyNote)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseElfDynamicDependenciesAndSearchPaths() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64())

            val analysis = analyzeHexBinaryFile(file)
            val elf = analysis.elf!!

            assertThat(elf.dynamicStringEntries).containsExactly(
                HexElfDynamicStringEntry(
                    index = 0,
                    type = HexElfDynamicStringType.NEEDED,
                    value = "libc.so",
                    entryFileOffset = 0x580L,
                    loadOrder = 1,
                    semantic = HexElfDynamicStringSemantic.NEEDED_LIBRARY_LOAD
                ),
                HexElfDynamicStringEntry(
                    index = 1,
                    type = HexElfDynamicStringType.SONAME,
                    value = "libdemo.so",
                    entryFileOffset = 0x590L,
                    semantic = HexElfDynamicStringSemantic.SONAME_IDENTITY
                ),
                HexElfDynamicStringEntry(
                    index = 2,
                    type = HexElfDynamicStringType.RUNPATH,
                    value = "\$ORIGIN",
                    entryFileOffset = 0x5A0L,
                    semantic = HexElfDynamicStringSemantic.RUNPATH_SEARCH
                )
            ).inOrder()
            assertThat(elf.neededLibraries.map { it.value }).containsExactly("libc.so")
            assertThat(elf.neededLibraries.first().loadOrder).isEqualTo(1)
            assertThat(elf.soname!!.value).isEqualTo("libdemo.so")
            assertThat(elf.runtimeSearchPaths.map { it.value }).containsExactly("\$ORIGIN")
            assertThat(filterElfDynamicEntries(elf.dynamicStringEntries, query = "declaration order"))
                .containsExactly(elf.dynamicStringEntries[0])
            assertThat(filterElfDynamicEntries(elf.dynamicStringEntries, query = "dependency search"))
                .containsExactly(elf.dynamicStringEntries[2])
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.ELF_DYNAMIC_DEPENDENCIES)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseElfDynamicFlagsForBindNow() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64())

            val elf = analyzeHexBinaryFile(file).elf!!

            assertThat(elf.dynamicFlagEntries).containsExactly(
                HexElfDynamicFlagEntry(
                    index = 3,
                    type = HexElfDynamicFlagType.BIND_NOW,
                    value = 0L,
                    entryFileOffset = 0x5B0L,
                    isBindNow = true
                ),
                HexElfDynamicFlagEntry(
                    index = 4,
                    type = HexElfDynamicFlagType.FLAGS,
                    value = 0x8L,
                    entryFileOffset = 0x5C0L,
                    isBindNow = true
                ),
                HexElfDynamicFlagEntry(
                    index = 5,
                    type = HexElfDynamicFlagType.FLAGS_1,
                    value = 0x1L,
                    entryFileOffset = 0x5D0L,
                    isBindNow = true
                )
            ).inOrder()
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseElfNotesAndBuildId() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64())

            val analysis = analyzeHexBinaryFile(file)
            val elf = analysis.elf!!

            assertThat(elf.noteEntries).containsExactly(
                HexElfNoteEntry(
                    index = 0,
                    sectionName = ".note.gnu.build-id",
                    name = "GNU",
                    type = 3L,
                    noteFileOffset = 0x460L,
                    descriptionOffset = 0x470L,
                    descriptionSize = 20L,
                    descriptionHex = "0123456789abcdeffedcba9876543210aabbccdd",
                    descriptionText = null,
                    isBuildId = true
                )
            )
            assertThat(elf.buildId).isEqualTo(elf.noteEntries.single())
            assertThat(analysis.signals.map { it.type })
                .containsAtLeast(HexAnalysisSignalType.ELF_NOTES, HexAnalysisSignalType.ELF_BUILD_ID)
            assertThat(analysis.signals.first { it.type == HexAnalysisSignalType.ELF_BUILD_ID }.offset)
                .isEqualTo(0x470L)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseElfDynamicSymbolsImportsExportsAndJni() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64())

            val analysis = analyzeHexBinaryFile(file)
            val elf = analysis.elf!!

            assertThat(elf.dynamicSymbols.map { it.name })
                .containsAtLeast("puts", "JNI_OnLoad", "Java_com_example_Native_call")
            assertThat(elf.importedSymbols.map { it.name }).containsExactly("puts")
            assertThat(elf.exportedSymbols.map { it.name })
                .containsAtLeast("JNI_OnLoad", "Java_com_example_Native_call")
            assertThat(elf.jniSymbols.map { it.name })
                .containsExactly("JNI_OnLoad", "Java_com_example_Native_call")
            assertThat(elf.dynamicSymbols.first { it.name == "puts" }.type).isEqualTo(HexElfSymbolType.FUNC)
            val importedSymbol = elf.dynamicSymbols.first { it.name == "puts" }
            val jniOnLoad = elf.dynamicSymbols.first { it.name == "JNI_OnLoad" }
            val javaNativeCall = elf.dynamicSymbols.first { it.name == "Java_com_example_Native_call" }
            assertThat(jniOnLoad.binding).isEqualTo(HexElfSymbolBinding.GLOBAL)
            assertThat(jniOnLoad.sectionName).isEqualTo(".rodata")
            assertThat(jniOnLoad.sectionFileOffset).isEqualTo(0x3D0L)
            assertThat(jniOnLoad.sectionSize).isEqualTo(16L)
            assertThat(javaNativeCall.sectionName).isEqualTo(".rodata")
            assertThat(importedSymbol.sectionName).isNull()
            assertThat(elf.nativeApiHints).containsExactly(
                HexElfNativeApiHint(
                    index = 0,
                    category = HexElfNativeApiCategory.LOGGING,
                    symbolName = "puts",
                    evidenceFileOffset = null
                )
            )
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.ELF_NATIVE_API_HINTS)
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.ELF_JNI_SYMBOLS)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldBuildJniRegistrationHints() {
        runBlocking {
            val file = tempBinaryFile(
                "demo.so",
                minimalElf64Aarch64(rodataText = "RegisterNatives\u0000(I)V\u0000Lx/y/Z;")
            )

            val analysis = analyzeHexBinaryFile(file)
            val elf = analysis.elf!!

            assertThat(elf.jniRegistrationHints.map { it.type })
                .containsAtLeast(
                    HexElfJniRegistrationHintType.JNI_ONLOAD_ENTRY,
                    HexElfJniRegistrationHintType.STATIC_JNI_EXPORT,
                    HexElfJniRegistrationHintType.REGISTER_NATIVES_STRING,
                    HexElfJniRegistrationHintType.JNI_METHOD_SIGNATURE,
                    HexElfJniRegistrationHintType.JAVA_CLASS_DESCRIPTOR
                )
            assertThat(elf.jniRegistrationHints.first { it.type == HexElfJniRegistrationHintType.JNI_ONLOAD_ENTRY })
                .isEqualTo(
                    HexElfJniRegistrationHint(
                        index = 0,
                        type = HexElfJniRegistrationHintType.JNI_ONLOAD_ENTRY,
                        evidenceFileOffset = 0x3D0L,
                        symbolName = "JNI_OnLoad"
                    )
                )
            assertThat(elf.jniRegistrationHints.first { it.type == HexElfJniRegistrationHintType.STATIC_JNI_EXPORT }.symbolName)
                .isEqualTo("Java_com_example_Native_call")
            assertThat(
                elf.jniRegistrationHints.first {
                    it.type == HexElfJniRegistrationHintType.REGISTER_NATIVES_STRING
                }.evidenceFileOffset
            ).isEqualTo(0x3D0L)
            assertThat(
                elf.jniRegistrationHints.first {
                    it.type == HexElfJniRegistrationHintType.JNI_METHOD_SIGNATURE
                }.stringValue
            ).isEqualTo("(I)V")
            assertThat(
                elf.jniRegistrationHints.first {
                    it.type == HexElfJniRegistrationHintType.JAVA_CLASS_DESCRIPTOR
                }.stringValue
            ).isEqualTo("Lx/y/Z;")
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.ELF_JNI_REGISTRATION_HINTS)
            assertThat(analysis.signals.first { it.type == HexAnalysisSignalType.ELF_JNI_REGISTRATION_HINTS }.offset)
                .isEqualTo(0x3D0L)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldMapElfVirtualAddressesToFileOffsets() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64())

            val elf = analyzeHexBinaryFile(file).elf!!
            val jniOnLoad = elf.dynamicSymbols.first { it.name == "JNI_OnLoad" }
            val javaNativeCall = elf.dynamicSymbols.first { it.name == "Java_com_example_Native_call" }

            assertThat(elf.virtualAddressToFileOffset(0x400000)).isEqualTo(0L)
            assertThat(elf.virtualAddressToFileOffset(0x4003D0)).isEqualTo(0x3D0L)
            assertThat(elf.virtualAddressToFileOffset(0x500000)).isNull()
            assertThat(jniOnLoad.fileOffset).isEqualTo(0x3D0L)
            assertThat(javaNativeCall.fileOffset).isEqualTo(0x3F0L)
            assertThat(elf.importedSymbols.single().fileOffset).isNull()
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseElfInitArrayEntries() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64())

            val elf = analyzeHexBinaryFile(file).elf!!

            assertThat(elf.initArrayEntries).hasSize(2)
            assertThat(elf.initArrayEntries[0]).isEqualTo(
                HexElfInitArrayEntry(
                    index = 0,
                    pointerFileOffset = 0x3C0L,
                    functionAddress = 0x4003D0L,
                    functionFileOffset = 0x3D0L
                )
            )
            assertThat(elf.initArrayEntries[1]).isEqualTo(
                HexElfInitArrayEntry(
                    index = 1,
                    pointerFileOffset = 0x3C8L,
                    functionAddress = 0x500000L,
                    functionFileOffset = null
                )
            )
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldParseElfRelocationsWithSymbolsAndOffsets() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64())

            val analysis = analyzeHexBinaryFile(file)
            val elf = analysis.elf!!

            assertThat(elf.relocations).hasSize(2)
            assertThat(elf.relocations[0]).isEqualTo(
                HexElfRelocationEntry(
                    index = 0,
                    sectionName = ".rela.plt",
                    relocationFileOffset = 0x410L,
                    offsetAddress = 0x4004A0L,
                    offsetFileOffset = 0x4A0L,
                    targetSectionName = ".got.plt",
                    symbolName = "puts",
                    symbolBinding = HexElfSymbolBinding.GLOBAL,
                    symbolType = HexElfSymbolType.FUNC,
                    isSymbolImported = true,
                    isSymbolExported = false,
                    isSymbolJni = false,
                    symbolIndex = 1L,
                    type = 1026L,
                    typeName = "AARCH64_JUMP_SLOT",
                    semantic = HexElfRelocationSemantic.JUMP_SLOT_BINDING,
                    addend = 0L
                )
            )
            assertThat(elf.relocations[1]).isEqualTo(
                HexElfRelocationEntry(
                    index = 1,
                    sectionName = ".rela.plt",
                    relocationFileOffset = 0x428L,
                    offsetAddress = 0x4004A8L,
                    offsetFileOffset = 0x4A8L,
                    targetSectionName = ".got.plt",
                    symbolName = "JNI_OnLoad",
                    symbolBinding = HexElfSymbolBinding.GLOBAL,
                    symbolType = HexElfSymbolType.FUNC,
                    isSymbolImported = false,
                    isSymbolExported = true,
                    isSymbolJni = true,
                    symbolIndex = 2L,
                    type = 1027L,
                    typeName = "AARCH64_RELATIVE",
                    semantic = HexElfRelocationSemantic.RELATIVE_REBASE,
                    addend = 4L
                )
            )
            assertThat(filterElfRelocations(elf.relocations, query = "load bias"))
                .containsExactly(elf.relocations[1])
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.ELF_RELOCATIONS)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldDeriveElfPltGotLinkageEntries() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64())

            val analysis = analyzeHexBinaryFile(file)
            val elf = analysis.elf!!

            assertThat(elf.linkageEntries).hasSize(2)
            assertThat(elf.linkageEntries[0]).isEqualTo(
                HexElfLinkageEntry(
                    index = 0,
                    symbolName = "puts",
                    symbolIndex = 1L,
                    relocationSectionName = ".rela.plt",
                    relocationTypeName = "AARCH64_JUMP_SLOT",
                    relocationFileOffset = 0x410L,
                    slotAddress = 0x4004A0L,
                    slotFileOffset = 0x4A0L,
                    slotSectionName = ".got.plt",
                    symbolBinding = HexElfSymbolBinding.GLOBAL,
                    symbolType = HexElfSymbolType.FUNC,
                    isImported = true,
                    isExported = false,
                    isJni = false,
                    entryKind = HexElfLinkageEntryKind.PLT,
                    bindingMode = HexElfLinkageBindingMode.NOW,
                    resolutionSemantic = HexElfLinkageResolutionSemantic.EAGER_PLT_BINDING,
                    pltStub = HexElfPltStub(
                        fileOffset = 0x220L,
                        virtualAddress = 0x400220L,
                        byteCount = 16,
                        instructionBytes = "10 00 00 90 11 02 40 F9 10 02 00 91 20 02 1F D6",
                        architecture = HexElfPltStubArchitecture.AARCH64,
                        semantic = HexElfPltStubSemantic.LOAD_GOT_SLOT_AND_BRANCH,
                        slotFileOffset = 0x4A0L,
                        slotAddress = 0x4004A0L
                    )
                )
            )
            assertThat(filterElfLinkageEntries(elf.linkageEntries, query = "400220"))
                .containsExactly(elf.linkageEntries[0])
            assertThat(filterElfLinkageEntries(elf.linkageEntries, query = "startup"))
                .containsExactly(elf.linkageEntries[0])
            assertThat(elf.linkageEntries[1].entryKind).isEqualTo(HexElfLinkageEntryKind.RELATIVE)
            assertThat(elf.linkageEntries[1].bindingMode).isEqualTo(HexElfLinkageBindingMode.LOCAL)
            assertThat(elf.linkageEntries[1].resolutionSemantic)
                .isEqualTo(HexElfLinkageResolutionSemantic.RELATIVE_REBASE)
            assertThat(elf.linkageEntries[1].isJni).isTrue()
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.ELF_LINKAGE)
            assertThat(analysis.signals.first { it.type == HexAnalysisSignalType.ELF_LINKAGE }.offset)
                .isEqualTo(0x4A0L)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldDeriveX86_64PltStubSemantics() {
        runBlocking {
            val file = tempBinaryFile("demo-x86_64.so", minimalElf64X86_64())

            val analysis = analyzeHexBinaryFile(file)
            val elf = analysis.elf!!
            val pltEntry = elf.linkageEntries.single { entry -> entry.symbolName == "puts" }
            val pltStub = pltEntry.pltStub!!

            assertThat(pltStub.architecture).isEqualTo(HexElfPltStubArchitecture.X86_64)
            assertThat(pltStub.semantic).isEqualTo(HexElfPltStubSemantic.LOAD_GOT_SLOT_AND_BRANCH)
            assertThat(pltStub.fileOffset).isEqualTo(0x210L)
            assertThat(pltStub.virtualAddress).isEqualTo(0x400210L)
            assertThat(pltStub.byteCount).isEqualTo(16)
            assertThat(pltStub.instructionBytes).isEqualTo("FF 25 78 56 34 12 68 00 00 00 00 E9 11 22 33 44")
            assertThat(filterElfLinkageEntries(elf.linkageEntries, query = "load got slot"))
                .containsExactly(pltEntry)
            assertThat(filterElfLinkageEntries(elf.linkageEntries, query = "branch"))
                .containsExactly(pltEntry)
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.ELF_LINKAGE)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldMarkPltLinkageLazyWithoutBindNow() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64(bindNow = false))

            val elf = analyzeHexBinaryFile(file).elf!!
            val pltEntry = elf.linkageEntries.first { entry -> entry.symbolName == "puts" }

            assertThat(pltEntry.entryKind).isEqualTo(HexElfLinkageEntryKind.PLT)
            assertThat(pltEntry.bindingMode).isEqualTo(HexElfLinkageBindingMode.LAZY)
            assertThat(pltEntry.resolutionSemantic).isEqualTo(HexElfLinkageResolutionSemantic.LAZY_PLT_CALL)
            assertThat(filterElfLinkageEntries(elf.linkageEntries, query = "first call"))
                .containsExactly(pltEntry)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldBuildDynamicLinkerStepsForBindNowElf() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64())

            val analysis = analyzeHexBinaryFile(file)
            val elf = analysis.elf!!

            assertThat(elf.dynamicLinkerSteps.map { it.type })
                .containsExactly(
                    HexElfDynamicLinkerStepType.MAP_LOAD_SEGMENTS,
                    HexElfDynamicLinkerStepType.LOAD_NEEDED_LIBRARIES,
                    HexElfDynamicLinkerStepType.APPLY_RELOCATIONS,
                    HexElfDynamicLinkerStepType.RESOLVE_NOW_BINDINGS,
                    HexElfDynamicLinkerStepType.PROTECT_RELRO,
                    HexElfDynamicLinkerStepType.CALL_INIT_ARRAY,
                    HexElfDynamicLinkerStepType.EXPOSE_JNI_ENTRYPOINTS
                )
                .inOrder()
            assertThat(elf.dynamicLinkerSteps.first { it.type == HexElfDynamicLinkerStepType.LOAD_NEEDED_LIBRARIES })
                .isEqualTo(
                    HexElfDynamicLinkerStep(
                        index = 1,
                        type = HexElfDynamicLinkerStepType.LOAD_NEEDED_LIBRARIES,
                        evidenceFileOffset = 0x580L,
                        relatedCount = 1,
                        detailValue = "#1 libc.so; RUNPATH \$ORIGIN"
                    )
                )
            assertThat(elf.dynamicLinkerSteps.first { it.type == HexElfDynamicLinkerStepType.APPLY_RELOCATIONS }.relatedCount)
                .isEqualTo(2)
            assertThat(elf.dynamicLinkerSteps.first { it.type == HexElfDynamicLinkerStepType.RESOLVE_NOW_BINDINGS }.detailValue)
                .isEqualTo("puts")
            assertThat(elf.dynamicLinkerSteps.first { it.type == HexElfDynamicLinkerStepType.PROTECT_RELRO }.evidenceFileOffset)
                .isEqualTo(0xB0L)
            assertThat(elf.dynamicLinkerSteps.first { it.type == HexElfDynamicLinkerStepType.EXPOSE_JNI_ENTRYPOINTS }.detailValue)
                .isEqualTo("JNI_OnLoad, Java_com_example_Native_call")
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.ELF_DYNAMIC_LINKER_STEPS)
        }
    }

    @Test
    fun elfPltStubSemantic_shouldClassifyX86_64StubBytes() {
        assertThat(
            classifyPltStubSemantic(
                machine = 0x3E,
                endian = HexEndian.LITTLE,
                stubBytes = byteArrayOf(
                    0xFF.toByte(),
                    0x25.toByte(),
                    0x78.toByte(),
                    0x56.toByte(),
                    0x34.toByte(),
                    0x12.toByte(),
                    0x68.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0xE9.toByte(),
                    0x11.toByte(),
                    0x22.toByte(),
                    0x33.toByte(),
                    0x44.toByte()
                )
            )
        ).isEqualTo(HexElfPltStubSemantic.LOAD_GOT_SLOT_AND_BRANCH)
    }

    @Test
    fun analyzeHexBinaryFile_shouldBuildLazyDynamicLinkerStepWithoutBindNow() {
        runBlocking {
            val file = tempBinaryFile("demo.so", minimalElf64Aarch64(bindNow = false))

            val elf = analyzeHexBinaryFile(file).elf!!

            assertThat(elf.dynamicLinkerSteps.map { it.type })
                .contains(HexElfDynamicLinkerStepType.ENABLE_LAZY_PLT)
            assertThat(elf.dynamicLinkerSteps.map { it.type })
                .doesNotContain(HexElfDynamicLinkerStepType.RESOLVE_NOW_BINDINGS)
            assertThat(elf.dynamicLinkerSteps.first { it.type == HexElfDynamicLinkerStepType.ENABLE_LAZY_PLT }.detailValue)
                .isEqualTo("puts")
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldReportOllvmObfuscationMarkersAsHeuristics() {
        runBlocking {
            val file = tempBinaryFile(
                "demo.so",
                minimalElf64Aarch64(rodataText = "ollvm-fla ollvm-bcf ollvm-sub")
            )

            val analysis = analyzeHexBinaryFile(file)

            assertThat(analysis.obfuscationFindings.map { it.type })
                .containsAtLeast(
                    HexObfuscationFindingType.OLLVM_MARKER,
                    HexObfuscationFindingType.CONTROL_FLOW_FLATTENING_MARKER,
                    HexObfuscationFindingType.BOGUS_CONTROL_FLOW_MARKER,
                    HexObfuscationFindingType.INSTRUCTION_SUBSTITUTION_MARKER
                )
            assertThat(analysis.obfuscationFindings.map { it.confidence }).contains(HexFindingConfidence.HIGH)
            assertThat(analysis.obfuscationFindings.mapNotNull { it.offset }).contains(0x3D0L)
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.OBFUSCATION_RISK)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldReportAntiDebugAndInstrumentationHeuristics() {
        runBlocking {
            val file = tempBinaryFile(
                "demo.so",
                minimalElf64Aarch64(rodataText = "ptrace TracerPid /proc/self/status frida xposed")
            )

            val analysis = analyzeHexBinaryFile(file)

            assertThat(analysis.obfuscationFindings.map { it.type })
                .containsAtLeast(
                    HexObfuscationFindingType.ANTI_DEBUG_HEURISTIC,
                    HexObfuscationFindingType.ANTI_INSTRUMENTATION_HEURISTIC
                )
            assertThat(
                analysis.obfuscationFindings.first {
                    it.type == HexObfuscationFindingType.ANTI_DEBUG_HEURISTIC
                }.evidence
            ).contains("ptrace")
            assertThat(analysis.obfuscationFindings.mapNotNull { it.offset }).contains(0x3D0L)
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.OBFUSCATION_RISK)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldReportProtectorPackerMarkersAsHeuristics() {
        runBlocking {
            val file = tempBinaryFile(
                "demo.so",
                minimalElf64Aarch64(rodataText = "libjiagu DexProtector secneo")
            )

            val analysis = analyzeHexBinaryFile(file)
            val finding = analysis.obfuscationFindings.first {
                it.type == HexObfuscationFindingType.PROTECTOR_PACKER_MARKER
            }

            assertThat(finding.confidence).isEqualTo(HexFindingConfidence.MEDIUM)
            assertThat(finding.evidence).contains("libjiagu")
            assertThat(finding.offset).isEqualTo(0x3D0L)
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.OBFUSCATION_RISK)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldExtractPrintableStrings() {
        runBlocking {
            val file = tempBinaryFile(
                "strings.bin",
                byteArrayOf(0x00, 0x01) + "JNI_OnLoad libc.so".toByteArray() + byteArrayOf(0x00)
            )

            val analysis = analyzeHexBinaryFile(file)

            assertThat(analysis.strings).contains(HexStringEntry(offset = 2L, value = "JNI_OnLoad libc.so"))
            assertThat(analysis.strings.first { it.value == "JNI_OnLoad libc.so" }.encoding)
                .isEqualTo(HexStringEncoding.ASCII)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldExtractUtf16StringsWithEncodingAndOffset() {
        runBlocking {
            val leOffset = 1L
            val beOffset = 24L
            val bytes = byteArrayOf(0x00) +
                "WideLE".toUtf16LeBytes() +
                ByteArray((beOffset - leOffset - "WideLE".length * 2).toInt()) +
                "WideBE".toUtf16BeBytes()
            val file = tempBinaryFile("wide.bin", bytes)

            val analysis = analyzeHexBinaryFile(file)

            assertThat(analysis.strings).contains(
                HexStringEntry(offset = leOffset, value = "WideLE", encoding = HexStringEncoding.UTF_16LE)
            )
            assertThat(analysis.strings).contains(
                HexStringEntry(offset = beOffset, value = "WideBE", encoding = HexStringEncoding.UTF_16BE)
            )
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldExtractUtf8StringsWithoutDuplicatingPlainAscii() {
        runBlocking {
            val utf8Text = "caf\u00E9_delta"
            val asciiText = "plain_ascii"
            val asciiOffset = 2L + utf8Text.toByteArray(Charsets.UTF_8).size + 1
            val file = tempBinaryFile(
                "utf8.bin",
                byteArrayOf(0x00, 0x01) +
                    utf8Text.toByteArray(Charsets.UTF_8) +
                    byteArrayOf(0x00) +
                    asciiText.toByteArray(Charsets.US_ASCII) +
                    byteArrayOf(0x00)
            )

            val analysis = analyzeHexBinaryFile(file)

            assertThat(analysis.strings).contains(
                HexStringEntry(offset = 2L, value = utf8Text, encoding = HexStringEncoding.UTF_8)
            )
            assertThat(analysis.strings)
                .doesNotContain(HexStringEntry(offset = asciiOffset, value = asciiText, encoding = HexStringEncoding.UTF_8))
        }
    }

    @Test
    fun filterStringEntries_shouldFilterByQueryEncodingOffsetAndLimit() {
        val asciiLibrary = HexStringEntry(offset = 0x02L, value = "libart.so")
        val asciiJni = HexStringEntry(offset = 0x10L, value = "JNI_OnLoad")
        val utf16Jni = HexStringEntry(offset = 0x20L, value = "jni_bridge", encoding = HexStringEncoding.UTF_16LE)
        val utf8Jni = HexStringEntry(offset = 0x28L, value = "jni_caf\u00E9", encoding = HexStringEncoding.UTF_8)
        val utf16Wide = HexStringEntry(offset = 0x30L, value = "WideString", encoding = HexStringEncoding.UTF_16BE)
        val entries = listOf(utf16Wide, utf8Jni, utf16Jni, asciiJni, asciiLibrary)

        assertThat(filterStringEntries(entries, query = "jni"))
            .containsExactly(asciiJni, utf16Jni, utf8Jni)
            .inOrder()
        assertThat(
            filterStringEntries(
                entries = entries,
                query = "caf\u00E9",
                encodingFilter = StringEntryEncodingFilter.UTF_8
            )
        ).containsExactly(utf8Jni)
        assertThat(
            filterStringEntries(
                entries = entries,
                query = "jni",
                encodingFilter = StringEntryEncodingFilter.UTF_16LE
            )
        ).containsExactly(utf16Jni)
        assertThat(
            filterStringEntries(
                entries = entries,
                query = "0x3",
                encodingFilter = StringEntryEncodingFilter.UTF_16BE
            )
        ).containsExactly(utf16Wide)
        assertThat(filterStringEntries(entries, query = "", limit = 2))
            .containsExactly(asciiLibrary, asciiJni)
            .inOrder()
        assertThat(filterStringEntries(entries, query = "jni", limit = 0)).isEmpty()
    }

    @Test
    fun formatStringEntriesExport_shouldWriteStableTabSeparatedRows() {
        val entries = listOf(
            HexStringEntry(offset = 0x02L, value = "JNI_OnLoad", encoding = HexStringEncoding.ASCII),
            HexStringEntry(offset = 0x28L, value = "jni_caf\u00E9", encoding = HexStringEncoding.UTF_8),
            HexStringEntry(offset = 0x40L, value = "line\tvalue\\tail", encoding = HexStringEncoding.UTF_16LE)
        )

        assertThat(formatStringEntriesExport(entries)).isEqualTo(
            "0x00000002\tASCII\tJNI_OnLoad\n" +
                "0x00000028\tUTF-8\tjni_caf\u00E9\n" +
                "0x00000040\tUTF-16LE\tline\\tvalue\\\\tail"
        )
    }

    @Test
    fun filterDexTypeProtoFieldMethodAndClassEntries_shouldFilterByDescriptorNameOffsetAndLimit() {
        val mainType = HexDexTypeEntry(
            index = 0,
            typeIdOffset = 0x78L,
            descriptorStringIndex = 0L,
            descriptor = "Lcom/example/MainActivity;"
        )
        val utilType = HexDexTypeEntry(
            index = 1,
            typeIdOffset = 0x7CL,
            descriptorStringIndex = 2L,
            descriptor = "Lcom/example/Util;"
        )
        val intProto = HexDexProtoEntry(
            index = 0,
            protoIdOffset = 0x8CL,
            shortyStringIndex = 4L,
            shorty = "II",
            returnTypeIndex = 1L,
            returnTypeDescriptor = "I",
            parametersOffset = 0x1F0L,
            parameterTypeDescriptors = listOf("I"),
            signature = "(I)I"
        )
        val voidProto = intProto.copy(
            index = 1,
            protoIdOffset = 0x98L,
            shorty = "V",
            returnTypeIndex = 0L,
            returnTypeDescriptor = "V",
            parametersOffset = 0L,
            parameterTypeDescriptors = emptyList(),
            signature = "()V"
        )
        val counterField = HexDexFieldEntry(
            index = 0,
            fieldIdOffset = 0xA4L,
            classIndex = 0,
            classDescriptor = mainType.descriptor,
            typeIndex = 1,
            typeDescriptor = "I",
            nameStringIndex = 5L,
            name = "counter"
        )
        val helperField = counterField.copy(
            index = 1,
            fieldIdOffset = 0xACL,
            classIndex = 1,
            classDescriptor = utilType.descriptor,
            name = "helperState"
        )
        val nativeMethod = HexDexMethodEntry(
            index = 0,
            methodIdOffset = 0x80L,
            classIndex = 0,
            classDescriptor = mainType.descriptor,
            protoIndex = 0,
            protoShorty = intProto.shorty,
            protoSignature = intProto.signature,
            nameStringIndex = 1L,
            name = "nativeEntry"
        )
        val helperMethod = HexDexMethodEntry(
            index = 1,
            methodIdOffset = 0x88L,
            classIndex = 1,
            classDescriptor = utilType.descriptor,
            protoIndex = 0,
            protoShorty = intProto.shorty,
            protoSignature = intProto.signature,
            nameStringIndex = 3L,
            name = "helper"
        )
        val mainClass = HexDexClassDefEntry(
            index = 0,
            classDefOffset = 0x90L,
            classIndex = 0L,
            classDescriptor = mainType.descriptor,
            accessFlags = 1L,
            superclassIndex = null,
            superclassDescriptor = null,
            interfacesOffset = 0L,
            sourceFileIndex = null,
            sourceFile = null,
            annotationsOffset = 0L,
            classDataOffset = 0x1F0L,
            staticValuesOffset = 0L
        )
        val utilClass = mainClass.copy(
            index = 1,
            classDefOffset = 0xB0L,
            classIndex = 1L,
            classDescriptor = utilType.descriptor,
            classDataOffset = 0x208L
        )
        val directCodeMethod = HexDexClassDataMethodEntry(
            index = 0,
            classDefIndex = 0,
            classDescriptor = mainType.descriptor,
            kind = HexDexClassDataMethodKind.DIRECT,
            methodIndex = 0L,
            methodName = "nativeEntry",
            methodClassDescriptor = mainType.descriptor,
            protoSignature = intProto.signature,
            accessFlags = 1L,
            classDataOffset = 0x208L,
            entryOffset = 0x20CL,
            codeOffset = 0x218L
        )
        val virtualCodeMethod = directCodeMethod.copy(
            index = 1,
            kind = HexDexClassDataMethodKind.VIRTUAL,
            methodIndex = 1L,
            methodName = "helper",
            methodClassDescriptor = utilType.descriptor,
            entryOffset = 0x228L,
            codeOffset = 0L
        )
        val nativeClassDataMethod = directCodeMethod.copy(
            index = 2,
            methodName = "jniBridge",
            accessFlags = 0x101L,
            entryOffset = 0x238L,
            codeOffset = 0L,
            executionKind = HexDexClassDataMethodExecutionKind.NATIVE
        )
        val returnCodeItem = HexDexCodeItemEntry(
            index = 0,
            methodIndex = 0L,
            methodName = "nativeEntry",
            methodClassDescriptor = mainType.descriptor,
            protoSignature = intProto.signature,
            codeOffset = 0x218L,
            registersSize = 2,
            insSize = 1,
            outsSize = 1,
            triesSize = 0,
            debugInfoOffset = 0L,
            insnsSize = 1L,
            firstOpcode = 0x0F,
            firstOpcodeName = "return",
            previewCodeUnitsHex = "000F"
        )
        val invokeCodeItem = returnCodeItem.copy(
            index = 1,
            methodIndex = 1L,
            methodName = "helper",
            methodClassDescriptor = utilType.descriptor,
            codeOffset = 0x240L,
            firstOpcode = 0x6E,
            firstOpcodeName = "invoke",
            previewCodeUnitsHex = "006E 0000"
        )
        val invokeReference = HexDexCallReferenceEntry(
            index = 0,
            callerMethodIndex = 0L,
            callerClassDescriptor = mainType.descriptor,
            callerMethodName = "nativeEntry",
            callerProtoSignature = intProto.signature,
            targetMethodIndex = 1L,
            targetClassDescriptor = utilType.descriptor,
            targetMethodName = "helper",
            targetProtoSignature = intProto.signature,
            opcode = 0x6E,
            opcodeName = "invoke",
            instructionOffset = 0x228L,
            codeOffset = 0x218L,
            targetMethodIdOffset = helperMethod.methodIdOffset
        )
        val localInvokeReference = invokeReference.copy(
            index = 1,
            targetMethodIndex = 0L,
            targetClassDescriptor = mainType.descriptor,
            targetMethodName = "nativeEntry",
            targetMethodIdOffset = nativeMethod.methodIdOffset
        )
        val stringReference = HexDexStringReferenceEntry(
            index = 0,
            callerMethodIndex = 0L,
            callerClassDescriptor = mainType.descriptor,
            callerMethodName = "nativeEntry",
            callerProtoSignature = intProto.signature,
            stringIndex = 3L,
            value = "counter",
            opcode = 0x1A,
            opcodeName = "const-string",
            instructionOffset = 0x228L,
            codeOffset = 0x218L,
            stringIdOffset = 0x7CL,
            stringDataOffset = 0x158L
        )
        val helperStringReference = stringReference.copy(
            index = 1,
            value = "helper",
            stringIndex = 6L,
            instructionOffset = 0x22CL,
            stringIdOffset = 0x88L,
            stringDataOffset = 0x180L
        )
        val fieldReference = HexDexFieldReferenceEntry(
            index = 0,
            callerMethodIndex = 0L,
            callerClassDescriptor = mainType.descriptor,
            callerMethodName = "nativeEntry",
            callerProtoSignature = intProto.signature,
            fieldIndex = 0L,
            fieldClassDescriptor = mainType.descriptor,
            fieldName = "counter",
            fieldTypeDescriptor = "I",
            opcode = 0x60,
            opcodeName = "staticop",
            instructionOffset = 0x230L,
            codeOffset = 0x218L,
            fieldIdOffset = counterField.fieldIdOffset
        )
        val helperFieldReference = fieldReference.copy(
            index = 1,
            fieldIndex = 1L,
            fieldClassDescriptor = utilType.descriptor,
            fieldName = "helperState",
            instructionOffset = 0x234L,
            fieldIdOffset = helperField.fieldIdOffset
        )

        assertThat(filterDexTypeEntries(listOf(mainType, utilType), query = "util"))
            .containsExactly(utilType)
        assertThat(filterDexProtoEntries(listOf(intProto, voidProto), query = "(I)I"))
            .containsExactly(intProto)
        assertThat(filterDexProtoEntries(listOf(intProto, voidProto), query = "0x98"))
            .containsExactly(voidProto)
        assertThat(filterDexFieldEntries(listOf(counterField, helperField), query = "counter"))
            .containsExactly(counterField)
        assertThat(filterDexFieldEntries(listOf(counterField, helperField), query = "util"))
            .containsExactly(helperField)
        assertThat(filterDexMethodEntries(listOf(nativeMethod, helperMethod), query = "native"))
            .containsExactly(nativeMethod)
        assertThat(filterDexMethodEntries(listOf(nativeMethod, helperMethod), query = "(I)I"))
            .containsExactly(nativeMethod, helperMethod)
            .inOrder()
        assertThat(filterDexMethodEntries(listOf(nativeMethod, helperMethod), query = "0x88"))
            .containsExactly(helperMethod)
        assertThat(filterDexClassDefEntries(listOf(mainClass, utilClass), query = "0x1f0"))
            .containsExactly(mainClass)
        assertThat(filterDexClassDataMethodEntries(listOf(directCodeMethod, virtualCodeMethod), query = "direct"))
            .containsExactly(directCodeMethod)
        assertThat(filterDexClassDataMethodEntries(listOf(directCodeMethod, virtualCodeMethod), query = "0x218"))
            .containsExactly(directCodeMethod)
        assertThat(filterDexClassDataMethodEntries(listOf(directCodeMethod, virtualCodeMethod), query = "helper"))
            .containsExactly(virtualCodeMethod)
        assertThat(
            filterDexClassDataMethodEntries(
                listOf(directCodeMethod, virtualCodeMethod, nativeClassDataMethod),
                query = "acc_native"
            )
        ).containsExactly(nativeClassDataMethod)
        assertThat(filterDexCodeItemEntries(listOf(returnCodeItem, invokeCodeItem), query = "return"))
            .containsExactly(returnCodeItem)
        assertThat(filterDexCodeItemEntries(listOf(returnCodeItem, invokeCodeItem), query = "0x218"))
            .containsExactly(returnCodeItem)
        assertThat(filterDexCodeItemEntries(listOf(returnCodeItem, invokeCodeItem), query = "006E"))
            .containsExactly(invokeCodeItem)
        assertThat(filterDexCodeItemEntries(listOf(returnCodeItem, invokeCodeItem), query = "helper"))
            .containsExactly(invokeCodeItem)
        assertThat(filterDexCallReferenceEntries(listOf(invokeReference, localInvokeReference), query = "helper"))
            .containsExactly(invokeReference)
        assertThat(filterDexCallReferenceEntries(listOf(invokeReference, localInvokeReference), query = "0x228"))
            .containsExactly(invokeReference, localInvokeReference)
            .inOrder()
        assertThat(filterDexCallReferenceEntries(listOf(invokeReference, localInvokeReference), query = "0x88"))
            .containsExactly(invokeReference)
        assertThat(filterDexStringReferenceEntries(listOf(stringReference, helperStringReference), query = "counter"))
            .containsExactly(stringReference)
        assertThat(filterDexStringReferenceEntries(listOf(stringReference, helperStringReference), query = "0x158"))
            .containsExactly(stringReference)
        assertThat(filterDexFieldReferenceEntries(listOf(fieldReference, helperFieldReference), query = "helperState"))
            .containsExactly(helperFieldReference)
        assertThat(filterDexFieldReferenceEntries(listOf(fieldReference, helperFieldReference), query = "0xA4"))
            .containsExactly(fieldReference)
        assertThat(filterDexClassDefEntries(listOf(mainClass, utilClass), query = "", limit = 1))
            .containsExactly(mainClass)
        assertThat(filterDexTypeEntries(listOf(mainType, utilType), query = "main", limit = 0))
            .isEmpty()
    }

    @Test
    fun dexClassDataMethodExecutionKind_shouldClassifyAccessFlagsAndCodeOffset() {
        assertThat(dexClassDataMethodExecutionKind(accessFlags = 0L, codeOffset = 0x218L))
            .isEqualTo(HexDexClassDataMethodExecutionKind.CODE)
        assertThat(dexClassDataMethodExecutionKind(accessFlags = 0x0100L, codeOffset = 0L))
            .isEqualTo(HexDexClassDataMethodExecutionKind.NATIVE)
        assertThat(dexClassDataMethodExecutionKind(accessFlags = 0x0400L, codeOffset = 0L))
            .isEqualTo(HexDexClassDataMethodExecutionKind.ABSTRACT)
        assertThat(dexClassDataMethodExecutionKind(accessFlags = 0L, codeOffset = 0L))
            .isEqualTo(HexDexClassDataMethodExecutionKind.NO_CODE)
        assertThat(dexClassDataMethodExecutionKind(accessFlags = 0x0100L, codeOffset = 0x218L))
            .isEqualTo(HexDexClassDataMethodExecutionKind.NATIVE)
    }

    @Test
    fun filterElfSections_shouldFilterByRoleQueryAddressAndLimit() {
        val stringTable = elfSection(
            index = 1,
            name = ".dynstr",
            type = 3L,
            fileOffset = 0x300L,
            size = 64L
        )
        val executable = elfSection(
            index = 2,
            name = ".text",
            flags = 0x6L,
            virtualAddress = 0x400200L,
            fileOffset = 0x200L,
            size = 128L
        )
        val writable = elfSection(
            index = 3,
            name = ".data",
            flags = 0x3L,
            virtualAddress = 0x500100L,
            fileOffset = 0x500L,
            size = 32L
        )
        val dynamicSymbols = elfSection(
            index = 4,
            name = ".dynsym",
            type = 11L,
            fileOffset = 0x340L,
            size = 96L
        )
        val sections = listOf(stringTable, executable, writable, dynamicSymbols)

        assertThat(filterElfSections(sections, query = "text", sectionFilter = ElfSectionFilter.EXECUTABLE))
            .containsExactly(executable)
        assertThat(filterElfSections(sections, query = "0x500", sectionFilter = ElfSectionFilter.WRITABLE))
            .containsExactly(writable)
        assertThat(filterElfSections(sections, query = "", sectionFilter = ElfSectionFilter.STRING_TABLE))
            .containsExactly(stringTable)
        assertThat(filterElfSections(sections, query = "", sectionFilter = ElfSectionFilter.SYMBOL_TABLE))
            .containsExactly(dynamicSymbols)
        assertThat(filterElfSections(sections, query = "", limit = 2))
            .containsExactly(stringTable, executable)
            .inOrder()
        assertThat(filterElfSections(sections, query = "data", limit = 0)).isEmpty()
    }

    @Test
    fun filterElfSymbols_shouldFilterByRoleQueryAddressAndLimit() {
        val imported = elfSymbol(name = "puts", isImported = true)
        val exported = elfSymbol(
            name = "native_entry",
            value = 0x400320L,
            fileOffset = 0x320L,
            isExported = true
        )
        val jni = elfSymbol(
            name = "JNI_OnLoad",
            value = 0x4003D0L,
            fileOffset = 0x3D0L,
            isExported = true,
            isJni = true,
            sectionName = ".rodata",
            sectionFileOffset = 0x3D0L,
            sectionSize = 16L
        )
        val symbols = listOf(imported, exported, jni)

        assertThat(filterElfSymbols(symbols, query = "jni", symbolFilter = ElfSymbolFilter.JNI))
            .containsExactly(jni)
        assertThat(filterElfSymbols(symbols, query = "0x3d0", symbolFilter = ElfSymbolFilter.EXPORTED))
            .containsExactly(jni)
        assertThat(filterElfSymbols(symbols, query = ".rodata", symbolFilter = ElfSymbolFilter.JNI))
            .containsExactly(jni)
        assertThat(filterElfSymbols(symbols, query = "", symbolFilter = ElfSymbolFilter.IMPORTED))
            .containsExactly(imported)
        assertThat(filterElfSymbols(symbols, query = "", limit = 2))
            .containsExactly(imported, exported)
            .inOrder()
        assertThat(filterElfSymbols(symbols, query = "native", limit = 0)).isEmpty()
    }

    @Test
    fun analyzeHexBinaryFile_shouldWarnWhenElfHardeningIsIncomplete() {
        runBlocking {
            val file = tempBinaryFile(
                "demo.so",
                minimalElf64Aarch64(
                    includeRelro = false,
                    executableStack = true,
                    bindNow = false
                )
            )

            val analysis = analyzeHexBinaryFile(file)
            val elf = analysis.elf!!

            assertThat(elf.hardeningChecks.first { it.type == HexElfHardeningType.NX }.enabled).isFalse()
            assertThat(elf.hardeningChecks.first { it.type == HexElfHardeningType.RELRO }.enabled).isFalse()
            assertThat(elf.hardeningChecks.first { it.type == HexElfHardeningType.BIND_NOW }.enabled).isFalse()
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.ELF_HARDENING_WARNING)
        }
    }

    @Test
    fun analyzeHexBinaryFile_shouldReportElfRiskFindings() {
        runBlocking {
            val file = tempBinaryFile(
                "demo.so",
                minimalElf64Aarch64(
                    includeRelro = false,
                    executableStack = true,
                    bindNow = false,
                    rwxLoadSegment = true,
                    writableExecutableSection = true
                )
            )

            val analysis = analyzeHexBinaryFile(file)
            val elf = analysis.elf!!

            assertThat(elf.riskFindings.map { it.type })
                .containsAtLeast(
                    HexElfRiskFindingType.RWX_LOAD_SEGMENT,
                    HexElfRiskFindingType.WRITABLE_EXECUTABLE_SECTION,
                    HexElfRiskFindingType.EXECUTABLE_STACK,
                    HexElfRiskFindingType.MISSING_RELRO,
                    HexElfRiskFindingType.MISSING_BIND_NOW,
                    HexElfRiskFindingType.RUNPATH_PRESENT
                )
            assertThat(elf.riskFindings.filter { it.severity == HexElfRiskSeverity.HIGH }.map { it.type })
                .containsAtLeast(
                    HexElfRiskFindingType.RWX_LOAD_SEGMENT,
                    HexElfRiskFindingType.WRITABLE_EXECUTABLE_SECTION,
                    HexElfRiskFindingType.EXECUTABLE_STACK
                )
            assertThat(elf.riskFindings.first { it.type == HexElfRiskFindingType.RWX_LOAD_SEGMENT }.evidenceFileOffset)
                .isEqualTo(0x40L)
            assertThat(
                elf.riskFindings.first {
                    it.type == HexElfRiskFindingType.WRITABLE_EXECUTABLE_SECTION
                }.evidenceFileOffset
            ).isEqualTo(0x3D0L)
            assertThat(elf.riskFindings.first { it.type == HexElfRiskFindingType.EXECUTABLE_STACK }.evidenceFileOffset)
                .isEqualTo(0xE8L)
            assertThat(elf.riskFindings.first { it.type == HexElfRiskFindingType.MISSING_RELRO }.evidenceFileOffset)
                .isNull()
            assertThat(elf.riskFindings.first { it.type == HexElfRiskFindingType.MISSING_BIND_NOW }.evidenceFileOffset)
                .isNull()
            assertThat(elf.riskFindings.first { it.type == HexElfRiskFindingType.RUNPATH_PRESENT }.evidenceFileOffset)
                .isEqualTo(0x5A0L)
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.ELF_RISK_FINDINGS)
            assertThat(analysis.signals.first { it.type == HexAnalysisSignalType.ELF_RISK_FINDINGS }.offset)
                .isEqualTo(0x40L)
        }
    }

    @Test
    fun filterElfDynamicEntries_shouldFilterByTypeQueryOffsetAndLimit() {
        val needed = HexElfDynamicStringEntry(
            index = 0,
            type = HexElfDynamicStringType.NEEDED,
            value = "libc.so",
            entryFileOffset = 0x580L,
            loadOrder = 1,
            semantic = HexElfDynamicStringSemantic.NEEDED_LIBRARY_LOAD
        )
        val soname = HexElfDynamicStringEntry(
            index = 1,
            type = HexElfDynamicStringType.SONAME,
            value = "libdemo.so",
            entryFileOffset = 0x590L,
            semantic = HexElfDynamicStringSemantic.SONAME_IDENTITY
        )
        val runpath = HexElfDynamicStringEntry(
            index = 2,
            type = HexElfDynamicStringType.RUNPATH,
            value = "\$ORIGIN",
            entryFileOffset = 0x5A0L,
            semantic = HexElfDynamicStringSemantic.RUNPATH_SEARCH
        )
        val entries = listOf(needed, soname, runpath)

        assertThat(filterElfDynamicEntries(entries, query = "libc", dynamicEntryFilter = ElfDynamicEntryFilter.NEEDED))
            .containsExactly(needed)
        assertThat(filterElfDynamicEntries(entries, query = "SONAME"))
            .containsExactly(soname)
        assertThat(filterElfDynamicEntries(entries, query = "0x5a0", dynamicEntryFilter = ElfDynamicEntryFilter.RUNPATH))
            .containsExactly(runpath)
        assertThat(filterElfDynamicEntries(entries, query = "declaration order"))
            .containsExactly(needed)
        assertThat(filterElfDynamicEntries(entries, query = "identity"))
            .containsExactly(soname)
        assertThat(filterElfDynamicEntries(entries, query = "dependency search"))
            .containsExactly(runpath)
        assertThat(filterElfDynamicEntries(entries, query = "", limit = 2))
            .containsExactly(needed, soname)
        assertThat(filterElfDynamicEntries(entries, query = "lib", limit = 0)).isEmpty()
    }

    @Test
    fun filterElfDynamicFlags_shouldFilterByBindNowTypeQueryOffsetAndLimit() {
        val bindNow = HexElfDynamicFlagEntry(
            index = 3,
            type = HexElfDynamicFlagType.BIND_NOW,
            value = 0L,
            entryFileOffset = 0x5B0L,
            isBindNow = true
        )
        val flags = HexElfDynamicFlagEntry(
            index = 4,
            type = HexElfDynamicFlagType.FLAGS,
            value = 0x8L,
            entryFileOffset = 0x5C0L,
            isBindNow = true
        )
        val flags1 = HexElfDynamicFlagEntry(
            index = 5,
            type = HexElfDynamicFlagType.FLAGS_1,
            value = 0x1L,
            entryFileOffset = 0x5D0L,
            isBindNow = true
        )
        val entries = listOf(bindNow, flags, flags1)

        assertThat(filterElfDynamicFlags(entries, query = "BIND_NOW", dynamicFlagFilter = ElfDynamicFlagFilter.BIND_NOW))
            .containsExactly(bindNow, flags, flags1)
            .inOrder()
        assertThat(filterElfDynamicFlags(entries, query = "FLAGS_1"))
            .containsExactly(flags1)
        assertThat(filterElfDynamicFlags(entries, query = "0x5c0", dynamicFlagFilter = ElfDynamicFlagFilter.FLAGS))
            .containsExactly(flags)
        assertThat(filterElfDynamicFlags(entries, query = "", limit = 2))
            .containsExactly(bindNow, flags)
        assertThat(filterElfDynamicFlags(entries, query = "NOW", limit = 0)).isEmpty()
    }

    @Test
    fun filterElfNotes_shouldFilterByBuildIdNameQueryOffsetAndLimit() {
        val buildId = HexElfNoteEntry(
            index = 0,
            sectionName = ".note.gnu.build-id",
            name = "GNU",
            type = 3L,
            noteFileOffset = 0x460L,
            descriptionOffset = 0x470L,
            descriptionSize = 20L,
            descriptionHex = "0123456789abcdeffedcba9876543210aabbccdd",
            descriptionText = null,
            isBuildId = true
        )
        val androidNote = HexElfNoteEntry(
            index = 1,
            sectionName = ".note.android.ident",
            name = "Android",
            type = 1L,
            noteFileOffset = 0x490L,
            descriptionOffset = 0x4A0L,
            descriptionSize = 6L,
            descriptionHex = "6170692d3234",
            descriptionText = "api-24",
            isBuildId = false
        )
        val notes = listOf(buildId, androidNote)

        assertThat(filterElfNotes(notes, query = "", noteFilter = ElfNoteFilter.BUILD_ID))
            .containsExactly(buildId)
        assertThat(filterElfNotes(notes, query = "GNU"))
            .containsExactly(buildId)
        assertThat(filterElfNotes(notes, query = "0x4a0", noteFilter = ElfNoteFilter.ANDROID))
            .containsExactly(androidNote)
        assertThat(filterElfNotes(notes, query = "api-24"))
            .containsExactly(androidNote)
        assertThat(filterElfNotes(notes, query = "", limit = 1))
            .containsExactly(buildId)
        assertThat(filterElfNotes(notes, query = "build", limit = 0)).isEmpty()
    }

    @Test
    fun filterElfProgramHeaders_shouldFilterByRoleQueryAddressAndLimit() {
        val load = HexElfProgramHeader(
            index = 0,
            type = 1L,
            typeName = "LOAD",
            programHeaderFileOffset = 0x40L,
            fileOffset = 0L,
            virtualAddress = 0x400000L,
            physicalAddress = 0x400000L,
            fileSize = 0x900L,
            memorySize = 0x900L,
            flags = 0x5,
            align = 0x1000L
        )
        val dynamic = HexElfProgramHeader(
            index = 1,
            type = 2L,
            typeName = "DYNAMIC",
            programHeaderFileOffset = 0x78L,
            fileOffset = 0x580L,
            virtualAddress = 0x400580L,
            physicalAddress = 0x400580L,
            fileSize = 0x40L,
            memorySize = 0x40L,
            flags = 0x6,
            align = 0x8L
        )
        val relro = HexElfProgramHeader(
            index = 2,
            type = 0x6474E552L,
            typeName = "GNU_RELRO",
            programHeaderFileOffset = 0xB0L,
            fileOffset = 0x4A0L,
            virtualAddress = 0x4004A0L,
            physicalAddress = 0x4004A0L,
            fileSize = 0x100L,
            memorySize = 0x100L,
            flags = 0x4,
            align = 0x8L
        )
        val programHeaders = listOf(load, dynamic, relro)

        assertThat(
            filterElfProgramHeaders(
                programHeaders,
                query = "0x400000",
                programHeaderFilter = ElfProgramHeaderFilter.EXECUTABLE
            )
        ).containsExactly(load)
        assertThat(filterElfProgramHeaders(programHeaders, query = "DYNAMIC"))
            .containsExactly(dynamic)
        assertThat(filterElfProgramHeaders(programHeaders, query = "", programHeaderFilter = ElfProgramHeaderFilter.HARDENING))
            .containsExactly(relro)
        assertThat(filterElfProgramHeaders(programHeaders, query = "", limit = 2))
            .containsExactly(load, dynamic)
        assertThat(filterElfProgramHeaders(programHeaders, query = "LOAD", limit = 0)).isEmpty()
    }

    @Test
    fun filterElfSectionSegmentMappings_shouldFilterBySegmentFlagsQueryAddressAndLimit() {
        val executable = HexElfSectionSegmentMapping(
            index = 0,
            sectionIndex = 2,
            sectionName = ".text",
            sectionFileOffset = 0x200L,
            sectionSize = 128L,
            sectionVirtualAddress = 0x400200L,
            segmentIndex = 0,
            segmentTypeName = "LOAD",
            segmentFileOffset = 0L,
            segmentFileSize = 0x800L,
            segmentVirtualAddress = 0x400000L,
            segmentMemorySize = 0x800L,
            segmentFlags = 0x5,
            isExecutable = true,
            isWritable = false,
            isReadable = true
        )
        val writable = HexElfSectionSegmentMapping(
            index = 1,
            sectionIndex = 3,
            sectionName = ".data",
            sectionFileOffset = 0x500L,
            sectionSize = 32L,
            sectionVirtualAddress = 0x500100L,
            segmentIndex = 1,
            segmentTypeName = "LOAD",
            segmentFileOffset = 0x500L,
            segmentFileSize = 0x100L,
            segmentVirtualAddress = 0x500000L,
            segmentMemorySize = 0x100L,
            segmentFlags = 0x6,
            isExecutable = false,
            isWritable = true,
            isReadable = true
        )
        val mappings = listOf(executable, writable)

        assertThat(
            filterElfSectionSegmentMappings(
                mappings,
                query = "text",
                sectionSegmentFilter = ElfSectionSegmentFilter.EXECUTABLE
            )
        ).containsExactly(executable)
        assertThat(
            filterElfSectionSegmentMappings(
                mappings,
                query = "0x500100",
                sectionSegmentFilter = ElfSectionSegmentFilter.WRITABLE
            )
        ).containsExactly(writable)
        assertThat(filterElfSectionSegmentMappings(mappings, query = "RW"))
            .containsExactly(writable)
        assertThat(filterElfSectionSegmentMappings(mappings, query = "", sectionSegmentFilter = ElfSectionSegmentFilter.READABLE, limit = 1))
            .containsExactly(executable)
        assertThat(filterElfSectionSegmentMappings(mappings, query = "data", limit = 0)).isEmpty()
    }

    @Test
    fun filterElfRelocations_shouldFilterByRoleQueryAddressAndLimit() {
        val pltRelocation = elfRelocation(
            sectionName = ".rela.plt",
            symbolName = "puts",
            relocationFileOffset = 0x410L,
            offsetAddress = 0x4004A0L,
            offsetFileOffset = 0x4A0L,
            type = 1026L,
            targetSectionName = ".got.plt",
            isSymbolImported = true
        )
        val dynamicRelocation = elfRelocation(
            sectionName = ".rela.dyn",
            symbolName = "JNI_OnLoad",
            relocationFileOffset = 0x450L,
            offsetAddress = 0x4004C0L,
            offsetFileOffset = 0x4C0L,
            type = 1027L
        )
        val relocations = listOf(pltRelocation, dynamicRelocation)

        assertThat(filterElfRelocations(relocations, query = "puts", relocationFilter = ElfRelocationFilter.PLT))
            .containsExactly(pltRelocation)
        assertThat(filterElfRelocations(relocations, query = "0x4c0", relocationFilter = ElfRelocationFilter.DYNAMIC))
            .containsExactly(dynamicRelocation)
        assertThat(filterElfRelocations(relocations, query = "", limit = 1))
            .containsExactly(pltRelocation)
        assertThat(filterElfRelocations(relocations, query = "JUMP_SLOT"))
            .containsExactly(pltRelocation)
        assertThat(filterElfRelocations(relocations, query = ".got.plt"))
            .containsExactly(pltRelocation)
        assertThat(filterElfRelocations(relocations, query = "imported"))
            .containsExactly(pltRelocation)
        assertThat(filterElfRelocations(relocations, query = "FUNC", relocationFilter = ElfRelocationFilter.PLT))
            .containsExactly(pltRelocation)
        assertThat(filterElfRelocations(relocations, query = "load bias"))
            .containsExactly(dynamicRelocation)
        assertThat(filterElfRelocations(relocations, query = "jni", limit = 0)).isEmpty()
    }

    @Test
    fun filterElfLinkageEntries_shouldFilterByRoleModeQueryAddressAndLimit() {
        val pltEntry = HexElfLinkageEntry(
            index = 0,
            symbolName = "puts",
            symbolIndex = 1L,
            relocationSectionName = ".rela.plt",
            relocationTypeName = "AARCH64_JUMP_SLOT",
            relocationFileOffset = 0x410L,
            slotAddress = 0x4004A0L,
            slotFileOffset = 0x4A0L,
            slotSectionName = ".got.plt",
            symbolBinding = HexElfSymbolBinding.GLOBAL,
            symbolType = HexElfSymbolType.FUNC,
            isImported = true,
            isExported = false,
            isJni = false,
            entryKind = HexElfLinkageEntryKind.PLT,
            bindingMode = HexElfLinkageBindingMode.LAZY,
            resolutionSemantic = HexElfLinkageResolutionSemantic.LAZY_PLT_CALL
        )
        val jniEntry = HexElfLinkageEntry(
            index = 1,
            symbolName = "JNI_OnLoad",
            symbolIndex = 2L,
            relocationSectionName = ".rela.dyn",
            relocationTypeName = "AARCH64_RELATIVE",
            relocationFileOffset = 0x450L,
            slotAddress = 0x4004C0L,
            slotFileOffset = 0x4C0L,
            slotSectionName = ".got",
            symbolBinding = HexElfSymbolBinding.GLOBAL,
            symbolType = HexElfSymbolType.FUNC,
            isImported = false,
            isExported = true,
            isJni = true,
            entryKind = HexElfLinkageEntryKind.RELATIVE,
            bindingMode = HexElfLinkageBindingMode.LOCAL,
            resolutionSemantic = HexElfLinkageResolutionSemantic.RELATIVE_REBASE
        )
        val entries = listOf(pltEntry, jniEntry)

        assertThat(filterElfLinkageEntries(entries, query = "", linkageFilter = ElfLinkageFilter.IMPORTS))
            .containsExactly(pltEntry)
        assertThat(filterElfLinkageEntries(entries, query = "JUMP_SLOT", linkageFilter = ElfLinkageFilter.PLT))
            .containsExactly(pltEntry)
        assertThat(filterElfLinkageEntries(entries, query = "0x4c0", linkageFilter = ElfLinkageFilter.GOT))
            .containsExactly(jniEntry)
        assertThat(filterElfLinkageEntries(entries, query = "jni", linkageFilter = ElfLinkageFilter.JNI))
            .containsExactly(jniEntry)
        assertThat(filterElfLinkageEntries(entries, query = "lazy", linkageFilter = ElfLinkageFilter.LAZY))
            .containsExactly(pltEntry)
        assertThat(filterElfLinkageEntries(entries, query = "rebase"))
            .containsExactly(jniEntry)
        assertThat(filterElfLinkageEntries(entries, query = "", limit = 1))
            .containsExactly(pltEntry)
        assertThat(filterElfLinkageEntries(entries, query = "puts", limit = 0)).isEmpty()
    }

    @Test
    fun filterElfDynamicLinkerSteps_shouldFilterByTypeQueryOffsetAndLimit() {
        val loading = HexElfDynamicLinkerStep(
            index = 0,
            type = HexElfDynamicLinkerStepType.LOAD_NEEDED_LIBRARIES,
            evidenceFileOffset = 0x580L,
            relatedCount = 1,
            detailValue = "libc.so"
        )
        val relocation = HexElfDynamicLinkerStep(
            index = 1,
            type = HexElfDynamicLinkerStepType.APPLY_RELOCATIONS,
            evidenceFileOffset = 0x410L,
            relatedCount = 2,
            detailValue = "puts, JNI_OnLoad"
        )
        val lazyBinding = HexElfDynamicLinkerStep(
            index = 2,
            type = HexElfDynamicLinkerStepType.ENABLE_LAZY_PLT,
            evidenceFileOffset = 0x4A0L,
            relatedCount = 1,
            detailValue = "puts"
        )
        val relro = HexElfDynamicLinkerStep(
            index = 3,
            type = HexElfDynamicLinkerStepType.PROTECT_RELRO,
            evidenceFileOffset = 0xB0L,
            relatedCount = 1,
            detailValue = null
        )
        val jni = HexElfDynamicLinkerStep(
            index = 4,
            type = HexElfDynamicLinkerStepType.EXPOSE_JNI_ENTRYPOINTS,
            evidenceFileOffset = 0x3D0L,
            relatedCount = 2,
            detailValue = "JNI_OnLoad, Java_com_example_Native_call"
        )
        val steps = listOf(loading, relocation, lazyBinding, relro, jni)

        assertThat(filterElfDynamicLinkerSteps(steps, query = "libc", stepFilter = ElfDynamicLinkerStepFilter.LOADING))
            .containsExactly(loading)
        assertThat(filterElfDynamicLinkerSteps(steps, query = "0x410", stepFilter = ElfDynamicLinkerStepFilter.RELOCATIONS))
            .containsExactly(relocation)
        assertThat(filterElfDynamicLinkerSteps(steps, query = "lazy", stepFilter = ElfDynamicLinkerStepFilter.BINDING))
            .containsExactly(lazyBinding)
        assertThat(filterElfDynamicLinkerSteps(steps, query = "relro", stepFilter = ElfDynamicLinkerStepFilter.HARDENING))
            .containsExactly(relro)
        assertThat(filterElfDynamicLinkerSteps(steps, query = "Java_", stepFilter = ElfDynamicLinkerStepFilter.ENTRYPOINTS))
            .containsExactly(jni)
        assertThat(filterElfDynamicLinkerSteps(steps, query = "", limit = 2))
            .containsExactly(loading, relocation)
        assertThat(filterElfDynamicLinkerSteps(steps, query = "puts", limit = 0)).isEmpty()
    }

    @Test
    fun filterElfRiskFindings_shouldFilterBySeverityCategoryQueryOffsetAndLimit() {
        val rwx = HexElfRiskFinding(
            index = 0,
            type = HexElfRiskFindingType.RWX_LOAD_SEGMENT,
            severity = HexElfRiskSeverity.HIGH,
            evidenceFileOffset = 0x40L,
            detailValue = "LOAD"
        )
        val relro = HexElfRiskFinding(
            index = 1,
            type = HexElfRiskFindingType.MISSING_RELRO,
            severity = HexElfRiskSeverity.WARNING,
            evidenceFileOffset = null,
            detailValue = "PT_GNU_RELRO"
        )
        val rpath = HexElfRiskFinding(
            index = 2,
            type = HexElfRiskFindingType.LEGACY_RPATH,
            severity = HexElfRiskSeverity.WARNING,
            evidenceFileOffset = 0x5A0L,
            detailValue = "\$ORIGIN"
        )
        val soname = HexElfRiskFinding(
            index = 3,
            type = HexElfRiskFindingType.MISSING_SONAME,
            severity = HexElfRiskSeverity.INFO,
            evidenceFileOffset = null,
            detailValue = "DT_SONAME"
        )
        val findings = listOf(rwx, relro, rpath, soname)

        assertThat(filterElfRiskFindings(findings, query = "rwx", riskFilter = ElfRiskFilter.HIGH))
            .containsExactly(rwx)
        assertThat(filterElfRiskFindings(findings, query = "relro", riskFilter = ElfRiskFilter.HARDENING))
            .containsExactly(relro)
        assertThat(filterElfRiskFindings(findings, query = "0x5a0", riskFilter = ElfRiskFilter.PATHS))
            .containsExactly(rpath)
        assertThat(filterElfRiskFindings(findings, query = "soname", riskFilter = ElfRiskFilter.METADATA))
            .containsExactly(soname)
        assertThat(filterElfRiskFindings(findings, query = "", riskFilter = ElfRiskFilter.WARNING))
            .containsExactly(relro, rpath)
            .inOrder()
        assertThat(filterElfRiskFindings(findings, query = "", limit = 2))
            .containsExactly(rwx, relro)
            .inOrder()
        assertThat(filterElfRiskFindings(findings, query = "rpath", limit = 0)).isEmpty()
    }

    @Test
    fun filterElfJniRegistrationHints_shouldFilterByTypeQueryOffsetAndLimit() {
        val registerNatives = HexElfJniRegistrationHint(
            index = 0,
            type = HexElfJniRegistrationHintType.REGISTER_NATIVES_STRING,
            evidenceFileOffset = 0x3D0L,
            stringValue = "RegisterNatives"
        )
        val onLoad = HexElfJniRegistrationHint(
            index = 1,
            type = HexElfJniRegistrationHintType.JNI_ONLOAD_ENTRY,
            evidenceFileOffset = 0x400L,
            symbolName = "JNI_OnLoad"
        )
        val staticExport = HexElfJniRegistrationHint(
            index = 2,
            type = HexElfJniRegistrationHintType.STATIC_JNI_EXPORT,
            evidenceFileOffset = 0x420L,
            symbolName = "Java_com_example_Native_call"
        )
        val descriptor = HexElfJniRegistrationHint(
            index = 3,
            type = HexElfJniRegistrationHintType.JNI_METHOD_SIGNATURE,
            evidenceFileOffset = 0x440L,
            stringValue = "(Ljava/lang/String;)I"
        )
        val hints = listOf(registerNatives, onLoad, staticExport, descriptor)

        assertThat(
            filterElfJniRegistrationHints(
                hints,
                query = "RegisterNatives",
                hintFilter = ElfJniHintFilter.REGISTER_NATIVES
            )
        ).containsExactly(registerNatives)
        assertThat(filterElfJniRegistrationHints(hints, query = "0x400", hintFilter = ElfJniHintFilter.ENTRYPOINTS))
            .containsExactly(onLoad)
        assertThat(filterElfJniRegistrationHints(hints, query = "Java_", hintFilter = ElfJniHintFilter.STATIC_EXPORTS))
            .containsExactly(staticExport)
        assertThat(filterElfJniRegistrationHints(hints, query = "String", hintFilter = ElfJniHintFilter.DESCRIPTORS))
            .containsExactly(descriptor)
        assertThat(filterElfJniRegistrationHints(hints, query = "", limit = 2))
            .containsExactly(registerNatives, onLoad)
            .inOrder()
        assertThat(filterElfJniRegistrationHints(hints, query = "JNI", limit = 0)).isEmpty()
    }

    @Test
    fun buildElfNativeApiHints_shouldClassifyImportedSymbols() {
        val symbols = listOf(
            elfSymbol(name = "dlopen", isImported = true),
            elfSymbol(name = "mprotect", isImported = true),
            elfSymbol(name = "ptrace", isImported = true),
            elfSymbol(name = "socket", isImported = true),
            elfSymbol(name = "EVP_EncryptInit_ex", isImported = true),
            elfSymbol(name = "pthread_create", isImported = true),
            elfSymbol(name = "__android_log_print", isImported = true),
            elfSymbol(name = "Java_com_example_Native_call", isExported = true, isJni = true)
        )

        val hints = buildElfNativeApiHints(symbols)

        assertThat(hints.map { it.category })
            .containsExactly(
                HexElfNativeApiCategory.DYNAMIC_LOADING,
                HexElfNativeApiCategory.MEMORY_PROTECTION,
                HexElfNativeApiCategory.PROCESS_CONTROL,
                HexElfNativeApiCategory.NETWORK,
                HexElfNativeApiCategory.CRYPTO,
                HexElfNativeApiCategory.THREADING,
                HexElfNativeApiCategory.LOGGING
            )
            .inOrder()
        assertThat(hints.map { it.symbolName })
            .doesNotContain("Java_com_example_Native_call")
    }

    @Test
    fun filterElfNativeApiHints_shouldFilterByCategoryQueryOffsetAndLimit() {
        val loader = HexElfNativeApiHint(
            index = 0,
            category = HexElfNativeApiCategory.DYNAMIC_LOADING,
            symbolName = "dlopen",
            evidenceFileOffset = null
        )
        val memory = HexElfNativeApiHint(
            index = 1,
            category = HexElfNativeApiCategory.MEMORY_PROTECTION,
            symbolName = "mprotect",
            evidenceFileOffset = 0x410L
        )
        val process = HexElfNativeApiHint(
            index = 2,
            category = HexElfNativeApiCategory.PROCESS_CONTROL,
            symbolName = "ptrace",
            evidenceFileOffset = null
        )
        val network = HexElfNativeApiHint(
            index = 3,
            category = HexElfNativeApiCategory.NETWORK,
            symbolName = "socket",
            evidenceFileOffset = null
        )
        val crypto = HexElfNativeApiHint(
            index = 4,
            category = HexElfNativeApiCategory.CRYPTO,
            symbolName = "EVP_EncryptInit_ex",
            evidenceFileOffset = null
        )
        val threading = HexElfNativeApiHint(
            index = 5,
            category = HexElfNativeApiCategory.THREADING,
            symbolName = "pthread_create",
            evidenceFileOffset = null
        )
        val logging = HexElfNativeApiHint(
            index = 6,
            category = HexElfNativeApiCategory.LOGGING,
            symbolName = "__android_log_print",
            evidenceFileOffset = null
        )
        val hints = listOf(loader, memory, process, network, crypto, threading, logging)

        assertThat(filterElfNativeApiHints(hints, query = "dlopen", apiFilter = ElfNativeApiFilter.DYNAMIC_LOADING))
            .containsExactly(loader)
        assertThat(filterElfNativeApiHints(hints, query = "0x410", apiFilter = ElfNativeApiFilter.MEMORY))
            .containsExactly(memory)
        assertThat(filterElfNativeApiHints(hints, query = "anti", apiFilter = ElfNativeApiFilter.PROCESS))
            .containsExactly(process)
        assertThat(filterElfNativeApiHints(hints, query = "socket", apiFilter = ElfNativeApiFilter.NETWORK))
            .containsExactly(network)
        assertThat(filterElfNativeApiHints(hints, query = "openssl", apiFilter = ElfNativeApiFilter.CRYPTO))
            .containsExactly(crypto)
        assertThat(filterElfNativeApiHints(hints, query = "pthread", apiFilter = ElfNativeApiFilter.THREADING))
            .containsExactly(threading)
        assertThat(filterElfNativeApiHints(hints, query = "log", apiFilter = ElfNativeApiFilter.LOGGING))
            .containsExactly(logging)
        assertThat(filterElfNativeApiHints(hints, query = "", limit = 2))
            .containsExactly(loader, memory)
            .inOrder()
        assertThat(filterElfNativeApiHints(hints, query = "dlopen", limit = 0)).isEmpty()
    }

    @Test
    fun filterDexEntries_shouldFilterByQueryCategoryOffsetAndLimit() {
        val mainClass = HexDexStringEntry(
            index = 0,
            stringIdOffset = 0x70L,
            dataOffset = 0x120L,
            value = "Lcom/example/MainActivity;"
        )
        val nativeName = HexDexStringEntry(
            index = 1,
            stringIdOffset = 0x74L,
            dataOffset = 0x140L,
            value = "nativeEntry"
        )
        val stringIdMap = HexDexMapEntry(
            index = 0,
            type = 0x0001,
            typeName = "string_id_item",
            size = 2,
            offset = 0x70L,
            entryFileOffset = 0x180L
        )
        val classDataMap = HexDexMapEntry(
            index = 1,
            type = 0x2000,
            typeName = "class_data_item",
            size = 1,
            offset = 0x160L,
            entryFileOffset = 0x18CL
        )
        val codeMap = HexDexMapEntry(
            index = 2,
            type = 0x2001,
            typeName = "code_item",
            size = 3,
            offset = 0x190L,
            entryFileOffset = 0x198L
        )

        assertThat(filterDexStringEntries(listOf(mainClass, nativeName), query = "main"))
            .containsExactly(mainClass)
        assertThat(filterDexStringEntries(listOf(mainClass, nativeName), query = "0x140"))
            .containsExactly(nativeName)
        assertThat(filterDexStringEntries(listOf(mainClass, nativeName), query = "", limit = 1))
            .containsExactly(mainClass)
        assertThat(
            filterDexMapEntries(
                listOf(stringIdMap, classDataMap, codeMap),
                query = "string",
                mapFilter = DexMapEntryFilter.IDS
            )
        ).containsExactly(stringIdMap)
        assertThat(filterDexMapEntries(listOf(stringIdMap, classDataMap, codeMap), query = "0x160"))
            .containsExactly(classDataMap)
        assertThat(filterDexMapEntries(listOf(stringIdMap, classDataMap, codeMap), query = "", mapFilter = DexMapEntryFilter.CODE))
            .containsExactly(codeMap)
    }

    @Test
    fun filterArchiveEntries_shouldFilterByCategoryQueryOffsetAndLimit() {
        val manifest = archiveEntry(index = 0, name = "AndroidManifest.xml", localHeaderOffset = 0L)
        val dex = archiveEntry(index = 1, name = "classes.dex", localHeaderOffset = 0x40L)
        val nativeLibrary = archiveEntry(
            index = 2,
            name = "lib/arm64-v8a/libdemo.so",
            localHeaderOffset = 0x80L,
            dataOffset = 0x1000L,
            compressionMethod = ZipEntry.STORED
        )
        val resource = archiveEntry(
            index = 3,
            name = "res/raw/payload.bin",
            localHeaderOffset = 0xC0L,
            dataOffset = 0xE0L,
            crc32 = 0x1234L
        )
        val signature = archiveEntry(index = 4, name = "META-INF/CERT.RSA", localHeaderOffset = 0x100L)
        val overlapping = archiveEntry(
            index = 5,
            name = "assets/overlap.bin",
            localHeaderOffset = 0x140L,
            dataOffset = 0x380L,
            compressedSize = 0x80L,
            centralDirectoryOffset = 0x3C0L,
            dataRangeStatus = HexArchiveEntryDataRangeStatus.OVERLAPS_CENTRAL_DIRECTORY
        )
        val localMismatch = archiveEntry(
            index = 6,
            name = "assets/central.bin",
            localHeaderOffset = 0x180L,
            localHeaderName = "assets/local.bin"
        )
        val entries = listOf(manifest, dex, nativeLibrary, resource, signature, overlapping, localMismatch)

        assertThat(filterArchiveEntries(entries, query = "manifest", archiveFilter = ArchiveEntryFilter.MANIFEST))
            .containsExactly(manifest)
        assertThat(filterArchiveEntries(entries, query = "classes", archiveFilter = ArchiveEntryFilter.DEX))
            .containsExactly(dex)
        assertThat(filterArchiveEntries(entries, query = "libdemo", archiveFilter = ArchiveEntryFilter.NATIVE_LIBRARIES))
            .containsExactly(nativeLibrary)
        assertThat(filterArchiveEntries(entries, query = "mmap", archiveFilter = ArchiveEntryFilter.NATIVE_LIBRARIES))
            .containsExactly(nativeLibrary)
        assertThat(filterArchiveEntries(entries, query = "stored", archiveFilter = ArchiveEntryFilter.NATIVE_LIBRARIES))
            .containsExactly(nativeLibrary)
        assertThat(filterArchiveEntries(entries, query = "0xc0", archiveFilter = ArchiveEntryFilter.RESOURCES))
            .containsExactly(resource)
        assertThat(filterArchiveEntries(entries, query = "0xe0", archiveFilter = ArchiveEntryFilter.RESOURCES))
            .containsExactly(resource)
        assertThat(filterArchiveEntries(entries, query = "0x1234"))
            .containsExactly(resource)
        assertThat(filterArchiveEntries(entries, query = "0x400"))
            .containsExactly(overlapping)
        assertThat(filterArchiveEntries(entries, query = "overlaps central"))
            .containsExactly(overlapping)
        assertThat(filterArchiveEntries(entries, query = "local mismatch"))
            .containsExactly(localMismatch)
        assertThat(filterArchiveEntries(entries, query = "local.bin"))
            .containsExactly(localMismatch)
        assertThat(filterArchiveEntries(entries, query = "cert", archiveFilter = ArchiveEntryFilter.SIGNATURE))
            .containsExactly(signature)
        assertThat(filterArchiveEntries(entries, query = "", limit = 2))
            .containsExactly(manifest, dex)
            .inOrder()

        val v2Block = HexArchiveSigningBlockEntry(
            index = 0,
            id = 0x7109871AL,
            idName = "APK Signature Scheme v2",
            valueSize = 4L,
            blockOffset = 0x180L,
            blockSize = 48L,
            pairOffset = 0x188L,
            valueOffset = 0x194L
        )
        val unknownBlock = v2Block.copy(
            index = 1,
            id = 0x12345678L,
            idName = "id_0x12345678",
            valueSize = 12L,
            pairOffset = 0x1A0L,
            valueOffset = 0x1ACL
        )
        assertThat(filterArchiveSigningBlockEntries(listOf(v2Block, unknownBlock), query = "v2"))
            .containsExactly(v2Block)
        assertThat(filterArchiveSigningBlockEntries(listOf(v2Block, unknownBlock), query = "0x194"))
            .containsExactly(v2Block)
        assertThat(filterArchiveSigningBlockEntries(listOf(v2Block, unknownBlock), query = "12345678"))
            .containsExactly(unknownBlock)
    }

    @Test
    fun archiveEntryDataRangeStatus_shouldClassifyCompressedDataRanges() {
        assertThat(archiveEntryDataEndOffset(0x1000L, 0x20L)).isEqualTo(0x1020L)
        assertThat(archiveEntryDataEndOffset(null, 0x20L)).isNull()

        assertThat(
            archiveEntryDataRangeStatus(
                dataOffset = 0x1000L,
                dataEndOffset = 0x1020L,
                centralDirectoryOffset = 0x2000L,
                fileSize = 0x3000L
            )
        ).isEqualTo(HexArchiveEntryDataRangeStatus.OK)
        assertThat(
            archiveEntryDataRangeStatus(
                dataOffset = null,
                dataEndOffset = null,
                centralDirectoryOffset = 0x2000L,
                fileSize = 0x3000L
            )
        ).isEqualTo(HexArchiveEntryDataRangeStatus.UNKNOWN)
        assertThat(
            archiveEntryDataRangeStatus(
                dataOffset = 0x1000L,
                dataEndOffset = 0x4000L,
                centralDirectoryOffset = 0x2000L,
                fileSize = 0x3000L
            )
        ).isEqualTo(HexArchiveEntryDataRangeStatus.OUT_OF_FILE)
        assertThat(
            archiveEntryDataRangeStatus(
                dataOffset = 0x1000L,
                dataEndOffset = 0x2400L,
                centralDirectoryOffset = 0x2000L,
                fileSize = 0x3000L
            )
        ).isEqualTo(HexArchiveEntryDataRangeStatus.OVERLAPS_CENTRAL_DIRECTORY)
    }

    @Test
    fun archiveEntryLocalHeaderConsistency_shouldClassifyCentralAndLocalMismatches() {
        assertThat(
            archiveEntryLocalHeaderConsistency(
                centralName = "classes.dex",
                centralGeneralPurposeBitFlag = 0x08,
                centralCompressionMethod = ZipEntry.DEFLATED,
                localName = "classes.dex",
                localGeneralPurposeBitFlag = 0x08,
                localCompressionMethod = ZipEntry.DEFLATED
            )
        ).isEqualTo(HexArchiveEntryLocalHeaderConsistency.OK)
        assertThat(
            archiveEntryLocalHeaderConsistency(
                centralName = "classes.dex",
                centralGeneralPurposeBitFlag = 0x08,
                centralCompressionMethod = ZipEntry.DEFLATED,
                localName = null,
                localGeneralPurposeBitFlag = null,
                localCompressionMethod = null
            )
        ).isEqualTo(HexArchiveEntryLocalHeaderConsistency.UNKNOWN)
        assertThat(
            archiveEntryLocalHeaderConsistency(
                centralName = "classes.dex",
                centralGeneralPurposeBitFlag = 0x08,
                centralCompressionMethod = ZipEntry.DEFLATED,
                localName = "classes2.dex",
                localGeneralPurposeBitFlag = 0x08,
                localCompressionMethod = ZipEntry.DEFLATED
            )
        ).isEqualTo(HexArchiveEntryLocalHeaderConsistency.NAME_MISMATCH)
        assertThat(
            archiveEntryLocalHeaderConsistency(
                centralName = "classes.dex",
                centralGeneralPurposeBitFlag = 0x08,
                centralCompressionMethod = ZipEntry.DEFLATED,
                localName = "classes.dex",
                localGeneralPurposeBitFlag = 0,
                localCompressionMethod = ZipEntry.STORED
            )
        ).isEqualTo(HexArchiveEntryLocalHeaderConsistency.METADATA_MISMATCH)
        assertThat(
            archiveEntryLocalHeaderConsistency(
                centralName = "classes.dex",
                centralGeneralPurposeBitFlag = 0x08,
                centralCompressionMethod = ZipEntry.DEFLATED,
                localName = "classes2.dex",
                localGeneralPurposeBitFlag = 0,
                localCompressionMethod = ZipEntry.STORED
            )
        ).isEqualTo(HexArchiveEntryLocalHeaderConsistency.MULTIPLE_MISMATCHES)
    }

    @Test
    fun archiveEntryNameRisks_shouldClassifyUnsafeArchiveEntryNames() {
        assertThat(archiveEntryNameRisks("classes.dex")).isEmpty()
        assertThat(archiveEntryNameRisks("", occurrenceCount = 2))
            .containsExactly(HexArchiveEntryNameRisk.EMPTY_NAME, HexArchiveEntryNameRisk.DUPLICATE_NAME)
        assertThat(archiveEntryNameRisks("/assets/payload.bin"))
            .containsExactly(HexArchiveEntryNameRisk.ABSOLUTE_PATH)
        assertThat(archiveEntryNameRisks("C:\\payload.bin"))
            .containsExactly(
                HexArchiveEntryNameRisk.WINDOWS_DRIVE_PATH,
                HexArchiveEntryNameRisk.BACKSLASH_SEPARATOR
            )
        assertThat(archiveEntryNameRisks("assets\\..\\payload.bin"))
            .containsExactly(
                HexArchiveEntryNameRisk.PATH_TRAVERSAL,
                HexArchiveEntryNameRisk.BACKSLASH_SEPARATOR
            )
    }

    @Test
    fun archiveNativeLoadMode_shouldClassifyCompressionAndPageAlignment() {
        assertThat(archiveNativeLoadMode(ZipEntry.STORED, 0x1000L))
            .isEqualTo(HexArchiveNativeLoadMode.DIRECT_MMAP_READY)
        assertThat(archiveNativeLoadMode(ZipEntry.STORED, 0x1001L))
            .isEqualTo(HexArchiveNativeLoadMode.STORED_UNALIGNED)
        assertThat(archiveNativeLoadMode(ZipEntry.STORED, null))
            .isEqualTo(HexArchiveNativeLoadMode.UNKNOWN)
        assertThat(archiveNativeLoadMode(ZipEntry.DEFLATED, 0x1000L))
            .isEqualTo(HexArchiveNativeLoadMode.NEEDS_DECOMPRESSION)
        assertThat(archiveNativePageAlignmentRemainder(0x1001L)).isEqualTo(1L)
        assertThat(archiveNativePageAlignmentRemainder(null)).isNull()
    }

    @Test
    fun filterArchiveNativeLibrarySummaries_shouldFilterByLoadModeQueryAndOffsets() {
        val directMmap = nativeLibrarySummary(
            entryName = "lib/arm64-v8a/libdirect.so",
            compressionMethod = ZipEntry.STORED,
            dataOffset = 0x1000L,
            loadMode = HexArchiveNativeLoadMode.DIRECT_MMAP_READY
        )
        val compressed = nativeLibrarySummary(
            entryName = "lib/x86/libpacked.so",
            abi = "x86",
            compressionMethod = ZipEntry.DEFLATED,
            dataOffset = 0x1200L,
            loadMode = HexArchiveNativeLoadMode.NEEDS_DECOMPRESSION
        )
        val unaligned = nativeLibrarySummary(
            entryName = "lib/armeabi-v7a/libunaligned.so",
            abi = "armeabi-v7a",
            compressionMethod = ZipEntry.STORED,
            dataOffset = 0x1001L,
            loadMode = HexArchiveNativeLoadMode.STORED_UNALIGNED
        )
        val unknown = nativeLibrarySummary(
            entryName = "lib/mips/libunknown.so",
            abi = "mips",
            compressionMethod = ZipEntry.STORED,
            dataOffset = null,
            loadMode = HexArchiveNativeLoadMode.UNKNOWN
        )
        val entries = listOf(directMmap, compressed, unaligned, unknown)

        assertThat(filterArchiveNativeLibrarySummaries(entries, query = "mmap"))
            .containsExactly(directMmap)
        assertThat(filterArchiveNativeLibrarySummaries(entries, query = "decompression"))
            .containsExactly(compressed)
        assertThat(filterArchiveNativeLibrarySummaries(entries, query = "0x1200"))
            .containsExactly(compressed)
        assertThat(filterArchiveNativeLibrarySummaries(entries, query = "x86"))
            .containsExactly(compressed)
        assertThat(filterArchiveNativeLibrarySummaries(entries, query = "", limit = 1))
            .containsExactly(directMmap)
        assertThat(
            filterArchiveNativeLibrarySummaries(
                entries,
                query = "",
                loadModeFilter = ArchiveNativeLibraryLoadModeFilter.DIRECT_MMAP_READY
            )
        ).containsExactly(directMmap)
        assertThat(
            filterArchiveNativeLibrarySummaries(
                entries,
                query = "",
                loadModeFilter = ArchiveNativeLibraryLoadModeFilter.NEEDS_DECOMPRESSION
            )
        ).containsExactly(compressed)
        assertThat(
            filterArchiveNativeLibrarySummaries(
                entries,
                query = "",
                loadModeFilter = ArchiveNativeLibraryLoadModeFilter.STORED_UNALIGNED
            )
        ).containsExactly(unaligned)
        assertThat(
            filterArchiveNativeLibrarySummaries(
                entries,
                query = "",
                loadModeFilter = ArchiveNativeLibraryLoadModeFilter.UNKNOWN
            )
        ).containsExactly(unknown)
    }

    @Test
    fun filterArchiveDexSummaries_shouldFilterByNameVersionCountsAndOffset() {
        val mainDex = HexArchiveDexSummary(
            entryName = "classes.dex",
            localHeaderOffset = 0x40L,
            analyzedBytes = 0x220L,
            truncated = false,
            dex = dexSummary(version = "035", stringCount = 2, methodCount = 1, classCount = 1)
        )
        val featureDex = HexArchiveDexSummary(
            entryName = "classes2.dex",
            localHeaderOffset = 0x240L,
            analyzedBytes = 0x300L,
            truncated = true,
            dex = dexSummary(version = "039", stringCount = 12, methodCount = 8, classCount = 3)
        )
        val entries = listOf(mainDex, featureDex)

        assertThat(filterArchiveDexSummaries(entries, query = "classes2"))
            .containsExactly(featureDex)
        assertThat(filterArchiveDexSummaries(entries, query = "039"))
            .containsExactly(featureDex)
        assertThat(filterArchiveDexSummaries(entries, query = "64"))
            .containsExactly(mainDex)
        assertThat(filterArchiveDexSummaries(entries, query = "", limit = 1))
            .containsExactly(mainDex)
    }

    @Test
    fun elfRelocationTypeName_shouldMapCommonAndroidAbiTypes() {
        assertThat(elfRelocationTypeName(0xB7, 1026L)).isEqualTo("AARCH64_JUMP_SLOT")
        assertThat(elfRelocationTypeName(0xB7, 1027L)).isEqualTo("AARCH64_RELATIVE")
        assertThat(elfRelocationTypeName(0x3E, 7L)).isEqualTo("X86_64_JUMP_SLOT")
        assertThat(elfRelocationTypeName(0x28, 23L)).isEqualTo("ARM_RELATIVE")
        assertThat(elfRelocationTypeName(0x03, 8L)).isEqualTo("I386_RELATIVE")
        assertThat(elfRelocationTypeName(0xB7, 65535L)).isNull()
    }

    @Test
    fun elfRelocationSemantic_shouldClassifyCommonRelocationTypes() {
        assertThat(elfRelocationSemantic("AARCH64_JUMP_SLOT"))
            .isEqualTo(HexElfRelocationSemantic.JUMP_SLOT_BINDING)
        assertThat(elfRelocationSemantic("AARCH64_GLOB_DAT"))
            .isEqualTo(HexElfRelocationSemantic.GLOB_DAT_ADDRESS)
        assertThat(elfRelocationSemantic("AARCH64_RELATIVE"))
            .isEqualTo(HexElfRelocationSemantic.RELATIVE_REBASE)
        assertThat(elfRelocationSemantic("AARCH64_COPY"))
            .isEqualTo(HexElfRelocationSemantic.COPY_RELOCATION)
        assertThat(elfRelocationSemantic("AARCH64_ABS64"))
            .isEqualTo(HexElfRelocationSemantic.ABSOLUTE_ADDRESS)
        assertThat(elfRelocationSemantic("X86_64_PC32"))
            .isEqualTo(HexElfRelocationSemantic.PC_RELATIVE_ADDRESS)
        assertThat(elfRelocationSemantic(null))
            .isEqualTo(HexElfRelocationSemantic.OTHER)
    }

    @Test
    fun analyzeHexBinaryFile_shouldReportHighEntropySignal() {
        runBlocking {
            val bytes = ByteArray(8192) { index -> (index and 0xFF).toByte() }
            val file = tempBinaryFile("entropy.bin", bytes)

            val analysis = analyzeHexBinaryFile(file)

            assertThat(analysis.entropy).isNotEmpty()
            assertThat(analysis.entropy.maxOf { it.entropy }).isAtLeast(7.5)
            assertThat(analysis.signals.map { it.type }).contains(HexAnalysisSignalType.HIGH_ENTROPY_REGION)
        }
    }

    @Test
    fun entropyBuckets_shouldBuildVisualBucketsWithLevelsAndNormalizedHeights() {
        val visualBuckets = listOf(
            HexEntropyBucket(startOffset = 0L, endOffset = 9L, entropy = 0.0),
            HexEntropyBucket(startOffset = 10L, endOffset = 19L, entropy = 6.0),
            HexEntropyBucket(startOffset = 20L, endOffset = 29L, entropy = 8.0)
        ).toVisualBuckets()

        assertThat(visualBuckets.map { it.level })
            .containsExactly(HexEntropyLevel.LOW, HexEntropyLevel.MEDIUM, HexEntropyLevel.HIGH)
            .inOrder()
        assertThat(visualBuckets[0].normalizedHeight).isWithin(0.001f).of(0.12f)
        assertThat(visualBuckets[1].normalizedHeight).isWithin(0.001f).of(0.75f)
        assertThat(visualBuckets[2].normalizedHeight).isWithin(0.001f).of(1.0f)
        assertThat(visualBuckets[2].startOffset).isEqualTo(20L)
    }

    @Test
    fun filterEntropyVisualBuckets_shouldFilterByLevelAndLimit() {
        val low = entropyVisualBucket(startOffset = 0L, level = HexEntropyLevel.LOW)
        val medium = entropyVisualBucket(startOffset = 10L, level = HexEntropyLevel.MEDIUM)
        val high = entropyVisualBucket(startOffset = 20L, level = HexEntropyLevel.HIGH)
        val highLater = entropyVisualBucket(startOffset = 30L, level = HexEntropyLevel.HIGH)
        val buckets = listOf(low, medium, high, highLater)

        assertThat(filterEntropyVisualBuckets(buckets, filter = EntropyBucketFilter.ALL))
            .containsExactly(low, medium, high, highLater)
            .inOrder()
        assertThat(filterEntropyVisualBuckets(buckets, filter = EntropyBucketFilter.LOW))
            .containsExactly(low)
        assertThat(filterEntropyVisualBuckets(buckets, filter = EntropyBucketFilter.MEDIUM))
            .containsExactly(medium)
        assertThat(filterEntropyVisualBuckets(buckets, filter = EntropyBucketFilter.HIGH, limit = 1))
            .containsExactly(high)
        assertThat(filterEntropyVisualBuckets(buckets, filter = EntropyBucketFilter.HIGH, limit = 0))
            .isEmpty()
    }

    @Test
    fun filterElfSectionEntropyEntries_shouldFilterByLevelQueryAndLimit() {
        val low = elfSectionEntropyEntry(
            sectionName = ".rodata",
            fileOffset = 0x300L,
            entropy = 1.0,
            level = HexEntropyLevel.LOW
        )
        val medium = elfSectionEntropyEntry(
            sectionName = ".dynstr",
            fileOffset = 0x400L,
            entropy = 6.0,
            level = HexEntropyLevel.MEDIUM
        )
        val high = elfSectionEntropyEntry(
            sectionName = ".packed",
            fileOffset = 0x500L,
            entropy = 7.9,
            level = HexEntropyLevel.HIGH
        )
        val entries = listOf(low, medium, high)

        assertThat(filterElfSectionEntropyEntries(entries, query = "packed", entropyFilter = EntropyBucketFilter.HIGH))
            .containsExactly(high)
        assertThat(filterElfSectionEntropyEntries(entries, query = "0x400", entropyFilter = EntropyBucketFilter.MEDIUM))
            .containsExactly(medium)
        assertThat(filterElfSectionEntropyEntries(entries, query = "1.00"))
            .containsExactly(low)
        assertThat(filterElfSectionEntropyEntries(entries, query = "", limit = 2))
            .containsExactly(low, medium)
            .inOrder()
        assertThat(filterElfSectionEntropyEntries(entries, query = "packed", limit = 0))
            .isEmpty()
    }

    private fun entropyVisualBucket(
        startOffset: Long,
        level: HexEntropyLevel
    ): HexEntropyVisualBucket = HexEntropyVisualBucket(
        startOffset = startOffset,
        endOffset = startOffset + 9L,
        entropy = when (level) {
            HexEntropyLevel.LOW -> 1.0
            HexEntropyLevel.MEDIUM -> 6.0
            HexEntropyLevel.HIGH -> 8.0
        },
        normalizedHeight = 1.0f,
        level = level
    )

    private fun elfSectionEntropyEntry(
        sectionName: String,
        fileOffset: Long,
        entropy: Double,
        level: HexEntropyLevel
    ): HexElfSectionEntropyEntry = HexElfSectionEntropyEntry(
        index = 0,
        sectionIndex = 1,
        sectionName = sectionName,
        fileOffset = fileOffset,
        size = 64L,
        virtualAddress = 0x400000L + fileOffset,
        sampleSize = 64L,
        entropy = entropy,
        level = level,
        isAllocated = true,
        isExecutable = false,
        isWritable = false
    )

    private fun elfSection(
        index: Int,
        name: String,
        type: Long = 1L,
        flags: Long = 0L,
        virtualAddress: Long = 0L,
        fileOffset: Long = 0L,
        size: Long = 0L
    ): HexElfSection = HexElfSection(
        index = index,
        name = name,
        type = type,
        flags = flags,
        virtualAddress = virtualAddress,
        fileOffset = fileOffset,
        size = size,
        link = 0,
        entrySize = 0L
    )

    private fun elfSymbol(
        name: String,
        value: Long = 0L,
        fileOffset: Long? = null,
        isImported: Boolean = false,
        isExported: Boolean = false,
        isJni: Boolean = false,
        sectionName: String? = null,
        sectionFileOffset: Long? = null,
        sectionSize: Long? = null
    ): HexElfSymbol = HexElfSymbol(
        name = name,
        value = value,
        fileOffset = fileOffset,
        size = 32L,
        binding = HexElfSymbolBinding.GLOBAL,
        type = HexElfSymbolType.FUNC,
        sectionIndex = if (isImported) 0 else 1,
        isImported = isImported,
        isExported = isExported,
        isJni = isJni,
        sectionName = sectionName,
        sectionFileOffset = sectionFileOffset,
        sectionSize = sectionSize
    )

    private fun elfRelocation(
        sectionName: String,
        symbolName: String?,
        relocationFileOffset: Long,
        offsetAddress: Long,
        offsetFileOffset: Long?,
        type: Long,
        targetSectionName: String? = null,
        symbolBinding: HexElfSymbolBinding? = HexElfSymbolBinding.GLOBAL,
        symbolType: HexElfSymbolType? = HexElfSymbolType.FUNC,
        isSymbolImported: Boolean = false,
        isSymbolExported: Boolean = false,
        isSymbolJni: Boolean = false,
        addend: Long? = 0L
    ): HexElfRelocationEntry = HexElfRelocationEntry(
        index = 0,
        sectionName = sectionName,
        relocationFileOffset = relocationFileOffset,
        offsetAddress = offsetAddress,
        offsetFileOffset = offsetFileOffset,
        symbolName = symbolName,
        symbolBinding = symbolBinding,
        symbolType = symbolType,
        isSymbolImported = isSymbolImported,
        isSymbolExported = isSymbolExported,
        isSymbolJni = isSymbolJni,
        symbolIndex = 1L,
        type = type,
        typeName = elfRelocationTypeName(0xB7, type),
        semantic = elfRelocationSemantic(elfRelocationTypeName(0xB7, type)),
        targetSectionName = targetSectionName,
        addend = addend
    )

    private fun tempBinaryFile(name: String, bytes: ByteArray): File = File.createTempFile(name.substringBefore('.'), ".${name.substringAfter('.', "bin")}").apply {
        deleteOnExit()
        writeBytes(bytes)
    }

    private fun archiveEntry(
        index: Int,
        name: String,
        localHeaderOffset: Long,
        dataOffset: Long? = localHeaderOffset + 0x20L,
        generalPurposeBitFlag: Int = 0,
        compressionMethod: Int = ZipEntry.DEFLATED,
        compressedSize: Long = 16L,
        uncompressedSize: Long = 32L,
        centralDirectoryOffset: Long = (archiveEntryDataEndOffset(dataOffset, compressedSize) ?: localHeaderOffset) + 0x200L,
        dataEndOffset: Long? = archiveEntryDataEndOffset(dataOffset, compressedSize),
        dataRangeStatus: HexArchiveEntryDataRangeStatus = archiveEntryDataRangeStatus(
            dataOffset = dataOffset,
            dataEndOffset = dataEndOffset,
            centralDirectoryOffset = centralDirectoryOffset,
            fileSize = 0x1000L
        ),
        crc32: Long = 0L,
        localHeaderName: String? = name,
        localHeaderGeneralPurposeBitFlag: Int? = generalPurposeBitFlag,
        localHeaderCompressionMethod: Int? = compressionMethod,
        localHeaderConsistency: HexArchiveEntryLocalHeaderConsistency = archiveEntryLocalHeaderConsistency(
            centralName = name,
            centralGeneralPurposeBitFlag = generalPurposeBitFlag,
            centralCompressionMethod = compressionMethod,
            localName = localHeaderName,
            localGeneralPurposeBitFlag = localHeaderGeneralPurposeBitFlag,
            localCompressionMethod = localHeaderCompressionMethod
        ),
        nameRisks: Set<HexArchiveEntryNameRisk> = archiveEntryNameRisks(name)
    ): HexArchiveEntry = HexArchiveEntry(
        index = index,
        name = name,
        generalPurposeBitFlag = generalPurposeBitFlag,
        compressionMethod = compressionMethod,
        crc32 = crc32,
        compressedSize = compressedSize,
        uncompressedSize = uncompressedSize,
        localHeaderOffset = localHeaderOffset,
        centralDirectoryOffset = centralDirectoryOffset,
        dataOffset = dataOffset,
        dataEndOffset = dataEndOffset,
        dataRangeStatus = dataRangeStatus,
        localHeaderName = localHeaderName,
        localHeaderGeneralPurposeBitFlag = localHeaderGeneralPurposeBitFlag,
        localHeaderCompressionMethod = localHeaderCompressionMethod,
        localHeaderConsistency = localHeaderConsistency,
        nameRisks = nameRisks
    )

    private fun nativeLibrarySummary(
        entryName: String,
        abi: String = "arm64-v8a",
        fileName: String = entryName.substringAfterLast('/'),
        localHeaderOffset: Long = 0x80L,
        dataOffset: Long? = 0x1000L,
        compressionMethod: Int = ZipEntry.DEFLATED,
        loadMode: HexArchiveNativeLoadMode = archiveNativeLoadMode(compressionMethod, dataOffset)
    ): HexArchiveNativeLibrarySummary = HexArchiveNativeLibrarySummary(
        entryName = entryName,
        abi = abi,
        fileName = fileName,
        localHeaderOffset = localHeaderOffset,
        dataOffset = dataOffset,
        compressionMethod = compressionMethod,
        crc32 = 0x1234L,
        compressedSize = 16L,
        uncompressedSize = 32L,
        analyzedBytes = 32L,
        truncated = false,
        isElf = true,
        is64Bit = true,
        endian = HexEndian.LITTLE,
        machineName = "AArch64",
        loadMode = loadMode,
        pageAlignmentRemainder = archiveNativePageAlignmentRemainder(dataOffset)
    )

    private fun dexSummary(
        version: String,
        stringCount: Int,
        methodCount: Int,
        classCount: Int
    ): HexDexSummary = HexDexSummary(
        version = version,
        checksum = 0L,
        signatureHex = "",
        fileSizeFromHeader = 0x220L,
        headerSize = 0x70L,
        endianTag = 0x12345678L,
        mapOffset = 0x180L,
        stringIdsSize = stringCount,
        stringIdsOffset = 0x70L,
        typeIdsSize = 0,
        typeIdsOffset = 0L,
        protoIdsSize = 0,
        protoIdsOffset = 0L,
        fieldIdsSize = 0,
        fieldIdsOffset = 0L,
        methodIdsSize = methodCount,
        methodIdsOffset = 0x80L,
        classDefsSize = classCount,
        classDefsOffset = 0x90L,
        dataSize = 0x100L,
        dataOffset = 0x120L
    )

    private fun minimalDex(): ByteArray {
        val bytes = ByteArray(0x240)
        "dex\n035\u0000".toByteArray().copyInto(bytes, destinationOffset = 0)
        bytes.writeLe32(8, 0x12345678)
        repeat(20) { index -> bytes[12 + index] = (index + 1).toByte() }
        bytes.writeLe32(32, bytes.size)
        bytes.writeLe32(36, 0x70)
        bytes.writeLe32(40, 0x12345678)
        bytes.writeLe32(52, 0x180)
        bytes.writeLe32(56, 5)
        bytes.writeLe32(60, 0x70)
        bytes.writeLe32(64, 2)
        bytes.writeLe32(68, 0x84)
        bytes.writeLe32(72, 1)
        bytes.writeLe32(76, 0x8C)
        bytes.writeLe32(80, 1)
        bytes.writeLe32(84, 0x98)
        bytes.writeLe32(88, 1)
        bytes.writeLe32(92, 0xA0)
        bytes.writeLe32(96, 1)
        bytes.writeLe32(100, 0xA8)
        bytes.writeLe32(104, 0x120)
        bytes.writeLe32(108, 0x120)
        bytes.writeLe32(0x70, 0x120)
        bytes.writeLe32(0x74, 0x140)
        bytes.writeLe32(0x78, 0x150)
        bytes.writeLe32(0x7C, 0x158)
        bytes.writeLe32(0x80, 0x168)
        bytes.writeLe32(0x84, 0)
        bytes.writeLe32(0x88, 4)
        bytes.writeLe32(0x8C, 2)
        bytes.writeLe32(0x90, 1)
        bytes.writeLe32(0x94, 0x200)
        bytes.writeLe16(0x98, 0)
        bytes.writeLe16(0x9A, 1)
        bytes.writeLe32(0x9C, 3)
        bytes.writeLe16(0xA0, 0)
        bytes.writeLe16(0xA2, 0)
        bytes.writeLe32(0xA4, 1)
        bytes.writeLe32(0xA8, 0)
        bytes.writeLe32(0xAC, 1)
        bytes.writeLe32(0xB0, -1)
        bytes.writeLe32(0xB4, 0)
        bytes.writeLe32(0xB8, -1)
        bytes.writeLe32(0xBC, 0)
        bytes.writeLe32(0xC0, 0x208)
        bytes.writeLe32(0xC4, 0)
        bytes.writeDexString(0x120, "Lcom/example/MainActivity;")
        bytes.writeDexString(0x140, "nativeEntry")
        bytes.writeDexString(0x150, "II")
        bytes.writeDexString(0x158, "counter")
        bytes.writeDexString(0x168, "I")
        bytes.writeLe32(0x180, 10)
        bytes.writeDexMapEntry(0x184, type = 0x0001, size = 5, dataOffset = 0x70)
        bytes.writeDexMapEntry(0x190, type = 0x0002, size = 2, dataOffset = 0x84)
        bytes.writeDexMapEntry(0x19C, type = 0x0003, size = 1, dataOffset = 0x8C)
        bytes.writeDexMapEntry(0x1A8, type = 0x0004, size = 1, dataOffset = 0x98)
        bytes.writeDexMapEntry(0x1B4, type = 0x0005, size = 1, dataOffset = 0xA0)
        bytes.writeDexMapEntry(0x1C0, type = 0x0006, size = 1, dataOffset = 0xA8)
        bytes.writeDexMapEntry(0x1CC, type = 0x1000, size = 1, dataOffset = 0x180)
        bytes.writeDexMapEntry(0x1D8, type = 0x1001, size = 1, dataOffset = 0x200)
        bytes.writeDexMapEntry(0x1E4, type = 0x2000, size = 1, dataOffset = 0x208)
        bytes.writeDexMapEntry(0x1F0, type = 0x2001, size = 1, dataOffset = 0x218)
        bytes.writeLe32(0x200, 1)
        bytes.writeLe16(0x204, 1)
        var classDataCursor = 0x208
        classDataCursor = bytes.writeDexUleb128(classDataCursor, 0)
        classDataCursor = bytes.writeDexUleb128(classDataCursor, 0)
        classDataCursor = bytes.writeDexUleb128(classDataCursor, 1)
        classDataCursor = bytes.writeDexUleb128(classDataCursor, 0)
        classDataCursor = bytes.writeDexUleb128(classDataCursor, 0)
        classDataCursor = bytes.writeDexUleb128(classDataCursor, 1)
        bytes.writeDexUleb128(classDataCursor, 0x218)
        bytes.writeLe16(0x218, 2)
        bytes.writeLe16(0x21A, 1)
        bytes.writeLe16(0x21C, 1)
        bytes.writeLe16(0x21E, 0)
        bytes.writeLe32(0x220, 0)
        bytes.writeLe32(0x224, 1)
        bytes.writeLe16(0x228, 0x000F)
        return bytes
    }

    private fun minimalDexWithInvokeReference(): ByteArray = minimalDex().apply {
        writeLe32(0x224, 4)
        writeLe16(0x228, 0x0171)
        writeLe16(0x22A, 0)
        writeLe16(0x22C, 0)
        writeLe16(0x22E, 0x000F)
    }

    private fun minimalDexWithDataReferences(): ByteArray = minimalDex().apply {
        writeLe32(0x224, 5)
        writeLe16(0x228, 0x001A)
        writeLe16(0x22A, 3)
        writeLe16(0x22C, 0x0060)
        writeLe16(0x22E, 0)
        writeLe16(0x230, 0x000F)
    }

    private fun minimalDexWithNativeMethod(): ByteArray = minimalDex().apply {
        this[0x20D] = 0x81.toByte()
        this[0x20E] = 0x02.toByte()
        this[0x20F] = 0x00.toByte()
    }

    private fun minimalApk(nativeLibraryBytes: ByteArray = minimalArchiveNativeLibrary()): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putBytes("AndroidManifest.xml", minimalBinaryAndroidManifest())
            zip.putBytes("classes.dex", minimalDex())
            zip.putBytes("lib/arm64-v8a/libdemo.so", nativeLibraryBytes)
            zip.putBytes("resources.arsc", minimalResourcesArsc())
            zip.putBytes("res/raw/payload.bin", byteArrayOf(1, 2, 3))
            zip.putBytes("META-INF/CERT.RSA", byteArrayOf(4, 5, 6))
        }
        return output.toByteArray().withApkSigningBlock()
    }

    private fun minimalApkWithStoredAlignedNativeLibrary(nativeLibraryBytes: ByteArray = minimalArchiveNativeLibrary()): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putStoredBytesAligned(
                name = "lib/arm64-v8a/libaligned.so",
                bytes = nativeLibraryBytes,
                dataAlignment = 4096
            )
        }
        return output.toByteArray()
    }

    private fun minimalArchiveNativeLibrary(): ByteArray = minimalElf64Aarch64(
        rodataText = "ollvm-fla ollvm-bcf ollvm-sub libjiagu"
    )

    private fun minimalElf64X86_64(includeGnuProperty: Boolean = false): ByteArray {
        val bytes = ByteArray(0x900)
        bytes[0] = 0x7F
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = 2
        bytes[5] = 1
        bytes[6] = 1
        bytes.writeLe16(16, 3)
        bytes.writeLe16(18, 0x3E)
        bytes.writeLe32(20, 1)
        bytes.writeLe64(24, 0x400000)
        bytes.writeLe64(32, 0x40)
        bytes.writeLe64(40, 0x600)
        bytes.writeLe32(48, 0)
        bytes.writeLe16(52, 64)
        bytes.writeLe16(54, 56)
        bytes.writeLe16(56, 4)
        bytes.writeLe16(58, 64)
        bytes.writeLe16(60, 11)
        bytes.writeLe16(62, 1)
        bytes.writeElf64ProgramHeader(
            offset = 0x40,
            type = 1,
            flags = 0x5,
            fileOffset = 0,
            virtualAddress = 0x400000,
            fileSize = bytes.size,
            memorySize = bytes.size,
            align = 0x1000
        )
        bytes.writeElf64ProgramHeader(
            offset = 0x78,
            type = 2,
            flags = 0x6,
            fileOffset = 0x580,
            virtualAddress = 0x400580,
            fileSize = 0x40,
            memorySize = 0x40,
            align = 0x8
        )
        bytes.writeElf64ProgramHeader(
            offset = 0xB0,
            type = 0x6474E552,
            flags = 0x4,
            fileOffset = 0x4A0,
            virtualAddress = 0x4004A0,
            fileSize = 0x100,
            memorySize = 0x100,
            align = 0x8
        )
        bytes.writeElf64ProgramHeader(
            offset = 0xE8,
            type = 0x6474E551,
            flags = 0x6,
            fileOffset = 0,
            virtualAddress = 0,
            fileSize = 0,
            memorySize = 0,
            align = 0x10
        )

        val stringTable = byteArrayOf(0) +
            ".shstrtab".toByteArray() + byteArrayOf(0) +
            ".init_array".toByteArray() + byteArrayOf(0) +
            ".rodata".toByteArray() + byteArrayOf(0) +
            ".dynstr".toByteArray() + byteArrayOf(0) +
            ".dynsym".toByteArray() + byteArrayOf(0) +
            ".rela.plt".toByteArray() + byteArrayOf(0) +
            ".got.plt".toByteArray() + byteArrayOf(0) +
            ".dynamic".toByteArray() + byteArrayOf(0) +
            ".note.gnu.build-id".toByteArray() + byteArrayOf(0) +
            ".plt".toByteArray() + byteArrayOf(0)
        stringTable.copyInto(bytes, destinationOffset = 0x500)
        val shstrtabNameOffset = 1
        val initArrayNameOffset = shstrtabNameOffset + ".shstrtab".length + 1
        val rodataNameOffset = initArrayNameOffset + ".init_array".length + 1
        val dynstrNameOffset = rodataNameOffset + ".rodata".length + 1
        val dynsymNameOffset = dynstrNameOffset + ".dynstr".length + 1
        val relaPltNameOffset = dynsymNameOffset + ".dynsym".length + 1
        val gotPltNameOffset = relaPltNameOffset + ".rela.plt".length + 1
        val dynamicNameOffset = gotPltNameOffset + ".got.plt".length + 1
        val noteBuildIdNameOffset = dynamicNameOffset + ".dynamic".length + 1
        val pltNameOffset = noteBuildIdNameOffset + ".note.gnu.build-id".length + 1

        val dynstr = byteArrayOf(0) +
            "puts".toByteArray() + byteArrayOf(0) +
            "JNI_OnLoad".toByteArray() + byteArrayOf(0) +
            "Java_com_example_Native_call".toByteArray() + byteArrayOf(0) +
            "libc.so".toByteArray() + byteArrayOf(0) +
            "libdemo.so".toByteArray() + byteArrayOf(0) +
            "\$ORIGIN".toByteArray() + byteArrayOf(0)
        dynstr.copyInto(bytes, destinationOffset = 0x300)
        val putsNameOffset = 1
        val jniOnLoadNameOffset = putsNameOffset + "puts".length + 1
        val javaNativeCallNameOffset = jniOnLoadNameOffset + "JNI_OnLoad".length + 1
        val libcNameOffset = javaNativeCallNameOffset + "Java_com_example_Native_call".length + 1
        val sonameOffset = libcNameOffset + "libc.so".length + 1
        val runpathOffset = sonameOffset + "libdemo.so".length + 1

        bytes.writeElf64Symbol(0x340 + 24, putsNameOffset, 0, 0)
        bytes.writeElf64Symbol(0x340 + 48, jniOnLoadNameOffset, 3, 0x4003D0)
        bytes.writeElf64Symbol(0x340 + 72, javaNativeCallNameOffset, 3, 0x4003F0)
        bytes.writeLe64(0x3C0, 0x4003D0)
        bytes.writeLe64(0x3C8, 0x500000)
        bytes.writeElf64Rela(0x410, offsetAddress = 0x4004A0, symbolIndex = 1, type = 7, addend = 0)
        bytes.writeElf64Rela(0x428, offsetAddress = 0x4004A8, symbolIndex = 2, type = 8, addend = 4)
        byteArrayOf(
            0xFF.toByte(),
            0x25.toByte(),
            0x78.toByte(),
            0x56.toByte(),
            0x34.toByte(),
            0x12.toByte(),
            0x68.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0xE9.toByte(),
            0x11.toByte(),
            0x22.toByte(),
            0x33.toByte(),
            0x44.toByte()
        ).copyInto(bytes, destinationOffset = 0x210)
        bytes.writeLe32(0x460, 4)
        bytes.writeLe32(0x464, 20)
        bytes.writeLe32(0x468, 3)
        byteArrayOf('G'.code.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 0)
            .copyInto(bytes, destinationOffset = 0x46C)
        byteArrayOf(
            0x01,
            0x23,
            0x45,
            0x67,
            0x89.toByte(),
            0xAB.toByte(),
            0xCD.toByte(),
            0xEF.toByte(),
            0xFE.toByte(),
            0xDC.toByte(),
            0xBA.toByte(),
            0x98.toByte(),
            0x76,
            0x54,
            0x32,
            0x10,
            0xAA.toByte(),
            0xBB.toByte(),
            0xCC.toByte(),
            0xDD.toByte()
        ).copyInto(bytes, destinationOffset = 0x470)
        if (includeGnuProperty) {
            bytes.writeLe32(0x484, 4)
            bytes.writeLe32(0x488, 16)
            bytes.writeLe32(0x48C, 5)
            byteArrayOf('G'.code.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 0)
                .copyInto(bytes, destinationOffset = 0x490)
            bytes.writeLe32(0x494, 0xC0000002.toInt())
            bytes.writeLe32(0x498, 4)
            bytes.writeLe32(0x49C, 0x3)
        }
        bytes.writeElf64Dynamic(0x580, tag = 1, value = libcNameOffset)
        bytes.writeElf64Dynamic(0x590, tag = 14, value = sonameOffset)
        bytes.writeElf64Dynamic(0x5A0, tag = 29, value = runpathOffset)
        bytes.writeElf64Dynamic(0x5B0, tag = 24, value = 0)
        bytes.writeElf64Dynamic(0x5C0, tag = 30, value = 0x8)
        bytes.writeElf64Dynamic(0x5D0, tag = 0x6FFFFFFB, value = 0x1)
        bytes.writeElf64Dynamic(0x5E0, tag = 0, value = 0)

        val sectionHeaderOffset = 0x600
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 64,
            nameOffset = shstrtabNameOffset,
            sectionOffset = 0x500,
            sectionSize = stringTable.size,
            sectionType = 3
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 128,
            nameOffset = initArrayNameOffset,
            sectionOffset = 0x3C0,
            sectionSize = 16,
            flags = 0x3,
            virtualAddress = 0x4003C0
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 192,
            nameOffset = rodataNameOffset,
            sectionOffset = 0x3D0,
            sectionSize = 16,
            flags = 0x2,
            virtualAddress = 0x4003D0
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 256,
            nameOffset = dynstrNameOffset,
            sectionOffset = 0x300,
            sectionSize = dynstr.size,
            sectionType = 3
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 320,
            nameOffset = dynsymNameOffset,
            sectionOffset = 0x340,
            sectionSize = 96,
            sectionType = 11,
            link = 4,
            entrySize = 24
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 384,
            nameOffset = relaPltNameOffset,
            sectionOffset = 0x410,
            sectionSize = 48,
            sectionType = 4,
            flags = 0x2,
            virtualAddress = 0x400410,
            link = 5,
            entrySize = 24
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 448,
            nameOffset = gotPltNameOffset,
            sectionOffset = 0x4A0,
            sectionSize = 16,
            flags = 0x3,
            virtualAddress = 0x4004A0
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 512,
            nameOffset = dynamicNameOffset,
            sectionOffset = 0x580,
            sectionSize = 112,
            sectionType = 6,
            flags = 0x3,
            virtualAddress = 0x400580,
            link = 4,
            entrySize = 16
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 576,
            nameOffset = noteBuildIdNameOffset,
            sectionOffset = 0x460,
            sectionSize = if (includeGnuProperty) 68 else 36,
            sectionType = 7,
            flags = 0x2,
            virtualAddress = 0x400460
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 640,
            nameOffset = pltNameOffset,
            sectionOffset = 0x200,
            sectionSize = 48,
            flags = 0x6,
            virtualAddress = 0x400200,
            entrySize = 16
        )
        return bytes
    }

    private fun minimalBinaryAndroidManifest(): ByteArray {
        val strings = listOf(
            "manifest",
            "package",
            "com.example.demo",
            "uses-permission",
            "name",
            "android.permission.INTERNET"
        )
        val stringPool = androidStringPoolChunk(strings)
        val manifestElement = androidStartElementChunk(
            nameIndex = 0,
            attributes = listOf(AndroidBinaryXmlAttribute(nameIndex = 1, valueIndex = 2))
        )
        val permissionElement = androidStartElementChunk(
            nameIndex = 3,
            attributes = listOf(AndroidBinaryXmlAttribute(nameIndex = 4, valueIndex = 5))
        )
        val totalSize = 8 + stringPool.size + manifestElement.size + permissionElement.size
        return ByteArrayOutputStream().apply {
            writeLe16(0x0003)
            writeLe16(8)
            writeLe32(totalSize)
            write(stringPool)
            write(manifestElement)
            write(permissionElement)
        }.toByteArray()
    }

    private data class AndroidBinaryXmlAttribute(
        val nameIndex: Int,
        val valueIndex: Int
    )

    private fun androidStringPoolChunk(strings: List<String>): ByteArray {
        val stringData = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        strings.forEach { value ->
            offsets += stringData.size()
            val bytes = value.toByteArray(Charsets.UTF_8)
            stringData.writeAndroidUtf8Length(value.length)
            stringData.writeAndroidUtf8Length(bytes.size)
            stringData.write(bytes)
            stringData.write(0)
        }
        while (stringData.size() % 4 != 0) {
            stringData.write(0)
        }

        val stringsStart = 28 + offsets.size * 4
        val chunkSize = stringsStart + stringData.size()
        return ByteArrayOutputStream().apply {
            writeLe16(0x0001)
            writeLe16(28)
            writeLe32(chunkSize)
            writeLe32(strings.size)
            writeLe32(0)
            writeLe32(0x00000100)
            writeLe32(stringsStart)
            writeLe32(0)
            offsets.forEach { offset -> writeLe32(offset) }
            write(stringData.toByteArray())
        }.toByteArray()
    }

    private fun androidStartElementChunk(
        nameIndex: Int,
        attributes: List<AndroidBinaryXmlAttribute>
    ): ByteArray {
        val chunkSize = 36 + attributes.size * 20
        return ByteArrayOutputStream().apply {
            writeLe16(0x0102)
            writeLe16(16)
            writeLe32(chunkSize)
            writeLe32(1)
            writeLe32(-1)
            writeLe32(-1)
            writeLe32(nameIndex)
            writeLe16(20)
            writeLe16(20)
            writeLe16(attributes.size)
            writeLe16(0)
            writeLe16(0)
            writeLe16(0)
            attributes.forEach { attribute ->
                writeLe32(-1)
                writeLe32(attribute.nameIndex)
                writeLe32(attribute.valueIndex)
                writeLe16(8)
                write(0)
                write(0x03)
                writeLe32(attribute.valueIndex)
            }
        }.toByteArray()
    }

    private fun minimalResourcesArsc(): ByteArray {
        val globalStringPool = androidStringPoolChunk(listOf("app_name"))
        val packageChunk = androidResourcePackageChunk()
        val totalSize = 12 + globalStringPool.size + packageChunk.size
        return ByteArrayOutputStream().apply {
            writeLe16(0x0002)
            writeLe16(12)
            writeLe32(totalSize)
            writeLe32(1)
            write(globalStringPool)
            write(packageChunk)
        }.toByteArray()
    }

    private fun androidResourcePackageChunk(): ByteArray {
        val typeStringPool = androidStringPoolChunk(listOf("string"))
        val keyStringPool = androidStringPoolChunk(listOf("app_name"))
        val typeSpecChunk = androidResourceTypeSpecChunk()
        val typeChunk = androidResourceTypeChunk()
        val headerSize = 288
        val typeStringsOffset = headerSize
        val keyStringsOffset = typeStringsOffset + typeStringPool.size
        val chunkSize = headerSize + typeStringPool.size + keyStringPool.size + typeSpecChunk.size + typeChunk.size
        return ByteArrayOutputStream().apply {
            writeLe16(0x0200)
            writeLe16(headerSize)
            writeLe32(chunkSize)
            writeLe32(0x7F)
            writeFixedUtf16("com.example.demo", 128)
            writeLe32(typeStringsOffset)
            writeLe32(1)
            writeLe32(keyStringsOffset)
            writeLe32(1)
            writeLe32(0)
            write(typeStringPool)
            write(keyStringPool)
            write(typeSpecChunk)
            write(typeChunk)
        }.toByteArray()
    }

    private fun androidResourceTypeSpecChunk(): ByteArray = ByteArrayOutputStream().apply {
        writeLe16(0x0202)
        writeLe16(16)
        writeLe32(20)
        write(1)
        write(0)
        writeLe16(0)
        writeLe32(1)
        writeLe32(0)
    }.toByteArray()

    private fun androidResourceTypeChunk(): ByteArray = ByteArrayOutputStream().apply {
        writeLe16(0x0201)
        writeLe16(84)
        writeLe32(84)
        write(1)
        write(0)
        writeLe16(0)
        writeLe32(0)
        writeLe32(84)
        writeLe32(0)
        repeat(60) { write(0) }
    }.toByteArray()

    private fun ByteArray.withApkSigningBlock(): ByteArray {
        val eocdOffset = findLastZipSignature(0x06054B50) ?: error("EOCD not found")
        val centralDirectoryOffset = readLe32(eocdOffset + 16)
        val signingBlock = apkSigningBlock()
        val signedBytes = ByteArray(size + signingBlock.size)
        copyInto(signedBytes, destinationOffset = 0, startIndex = 0, endIndex = centralDirectoryOffset)
        signingBlock.copyInto(signedBytes, destinationOffset = centralDirectoryOffset)
        copyInto(
            signedBytes,
            destinationOffset = centralDirectoryOffset + signingBlock.size,
            startIndex = centralDirectoryOffset,
            endIndex = size
        )
        signedBytes.writeLe32(eocdOffset + signingBlock.size + 16, centralDirectoryOffset + signingBlock.size)
        return signedBytes
    }

    private fun apkSigningBlock(): ByteArray {
        val block = ByteArray(48)
        block.writeLe64(0, 40L)
        block.writeLe64(8, 8L)
        block.writeLe32(16, 0x7109871A)
        block.writeLe32(20, 0x12345678)
        block.writeLe64(24, 40L)
        "APK Sig Block 42".toByteArray(Charsets.US_ASCII).copyInto(block, destinationOffset = 32)
        return block
    }

    private fun minimalElf64Aarch64(
        rodataText: String? = null,
        includeRelro: Boolean = true,
        executableStack: Boolean = false,
        bindNow: Boolean = true,
        rwxLoadSegment: Boolean = false,
        writableExecutableSection: Boolean = false
    ): ByteArray {
        val bytes = ByteArray(0x900)
        bytes[0] = 0x7F
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = 2
        bytes[5] = 1
        bytes[6] = 1
        bytes.writeLe16(16, 3)
        bytes.writeLe16(18, 0xB7)
        bytes.writeLe32(20, 1)
        bytes.writeLe64(24, 0x400000)
        bytes.writeLe64(32, 0x40)
        bytes.writeLe64(40, 0x600)
        bytes.writeLe32(48, 0)
        bytes.writeLe16(52, 64)
        bytes.writeLe16(54, 56)
        bytes.writeLe16(56, 4)
        bytes.writeLe16(58, 64)
        bytes.writeLe16(60, 11)
        bytes.writeLe16(62, 1)
        bytes.writeElf64ProgramHeader(
            offset = 0x40,
            type = 1,
            flags = if (rwxLoadSegment) 0x7 else 0x5,
            fileOffset = 0,
            virtualAddress = 0x400000,
            fileSize = bytes.size,
            memorySize = bytes.size,
            align = 0x1000
        )
        bytes.writeElf64ProgramHeader(
            offset = 0x78,
            type = 2,
            flags = 0x6,
            fileOffset = 0x580,
            virtualAddress = 0x400580,
            fileSize = 0x40,
            memorySize = 0x40,
            align = 0x8
        )
        bytes.writeElf64ProgramHeader(
            offset = 0xB0,
            type = 0x6474E552,
            flags = 0x4,
            fileOffset = 0x4A0,
            virtualAddress = 0x4004A0,
            fileSize = if (includeRelro) 0x100 else 0,
            memorySize = if (includeRelro) 0x100 else 0,
            align = 0x8
        )
        if (!includeRelro) {
            bytes.writeLe32(0xB0, 0)
        }
        bytes.writeElf64ProgramHeader(
            offset = 0xE8,
            type = 0x6474E551,
            flags = if (executableStack) 0x7 else 0x6,
            fileOffset = 0,
            virtualAddress = 0,
            fileSize = 0,
            memorySize = 0,
            align = 0x10
        )

        val stringTable = byteArrayOf(0) +
            ".shstrtab".toByteArray() + byteArrayOf(0) +
            ".init_array".toByteArray() + byteArrayOf(0) +
            ".rodata".toByteArray() + byteArrayOf(0) +
            ".dynstr".toByteArray() + byteArrayOf(0) +
            ".dynsym".toByteArray() + byteArrayOf(0) +
            ".rela.plt".toByteArray() + byteArrayOf(0) +
            ".got.plt".toByteArray() + byteArrayOf(0) +
            ".dynamic".toByteArray() + byteArrayOf(0) +
            ".note.gnu.build-id".toByteArray() + byteArrayOf(0) +
            ".plt".toByteArray() + byteArrayOf(0)
        stringTable.copyInto(bytes, destinationOffset = 0x500)
        val shstrtabNameOffset = 1
        val initArrayNameOffset = shstrtabNameOffset + ".shstrtab".length + 1
        val rodataNameOffset = initArrayNameOffset + ".init_array".length + 1
        val dynstrNameOffset = rodataNameOffset + ".rodata".length + 1
        val dynsymNameOffset = dynstrNameOffset + ".dynstr".length + 1
        val relaPltNameOffset = dynsymNameOffset + ".dynsym".length + 1
        val gotPltNameOffset = relaPltNameOffset + ".rela.plt".length + 1
        val dynamicNameOffset = gotPltNameOffset + ".got.plt".length + 1
        val noteBuildIdNameOffset = dynamicNameOffset + ".dynamic".length + 1
        val pltNameOffset = noteBuildIdNameOffset + ".note.gnu.build-id".length + 1

        val dynstr = byteArrayOf(0) +
            "puts".toByteArray() + byteArrayOf(0) +
            "JNI_OnLoad".toByteArray() + byteArrayOf(0) +
            "Java_com_example_Native_call".toByteArray() + byteArrayOf(0) +
            "libc.so".toByteArray() + byteArrayOf(0) +
            "libdemo.so".toByteArray() + byteArrayOf(0) +
            "\$ORIGIN".toByteArray() + byteArrayOf(0)
        dynstr.copyInto(bytes, destinationOffset = 0x300)
        val putsNameOffset = 1
        val jniOnLoadNameOffset = putsNameOffset + "puts".length + 1
        val javaNativeCallNameOffset = jniOnLoadNameOffset + "JNI_OnLoad".length + 1
        val libcNameOffset = javaNativeCallNameOffset + "Java_com_example_Native_call".length + 1
        val sonameOffset = libcNameOffset + "libc.so".length + 1
        val runpathOffset = sonameOffset + "libdemo.so".length + 1

        bytes.writeElf64Symbol(0x340 + 24, putsNameOffset, 0, 0)
        bytes.writeElf64Symbol(0x340 + 48, jniOnLoadNameOffset, 3, 0x4003D0)
        bytes.writeElf64Symbol(0x340 + 72, javaNativeCallNameOffset, 3, 0x4003F0)
        bytes.writeLe64(0x3C0, 0x4003D0)
        bytes.writeLe64(0x3C8, 0x500000)
        bytes.writeElf64Rela(0x410, offsetAddress = 0x4004A0, symbolIndex = 1, type = 1026, addend = 0)
        bytes.writeElf64Rela(0x428, offsetAddress = 0x4004A8, symbolIndex = 2, type = 1027, addend = 4)
        byteArrayOf(
            0x10,
            0x00,
            0x00,
            0x90.toByte(),
            0x11,
            0x02,
            0x40,
            0xF9.toByte(),
            0x10,
            0x02,
            0x00,
            0x91.toByte(),
            0x20,
            0x02,
            0x1F,
            0xD6.toByte()
        ).copyInto(bytes, destinationOffset = 0x220)
        bytes.writeLe32(0x460, 4)
        bytes.writeLe32(0x464, 20)
        bytes.writeLe32(0x468, 3)
        byteArrayOf('G'.code.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 0)
            .copyInto(bytes, destinationOffset = 0x46C)
        byteArrayOf(
            0x01,
            0x23,
            0x45,
            0x67,
            0x89.toByte(),
            0xAB.toByte(),
            0xCD.toByte(),
            0xEF.toByte(),
            0xFE.toByte(),
            0xDC.toByte(),
            0xBA.toByte(),
            0x98.toByte(),
            0x76,
            0x54,
            0x32,
            0x10,
            0xAA.toByte(),
            0xBB.toByte(),
            0xCC.toByte(),
            0xDD.toByte()
        ).copyInto(bytes, destinationOffset = 0x470)
        bytes.writeElf64Dynamic(0x580, tag = 1, value = libcNameOffset)
        bytes.writeElf64Dynamic(0x590, tag = 14, value = sonameOffset)
        bytes.writeElf64Dynamic(0x5A0, tag = 29, value = runpathOffset)
        if (bindNow) {
            bytes.writeElf64Dynamic(0x5B0, tag = 24, value = 0)
            bytes.writeElf64Dynamic(0x5C0, tag = 30, value = 0x8)
            bytes.writeElf64Dynamic(0x5D0, tag = 0x6FFFFFFB, value = 0x1)
            bytes.writeElf64Dynamic(0x5E0, tag = 0, value = 0)
        } else {
            bytes.writeElf64Dynamic(0x5B0, tag = 30, value = 0)
            bytes.writeElf64Dynamic(0x5C0, tag = 0x6FFFFFFB, value = 0)
            bytes.writeElf64Dynamic(0x5D0, tag = 0, value = 0)
        }

        val sectionHeaderOffset = 0x600
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 64,
            nameOffset = shstrtabNameOffset,
            sectionOffset = 0x500,
            sectionSize = stringTable.size,
            sectionType = 3
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 128,
            nameOffset = initArrayNameOffset,
            sectionOffset = 0x3C0,
            sectionSize = 16,
            flags = 0x3,
            virtualAddress = 0x4003C0
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 192,
            nameOffset = rodataNameOffset,
            sectionOffset = 0x3D0,
            sectionSize = 16,
            flags = if (writableExecutableSection) 0x7 else 0x2,
            virtualAddress = 0x4003D0
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 256,
            nameOffset = dynstrNameOffset,
            sectionOffset = 0x300,
            sectionSize = dynstr.size,
            sectionType = 3
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 320,
            nameOffset = dynsymNameOffset,
            sectionOffset = 0x340,
            sectionSize = 96,
            sectionType = 11,
            link = 4,
            entrySize = 24
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 384,
            nameOffset = relaPltNameOffset,
            sectionOffset = 0x410,
            sectionSize = 48,
            sectionType = 4,
            flags = 0x2,
            virtualAddress = 0x400410,
            link = 5,
            entrySize = 24
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 448,
            nameOffset = gotPltNameOffset,
            sectionOffset = 0x4A0,
            sectionSize = 16,
            flags = 0x3,
            virtualAddress = 0x4004A0
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 512,
            nameOffset = dynamicNameOffset,
            sectionOffset = 0x580,
            sectionSize = 112,
            sectionType = 6,
            flags = 0x3,
            virtualAddress = 0x400580,
            link = 4,
            entrySize = 16
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 576,
            nameOffset = noteBuildIdNameOffset,
            sectionOffset = 0x460,
            sectionSize = 36,
            sectionType = 7,
            flags = 0x2,
            virtualAddress = 0x400460
        )
        bytes.writeElf64SectionHeader(
            offset = sectionHeaderOffset + 640,
            nameOffset = pltNameOffset,
            sectionOffset = 0x200,
            sectionSize = 48,
            flags = 0x6,
            virtualAddress = 0x400200,
            entrySize = 16
        )
        rodataText?.toByteArray()?.copyInto(bytes, destinationOffset = 0x3D0)
        return bytes
    }

    private fun ByteArray.writeElf64ProgramHeader(
        offset: Int,
        type: Int,
        flags: Int,
        fileOffset: Int,
        virtualAddress: Long,
        fileSize: Int,
        memorySize: Int,
        align: Int
    ) {
        writeLe32(offset, type)
        writeLe32(offset + 4, flags)
        writeLe64(offset + 8, fileOffset.toLong())
        writeLe64(offset + 16, virtualAddress)
        writeLe64(offset + 24, virtualAddress)
        writeLe64(offset + 32, fileSize.toLong())
        writeLe64(offset + 40, memorySize.toLong())
        writeLe64(offset + 48, align.toLong())
    }

    private fun ByteArray.writeElf64SectionHeader(
        offset: Int,
        nameOffset: Int,
        sectionOffset: Int,
        sectionSize: Int,
        sectionType: Int = 1,
        flags: Long = 0L,
        virtualAddress: Long = 0L,
        link: Int = 0,
        entrySize: Int = 0
    ) {
        writeLe32(offset, nameOffset)
        writeLe32(offset + 4, sectionType)
        writeLe64(offset + 8, flags)
        writeLe64(offset + 16, virtualAddress)
        writeLe64(offset + 24, sectionOffset.toLong())
        writeLe64(offset + 32, sectionSize.toLong())
        writeLe32(offset + 40, link)
        writeLe64(offset + 56, entrySize.toLong())
    }

    private fun ByteArray.writeElf64Symbol(offset: Int, nameOffset: Int, sectionIndex: Int, value: Long) {
        writeLe32(offset, nameOffset)
        this[offset + 4] = ((1 shl 4) or 2).toByte()
        writeLe16(offset + 6, sectionIndex)
        writeLe64(offset + 8, value)
        writeLe64(offset + 16, 32)
    }

    private fun ByteArray.writeElf64Rela(
        offset: Int,
        offsetAddress: Long,
        symbolIndex: Long,
        type: Long,
        addend: Long
    ) {
        writeLe64(offset, offsetAddress)
        writeLe64(offset + 8, (symbolIndex shl 32) or type)
        writeLe64(offset + 16, addend)
    }

    private fun ByteArray.writeElf64Dynamic(offset: Int, tag: Long, value: Int) {
        writeLe64(offset, tag)
        writeLe64(offset + 8, value.toLong())
    }

    private fun ByteArray.writeDexString(offset: Int, value: String) {
        val valueBytes = value.toByteArray()
        this[offset] = value.length.toByte()
        valueBytes.copyInto(this, destinationOffset = offset + 1)
        this[offset + 1 + valueBytes.size] = 0
    }

    private fun ByteArray.writeDexMapEntry(offset: Int, type: Int, size: Int, dataOffset: Int) {
        writeLe16(offset, type)
        writeLe16(offset + 2, 0)
        writeLe32(offset + 4, size)
        writeLe32(offset + 8, dataOffset)
    }

    private fun ByteArray.writeDexUleb128(offset: Int, value: Int): Int {
        var cursor = offset
        var remainingValue = value
        do {
            var byteValue = remainingValue and 0x7F
            remainingValue = remainingValue ushr 7
            if (remainingValue != 0) byteValue = byteValue or 0x80
            this[cursor] = byteValue.toByte()
            cursor++
        } while (remainingValue != 0)
        return cursor
    }

    private fun ByteArrayOutputStream.writeLe16(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLe32(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeAndroidUtf8Length(value: Int) {
        if (value < 0x80) {
            write(value)
        } else {
            write(((value ushr 8) and 0x7F) or 0x80)
            write(value and 0xFF)
        }
    }

    private fun ByteArrayOutputStream.writeFixedUtf16(value: String, maxChars: Int) {
        repeat(maxChars) { index ->
            val charValue = value.getOrNull(index)?.code ?: 0
            writeLe16(charValue)
        }
    }

    private fun ZipOutputStream.putBytes(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.putStoredBytesAligned(
        name: String,
        bytes: ByteArray,
        dataAlignment: Int
    ) {
        val nameLength = name.toByteArray(Charsets.UTF_8).size
        val baseDataOffset = 30 + nameLength
        val extraLength = (dataAlignment - (baseDataOffset % dataAlignment)) % dataAlignment
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = crc32Of(bytes)
            extra = zipPaddingExtra(extraLength)
        }
        putNextEntry(entry)
        write(bytes)
        closeEntry()
    }

    private fun zipPaddingExtra(totalLength: Int): ByteArray {
        if (totalLength == 0) return ByteArray(0)
        require(totalLength >= 4)
        val payloadLength = totalLength - 4
        return ByteArray(totalLength).apply {
            this[0] = 0xFE.toByte()
            this[1] = 0xCA.toByte()
            this[2] = (payloadLength and 0xFF).toByte()
            this[3] = ((payloadLength ushr 8) and 0xFF).toByte()
        }
    }

    private fun ByteArray.findLastZipSignature(signature: Int): Int? {
        if (size < 4) return null
        for (index in size - 4 downTo 0) {
            if (readLe32(index) == signature) return index
        }
        return null
    }

    private fun ByteArray.zipLocalDataOffset(localHeaderOffset: Long): Long {
        val offset = localHeaderOffset.toInt()
        check(readLe32(offset) == 0x04034B50)
        val nameLength = readLe16(offset + 26)
        val extraLength = readLe16(offset + 28)
        return localHeaderOffset + 30L + nameLength + extraLength
    }

    private fun ByteArray.replaceCentralDirectoryEntryName(from: String, to: String): ByteArray {
        val fromBytes = from.toByteArray(Charsets.UTF_8)
        val toBytes = to.toByteArray(Charsets.UTF_8)
        require(fromBytes.size == toBytes.size) {
            "central directory name replacement must preserve byte length"
        }
        val output = copyOf()
        var cursor = 0
        while (cursor <= output.size - 46) {
            if (output.readLe32(cursor) == 0x02014B50) {
                val nameLength = output.readLe16(cursor + 28)
                val nameOffset = cursor + 46
                if (
                    nameLength == fromBytes.size &&
                    nameOffset + nameLength <= output.size &&
                    output.copyOfRange(nameOffset, nameOffset + nameLength).contentEquals(fromBytes)
                ) {
                    toBytes.copyInto(output, destinationOffset = nameOffset)
                    return output
                }
            }
            cursor++
        }
        error("central directory entry not found: $from")
    }

    private fun ByteArray.readLe16(offset: Int): Int = (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.readLe32(offset: Int): Int = (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun crc32Of(bytes: ByteArray): Long {
        val crc32 = CRC32()
        crc32.update(bytes)
        return crc32.value
    }

    private fun ByteArray.writeLe16(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun ByteArray.writeLe32(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        this[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun ByteArray.writeLe64(offset: Int, value: Long) {
        repeat(8) { byteIndex ->
            this[offset + byteIndex] = ((value ushr (byteIndex * 8)) and 0xFF).toByte()
        }
    }

    private fun String.toUtf16LeBytes(): ByteArray {
        val bytes = ByteArray(length * 2)
        forEachIndexed { index, char ->
            bytes[index * 2] = (char.code and 0xFF).toByte()
            bytes[index * 2 + 1] = ((char.code ushr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun String.toUtf16BeBytes(): ByteArray {
        val bytes = ByteArray(length * 2)
        forEachIndexed { index, char ->
            bytes[index * 2] = ((char.code ushr 8) and 0xFF).toByte()
            bytes[index * 2 + 1] = (char.code and 0xFF).toByte()
        }
        return bytes
    }
}
