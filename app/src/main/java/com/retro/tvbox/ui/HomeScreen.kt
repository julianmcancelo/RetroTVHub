package com.retro.tvbox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.retro.tvbox.RetroApplication
import com.retro.tvbox.core.BiosManager
import com.retro.tvbox.repository.CacheManager
import com.retro.tvbox.repository.ConsoleCatalog
import com.retro.tvbox.repository.ConsoleManifest
import com.retro.tvbox.repository.DownloadProgress
import com.retro.tvbox.repository.ExternalRomScanner
import com.retro.tvbox.repository.GameDownloader
import com.retro.tvbox.repository.GameEntry
import com.retro.tvbox.repository.RepositoryClient
import com.retro.tvbox.repository.UpdateInfo
import com.retro.tvbox.repository.UpdateManager
import com.retro.tvbox.ui.components.BiosSettingsDialog
import com.retro.tvbox.ui.components.CacheSettingsDialog
import com.retro.tvbox.ui.components.QrCodeDialog
import com.retro.tvbox.ui.components.UpdateDialog
import com.retro.tvbox.ui.theme.NeonCyan
import com.retro.tvbox.ui.theme.NeonPink
import com.retro.tvbox.ui.theme.TextMuted
import com.retro.tvbox.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Auténtica interfaz de inicio PlayStation 5 para RetroTV Hub.
 *
 * Reproduce fielmente el layout oficial de PS5:
 * 1. Barra superior con pestañas Games / Media a la izquierda y reloj/avatar a la derecha.
 * 2. Fila superior de iconos de consolas/apps con halo blanco y título inferior dinámico.
 * 3. Fondo dinámico de alta resolución con arte conceptual de la consola/juego enfocado.
 * 4. Área intermedia con metadatos y tipografía cinematográfica.
 * 5. Fila inferior de tarjetas de actividades/juegos con acción directa (JUGAR / DESCARGAR en 1 clic).
 */
