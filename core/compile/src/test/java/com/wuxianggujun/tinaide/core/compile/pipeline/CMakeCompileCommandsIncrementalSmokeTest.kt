package com.wuxianggujun.tinaide.core.compile.pipeline

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.compile.BuildOptions
import com.wuxianggujun.tinaide.core.compile.BuildSystem
import com.wuxianggujun.tinaide.core.compile.BuildType
import com.wuxianggujun.tinaide.core.compile.CMakeBuildTypeOption
import com.wuxianggujun.tinaide.core.compile.CMakeGeneratorOption
import com.wuxianggujun.tinaide.core.compile.CompilerType
import com.wuxianggujun.tinaide.core.compile.TargetInfo
import com.wuxianggujun.tinaide.core.compile.action.BuildIntent
import com.wuxianggujun.tinaide.core.compile.action.CompileRequest
import com.wuxianggujun.tinaide.core.compile.action.LaunchIntent
import com.wuxianggujun.tinaide.core.compile.artifact.Artifact
import com.wuxianggujun.tinaide.core.compile.artifact.ArtifactId
import com.wuxianggujun.tinaide.core.compile.artifact.ArtifactKind
import com.wuxianggujun.tinaide.core.compile.artifact.ArtifactSpec
import com.wuxianggujun.tinaide.core.compile.artifact.ArtifactStore
import com.wuxianggujun.tinaide.core.compile.artifact.BuildFingerprint
import com.wuxianggujun.tinaide.core.compile.artifact.FingerprintCalculator
import com.wuxianggujun.tinaide.core.compile.artifact.SourceRef
import com.wuxianggujun.tinaide.core.compile.artifact.TrackedInputCollector
import com.wuxianggujun.tinaide.core.compile.event.BuildEventEmitter
import com.wuxianggujun.tinaide.core.compile.strategy.BuildContext
import com.wuxianggujun.tinaide.core.compile.strategy.BuildStrategy
import com.wuxianggujun.tinaide.core.compile.strategy.BuildStrategyRegistry
import com.wuxianggujun.tinaide.core.compile.strategy.ExecutionOutcome
import com.wuxianggujun.tinaide.core.linux.LinuxRunModePolicy
import io.mockk.mockk
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CMakeCompileCommandsIncrementalSmokeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val calculator = FingerprintCalculator()

    @Test
    fun `cmake compile commands source content change triggers rebuild instead of cached artifact reuse`() = runTest {
        val projectRoot = tempFolder.newFolder("ndk-shared-project")
        val buildDir = File(projectRoot, "build").apply { mkdirs() }
        val source = File(projectRoot, "src/native-lib.cpp").apply {
            parentFile?.mkdirs()
            writeText("""extern "C" int answer() { return 1; }""")
        }
        val cmakeLists = File(projectRoot, "CMakeLists.txt").apply {
            writeText("add_library(demo SHARED src/native-lib.cpp)")
        }
        File(buildDir, "compile_commands.json").writeText(
            """
            [
              {
                "directory": "${jsonPath(projectRoot)}",
                "command": "clang++ -o CMakeFiles/demo.dir/src/native-lib.cpp.o -c src/native-lib.cpp",
                "file": "src/native-lib.cpp",
                "output": "CMakeFiles/demo.dir/src/native-lib.cpp.o"
              }
            ]
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val ctx = newContext(projectRoot, buildDir)
        val initialSpec = cmakeSpec(ctx, source, cmakeLists)
        val store = FakeArtifactStore()
        store.put(
            newArtifact(
                ctx = ctx,
                spec = initialSpec,
                sources = initialSpec.sources.map { SourceRef.capture(it, projectRoot) },
            )
        )

        val oldTimestamp = source.lastModified()
        source.writeText("""extern "C" int answer() { return 2; }""")
        source.setLastModified(oldTimestamp)

        val strategy = FakeCMakeStrategy { cmakeSpec(ctx, source, cmakeLists) }
        val planner = BuildPlanner(
            strategyRegistry = BuildStrategyRegistry(listOf(strategy)),
            artifactStore = store,
            fingerprintCalculator = calculator,
        )

        val plan = planner.plan(
            request = CompileRequest(BuildIntent.IfNeeded, LaunchIntent.None),
            ctx = ctx,
        )

        assertThat(plan).isInstanceOf(BuildPlan.Build::class.java)
        assertThat((plan as BuildPlan.Build).reason).contains("trackedInputsHash")
    }

    private fun cmakeSpec(
        ctx: BuildContext,
        source: File,
        cmakeLists: File,
    ): ArtifactSpec {
        val compileInputs = TrackedInputCollector.collectCMakeInputs(
            projectRoot = ctx.projectRoot,
            buildDir = ctx.buildDir,
            targetNames = setOf("demo"),
            targetSources = listOf(source),
        )
        return ArtifactSpec(
            id = ArtifactId(projectId = ctx.projectId, targetName = "demo", variant = "default"),
            expectedPath = File(ctx.buildDir, "libdemo.so"),
            kind = ArtifactKind.SHARED_LIBRARY,
            sources = compileInputs,
            reconfigureSources = listOf(cmakeLists),
        )
    }

    private fun newContext(projectRoot: File, buildDir: File): BuildContext = BuildContext(
        appContext = mockk(relaxed = true),
        projectRoot = projectRoot,
        buildDir = buildDir,
        buildSystem = BuildSystem.CMAKE,
        options = BuildOptions(
            buildType = BuildType.DEBUG,
            compilerType = CompilerType.CLANG,
            cmakeBuildType = CMakeBuildTypeOption.DEBUG,
            cmakeGenerator = CMakeGeneratorOption.NINJA,
            resolvedRunMode = LinuxRunModePolicy.RunMode.NATIVE,
            sysrootProfileId = "builtin-ndk-r28c-arm64",
            sysrootApiLevel = 28,
            parallelJobs = 4,
        ),
        projectId = "ndk-shared-project",
        target = "demo",
    )

    private fun newArtifact(
        ctx: BuildContext,
        spec: ArtifactSpec,
        sources: List<SourceRef>,
    ): Artifact {
        val file = spec.expectedPath.apply {
            parentFile?.mkdirs()
            writeText("old-binary")
        }
        return Artifact(
            id = spec.id,
            absolutePath = file.absolutePath,
            kind = spec.kind,
            contentHash = "old-content",
            fingerprint = calculator.compute(ctx, spec),
            sources = sources,
            compiledAt = System.currentTimeMillis(),
            buildTimeMs = 1L,
        )
    }

    private fun jsonPath(file: File): String = file.absolutePath.replace('\\', '/')

    private class FakeArtifactStore : ArtifactStore {
        private val artifacts = ConcurrentHashMap<String, Artifact>()

        fun put(artifact: Artifact) {
            artifacts[key(artifact.id, File(artifact.absolutePath).parentFile!!)] = artifact
        }

        override suspend fun find(id: ArtifactId, buildDir: File): Artifact? = artifacts[key(id, buildDir)]

        override suspend fun register(artifact: Artifact, buildDir: File) {
            artifacts[key(artifact.id, buildDir)] = artifact
        }

        override suspend fun invalidate(id: ArtifactId, buildDir: File) {
            artifacts.remove(key(id, buildDir))
        }

        override suspend fun listAll(buildDir: File): List<Artifact> =
            artifacts.filterKeys { it.endsWith("|${buildDir.absolutePath}") }.values.toList()

        override suspend fun clearAll(buildDir: File) {
            artifacts.keys.filter { it.endsWith("|${buildDir.absolutePath}") }.forEach { artifacts.remove(it) }
        }

        private fun key(id: ArtifactId, buildDir: File) = "${id.storageKey()}|${buildDir.absolutePath}"
    }

    private class FakeCMakeStrategy(
        private val specFactory: (BuildContext) -> ArtifactSpec,
    ) : BuildStrategy {
        override val buildSystem: BuildSystem = BuildSystem.CMAKE

        override suspend fun canHandle(projectRoot: File): Boolean = true

        override suspend fun describeOutput(ctx: BuildContext): ArtifactSpec = specFactory(ctx)

        override suspend fun execute(
            ctx: BuildContext,
            spec: ArtifactSpec,
            fingerprint: BuildFingerprint,
            emitter: BuildEventEmitter,
        ): ExecutionOutcome = ExecutionOutcome.Failure("not used in smoke test")

        override suspend fun clean(ctx: BuildContext, reconfigure: Boolean) = Unit

        override suspend fun getTargets(ctx: BuildContext): List<TargetInfo> =
            listOf(TargetInfo("demo", TargetInfo.Type.SHARED_LIBRARY, sources = listOf("src/native-lib.cpp")))
    }
}
