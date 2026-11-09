package com.retro.tvbox.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Despachador de Intents para lanzar emuladores externos de alto rendimiento
 * como AetherSX2 / NetherSX2 (PlayStation 2) en Android TV.
 */
object ExternalLauncher {

    private const val TAG = "ExternalLauncher"

    // Paquetes conocidos de emuladores de PS2 para Android
    val AETHERSX2_PACKAGES = listOf(
        "xyz.aethersx2.android",
        "xyz.nethersx2.android",
        "xyz.aethersx2.android.alpha"
    )

    /**
     * Verifica si alguno de los paquetes de AetherSX2 / NetherSX2 se encuentra instalado.
     */
    fun isPS2EmulatorInstalled(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in AETHERSX2_PACKAGES) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Continuar comprobando
            }
        }
        return false
    }

    /**
     * Obtiene el paquete activo instalado entre las variantes admitidas.
     */
    fun getInstalledPS2Package(context: Context): String? {
        val pm = context.packageManager
        for (pkg in AETHERSX2_PACKAGES) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                return pkg
            } catch (e: PackageManager.NameNotFoundException) {
                // Siguiente variante
            }
        }
        return null
    }

    /**
     * Despacha el Intent de ejecución directa del juego en AetherSX2 / NetherSX2.
     * Configura permisos de URI y parámetros de arranque de imagen ISO/CHD.
     */
    fun launchPS2Game(context: Context, romFile: File, resumeState: Boolean = false): Result<Unit> {
        if (!romFile.exists()) {
            return Result.failure(IllegalArgumentException("El archivo ROM/ISO no existe: ${romFile.absolutePath}"))
        }

        val targetPackage = getInstalledPS2Package(context)
        if (targetPackage == null) {
            Log.w(TAG, "No se encontró ningún paquete de AetherSX2 / NetherSX2 instalado.")
            return Result.failure(IllegalStateException("AetherSX2 / NetherSX2 no está instalado en este dispositivo."))
        }

        return try {
            val fileUri: Uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    romFile
                )
            } catch (e: Exception) {
                Uri.fromFile(romFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(targetPackage)
                setDataAndType(fileUri, "application/x-iso9660-image")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                // Parámetros reconocidos por AetherSX2 / NetherSX2
                putExtra("bootPath", romFile.absolutePath)
                putExtra("boot", true)
                putExtra("fullscreen", true)
                if (resumeState) {
                    putExtra("resumeState", true)
                }
            }

            context.startActivity(intent)
            Log.i(TAG, "Lanzado $targetPackage con ISO: ${romFile.name}")
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Fallo al iniciar actividad de $targetPackage", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al despachar Intent", e)
            Result.failure(e)
        }
    }

    /**
     * Abre la tienda o navegador hacia el enlace de instalación del emulador.
     */
    fun openStoreOrDownloadPage(context: Context) {
        val playStoreUri = Uri.parse("market://details?id=xyz.aethersx2.android")
        val webUri = Uri.parse("https://github.com/Trixarian/NetherSX2-patch")

        try {
            val storeIntent = Intent(Intent.ACTION_VIEW, playStoreUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(storeIntent)
        } catch (e: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (e2: Exception) {
                Log.w(TAG, "No hay navegador ni Play Store en este Android TV.")
                android.widget.Toast.makeText(
                    context,
                    "Instala AetherSX2 o NetherSX2 manualmente por USB o sideload para jugar PS2.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
