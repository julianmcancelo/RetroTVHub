package com.retro.tvbox.save

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Serializable
data class SaveStateInfo(
    val gameId: String,
    val slot: Int,
    val timestamp: Long,
    val stateSizeBytes: Long,
    val hasScreenshot: Boolean,
    val screenshotPath: String? = null,
    val note: String = ""
)

/**
 * Servicio encargado de gestionar el guardado y carga de Savestates en tiempo de ejecución,
 * asociando a cada ranura (slot) una captura gráfica en formato PNG para visualización en OSD.
 */
class SaveStateService(private val context: Context) {

    private val tag = "SaveStateService"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val statesDir: File by lazy {
        File(context.filesDir, "save_states").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun getStateFile(gameId: String, slot: Int): File {
        val safeId = sanitize(gameId)
        return File(statesDir, "${safeId}_slot$slot.state")
    }

    private fun getScreenshotFile(gameId: String, slot: Int): File {
        val safeId = sanitize(gameId)
        return File(statesDir, "${safeId}_slot$slot.png")
    }

    private fun getMetaFile(gameId: String, slot: Int): File {
        val safeId = sanitize(gameId)
        return File(statesDir, "${safeId}_slot$slot.json")
    }

    /**
     * Guarda el estado binario y su captura PNG asociada de forma atómica.
     */
    fun saveStateWithScreenshot(
        gameId: String,
        slot: Int,
        stateData: ByteArray,
        screenshotBitmap: Bitmap?,
        note: String = ""
    ): Boolean {
        if (stateData.isEmpty()) {
            Log.e(tag, "No se puede guardar un savestate vacío para $gameId slot $slot")
            return false
        }

        val stateFile = getStateFile(gameId, slot)
        val screenshotFile = getScreenshotFile(gameId, slot)
        val metaFile = getMetaFile(gameId, slot)

        val tmpState = File(statesDir, "${stateFile.name}.tmp")
        val tmpShot = File(statesDir, "${screenshotFile.name}.tmp")

        return try {
            // 1. Escribir binario del estado
            FileOutputStream(tmpState).use { fos ->
                fos.write(stateData)
                fos.flush()
                fos.fd.sync()
            }
            tmpState.renameTo(stateFile)

            // 2. Comprimir y persistir captura PNG de pantalla si fue provista
            var hasShot = false
            if (screenshotBitmap != null && !screenshotBitmap.isRecycled) {
                FileOutputStream(tmpShot).use { fos ->
                    screenshotBitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
                    fos.flush()
                    fos.fd.sync()
                }
                tmpShot.renameTo(screenshotFile)
                hasShot = true
            }

            // 3. Escribir metadatos JSON
            val info = SaveStateInfo(
                gameId = gameId,
                slot = slot,
                timestamp = System.currentTimeMillis(),
                stateSizeBytes = stateFile.length(),
                hasScreenshot = hasShot,
                screenshotPath = if (hasShot) screenshotFile.absolutePath else null,
                note = note
            )
            metaFile.writeText(json.encodeToString(info))

            Log.i(tag, "Savestate guardado con éxito: $gameId [Slot $slot]")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error guardando savestate $gameId slot $slot", e)
            if (tmpState.exists()) tmpState.delete()
            if (tmpShot.exists()) tmpShot.delete()
            false
        }
    }

    /**
     * Carga el bloque de memoria binaria del savestate.
     */
    fun loadState(gameId: String, slot: Int): ByteArray? {
        val stateFile = getStateFile(gameId, slot)
        if (!stateFile.exists() || stateFile.length() == 0L) {
            Log.w(tag, "El savestate no existe para $gameId [Slot $slot]")
            return null
        }
        return try {
            stateFile.readBytes()
        } catch (e: IOException) {
            Log.e(tag, "Error al leer savestate $gameId [Slot $slot]", e)
            null
        }
    }

    /**
     * Carga el Bitmap de la captura de pantalla guardada.
     */
    fun loadScreenshot(gameId: String, slot: Int): Bitmap? {
        val shotFile = getScreenshotFile(gameId, slot)
        if (!shotFile.exists()) return null
        return try {
            BitmapFactory.decodeFile(shotFile.absolutePath)
        } catch (e: Exception) {
            Log.e(tag, "Error decodificando screenshot para $gameId [Slot $slot]", e)
            null
        }
    }

    /**
     * Obtiene los metadatos de un slot particular.
     */
    fun getSlotInfo(gameId: String, slot: Int): SaveStateInfo? {
        val metaFile = getMetaFile(gameId, slot)
        val stateFile = getStateFile(gameId, slot)

        if (!stateFile.exists()) return null

        if (metaFile.exists()) {
            return try {
                json.decodeFromString<SaveStateInfo>(metaFile.readText())
            } catch (e: Exception) {
                null
            }
        }

        val shotFile = getScreenshotFile(gameId, slot)
        return SaveStateInfo(
            gameId = gameId,
            slot = slot,
            timestamp = stateFile.lastModified(),
            stateSizeBytes = stateFile.length(),
            hasScreenshot = shotFile.exists(),
            screenshotPath = if (shotFile.exists()) shotFile.absolutePath else null
        )
    }

    /**
     * Lista todos los estados disponibles para un juego.
     */
    fun listGameStates(gameId: String): List<SaveStateInfo> {
        val list = mutableListOf<SaveStateInfo>()
        // Consideramos hasta 10 slots
        for (slot in 0..9) {
            val info = getSlotInfo(gameId, slot)
            if (info != null) {
                list.add(info)
            }
        }
        return list
    }

    /**
     * Elimina el estado y su captura.
     */
    fun deleteState(gameId: String, slot: Int): Boolean {
        val stateFile = getStateFile(gameId, slot)
        val shotFile = getScreenshotFile(gameId, slot)
        val metaFile = getMetaFile(gameId, slot)

        var deleted = true
        if (stateFile.exists() && !stateFile.delete()) deleted = false
        if (shotFile.exists()) shotFile.delete()
        if (metaFile.exists()) metaFile.delete()

        return deleted
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
