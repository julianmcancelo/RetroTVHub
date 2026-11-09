package com.retro.tvbox.ui

import com.retro.tvbox.RetroApplication

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.retro.tvbox.repository.CacheManager
import com.retro.tvbox.repository.ConsoleManifest
import com.retro.tvbox.repository.DownloadProgress
import com.retro.tvbox.repository.ExternalRomScanner
import com.retro.tvbox.repository.GameDownloader
import com.retro.tvbox.repository.GameEntry
import com.retro.tvbox.repository.RepositoryClient
import com.retro.tvbox.ui.theme.AccentGreen
import com.retro.tvbox.ui.theme.BadgeCachedBg
import com.retro.tvbox.ui.theme.BadgeCachedText
import com.retro.tvbox.ui.theme.BadgeCloudBg
import com.retro.tvbox.ui.theme.BadgeCloudText
import com.retro.tvbox.ui.theme.FocusYellow
import com.retro.tvbox.ui.theme.NeonCyan
import com.retro.tvbox.ui.theme.NeonPink
import com.retro.tvbox.ui.theme.SurfaceDark
import com.retro.tvbox.ui.theme.SurfaceDarkVariant
import com.retro.tvbox.ui.theme.TextGray
import com.retro.tvbox.ui.theme.TextMuted
import com.retro.tvbox.ui.theme.TextWhite
import kotlinx.coroutines.launch
import java.io.File

/**
 * PS5-styled Game Library & Store Catalog screen.
 *
 * Implements midnight-blue ambient glow background, dual touch / D-Pad support,
 * and high-fidelity PS5 game detail presentation with Play-on-Demand downloads.
 */
