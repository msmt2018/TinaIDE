package com.wuxianggujun.tinaide.core.linuxdistro

import android.content.Context
import java.io.File

interface LinuxDistroManifestSource {
    fun loadManifest(): LinuxDistroManifest
}

class FileLinuxDistroManifestSource(
    private val manifestFile: File,
) : LinuxDistroManifestSource {
    override fun loadManifest(): LinuxDistroManifest {
        require(manifestFile.isFile) { "Linux distro manifest does not exist: ${manifestFile.absolutePath}" }
        return manifestFile.inputStream().use { input -> LinuxDistroManifestParser.decode(input) }
    }
}

class AndroidAssetLinuxDistroManifestSource(
    context: Context,
    private val assetPath: String = DEFAULT_ASSET_PATH,
) : LinuxDistroManifestSource {
    private val appContext = context.applicationContext

    override fun loadManifest(): LinuxDistroManifest {
        return appContext.assets.open(assetPath).use { input -> LinuxDistroManifestParser.decode(input) }
    }

    companion object {
        const val DEFAULT_ASSET_PATH = "linux-distro/manifest.json"
    }
}

class StaticLinuxDistroManifestSource(
    private val manifest: LinuxDistroManifest,
) : LinuxDistroManifestSource {
    override fun loadManifest(): LinuxDistroManifest = manifest
}

fun LinuxDistroManifestSource.loadCatalog(): LinuxDistroCatalog {
    return ManifestLinuxDistroCatalog(loadManifest())
}
