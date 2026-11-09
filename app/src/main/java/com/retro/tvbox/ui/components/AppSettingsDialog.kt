package com.retro.tvbox.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.retro.tvbox.BuildConfig
import com.retro.tvbox.RetroApplication
import com.retro.tvbox.core.BiosInfo
import com.retro.tvbox.core.BiosManager
import com.retro.tvbox.core.BiosStatus
import com.retro.tvbox.core.BiosStatusEntry
import com.retro.tvbox.input.InputRouter
import com.retro.tvbox.repository.CacheManager
import com.retro.tvbox.repository.CacheStats
import com.retro.tvbox.repository.ExternalRomScanner
import com.retro.tvbox.repository.UpdateInfo
import com.retro.tvbox.repository.UpdateManager
import com.retro.tvbox.ui.theme.AppThemeManager
import com.retro.tvbox.ui.theme.UiThemeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hub Integral de Ajustes PS5 / Android TV (`AppSettingsDialog`).
 *
 * Incluye navegación fluida con D-pad, 5 pestañas completas y persistencia directa:
 * 1. Estilo Visual (PS5 Horizon / PS4 Dynamic Flow / Steam Big Picture).
 * 2. Control & Mandos (Detección DualSense/Xbox, test de vibración háptica, servidor celular).
 * 3. Firmware & BIOS (Descarga en 1 clic de PS1, PS2, GBA con verificación MD5).
 * 4. Almacenamiento & Caché (Medidor LRU, limpieza de ROMs y escaneo de USB/SD).
 * 5. Actualizaciones OTA (Verificación en vivo con GitHub Releases e instalación).
 */
