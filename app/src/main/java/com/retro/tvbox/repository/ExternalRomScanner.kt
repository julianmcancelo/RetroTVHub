package com.retro.tvbox.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Escáner de ROMs externas y descargadas.
 * Detecta automáticamente juegos descargados desde el navegador (ej: wowroms.com, archive.org),
 * carpetas locales (/sdcard/Download, /sdcard/ROMs) y memorias USB conectadas a la Android TV.
 */
object ExternalRomScanner {
    private const val TAG = "ExternalRomScanner"

    // Mapeo de extensiones a consola de emulación
    private val EXTENSION_MAP = mapOf(
        // Sony PlayStation 1
        "chd" to "psx",
        "iso" to "psx",
        "cue" to "psx",
        "bin" to "psx",
        "pbp" to "psx",
        // Super Nintendo
        "sfc" to "snes",
        "smc" to "snes",
        // Sega Genesis / Mega Drive
        "md" to "genesis",
        "gen" to "genesis",
        // Game Boy Advance
        "gba" to "gba",
        // NES / Famicom
        "nes" to "nes",
        // Nintendo 64
        "z64" to "n64",
        "n64" to "n64",
        "v64" to "n64"
    )

    fun scanAllExternalRoms(context: Context): List<GameEntry> {
        val foundGames = mutableListOf<GameEntry>()
        val scannedDirs = mutableListOf<File>()

        try {
            // 1. Directorio público de descargas (/sdcard/Download) - donde van las descargas de WowRoms / Chrome
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && downloadDir.exists() && downloadDir.isDirectory) {
                scannedDirs.add(downloadDir)
            }

            // 2. /sdcard/ROMs, /sdcard/Roms, /sdcard/RetroTV
            val storageRoot = Environment.getExternalStorageDirectory()
            if (storageRoot != null && storageRoot.exists()) {
                listOf("ROMs", "Roms", "RetroTV", "Games").forEach { folderName ->
                    val dir = File(storageRoot, folderName)
                    if (dir.exists() && dir.isDirectory) {
                        scannedDirs.add(dir)
                    }
                }
            }

            // 3. Unidades USB / Pendrives (/storage/XXXX-XXXX)
            val externalStorageRoot = File("/storage")
            if (externalStorageRoot.exists() && externalStorageRoot.isDirectory) {
                externalStorageRoot.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.canRead() && file.name != "emulated" && file.name != "self") {
                        scannedDirs.add(file)
                    }
                }
            }

            // Recorrer directorios detectados
            val visitedPaths = mutableSetOf<String>()
            scannedDirs.forEach { baseDir ->
                baseDir.walkTopDown()
                    .maxDepth(3)
                    .filter { it.isFile && it.canRead() && it.length() > 2048 }
                    .forEach { file ->
                        val ext = file.extension.lowercase()
                        val consoleId = EXTENSION_MAP[ext]
                        if (consoleId != null && visitedPaths.add(file.absolutePath)) {
                            val cleanTitle = file.nameWithoutExtension
                                .replace("_", " ")
                                .replace(Regex("\\(.*\\)|\\[.*\\]"), "")
                                .trim()

                            val sourceName = if (file.absolutePath.contains("Download", ignoreCase = true)) {
                                "Descargas (WowRoms / Web)"
                            } else if (file.absolutePath.startsWith("/storage/") && !file.absolutePath.contains("emulated")) {
                                "Memoria USB / Disco Externo"
                            } else {
                                "Almacenamiento Local"
                            }

                            foundGames.add(
                                GameEntry(
                                    id = "local_${file.absolutePath.hashCode()}",
                                    consoleId = consoleId,
                                    title = cleanTitle.ifBlank { file.nameWithoutExtension },
                                    fileUrl = file.absolutePath, // Ruta directa al archivo
                                    isCompressed = false,
                                    expectedExtension = ext,
                                    sizeBytes = file.length(),
                                    description = "Juego detectado en $sourceName (${file.name}). Listo para jugar al instante con renderizado nativo.",
                                    genre = "ROM Local / WowRoms",
                                    releaseYear = 2024,
                                    maxPlayers = 2,
                                    coverUrl = ""
                                )
                            )
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error durante el escaneo de ROMs externas", e)
        }

        Log.i(TAG, "Escaneo finalizado. Se detectaron ${foundGames.size} ROMs externas.")
        return foundGames
    }
}
