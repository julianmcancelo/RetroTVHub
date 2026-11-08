package com.retro.tvbox.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.retro.tvbox.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    val size: Long = 0,
    val browser_download_url: String
)

data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkSize: Long
)

class UpdateManager(private val context: Context) {

    private val tag = "UpdateManager"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Consulta el endpoint público de GitHub Releases para verificar si hay una nueva versión.
     */
    suspend fun checkForUpdates(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/julianmcancelo/RetroTVHub/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "RetroTVHub-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) return@withContext Result.success(null)
                    throw IOException("HTTP error ${response.code}: ${response.message}")
                }

                val body = response.body?.string() ?: throw IOException("Respuesta vacía de GitHub")
                val release = json.decodeFromString<GitHubRelease>(body)

                val currentVersion = BuildConfig.VERSION_NAME.trimStart('v')
                val latestVersion = release.tag_name.trimStart('v')

                if (isNewerVersion(latestVersion, currentVersion)) {
                    val apkAsset = release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
                    if (apkAsset != null) {
                        val info = UpdateInfo(
                            version = release.tag_name,
                            releaseNotes = release.body ?: "Nueva versión disponible.",
                            apkUrl = apkAsset.browser_download_url,
                            apkSize = apkAsset.size
                        )
                        Log.i(tag, "Actualización disponible: ${info.version} (Actual: $currentVersion)")
                        return@withContext Result.success(info)
                    }
                }

                Log.i(tag, "La app está en la versión más reciente ($currentVersion)")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error al comprobar actualizaciones: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Descarga el APK al directorio seguro de caché e invoca el instalador del sistema.
     */
    suspend fun downloadAndInstallUpdate(
        updateInfo: UpdateInfo,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
            val apkFile = File(updatesDir, "RetroTVHub_${updateInfo.version}.apk")

            val request = Request.Builder()
                .url(updateInfo.apkUrl)
                .header("User-Agent", "RetroTVHub-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Fallo en descarga: HTTP ${response.code}")

                val body = response.body ?: throw IOException("Cuerpo vacío de descarga")
                val totalBytes = if (updateInfo.apkSize > 0) updateInfo.apkSize else body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                                onProgress(progress.coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }
            }

            Log.i(tag, "APK descargado exitosamente: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
            installApk(apkFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error descargando o instalando actualización", e)
            Result.failure(e)
        }
    }

    private fun installApk(file: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split('.').mapNotNull { it.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