@Composable
fun CatalogScreen(
    console: ConsoleManifest,
    repoClient: RepositoryClient,
    downloader: GameDownloader,
    cacheManager: CacheManager,
    onBack: () -> Unit,
    onLaunchGame: (GameEntry, File) -> Unit
) {
    var games by remember { mutableStateOf<List<GameEntry>>(emptyList()) }
    var focusedGame by remember { mutableStateOf<GameEntry?>(null) }
    var isLoadingCatalog by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()
    val activeDownloads = remember { mutableStateMapOf<String, DownloadProgress>() }
    val firstFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    val context = LocalContext.current

    LaunchedEffect(console.id) {
        isLoadingCatalog = true
        val result = repoClient.fetchConsoleCatalog(console.catalogUrl, console.id)
        val catalog = result.getOrNull() ?: repoClient.loadAssetCatalogSync(console.id)
        val localGames = try {
            ExternalRomScanner.scanAllExternalRoms(context).filter { it.consoleId == console.id }
        } catch (_: Exception) { emptyList() }
        games = localGames + catalog.games.filter { g -> !localGames.any { it.fileUrl == g.fileUrl } }
        focusedGame = games.firstOrNull()
        isLoadingCatalog = false
    }

    LaunchedEffect(games) {
        if (games.isNotEmpty()) {
            try {
                firstFocus.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // --- IMMERSIVE PS5 AMBIENT GLOW BACKGROUND (#080C14 to #151D2A) ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF080C14), Color(0xFF0E1422), Color(0xFF151D2A))
                )
            )
    ) {
        // Cyan ambient glow (Top Right)
        Box(
            modifier = Modifier
                .size(700.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.08f), Color.Transparent),
                        radius = 700f
                    )
                )
        )

        // Magenta/Purple ambient glow (Bottom Left)
        Box(
            modifier = Modifier
                .size(800.dp)
                .align(Alignment.BottomStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF8A2BE2).copy(alpha = 0.08f), Color.Transparent),
                        radius = 800f
                    )
                )
        )

        val context = LocalContext.current
        val isTvDevice = remember { context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) }
        val horizontalPadding = if (isTvDevice) 36.dp else 16.dp
        val verticalPadding = if (isTvDevice) 24.dp else 12.dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(if (isTvDevice) 32.dp else 16.dp)
        ) {
            // --- LEFT COLUMN: GAME TILES GRID (TOUCH SWIPE + D-PAD NAVIGATION) ---
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxSize()
            ) {
                // Top header with Back button & Console Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Ps5BackButton(onClick = onBack)
                    Spacer(modifier = Modifier.width(18.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = console.name.uppercase(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = TextWhite,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "BIBLIOTECA PS5",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonCyan
                                )
                            }
                        }
                        Text(
                            text = "${games.size} Títulos Disponibles  •  Play-on-Demand  •  4K Upscaled",
                            fontSize = 12.sp,
                            color = Color(0xFF8E9BAE)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isLoadingCatalog) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonCyan)
                    }
                } else if (games.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No se encontraron títulos disponibles.",
                            color = TextGray,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(games) { index, game ->
                            val local = remember(game.fileUrl) { File(game.fileUrl) }
                            val isCached = (local.exists() && local.length() > 0) || cacheManager.isRomCached(game.id, game.expectedExtension)
                            val downloadStatus = activeDownloads[game.id]

                            val itemModifier = if (index == 0) {
                                Modifier.focusRequester(firstFocus)
                            } else Modifier

                            Ps5GameTile(
                                game = game,
                                isCached = isCached,
                                downloadProgress = downloadStatus,
                                modifier = itemModifier,
                                onFocused = { focusedGame = game },
                                onAction = {
                                    if (local.exists() && local.length() > 0) {
                                        onLaunchGame(game, local)
                                    } else if (isCached) {
                                        val romFile = cacheManager.getRomFile(game.id, game.expectedExtension)
                                        onLaunchGame(game, romFile)
                                    } else {
                                        // Trigger Play-on-Demand download with app-persistent scope
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
                    }
                }
            }

            // --- RIGHT COLUMN: PS5 GAME HUB HERO DETAILS ---
            Column(
                modifier = Modifier
                    .weight(0.75f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F1524).copy(alpha = 0.90f))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.04f))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(26.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                focusedGame?.let { game ->
                    val local = remember(game.fileUrl) { File(game.fileUrl) }
                    val isCached = (local.exists() && local.length() > 0) || cacheManager.isRomCached(game.id, game.expectedExtension)
                    val sizeMb = if (game.sizeBytes > 0) game.sizeBytes / (1024 * 1024) else 1
                    val downloadStatus = activeDownloads[game.id]

                    Column {
                        // Category tag
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan)
                            )
                            Text(
                                text = "DETALLES DEL JUEGO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonCyan,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Game Title
                        Text(
                            text = game.title,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = TextWhite,
                            lineHeight = 32.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Metadata pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Ps5DetailPill(icon = Icons.Default.Schedule, text = "${game.releaseYear}")
                            Ps5DetailPill(icon = Icons.Default.Games, text = game.genre)
                            Ps5DetailPill(icon = Icons.Default.Person, text = "${game.maxPlayers}P")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Cache status banner
                        if (isCached) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BadgeCachedBg)
                                    .border(1.dp, Color(0xFF00E676).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = BadgeCachedText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Instalado en Almacenamiento local • Listo",
                                    fontSize = 11.sp,
                                    color = BadgeCachedText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BadgeCloudBg)
                                    .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = BadgeCloudText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Play-on-Demand • Descarga directa (~$sizeMb MB)",
                                    fontSize = 11.sp,
                                    color = BadgeCloudText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Synopsis
                        Text(
                            text = "SINOPSIS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = game.description.ifEmpty {
                                "Disfruta de esta obra maestra del retro gaming con resolución nativa ampliada y sonido estéreo inmersivo."
                            },
                            fontSize = 13.sp,
                            color = TextGray,
                            lineHeight = 20.sp
                        )
                    }

                    // Bottom Action Button: JUGAR / DESCARGAR
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (downloadStatus is DownloadProgress.Downloading) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Descargando título...",
                                        fontSize = 12.sp,
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${downloadStatus.percentage.toInt()}%",
                                        fontSize = 12.sp,
                                        color = FocusYellow,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { downloadStatus.percentage / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = NeonCyan,
                                    trackColor = Color(0xFF1B2436)
                                )
                            }
                        } else {
                            Ps5PlayDownloadButton(
                                isCached = isCached,
                                sizeMb = sizeMb,
                                onClick = {
                                    if (local.exists() && local.length() > 0) {
                                        onLaunchGame(game, local)
                                    } else if (isCached) {
                                        val romFile = cacheManager.getRomFile(game.id, game.expectedExtension)
                                        onLaunchGame(game, romFile)
                                    } else {
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
                    }
                } ?: run {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Selecciona un título de la biblioteca", color = TextMuted)
                    }
                }
            }
        }
    }
}

