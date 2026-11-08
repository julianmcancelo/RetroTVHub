package com.retro.tvbox.core

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class CoreDownloadProgress(
    val coreId: String,
    val progressPercent: Int,
    val isDone: Boolean = false,
    val error: String? = null,
    val statusMessage: String? = null
)

class CoreManager(private val context: Context) {

    private val tag = "CoreManager"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val coresDir: File = File(context.filesDir, "cores").apply { if (!exists()) mkdirs() }
    val systemDir: File = File(context.filesDir, "system").apply { if (!exists()) mkdirs() }

    companion object {
        const val BUILDBOT_BASE = "https://buildbot.libretro.com/nightly/android/latest"

        // Map console ID / aliases to official Libretro core name
        val CONSOLE_TO_CORE = mapOf(
            "snes" to "snes9x",
            "sfc" to "snes9x",
            "nes" to "fceumm",
            "fc" to "fceumm",
            "famicom" to "fceumm",
            "genesis" to "genesis_plus_gx",
            "megadrive" to "genesis_plus_gx",
            "md" to "genesis_plus_gx",
            "sega" to "genesis_plus_gx",
            "psx" to "pcsx_rearmed",
            "ps1" to "pcsx_rearmed",
            "playstation" to "pcsx_rearmed",
            "n64" to "parallel_n64",
            "nintendo64" to "parallel_n64",
            "gba" to "mgba",
            "gameboyadvance" to "mgba"
        )

        // Human-friendly core description
        val CORE_DESCRIPTIONS = mapOf(
            "snes" to "Snes9x (Super Nintendo)",
            "nes" to "FCEUmm (Nintendo Entertainment System)",
            "genesis" to "Genesis Plus GX (Sega Genesis / Mega Drive)",
            "psx" to "PCSX-ReARMed (Sony PlayStation 1)",
            "n64" to "ParaLLEl N64 (Nintendo 64)",
            "gba" to "mGBA (Game Boy Advance)"
        )
    }

    /**
     * Devuelve el nombre base oficial del core para la consola indicada.
     */
    fun getCoreName(consoleId: String): String {
        return CONSOLE_TO_CORE[consoleId.lowercase().trim()] ?: "snes9x"
    }

    /**
     * Retorna una descripción legible del core para la interfaz de usuario.
     */
    fun getCoreDescription(consoleId: String): String {
        return CORE_DESCRIPTIONS[consoleId.lowercase().trim()] ?: getCoreName(consoleId).uppercase()
    }

    /**
     * Obtiene la lista ordenada de ABIs compatibles soportadas por el dispositivo y el Buildbot.
     */
    fun getDeviceSupportedAbis(): List<String> {
        val buildbotAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        val supported = mutableListOf<String>()
        for (abi in Build.SUPPORTED_ABIS) {
            if (abi in buildbotAbis && abi !in supported) {
                supported.add(abi)
            }
        }
        if (supported.isEmpty()) {
            supported.add("arm64-v8a")
        }
        return supported
    }

    /**
     * Determina la arquitectura principal del dispositivo (arm64-v8a, armeabi-v7a, x86_64, x86).
     */
    fun getDeviceAbi(): String {
        return getDeviceSupportedAbis().first()
    }

    /**
     * Retorna los posibles nombres de archivo del core en el Buildbot.
     * En N64, Buildbot publica 'mupen64plus_next_gles3' o 'mupen64plus_next_gles2'.
     */
    fun getBuildbotCoreCandidates(consoleId: String): List<String> {
        val coreName = getCoreName(consoleId)
        return when (coreName) {
            "parallel_n64", "mupen64plus_next" -> listOf(
                "parallel_n64",
                "mupen64plus_next_gles3",
                "mupen64plus_next_gles2"
            )
            else -> listOf(coreName)
        }
    }

    /**
     * Obtiene la ruta al archivo .so local del core.
     */
    fun getCoreFile(consoleId: String): File {
        val coreName = getCoreName(consoleId)
        val defaultFile = File(coresDir, "${coreName}_libretro_android.so")
        if (defaultFile.exists()) return defaultFile

        // Si se instaló con un candidato alternativo (ej. gles3), retornar el archivo existente
        for (candidate in getBuildbotCoreCandidates(consoleId)) {
            val candidateFile = File(coresDir, "${candidate}_libretro_android.so")
            if (candidateFile.exists()) return candidateFile
        }
        return defaultFile
    }

    /**
     * Comprueba si el núcleo está instalado y es un binario válido.
     */
    fun isCoreInstalled(consoleId: String): Boolean {
        val file = getCoreFile(consoleId)
        if (file.exists() && file.length() > 50_000 && isValidElf(file)) {
            return true
        }
        for (candidate in getBuildbotCoreCandidates(consoleId)) {
            val candidateFile = File(coresDir, "${candidate}_libretro_android.so")
            if (candidateFile.exists() && candidateFile.length() > 50_000 && isValidElf(candidateFile)) {
                return true
            }
        }
        return false
    }

