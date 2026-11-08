package com.retro.tvbox.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.retro.tvbox.save.CardType
import com.retro.tvbox.save.MemoryCardManager
import com.retro.tvbox.save.MemoryCardSummary
import com.retro.tvbox.save.PS1BlockInfo
import com.retro.tvbox.ui.theme.AccentGreen
import com.retro.tvbox.ui.theme.BackgroundDark
import com.retro.tvbox.ui.theme.FocusBorderColor
import com.retro.tvbox.ui.theme.FocusYellow
import com.retro.tvbox.ui.theme.NeonCyan
import com.retro.tvbox.ui.theme.NeonPink
import com.retro.tvbox.ui.theme.SurfaceCard
import com.retro.tvbox.ui.theme.SurfaceDark
import com.retro.tvbox.ui.theme.SurfaceDarkVariant
import com.retro.tvbox.ui.theme.TextGray
import com.retro.tvbox.ui.theme.TextMuted
import com.retro.tvbox.ui.theme.TextWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MemCardScreen(
    memCardManager: MemoryCardManager,
    onBack: () -> Unit
) {
    var selectedCardType by remember { mutableStateOf(CardType.PS1) }
    var currentCardSummary by remember { mutableStateOf<MemoryCardSummary?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // Launcher para exportar la Memory Card actual vía Storage Access Framework (SAF)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null && currentCardSummary != null) {
            val res = memCardManager.exportCardToUri(currentCardSummary!!.file, uri)
            if (res.isSuccess) {
                Toast.makeText(context, "Memory Card exportada exitosamente", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Error exportando: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Launcher para importar una Memory Card externa
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val res = memCardManager.importCardFromUri(uri)
            if (res.isSuccess) {
                Toast.makeText(context, "Memory Card importada exitosamente", Toast.LENGTH_SHORT).show()
                refreshTrigger++
            } else {
                Toast.makeText(context, "Error al importar: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(selectedCardType, refreshTrigger) {
        val file = memCardManager.getOrCreateDefaultCard(selectedCardType, slot = 1)
        currentCardSummary = memCardManager.inspectCard(file)
    }

    LaunchedEffect(Unit) {
        try {
            firstFocus.requestFocus()
        } catch (e: Exception) {
            // Focus settles on dpad
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF131A2B), BackgroundDark),
                    radius = 1400f
                )
            )
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemCardBackButton(onClick = onBack)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(
                            text = "EXPLORADOR DE MEMORY CARDS",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = TextWhite,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Gestión Visual de Bloques y Archivos PS1 / PS2",
                            fontSize = 12.sp,
                            color = NeonCyan
                        )
                    }
                }

                // Switch between PS1 and PS2
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MemCardTabButton(
                        text = "PlayStation 1 (128 KB)",
                        isSelected = selectedCardType == CardType.PS1,
                        onClick = { selectedCardType = CardType.PS1 }
                    )
                    MemCardTabButton(
                        text = "PlayStation 2 (8 MB)",
                        isSelected = selectedCardType == CardType.PS2,
                        onClick = { selectedCardType = CardType.PS2 }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary Card
            currentCardSummary?.let { summary ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDarkVariant)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Archivo: ${summary.file.name}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Tamaño: ${summary.totalSizeBytes / 1024} KB  •  Estado: ${if (summary.isValid) "Válido y Formateado" else "Sin Formato"}",
                                fontSize = 12.sp,
                                color = if (summary.isValid) AccentGreen else NeonPink
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Respaldar a Descargas
                        MemCardActionPill(
                            icon = Icons.Default.Save,
                            label = "Respaldar",
                            onClick = {
                                val res = memCardManager.backupCardToDownloads(summary.file)
                                if (res.isSuccess) {
                                    Toast.makeText(
                                        context,
                                        "Respaldo creado en Descargas:\n${res.getOrThrow().name}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        )

                        // Exportar a USB o SAF
                        MemCardActionPill(
                            icon = Icons.Default.Upload,
                            label = "Exportar",
                            onClick = {
                                exportLauncher.launch(summary.file.name)
                            }
                        )

                        // Importar tarjeta externa
                        MemCardActionPill(
                            icon = Icons.Default.Download,
                            label = "Importar",
                            onClick = {
                                importLauncher.launch(arrayOf("*/*"))
                            }
                        )

                        // Formatear oficialmente
                        MemCardActionPill(
                            icon = Icons.Default.Refresh,
                            label = "Formatear",
                            isDestructive = true,
                            onClick = {
                                val ok = if (selectedCardType == CardType.PS1) {
                                    memCardManager.formatPS1Card(summary.file)
                                } else {
                                    memCardManager.formatPS2Card(summary.file)
                                }
                                refreshTrigger++
                                if (ok) {
                                    Toast.makeText(context, "Memory Card formateada con éxito", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Blocks Grid
            if (selectedCardType == CardType.PS1) {
                val blocks = currentCardSummary?.ps1Blocks ?: emptyList()
                TvLazyVerticalGrid(
                    columns = TvGridCells.Fixed(5),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(blocks) { index, block ->
                        val itemModifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                        PS1BlockCard(block = block, modifier = itemModifier)
                    }
                }
            } else {
                // PS2 Overview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Memory Card Oficial Sony PlayStation 2 (8 MB)",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Superbloque formateado compatible con NetherSX2 y AetherSX2.",
                            fontSize = 14.sp,
                            color = TextGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Capacidad libre: ~8,128 KB asignables dinámicamente en clusters.",
                            fontSize = 13.sp,
                            color = AccentGreen
                        )
                    }
                }
            }

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Persistencia segura: filesDir/memcards/ • Inmune a purgas de caché",
                    fontSize = 12.sp,
                    color = AccentGreen
                )
                Text(
                    text = "Formatos oficiales: PS1 (.mcr 128KB) y PS2 (.ps2 8MB)",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun PS1BlockCard(
    block: PS1BlockInfo,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "BlockScale")

    val bg = when {
        isFocused -> if (block.isUsed) NeonCyan else SurfaceDarkVariant
        block.isUsed -> Color(0xFF1E3A5F)
        else -> SurfaceCard
    }

    val titleColor = when {
        isFocused -> if (block.isUsed) BackgroundDark else FocusYellow
        block.isUsed -> TextWhite
        else -> TextMuted
    }

    Box(
        modifier = modifier
            .scale(scale)
            .height(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) FocusBorderColor else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .focusable(interactionSource = interactionSource)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "BLOQUE ${block.blockIndex}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused && block.isUsed) BackgroundDark else NeonCyan
                )
                Text(
                    text = if (block.isUsed) "8 KB" else "LIBRE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (block.isUsed) AccentGreen else TextMuted
                )
            }

            Column {
                Text(
                    text = block.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 2
                )
                if (block.productCode.isNotEmpty()) {
                    Text(
                        text = block.productCode,
                        fontSize = 9.sp,
                        color = if (isFocused && block.isUsed) BackgroundDark.copy(alpha = 0.7f) else TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun MemCardTabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> FocusYellow
        isSelected -> NeonCyan
        else -> SurfaceDarkVariant
    }

    val contentColor = when {
        isFocused -> BackgroundDark
        isSelected -> BackgroundDark
        else -> TextWhite
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun MemCardActionPill(
    icon: ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) (if (isDestructive) NeonPink else NeonCyan) else SurfaceCard)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) BackgroundDark else (if (isDestructive) NeonPink else TextWhite),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) BackgroundDark else TextWhite
        )
    }
}

@Composable
private fun MemCardBackButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (isFocused) FocusYellow else SurfaceDarkVariant)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "Atrás",
            tint = if (isFocused) BackgroundDark else TextWhite,
            modifier = Modifier.size(20.dp)
        )
    }
}
