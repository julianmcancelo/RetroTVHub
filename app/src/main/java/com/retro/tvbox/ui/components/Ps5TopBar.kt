package com.retro.tvbox.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.BatteryManager
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PS5-styled cinematic top bar featuring real-time clock, battery status,
 * Wi-Fi indicator, retro gamer avatar, and quick access action pills.
 *
 * Supports both D-pad remote navigation on Android TV and direct touch taps on smartphones.
 */
@Composable
fun Ps5TopBar(
    modifier: Modifier = Modifier,
    onOpenBios: () -> Unit,
    onOpenMemCards: () -> Unit,
    onConnectPhone: () -> Unit,
    onOpenCache: () -> Unit = {},
    cacheStatusText: String? = null
) {
    val currentTime = rememberCurrentTime()
    val batteryState = rememberBatteryState()
    val isWifiConnected = rememberWifiConnected()

    // Glassmorphic top bar container
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x660B0F1A))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- LEFT BRANDING: PS5 / RETRO TV LOGO & STATUS ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Glowing PlayStation-style console emblem
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF00D2FF), Color(0xFF7928CA), Color(0xFFFF0080))
                        )
                    )
                    .padding(1.5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0xFF0A0D16)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = "RetroTV Hub",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "RETRO",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "TV HUB",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E5FF),
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = cacheStatusText ?: "PlayStation Core • Ready",
                    fontSize = 11.sp,
                    color = Color(0xFF8E9BAE),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // --- CENTER / RIGHT: QUICK ACCESS ACTION PILLS ---
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick Access 1: Ajustes de BIOS
            Ps5ActionPill(
                icon = Icons.Default.Tune,
                label = "Ajustes de BIOS",
                onClick = onOpenBios
            )

            // Quick Access 2: Memory Cards
            Ps5ActionPill(
                icon = Icons.Default.Memory,
                label = "Memory Cards",
                onClick = onOpenMemCards
            )

            // Quick Access 3: Conectar Celular como Mando
            Ps5ActionPill(
                icon = Icons.Default.QrCode,
                label = "Conectar Celular",
                isHighlighted = true,
                onClick = onConnectPhone
            )

            // Quick Access 4: Almacenamiento Caché Play-on-Demand
            Ps5ActionPill(
                icon = Icons.Default.Storage,
                label = "Caché",
                onClick = onOpenCache
            )

            Spacer(modifier = Modifier.width(6.dp))

            // --- SYSTEM METRICS (Wi-Fi, Battery, Clock) ---
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF131926))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Wi-Fi Icon with status dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = "Wi-Fi",
                        tint = if (isWifiConnected) Color(0xFF00E676) else Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Battery Icon + Level
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val batteryIcon = when {
                        batteryState.isCharging -> Icons.Default.BatteryChargingFull
                        batteryState.level <= 15 -> Icons.Default.BatteryAlert
                        batteryState.level >= 80 -> Icons.Default.BatteryFull
                        else -> Icons.Default.BatteryStd
                    }
                    val batteryColor = when {
                        batteryState.isCharging -> Color(0xFF00E5FF)
                        batteryState.level <= 20 -> Color(0xFFFF5252)
                        else -> Color(0xFFE2E8F0)
                    }

                    Icon(
                        imageVector = batteryIcon,
                        contentDescription = "Batería",
                        tint = batteryColor,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = "${batteryState.level}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = batteryColor
                    )
                }

                // Digital Real-time Clock (e.g. "15:30")
                Text(
                    text = currentTime,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }

            // --- RETRO GAMER AVATAR ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF131926))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(Color(0xFF00E5FF), Color(0xFFFF007F), Color(0xFF7928CA), Color(0xFF00E5FF))
                            )
                        )
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(Color(0xFF0C101D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }

                Column {
                    Text(
                        text = "PLAYER 1",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ONLINE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }
                }
            }
        }
    }
}

/**
 * PS5 Interactive Quick Action Pill with focus elevation and neon glow.
 */
@Composable
private fun Ps5ActionPill(
    icon: ImageVector,
    label: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        label = "Ps5PillScale"
    )

    val backgroundColor = when {
        isFocused -> if (isHighlighted) Color(0xFFFF0055) else Color(0xFF00D2FF)
        isHighlighted -> Color(0xFF261026)
        else -> Color(0xFF131926)
    }

    val contentColor = when {
        isFocused -> Color(0xFF050811)
        isHighlighted -> Color(0xFFFF4081)
        else -> Color(0xFFE2E8F0)
    }

    val borderColor = when {
        isFocused -> Color.White
        isHighlighted -> Color(0xFFFF0055).copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.10f)
    }

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

/**
 * State helper: Real-time clock formatted (e.g. "15:30")
 */
@Composable
private fun rememberCurrentTime(): String {
    var timeStr by remember { mutableStateOf(getFormattedTime()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            timeStr = getFormattedTime()
            delay(1000L)
        }
    }
    return timeStr
}

private fun getFormattedTime(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date())
}

/**
 * State helper: Android battery level & charging state
 */
private data class BatteryState(val level: Int, val isCharging: Boolean)

@Composable
private fun rememberBatteryState(): BatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BatteryState(level = 100, isCharging = false)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.let {
                    val rawLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    val pct = if (rawLevel >= 0 && scale > 0) {
                        (rawLevel * 100 / scale.toFloat()).toInt().coerceIn(0, 100)
                    } else 100
                    state = BatteryState(pct, isCharging)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = context.registerReceiver(receiver, filter)
        initialIntent?.let {
            val rawLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (rawLevel >= 0 && scale > 0) {
                (rawLevel * 100 / scale.toFloat()).toInt().coerceIn(0, 100)
            } else 100
            state = BatteryState(pct, isCharging)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }
    return state
}

/**
 * State helper: Android network Wi-Fi / connection listener
 */
@Composable
private fun rememberWifiConnected(): Boolean {
    val context = LocalContext.current
    var isConnected by remember { mutableStateOf(true) }

    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isConnected = true
            }

            override fun onLost(network: Network) {
                isConnected = false
            }
        }
        val request = NetworkRequest.Builder().build()
        try {
            cm?.registerNetworkCallback(request, callback)
            @Suppress("DEPRECATION")
            val active = cm?.activeNetworkInfo
            @Suppress("DEPRECATION")
            isConnected = active?.isConnectedOrConnecting == true
        } catch (_: Exception) {
            isConnected = true
        }
        onDispose {
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }
    return isConnected
}
