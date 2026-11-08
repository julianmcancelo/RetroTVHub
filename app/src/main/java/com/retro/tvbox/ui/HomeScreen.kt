package com.retro.tvbox.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.tvbox.RetroApplication
import com.retro.tvbox.core.BiosManager
import com.retro.tvbox.repository.CacheManager
import com.retro.tvbox.repository.ConsoleManifest
import com.retro.tvbox.repository.RepositoryClient
import com.retro.tvbox.ui.components.BiosSettingsDialog
import com.retro.tvbox.ui.components.CacheSettingsDialog
import com.retro.tvbox.ui.components.Ps5TopBar
import com.retro.tvbox.ui.components.QrCodeDialog
import com.retro.tvbox.ui.theme.BackgroundDark
import com.retro.tvbox.ui.theme.FocusYellow
import com.retro.tvbox.ui.theme.NeonCyan
import com.retro.tvbox.ui.theme.NeonPink
import com.retro.tvbox.ui.theme.SurfaceCard
import com.retro.tvbox.ui.theme.TextGray
import com.retro.tvbox.ui.theme.TextMuted
import com.retro.tvbox.ui.theme.TextWhite

/**
 * PS5-styled cinematic Home Screen for RetroTV Hub.
 *
 * Supports seamless touch swiping/gestures on smartphones and full D-Pad navigation on Android TV.
 */
@Composable
fun HomeScreen(
    repoClient: RepositoryClient,
    cacheManager: CacheManager,
    serverUrl: String,
    onConsoleSelected: (ConsoleManifest) -> Unit,
    onOpenMemCards: () -> Unit,
    biosManager: BiosManager = remember { RetroApplication.instance.biosManager }
) {
    var consoles by remember { mutableStateOf<List<ConsoleManifest>>(emptyList()) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showBiosDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var focusedConsole by remember { mutableStateOf<ConsoleManifest?>(null) }

    val firstItemFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val result = repoClient.fetchRepositoryIndex()
        val index = result.getOrNull() ?: RepositoryClient.createBuiltinFactoryIndex()
        consoles = index.consoles
        if (consoles.isNotEmpty()) {
            focusedConsole = consoles.first()
        }
    }

    LaunchedEffect(consoles) {
        if (consoles.isNotEmpty()) {
            try {
                firstItemFocus.requestFocus()
            } catch (_: Exception) {
                // Focus will settle upon first user input
            }
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
                .size(650.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.09f), Color.Transparent),
                        radius = 650f
                    )
                )
        )

        // Magenta/Purple ambient glow (Bottom Left)
        Box(
            modifier = Modifier
                .size(750.dp)
                .align(Alignment.BottomStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF8A2BE2).copy(alpha = 0.08f), Color.Transparent),
                        radius = 750f
                    )
                )
        )

        // Subtle center highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.Center)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF00D2FF).copy(alpha = 0.03f), Color.Transparent)
                    )
                )
        )

        // Main content column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- 1. PS5 TOP BAR COMPONENT ---
            val cacheStats = remember { cacheManager.getStats() }
            val usedMb = cacheStats.usedBytes / (1024 * 1024)
            val maxMb = cacheStats.maxBytes / (1024 * 1024)

            Ps5TopBar(
                onOpenBios = { showBiosDialog = true },
                onOpenMemCards = onOpenMemCards,
                onConnectPhone = { showQrDialog = true },
                onOpenCache = { showCacheDialog = true },
                cacheStatusText = "Caché: $usedMb / $maxMb MB  •  PlayStation UI"
            )

            // --- 2. HERO HIGHLIGHT: SYSTEM DETAILS & PS5 TAG ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brush.horizontalGradient(listOf(NeonCyan, NeonPink)))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "EXPLORADOR DE SISTEMAS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF070B14),
                            letterSpacing = 1.2.sp
                        )
                    }

                    focusedConsole?.let {
                        Text(
                            text = "${it.manufacturer.uppercase()} • AÑO ${it.releaseYear}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = focusedConsole?.name ?: "Sistemas Clásicos y Retro",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    letterSpacing = 0.5.sp
                )
            }

            // --- 3. CONSOLE CAROUSEL (LAZY ROW WITH FULL TOUCH & D-PAD FOCUS SUPPORT) ---
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                itemsIndexed(consoles) { index, console ->
                    val itemModifier = if (index == 0) {
                        Modifier.focusRequester(firstItemFocus)
                    } else Modifier

                    Ps5ConsoleCardItem(
                        console = console,
                        modifier = itemModifier,
                        onFocused = { focusedConsole = console },
                        onClick = { onConsoleSelected(console) }
                    )
                }
            }

            // --- 4. BOTTOM DOCK FOOTER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x400A0D16))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        text = "Gestos: Desliza y toca en celulares  |  D-Pad [Arriba/Abajo/Izq/Der] y [OK] en Android TV",
                        fontSize = 11.sp,
                        color = TextGray
                    )
                }

                Text(
                    text = "RetroTV Hub • PS5 Edition v1.5",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Smartphone Gamepad QR Pairing Modal
        if (showQrDialog) {
            QrCodeDialog(
                serverUrl = serverUrl,
                onDismissRequest = { showQrDialog = false }
            )
        }

        // BIOS Settings Modal
        if (showBiosDialog) {
            BiosSettingsDialog(
                biosManager = biosManager,
                onDismissRequest = { showBiosDialog = false }
            )
        }

        // Cache Management Modal
        if (showCacheDialog) {
            CacheSettingsDialog(
                cacheManager = cacheManager,
                onDismissRequest = { showCacheDialog = false }
            )
        }
    }
}

