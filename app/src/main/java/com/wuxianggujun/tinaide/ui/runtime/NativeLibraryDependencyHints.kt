package com.wuxianggujun.tinaide.ui.runtime

import android.content.Context
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import java.io.File

object NativeLibraryDependencyHints {
    private val libraryPackageHints = mapOf(
        "libSDL2.so" to "sdl2",
        "libSDL3.so" to "sdl3",
        "libSDL3_image.so" to "sdl3-image",
        "libSDL3_mixer.so" to "sdl3-mixer",
        "libSDL3_net.so" to "sdl3-net",
        "libSDL3_ttf.so" to "sdl3-ttf",
        "libBox2D.so" to "box2d",
        "libbox2d.so" to "box2d",
        "libminiaudio.so" to "miniaudio",
        "libraylib.so" to "raylib"
    )

    fun inferPackageIds(libraryNames: List<String>): List<String> {
        return libraryNames.asSequence()
            .map(::normalizeLibraryName)
            .mapNotNull(libraryPackageHints::get)
            .distinct()
            .sorted()
            .toList()
    }

    fun filterUnresolvedLibraries(
        missingLibraries: List<String>,
        providedLibraries: List<File>
    ): List<String> {
        if (missingLibraries.isEmpty() || providedLibraries.isEmpty()) {
            return normalizeLibraryNames(missingLibraries)
        }

        val providedNames = providedLibraries.asSequence()
            .flatMap { library ->
                sequenceOf(library.name, normalizeLibraryName(library.name))
            }
            .toSet()

        return normalizeLibraryNames(missingLibraries)
            .filterNot { missing -> missing in providedNames }
    }

    fun buildMissingLibrariesMessage(
        context: Context,
        missingLibraries: List<String>,
        includeApkImportHint: Boolean = false
    ): String {
        val normalizedLibraries = normalizeLibraryNames(missingLibraries)
        if (normalizedLibraries.isEmpty()) return ""

        val missingText = Strings.native_library_missing_libraries_message.strOr(
            context,
            normalizedLibraries.joinToString(", ")
        )
        val packageIds = inferPackageIds(normalizedLibraries)
        val hint = when {
            packageIds.isNotEmpty() && includeApkImportHint ->
                Strings.native_library_missing_libraries_package_or_import_hint.strOr(
                    context,
                    packageIds.joinToString(", ")
                )
            packageIds.isNotEmpty() ->
                Strings.native_library_missing_libraries_package_hint.strOr(
                    context,
                    packageIds.joinToString(", ")
                )
            includeApkImportHint ->
                Strings.native_library_missing_libraries_generic_or_import_hint.strOr(context)
            else ->
                Strings.native_library_missing_libraries_generic_hint.strOr(context)
        }
        return listOf(missingText, hint).joinToString("\n")
    }

    private fun normalizeLibraryNames(libraryNames: List<String>): List<String> {
        return libraryNames.asSequence()
            .map(::normalizeLibraryName)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
    }

    internal fun normalizeLibraryName(name: String): String {
        val trimmed = name.trim()
        val markerIndex = trimmed.indexOf(".so")
        if (markerIndex < 0) return trimmed
        return trimmed.substring(0, markerIndex + 3)
    }
}