@Composable
fun HomeScreen(
    repoClient: RepositoryClient,
    cacheManager: CacheManager,
    downloader: GameDownloader = remember { RetroApplication.instance.gameDownloader },
    serverUrl: String,
    onConsoleSelected: (ConsoleManifest) -> Unit,
    onOpenMemCards: () -> Unit,
    onLaunchGame: (GameEntry, File) -> Unit = { _, _ -> },
    biosManager: BiosManager = remember { RetroApplication.instance.biosManager }
) {
    var consoles by remember { mutableStateOf<List<ConsoleManifest>>(RepositoryClient.createBuiltinFactoryIndex().consoles) }
    var focusedConsoleIndex by remember { mutableStateOf(0) }
    var selectedTopTab by remember { mutableStateOf("Games") }

    val gamesByConsole = remember {
        mutableStateMapOf<String, List<GameEntry>>().apply {
            RepositoryClient.createBuiltinFactoryIndex().consoles.forEach { console ->
                put(console.id, RepositoryClient.createBuiltinCatalog(console.id).games)
            }
        }
    }
    val activeDownloads = remember { mutableStateMapOf<String, DownloadProgress>() }

    var showQrDialog by remember { mutableStateOf(false) }
    var showBiosDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val updateManager = remember { UpdateManager(context) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadProgress by remember { mutableStateOf<Float?>(null) }

    val firstConsoleFocus = remember { FocusRequester() }
    val consoleListState = rememberLazyListState()
    val gamesListState = rememberLazyListState()

    // Carga de consolas e índice
    LaunchedEffect(Unit) {
        val result = repoClient.fetchRepositoryIndex()
        val index = result.getOrNull() ?: RepositoryClient.createBuiltinFactoryIndex()
        consoles = index.consoles

        // Pre-cargar catálogos de juegos para cada consola
        consoles.forEach { console ->
            val catRes = repoClient.fetchConsoleCatalog(console.catalogUrl, console.id)
            val catalog = catRes.getOrNull() ?: RepositoryClient.createBuiltinCatalog(console.id)
            gamesByConsole[console.id] = catalog.games
        }

        // Auto-detección de ROMs locales y descargadas (WowRoms / /sdcard/Download / USB)
        try {
            val localRoms = ExternalRomScanner.scanAllExternalRoms(context)
            localRoms.forEach { localGame ->
                val currentList = gamesByConsole[localGame.consoleId] ?: emptyList()
                if (!currentList.any { it.fileUrl == localGame.fileUrl }) {
                    gamesByConsole[localGame.consoleId] = listOf(localGame) + currentList
                }
            }
        } catch (_: Exception) {}

        // Verificación OTA de actualización
        try {
            val updateRes = updateManager.checkForUpdates()
            availableUpdate = updateRes.getOrNull()
        } catch (_: Exception) {}
    }

    LaunchedEffect(consoles) {
        if (consoles.isNotEmpty()) {
            try {
                firstConsoleFocus.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val focusedConsole = consoles.getOrNull(focusedConsoleIndex)
    val focusedGames = focusedConsole?.let { gamesByConsole[it.id] } ?: emptyList()

    // Arte de fondo dinámico según la consola enfocada
    val heroBackdropUrl = remember(focusedConsole?.id) {
        when (focusedConsole?.id) {
            "psx" -> "https://images.alphacoders.com/112/1126749.jpg"
            "ps2" -> "https://images.alphacoders.com/978/978254.jpg"
            "genesis" -> "https://images.alphacoders.com/269/269785.jpg"
            "snes" -> "https://images.alphacoders.com/129/1294867.png"
            "gba" -> "https://images.alphacoders.com/128/1284561.png"
            "n64" -> "https://images.alphacoders.com/114/1146603.jpg"
            "nes" -> "https://images.alphacoders.com/605/605574.jpg"
            else -> "https://images.alphacoders.com/112/1126749.jpg"
        }
    }

    // --- CONTENEDOR PRINCIPAL PS5 ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060913))
    ) {
        // 1. Wallpaper de fondo de alta resolución con fundido elegante
        AsyncImage(
            model = heroBackdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.38f
        )

        // Degradados oscuros estilo PS5 (Vignette)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xD9060913),
                            Color(0x800A101E),
                            Color(0xF2060913)
                        )
                    )
                )
        )

        // Auras ambientales sutiles en esquinas
        Box(
            modifier = Modifier
                .size(600.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.08f), Color.Transparent),
                        radius = 600f
                    )
                )
        )

        // Contenido estructurado de pantalla
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- A. BARRA SUPERIOR AUTÉNTICA PS5 ---
            Ps5OfficialHeader(
                selectedTab = selectedTopTab,
                onTabSelected = { selectedTopTab = it },
                onOpenSearch = { /* Search */ },
                onOpenSettings = { showBiosDialog = true },
                onOpenMemCards = onOpenMemCards,
                onConnectPhone = { showQrDialog = true }
            )

            // --- B. FILA DE ICONOS DE CONSOLAS / APPS (Top Icon Row) ---
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyRow(
                    state = consoleListState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(consoles) { index, console ->
                        val isFirst = index == 0
                        val isSelected = index == focusedConsoleIndex

                        Ps5ConsoleTile(
                            console = console,
                            isSelected = isSelected,
                            modifier = if (isFirst) Modifier.focusRequester(firstConsoleFocus) else Modifier,
                            onFocused = { focusedConsoleIndex = index },
                            onClick = { onConsoleSelected(console) }
                        )
                    }
                }

                // Título de la consola enfocada justo debajo de la fila de iconos
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = focusedConsole?.name ?: "Biblioteca de Juegos",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // --- C. ÁREA INTERMEDIA: DETALLES CINEMATOGRÁFICOS ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                focusedConsole?.let { console ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${console.manufacturer.uppercase()}  •  AÑO ${console.releaseYear}  •  ${console.supportedExtensions.joinToString("/").uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            letterSpacing = 1.2.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Catálogo Oficial ${console.name}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.4.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Revive los títulos más aclamados con renderizado nativo a 60 FPS, partidas automáticas y compatibilidad total con mandos físicos y celular.",
                        fontSize = 13.sp,
                        color = Color(0xFFB0BDD0),
                        maxLines = 2,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth(0.65f)
                    )
                }
            }

            // --- D. FILA INFERIOR: TARJETAS DE ACTIVIDADES Y JUEGOS PS5 ---
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "TÍTULOS DESTACADOS  •  PLAY-ON-DEMAND",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF8E9BAE),
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    state = gamesListState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(focusedGames) { _, game ->
                        val isCached = remember(game.id) {
                            val local = File(game.fileUrl)
                            (local.exists() && local.length() > 0) || cacheManager.isRomCached(game.id, game.expectedExtension)
                        }
                        val downloadStatus = activeDownloads[game.id]

                        Ps5GameActivityCard(
                            game = game,
                            isCached = isCached,
                            downloadStatus = downloadStatus,
                            onClick = {
                                val local = File(game.fileUrl)
                                if (local.exists() && local.length() > 0) {
                                    onLaunchGame(game, local)
                                } else if (isCached) {
                                    val romFile = cacheManager.getRomFile(game.id, game.expectedExtension)
                                    onLaunchGame(game, romFile)
                                } else {
                                    // Descarga Play-on-Demand persistente
                                    RetroApplication.instance.applicationScope.launch {
                                        downloader.downloadGame(game).collect { progress ->
                                            activeDownloads[game.id] = progress
                                            if (progress is DownloadProgress.Completed) {
                                                onLaunchGame(game, progress.destinationFile)
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }

                    // Tarjeta final: Ver Catálogo Completo
                    focusedConsole?.let { console ->
                        item {
                            Ps5ExploreCatalogCard(
                                console = console,
                                totalGames = focusedGames.size,
                                onClick = { onConsoleSelected(console) }
                            )
                        }
                    }
                }
            }
        }

        // Diálogos modales del sistema
        if (showQrDialog) {
            QrCodeDialog(serverUrl = serverUrl, onDismissRequest = { showQrDialog = false })
        }

        if (showBiosDialog) {
            BiosSettingsDialog(biosManager = biosManager, onDismissRequest = { showBiosDialog = false })
        }

        if (showCacheDialog) {
            CacheSettingsDialog(cacheManager = cacheManager, onDismissRequest = { showCacheDialog = false })
        }

        availableUpdate?.let { update ->
            UpdateDialog(
                updateInfo = update,
                downloadProgress = updateDownloadProgress,
                isDownloading = isDownloadingUpdate,
                onConfirmUpdate = {
                    isDownloadingUpdate = true
                    RetroApplication.instance.applicationScope.launch {
                        updateManager.downloadAndInstallUpdate(
                            updateInfo = update,
                            onProgress = { progress -> updateDownloadProgress = progress }
                        )
                        isDownloadingUpdate = false
                    }
                },
                onDismiss = { availableUpdate = null }
            )
        }
    }
}

/**
 * Encabezado oficial PS5: Pestañas Games / Media y controles rápidos.
 */
@Composable
fun Ps5OfficialHeader(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemCards: () -> Unit,
    onConnectPhone: () -> Unit
) {
    val currentTime = rememberTimeLive()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Izquierda: Tabs "Games" y "Media"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Column(
                modifier = Modifier.clickable { onTabSelected("Games") }
            ) {
                Text(
                    text = "Games",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = if (selectedTab == "Games") Color.White else Color(0xFF8E9BAE)
                )
                if (selectedTab == "Games") {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .width(28.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )
                }
            }

            Column(
                modifier = Modifier.clickable { onTabSelected("Media") }
            ) {
                Text(
                    text = "Media",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selectedTab == "Media") Color.White else Color(0xFF8E9BAE)
                )
            }
        }

        // Derecha: Acciones rápidas (Búsqueda, Settings, Tarjetas, Mando Celular, Avatar, Reloj)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Ps5HeaderIconButton(icon = Icons.Default.Search, contentDescription = "Buscar", onClick = onOpenSearch)
            Ps5HeaderIconButton(icon = Icons.Default.Settings, contentDescription = "Ajustes de BIOS", onClick = onOpenSettings)
            Ps5HeaderIconButton(icon = Icons.Default.Memory, contentDescription = "Tarjetas de Memoria", onClick = onOpenMemCards)
            Ps5HeaderIconButton(icon = Icons.Default.QrCode, contentDescription = "Conectar Celular", onClick = onConnectPhone)

            // Avatar circular con indicador online verde
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
                // Online Dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676))
                        .border(1.5.dp, Color(0xFF060913), CircleShape)
                )
            }

            // Reloj en tiempo real estilo PS5 (ej: 2:51 PM)
            Text(
                text = currentTime,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun Ps5HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.2f else 1.0f, label = "scale")
    val tintColor by animateColorAsState(targetValue = if (isFocused) Color(0xFF00E5FF) else Color(0xFFCBD5E1), label = "tint")

    Box(
        modifier = Modifier
            .size(34.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent)
            .border(if (isFocused) 1.dp else 0.dp, if (isFocused) Color.White else Color.Transparent, CircleShape)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tintColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Tile cuadrado de la fila superior de consolas estilo PS5.
 */
@Composable
fun Ps5ConsoleTile(
    console: ConsoleManifest,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    val active = isFocused || isSelected
    val scale by animateFloatAsState(targetValue = if (active) 1.15f else 1.0f, label = "scale")

    val (bgGradient, emblemText) = remember(console.id) {
        when (console.id) {
            "psx" -> listOf(Color(0xFF003791), Color(0xFF002050)) to "PS1"
            "ps2" -> listOf(Color(0xFF001B44), Color(0xFF000F25)) to "PS2"
            "snes" -> listOf(Color(0xFF6B1B9A), Color(0xFF4A148C)) to "SNES"
            "genesis" -> listOf(Color(0xFF006699), Color(0xFF003366)) to "GEN"
            "gba" -> listOf(Color(0xFF1B5E20), Color(0xFF0D3311)) to "GBA"
            "n64" -> listOf(Color(0xFFB71C1C), Color(0xFF5F0909)) to "N64"
            "nes" -> listOf(Color(0xFFC2185B), Color(0xFF6A0D32)) to "NES"
            else -> listOf(Color(0xFF263238), Color(0xFF10171B)) to "SYS"
        }
    }

    Box(
        modifier = modifier
            .size(68.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(bgGradient))
            .border(
                width = if (active) 2.5.dp else 1.dp,
                color = if (active) Color.White else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = console.name,
                tint = if (active) Color.White else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = emblemText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }
    }
}

/**
 * Tarjeta de actividad de juego horizontal 16:9 con Play/Download directo.
 */
@Composable
fun Ps5GameActivityCard(
    game: GameEntry,
    isCached: Boolean,
    downloadStatus: DownloadProgress?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "scale")

    val isDownloading = downloadStatus is DownloadProgress.Downloading

    Box(
        modifier = Modifier
            .width(230.dp)
            .height(130.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        // Thumbnail de la carátula o fondo genérico estilizado
        if (!game.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = game.coverUrl,
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.55f
            )
        }

        // Sombra / Gradiente para legibilidad
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xCC060913), Color(0xF2060913))
                    )
                )
        )

        // Contenido de la tarjeta
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Insignia superior derecha de estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isCached) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF00E676))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "EN CACHÉ",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF060913)
                        )
                    }
                }
            }

            // Título e indicador de acción
            Column {
                Text(
                    text = game.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${game.genre}  •  ${game.releaseYear}",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Botón de acción con Play/Download
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color(0xFF00E5FF),
                            strokeWidth = 2.dp
                        )
                        val p = (downloadStatus as DownloadProgress.Downloading).percentage.toInt().coerceIn(0, 100)
                        Text(
                            text = "DESCARGANDO $p%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    } else if (isCached) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Jugar",
                                tint = Color(0xFF060913),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = "JUGAR AHORA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00E676)
                        )
                    } else {
                        val sizeMb = game.sizeBytes / (1024 * 1024)
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00D2FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Descargar",
                                tint = Color(0xFF060913),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = if (sizeMb > 0) "DESCARGAR (~$sizeMb MB)" else "DESCARGAR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta para abrir el catálogo completo de la consola seleccionada.
 */
@Composable
fun Ps5ExploreCatalogCard(
    console: ConsoleManifest,
    totalGames: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "scale")

    Box(
        modifier = Modifier
            .width(200.dp)
            .height(130.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x331E293B))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x3300E5FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ver Catálogo Completo",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "$totalGames títulos disponibles",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun rememberTimeLive(): String {
    var timeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        while (isActive) {
            timeStr = format.format(Date())
            delay(1000)
        }
    }
    return timeStr
}
