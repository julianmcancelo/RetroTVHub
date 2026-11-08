package com.retro.tvbox.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.retro.tvbox.core.LibretroBridge
import com.retro.tvbox.input.model.GamepadButton
import com.retro.tvbox.input.model.GamepadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * High-performance input routing engine managing physical controllers (Bluetooth/USB)
 * and virtual phone controllers for up to 4 simultaneous players (P1 to P4).
 *
 * Dispatches real-time inputs with minimal latency and zero garbage collection in hot loops.
 */
class InputRouter private constructor() : InputManager.InputDeviceListener {

    companion object {
        private const val TAG = "InputRouter"
        const val MAX_PLAYERS = 4

        @Volatile
        private var instance: InputRouter? = null

        fun getInstance(): InputRouter {
            return instance ?: synchronized(this) {
                instance ?: InputRouter().also { instance = it }
            }
        }
    }

    /**
     * Listener interface for real-time input dispatching directly to Libretro or emulator core.
     */
    fun interface InputEventListener {
        fun onInputUpdate(playerIndex: Int, state: GamepadState, bitmask: Int)
    }

    // Controller Slot Type
    enum class ControllerType {
        NONE,
        PHYSICAL_GAMEPAD,
        VIRTUAL_PHONE,
        KEYBOARD_REMOTE
    }

    data class PlayerSlotInfo(
        val playerIndex: Int,
        var type: ControllerType = ControllerType.NONE,
        var identifier: String = "", // deviceId string or phone sessionId
        var displayName: String = "Desconectado"
    )

    // Player state buffers (Index 0 = P1, 1 = P2, 2 = P3, 3 = P4)
    private val playerStates = Array(MAX_PLAYERS) { GamepadState() }
    private val playerBitmasks = IntArray(MAX_PLAYERS) { 0 }

    // Slot assignments
    private val slotInfos = Array(MAX_PLAYERS) { i -> PlayerSlotInfo(playerIndex = i) }

    // Physical device mappings: Android deviceId -> playerIndex (0..3)
    private val physicalDeviceMap = ConcurrentHashMap<Int, Int>()

    // Virtual phone mappings: Session ID -> playerIndex (0..3)
    private val virtualPhoneMap = ConcurrentHashMap<String, Int>()

    // Observers & Dispatchers
    private val listeners = CopyOnWriteArrayList<InputEventListener>()
    private val _slotsStateFlow = MutableStateFlow<List<PlayerSlotInfo>>(slotInfos.toList())
    val slotsStateFlow: StateFlow<List<PlayerSlotInfo>> = _slotsStateFlow.asStateFlow()

    private var inputManager: InputManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Initializes hardware listener on Android InputManager.
     */
    fun initialize(context: Context) {
        if (inputManager == null) {
            val im = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
            im?.registerInputDeviceListener(this, mainHandler)
            inputManager = im
            refreshPhysicalDevices()
            Log.i(TAG, "InputRouter initialized successfully")
        }
    }

    /**
     * Adds a listener for real-time gamepad updates.
     */
    fun addInputListener(listener: InputEventListener) {
        listeners.add(listener)
    }

    /**
     * Removes a registered listener.
     */
    fun removeInputListener(listener: InputEventListener) {
        listeners.remove(listener)
    }

    private var activeBridgeListener: InputEventListener? = null

    /**
     * Binds input routing directly to LibretroBridge instance for in-game execution.
     */
    fun bindToLibretroBridge(bridge: LibretroBridge) {
        unbindLibretroBridge()
        val listener = InputEventListener { playerIndex, _, bitmask ->
            for (btnId in 0..15) {
                val isPressed = (bitmask and (1 shl btnId)) != 0
                bridge.sendButtonInput(playerIndex, btnId, isPressed)
            }
        }
        activeBridgeListener = listener
        addInputListener(listener)
    }

    /**
     * Unbinds the active Libretro bridge listener.
     */
    fun unbindLibretroBridge() {
        activeBridgeListener?.let { removeInputListener(it) }
        activeBridgeListener = null
    }

    // =========================================================================
    // PHYSICAL CONTROLLER MANAGEMENT (KeyEvent & MotionEvent)
    // =========================================================================