@Composable
fun AppSettingsDialog(
    biosManager: BiosManager = remember { RetroApplication.instance.biosManager },
    cacheManager: CacheManager = remember { RetroApplication.instance.cacheManager },
    inputRouter: InputRouter = remember { RetroApplication.instance.inputRouter },
    serverUrl: String,
    onOpenQrModal: () -> Unit = {},
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeTheme by AppThemeManager.currentTheme
    val palette = remember(activeTheme) { AppThemeManager.getPalette(activeTheme) }

    var selectedTab by remember { mutableStateOf(SettingsTab.VISUAL_STYLE) }

    val closeFocusRequester = remember { FocusRequester() }
    val firstTabFocusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .onKeyEvent { event ->
                    if (event.key == Key.Back || event.key == Key.Escape) {
                        onDismissRequest()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            // Contenedor principal de 10-foot TV
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.backgroundBase)
                    .border(1.5.dp, palette.surfaceHighlight, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // --- TOP BAR DEL MODAL DE AJUSTES ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(palette.topGradientStart, palette.topGradientEnd)
                                )
                            )
                            .padding(horizontal = 28.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(palette.surfaceHighlight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = palette.accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "PANEL DE CONFIGURACIÓN DEL SISTEMA",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = palette.textPrimary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Personalización de Interfaz • Mandos Hápticos • BIOS • Almacenamiento • Versión ${BuildConfig.VERSION_NAME}",
                                    fontSize = 12.sp,
                                    color = palette.textSecondary
                                )
                            }
                        }

                        // Botón de Cerrar Modal
                        FocusableHeaderButton(
                            icon = Icons.Default.Close,
                            label = "Cerrar (O/B)",
                            focusColor = palette.focusBorderColor,
                            modifier = Modifier.focusRequester(closeFocusRequester),
                            onClick = onDismissRequest
                        )
                    }

                    // --- CUERPO: NAVEGADOR DE PESTAÑAS A LA IZQUIERDA + CONTENIDO A LA DERECHA ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Columna Izquierda: Pestañas de Ajustes
                        Column(
                            modifier = Modifier
                                .width(280.dp)
                                .fillMaxHeight()
                                .background(palette.surfaceColor)
                                .padding(vertical = 16.dp, horizontal = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsTab.entries.forEachIndexed { index, tab ->
                                SettingsTabItem(
                                    tab = tab,
                                    isSelected = selectedTab == tab,
                                    accentColor = palette.accentColor,
                                    focusColor = palette.focusBorderColor,
                                    modifier = if (index == 0) Modifier.focusRequester(firstTabFocusRequester) else Modifier,
                                    onClick = { selectedTab = tab }
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Indicador de estado del Servidor de Mandos
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.surfaceHighlight)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00E676))
                                        )
                                        Text(
                                            text = "SERVIDOR ACTIVO",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E676),
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = serverUrl,
                                        fontSize = 11.sp,
                                        color = palette.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Separador Vertical
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(palette.surfaceHighlight)
                        )

                        // Área Principal: Contenido según la Pestaña Seleccionada
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(24.dp)
                        ) {
                            when (selectedTab) {
                                SettingsTab.VISUAL_STYLE -> {
                                    VisualStyleTabContent(
                                        activeStyle = activeTheme,
                                        onSelectStyle = { newStyle ->
                                            AppThemeManager.setTheme(newStyle, context)
                                            Toast.makeText(context, "Estilo aplicado: ${newStyle.title}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                SettingsTab.CONTROLLERS -> {
                                    ControllersTabContent(
                                        inputRouter = inputRouter,
                                        serverUrl = serverUrl,
                                        onOpenQrModal = onOpenQrModal,
                                        palette = palette
                                    )
                                }
                                SettingsTab.BIOS_FIRMWARE -> {
                                    BiosFirmwareTabContent(
                                        biosManager = biosManager,
                                        palette = palette
                                    )
                                }
                                SettingsTab.STORAGE_CACHE -> {
                                    StorageCacheTabContent(
                                        cacheManager = cacheManager,
                                        palette = palette
                                    )
                                }
                                SettingsTab.UPDATES_OTA -> {
                                    UpdatesOtaTabContent(
                                        palette = palette
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class SettingsTab(val label: String, val icon: ImageVector) {
    VISUAL_STYLE("Estilo Visual", Icons.Default.Palette),
    CONTROLLERS("Mandos y Controles", Icons.Default.SportsEsports),
    BIOS_FIRMWARE("Firmware & BIOS", Icons.Default.Memory),
    STORAGE_CACHE("Almacenamiento", Icons.Default.Storage),
    UPDATES_OTA("Actualizaciones OTA", Icons.Default.SystemUpdate)
}

/**
 * Pestaña 1: Selector en caliente de formatos de interfaz y estética.
 */
@Composable
private fun VisualStyleTabContent(
    activeStyle: UiThemeStyle,
    onSelectStyle: (UiThemeStyle) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "FORMATO VISUAL & ESTILO DE EXPERIENCIA",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 1.sp
        )
        Text(
            text = "Elige la estética visual del inicio. Los cambios se guardan y aplican instantáneamente en tiempo real.",
            fontSize = 13.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(UiThemeStyle.entries) { style ->
                val isSelected = style == activeStyle
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()

                val cardBg = when {
                    isSelected -> Color(0xFF1E293B)
                    isFocused -> Color(0xFF1A2333)
                    else -> Color(0xFF0F172A)
                }
                val borderColor = when {
                    isFocused -> Color.White
                    isSelected -> Color(0xFF00E5FF)
                    else -> Color(0xFF334155)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardBg)
                        .border(if (isFocused || isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                        .clickable(interactionSource = interactionSource, indication = null) {
                            onSelectStyle(style)
                        }
                        .focusable(interactionSource = interactionSource)
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = style.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.White
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF004D5A) else Color(0xFF334155))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = style.tag,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFCBD5E1)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = style.subtitle,
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Activo",
                                tint = Color(0xFF060913),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Activar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pestaña 2: Control & Mandos físicos, vibración háptica y estado del servidor de celular.
 */
@Composable
private fun ControllersTabContent(
    inputRouter: InputRouter,
    serverUrl: String,
    onOpenQrModal: () -> Unit,
    palette: com.retro.tvbox.ui.theme.UiThemePalette
) {
    val context = LocalContext.current
    val slots by inputRouter.slotsStateFlow.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "MANDOS, GAMEPADS Y RETROALIMENTACIÓN HÁPTICA",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 1.sp
        )
        Text(
            text = "Reconocimiento automático de mandos DualSense, DualShock, Xbox y conexión virtual de smartphones.",
            fontSize = 13.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Sección: Mandos asignados (Jugadores 1 a 4)
            items(slots) { slot ->
                val isConnected = slot.type != InputRouter.ControllerType.NONE
                val playerLabel = "JUGADOR ${slot.playerIndex + 1}"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceColor)
                        .border(1.dp, palette.surfaceHighlight, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF334155)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (slot.type == InputRouter.ControllerType.VIRTUAL_PHONE) Icons.Default.PhoneAndroid else Icons.Default.Gamepad,
                                contentDescription = null,
                                tint = if (isConnected) Color(0xFF00E676) else Color(0xFF94A3B8),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = playerLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isConnected) palette.accentColor else Color(0xFF94A3B8)
                                )
                                Text(
                                    text = "• ${if (isConnected) slot.displayName else "Sin Mando"}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = when (slot.type) {
                                    InputRouter.ControllerType.PHYSICAL_GAMEPAD -> "Mando físico USB / Bluetooth activo"
                                    InputRouter.ControllerType.VIRTUAL_PHONE -> "Celular emparejado como Gamepad táctil"
                                    InputRouter.ControllerType.KEYBOARD_REMOTE -> "Control remoto / Teclado Android TV"
                                    else -> "Conecta un mando o escanea el QR con tu smartphone"
                                },
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                        }
                    }

                    // Test de vibración para mandos conectados
                    FocusableButton(
                        text = "Vibrar Test",
                        icon = Icons.Default.Vibration,
                        focusColor = palette.focusBorderColor,
                        onClick = {
                            testControllerVibration(context)
                            Toast.makeText(context, "Enviando pulso de vibración háptica...", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Sección: Mando Celular Virtual
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceHighlight)
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "EMPAREJAR CELULAR COMO JOYSTICK",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Abre la cámara de cualquier teléfono conectado al mismo Wi-Fi y juega sin instalar aplicaciones adicionales.",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    FocusableButton(
                        text = "Ver Código QR",
                        icon = Icons.Default.PhoneAndroid,
                        focusColor = palette.focusBorderColor,
                        onClick = onOpenQrModal
                    )
                }
            }
        }
    }
}

/**
 * Pestaña 3: Firmware & BIOS con descarga directa e integridad MD5.
 */
@Composable
private fun BiosFirmwareTabContent(
    biosManager: BiosManager,
    palette: com.retro.tvbox.ui.theme.UiThemePalette
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var biosEntries by remember { mutableStateOf<List<BiosStatusEntry>>(emptyList()) }
    var downloadingFile by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            biosEntries = biosManager.getAllBiosStatuses()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "IMÁGENES DE SISTEMA Y BIOS OFICIALES",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "PlayStation 1 (SCPH1001), PlayStation 2 y Game Boy Advance. Archivos alojados en filesDir/system/.",
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            FocusableButton(
                text = "Recargar Estado",
                icon = Icons.Default.Refresh,
                focusColor = palette.focusBorderColor,
                onClick = {
                    coroutineScope.launch {
                        biosEntries = withContext(Dispatchers.IO) { biosManager.getAllBiosStatuses() }
                        Toast.makeText(context, "Estado de BIOS actualizado", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(biosEntries) { entry ->
                val isInstalled = entry.status == BiosStatus.INSTALLED_VERIFIED
                val isDownloading = downloadingFile == entry.info.filename

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceColor)
                        .border(1.dp, palette.surfaceHighlight, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = entry.info.filename,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            if (entry.info.isMandatory) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE53935))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "OBLIGATORIA",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${entry.info.description}  •  ${entry.info.consoleId.uppercase()}",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )

                        Text(
                            text = if (isInstalled) "Instalada y verificada (${entry.currentSizeBytes / 1024} KB)"
                            else "Falta en el sistema (Se descargará automáticamente si se pulsa el botón)",
                            fontSize = 11.sp,
                            color = if (isInstalled) Color(0xFF00E676) else Color(0xFFFFB74D)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    if (isInstalled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Lista",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }
                    } else if (isDownloading) {
                        CircularProgressIndicator(
                            color = palette.accentColor,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        FocusableButton(
                            text = "Descargar",
                            icon = Icons.Default.CloudDownload,
                            focusColor = palette.focusBorderColor,
                            onClick = {
                                if (entry.info.downloadUrl != null) {
                                    downloadingFile = entry.info.filename
                                    coroutineScope.launch {
                                        val res = biosManager.downloadBios(entry.info)
                                        downloadingFile = null
                                        if (res.isSuccess) {
                                            biosEntries = withContext(Dispatchers.IO) { biosManager.getAllBiosStatuses() }
                                            Toast.makeText(context, "BIOS ${entry.info.filename} instalada con éxito", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Esta BIOS debe importarse manualmente", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pestaña 4: Almacenamiento & Caché con límite LRU y limpieza segura.
 */
@Composable
private fun StorageCacheTabContent(
    cacheManager: CacheManager,
    palette: com.retro.tvbox.ui.theme.UiThemePalette
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(cacheManager.getStats()) }
    var isScanning by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "ALMACENAMIENTO PLAY-ON-DEMAND Y CACHÉ",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 1.sp
        )
        Text(
            text = "Supervisión de ROMs temporales en cacheDir/temp_roms/ y protección total de saves y memcards.",
            fontSize = 13.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        // Medidor de ocupación de almacenamiento
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surfaceColor)
                .border(1.dp, palette.surfaceHighlight, RoundedCornerShape(14.dp))
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
                            text = "USO DE CACHÉ DE JUEGOS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = palette.accentColor,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "${(stats.usedBytes / (1024 * 1024))} MB usados de ${stats.maxGbConfigured} GB asignados",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "${stats.totalFiles} Juegos en Disco",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { (stats.usedPercentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = palette.accentColor,
                    trackColor = palette.surfaceHighlight,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Opciones de acción: Limpieza y Escaneo de USB
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tarjeta de Vaciar Caché
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surfaceColor)
                    .border(1.dp, palette.surfaceHighlight, RoundedCornerShape(14.dp))
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFFF5252))
                    Text(text = "Liberar Espacio Temporal", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Elimina las ROMs descargadas en caché para recuperar espacio. Tus partidas guardadas están 100% protegidas y no se tocarán.",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                FocusableButton(
                    text = "Vaciar Caché Ahora",
                    icon = Icons.Default.DeleteSweep,
                    focusColor = Color(0xFFFF5252),
                    onClick = {
                        val freed = cacheManager.clearAll()
                        stats = cacheManager.getStats()
                        Toast.makeText(context, "Se liberaron ${freed / (1024 * 1024)} MB de caché", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Tarjeta de Escanear Pendrive / USB
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surfaceColor)
                    .border(1.dp, palette.surfaceHighlight, RoundedCornerShape(14.dp))
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = Color(0xFF00E5FF))
                    Text(text = "Escanear Pendrive / SD", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Detecta automáticamente memorias USB, tarjetas MicroSD y carpetas de Descargas con ROMs locales listas para jugar.",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                FocusableButton(
                    text = if (isScanning) "Escaneando..." else "Escanear Almacenamiento",
                    icon = Icons.Default.Refresh,
                    focusColor = palette.focusBorderColor,
                    onClick = {
                        isScanning = true
                        coroutineScope.launch {
                            val scanned = withContext(Dispatchers.IO) {
                                ExternalRomScanner.scanAllExternalRoms(context)
                            }
                            isScanning = false
                            Toast.makeText(context, "Escaneo completado: ${scanned.size} ROMs encontradas", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }
}

/**
 * Pestaña 5: Actualizaciones del Sistema OTA vía GitHub Releases.
 */
@Composable
private fun UpdatesOtaTabContent(
    palette: com.retro.tvbox.ui.theme.UiThemePalette
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }

    var isChecking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkMessage by remember { mutableStateOf("El sistema no ha buscado actualizaciones en esta sesión.") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "ACTUALIZACIONES DEL SISTEMA (OTA)",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 1.sp
        )
        Text(
            text = "Mantén RetroTV Hub al día con nuevas consolas, compatibilidad de mandos y núcleos Libretro actualizados.",
            fontSize = 13.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surfaceColor)
                .border(1.dp, palette.surfaceHighlight, RoundedCornerShape(14.dp))
                .padding(22.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VERSIÓN ACTUAL INSTALADA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = palette.accentColor,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "RetroTV Hub v${BuildConfig.VERSION_NAME}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    FocusableButton(
                        text = if (isChecking) "Comprobando..." else "Buscar Actualizaciones Ahora",
                        icon = Icons.Default.Refresh,
                        focusColor = palette.focusBorderColor,
                        onClick = {
                            isChecking = true
                            checkMessage = "Consultando GitHub Releases..."
                            coroutineScope.launch {
                                val res = updateManager.checkForUpdates()
                                isChecking = false
                                if (res.isSuccess) {
                                    val info = res.getOrNull()
                                    updateResult = info
                                    if (info != null) {
                                        checkMessage = "¡Nueva versión encontrada: ${info.version}!"
                                    } else {
                                        checkMessage = "¡Tienes la versión más reciente instalada (v${BuildConfig.VERSION_NAME})!"
                                    }
                                } else {
                                    checkMessage = "Error al conectar: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = checkMessage,
                    fontSize = 13.sp,
                    color = if (updateResult != null) Color(0xFF00E676) else palette.textSecondary
                )

                // Si hay actualización disponible, mostrar detalles y botón de descarga
                updateResult?.let { update ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceHighlight)
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "Novedades de la versión ${update.version}:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = update.releaseNotes,
                                fontSize = 12.sp,
                                color = palette.textSecondary,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isDownloading) {
                                downloadProgress?.let { prog ->
                                    LinearProgressIndicator(
                                        progress = { prog },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = palette.accentColor
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Descargando paquete: ${(prog * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                }
                            } else {
                                FocusableButton(
                                    text = "Descargar e Instalar ${update.version}",
                                    icon = Icons.Default.CloudDownload,
                                    focusColor = Color(0xFF00E676),
                                    onClick = {
                                        isDownloading = true
                                        coroutineScope.launch {
                                            updateManager.downloadAndInstallUpdate(
                                                updateInfo = update,
                                                onProgress = { p -> downloadProgress = p }
                                            )
                                            isDownloading = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Item de pestaña lateral en el panel de configuración.
 */
@Composable
private fun SettingsTabItem(
    tab: SettingsTab,
    isSelected: Boolean,
    accentColor: Color,
    focusColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isSelected -> accentColor.copy(alpha = 0.15f)
        isFocused -> Color(0xFF1E293B)
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> accentColor
        isFocused -> Color.White
        else -> Color(0xFF94A3B8)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(if (isFocused) 1.5.dp else 0.dp, focusColor, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = tab.label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}

/**
 * Botón navegable con control remoto TV y ratón.
 */
@Composable
private fun FocusableButton(
    text: String,
    icon: ImageVector? = null,
    focusColor: Color = Color(0xFF00E5FF),
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "btnScale")

    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) focusColor else Color(0xFF1E293B))
            .border(1.dp, if (isFocused) Color.White else Color(0xFF334155), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color(0xFF060913) else Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) Color(0xFF060913) else Color.White
        )
    }
}

/**
 * Botón superior del modal.
 */
@Composable
private fun FocusableHeaderButton(
    icon: ImageVector,
    label: String,
    focusColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) focusColor else Color(0xFF1E293B))
            .border(1.dp, if (isFocused) Color.White else Color(0xFF334155), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) Color(0xFF060913) else Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) Color(0xFF060913) else Color.White
        )
    }
}

/**
 * Emite una vibración háptica de prueba en mandos físicos o en el dispositivo local.
 */
private fun testControllerVibration(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(300)
        }
    } catch (_: Exception) {}
}
