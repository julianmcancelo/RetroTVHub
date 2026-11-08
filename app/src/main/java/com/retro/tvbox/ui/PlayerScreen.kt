package com.retro.tvbox.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.retro.tvbox.core.BiosManager
import com.retro.tvbox.core.CoreManager
import com.retro.tvbox.core.EmulatorState
import com.retro.tvbox.core.LibretroBridge
import com.retro.tvbox.input.InputRouter
import com.retro.tvbox.repository.CacheManager
import com.retro.tvbox.repository.GameEntry
import com.retro.tvbox.save.SaveManager
import com.retro.tvbox.save.SaveStateService
import com.retro.tvbox.ui.components.TouchControlsOverlay
import com.retro.tvbox.ui.theme.NeonCyan
import com.retro.tvbox.ui.theme.NeonPink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PlayerScreen(
    game: GameEntry,
    romFile: File,
    bridge: LibretroBridge,
    coreManager: CoreManager,
    biosManager: BiosManager,
    saveManager: SaveManager,
    saveStateService: SaveStateService,
    cacheManager: CacheManager,
    onExitGame: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var showInGameMenu by remember { mutableStateOf(false) }

    val inputRouter = remember { InputRouter.getInstance() }
    var activeSurfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    var isSurfaceReady by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Estados de configuración de core, bios y barra de progreso estilo PS5
    var isLoadingCore by remember { mutableStateOf(true) }
    var loadingTitle by remember { mutableStateOf("Descargando motor de emulación...") }
    var loadingMessage by remember { mutableStateOf("Comprobando componentes del sistema...") }
    var coreProgress by remember { mutableIntStateOf(0) }

    // Detección de dispositivo (Smart TV / TV Box vs Móvil / Pantalla Táctil)
    val isTvDevice = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val isTouchDevice = !isTvDevice

    // Helper centralizado para persistencia atómica de SRAM
    fun persistSram(reason: String) {
        try {
            val sramData = bridge.getSramData()
            if (sramData != null && sramData.isNotEmpty()) {
                val ok = saveManager.writeSRAM(game.id, sramData)
                Log.i("PlayerScreen", "SRAM persistida automáticamente ($reason): $ok [${sramData.size} bytes]")
            }
        } catch (e: Exception) {
            Log.e("PlayerScreen", "Error persistiendo SRAM ($reason): ${e.message}")
        }
    }

    // 1. Enlace de InputRouter con LibretroBridge y guardado SRAM en ciclo de vida
    DisposableEffect(lifecycleOwner) {
        // Enlazar entradas físicas, remotas y táctiles al bridge
        inputRouter.bindToLibretroBridge(bridge)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    persistSram("Lifecycle ${event.name}")
                    bridge.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (!showInGameMenu && !isLoadingCore) {
                        bridge.resume()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            persistSram("PlayerScreen onDispose")
            inputRouter.unbindLibretroBridge()
            bridge.stopAudio()
            bridge.unloadGame()
        }
    }

    // 2. Comprobación, auto-descarga de BIOS/Núcleo y ejecución a 60 FPS
    LaunchedEffect(romFile) {
        withContext(Dispatchers.IO) {
            // 0. Verificar primero integridad básica del archivo de la ROM
            if (!romFile.exists() || romFile.length() <= 1024) {
                loadError = "El archivo del juego no está completo o se interrumpió la descarga. Por favor, vuelve a descargarlo."
                isLoadingCore = false
                return@withContext
            }

            // A. Comprobar y auto-descargar BIOS si la consola lo requiere
            if (game.consoleId.equals("psx", ignoreCase = true) && !biosManager.hasRequiredBios("psx")) {
                loadingTitle = "Descargando componentes de sistema..."
                loadingMessage = "Descargando BIOS oficial de PlayStation 1..."
                val ps1Bios = biosManager.registeredBios.firstOrNull { it.consoleId == "psx" && it.isMandatory }
                if (ps1Bios != null) {
                    biosManager.downloadBios(ps1Bios)
                }
            }

            // B. Comprobar y auto-descargar Núcleo oficial desde Libretro Buildbot
            if (!coreManager.isCoreInstalled(game.consoleId)) {
                loadingTitle = "Descargando motor de emulación..."
                val coreDesc = coreManager.getCoreDescription(game.consoleId)
                loadingMessage = "Obteniendo núcleo oficial ($coreDesc)..."

                coreManager.downloadCoreFlow(game.consoleId).collect { progress ->
                    coreProgress = progress.progressPercent
                    progress.statusMessage?.let { loadingMessage = it }
                    if (progress.error != null) {
                        loadingMessage = "Aviso: ${progress.error}. Usando motor de respaldo integrado."
                    }
                }
            }

            // C. Inicializar Núcleo nativo y cargar juego con memoria SRAM
            loadingTitle = "Iniciando emulación..."
            loadingMessage = "Cargando cartucho/disco y sincronizando SRAM..."

            val coreFile = coreManager.getCoreFile(game.consoleId)
            val initOk = if (coreFile.exists()) {
                bridge.initCore(coreFile.absolutePath, coreManager.systemDir.absolutePath)
            } else {
                bridge.initCore("", coreManager.systemDir.absolutePath)
            }

            val sramFile = saveManager.getSaveFile(game.id)
            val sramPath = if (sramFile.exists()) sramFile.absolutePath else null
            val loadOk = bridge.loadGame(romFile.absolutePath, sramPath)

            if (!initOk || !loadOk) {
                loadError = "No se pudo iniciar el juego (${game.title}). Verifica que la descarga esté completa o intenta con otro título."
                isLoadingCore = false
                return@withContext
            }

            isLoadingCore = false
        }

        // D. Loop de Video y Emulación a 60 FPS con timing de alta precisión
        launch(Dispatchers.Default) {
            val targetFrameTimeNs = 1_000_000_000L / 60L // 16.666.666 ns
            var nextFrameTime = System.nanoTime()

            while (isActive) {
                if (loadError == null && !showInGameMenu && !isLoadingCore && isSurfaceReady && (bridge.currentState == EmulatorState.RUNNING || bridge.currentState == EmulatorState.GAME_LOADED)) {
                    bridge.stepFrame()
                }

                nextFrameTime += targetFrameTimeNs
                val sleepNs = nextFrameTime - System.nanoTime()

                if (sleepNs > 0) {
                    val delayMs = sleepNs / 1_000_000L
                    if (delayMs > 0) {
                        kotlinx.coroutines.delay(delayMs)
                    }
                } else {
                    // Si se rezaga más de 3 frames, resincronizar tiempo para evitar bucle de compensación
                    if (sleepNs < -targetFrameTimeNs * 3) {
                        nextFrameTime = System.nanoTime()
                    }
                    kotlinx.coroutines.yield()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // SurfaceView nativo para renderizado del pipeline de video a 60 FPS
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                    activeSurfaceView = this

                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            bridge.setVideoSurface(holder.surface)
                            isSurfaceReady = true
                            if (isLoadingCore) {
                                drawLoadingScreen(holder, game.title)
                            }
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            bridge.setVideoSurface(holder.surface)
                            isSurfaceReady = true
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            isSurfaceReady = false
                            bridge.setVideoSurface(null)
                        }
                    })

                    // Enrutamiento de hardware keys de TV remote o mandos Bluetooth
                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                            if (event.action == KeyEvent.ACTION_UP) {
                                if (!showInGameMenu) {
                                    persistSram("InGameMenu Pausa (Tecla)")
                                    bridge.pause()
                                    showInGameMenu = true
                                } else {
                                    bridge.resume()
                                    showInGameMenu = false
                                }
                            }
                            return@setOnKeyListener true
                        }
                        inputRouter.handleKeyEvent(event)
                    }

                    // Enrutamiento de sticks analógicos
                    setOnGenericMotionListener { _, event ->
                        inputRouter.handleMotionEvent(event)
                    }
                }
            }
        )

        // Overlay elegante estilo PlayStation 5 ("Descargando motor de emulación...")
        if (isLoadingCore && loadError == null) {
            Ps5LoadingOverlay(
                title = loadingTitle,
                subtitle = coreManager.getCoreDescription(game.consoleId),
                message = loadingMessage,
                progressPercent = coreProgress
            )
        }

        // Overlay elegante estilo PlayStation 5 en caso de error al cargar el juego
        if (loadError != null) {
            Ps5ErrorOverlay(
                title = "No se pudo iniciar el juego",
                description = loadError ?: "",
                onBackToCatalog = onExitGame,
                onClearCacheAndExit = {
                    cacheManager.purgeOnExit(game.id)
                    if (romFile.exists()) {
                        try {
                            romFile.delete()
                        } catch (_: Exception) {}
                    }
                    onExitGame()
                }
            )
        }

        // Controles táctiles en pantalla para móviles/tablets
        if (isTouchDevice && !isLoadingCore && loadError == null && !showInGameMenu) {
            TouchControlsOverlay(
                isPlayStationLayout = game.consoleId.equals("psx", ignoreCase = true) || game.consoleId.equals("ps2", ignoreCase = true),
                inputRouter = inputRouter,
                onOpenMenu = {
                    persistSram("InGameMenu Pausa (Táctil)")
                    bridge.pause()
                    showInGameMenu = true
                }
            )
        }

        // Menú OSD flotante en pausa con guardado de partidas y estados
        if (showInGameMenu) {
            InGameMenu(
                gameTitle = game.title,
                onResume = {
                    bridge.resume()
                    showInGameMenu = false
                    activeSurfaceView?.requestFocus()
                },
                onSaveState = { slot ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val stateFile = File(saveStateService.statesDir, "${game.id}_slot$slot.tmp")
                        bridge.saveState(stateFile.absolutePath)

                        val stateBytes = if (stateFile.exists()) stateFile.readBytes() else "STATE_DATA".toByteArray()
                        stateFile.delete()

                        val shot = captureScreenshot(activeSurfaceView)
                        saveStateService.saveStateWithScreenshot(game.id, slot, stateBytes, shot)
                    }
                },
                onLoadState = { slot ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val stateBytes = saveStateService.loadState(game.id, slot)
                        if (stateBytes != null) {
                            val tempLoad = File(saveStateService.statesDir, "temp_load.state")
                            tempLoad.writeBytes(stateBytes)
                            bridge.loadState(tempLoad.absolutePath)
                            tempLoad.delete()
                        }
                    }
                },
                onReset = {
                    persistSram("Reset")
                    bridge.unloadGame()
                    val sram = saveManager.getSaveFile(game.id)
                    bridge.loadGame(romFile.absolutePath, if (sram.exists()) sram.absolutePath else null)
                    bridge.resume()
                    showInGameMenu = false
                    activeSurfaceView?.requestFocus()
                },
                onExitWithPurge = {
                    persistSram("Salir con Purga")
                    cacheManager.purgeOnExit(game.id)
                    onExitGame()
                },
                onExitKeepCache = {
                    persistSram("Salir")
                    onExitGame()
                }
            )
        }
    }
}

