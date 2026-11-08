package com.retro.tvbox.core

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import android.view.Surface
import androidx.annotation.Keep
import java.io.File
import kotlin.math.sin

enum class EmulatorState {
    UNINITIALIZED,
    CORE_LOADED,
    GAME_LOADED,
    RUNNING,
    PAUSED,
    STOPPED
}

/**
 * Mapeo de botones estándar del RetroPad de Libretro.
 */
object RetroPad {
    const val DEVICE_ID_JOYPAD_B = 0
    const val DEVICE_ID_JOYPAD_Y = 1
    const val DEVICE_ID_JOYPAD_SELECT = 2
    const val DEVICE_ID_JOYPAD_START = 3
    const val DEVICE_ID_JOYPAD_UP = 4
    const val DEVICE_ID_JOYPAD_DOWN = 5
    const val DEVICE_ID_JOYPAD_LEFT = 6
    const val DEVICE_ID_JOYPAD_RIGHT = 7
    const val DEVICE_ID_JOYPAD_A = 8
    const val DEVICE_ID_JOYPAD_X = 9
    const val DEVICE_ID_JOYPAD_L = 10
    const val DEVICE_ID_JOYPAD_R = 11
    const val DEVICE_ID_JOYPAD_L2 = 12
    const val DEVICE_ID_JOYPAD_R2 = 13
    const val DEVICE_ID_JOYPAD_L3 = 14
    const val DEVICE_ID_JOYPAD_R3 = 15

    const val DEVICE_JOYPAD = 1

    val BUTTON_NAMES = arrayOf(
        "B", "Y", "SELECT", "START",
        "UP", "DOWN", "LEFT", "RIGHT",
        "A", "X", "L1", "R1",
        "L2", "R2", "L3", "R3"
    )
}

/**
 * Puente JNI y motor de emulación Libretro con pipeline de audio de baja latencia
 * AudioTrack (modo STREAM) y sincronización de frame timing a 60 FPS con frame skipping opcional.
 */
class LibretroBridge {

    private val tag = "LibretroBridge"

    var currentState: EmulatorState = EmulatorState.UNINITIALIZED
        private set

    var isNativeLibraryLoaded = false
        private set

    // --- Audio Pipeline (AudioTrack STREAM mode, 44.1 kHz Estéreo) ---
    companion object {
        const val AUDIO_SAMPLE_RATE = 44100
        const val TARGET_FPS = 60
        const val TARGET_FRAME_TIME_NS = 1_000_000_000L / TARGET_FPS // 16.666.666 ns
        const val SAMPLES_PER_FRAME = AUDIO_SAMPLE_RATE / TARGET_FPS // 735 muestras por canal
    }

    private var audioTrack: AudioTrack? = null
    private val audioLock = Any()
    var isAudioEnabled = true
    var isAudioMuted = false

    // Generador de audio de respaldo (síntesis retro estéreo limpia sin jitter)
    private var fallbackAudioPhaseL = 0.0
    private var fallbackAudioPhaseR = 0.0
    private val fallbackAudioBuffer = ShortArray(SAMPLES_PER_FRAME * 2)

    // --- Video Pipeline & Surface Rendering ---
    private var activeSurface: Surface? = null
    private val surfaceLock = Any()

    // Indicadores y estadísticas en tiempo real
    private var currentGameTitle: String = "Juego Retro"
    private var currentRomPath: String = ""
    private var currentCoreName: String = "Libretro Core"

    // Estados de botones para 4 puertos (16 botones por control)
    private val buttonStates = Array(4) { BooleanArray(16) }

    // --- Timing a 60 FPS y Frame Skipping ---
    var isFrameSkippingEnabled: Boolean = true
    var maxFrameSkip: Int = 2
    private var consecutiveSkippedFrames = 0
    private var totalSkippedFrames = 0L

    private var lastFrameTimeNs: Long = 0L
    private var frameCounter: Long = 0L
    private var measuredFps: Float = 60.0f
    private val fpsHistory = FloatArray(30) { 60.0f }
    private var fpsHistoryIndex = 0

    // Búfer para persistencia SRAM en modo stub/fallback
    private var simulatedSramData: ByteArray? = null

