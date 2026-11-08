package com.retro.tvbox.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.tvbox.input.InputRouter
import com.retro.tvbox.input.model.GamepadButton

/**
 * PS4 / PS5-inspired ergonomic transparent touch controls overlay for smartphones and tablets.
 *
 * Features:
 * - Sony-style D-Pad with independent directional wings and tactile tactile shadows.
 * - Iconic PlayStation action buttons (▲ Green, ● Red, ✕ Blue, ■ Pink) with crystal glassmorphism.
 * - Top shoulder triggers (L1/L2 and R1/R2) in screen corners.
 * - Central pill-styled SELECT and START buttons.
 * - Low-latency haptic vibration response on touch down.
 * - Floating top button to open the OSD in-game menu without needing physical hardware buttons.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchControlsOverlay(
    modifier: Modifier = Modifier,
    isPlayStationLayout: Boolean = true,
    inputRouter: InputRouter = remember { InputRouter.getInstance() },
    onOpenMenu: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    val triggerHaptic = {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (_: Exception) {}
    }

    val sendAction = { button: GamepadButton, isDown: Boolean ->
        inputRouter.sendButton(0, button, isDown)
        if (isDown) triggerHaptic()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- 1. TOP TRIGGERS (L1 / L2 on Left, R1 / R2 on Right) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left Shoulders
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TouchTriggerButton(label = "L1", onAction = { sendAction(GamepadButton.BUTTON_L1, it) })
                TouchTriggerButton(label = "L2", onAction = { sendAction(GamepadButton.BUTTON_L2, it) })
            }

            // Top Center Floating Menu Button (if callback provided)
            onOpenMenu?.let { openMenuAction ->
                TouchMenuPillButton(
                    onClick = {
                        triggerHaptic()
                        openMenuAction()
                    }
                )
            }

            // Right Shoulders
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TouchTriggerButton(label = "R2", onAction = { sendAction(GamepadButton.BUTTON_R2, it) })
                TouchTriggerButton(label = "R1", onAction = { sendAction(GamepadButton.BUTTON_R1, it) })
            }
        }

        // --- 2. SONY-STYLE D-PAD (BOTTOM LEFT) ---
        SonyDpad(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 16.dp, start = 8.dp),
            onAction = sendAction
        )

        // --- 3. SYSTEM BUTTONS (SELECT & START PILLS AT BOTTOM CENTER) ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TouchPillButton(
                label = if (isPlayStationLayout) "SHARE" else "SELECT",
                onAction = { sendAction(GamepadButton.BUTTON_SELECT, it) }
            )
            TouchPillButton(
                label = if (isPlayStationLayout) "OPTIONS" else "START",
                onAction = { sendAction(GamepadButton.BUTTON_START, it) }
            )
        }

        // --- 4. ICONIC PLAYSTATION ACTION BUTTONS (BOTTOM RIGHT) ---
        PlayStationActionCluster(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 8.dp),
            isPlayStationLayout = isPlayStationLayout,
            onAction = sendAction
        )
    }
}

/**
 * Sony D-Pad: 4 independent directional wings with ergonomic tactile shadows and center pivot.
 */
@Composable
private fun SonyDpad(
    modifier: Modifier = Modifier,
    onAction: (GamepadButton, Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .size(164.dp),
        contentAlignment = Alignment.Center
    ) {
        // Central pivot background plate
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0x33000000))
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
        )

        // UP Wing
        SonyDpadWing(
            direction = "▲",
            modifier = Modifier.align(Alignment.TopCenter),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            onAction = { onAction(GamepadButton.DPAD_UP, it) }
        )

        // DOWN Wing
        SonyDpadWing(
            direction = "▼",
            modifier = Modifier.align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            onAction = { onAction(GamepadButton.DPAD_DOWN, it) }
        )

        // LEFT Wing
        SonyDpadWing(
            direction = "◀",
            modifier = Modifier.align(Alignment.CenterStart),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
            onAction = { onAction(GamepadButton.DPAD_LEFT, it) }
        )

        // RIGHT Wing
        SonyDpadWing(
            direction = "▶",
            modifier = Modifier.align(Alignment.CenterEnd),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            onAction = { onAction(GamepadButton.DPAD_RIGHT, it) }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SonyDpadWing(
    direction: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    onAction: (isDown: Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val bgBrush = if (isPressed) {
        Brush.radialGradient(listOf(Color(0xFF00E5FF).copy(alpha = 0.45f), Color(0x66000000)))
    } else {
        Brush.radialGradient(listOf(Color(0x551E293B), Color(0x77000000)))
    }

    val borderColor = if (isPressed) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.25f)
    val textColor = if (isPressed) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.85f)

    Box(
        modifier = modifier
            .size(52.dp)
            .shadow(4.dp, shape = shape)
            .clip(shape)
            .background(bgBrush)
            .border(1.5.dp, borderColor, shape)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onAction(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onAction(false)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = direction,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = textColor
        )
    }
}

