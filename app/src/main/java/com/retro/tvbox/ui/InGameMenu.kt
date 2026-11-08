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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
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

private enum class MenuState {
    MAIN,
    SAVE_SLOTS,
    LOAD_SLOTS
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun InGameMenu(
    gameTitle: String,
    onResume: () -> Unit,
    onSaveState: (slot: Int) -> Unit,
    onLoadState: (slot: Int) -> Unit,
    onReset: () -> Unit,
    onExitWithPurge: () -> Unit,
    onExitKeepCache: () -> Unit
) {
    var menuState by remember { mutableStateOf(MenuState.MAIN) }
    var selectedSlot by remember { mutableIntStateOf(0) }

    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(menuState) {
        try {
            firstFocus.requestFocus()
        } catch (e: Exception) {
            // Handled on first user dpad click
        }
    }

    Dialog(
        onDismissRequest = onResume,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceDark)
                    .border(2.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "MENÚ EN JUEGO (OSD)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = gameTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(24.dp))

                when (menuState) {
                    MenuState.MAIN -> {
                        // Options
                        TvMenuButton(
                            icon = Icons.Default.PlayArrow,
                            title = "Continuar Partida",
                            subtitle = "Reanudar la emulación de inmediato",
                            modifier = Modifier.focusRequester(firstFocus),
                            onClick = onResume
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TvMenuButton(
                            icon = Icons.Default.Save,
                            title = "Guardar Estado (Savestate)",
                            subtitle = "Salvar instantánea en una ranura de memoria",
                            onClick = { menuState = MenuState.SAVE_SLOTS }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TvMenuButton(
                            icon = Icons.Default.Upload,
                            title = "Cargar Estado",
                            subtitle = "Restaurar partida desde una ranura guardada",
                            onClick = { menuState = MenuState.LOAD_SLOTS }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TvMenuButton(
                            icon = Icons.Default.Refresh,
                            title = "Reiniciar Consola",
                            subtitle = "Reset suave al juego en curso",
                            onClick = onReset
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TvMenuButton(
                            icon = Icons.Default.DeleteSweep,
                            title = "Salir y Purgar Caché",
                            subtitle = "Cerrar sesión y liberar espacio de ROM en disco",
                            isDestructive = true,
                            onClick = onExitWithPurge
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TvMenuButton(
                            icon = Icons.Default.ExitToApp,
                            title = "Salir Manteniendo Caché",
                            subtitle = "Cerrar sesión pero conservar ROM para juego rápido",
                            onClick = onExitKeepCache
                        )
                    }

                    MenuState.SAVE_SLOTS, MenuState.LOAD_SLOTS -> {
                        val isSaving = menuState == MenuState.SAVE_SLOTS
                        Text(
                            text = if (isSaving) "SELECCIONA RANURA DE GUARDADO" else "SELECCIONA RANURA PARA CARGAR",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = FocusYellow
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (slot in 0..4) {
                                val itemModifier = if (slot == 0) Modifier.focusRequester(firstFocus) else Modifier
                                TvSlotCard(
                                    slotIndex = slot,
                                    modifier = itemModifier,
                                    onClick = {
                                        if (isSaving) onSaveState(slot) else onLoadState(slot)
                                        onResume()
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        TvMenuButton(
                            icon = Icons.Default.ExitToApp,
                            title = "Volver al Menú Principal",
                            subtitle = "",
                            onClick = { menuState = MenuState.MAIN }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvMenuButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.03f else 1.0f, label = "TvMenuBtnScale")

    val bg = when {
        isFocused -> if (isDestructive) NeonPink else NeonCyan
        else -> SurfaceCard
    }

    val contentColor = when {
        isFocused -> BackgroundDark
        isDestructive -> NeonPink
        else -> TextWhite
    }

    Row(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isFocused) BackgroundDark.copy(alpha = 0.8f) else TextMuted
                )
            }
        }
    }
}

@Composable
private fun TvSlotCard(
    slotIndex: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.15f else 1.0f, label = "SlotScale")

    Box(
        modifier = modifier
            .scale(scale)
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) NeonCyan else SurfaceDarkVariant)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) FocusYellow else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SLOT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) BackgroundDark else TextMuted
            )
            Text(
                text = "$slotIndex",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = if (isFocused) BackgroundDark else TextWhite
            )
        }
    }
}
