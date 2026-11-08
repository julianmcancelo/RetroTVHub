package com.retro.tvbox.ui.components

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.retro.tvbox.repository.CacheManager
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

/**
 * Diálogo de configuración y supervisión de almacenamiento Play-on-Demand.
 * Permite ajustar el límite LRU de 1 GB a 4 GB, monitorear el consumo de temp_roms/
 * y verificar el blindaje físico de filesDir/saves/ y filesDir/memcards/.
 */
@Composable
fun CacheSettingsDialog(
    cacheManager: CacheManager,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }

    val stats = remember(refreshKey) { cacheManager.getStats() }
    val currentMaxGb = remember(refreshKey) { cacheManager.getMaxCacheSizeGb() }

    val usedMb = stats.usedBytes / (1024 * 1024)
    val maxMb = stats.maxBytes / (1024 * 1024)
    val progress = (stats.usedPercentage / 100f).coerceIn(0f, 1f)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD9080B14))
                .onKeyEvent { event ->
                    if (event.key == Key.Back || event.key == Key.Escape) {
                        onDismissRequest()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(680.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF101624))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF00E5FF).copy(alpha = 0.6f), Color(0xFF7C4DFF).copy(alpha = 0.6f))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(30.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeonCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "ALMACENAMIENTO PLAY-ON-DEMAND",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Partición Volátil: cacheDir/temp_roms/",
                                    fontSize = 11.sp,
                                    color = TextGray
                                )
                            }
                        }

                        // Close button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1D2638))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .clickable { onDismissRequest() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Gauge Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF151D2C))
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Espacio en Uso",
                                        fontSize = 12.sp,
                                        color = TextGray
                                    )
                                    Text(
                                        text = "$usedMb MB / $maxMb MB",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TextWhite
                                    )
                                }

                                Text(
                                    text = "${stats.usedPercentage.toInt()}%",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (stats.usedPercentage > 85f) NeonPink else NeonCyan
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = if (stats.usedPercentage > 85f) NeonPink else NeonCyan,
                                trackColor = Color(0xFF0C101A),
                                strokeCap = StrokeCap.Round
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "ROMs temporales activas: ${stats.totalFiles} juegos almacenados en caché",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Selector de Límite LRU (1 GB a 4 GB)
                    Text(
                        text = "LÍMITE MÁXIMO DE CACHÉ (POLÍTICA LRU)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (gb in 1..4) {
                            val isSelected = currentMaxGb == gb
                            QuotaPill(
                                label = "$gb GB",
                                isSelected = isSelected,
                                onClick = {
                                    cacheManager.setMaxCacheSizeGb(gb)
                                    refreshKey++
                                    Toast.makeText(context, "Límite ajustado a $gb GB", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Critical Protection Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF003822).copy(alpha = 0.5f))
                            .border(1.dp, AccentGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Protección Total de Partidas y Memory Cards",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen
                            )
                            Text(
                                text = "Los archivos en filesDir/saves/ y filesDir/memcards/ NUNCA son tocados por la limpieza.",
                                fontSize = 11.sp,
                                color = TextWhite.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Actions Bottom Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CacheActionButton(
                                icon = Icons.Default.Refresh,
                                label = "Aplicar LRU",
                                onClick = {
                                    cacheManager.enforceLRU()
                                    refreshKey++
                                    Toast.makeText(context, "Purga LRU preventiva ejecutada", Toast.LENGTH_SHORT).show()
                                }
                            )

                            CacheActionButton(
                                icon = Icons.Default.DeleteSweep,
                                label = "Vaciar Caché",
                                isDestructive = true,
                                onClick = {
                                    val freed = cacheManager.clearAll()
                                    refreshKey++
                                    Toast.makeText(context, "Liberados ${freed / (1024 * 1024)} MB de ROMs", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // Close button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeonCyan)
                                .border(1.dp, Color.White, RoundedCornerShape(12.dp))
                                .clickable { onDismissRequest() }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "LISTO",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF070B14)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "quotaScale")

    val bg = when {
        isFocused -> FocusYellow
        isSelected -> NeonCyan.copy(alpha = 0.25f)
        else -> Color(0xFF151D2C)
    }

    val textColor = when {
        isFocused -> BackgroundDark
        isSelected -> NeonCyan
        else -> TextWhite
    }

    val border = when {
        isFocused -> FocusBorderColor
        isSelected -> NeonCyan
        else -> Color.White.copy(alpha = 0.1f)
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(if (isSelected || isFocused) 2.dp else 1.dp, border, RoundedCornerShape(10.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun CacheActionButton(
    icon: ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "cacheBtnScale")

    val bg = when {
        isFocused -> if (isDestructive) NeonPink else FocusYellow
        isDestructive -> Color(0xFF38101C)
        else -> Color(0xFF151D2C)
    }

    val contentColor = when {
        isFocused -> BackgroundDark
        isDestructive -> NeonPink
        else -> TextWhite
    }

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, if (isFocused) FocusBorderColor else Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}