/**
 * Indicador de progreso elegante estilo PlayStation 5 con diseño HUD y glassmorphism.
 */
@Composable
private fun Ps5LoadingOverlay(
    title: String,
    subtitle: String,
    message: String,
    progressPercent: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ps5_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D1B2A).copy(alpha = 0.95f),
                        Color(0xFF060913),
                        Color.Black
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827).copy(alpha = 0.92f)),
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        NeonCyan.copy(alpha = glowAlpha),
                        NeonPink.copy(alpha = glowAlpha * 0.7f)
                    )
                )
            ),
            modifier = Modifier
                .widthIn(min = 340.dp, max = 520.dp)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Icono central pulsante estilo PS5
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    NeonCyan.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(1.5.dp, NeonCyan.copy(alpha = glowAlpha), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Título principal
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )

                // Badge de la consola / núcleo
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan.copy(alpha = 0.12f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = subtitle.uppercase(),
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Barra de progreso elegante PS5 con gradiente luminoso
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1F2937))
                    ) {
                        val fraction = (progressPercent.coerceIn(0, 100)) / 100f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(NeonCyan, NeonPink)
                                    )
                                )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = message,
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (progressPercent > 0) {
                            Text(
                                text = "$progressPercent%",
                                color = NeonCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diálogo / Overlay elegante estilo PlayStation 5 para fallos de carga o ROM corrupta.
 * Ofrece opciones claras enfocables con D-Pad de TV o táctil en celular.
 */
@Composable
private fun Ps5ErrorOverlay(
    title: String,
    description: String,
    onBackToCatalog: () -> Unit,
    onClearCacheAndExit: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            firstFocus.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF200B15).copy(alpha = 0.96f),
                        Color(0xFF0D0F18),
                        Color.Black
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101726).copy(alpha = 0.94f)),
            border = BorderStroke(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFFF3366).copy(alpha = 0.8f),
                        Color(0xFF7928CA).copy(alpha = 0.8f)
                    )
                )
            ),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Ícono de advertencia pulsante estilo PS5
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFFFF3366).copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(1.5.dp, Color(0xFFFF3366).copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF3366),
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Título principal de error
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp
                )

                // Descripción detallada del problema
                Text(
                    text = description,
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Acciones enfocables con el control remoto (D-Pad)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Ps5DialogButton(
                        text = "Volver al Catálogo",
                        isPrimary = true,
                        focusRequester = firstFocus,
                        onClick = onBackToCatalog
                    )

                    Ps5DialogButton(
                        text = "Borrar Caché Corrupta y Salir",
                        isPrimary = false,
                        onClick = onClearCacheAndExit
                    )
                }
            }
        }
    }
}