/**
 * PS5 Console Card Item:
 * - 20 dp curved corners
 * - Manufacturer and year badges
 * - Glowing dual-color Cyan/Magenta neon border when focused
 * - Native touch click and TV D-pad focus elevation
 */
@Composable
private fun Ps5ConsoleCardItem(
    console: ConsoleManifest,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        label = "Ps5ConsoleCardScale"
    )

    // Console specific tint accents
    val (primaryGlow, secondaryGlow) = when (console.id.lowercase()) {
        "psx", "ps1" -> Color(0xFF00D2FF) to Color(0xFF2979FF)
        "ps2" -> Color(0xFF2979FF) to Color(0xFF9D4EDD)
        "snes" -> Color(0xFF7928CA) to Color(0xFFFF0080)
        "genesis", "sega" -> Color(0xFF00E5FF) to Color(0xFF003366)
        "gba" -> Color(0xFF9D4EDD) to Color(0xFFFF0055)
        else -> NeonCyan to NeonPink
    }

    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .scale(scale)
            .width(270.dp)
            .height(240.dp)
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    colors = if (isFocused) {
                        listOf(Color(0xFF192338), Color(0xFF101726))
                    } else {
                        listOf(Color(0xFF131A2B), Color(0xFF0B101C))
                    }
                )
            )
            .border(
                width = if (isFocused) 3.5.dp else 1.dp,
                brush = if (isFocused) {
                    Brush.linearGradient(listOf(NeonCyan, NeonPink))
                } else {
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f)))
                },
                shape = cardShape
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Badges: Manufacturer & Year
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFocused) NeonCyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.07f))
                        .border(
                            1.dp,
                            if (isFocused) NeonCyan else Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = console.manufacturer.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isFocused) FocusYellow else TextWhite
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${console.releaseYear}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextGray
                    )
                }
            }

            // Big Illuminated Emblem / Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(listOf(primaryGlow, secondaryGlow, primaryGlow))
                        )
                        .padding(2.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(Color(0xFF0C101A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = console.id.uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isFocused) NeonCyan else TextWhite
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (isFocused) NeonPink else Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Console Title & Supported Formats
            Column {
                Text(
                    text = console.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextWhite,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ROMs: ${console.supportedExtensions.joinToString(", ") { ".$it" }}",
                    fontSize = 11.sp,
                    color = if (isFocused) NeonCyan else TextGray,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