    /**
     * Valida si un archivo posee la cabecera ELF mágica (0x7F, 'E', 'L', 'F').
     */
    private fun isValidElf(file: File): Boolean {
        if (!file.exists() || file.length() < 52) return false
        return try {
            file.inputStream().use { stream ->
                val magic = ByteArray(4)
                val read = stream.read(magic)
                read == 4 && magic[0] == 0x7F.toByte() && magic[1] == 'E'.code.toByte() && magic[2] == 'L'.code.toByte() && magic[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Descarga y extrae de forma segura el archivo .so del core desde Libretro Buildbot.
     */
    fun downloadCoreFlow(consoleId: String): Flow<CoreDownloadProgress> = flow {
        val candidates = getBuildbotCoreCandidates(consoleId)
        val abis = getDeviceSupportedAbis()
        val targetSo = getCoreFile(consoleId)

        emit(CoreDownloadProgress(consoleId, 0, statusMessage = "Iniciando descarga de núcleo..."))

        var downloadSuccess = false
        var lastErrorMessage = "No se pudo conectar con el servidor"

        // Probar candidatos de cores y arquitecturas compatibles
        candidateLoop@ for (abi in abis) {
            for (coreCandidate in candidates) {
                val zipUrl = "$BUILDBOT_BASE/$abi/${coreCandidate}_libretro_android.so.zip"
                Log.i(tag, "Intentando descargar core [$consoleId] desde: $zipUrl")
                emit(CoreDownloadProgress(consoleId, 5, statusMessage = "Conectando a Buildbot ($abi)..."))

                val tempZip = File(coresDir, "${coreCandidate}_temp_${System.currentTimeMillis()}.zip")
                val tempSo = File(coresDir, "${targetSo.name}.tmp_${System.currentTimeMillis()}")

                try {
                    val request = Request.Builder()
                        .url(zipUrl)
                        .header("User-Agent", "RetroTVBox-Android/1.0")
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful || response.body == null) {
                        lastErrorMessage = "HTTP ${response.code} en $zipUrl"
                        response.close()
                        continue
                    }

                    val body = response.body!!
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L

                    emit(CoreDownloadProgress(consoleId, 10, statusMessage = "Descargando binario nativo ($coreCandidate)..."))

                    FileOutputStream(tempZip).use { fos ->
                        val buffer = ByteArray(16384)
                        val stream = body.byteStream()
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val percent = 10 + ((downloadedBytes * 75) / totalBytes).toInt()
                                emit(CoreDownloadProgress(consoleId, percent.coerceAtMost(85), statusMessage = "Descargando: $percent%"))
                            }
                        }
                        fos.flush()
                    }

                    emit(CoreDownloadProgress(consoleId, 88, statusMessage = "Extrayendo y verificando integridad ELF..."))

                    // Extracción segura del .so con mitigación contra Zip Slip
                    var extracted = false
                    ZipInputStream(tempZip.inputStream().buffered()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name
                            if (!entry.isDirectory && entryName.endsWith(".so", ignoreCase = true)) {
                                // Protección Zip Slip
                                val canonicalDest = File(coresDir, entryName).canonicalPath
                                if (!canonicalDest.startsWith(coresDir.canonicalPath + File.separator)) {
                                    throw SecurityException("Zip Slip detectado en entrada de archivo: $entryName")
                                }

                                FileOutputStream(tempSo).use { fos ->
                                    zis.copyTo(fos)
                                    fos.flush()
                                    fos.fd.sync()
                                }

                                if (isValidElf(tempSo)) {
                                    extracted = true
                                    break
                                } else {
                                    Log.w(tag, "Archivo extraído no es un ELF válido: $entryName")
                                }
                            }
                            entry = zis.nextEntry
                        }
                    }

                    if (extracted && tempSo.exists() && tempSo.length() > 0) {
                        if (targetSo.exists()) {
                            targetSo.delete()
                        }
                        if (!tempSo.renameTo(targetSo)) {
                            tempSo.copyTo(targetSo, overwrite = true)
                            tempSo.delete()
                        }
                        targetSo.setReadable(true, false)
                        targetSo.setExecutable(true, false)

                        downloadSuccess = true
                        emit(CoreDownloadProgress(consoleId, 100, isDone = true, statusMessage = "Núcleo instalado correctamente"))
                        Log.i(tag, "Núcleo $consoleId ($coreCandidate) instalado exitosamente en ${targetSo.absolutePath} (${targetSo.length()} bytes)")
                        break@candidateLoop
                    } else {
                        lastErrorMessage = "No se encontró ningún binario .so válido en el archivo ZIP"
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Fallo al procesar descarga de $zipUrl: ${e.message}")
                    lastErrorMessage = e.localizedMessage ?: "Error de descarga"
                } finally {
                    if (tempZip.exists()) tempZip.delete()
                    if (tempSo.exists()) tempSo.delete()
                }
            }
        }

        if (!downloadSuccess) {
            emit(CoreDownloadProgress(consoleId, 0, isDone = false, error = lastErrorMessage, statusMessage = "Error: $lastErrorMessage"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Elimina el archivo del core si existe.
     */
    fun deleteCore(consoleId: String): Boolean {
        val file = getCoreFile(consoleId)
        return if (file.exists()) file.delete() else false
    }
}