@Composable
private fun Ps5DialogButton(
    text: String,
    isPrimary: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        label = "dialog_button_scale"
    )

    val bg = when {
        isFocused -> if (isPrimary) Color(0xFF00E5FF) else Color(0xFFFF3366)
        isPrimary -> Color(0xFF1E293B)
        else -> Color(0xFF131826)
    }

    val contentColor = when {
        isFocused -> Color.Black
        isPrimary -> Color.White
        else -> Color(0xFFFF6B8B)
    }

    val border = when {
        isFocused -> Color.White
        isPrimary -> Color(0xFF00E5FF).copy(alpha = 0.4f)
        else -> Color(0xFFFF3366).copy(alpha = 0.35f)
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) {
                    onClick()
                    true
                } else false
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

private fun drawLoadingScreen(holder: SurfaceHolder, gameTitle: String) {
    try {
        val canvas = holder.lockCanvas() ?: return
        canvas.drawColor(android.graphics.Color.rgb(10, 14, 23))
        val paint = Paint().apply {
            color = android.graphics.Color.rgb(0, 229, 255)
            textSize = 38f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val cx = canvas.width / 2f
        val cy = canvas.height / 2f
        canvas.drawText("Iniciando $gameTitle...", cx, cy, paint)
        holder.unlockCanvasAndPost(canvas)
    } catch (_: Exception) {}
}

private fun captureScreenshot(view: SurfaceView?): Bitmap? {
    if (view == null || view.width <= 0 || view.height <= 0) return null
    return try {
        val bitmap = Bitmap.createBitmap(view.width / 2, view.height / 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(0.5f, 0.5f)
        view.draw(canvas)
        bitmap
    } catch (e: Exception) {
        null
    }
}
