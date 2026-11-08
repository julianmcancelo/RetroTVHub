package com.retro.tvbox.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CacheStats(
    val usedBytes: Long,
    val maxBytes: Long,
    val totalFiles: Int,
    val usedPercentage: Float,
    val maxGbConfigured: Int
)

@Serializable
private data class LRUEntry(
    val gameId: String,
    val filePath: String,
    val sizeBytes: Long,
    var lastAccessedTimestamp: Long
)

/**
 * Gestor de almacenamiento temporal de ROMs (Play-on-Demand).
 * Controla el directorio cacheDir/temp_roms/ aplicando expulsión automática por LRU
 * (Least Recently Used) con cuota de gigabytes configurable de 1 GB a 4 GB.
 *
 * GARANTÍA DE SEGURIDAD CRÍTICA:
 * Incorpora un cerrojo de protección física (isProtectedPath) que impide terminantemente
 * tocar o eliminar cualquier archivo ubicado en filesDir/ (saves, memcards, system, etc.).
 */
class CacheManager(
    private val context: Context,
    initialMaxCacheBytes: Long? = null
) {

    private val tag = "CacheManager"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    companion object {
        private const val PREFS_NAME = "retro_cache_prefs"
        private const val KEY_MAX_CACHE_GB = "max_cache_gb"
        const val DEFAULT_MAX_CACHE_GB = 2 // 2 GB estándar para TV / teléfono
        const val MIN_CACHE_GB = 1
        const val MAX_CACHE_GB = 4
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Cuota en gigabytes configurada por el usuario (1 GB a 4 GB)
    @Volatile
    private var configuredMaxGb: Int = run {
        val savedGb = prefs.getInt(KEY_MAX_CACHE_GB, DEFAULT_MAX_CACHE_GB)
        savedGb.coerceIn(MIN_CACHE_GB, MAX_CACHE_GB)
    }

    // Cuota dinámica en bytes calculada a partir del límite configurado
    val maxCacheBytes: Long
        get() = configuredMaxGb.toLong() * 1024L * 1024L * 1024L

    // Directorio volátil y aislado exclusivamente para ROMs temporales
    val tempRomsDir: File by lazy {
        File(context.cacheDir, "temp_roms").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private val lruIndexFile: File by lazy {
        File(context.cacheDir, "cache_lru_index.json")
    }

    private val memoryLRUMap = mutableMapOf<String, LRUEntry>()

    init {
        if (initialMaxCacheBytes != null) {
            val gb = (initialMaxCacheBytes / (1024L * 1024L * 1024L)).toInt().coerceIn(MIN_CACHE_GB, MAX_CACHE_GB)
            setMaxCacheSizeGb(gb)
        }
        loadLRUIndex()
    }

    /**
     * Obtiene la cuota actual configurada en Gigabytes (1 a 4).
     */
    fun getMaxCacheSizeGb(): Int = configuredMaxGb

    /**
     * Modifica dinámicamente la cuota de la caché (1 GB a 4 GB), persistiendo la preferencia
     * y ejecutando la política LRU de inmediato para ajustar el espacio en disco.
     */
    @Synchronized
    fun setMaxCacheSizeGb(gb: Int) {
        val clamped = gb.coerceIn(MIN_CACHE_GB, MAX_CACHE_GB)
        configuredMaxGb = clamped
        prefs.edit().putInt(KEY_MAX_CACHE_GB, clamped).apply()
        Log.i(tag, "Cuota de caché Play-on-Demand actualizada a: $clamped GB (${maxCacheBytes / (1024 * 1024)} MB)")
        enforceLRU()
    }

    /**
     * CERROJO DE PROTECCIÓN ABSOLUTA:
     * Verifica si una ruta pertenece a los directorios protegidos de partidas (filesDir/saves/),
     * Memory Cards (filesDir/memcards/), BIOS (filesDir/system/), o cualquier subdirectorio de filesDir/.
     * JAMÁS se permite la eliminación de estos archivos bajo ninguna circunstancia.
     */
    fun isProtectedPath(file: File): Boolean {
        return try {
            val canonical = file.canonicalPath
            val filesDirCanonical = context.filesDir.canonicalPath
            val savesCanonical = File(context.filesDir, "saves").canonicalPath
            val memcardsCanonical = File(context.filesDir, "memcards").canonicalPath
            val legacySaves = File(context.filesDir, "user_saves").canonicalPath
            val legacyMemcards = File(context.filesDir, "memory_cards").canonicalPath
            val systemCanonical = File(context.filesDir, "system").canonicalPath
            val statesCanonical = File(context.filesDir, "save_states").canonicalPath

            canonical.startsWith(filesDirCanonical) ||
                canonical.startsWith(savesCanonical) ||
                canonical.startsWith(memcardsCanonical) ||
                canonical.startsWith(legacySaves) ||
                canonical.startsWith(legacyMemcards) ||
                canonical.startsWith(systemCanonical) ||
                canonical.startsWith(statesCanonical)
        } catch (e: Exception) {
            // Ante cualquier fallo de resolución de ruta, tratar como protegido por seguridad
            true
        }
    }

    /**
     * Ejecuta una eliminación validando que el archivo resida obligatoriamente en temp_roms/
     * y no forme parte de las zonas blindadas de guardado.
     */
    private fun safeDelete(file: File): Boolean {
        if (!file.exists()) return true

        if (isProtectedPath(file)) {
            Log.e(tag, "BLOQUEO DE SEGURIDAD: Se evitó la eliminación de archivo permanente protegido: ${file.absolutePath}")
            return false
        }

        return try {
            val inTempDir = file.canonicalPath.startsWith(tempRomsDir.canonicalPath)
            if (!inTempDir) {
                Log.e(tag, "BLOQUEO DE SEGURIDAD: Archivo fuera de temp_roms: ${file.absolutePath}")
                return false
            }
            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (!deleted) {
                Log.w(tag, "No se pudo borrar archivo volátil: ${file.name}")
            }
            deleted
        } catch (e: Exception) {
            Log.e(tag, "Error al eliminar archivo volátil ${file.name}", e)
            false
        }
    }

    /**
     * Obtiene la referencia al archivo de ROM en la caché temporal.
     */
    fun getRomFile(gameId: String, extension: String): File {
        val safeExt = extension.removePrefix(".")
        return File(tempRomsDir, "${sanitize(gameId)}.$safeExt")
    }

    /**
     * Verifica si una ROM ya está descargada y disponible en la caché.
     * Comprueba que exista, que no sea un archivo parcial (.part) y que supere 1 KB (evita archivos corruptos o vacíos).
     */
    fun isRomCached(gameId: String, extension: String): Boolean {
        val file = getRomFile(gameId, extension)
        val exists = file.exists() && !file.name.endsWith(".part") && file.length() > 1024L
        if (exists) {
            markRomAccessed(gameId, file)
        }
        return exists
    }

    /**
     * Elimina tanto el archivo final en temp_roms como cualquier archivo ${gameId}_download.part
     * si existen dentro de tempRomsDir.
     */
    @Synchronized
    fun deleteCorruptedOrPartial(gameId: String, extension: String) {
        val finalRom = getRomFile(gameId, extension)
        if (finalRom.exists()) {
            safeDelete(finalRom)
        }
        val partFile = File(tempRomsDir, "${gameId}_download.part")
        if (partFile.exists()) {
            safeDelete(partFile)
        }
        val sanitizedPartFile = File(tempRomsDir, "${sanitize(gameId)}_download.part")
        if (sanitizedPartFile.exists()) {
            safeDelete(sanitizedPartFile)
        }
        memoryLRUMap.remove(gameId)
        persistLRUIndex()
    }

    /**
     * Registra o actualiza el acceso a una ROM para la política LRU.
     */
    @Synchronized
    fun markRomAccessed(gameId: String, file: File) {
        if (!file.exists()) return
        file.setLastModified(System.currentTimeMillis())

        memoryLRUMap[gameId] = LRUEntry(
            gameId = gameId,
            filePath = file.absolutePath,
            sizeBytes = file.length(),
            lastAccessedTimestamp = System.currentTimeMillis()
        )
        persistLRUIndex()
    }

    /**
     * Purga obligatoria inmediata de la ROM al pulsar salir del juego (Play-on-Demand).
     * Libera de inmediato el espacio de la ROM temporal garantizando que savesDir y memcardsDir
     * se mantengan intactos.
     */
    @Synchronized
    fun purgeOnExit(gameId: String): Boolean {
        Log.i(tag, "Ejecutando política de purga inmediata al salir para: $gameId")
        val sanitized = sanitize(gameId)
        val matchingFiles = tempRomsDir.listFiles { file ->
            file.nameWithoutExtension == sanitized ||
                file.name.startsWith(sanitized) ||
                file.name.startsWith("${sanitized}_")
        }

        var allDeleted = true
        matchingFiles?.forEach { f ->
            val deleted = safeDelete(f)
            if (!deleted) {
                allDeleted = false
            }
        }

        memoryLRUMap.remove(gameId)
        persistLRUIndex()
        Log.i(tag, "Purga al salir completada para $gameId. Resultado: $allDeleted")
        return allDeleted
    }

    /**
     * Aplica la política LRU cuando el tamaño total excede la cuota configurada (1 GB a 4 GB).
     * Expulsa los juegos menos recientemente usados hasta volver al 75% del límite para
     * dar margen a nuevas descargas.
     */
    @Synchronized
    fun enforceLRU() {
        val currentSize = calculateTotalCacheSize()
        val limit = maxCacheBytes

        if (currentSize <= limit) {
            return
        }

        Log.w(
            tag,
            "Límite de caché superado (${currentSize / (1024 * 1024)} MB / ${limit / (1024 * 1024)} MB). " +
                "Iniciando purga preventiva LRU..."
        )

        val targetSizeBytes = (limit * 0.75).toLong()
        var accumulatedSize = currentSize

        // Ordenar registros por fecha de último acceso ascendente (los más antiguos primero)
        val sortedEntries = memoryLRUMap.values.sortedBy { it.lastAccessedTimestamp }

        for (entry in sortedEntries) {
            if (accumulatedSize <= targetSizeBytes) break

            val file = File(entry.filePath)
            if (file.exists()) {
                val fileSize = file.length()
                if (safeDelete(file)) {
                    accumulatedSize -= fileSize
                    Log.i(tag, "LRU Evicted: ${file.name} (liberados ${fileSize / (1024 * 1024)} MB)")
                }
            }
            memoryLRUMap.remove(entry.gameId)
        }

        // Si persisten archivos huérfanos sin registrar en el índice LRU, barrer el directorio
        if (accumulatedSize > targetSizeBytes) {
            val orphanFiles = tempRomsDir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            for (orphan in orphanFiles) {
                if (accumulatedSize <= targetSizeBytes) break
                val s = orphan.length()
                if (safeDelete(orphan)) {
                    accumulatedSize -= s
                }
            }
        }

        persistLRUIndex()
        Log.i(tag, "Purga LRU finalizada. Espacio restante en caché: ${accumulatedSize / (1024 * 1024)} MB")
    }

    /**
     * Devuelve las estadísticas actuales del almacenamiento de caché.
     */
    fun getStats(): CacheStats {
        val used = calculateTotalCacheSize()
        val files = tempRomsDir.listFiles()?.size ?: 0
        val percentage = (used.toFloat() / maxCacheBytes.toFloat()) * 100f
        return CacheStats(
            usedBytes = used,
            maxBytes = maxCacheBytes,
            totalFiles = files,
            usedPercentage = percentage.coerceIn(0f, 100f),
            maxGbConfigured = configuredMaxGb
        )
    }

    /**
     * Limpia completamente toda la caché volátil de ROMs.
     */
    @Synchronized
    fun clearAll(): Long {
        var freed = 0L
        tempRomsDir.listFiles()?.forEach { file ->
            val len = file.length()
            if (safeDelete(file)) {
                freed += len
            }
        }
        memoryLRUMap.clear()
        if (lruIndexFile.exists()) {
            lruIndexFile.delete()
        }
        Log.i(tag, "Caché de ROMs vaciada completamente. Bytes liberados: $freed")
        return freed
    }

    private fun calculateTotalCacheSize(): Long {
        var total = 0L
        tempRomsDir.walkTopDown().forEach {
            if (it.isFile) {
                total += it.length()
            }
        }
        return total
    }

    private fun loadLRUIndex() {
        if (!lruIndexFile.exists()) return
        try {
            val list = json.decodeFromString<List<LRUEntry>>(lruIndexFile.readText())
            list.forEach { memoryLRUMap[it.gameId] = it }
        } catch (e: Exception) {
            Log.e(tag, "Error leyendo índice LRU, reindexando directorio", e)
            rebuildIndexFromFiles()
        }
    }

    private fun persistLRUIndex() {
        try {
            val list = memoryLRUMap.values.toList()
            lruIndexFile.writeText(json.encodeToString(list))
        } catch (e: Exception) {
            Log.e(tag, "Error guardando índice LRU", e)
        }
    }

    private fun rebuildIndexFromFiles() {
        memoryLRUMap.clear()
        tempRomsDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val id = file.nameWithoutExtension
                memoryLRUMap[id] = LRUEntry(
                    gameId = id,
                    filePath = file.absolutePath,
                    sizeBytes = file.length(),
                    lastAccessedTimestamp = file.lastModified()
                )
            }
        }
        persistLRUIndex()
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
