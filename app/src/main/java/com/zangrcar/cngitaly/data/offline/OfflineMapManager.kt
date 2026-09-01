package com.zangrcar.cngitaly.data.offline

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class OfflineMapManager(private val filesDir: File) {
    val italyMapFile: File
        get() = File(File(filesDir, MAPS_DIRECTORY), ITALY_MAP_FILE).canonicalFile

    val hasItalyMap: Boolean
        get() = italyMapFile.isFile

    val italyMapSizeBytes: Long
        get() = italyMapFile.takeIf(File::isFile)?.length() ?: 0L

    val italyMapVersion: String?
        get() = italyVersionFile.takeIf(File::isFile)?.readText()?.trim()?.takeIf(String::isNotEmpty)

    fun italyMapUri(): String = "pmtiles://file://${italyMapFile.toURI().rawPath}"

    fun replaceItalyMap(downloadedFile: File, version: String? = null) {
        require(downloadedFile.isFile) { "Downloaded PMTiles file does not exist" }
        val destination = italyMapFile
        destination.parentFile?.mkdirs()
        val stagedMap = File(destination.parentFile, "$ITALY_MAP_FILE.pending")
        downloadedFile.copyTo(stagedMap, overwrite = true)
        try {
            moveReplacing(stagedMap, destination)
        } finally {
            stagedMap.delete()
        }

        if (version.isNullOrBlank()) {
            italyVersionFile.delete()
        } else {
            val temporaryVersion = File(destination.parentFile, "$ITALY_VERSION_FILE.tmp")
            temporaryVersion.writeText(version.trim())
            moveReplacing(temporaryVersion, italyVersionFile)
        }
    }

    fun deleteItalyMap(): Boolean {
        italyVersionFile.delete()
        return !italyMapFile.exists() || italyMapFile.delete()
    }

    private val italyVersionFile: File
        get() = File(italyMapFile.parentFile, ITALY_VERSION_FILE)

    private fun moveReplacing(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val MAPS_DIRECTORY = "maps"
        const val ITALY_MAP_FILE = "italy.pmtiles"
        const val ITALY_VERSION_FILE = "italy.pmtiles.version"
    }
}