/**
 * PlayStation Diamond Action Buttons cluster (▲ green, ● red, ✕ blue, ■ pink)
 */
@Composable
private fun PlayStationActionCluster(
    modifier: Modifier = Modifier,
    isPlayStationLayout: Boolean,
    onAction: (GamepadButton, Boolean) -> Unit
) {
    Box(
        modifier = modifier.size(164.dp),
        contentAlignment = Alignment.Center
    ) {
        // Top: ▲ (PlayStation) or X (SNES)
        PlayStationCrystalButton(
            label = if (isPlayStationLayout) "▲" else "X",
            color = if (isPlayStationLayout) Color(0xFF00E676) else Color(0xFF00D2FF),
            modifier = Modifier.align(Alignment.TopCenter),
            onAction = { onAction(GamepadButton.BUTTON_X, it) }
        )

        // Right: ● (PlayStation) or A (SNES)
        PlayStationCrystalButton(
            label = if (isPlayStationLayout) "●" else "A",
            color = if (isPlayStationLayout) Color(0xFFFF3366) else Color(0xFFFF5252),
            modifier = Modifier.align(Alignment.CenterEnd),
            onAction = { onAction(GamepadButton.BUTTON_A, it) }
        )

        // Bottom: ✕ (PlayStation) or B (SNES)
        PlayStationCrystalButton(
            label = if (isPlayStationLayout) "✕" else "B",
            color = if (isPlayStationLayout) Color(0xFF2979FF) else Color(0xFFFFD600),
            modifier = Modifier.align(Alignment.BottomCenter),
            onAction = { onAction(GamepadButton.BUTTON_B, it) }
        )

        // Left: ■ (PlayStation) or Y (SNES)
        PlayStationCrystalButton(
            label = if (isPlayStationLayout) "■" else "Y",
            color = if (isPlayStationLayout) Color(0xFFFF4081) else Color(0xFF00E676),
            modifier = Modifier.align(Alignment.CenterStart),
            onAction = { onAction(GamepadButton.BUTTON_Y, it) }
        )
    }
}

/**
 * Crystal translucent circular button with radial specular highlight and glowing symbol.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlayStationCrystalButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onAction: (isDown: Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        label = "CrystalBtnScale"
    )

    val bgBrush = if (isPressed) {
        Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.80f)))
    } else {
        Brush.radialGradient(listOf(Color(0x40FFFFFF), Color.Black.copy(alpha = 0.65f)))
    }

    val borderColor = if (isPressed) color else color.copy(alpha = 0.65f)

    Box(
        modifier = modifier
            .scale(scale)
            .size(54.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(bgBrush)
            .border(2.dp, borderColor, CircleShape)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onAction(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onAction(false)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

/**
 * Ergonomic shoulder triggers (L1, L2, R1, R2) for top corners.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TouchTriggerButton(
    label: String,
    onAction: (isDown: Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val bg = if (isPressed) Color(0xFF00E5FF).copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.50f)
    val borderColor = if (isPressed) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.30f)

    Box(
        modifier = Modifier
            .width(62.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onAction(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onAction(false)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = if (isPressed) Color(0xFF00E5FF) else Color.White
        )
    }
}

/**
 * Pill-style central buttons for SELECT / SHARE and START / OPTIONS.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TouchPillButton(
    label: String,
    onAction: (isDown: Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val bg = if (isPressed) Color(0xFF00D2FF).copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.55f)
    val borderColor = if (isPressed) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.2.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onAction(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onAction(false)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = if (isPressed) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.9f),
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Floating Top Menu OSD button for mobile players.
 */
@Composable
private fun TouchMenuPillButton(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.60f))
            .border(1.2.dp, Color(0xFFFF0055).copy(alpha = 0.80f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Menú OSD",
            tint = Color(0xFFFF0055),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "MENU OSD",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
    }
}
