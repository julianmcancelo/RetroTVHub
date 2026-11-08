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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.retro.tvbox.ui.theme.SurfaceDarkVariant
import com.retro.tvbox.ui.theme.TextGray
import com.retro.tvbox.ui.theme.TextMuted
import com.retro.tvbox.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PS5-styled BIOS Management Dialog for viewing, downloading (1-click) and importing
 * BIOS firmware (PS1 scph1001/scph5501, PS2 scph39001, GBA gba_bios.bin)
 * directly into the retro console system directory via SAF and OkHttp.
 */
@Composable
fun BiosSettingsDialog(
    biosManager: BiosManager,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val closeFocus = remember { FocusRequester() }

    val downloadingBios = remember { mutableStateMapOf<String, Boolean>() }
    var isDownloadingBatch by remember { mutableStateOf(false) }
    var refreshCounter by remember { mutableStateOf(0) }
    var biosEntries by remember { mutableStateOf<List<BiosStatusEntry>>(emptyList()) }

    var pendingImportFilename by remember { mutableStateOf<String?>(null) }

    // Launcher para importación individual SAF
    val singleImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val targetName = pendingImportFilename
        if (uri != null && targetName != null) {
            coroutineScope.launch {
                val res = biosManager.importBiosFromUri(uri, targetName)
                if (res.isSuccess) {
                    Toast.makeText(context, "BIOS $targetName importada exitosamente", Toast.LENGTH_SHORT).show()
                    refreshCounter++
                } else {
                    Toast.makeText(context, "Error importando: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
                pendingImportFilename = null
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
                    Toast.makeText(context, "BIOS detectada e importada: ${info.filename}", Toast.LENGTH_LONG).show()
                    refreshCounter++
                } else {
                    Toast.makeText(context, "Aviso: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(refreshCounter) {
        withContext(Dispatchers.IO) {
            biosEntries = biosManager.getAllBiosStatuses()
        }
    }

    LaunchedEffect(Unit) {
        try {
            closeFocus.requestFocus()
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD9080B14))
                .onKeyEvent { keyEvent ->
                    if (keyEvent.key == Key.Back || keyEvent.key == Key.Escape) {
                        onDismissRequest()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = modifier
                    .width(760.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF101624))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF00E5FF).copy(alpha = 0.6f), Color(0xFFFF0055).copy(alpha = 0.6f))
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
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "AJUSTES DE BIOS Y FIRMWARE",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "PS1 (scph1001/5501) • PS2 (scph39001) • GBA (gba_bios.bin)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8E9BAE)
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

                    Spacer(modifier = Modifier.height(18.dp))

                    // List of BIOSes
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(biosEntries, key = { it.info.filename }) { entry ->
                            val isDownloading = downloadingBios[entry.info.filename] == true

                            BiosItemCard(
                                entry = entry,
                                isDownloading = isDownloading,
                                onDownload = {
                                    coroutineScope.launch {
                                        downloadingBios[entry.info.filename] = true
                                        val res = biosManager.downloadBios(entry.info)
                                        downloadingBios[entry.info.filename] = false
                                        refreshCounter++
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "BIOS ${entry.info.filename} instalada con éxito", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onImport = {
                                    pendingImportFilename = entry.info.filename
                                    singleImportLauncher.launch(arrayOf("*/*"))
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Bottom Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Auto-import SAF
                            ActionPillButton(
                                icon = Icons.Default.FileOpen,
                                label = "Auto-Importar SAF",
                                onClick = {
                                    autoDetectImportLauncher.launch(arrayOf("*/*"))
                                }
                            )

                            // Batch download
                            val missingCount = biosEntries.count { it.status == BiosStatus.MISSING && it.info.downloadUrl != null }
                            ActionPillButton(
                                icon = Icons.Default.CloudDownload,
                                label = if (isDownloadingBatch) "Descargando..." else "Descargar Faltantes ($missingCount)",
                                isHighlighted = missingCount > 0,
                                enabled = missingCount > 0 && !isDownloadingBatch,
                                onClick = {
                                    isDownloadingBatch = true
                                    coroutineScope.launch {
                                        val results = biosManager.downloadAllMissing()
                                        isDownloadingBatch = false
                                        refreshCounter++
                                        val successCount = results.values.count { it.isSuccess }
                                        Toast.makeText(context, "Descargadas $successCount BIOS", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        // Close action button
                        Box(
                            modifier = Modifier
                                .focusRequester(closeFocus)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF00E5FF))
                                .border(1.dp, Color.White, RoundedCornerShape(12.dp))
                                .clickable { onDismissRequest() }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "LISTO (Cerrar)",
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
private fun BiosItemCard(
    entry: BiosStatusEntry,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onImport: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1.0f, label = "BiosCard")

    val (statusColor, statusText) = when (entry.status) {
        BiosStatus.INSTALLED_VERIFIED -> AccentGreen to "Instalada y Verificada (${entry.currentSizeBytes / 1024} KB)"
        BiosStatus.INSTALLED_SIZE_MISMATCH -> FocusYellow to "Tamaño Inconsistente (${entry.currentSizeBytes} B / Esperado: ${entry.info.expectedSizeBytes} B)"
        BiosStatus.MISSING -> NeonPink to "Faltante (Requiere ${entry.info.expectedSizeBytes / 1024} KB)"
    }

    Row(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF151D2C))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.info.filename,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (entry.info.isMandatory) Color(0xFF4A1020) else Color(0xFF132A44))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (entry.info.isMandatory) "REQUERIDA" else "OPCIONAL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = if (entry.info.isMandatory) Color(0xFFFF5252) else Color(0xFF00E5FF)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${entry.info.consoleId.uppercase()} • ${entry.info.description}",
                fontSize = 11.sp,
                color = Color(0xFF8E9BAE)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = statusText,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (entry.status == BiosStatus.INSTALLED_VERIFIED) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF003822))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "OK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                }
            } else if (isDownloading) {
                CircularProgressIndicator(
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
            } else if (entry.info.downloadUrl != null) {
                ItemActionButton(
                    icon = Icons.Default.CloudDownload,
                    label = "Descargar",
                    isHighlighted = true,
                    onClick = onDownload
                )
            }

            // Botón Importar SAF
            ItemActionButton(
                icon = Icons.Default.FileOpen,
                label = "Importar",
                isHighlighted = false,
                onClick = onImport
            )
        }
    }
}

@Composable
private fun ItemActionButton(
    icon: ImageVector,
    label: String,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, label = "ItemBtnScale")

    val bg = when {
        isFocused -> FocusYellow
        isHighlighted -> Color(0xFF00D2FF).copy(alpha = 0.2f)
        else -> Color(0xFF1D2638)
    }

    val contentColor = when {
        isFocused -> BackgroundDark
        isHighlighted -> Color(0xFF00D2FF)
        else -> TextWhite
    }

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                1.dp,
                if (isFocused) FocusBorderColor else if (isHighlighted) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(8.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun ActionPillButton(
    icon: ImageVector,
    label: String,
    isHighlighted: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, label = "ActionPillScale")

    val bg = when {
        isFocused -> FocusYellow
        isHighlighted -> NeonCyan.copy(alpha = 0.25f)
        else -> Color(0xFF151D2C)
    }

    val contentColor = when {
        isFocused -> BackgroundDark
        isHighlighted -> NeonCyan
        else -> TextWhite
    }

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                1.dp,
                if (isFocused) FocusBorderColor else if (isHighlighted) NeonCyan else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(10.dp)
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
