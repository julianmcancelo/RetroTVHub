package com.retro.tvbox.ui.components

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.retro.tvbox.core.BiosInfo
import com.retro.tvbox.core.BiosManager
import com.retro.tvbox.core.BiosStatus
import com.retro.tvbox.core.BiosStatusEntry
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BiosManagerDialog(
    biosManager: BiosManager,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var biosStatuses by remember { mutableStateOf<List<BiosStatusEntry>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }

    // Progreso individual de descargas: filename -> Float (0..1)
    val downloadProgressMap = remember { mutableStateMapOf<String, Float>() }
    var isDownloadingBatch by remember { mutableStateOf(false) }

    // Target para importación manual individual
    var pendingImportTargetFilename by remember { mutableStateOf<String?>(null) }

    // Launcher para importación individual SAF
    val singleImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val targetName = pendingImportTargetFilename
        if (uri != null && targetName != null) {
            coroutineScope.launch {
                val res = biosManager.importBiosFromUri(uri, targetName)
                if (res.isSuccess) {
                    Toast.makeText(context, "BIOS $targetName importada con éxito", Toast.LENGTH_SHORT).show()
                    refreshKey++
                } else {
                    Toast.makeText(context, "Error importando: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
                pendingImportTargetFilename = null
            }
        }
    }

    // Launcher para importación SAF con auto-detección
    val autoDetectImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val res = biosManager.autoDetectAndImportBios(uri)
                if (res.isSuccess) {
                    val (info, _) = res.getOrThrow()
                    Toast.makeText(context, "BIOS identificada e importada: ${info.filename}", Toast.LENGTH_LONG).show()
                    refreshKey++
                } else {
                    Toast.makeText(context, "No reconocida: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            biosStatuses = biosManager.getAllBiosStatuses()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(1.dp, SurfaceDarkVariant, RoundedCornerShape(24.dp))
                .padding(28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "GESTOR Y VERIFICADOR DE BIOS",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = TextWhite,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Archivos de arranque para PlayStation 1 (scph1001/5501), PlayStation 2 (scph39001) y GBA",
                            fontSize = 12.sp,
                            color = NeonCyan
                        )
                    }

                    // Botón Cerrar
                    BiosActionButton(
                        icon = Icons.Default.Close,
                        label = "Cerrar",
                        onClick = onDismissRequest
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List of BIOS entries
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(biosStatuses, key = { it.info.filename }) { item ->
                        val isDownloading = downloadProgressMap.containsKey(item.info.filename)
                        val progress = downloadProgressMap[item.info.filename] ?: 0f

                        BiosRowCard(
                            entry = item,
                            isDownloading = isDownloading,
                            downloadProgress = progress,
                            onDownloadClick = {
                                coroutineScope.launch {
                                    downloadProgressMap[item.info.filename] = 0.05f
                                    val res = biosManager.downloadBios(item.info) { prog ->
                                        downloadProgressMap[item.info.filename] = prog
                                    }
                                    downloadProgressMap.remove(item.info.filename)
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "BIOS ${item.info.filename} instalada", Toast.LENGTH_SHORT).show()
                                        refreshKey++
                                    } else {
                                        Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onImportClick = {
                                pendingImportTargetFilename = item.info.filename
                                singleImportLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Global Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ruta de instalación segura: filesDir/system/ • Inmune a limpiezas de caché",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Importar cualquier BIOS con auto-detección
                        BiosActionButton(
                            icon = Icons.Default.FileOpen,
                            label = "Auto-Importar SAF",
                            onClick = {
                                autoDetectImportLauncher.launch(arrayOf("*/*"))
                            }
                        )

                        // Descargar todas las faltantes en 1 clic
                        val missingWithUrl = biosStatuses.count { it.status == BiosStatus.MISSING && it.info.downloadUrl != null }
                        BiosActionButton(
                            icon = Icons.Default.CloudDownload,
                            label = if (isDownloadingBatch) "Descargando..." else "Descargar Faltantes ($missingWithUrl)",
                            highlight = missingWithUrl > 0,
                            enabled = missingWithUrl > 0 && !isDownloadingBatch,
                            onClick = {
                                isDownloadingBatch = true
                                coroutineScope.launch {
                                    val results = biosManager.downloadAllMissing { bios, prog ->
                                        downloadProgressMap[bios.filename] = prog
                                    }
                                    downloadProgressMap.clear()
                                    isDownloadingBatch = false
                                    refreshKey++
                                    val successCount = results.values.count { it.isSuccess }
                                    Toast.makeText(context, "Descargadas $successCount BIOS con éxito", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BiosRowCard(
    entry: BiosStatusEntry,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownloadClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1.0f,
        label = "biosRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) SurfaceCard else SurfaceDarkVariant)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) FocusBorderColor else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status Icon & Main info
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when (entry.status) {
                BiosStatus.INSTALLED_VERIFIED -> AccentGreen
                BiosStatus.INSTALLED_SIZE_MISMATCH -> FocusYellow
                BiosStatus.MISSING -> NeonPink
            }

            val statusIcon: ImageVector = when (entry.status) {
                BiosStatus.INSTALLED_VERIFIED -> Icons.Default.CheckCircle
                BiosStatus.INSTALLED_SIZE_MISMATCH -> Icons.Default.Warning
                BiosStatus.MISSING -> Icons.Default.Error
            }

            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.info.filename,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Console badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceDark)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = entry.info.consoleId.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonCyan
                        )
                    }
                    if (entry.info.isMandatory) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF381016))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "REQUERIDA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonPink
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = entry.info.description,
                    fontSize = 12.sp,
                    color = TextGray
                )

                Spacer(modifier = Modifier.height(2.dp))

                val statusText = when (entry.status) {
                    BiosStatus.INSTALLED_VERIFIED -> "Instalada y Verificada (${entry.currentSizeBytes / 1024} KB)"
                    BiosStatus.INSTALLED_SIZE_MISMATCH -> "Tamaño alterado: ${entry.currentSizeBytes} bytes (Esperado: ${entry.info.expectedSizeBytes} bytes)"
                    BiosStatus.MISSING -> "Faltante (Requiere ${entry.info.expectedSizeBytes / 1024} KB)"
                }
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDownloading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = NeonCyan
                    )
                }
            } else {
                if (entry.info.downloadUrl != null && entry.status != BiosStatus.INSTALLED_VERIFIED) {
                    BiosActionButton(
                        icon = Icons.Default.CloudDownload,
                        label = "1-Clic",
                        highlight = true,
                        onClick = onDownloadClick
                    )
                }

                BiosActionButton(
                    icon = Icons.Default.FileOpen,
                    label = "Importar",
                    onClick = onImportClick
                )
            }
        }
    }
}

@Composable
private fun BiosActionButton(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        label = "biosBtnScale"
    )

    val bgColor = when {
        isFocused -> FocusYellow
        highlight -> NeonCyan.copy(alpha = 0.25f)
        else -> SurfaceDark
    }

    val contentColor = when {
        isFocused -> BackgroundDark
        highlight -> NeonCyan
        else -> TextWhite
    }

    val borderColor = when {
        isFocused -> FocusBorderColor
        highlight -> NeonCyan.copy(alpha = 0.6f)
        else -> SurfaceDarkVariant
    }

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
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
