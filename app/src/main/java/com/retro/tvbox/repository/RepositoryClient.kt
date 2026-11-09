package com.retro.tvbox.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class RepositoryClient(private val context: Context) {

    private val tag = "RepositoryClient"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val manifestsDir: File by lazy {
        File(context.filesDir, "manifest_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Descarga y parsea el índice general de consolas del repositorio remoto.
     * En caso de error de red, recurre a la versión en caché local o al catálogo de fábrica.
     */
    suspend fun fetchRepositoryIndex(url: String? = null): Result<RepositoryIndex> = withContext(Dispatchers.IO) {
        val targetUrl = url ?: DEFAULT_REPO_URL
        val cacheFile = File(manifestsDir, "index_manifest.json")

        try {
            val request = Request.Builder().url(targetUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP error ${response.code}: ${response.message}")
                }
                val body = response.body?.string() ?: throw IOException("Cuerpo de respuesta vacío")
                val parsed = json.decodeFromString<RepositoryIndex>(body)
                // Actualizar caché
                cacheFile.writeText(body)
                Log.i(tag, "Índice de repositorio descargado exitosamente: ${parsed.repoName}")
                return@withContext Result.success(parsed)
            }
        } catch (e: Exception) {
            Log.w(tag, "Fallo al conectar con repositorio remoto ($targetUrl). Usando respaldo: ${e.message}")
            if (cacheFile.exists()) {
                try {
                    val cached = json.decodeFromString<RepositoryIndex>(cacheFile.readText())
                    return@withContext Result.success(cached)
                } catch (e2: Exception) {
                    Log.e(tag, "Caché de índice dañada", e2)
                }
            }
            try {
                context.assets.open("catalog/index.json").use { stream ->
                    val assetJson = stream.bufferedReader().readText()
                    val parsed = json.decodeFromString<RepositoryIndex>(assetJson)
                    return@withContext Result.success(parsed)
                }
            } catch (_: Exception) {}
            // Fallback catálogo nativo inicial de fábrica
            Result.success(createBuiltinFactoryIndex())
        }
    }

    /**
     * Descarga y parsea el catálogo de juegos de una consola específica.
     */
    suspend fun fetchConsoleCatalog(catalogUrl: String, consoleId: String): Result<ConsoleCatalog> = withContext(Dispatchers.IO) {
        val cacheFile = File(manifestsDir, "catalog_${consoleId}.json")

        // Cargar siempre catálogo base desde assets empaquetados (para evitar retraso de CDN)
        val assetCatalog: ConsoleCatalog? = try {
            context.assets.open("catalog/${consoleId}.json").use { stream ->
                val assetJson = stream.bufferedReader().readText()
                json.decodeFromString<ConsoleCatalog>(assetJson)
            }
        } catch (_: Exception) { null }

        try {
            val request = Request.Builder().url(catalogUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP error ${response.code}")
                }
                val body = response.body?.string() ?: throw IOException("Respuesta vacía")
                val parsed = json.decodeFromString<ConsoleCatalog>(body)
                // Si el catálogo empaquetado en assets tiene más juegos (ej. CDN aún cacheando), usar el más completo
                val bestCatalog = if (assetCatalog != null && assetCatalog.games.size >= parsed.games.size) {
                    assetCatalog
                } else {
                    parsed
                }
                cacheFile.writeText(json.encodeToString(ConsoleCatalog.serializer(), bestCatalog))
                return@withContext Result.success(bestCatalog)
            }
        } catch (e: Exception) {
            Log.w(tag, "Fallo descargando catálogo para $consoleId: ${e.message}")
            if (assetCatalog != null) {
                return@withContext Result.success(assetCatalog)
            }
            if (cacheFile.exists()) {
                try {
                    val cached = json.decodeFromString<ConsoleCatalog>(cacheFile.readText())
                    return@withContext Result.success(cached)
                } catch (e2: Exception) {
                    Log.e(tag, "Caché de catálogo dañada", e2)
                }
            }
            Result.success(createBuiltinCatalog(consoleId))
        }
    }

    fun loadAssetCatalogSync(consoleId: String): ConsoleCatalog {
        return try {
            context.assets.open("catalog/${consoleId}.json").use { stream ->
                val assetJson = stream.bufferedReader().readText()
                json.decodeFromString<ConsoleCatalog>(assetJson)
            }
        } catch (_: Exception) {
            createBuiltinCatalog(consoleId)
        }
    }

    companion object {
        const val DEFAULT_REPO_URL = "https://raw.githubusercontent.com/julianmcancelo/RetroTVHub/main/catalog/index.json"

        /**
         * Catálogo predeterminado offline de alto nivel con consolas clásicas soportadas.
         */
        fun createBuiltinFactoryIndex(): RepositoryIndex {
            return RepositoryIndex(
                repoName = "RetroTV Hub Oficial",
                version = 1,
                description = "Repositorio local verificado para Android TV",
                consoles = listOf(
                    ConsoleManifest(
                        id = "snes",
                        name = "Super Nintendo (SNES)",
                        manufacturer = "Nintendo",
                        releaseYear = 1990,
                        supportedExtensions = listOf("sfc", "smc", "zip"),
                        defaultCore = "snes9x_libretro_android.so",
                        catalogUrl = "https://raw.githubusercontent.com/julianmcancelo/RetroTVHub/main/catalog/snes.json"
                    ),
                    ConsoleManifest(
                        id = "nes",
                        name = "Family Game / NES",
                        manufacturer = "Nintendo",
                        releaseYear = 1983,
                        supportedExtensions = listOf("nes", "zip"),
                        defaultCore = "fceumm_libretro_android.so",
                        catalogUrl = "https://raw.githubusercontent.com/julianmcancelo/RetroTVHub/main/catalog/nes.json"
                    ),
                    ConsoleManifest(
                        id = "genesis",
                        name = "Sega Genesis / Mega Drive",
                        manufacturer = "Sega",
                        releaseYear = 1988,
                        supportedExtensions = listOf("md", "bin", "gen", "zip"),
                        defaultCore = "genesis_plus_gx_libretro_android.so",
                        catalogUrl = "https://raw.githubusercontent.com/julianmcancelo/RetroTVHub/main/catalog/genesis.json"
                    ),
                    ConsoleManifest(
                        id = "gba",
                        name = "Game Boy Advance",
                        manufacturer = "Nintendo",
                        releaseYear = 2001,
                        supportedExtensions = listOf("gba", "zip"),
                        defaultCore = "mgba_libretro_android.so",
                        catalogUrl = "https://raw.githubusercontent.com/julianmcancelo/RetroTVHub/main/catalog/gba.json"
                    ),
                    ConsoleManifest(
                        id = "psx",
                        name = "Sony PlayStation 1 (PSX)",
                        manufacturer = "Sony",
                        releaseYear = 1994,
                        supportedExtensions = listOf("chd", "iso", "bin", "cue"),
                        defaultCore = "pcsx_rearmed_libretro_android.so",
                        catalogUrl = "https://raw.githubusercontent.com/julianmcancelo/RetroTVHub/main/catalog/psx.json"
                    ),
                    ConsoleManifest(
                        id = "n64",
                        name = "Nintendo 64",
                        manufacturer = "Nintendo",
                        releaseYear = 1996,
                        supportedExtensions = listOf("z64", "n64", "v64", "zip"),
                        defaultCore = "mupen64plus_next_libretro_android.so",
                        catalogUrl = "https://raw.githubusercontent.com/julianmcancelo/RetroTVHub/main/catalog/n64.json"
                    )
                )
            )
        }

        fun createBuiltinCatalog(consoleId: String): ConsoleCatalog {
            val games = when (consoleId) {
                "snes" -> listOf(
                    GameEntry(
                        id = "snes_smw",
                        consoleId = "snes",
                        title = "Super Mario World",
                        fileUrl = "https://archive.org/download/snes-roms/Super%20Mario%20World.zip",
                        isCompressed = true,
                        internalRomName = "Super Mario World.sfc",
                        expectedExtension = "sfc",
                        sizeBytes = 524288,
                        description = "Aventura de plataformas legendaria de 16-bits junto a Mario y Yoshi en Dinosaur Land.",
                        genre = "Plataformas",
                        releaseYear = 1990,
                        maxPlayers = 2
                    ),
                    GameEntry(
                        id = "snes_zelda",
                        consoleId = "snes",
                        title = "The Legend of Zelda: A Link to the Past",
                        fileUrl = "https://archive.org/download/snes-roms/Zelda%20ALttP.zip",
                        isCompressed = true,
                        internalRomName = "Zelda ALttP.sfc",
                        expectedExtension = "sfc",
                        sizeBytes = 1048576,
                        description = "Obra maestra de aventura y rol en el reino de Hyrule y la Tierra Sombría.",
                        genre = "Aventura / RPG",
                        releaseYear = 1991,
                        maxPlayers = 1
                    ),
                    GameEntry(
                        id = "snes_metroid",
                        consoleId = "snes",
                        title = "Super Metroid",
                        fileUrl = "https://archive.org/download/snes-roms/Super%20Metroid.zip",
                        isCompressed = true,
                        internalRomName = "Super Metroid.sfc",
                        expectedExtension = "sfc",
                        sizeBytes = 3145728,
                        description = "Explora las profundidades del planeta Zebes encarnando a la cazarrecompensas Samus Aran.",
                        genre = "Acción / Metroidvania",
                        releaseYear = 1994,
                        maxPlayers = 1
                    )
                )
                "genesis" -> listOf(
                    GameEntry(
                        id = "gen_sonic2",
                        consoleId = "genesis",
                        title = "Sonic The Hedgehog 2",
                        fileUrl = "https://archive.org/download/genesis-roms/Sonic2.zip",
                        isCompressed = true,
                        internalRomName = "Sonic2.md",
                        expectedExtension = "md",
                        sizeBytes = 1048576,
                        description = "Velocidad vertiginosa a 60 FPS con Sonic y Tails.",
                        genre = "Plataformas",
                        releaseYear = 1992,
                        maxPlayers = 2
                    )
                )
                "psx" -> listOf(
                    GameEntry(
                        id = "psx_crash",
                        consoleId = "psx",
                        title = "Crash Bandicoot",
                        fileUrl = "https://archive.org/download/psx-roms/Crash.chd",
                        isCompressed = false,
                        expectedExtension = "chd",
                        sizeBytes = 460000000,
                        description = "El clásico juego 3D de plataformas de Naughty Dog para PS1.",
                        genre = "Plataformas 3D",
                        releaseYear = 1996,
                        maxPlayers = 1
                    )
                )
                "ps2" -> listOf(
                    GameEntry(
                        id = "ps2_gow",
                        consoleId = "ps2",
                        title = "God of War II",
                        fileUrl = "https://archive.org/download/ps2-roms/GodOfWar2.iso",
                        isCompressed = false,
                        expectedExtension = "iso",
                        sizeBytes = 4300000000,
                        description = "Épica aventura hack and slash en la mitología griega, ejecutada en AetherSX2 / NetherSX2.",
                        genre = "Hack & Slash",
                        releaseYear = 2007,
                        maxPlayers = 1
                    )
                )
                else -> emptyList()
            }

            return ConsoleCatalog(
                consoleId = consoleId,
                consoleName = consoleId.uppercase(),
                count = games.size,
                games = games
            )
        }
    }
}
