package com.retro.tvbox.input.model

import java.io.Serializable
import kotlin.math.abs

/**
 * Standard button identifiers for gamepads and virtual controllers.
 * Includes aliases for PlayStation and Sega layouts mapped to standard retro conventions.
 */
enum class GamepadButton {
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    BUTTON_A,       // SNES A, Xbox B, PS Circle, Sega B
    BUTTON_B,       // SNES B, Xbox A, PS Cross, Sega A
    BUTTON_X,       // SNES X, Xbox Y, PS Triangle, Sega Y
    BUTTON_Y,       // SNES Y, Xbox X, PS Square, Sega X
    BUTTON_L1,      // Left Bumper / L / L1
    BUTTON_R1,      // Right Bumper / R / R1
    BUTTON_L2,      // Left Trigger / ZL / L2
    BUTTON_R2,      // Right Trigger / ZR / R2
    BUTTON_L3,      // Left Thumbstick click
    BUTTON_R3,      // Right Thumbstick click
    BUTTON_SELECT,  // Select / Back / Share / Minus
    BUTTON_START,   // Start / Options / Menu / Plus
    BUTTON_HOME;    // Guide / Home button

    companion object {
        /**
         * Resolves string button names from WebSockets or external mappings
         * supporting case-insensitive input and console-specific aliases.
         */
        fun fromString(name: String?): GamepadButton? {
            if (name.isNullOrBlank()) return null
            val clean = name.trim().uppercase()
            return when (clean) {
                "UP", "DPAD_UP", "ARROWUP" -> DPAD_UP
                "DOWN", "DPAD_DOWN", "ARROWDOWN" -> DPAD_DOWN
                "LEFT", "DPAD_LEFT", "ARROWLEFT" -> DPAD_LEFT
                "RIGHT", "DPAD_RIGHT", "ARROWRIGHT" -> DPAD_RIGHT
                
                // Primary Action Buttons
                "A", "BUTTON_A", "EAST", "CIRCLE" -> BUTTON_A
                "B", "BUTTON_B", "SOUTH", "CROSS" -> BUTTON_B
                "X", "BUTTON_X", "NORTH", "TRIANGLE" -> BUTTON_X
                "Y", "BUTTON_Y", "WEST", "SQUARE" -> BUTTON_Y

                // Sega 3/6 button mapping aliases
                "SEGA_A" -> BUTTON_B
                "SEGA_B" -> BUTTON_A
                "SEGA_C" -> BUTTON_R1
                "SEGA_X" -> BUTTON_Y
                "SEGA_Y" -> BUTTON_X
                "SEGA_Z" -> BUTTON_L1

                // Shoulders & Triggers
                "L", "L1", "LB", "BUTTON_L1", "SHOULDER_LEFT" -> BUTTON_L1
                "R", "R1", "RB", "BUTTON_R1", "SHOULDER_RIGHT" -> BUTTON_R1
                "L2", "LT", "ZL", "BUTTON_L2", "TRIGGER_LEFT" -> BUTTON_L2
                "R2", "RT", "ZR", "BUTTON_R2", "TRIGGER_RIGHT" -> BUTTON_R2
                "L3", "THUMBL", "BUTTON_L3" -> BUTTON_L3
                "R3", "THUMBR", "BUTTON_R3" -> BUTTON_R3

                // Menu Buttons
                "SELECT", "BACK", "COIN", "MINUS", "BUTTON_SELECT", "SHARE" -> BUTTON_SELECT
                "START", "ENTER", "OPTIONS", "MENU", "PLUS", "BUTTON_START" -> BUTTON_START
                "HOME", "GUIDE", "MODE", "BUTTON_HOME" -> BUTTON_HOME

                else -> runCatching { valueOf(clean) }.getOrNull()
            }
        }
    }
}

/**
 * Represents the state of a single gamepad controller.
 * Thread-safe and designed for zero-allocation updates during hot emulation loops.
 * 
 * Provides direct compatibility with the Libretro RETRO_DEVICE_ID_JOYPAD specification.
 */
