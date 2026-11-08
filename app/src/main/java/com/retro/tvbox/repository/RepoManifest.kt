package com.retro.tvbox.repository

import kotlinx.serialization.Serializable

@Serializable
data class RepositoryIndex(
    val repoName: String,
    val version: Int = 1,
    val description: String = "",
    val consoles: List<ConsoleManifest> = emptyList()
)

@Serializable
data class ConsoleManifest(
    val id: String,                         // e.g. "snes", "psx", "ps2", "gba", "nes", "genesis"
    val name: String,                       // e.g. "Super Nintendo", "PlayStation"
    val manufacturer: String,               // e.g. "Nintendo", "Sony"
    val releaseYear: Int,                   // e.g. 1990
    val iconUrl: String? = null,
    val bannerUrl: String? = null,
    val supportedExtensions: List<String>,  // e.g. ["sfc", "smc", "zip"]
    val defaultCore: String,                // e.g. "snes9x_libretro_android.so"
    val catalogUrl: String                  // URL to fetch games list for this console
)

@Serializable
data class ConsoleCatalog(
    val consoleId: String,
    val consoleName: String,
    val count: Int,
    val games: List<GameEntry>
)

@Serializable
data class GameEntry(
    val id: String,
    val consoleId: String,
    val title: String,
    val fileUrl: String,
    val isCompressed: Boolean = false,
    val internalRomName: String? = null,
    val expectedExtension: String,
    val sizeBytes: Long = 0L,
    val hashSha256: String? = null,
    val coverUrl: String? = null,
    val backdropUrl: String? = null,
    val description: String = "",
    val genre: String = "Retro",
    val releaseYear: Int = 1995,
    val maxPlayers: Int = 1
)
