package com.wuxianggujun.tinaide.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers lightweight build-time guardrails that enforce Tina's
 * internal coding rules:
 *
 * - `checkNoAndroidUtilLog` fails the build if any Kotlin/Java source
 *   under `app/src` imports or references `android.util.Log`, except
 *   inside the dedicated Tina logging implementation packages and the
 *   SDL shim under `org.libsdl.app`.
 *
 * When the `tina.checkNoAndroidUtilLogOnBuild` gradle property is truthy
 * (defaults to `true` under CI, `false` locally), the task is wired as a
 * `preBuild` dependency so that guardrail failures surface before any
 * Kotlin / Java compilation runs.
 */
class TinaAndroidAppGuardrailsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            val enforceOnBuild = resolveBooleanGradleProperty(
                name = "tina.checkNoAndroidUtilLogOnBuild",
                default = System.getenv("CI")?.equals("true", ignoreCase = true) == true,
            )

            val checkNoAndroidUtilLog = tasks.register("checkNoAndroidUtilLog") {
                group = "verification"
                description =
                    "Fails the build if android.util.Log is used outside Tina logging implementation."

                val allowPathPrefixes = listOf(
                    project.projectDir.resolve("src/main/java/com/wuxianggujun/tinaide/utils/logging")
                        .invariantSeparatorsPath,
                    project.projectDir.resolve("src/main/java/com/wuxianggujun/tinaide/core/logging")
                        .invariantSeparatorsPath,
                    project.projectDir.resolve("src/main/java/org/libsdl/app")
                        .invariantSeparatorsPath,
                )

                val sources = fileTree(project.projectDir.resolve("src")) {
                    include("**/*.kt", "**/*.java")
                }

                inputs.files(sources)

                doLast {
                    val violations = mutableListOf<String>()
                    sources.files.forEach { file ->
                        val path = file.invariantSeparatorsPath
                        if (allowPathPrefixes.any { prefix -> path.startsWith(prefix) }) {
                            return@forEach
                        }

                        val text = file.readText(Charsets.UTF_8)
                        if (text.contains("import android.util.Log") ||
                            text.contains("android.util.Log.")
                        ) {
                            violations += path
                        }
                    }

                    if (violations.isNotEmpty()) {
                        throw GradleException(
                            buildString {
                                appendLine(
                                    "Forbidden android.util.Log usage detected (use Timber/TinaTimber instead).",
                                )
                                appendLine("Allowed only under:")
                                allowPathPrefixes.forEach { appendLine(" - $it") }
                                appendLine("Files:")
                                violations.sorted().forEach { appendLine(" - $it") }
                            },
                        )
                    }
                }
            }

            pluginManager.withPlugin("com.android.application") {
                if (enforceOnBuild) {
                    tasks.named("preBuild").configure {
                        dependsOn(checkNoAndroidUtilLog)
                    }
                }
            }
        }
    }
}
