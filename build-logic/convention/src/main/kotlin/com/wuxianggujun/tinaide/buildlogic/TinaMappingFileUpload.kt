package com.wuxianggujun.tinaide.buildlogic

import org.gradle.api.logging.Logger
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

/**
 * Core logic for the `uploadMappingFiles` task registered by
 * [TinaAndroidAppMappingPlugin].
 *
 * Iterates through every `build/outputs/mapping/<flavor>Release/mapping.txt`,
 * compresses it with gzip and uploads it as a multipart POST to
 * `<serverUrl>/api/mappings/upload`. Server-side is expected to
 * decompress and persist the uploaded artefact for crash log de-obfuscation.
 */
internal object TinaMappingFileUpload {

    private const val MAPPING_UPLOAD_ENDPOINT = "/api/mappings/upload"
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 300_000

    fun uploadMappings(
        mappingRoot: File,
        serverUrl: String,
        appVersionName: String,
        appVersionCode: Int,
        logger: Logger,
    ) {
        if (!mappingRoot.exists()) {
            logger.warn("No mapping files found. Run a release build first.")
            return
        }

        mappingRoot.listFiles()
            ?.filter { it.isDirectory && it.name.endsWith("Release") }
            ?.forEach { flavorDir ->
                val mappingFile = flavorDir.resolve("mapping.txt")
                if (!mappingFile.exists()) {
                    logger.warn("Mapping file not found: ${mappingFile.absolutePath}")
                    return@forEach
                }

                val flavor = flavorDir.name.removeSuffix("Release")
                uploadSingleMapping(
                    mappingFile = mappingFile,
                    flavor = flavor,
                    serverUrl = serverUrl,
                    appVersionName = appVersionName,
                    appVersionCode = appVersionCode,
                    logger = logger,
                )
            }
    }

    private fun uploadSingleMapping(
        mappingFile: File,
        flavor: String,
        serverUrl: String,
        appVersionName: String,
        appVersionCode: Int,
        logger: Logger,
    ) {
        val originalBytes = mappingFile.readBytes()
        val compressedBytes = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzip -> gzip.write(originalBytes) }
            baos.toByteArray()
        }

        val originalSizeMB = originalBytes.size / 1024.0 / 1024.0
        val compressedSizeMB = compressedBytes.size / 1024.0 / 1024.0
        val ratio = (1 - compressedBytes.size.toDouble() / originalBytes.size) * 100

        logger.lifecycle(
            "Uploading mapping for $appVersionName ($appVersionCode) flavor=$flavor...",
        )
        logger.lifecycle(
            "  Original: %.2f MB, Compressed: %.2f MB (%.1f%% reduction)"
                .format(originalSizeMB, compressedSizeMB, ratio),
        )

        try {
            val uploadUrl = URL(serverUrl.trimEnd('/') + MAPPING_UPLOAD_ENDPOINT)
            val boundary = "----MappingUpload${System.currentTimeMillis()}"
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            val connection = uploadUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            connection.useCaches = false
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Connection", "Keep-Alive")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val outputStream = connection.outputStream
            val writer = DataOutputStream(outputStream)

            // app_version_name
            writer.writeBytes("$twoHyphens$boundary$lineEnd")
            writer.writeBytes("Content-Disposition: form-data; name=\"app_version_name\"$lineEnd")
            writer.writeBytes(lineEnd)
            writer.writeBytes("$appVersionName$lineEnd")

            // app_version_code
            writer.writeBytes("$twoHyphens$boundary$lineEnd")
            writer.writeBytes("Content-Disposition: form-data; name=\"app_version_code\"$lineEnd")
            writer.writeBytes(lineEnd)
            writer.writeBytes("$appVersionCode$lineEnd")

            // flavor
            writer.writeBytes("$twoHyphens$boundary$lineEnd")
            writer.writeBytes("Content-Disposition: form-data; name=\"flavor\"$lineEnd")
            writer.writeBytes(lineEnd)
            writer.writeBytes("$flavor$lineEnd")

            // build_type
            writer.writeBytes("$twoHyphens$boundary$lineEnd")
            writer.writeBytes("Content-Disposition: form-data; name=\"build_type\"$lineEnd")
            writer.writeBytes(lineEnd)
            writer.writeBytes("release$lineEnd")

            // file (gzip 压缩后的文件，文件名带 .gz 后缀)
            writer.writeBytes("$twoHyphens$boundary$lineEnd")
            writer.writeBytes(
                "Content-Disposition: form-data; name=\"file\"; filename=\"mapping.txt.gz\"$lineEnd",
            )
            writer.writeBytes("Content-Type: application/gzip$lineEnd")
            writer.writeBytes(lineEnd)
            writer.write(compressedBytes)
            writer.writeBytes(lineEnd)

            writer.writeBytes("$twoHyphens$boundary$twoHyphens$lineEnd")
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: "No error message"
            }

            if (responseCode in 200..299) {
                logger.lifecycle(
                    "✓ Uploaded mapping for $appVersionName ($appVersionCode) flavor=$flavor",
                )
                logger.lifecycle("  Response: $responseBody")
            } else {
                logger.error(
                    "✗ Failed to upload mapping for $appVersionName ($appVersionCode) flavor=$flavor",
                )
                logger.error("  HTTP $responseCode: $responseBody")
            }

            connection.disconnect()
        } catch (e: Exception) {
            logger.error(
                "✗ Failed to upload mapping for $appVersionName ($appVersionCode) flavor=$flavor",
            )
            logger.error("  Error type: ${e.javaClass.name}")
            logger.error("  Error message: ${e.message}")
            e.cause?.let { cause ->
                logger.error("  Caused by: ${cause.javaClass.name}: ${cause.message}")
            }
            logger.error("  Stack trace:")
            e.stackTrace.take(10).forEach { frame ->
                logger.error("    at $frame")
            }
        }
    }
}
