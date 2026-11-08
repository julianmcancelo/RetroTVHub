package com.retro.tvbox.save

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SaveFileInfo(
    val gameId: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val hasBackup: Boolean
)

/**
 * Gestor seguro de partidas guardadas (SRAM / Cartridge Saves).
 * Opera en filesDir/saves/, un directorio aislado, permanente y blindado contra
 * cualquier purga o limpieza de caché del sistema o del módulo Play-on-Demand.
 */
class SaveManager(private val context: Context) {

    private val tag = "SaveManager"

    // Directorio persistente e inviolable en el almacenamiento privado de la app
    val savesDir: File by lazy {
        File(context.filesDir, "saves").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    init {
        migrateLegacySavesIfNeeded()
    }

    /**
     * Migra de forma transparente los archivos previos desde filesDir/user_saves/ a filesDir/saves/.
     */
    private fun migrateLegacySavesIfNeeded() {
        try {
            val legacyDir = File(context.filesDir, "user_saves")
            if (legacyDir.exists() && legacyDir.isDirectory) {
                legacyDir.listFiles()?.forEach { file ->
                    val dest = File(savesDir, file.name)
                    if (!dest.exists()) {
                        file.copyTo(dest, overwrite = true)
                        Log.i(tag, "Partida migrada con éxito a ubicación protegida: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Aviso durante la migración de partidas guardadas heredadas", e)
        }
    }

    /**
     * Valida si un archivo determinado pertenece a la zona de persistencia de partidas.
     */
    fun isProtectedFile(file: File): Boolean {
        return try {
            file.canonicalPath.startsWith(savesDir.canonicalPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtiene la ruta al archivo SRAM oficial para un identificador de juego.
     */
    fun getSaveFile(gameId: String, extension: String = "srm"): File {
        val sanitizedId = sanitizeFilename(gameId)
        return File(savesDir, "$sanitizedId.$extension")
    }

    /**
     * Obtiene el archivo de respaldo previo (.bak).
     */
    fun getBackupFile(gameId: String, extension: String = "srm"): File {
        val sanitizedId = sanitizeFilename(gameId)
        return File(savesDir, "$sanitizedId.$extension.bak")
    }

    /**
     * Comprueba si existe partida guardada para el juego.
     */
    fun hasSRAM(gameId: String): Boolean {
        val file = getSaveFile(gameId)
        val altFile = getSaveFile(gameId, "sav")
        return (file.exists() && file.length() > 0) || (altFile.exists() && altFile.length() > 0)
    }

    /**
     * Lee el contenido completo de la memoria SRAM.
     */
    fun readSRAM(gameId: String): ByteArray? {
        val saveFile = when {
            getSaveFile(gameId).exists() -> getSaveFile(gameId)
            getSaveFile(gameId, "sav").exists() -> getSaveFile(gameId, "sav")
            else -> null
        }

        if (saveFile == null || !saveFile.exists() || saveFile.length() == 0L) {
            Log.d(tag, "No existe SRAM guardada para: $gameId")
            return null
        }

        return try {
            saveFile.readBytes()
        } catch (e: IOException) {
            Log.e(tag, "Error al leer SRAM de $gameId", e)
            null
        }
    }

    /**
     * Escribe la SRAM de forma atómica y sincronizada con el disco físico.
     * Genera automáticamente una copia .bak antes del reemplazo atómico para prevenir
     * corrupción ante caídas de tensión o cierres bruscos del proceso.
     */
    @Synchronized
    fun writeSRAM(gameId: String, data: ByteArray, extension: String = "srm"): Boolean {
        if (data.isEmpty()) {
            Log.w(tag, "Ignorando guardado de SRAM vacía para $gameId")
            return false
        }

        val targetFile = getSaveFile(gameId, extension)
        val backupFile = getBackupFile(gameId, extension)
        val tempFile = File(savesDir, "${targetFile.name}.tmp")

        return try {
            // 1. Escritura y forzado fsync en archivo temporal dentro del mismo directorio
            FileOutputStream(tempFile).use { fos ->
                fos.write(data)
                fos.flush()
                fos.fd.sync()
            }

            // 2. Si ya existía un save funcional, actualizar el respaldo .bak
            if (targetFile.exists() && targetFile.length() > 0) {
                if (backupFile.exists()) {
                    backupFile.delete()
                }
                targetFile.copyTo(backupFile, overwrite = true)
            }

            // 3. Reemplazo atómico
            if (targetFile.exists() && !targetFile.delete()) {
                Log.w(tag, "No se pudo eliminar archivo destino antes del swap atómico")
            }

            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            Log.i(tag, "SRAM persistida con éxito en filesDir/saves para $gameId (${data.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(tag, "Fallo crítico al escribir SRAM para $gameId", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            false
        }
    }

    /**
     * Lista todas las partidas guardadas con sus metadatos.
     */
    fun listSaves(): List<SaveFileInfo> {
        val files = savesDir.listFiles { file ->
            val ext = file.extension.lowercase()
            file.isFile && (ext == "srm" || ext == "sav")
        } ?: return emptyList()

        return files.map { file ->
            val baseName = file.nameWithoutExtension
            val backup = File(file.parentFile, "${file.name}.bak")
            SaveFileInfo(
                gameId = baseName,
                fileName = file.name,
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
                hasBackup = backup.exists()
            )
        }.sortedByDescending { it.lastModified }
    }

    /**
     * Exporta el archivo SRAM a un Uri elegido por el usuario vía Storage Access Framework.
     */
    fun exportSaveToUri(gameId: String, destinationUri: Uri): Result<Unit> {
        val src = getSaveFile(gameId)
        val fileToExport = if (src.exists()) src else getSaveFile(gameId, "sav")
        if (!fileToExport.exists()) {
            return Result.failure(Exception("No existe partida para exportar: $gameId"))
        }

        return try {
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                FileInputStream(fileToExport).use { input ->
                    input.copyTo(output)
                    output.flush()
                }
            } ?: return Result.failure(Exception("No se pudo abrir el stream de salida para $destinationUri"))

            Log.i(tag, "Partida $gameId exportada a Uri: $destinationUri")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error exportando partida de $gameId a SAF", e)
            Result.failure(e)
        }
    }

    /**
     * Respalda la partida de un juego a la carpeta pública de Descargas o almacenamiento externo accesible.
     */
    fun backupSaveToDownloads(gameId: String): Result<File> {
        val src = getSaveFile(gameId)
        val fileToBackup = if (src.exists()) src else getSaveFile(gameId, "sav")
        if (!fileToBackup.exists()) {
            return Result.failure(Exception("No existe partida para respaldar: $gameId"))
        }

        return try {
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupDir = File(baseDir, "RetroTV_Saves").apply { if (!exists()) mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val targetFile = File(backupDir, "${fileToBackup.nameWithoutExtension}_backup_$timeStamp.${fileToBackup.extension}")

            fileToBackup.copyTo(targetFile, overwrite = true)
            Log.i(tag, "Respaldo de partida creado con éxito en: ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            // Fallback al directorio externo privado de la app si el directorio público está bloqueado por Scoped Storage
            try {
                val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "RetroTV_Saves").apply {
                    if (!exists()) mkdirs()
                }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val targetFile = File(fallbackDir, "${fileToBackup.nameWithoutExtension}_backup_$timeStamp.${fileToBackup.extension}")
                fileToBackup.copyTo(targetFile, overwrite = true)
                Log.i(tag, "Respaldo en almacenamiento externo de la app: ${targetFile.absolutePath}")
                Result.success(targetFile)
            } catch (fallbackEx: Exception) {
                Log.e(tag, "Error generando respaldo de partida en descargas", fallbackEx)
                Result.failure(fallbackEx)
            }
        }
    }

    /**
     * Respalda masivamente todas las partidas hacia un pendrive USB o directorio indicado.
     */
    fun backupAllSavesToDirectory(destinationDir: File): Result<List<File>> {
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            return Result.failure(Exception("No se pudo acceder o crear el directorio destino: ${destinationDir.absolutePath}"))
        }

        val backedUp = mutableListOf<File>()
        val files = savesDir.listFiles { f ->
            val ext = f.extension.lowercase()
            f.isFile && (ext == "srm" || ext == "sav")
        } ?: emptyArray()

        for (file in files) {
            try {
                val target = File(destinationDir, file.name)
                file.copyTo(target, overwrite = true)
                backedUp.add(target)
            } catch (e: Exception) {
                Log.e(tag, "Error copiando partida ${file.name} a ${destinationDir.absolutePath}", e)
            }
        }

        Log.i(tag, "Respaldadas ${backedUp.size} partidas en ${destinationDir.absolutePath}")
        return Result.success(backedUp)
    }

    /**
     * Importa una partida SRAM desde un Uri (SAF) hacia la zona segura filesDir/saves/.
     */
    fun importSaveFromUri(sourceUri: Uri, gameId: String, extension: String = "srm"): Result<File> {
        val targetFile = getSaveFile(gameId, extension)
        val tempFile = File(savesDir, "${targetFile.name}.import.tmp")

        return try {
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: return Result.failure(Exception("No se pudo abrir el stream de lectura para $sourceUri"))

            input.use { inStream ->
                FileOutputStream(tempFile).use { outStream ->
                    inStream.copyTo(outStream)
                    outStream.flush()
                }
            }

            if (tempFile.length() == 0L) {
                tempFile.delete()
                return Result.failure(Exception("El archivo de partida importado está vacío"))
            }

            // Backup si ya existía
            if (targetFile.exists()) {
                val bak = getBackupFile(gameId, extension)
                targetFile.copyTo(bak, overwrite = true)
                targetFile.delete()
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            Log.i(tag, "Partida SRAM importada exitosamente para $gameId (${targetFile.length()} bytes)")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(tag, "Fallo importando partida desde Uri para $gameId", e)
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