    /**
     * Routes hardware KeyEvents from Activity or View hierarchy.
     * @return true if consumed.
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        // Ignore repetitive auto-repeat keys if unwanted, but allow key down/up
        val deviceId = event.deviceId
        val isDown = event.action == KeyEvent.ACTION_DOWN

        // Map keycode to standard GamepadButton
        val button = mapKeyCodeToGamepadButton(event.keyCode) ?: return false

        val playerIndex = getOrAssignPhysicalPlayer(deviceId, event.device?.name ?: "Mando Físico")
        if (playerIndex !in 0 until MAX_PLAYERS) return false

        val state = playerStates[playerIndex]
        val changed = state.setButton(button, isDown)

        if (changed) {
            dispatchUpdate(playerIndex)
        }
        return true
    }

    /**
     * Routes analog sticks and triggers from MotionEvents.
     * @return true if consumed.
     */
    fun handleMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_CLASS_JOYSTICK) == 0 &&
            (event.source and InputDevice.SOURCE_GAMEPAD) == 0
        ) {
            return false
        }

        val deviceId = event.deviceId
        val playerIndex = getOrAssignPhysicalPlayer(deviceId, event.device?.name ?: "Mando Físico")
        if (playerIndex !in 0 until MAX_PLAYERS) return false

        val state = playerStates[playerIndex]
        var stateChanged = false

        // 1. Left Analog Stick
        val lx = event.getAxisValue(MotionEvent.AXIS_X)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y)
        if (abs(lx - state.leftStickX) > 0.05f || abs(ly - state.leftStickY) > 0.05f) {
            state.leftStickX = applyDeadzone(lx)
            state.leftStickY = applyDeadzone(ly)
            stateChanged = true
        }

        // 2. Right Analog Stick (Z and RZ)
        val rx = event.getAxisValue(MotionEvent.AXIS_Z)
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
        if (abs(rx - state.rightStickX) > 0.05f || abs(ry - state.rightStickY) > 0.05f) {
            state.rightStickX = applyDeadzone(rx)
            state.rightStickY = applyDeadzone(ry)
            stateChanged = true
        }

        // 3. D-Pad Hat Axes (AXIS_HAT_X, AXIS_HAT_Y)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val newDpadLeft = hatX < -0.5f
        val newDpadRight = hatX > 0.5f
        val newDpadUp = hatY < -0.5f
        val newDpadDown = hatY > 0.5f

        if (state.dpadLeft != newDpadLeft || state.dpadRight != newDpadRight ||
            state.dpadUp != newDpadUp || state.dpadDown != newDpadDown
        ) {
            state.dpadLeft = newDpadLeft
            state.dpadRight = newDpadRight
            state.dpadUp = newDpadUp
            state.dpadDown = newDpadDown
            stateChanged = true
        }

        // 4. Analog Triggers L2 (AXIS_BRAKE / AXIS_LTRIGGER) and R2 (AXIS_GAS / AXIS_RTRIGGER)
        val lTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        val rTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )

        val lPressed = lTrigger >= GamepadState.TRIGGER_THRESHOLD
        val rPressed = rTrigger >= GamepadState.TRIGGER_THRESHOLD

        if (state.leftTrigger != lTrigger || state.l2 != lPressed) {
            state.leftTrigger = lTrigger
            state.l2 = lPressed
            stateChanged = true
        }

        if (state.rightTrigger != rTrigger || state.r2 != rPressed) {
            state.rightTrigger = rTrigger
            state.r2 = rPressed
            stateChanged = true
        }

        if (stateChanged) {
            dispatchUpdate(playerIndex)
        }

        return true
    }

    private fun applyDeadzone(value: Float): Float {
        return if (abs(value) < GamepadState.ANALOG_DEADZONE) 0f else value
    }

    private fun mapKeyCodeToGamepadButton(keyCode: Int): GamepadButton? {
        return when (keyCode) {
            // Action buttons
            KeyEvent.KEYCODE_BUTTON_A -> GamepadButton.BUTTON_A
            KeyEvent.KEYCODE_BUTTON_B -> GamepadButton.BUTTON_B
            KeyEvent.KEYCODE_BUTTON_X -> GamepadButton.BUTTON_X
            KeyEvent.KEYCODE_BUTTON_Y -> GamepadButton.BUTTON_Y

            // Shoulders & Triggers
            KeyEvent.KEYCODE_BUTTON_L1 -> GamepadButton.BUTTON_L1
            KeyEvent.KEYCODE_BUTTON_R1 -> GamepadButton.BUTTON_R1
            KeyEvent.KEYCODE_BUTTON_L2 -> GamepadButton.BUTTON_L2
            KeyEvent.KEYCODE_BUTTON_R2 -> GamepadButton.BUTTON_R2
            KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadButton.BUTTON_L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadButton.BUTTON_R3

            // D-Pad
            KeyEvent.KEYCODE_DPAD_UP -> GamepadButton.DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> GamepadButton.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> GamepadButton.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadButton.DPAD_RIGHT

            // Meta
            KeyEvent.KEYCODE_BUTTON_START -> GamepadButton.BUTTON_START
            KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadButton.BUTTON_SELECT
            KeyEvent.KEYCODE_BUTTON_MODE -> GamepadButton.BUTTON_HOME

            // TV Remote fallback mapping
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> GamepadButton.BUTTON_A
            KeyEvent.KEYCODE_MENU -> GamepadButton.BUTTON_START
            KeyEvent.KEYCODE_BACK -> GamepadButton.BUTTON_SELECT

            // Keyboard testing keys (WASD, J/K/U/I, Space, Enter)
            KeyEvent.KEYCODE_W -> GamepadButton.DPAD_UP
            KeyEvent.KEYCODE_S -> GamepadButton.DPAD_DOWN
            KeyEvent.KEYCODE_A -> GamepadButton.DPAD_LEFT
            KeyEvent.KEYCODE_D -> GamepadButton.DPAD_RIGHT
            KeyEvent.KEYCODE_K -> GamepadButton.BUTTON_A
            KeyEvent.KEYCODE_J -> GamepadButton.BUTTON_B
            KeyEvent.KEYCODE_I -> GamepadButton.BUTTON_X
            KeyEvent.KEYCODE_U -> GamepadButton.BUTTON_Y
            KeyEvent.KEYCODE_SPACE -> GamepadButton.BUTTON_START

            else -> null
        }
    }

    private fun getOrAssignPhysicalPlayer(deviceId: Int, deviceName: String): Int {
        // Already assigned?
        physicalDeviceMap[deviceId]?.let { return it }

        // Find first empty slot
        for (i in 0 until MAX_PLAYERS) {
            if (slotInfos[i].type == ControllerType.NONE) {
                physicalDeviceMap[deviceId] = i
                slotInfos[i].type = ControllerType.PHYSICAL_GAMEPAD
                slotInfos[i].identifier = deviceId.toString()
                slotInfos[i].displayName = deviceName
                _slotsStateFlow.value = slotInfos.toList()
                Log.i(TAG, "Assigned physical device '$deviceName' (ID: $deviceId) to Player ${i + 1}")
                return i
            }
        }

        // Default to P1 if all slots full or shared
        return 0
    }

    // =========================================================================
    // VIRTUAL PHONE CONTROLLER MANAGEMENT (WebSockets)
    // =========================================================================

    /**
     * Registers a new virtual phone connection and binds it to an available player slot.
     * @param sessionId Unique identifier for the WebSocket session.
     * @param preferredSlot Optional preferred 0-based slot index (0..3).
     * @return Assigned player index (0..3), or -1 if no slots available.
     */
    @Synchronized
    fun registerVirtualPlayer(sessionId: String, preferredSlot: Int = -1): Int {
        // If already registered, return existing slot
        virtualPhoneMap[sessionId]?.let { return it }

        // Check preferred slot first
        if (preferredSlot in 0 until MAX_PLAYERS && slotInfos[preferredSlot].type == ControllerType.NONE) {
            assignVirtualSlot(sessionId, preferredSlot)
            return preferredSlot
        }

        // Find first available slot
        for (i in 0 until MAX_PLAYERS) {
            if (slotInfos[i].type == ControllerType.NONE) {
                assignVirtualSlot(sessionId, i)
                return i
            }
        }

        Log.w(TAG, "No free player slots available for phone session: $sessionId")
        return -1
    }

    private fun assignVirtualSlot(sessionId: String, slotIndex: Int) {
        virtualPhoneMap[sessionId] = slotIndex
        slotInfos[slotIndex].type = ControllerType.VIRTUAL_PHONE
        slotInfos[slotIndex].identifier = sessionId
        slotInfos[slotIndex].displayName = "Celular (P${slotIndex + 1})"
        playerStates[slotIndex].reset()
        dispatchUpdate(slotIndex)
        _slotsStateFlow.value = slotInfos.toList()
        Log.i(TAG, "Virtual phone registered: Session $sessionId assigned to Player ${slotIndex + 1}")
    }

    /**
     * Unregisters a disconnected virtual phone and resets its player slot.
     */
    @Synchronized
    fun unregisterVirtualPlayer(sessionId: String) {
        val slotIndex = virtualPhoneMap.remove(sessionId) ?: return
        slotInfos[slotIndex].type = ControllerType.NONE
        slotInfos[slotIndex].identifier = ""
        slotInfos[slotIndex].displayName = "Desconectado"
        playerStates[slotIndex].reset()
        dispatchUpdate(slotIndex)
        _slotsStateFlow.value = slotInfos.toList()
        Log.i(TAG, "Virtual phone disconnected: Player ${slotIndex + 1} freed")
    }

    /**
     * Routes a button press/release event received from a virtual phone.
     */
    fun handleVirtualButton(sessionId: String, buttonName: String, isDown: Boolean) {
        val playerIndex = virtualPhoneMap[sessionId] ?: return
        val button = GamepadButton.fromString(buttonName) ?: run {
            Log.w(TAG, "Unknown button received from phone: $buttonName")
            return
        }

        val state = playerStates[playerIndex]
        if (state.setButton(button, isDown)) {
            dispatchUpdate(playerIndex)
        }
    }

    /**
     * Routes virtual analog stick inputs from a phone controller.
     */
    fun handleVirtualAxis(sessionId: String, stick: String, x: Float, y: Float) {
        val playerIndex = virtualPhoneMap[sessionId] ?: return
        val state = playerStates[playerIndex]
        var changed = false

        if (stick.equals("left", ignoreCase = true)) {
            state.leftStickX = x.coerceIn(-1.0f, 1.0f)
            state.leftStickY = y.coerceIn(-1.0f, 1.0f)
            changed = true
        } else if (stick.equals("right", ignoreCase = true)) {
            state.rightStickX = x.coerceIn(-1.0f, 1.0f)
            state.rightStickY = y.coerceIn(-1.0f, 1.0f)
            changed = true
        }

        if (changed) {
            dispatchUpdate(playerIndex)
        }
    }

    /**
     * Directly sends a button state for a specific player (used by on-screen touch overlay).
     */
    fun sendButton(playerIndex: Int, button: GamepadButton, isDown: Boolean) {
        if (playerIndex in 0 until MAX_PLAYERS) {
            val state = playerStates[playerIndex]
            if (state.setButton(button, isDown)) {
                dispatchUpdate(playerIndex)
            }
        }
    }

    fun sendButton(playerIndex: Int, buttonName: String, isDown: Boolean) {
        val button = GamepadButton.fromString(buttonName) ?: return
        sendButton(playerIndex, button, isDown)
    }

    // =========================================================================
    // LIBRETRO AND EMULATION ENGINE DISPATCH
    // =========================================================================

    /**
     * Updates and dispatches the computed bitmask to all registered listeners.
     */
    private fun dispatchUpdate(playerIndex: Int) {
        val state = playerStates[playerIndex]
        val mask = state.toBitmask(mapAnalogToDpad = true)
        playerBitmasks[playerIndex] = mask

        for (i in 0 until listeners.size) {
            try {
                listeners[i].onInputUpdate(playerIndex, state, mask)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying input listener", e)
            }
        }
    }

    /**
     * Ultra-fast zero-allocation polling method for Libretro's retro_input_state_t callback.
     * @param playerIndex 0..3 (P1..P4)
     * @return 16-bit Libretro Joypad bitmask.
     */
    fun getLibretroBitmask(playerIndex: Int): Int {
        if (playerIndex !in 0 until MAX_PLAYERS) return 0
        return playerBitmasks[playerIndex]
    }

    /**
     * Analog axis query for Libretro analog device queries.
     * Returns standard Libretro range: -32768 to 32767.
     */
    fun getLibretroAnalog(playerIndex: Int, index: Int, id: Int): Short {
        if (playerIndex !in 0 until MAX_PLAYERS) return 0
        val state = playerStates[playerIndex]
        val value = when (index) {
            0 -> if (id == 0) state.leftStickX else state.leftStickY // Left stick X or Y
            1 -> if (id == 0) state.rightStickX else state.rightStickY // Right stick X or Y
            else -> 0f
        }
        return (value * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    }

    /**
     * Access to snapshot of a player's GamepadState.
     */
    fun getPlayerState(playerIndex: Int): GamepadState {
        return playerStates[playerIndex.coerceIn(0, MAX_PLAYERS - 1)]
    }

    /**
     * Resets input states and slot assignments.
     */
    fun resetAll() {
        for (i in 0 until MAX_PLAYERS) {
            playerStates[i].reset()
            playerBitmasks[i] = 0
            slotInfos[i].type = ControllerType.NONE
            slotInfos[i].identifier = ""
            slotInfos[i].displayName = "Desconectado"
            dispatchUpdate(i)
        }
        physicalDeviceMap.clear()
        virtualPhoneMap.clear()
        _slotsStateFlow.value = slotInfos.toList()
    }

    // =========================================================================
    // ANDROID INPUT DEVICE LISTENER
    // =========================================================================

    private fun refreshPhysicalDevices() {
        val ids = InputDevice.getDeviceIds()
        for (id in ids) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            ) {
                getOrAssignPhysicalPlayer(id, device.name)
            }
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId) ?: return
        val sources = device.sources
        if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        ) {
            getOrAssignPhysicalPlayer(deviceId, device.name)
        }
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        val slotIndex = physicalDeviceMap.remove(deviceId) ?: return
        slotInfos[slotIndex].type = ControllerType.NONE
        slotInfos[slotIndex].identifier = ""
        slotInfos[slotIndex].displayName = "Desconectado"
        playerStates[slotIndex].reset()
        dispatchUpdate(slotIndex)
        _slotsStateFlow.value = slotInfos.toList()
        Log.i(TAG, "Physical device removed (ID: $deviceId). Player ${slotIndex + 1} freed")
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        // No-op
    }
}
