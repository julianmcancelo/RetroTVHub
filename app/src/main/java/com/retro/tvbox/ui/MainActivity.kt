package com.retro.tvbox.ui

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.retro.tvbox.RetroApplication
import com.retro.tvbox.core.ExternalLauncher
import com.retro.tvbox.core.LibretroBridge
import com.retro.tvbox.input.InputRouter
import com.retro.tvbox.input.phone.PhoneControllerServer
import com.retro.tvbox.repository.ConsoleManifest
import com.retro.tvbox.repository.GameEntry
import com.retro.tvbox.ui.theme.BackgroundDark
import com.retro.tvbox.ui.theme.RetroTvTheme
import java.io.File

sealed class TvScreen {
    data object Home : TvScreen()
    data class Catalog(val console: ConsoleManifest) : TvScreen()
    data class Player(val game: GameEntry, val romFile: File) : TvScreen()
    data object MemCards : TvScreen()
}

class MainActivity : ComponentActivity() {

    private val tag = "MainActivity"
    private lateinit var app: RetroApplication
    private lateinit var phoneServer: PhoneControllerServer
    private lateinit var bridge: LibretroBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as RetroApplication

        // Initialize InputRouter & Phone Controller WebSocket Server
        InputRouter.getInstance().initialize(this)
        phoneServer = PhoneControllerServer.getInstance(this)
        phoneServer.start()

        // Core Libretro Bridge
        bridge = LibretroBridge()

        setContent {
            RetroTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    AppNavigationHost(
                        app = app,
                        bridge = bridge,
                        phoneServer = phoneServer,
                        onLaunchExternalPS2 = { game, romFile ->
                            launchPS2External(game, romFile)
                        }
                    )
                }
            }
        }
    }

    private fun launchPS2External(game: GameEntry, romFile: File) {
        val installed = ExternalLauncher.isPS2EmulatorInstalled(this)
        if (installed) {
            val result = ExternalLauncher.launchPS2Game(this, romFile)
            if (result.isFailure) {
                Toast.makeText(this, "Error al lanzar AetherSX2: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "AetherSX2 o NetherSX2 no está instalado. Abriendo instalador...", Toast.LENGTH_LONG).show()
            ExternalLauncher.openStoreOrDownloadPage(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        phoneServer.stop()
        bridge.deinitCore()
    }
}

@Composable
fun AppNavigationHost(
    app: RetroApplication,
    bridge: LibretroBridge,
    phoneServer: PhoneControllerServer,
    onLaunchExternalPS2: (GameEntry, File) -> Unit
) {
    var currentScreen by remember { mutableStateOf<TvScreen>(TvScreen.Home) }
    val serverUrl = remember { phoneServer.getLocalServerUrl() }

    BackHandler(enabled = currentScreen !is TvScreen.Home) {
        currentScreen = when (currentScreen) {
            is TvScreen.Catalog -> TvScreen.Home
            is TvScreen.MemCards -> TvScreen.Home
            is TvScreen.Player -> TvScreen.Home
            TvScreen.Home -> TvScreen.Home
        }
    }

    when (val screen = currentScreen) {
        is TvScreen.Home -> {
            HomeScreen(
                repoClient = app.repositoryClient,
                cacheManager = app.cacheManager,
                serverUrl = serverUrl,
                biosManager = app.biosManager,
                onConsoleSelected = { console ->
                    currentScreen = TvScreen.Catalog(console)
                },
                onOpenMemCards = {
                    currentScreen = TvScreen.MemCards
                }
            )
        }

        is TvScreen.Catalog -> {
            CatalogScreen(
                console = screen.console,
                repoClient = app.repositoryClient,
                downloader = app.gameDownloader,
                cacheManager = app.cacheManager,
                onBack = { currentScreen = TvScreen.Home },
                onLaunchGame = { game, romFile ->
                    if (game.consoleId.equals("ps2", ignoreCase = true)) {
                        onLaunchExternalPS2(game, romFile)
                    } else {
                        currentScreen = TvScreen.Player(game, romFile)
                    }
                }
            )
        }

        is TvScreen.Player -> {
            PlayerScreen(
                game = screen.game,
                romFile = screen.romFile,
                bridge = bridge,
                coreManager = app.coreManager,
                biosManager = app.biosManager,
                saveManager = app.saveManager,
                saveStateService = app.saveStateService,
                cacheManager = app.cacheManager,
                onExitGame = {
                    currentScreen = TvScreen.Home
                }
            )
        }

        is TvScreen.MemCards -> {
            MemCardScreen(
                memCardManager = app.memoryCardManager,
                onBack = { currentScreen = TvScreen.Home }
            )
        }
    }
}