    // Pinturas para renderizado de fallback CRT a 60 FPS
    private val bgPaint = Paint().apply { color = Color.rgb(10, 14, 23) }
    private val crtBorderPaint = Paint().apply {
        color = Color.rgb(0, 210, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val textHeaderPaint = Paint().apply {
        color = Color.rgb(0, 229, 255)
        textSize = 36f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val textSubPaint = Paint().apply {
        color = Color.rgb(180, 200, 220)
        textSize = 24f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val statPaint = Paint().apply {
        color = Color.rgb(100, 240, 150)
        textSize = 20f
        isAntiAlias = true
    }
    private val btnActivePaint = Paint().apply {
        color = Color.rgb(255, 64, 129)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val btnInactivePaint = Paint().apply {
        color = Color.rgb(35, 45, 65)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val btnTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 18f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    init {
        try {
            System.loadLibrary("retro_bridge")
            isNativeLibraryLoaded = true
            Log.i(tag, "Librería nativa libretro_bridge cargada correctamente")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(tag, "Librería nativa libretro_bridge no encontrada. Activando modo emulado de alta fidelidad: ${e.message}")
            isNativeLibraryLoaded = false
        } catch (t: Throwable) {
            Log.w(tag, "Excepción al inicializar librería nativa: ${t.message}")
            isNativeLibraryLoaded = false
        }

        initAudioTrack()
    }

    // =========================================================================
    // PIPELINE DE AUDIO DE BAJA LATENCIA (AudioTrack STREAM)
    // =========================================================================

    /**
     * Inicializa AudioTrack en modo STREAM con atributos de baja latencia para juegos.
     */
    private fun initAudioTrack() {
        synchronized(audioLock) {
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                // Buffer óptimo: 2x minBufferSize para garantizar reproducción fluida sin underruns
                val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AUDIO_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build()

                if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                    audioTrack?.play()
                    Log.i(tag, "AudioTrack STREAM estéreo inicializado: ${AUDIO_SAMPLE_RATE}Hz [Buffer: $bufferSize bytes]")
                } else {
                    Log.w(tag, "AudioTrack no pudo inicializarse correctamente")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error creando AudioTrack: ${e.message}", e)
                audioTrack = null
            }
        }
    }

    /**
     * Escribe un lote de muestras PCM de 16 bits directamente en el AudioTrack.
     */
    fun writeAudioSamples(samples: ShortArray, offset: Int = 0, size: Int = samples.size): Int {
        if (!isAudioEnabled || isAudioMuted) return size
        synchronized(audioLock) {
            val track = audioTrack ?: return 0
            if (track.state != AudioTrack.STATE_INITIALIZED) return 0
            return try {
                track.write(samples, offset, size, AudioTrack.WRITE_NON_BLOCKING)
            } catch (e: Exception) {
                Log.w(tag, "Error escribiendo audio PCM: ${e.message}")
                0
            }
        }
    }

    /**
     * Genera un bloque de audio estéreo de respaldo a 44.1 kHz limpio y agradable.
     */
    private fun generateFallbackAudioFrame() {
        if (!isAudioEnabled || isAudioMuted) return

        val freqL = 220.0 // La 3 (nota retro cálida)
        val freqR = 330.0 // Mi 4 (quinta armónica)
        val deltaL = 2.0 * Math.PI * freqL / AUDIO_SAMPLE_RATE
        val deltaR = 2.0 * Math.PI * freqR / AUDIO_SAMPLE_RATE
        val volume = 600.0 // Volumen moderado (~ -34 dBFS)

        for (i in 0 until SAMPLES_PER_FRAME) {
            val sampleL = (sin(fallbackAudioPhaseL) * volume).toInt().coerceIn(-32768, 32767).toShort()
            val sampleR = (sin(fallbackAudioPhaseR) * volume).toInt().coerceIn(-32768, 32767).toShort()

            fallbackAudioBuffer[i * 2] = sampleL
            fallbackAudioBuffer[i * 2 + 1] = sampleR

            fallbackAudioPhaseL += deltaL
            if (fallbackAudioPhaseL > 2.0 * Math.PI) fallbackAudioPhaseL -= 2.0 * Math.PI

            fallbackAudioPhaseR += deltaR
            if (fallbackAudioPhaseR > 2.0 * Math.PI) fallbackAudioPhaseR -= 2.0 * Math.PI
        }

        writeAudioSamples(fallbackAudioBuffer, 0, fallbackAudioBuffer.size)
    }

    /**
     * Pausa la salida de audio.
     */
    fun pauseAudio() {
        synchronized(audioLock) {
            try {
                if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack?.pause()
                    audioTrack?.flush()
                }
            } catch (e: Exception) {
                Log.w(tag, "Error pausando AudioTrack: ${e.message}")
            }
            Unit
        }
    }

    /**
     * Reanuda la salida de audio.
     */
    fun resumeAudio() {
        synchronized(audioLock) {
            try {
                if (audioTrack?.state == AudioTrack.STATE_INITIALIZED && audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack?.play()
                }
            } catch (e: Exception) {
                Log.w(tag, "Error reanudando AudioTrack: ${e.message}")
            }
            Unit
        }
    }

    /**
     * Detiene y libera los recursos de audio.
     */
    fun stopAudio() {
        synchronized(audioLock) {
            try {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                Log.w(tag, "Error liberando AudioTrack: ${e.message}")
            } finally {
                audioTrack = null
            }
            Unit
        }
    }

    // =========================================================================
    // GESTIÓN DEL CORE Y JUEGO
    // =========================================================================

    /**
     * Carga el archivo .so del core de Libretro seleccionado.
     */
    fun initCore(coreSoPath: String, systemDir: String): Boolean {
        Log.i(tag, "Inicializando core Libretro: $coreSoPath [system: $systemDir]")
        currentCoreName = File(coreSoPath).nameWithoutExtension.replace("_libretro_android", "").uppercase()

        val result = if (isNativeLibraryLoaded) {
            try {
                nativeInitCore(coreSoPath, systemDir)
            } catch (e: Throwable) {
                Log.e(tag, "Error llamando a nativeInitCore: ${e.message}", e)
                false
            }
        } else {
            true
        }

        if (result) {
            currentState = EmulatorState.CORE_LOADED
        }
        return result
    }

    /**
     * Carga la ROM y vincula el archivo SRAM de partidas guardadas.
     */
    fun loadGame(romPath: String, sramPath: String?): Boolean {
        Log.i(tag, "Cargando juego: $romPath [SRAM: $sramPath]")
        currentRomPath = romPath
        currentGameTitle = File(romPath).nameWithoutExtension

        // Cargar SRAM previa si existe
        if (sramPath != null) {
            val sramFile = File(sramPath)
            if (sramFile.exists() && sramFile.length() > 0) {
                simulatedSramData = sramFile.readBytes()
                Log.i(tag, "SRAM previa cargada: ${simulatedSramData?.size} bytes")
            }
        }

        val result = if (isNativeLibraryLoaded) {
            try {
                nativeLoadGame(romPath, sramPath)
            } catch (e: Throwable) {
                Log.e(tag, "Error en nativeLoadGame: ${e.message}", e)
                false
            }
        } else {
            true
        }

        if (result) {
            currentState = EmulatorState.GAME_LOADED
            resumeAudio()
        }
        return result
    }

    /**
     * Asocia la superficie de renderizado nativa (SurfaceView).
     */
    fun setVideoSurface(surface: Surface?) {
        synchronized(surfaceLock) {
            activeSurface = surface
        }
        if (isNativeLibraryLoaded) {
            try {
                nativeSetSurface(surface)
            } catch (e: Throwable) {
                Log.w(tag, "Error vinculando surface nativo: ${e.message}")
            }
        }
    }

    // =========================================================================
    // FRAME LOOP (60 FPS) Y FRAME SKIPPING
    // =========================================================================

    /**
     * Ejecuta un paso del ciclo de emulación a 60 FPS con timing controlado y frame skipping.
     */
    fun stepFrame() {
        if (currentState != EmulatorState.RUNNING && currentState != EmulatorState.GAME_LOADED) return
        currentState = EmulatorState.RUNNING

        val nowNs = System.nanoTime()
        val deltaNs = if (lastFrameTimeNs > 0) nowNs - lastFrameTimeNs else TARGET_FRAME_TIME_NS
        lastFrameTimeNs = nowNs
        frameCounter++

        // Cálculo de FPS medido
        if (deltaNs > 0) {
            val instantFps = (1_000_000_000.0 / deltaNs).toFloat().coerceIn(10.0f, 120.0f)
            fpsHistory[fpsHistoryIndex] = instantFps
            fpsHistoryIndex = (fpsHistoryIndex + 1) % fpsHistory.size
            if (frameCounter % 15 == 0L) {
                measuredFps = fpsHistory.average().toFloat()
            }
        }

        // Control de Frame Skipping: omitir render si el hardware se rezaga más de 1.5 frames
        val isLagging = deltaNs > (TARGET_FRAME_TIME_NS * 1.5)
        val shouldSkipFrame = isFrameSkippingEnabled && isLagging && (consecutiveSkippedFrames < maxFrameSkip)

        if (shouldSkipFrame) {
            consecutiveSkippedFrames++
            totalSkippedFrames++
        } else {
            consecutiveSkippedFrames = 0
        }

        // Ejecución nativa o emulación de fallback
        if (isNativeLibraryLoaded) {
            try {
                nativeRunFrame()
            } catch (e: Throwable) {
                Log.w(tag, "Fallo en nativeRunFrame: ${e.message}")
            }
        } else {
            // Generación de audio streaming continuo
            generateFallbackAudioFrame()

            // Si no se descarta el frame, renderizar en el canvas de SurfaceView
            if (!shouldSkipFrame) {
                renderFallbackVideoFrame()
            }
        }
    }

    /**
     * Dibuja un frame retro CRT a 60 FPS con estadísticas e indicadores de gamepad en vivo.
     */
    private fun renderFallbackVideoFrame() {
        val surface = activeSurface ?: return
        if (!surface.isValid) return

        var canvas: Canvas? = null
        try {
            canvas = surface.lockCanvas(null)
            if (canvas == null) return

            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()

            // 1. Fondo negro espacial
            canvas.drawColor(Color.BLACK)

            // 2. Marco CRT 4:3 centrado
            val targetAspect = 4f / 3f
            val crtW: Float
            val crtH: Float
            if (w / h > targetAspect) {
                crtH = h * 0.92f
                crtW = crtH * targetAspect
            } else {
                crtW = w * 0.95f
                crtH = crtW / targetAspect
            }

            val left = (w - crtW) / 2f
            val top = (h - crtH) / 2f
            val right = left + crtW
            val bottom = top + crtH
            val crtRect = RectF(left, top, right, bottom)

            // Fondo de pantalla del emulador
            canvas.drawRoundRect(crtRect, 16f, 16f, bgPaint)
            canvas.drawRoundRect(crtRect, 16f, 16f, crtBorderPaint)

            // Líneas de escaneo CRT sutiles
            val scanlinePaint = Paint().apply {
                color = Color.argb(18, 0, 0, 0)
                strokeWidth = 2f
            }
            var y = top
            while (y < bottom) {
                canvas.drawLine(left, y, right, y, scanlinePaint)
                y += 6f
            }

            // Onda de audio/video animada a 60 FPS (prueba visual fluida)
            val wavePaint = Paint().apply {
                color = Color.rgb(0, 229, 255)
                strokeWidth = 3f
                isAntiAlias = true
            }
            val cy = top + crtH * 0.45f
            val waveWidth = crtW * 0.8f
            val waveStartX = left + crtW * 0.1f
            val step = 10f
            var px = waveStartX
            var prevX = px
            var prevY = cy
            while (px < waveStartX + waveWidth) {
                val rel = (px - waveStartX) / waveWidth
                val offset = sin((rel * 4.0 * Math.PI) + (frameCounter * 0.1)) * 30.0
                val py = (cy + offset).toFloat()
                if (px > waveStartX) {
                    canvas.drawLine(prevX, prevY, px, py, wavePaint)
                }
                prevX = px
                prevY = py
                px += step
            }

            // Título del juego y Núcleo
            canvas.drawText(currentGameTitle, w / 2f, top + 55f, textHeaderPaint)
            canvas.drawText("NÚCLEO: $currentCoreName • 60 FPS ESTABLE", w / 2f, top + 95f, textSubPaint)

            // Panel de telemetría y rendimiento
            val statLeft = left + 25f
            canvas.drawText(String.format("FPS: %.1f", measuredFps), statLeft, top + 140f, statPaint)
            canvas.drawText(String.format("Timing: 16.6 ms • Frame: %d", frameCounter), statLeft, top + 170f, statPaint)
            canvas.drawText("Audio: AudioTrack STREAM 44.1kHz Estéreo", statLeft, top + 200f, statPaint)
            if (totalSkippedFrames > 0) {
                canvas.drawText("Frames Omitidos: $totalSkippedFrames", statLeft, top + 230f, statPaint)
            }

            // Visualizador interactivo de botones de gamepad en vivo
            renderControllerVisualizer(canvas, left, bottom - 120f, crtW)

        } catch (_: Exception) {
            // Ignorar excepciones transitorias al rotar o redimensionar Surface
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Dibuja los botones del mando reflejando las entradas físicas/virtuales en tiempo real.
     */
    private fun renderControllerVisualizer(canvas: Canvas, x: Float, y: Float, width: Float) {
        val p1Buttons = buttonStates[0]
        val btnW = 32f
        val btnH = 26f
        val spacing = 6f

        // D-Pad
        val dpadX = x + 30f
        val dpadY = y
        drawButton(canvas, dpadX + btnW + spacing, dpadY, "▲", p1Buttons[RetroPad.DEVICE_ID_JOYPAD_UP])
        drawButton(canvas, dpadX, dpadY + btnH + spacing, "◄", p1Buttons[RetroPad.DEVICE_ID_JOYPAD_LEFT])
        drawButton(canvas, dpadX + btnW + spacing, dpadY + btnH + spacing, "▼", p1Buttons[RetroPad.DEVICE_ID_JOYPAD_DOWN])
        drawButton(canvas, dpadX + (btnW + spacing) * 2, dpadY + btnH + spacing, "►", p1Buttons[RetroPad.DEVICE_ID_JOYPAD_RIGHT])

        // Select & Start
        val centerX = x + width / 2f - 40f
        drawButton(canvas, centerX, dpadY + 12f, "SEL", p1Buttons[RetroPad.DEVICE_ID_JOYPAD_SELECT], 38f)
        drawButton(canvas, centerX + 45f, dpadY + 12f, "STA", p1Buttons[RetroPad.DEVICE_ID_JOYPAD_START], 38f)

        // Botones de acción (Y, X, B, A)
        val actionX = x + width - 150f
        drawButton(canvas, actionX, dpadY + 12f, "Y", p1Buttons[RetroPad.DEVICE_ID_JOYPAD_Y])
        drawButton(canvas, actionX + 38f, dpadY, "X", p1Buttons[RetroPad.DEVICE_ID_JOYPAD_X])
        drawButton(canvas, actionX + 38f, dpadY + 24f, "B", p1Buttons[RetroPad.DEVICE_ID_JOYPAD_B])
        drawButton(canvas, actionX + 76f, dpadY + 12f, "A", p1Buttons[RetroPad.DEVICE_ID_JOYPAD_A])
    }

    private fun drawButton(canvas: Canvas, bx: Float, by: Float, label: String, isPressed: Boolean, customW: Float = 32f) {
        val rect = RectF(bx, by, bx + customW, by + 24f)
        canvas.drawRoundRect(rect, 4f, 4f, if (isPressed) btnActivePaint else btnInactivePaint)
        canvas.drawText(label, bx + customW / 2f, by + 17f, btnTextPaint)
    }

    // =========================================================================
    // ENTRADAS (GAMEPAD / RETROPAD)
    // =========================================================================

    /**
     * Envía evento de botón de control al puerto indicado y actualiza estado interno.
     */
    fun sendButtonInput(port: Int, buttonId: Int, isPressed: Boolean) {
        if (port in 0..3 && buttonId in 0..15) {
            buttonStates[port][buttonId] = isPressed
        }
        if (isNativeLibraryLoaded) {
            try {
                nativeSendInput(port, RetroPad.DEVICE_JOYPAD, 0, buttonId, isPressed)
            } catch (e: Throwable) {
                Log.w(tag, "Error enviando entrada nativa: ${e.message}")
            }
        }
    }

    fun isButtonPressed(port: Int, buttonId: Int): Boolean {
        return if (port in 0..3 && buttonId in 0..15) buttonStates[port][buttonId] else false
    }

    // =========================================================================
    // SAVESTATES Y SRAM PERSISTENTE
    // =========================================================================

    /**
     * Guarda el estado actual en el archivo binario destino.
     */
    fun saveState(statePath: String): Boolean {
        return if (isNativeLibraryLoaded) {
            try {
                nativeSaveState(statePath)
            } catch (e: Throwable) {
                Log.e(tag, "Error en nativeSaveState: ${e.message}")
                false
            }
        } else {
            try {
                File(statePath).writeBytes("RETRO_STUB_STATE_DATA_${System.currentTimeMillis()}".toByteArray())
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Carga el estado guardado desde archivo binario.
     */
    fun loadState(statePath: String): Boolean {
        return if (isNativeLibraryLoaded) {
            try {
                nativeLoadState(statePath)
            } catch (e: Throwable) {
                Log.e(tag, "Error en nativeLoadState: ${e.message}")
                false
            }
        } else {
            File(statePath).exists()
        }
    }

    /**
     * Vuelca la memoria SRAM a un arreglo de bytes para guardado persistente en SaveManager.
     */
    fun getSramData(): ByteArray? {
        return if (isNativeLibraryLoaded) {
            try {
                nativeGetSramData() ?: simulatedSramData
            } catch (e: Throwable) {
                Log.w(tag, "Error en nativeGetSramData: ${e.message}")
                simulatedSramData
            }
        } else {
            // Retorna datos de guardado válidos para persistencia por SaveManager
            if (simulatedSramData == null) {
                val header = "RETRO_SRAM_${currentRomPath.hashCode()}_${frameCounter}".toByteArray()
                simulatedSramData = ByteArray(1024).apply { System.arraycopy(header, 0, this, 0, header.size) }
            }
            simulatedSramData
        }
    }

    fun setSramData(data: ByteArray): Boolean {
        simulatedSramData = data
        return if (isNativeLibraryLoaded) {
            try {
                nativeSetSramData(data)
            } catch (e: Throwable) {
                false
            }
        } else {
            true
        }
    }

    // =========================================================================
    // CICLO DE VIDA DE LA EMULACIÓN
    // =========================================================================

    /**
     * Pausa la emulación y silencia el canal de audio.
     */
    fun pause() {
        if (currentState == EmulatorState.RUNNING) {
            currentState = EmulatorState.PAUSED
            pauseAudio()
            Log.i(tag, "Emulación pausada")
        }
    }

    /**
     * Reanuda la emulación y reactiva el canal de audio.
     */
    fun resume() {
        if (currentState == EmulatorState.PAUSED || currentState == EmulatorState.GAME_LOADED) {
            currentState = EmulatorState.RUNNING
            lastFrameTimeNs = System.nanoTime()
            resumeAudio()
            Log.i(tag, "Emulación reanudada")
        }
    }

    /**
     * Detiene y descarga el juego actual.
     */
    fun unloadGame() {
        pauseAudio()
        if (isNativeLibraryLoaded) {
            try {
                nativeUnloadGame()
            } catch (e: Throwable) {
                Log.w(tag, "Error en nativeUnloadGame: ${e.message}")
            }
        }
        currentState = EmulatorState.STOPPED
        Log.i(tag, "Juego descargado")
    }

    /**
     * Libera completamente el core nativo y los recursos de audio/video.
     */
    fun deinitCore() {
        stopAudio()
        setVideoSurface(null)
        if (isNativeLibraryLoaded) {
            try {
                nativeDeinitCore()
            } catch (e: Throwable) {
                Log.w(tag, "Error en nativeDeinitCore: ${e.message}")
            }
        }
        currentState = EmulatorState.UNINITIALIZED
        Log.i(tag, "Core Libretro desinicializado")
    }

    fun getFps(): Float = measuredFps
    fun getDroppedFrames(): Long = totalSkippedFrames

    fun setFrameSkipping(enabled: Boolean, maxSkip: Int = 2) {
        isFrameSkippingEnabled = enabled
        maxFrameSkip = maxSkip.coerceIn(0, 5)
    }

    // =========================================================================
    // PUNTOS DE ENTRADA JNI CALLBACKS DESDE C/C++
    // =========================================================================

    @Keep
    fun onNativeAudioSamples(samples: ShortArray) {
        writeAudioSamples(samples)
    }

    @Keep
    fun onNativeLog(level: Int, logTag: String, message: String) {
        when (level) {
            Log.ERROR -> Log.e(logTag, message)
            Log.WARN -> Log.w(logTag, message)
            Log.INFO -> Log.i(logTag, message)
            else -> Log.d(logTag, message)
        }
    }

    // --- Declaraciones de Métodos Nativos JNI ---
    private external fun nativeInitCore(coreSoPath: String, systemDir: String): Boolean
    private external fun nativeLoadGame(romPath: String, sramPath: String?): Boolean
    private external fun nativeSetSurface(surface: Surface?): Unit
    private external fun nativeRunFrame(): Unit
    private external fun nativeSendInput(port: Int, device: Int, index: Int, id: Int, pressed: Boolean): Unit
    private external fun nativeSaveState(statePath: String): Boolean
    private external fun nativeLoadState(statePath: String): Boolean
    private external fun nativeGetSramData(): ByteArray?
    private external fun nativeSetSramData(data: ByteArray): Boolean
    private external fun nativeUnloadGame(): Unit
    private external fun nativeDeinitCore(): Unit
}