data class GamepadState(
    var dpadUp: Boolean = false,
    var dpadDown: Boolean = false,
    var dpadLeft: Boolean = false,
    var dpadRight: Boolean = false,
    var a: Boolean = false,
    var b: Boolean = false,
    var x: Boolean = false,
    var y: Boolean = false,
    var l1: Boolean = false,
    var r1: Boolean = false,
    var l2: Boolean = false,
    var r2: Boolean = false,
    var l3: Boolean = false,
    var r3: Boolean = false,
    var select: Boolean = false,
    var start: Boolean = false,
    var home: Boolean = false,

    // Analog axes normalized between -1.0f and 1.0f
    var leftStickX: Float = 0f,
    var leftStickY: Float = 0f,
    var rightStickX: Float = 0f,
    var rightStickY: Float = 0f,

    // Analog triggers normalized between 0.0f and 1.0f
    var leftTrigger: Float = 0f,
    var rightTrigger: Float = 0f
) : Serializable {

    companion object {
        // Libretro RETRO_DEVICE_ID_JOYPAD constants
        const val RETRO_DEVICE_ID_JOYPAD_B = 0
        const val RETRO_DEVICE_ID_JOYPAD_Y = 1
        const val RETRO_DEVICE_ID_JOYPAD_SELECT = 2
        const val RETRO_DEVICE_ID_JOYPAD_START = 3
        const val RETRO_DEVICE_ID_JOYPAD_UP = 4
        const val RETRO_DEVICE_ID_JOYPAD_DOWN = 5
        const val RETRO_DEVICE_ID_JOYPAD_LEFT = 6
        const val RETRO_DEVICE_ID_JOYPAD_RIGHT = 7
        const val RETRO_DEVICE_ID_JOYPAD_A = 8
        const val RETRO_DEVICE_ID_JOYPAD_X = 9
        const val RETRO_DEVICE_ID_JOYPAD_L = 10
        const val RETRO_DEVICE_ID_JOYPAD_R = 11
        const val RETRO_DEVICE_ID_JOYPAD_L2 = 12
        const val RETRO_DEVICE_ID_JOYPAD_R2 = 13
        const val RETRO_DEVICE_ID_JOYPAD_L3 = 14
        const val RETRO_DEVICE_ID_JOYPAD_R3 = 15

        // Deadzone threshold for analog sticks and triggers
        const val ANALOG_DEADZONE = 0.20f
        const val TRIGGER_THRESHOLD = 0.35f
    }

    /**
     * Updates an individual button state.
     */
    fun setButton(button: GamepadButton, pressed: Boolean): Boolean {
        val changed = isButtonPressed(button) != pressed
        when (button) {
            GamepadButton.DPAD_UP -> dpadUp = pressed
            GamepadButton.DPAD_DOWN -> dpadDown = pressed
            GamepadButton.DPAD_LEFT -> dpadLeft = pressed
            GamepadButton.DPAD_RIGHT -> dpadRight = pressed
            GamepadButton.BUTTON_A -> a = pressed
            GamepadButton.BUTTON_B -> b = pressed
            GamepadButton.BUTTON_X -> x = pressed
            GamepadButton.BUTTON_Y -> y = pressed
            GamepadButton.BUTTON_L1 -> l1 = pressed
            GamepadButton.BUTTON_R1 -> r1 = pressed
            GamepadButton.BUTTON_L2 -> {
                l2 = pressed
                leftTrigger = if (pressed) 1.0f else 0.0f
            }
            GamepadButton.BUTTON_R2 -> {
                r2 = pressed
                rightTrigger = if (pressed) 1.0f else 0.0f
            }
            GamepadButton.BUTTON_L3 -> l3 = pressed
            GamepadButton.BUTTON_R3 -> r3 = pressed
            GamepadButton.BUTTON_SELECT -> select = pressed
            GamepadButton.BUTTON_START -> start = pressed
            GamepadButton.BUTTON_HOME -> home = pressed
        }
        return changed
    }

    /**
     * Queries whether an individual button is currently pressed.
     */
    fun isButtonPressed(button: GamepadButton): Boolean {
        return when (button) {
            GamepadButton.DPAD_UP -> dpadUp
            GamepadButton.DPAD_DOWN -> dpadDown
            GamepadButton.DPAD_LEFT -> dpadLeft
            GamepadButton.DPAD_RIGHT -> dpadRight
            GamepadButton.BUTTON_A -> a
            GamepadButton.BUTTON_B -> b
            GamepadButton.BUTTON_X -> x
            GamepadButton.BUTTON_Y -> y
            GamepadButton.BUTTON_L1 -> l1
            GamepadButton.BUTTON_R1 -> r1
            GamepadButton.BUTTON_L2 -> l2 || leftTrigger >= TRIGGER_THRESHOLD
            GamepadButton.BUTTON_R2 -> r2 || rightTrigger >= TRIGGER_THRESHOLD
            GamepadButton.BUTTON_L3 -> l3
            GamepadButton.BUTTON_R3 -> r3
            GamepadButton.BUTTON_SELECT -> select
            GamepadButton.BUTTON_START -> start
            GamepadButton.BUTTON_HOME -> home
        }
    }

    /**
     * Converts the current digital and analog button inputs into a Libretro 16-bit mask.
     * Optionally combines analog stick directions into D-pad for games that don't support analog.
     *
     * @param mapAnalogToDpad If true, left analog stick displacement beyond deadzone triggers D-pad.
     * @return 16-bit integer mask compatible with retro_input_state_t.
     */
    fun toBitmask(mapAnalogToDpad: Boolean = true): Int {
        var mask = 0

        // Libretro Joypad standard bit layout:
        // bit 0: B
        // bit 1: Y
        // bit 2: SELECT
        // bit 3: START
        // bit 4: UP
        // bit 5: DOWN
        // bit 6: LEFT
        // bit 7: RIGHT
        // bit 8: A
        // bit 9: X
        // bit 10: L
        // bit 11: R
        // bit 12: L2
        // bit 13: R2
        // bit 14: L3
        // bit 15: R3

        if (b) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_B)
        if (y) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_Y)
        if (select) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_SELECT)
        if (start) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_START)

        // D-pad logic (combining digital switches with optional analog stick deflection)
        val isUp = dpadUp || (mapAnalogToDpad && leftStickY < -ANALOG_DEADZONE)
        val isDown = dpadDown || (mapAnalogToDpad && leftStickY > ANALOG_DEADZONE)
        val isLeft = dpadLeft || (mapAnalogToDpad && leftStickX < -ANALOG_DEADZONE)
        val isRight = dpadRight || (mapAnalogToDpad && leftStickX > ANALOG_DEADZONE)

        if (isUp) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_UP)
        if (isDown) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_DOWN)
        if (isLeft) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_LEFT)
        if (isRight) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_RIGHT)

        if (a) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_A)
        if (x) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_X)
        if (l1) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_L)
        if (r1) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_R)
        if (l2 || leftTrigger >= TRIGGER_THRESHOLD) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_L2)
        if (r2 || rightTrigger >= TRIGGER_THRESHOLD) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_R2)
        if (l3) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_L3)
        if (r3) mask = mask or (1 shl RETRO_DEVICE_ID_JOYPAD_R3)

        return mask
    }

    /**
     * Resets all inputs to neutral/unpressed state without creating new objects.
     */
    fun reset() {
        dpadUp = false
        dpadDown = false
        dpadLeft = false
        dpadRight = false
        a = false
        b = false
        x = false
        y = false
        l1 = false
        r1 = false
        l2 = false
        r2 = false
        l3 = false
        r3 = false
        select = false
        start = false
        home = false
        leftStickX = 0f
        leftStickY = 0f
        rightStickX = 0f
        rightStickY = 0f
        leftTrigger = 0f
        rightTrigger = 0f
    }

    /**
     * Copies values from another GamepadState in place.
     */
    fun copyFrom(other: GamepadState) {
        dpadUp = other.dpadUp
        dpadDown = other.dpadDown
        dpadLeft = other.dpadLeft
        dpadRight = other.dpadRight
        a = other.a
        b = other.b
        x = other.x
        y = other.y
        l1 = other.l1
        r1 = other.r1
        l2 = other.l2
        r2 = other.r2
        l3 = other.l3
        r3 = other.r3
        select = other.select
        start = other.start
        home = other.home
        leftStickX = other.leftStickX
        leftStickY = other.leftStickY
        rightStickX = other.rightStickX
        rightStickY = other.rightStickY
        leftTrigger = other.leftTrigger
        rightTrigger = other.rightTrigger
    }
}