/**
 * PS5 Game Card Tile for grid browsing.
 */
@Composable
private fun Ps5GameTile(
    game: GameEntry,
    isCached: Boolean,
    downloadProgress: DownloadProgress?,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onAction: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        label = "Ps5TileScale"
    )

    val cardShape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .height(138.dp)
            .clip(cardShape)
            .background(
                if (isFocused) {
                    Brush.linearGradient(listOf(Color(0xFF1D283E), Color(0xFF121929)))
                } else {
                    Brush.linearGradient(listOf(Color(0xFF151C2C), Color(0xFF0F1522)))
                }
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                brush = if (isFocused) {
                    Brush.linearGradient(listOf(NeonCyan, NeonPink))
                } else {
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f)))
                },
                shape = cardShape
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onAction)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Game title & genre
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${game.genre} • ${game.releaseYear}",
                        fontSize = 11.sp,
                        color = if (isFocused) NeonCyan else TextGray
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play on Demand status icon
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isCached) BadgeCachedBg else BadgeCloudBg)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = if (isCached) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (isCached) BadgeCachedText else BadgeCloudText,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Download progress or Action Button badge
            if (downloadProgress is DownloadProgress.Downloading) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Descargando...",
                            fontSize = 10.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${downloadProgress.percentage.toInt()}%",
                            fontSize = 10.sp,
                            color = FocusYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress.percentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NeonCyan,
                        trackColor = SurfaceDark
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sizeLabel = if (game.sizeBytes > 0) "${game.sizeBytes / (1024 * 1024)} MB" else ""
                    Text(
                        text = sizeLabel,
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isFocused) {
                                    if (isCached) AccentGreen else NeonCyan
                                } else {
                                    Color.White.copy(alpha = 0.08f)
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isCached) Icons.Default.PlayArrow else Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = if (isFocused) Color(0xFF070B14) else (if (isCached) AccentGreen else NeonCyan),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isCached) "JUGAR" else "DESCARGAR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isFocused) Color(0xFF070B14) else (if (isCached) AccentGreen else NeonCyan)
                        )
                    }
                }
            }
        }
    }
}

/**
 * PS5 Hero CTA Play / Download button.
 */
@Composable
private fun Ps5PlayDownloadButton(
    isCached: Boolean,
    sizeMb: Long,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1.0f, label = "PlayBtnScale")

    val backgroundBrush = if (isCached) {
        Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00B0FF)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF00D2FF), Color(0xFFFF007F)))
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundBrush)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isCached) Icons.Default.PlayArrow else Icons.Default.CloudDownload,
                contentDescription = null,
                tint = Color(0xFF070B14),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (isCached) "JUGAR AHORA" else "DESCARGAR Y JUGAR (~$sizeMb MB)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF070B14),
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun Ps5BackButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (isFocused) NeonCyan else Color(0xFF161F32))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f),
                shape = CircleShape
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "Atrás",
            tint = if (isFocused) Color(0xFF070B14) else TextWhite,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun Ps5DetailPill(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDarkVariant)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NeonCyan,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = TextWhite,
            fontWeight = FontWeight.SemiBold
        )
    }
}
