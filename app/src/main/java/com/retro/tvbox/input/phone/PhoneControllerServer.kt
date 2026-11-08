package com.retro.tvbox.input.phone

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.retro.tvbox.input.InputRouter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Embedded HTTP and WebSocket server powered by Ktor CIO.
 * Serves the mobile gamepad web app and manages low-latency input streaming
 * between smartphones and the TV emulation subsystem.
 */
class PhoneControllerServer(
    private val appContext: Context,
    val port: Int = DEFAULT_PORT
) {
    companion object {
        private const val TAG = "PhoneControllerServer"
        const val DEFAULT_PORT = 8080
        private const val WEB_ASSETS_DIR = "web_controller"

        @Volatile
        private var instance: PhoneControllerServer? = null

        fun getInstance(context: Context, port: Int = DEFAULT_PORT): PhoneControllerServer {
            return instance ?: synchronized(this) {
                instance ?: PhoneControllerServer(context.applicationContext, port).also { instance = it }
            }
        }
    }

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: CIOApplicationEngine? = null

    // Track active WebSocket sessions: sessionId -> WebSocketSession wrapper
    private val activeSessions = ConcurrentHashMap<String, PhoneClientSession>()
    // Reverse map: playerIndex (0..3) -> sessionId
    private val playerToSessionMap = ConcurrentHashMap<Int, String>()

    @Volatile
    var isRunning: Boolean = false
        private set

    data class PhoneClientSession(
        val sessionId: String,
        var playerIndex: Int,
        val sendFrame: suspend (String) -> Unit
    )

    /**
     * Starts the embedded Ktor server on all network interfaces.
     */
    @Synchronized
    fun start() {
        if (isRunning) {
            Log.d(TAG, "Server already running on port $port")
            return
        }

        try {
            engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                install(WebSockets) {
                    pingPeriodMillis = 15_000
                    timeoutMillis = 30_000
                    maxFrameSize = 65_536
                    masking = false
                }

                routing {
                    // Serve virtual gamepad static assets
                    get("/") {
                        call.serveAssetFile(appContext, "$WEB_ASSETS_DIR/index.html", "text/html")
                    }

                    get("/index.html") {
                        call.serveAssetFile(appContext, "$WEB_ASSETS_DIR/index.html", "text/html")
                    }

                    get("/controller.css") {
                        call.serveAssetFile(appContext, "$WEB_ASSETS_DIR/controller.css", "text/css")
                    }

                    get("/controller.js") {
                        call.serveAssetFile(appContext, "$WEB_ASSETS_DIR/controller.js", "application/javascript")
                    }

                    // Catch-all for extra assets (images, sound effects, manifests)
                    get("/{path...}") {
                        val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                        val assetPath = "$WEB_ASSETS_DIR/$path"
                        val mimeType = resolveMimeType(assetPath)
                        call.serveAssetFile(appContext, assetPath, mimeType)
                    }

                    // WebSocket endpoint for real-time mobile gamepad
                    webSocket("/ws/controller") {
                        val sessionId = UUID.randomUUID().toString()
                        val inputRouter = InputRouter.getInstance()

                        // Assign player slot
                        val assignedSlot = inputRouter.registerVirtualPlayer(sessionId)
                        val clientSession = PhoneClientSession(
                            sessionId = sessionId,
                            playerIndex = assignedSlot,
                            sendFrame = { text ->
                                try {
                                    send(Frame.Text(text))
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to send frame to $sessionId", e)
                                }
                            }
                        )

                        activeSessions[sessionId] = clientSession
                        if (assignedSlot in 0 until InputRouter.MAX_PLAYERS) {
                            playerToSessionMap[assignedSlot] = sessionId
                        }

                        // Send initial handshake with assigned player slot
                        val welcomeJson = JSONObject().apply {
                            put("type", "handshake")
                            put("sessionId", sessionId)
                            put("player", assignedSlot + 1) // 1-based for UI
                            put("status", if (assignedSlot != -1) "connected" else "full")
                            put("serverTime", System.currentTimeMillis())
                        }
                        send(Frame.Text(welcomeJson.toString()))

                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    processIncomingMessage(sessionId, text)
                                }
                            }
                        } catch (e: ClosedReceiveChannelException) {
                            Log.i(TAG, "WebSocket channel closed for session: $sessionId")
                        } catch (e: Exception) {
                            Log.e(TAG, "WebSocket exception for session: $sessionId", e)
                        } finally {
                            activeSessions.remove(sessionId)
                            if (assignedSlot in 0 until InputRouter.MAX_PLAYERS) {
                                playerToSessionMap.remove(assignedSlot)
                            }
                            inputRouter.unregisterVirtualPlayer(sessionId)
                            Log.i(TAG, "Session cleaned up: $sessionId")
                        }
                    }
                }
            }

            engine?.start(wait = false)
            isRunning = true
            Log.i(TAG, "PhoneControllerServer started successfully at http://${getLocalIpAddress(appContext)}:$port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PhoneControllerServer", e)
            isRunning = false
        }
    }

    /**
     * Stops the embedded server and disconnects active virtual players.
     */
    @Synchronized
    fun stop() {
        if (!isRunning) return
        try {
            engine?.stop(1000, 2000)
            engine = null
            activeSessions.clear()
            playerToSessionMap.clear()
            isRunning = false
            Log.i(TAG, "PhoneControllerServer stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping PhoneControllerServer", e)
        }
    }

    /**
     * Sends a haptic vibration command to a specific connected phone.
     * Can be invoked when emulator cores trigger rumble events.
     */
    fun sendHapticVibration(playerIndex: Int, durationMs: Long = 40) {
        val sessionId = playerToSessionMap[playerIndex] ?: return
        val session = activeSessions[sessionId] ?: return

        serverScope.launch {
            val json = JSONObject().apply {
                put("type", "vibrate")
                put("duration", durationMs)
            }
            session.sendFrame(json.toString())
        }
    }

    /**
     * Broadcasts haptic vibration to all connected virtual phone gamepads.
     */
    fun broadcastVibration(durationMs: Long = 40) {
        val json = JSONObject().apply {
            put("type", "vibrate")
            put("duration", durationMs)
        }.toString()

        serverScope.launch {
            activeSessions.values.forEach { session ->
                session.sendFrame(json)
            }
        }
    }

    /**
     * Parses and routes JSON messages from virtual gamepads.
     */
    private fun processIncomingMessage(sessionId: String, payload: String) {
        val inputRouter = InputRouter.getInstance()
        try {
            val json = JSONObject(payload)
            val type = json.optString("type", "button")

            when (type) {
                "button" -> {
                    val button = json.optString("button")
                    val action = json.optString("action") // "down" or "up"
                    val isDown = action.equals("down", ignoreCase = true)
                    inputRouter.handleVirtualButton(sessionId, button, isDown)
                }

                "axis" -> {
                    val stick = json.optString("stick", "left")
                    val x = json.optDouble("x", 0.0).toFloat()
                    val y = json.optDouble("y", 0.0).toFloat()
                    inputRouter.handleVirtualAxis(sessionId, stick, x, y)
                }

                "ping" -> {
                    val clientTime = json.optLong("clientTime", 0)
                    val session = activeSessions[sessionId]
                    if (session != null) {
                        serverScope.launch {
                            val pong = JSONObject().apply {
                                put("type", "pong")
                                put("clientTime", clientTime)
                                put("serverTime", System.currentTimeMillis())
                            }
                            session.sendFrame(pong.toString())
                        }
                    }
                }

                "changePlayer" -> {
                    val targetPlayer = json.optInt("player", 1) - 1
                    if (targetPlayer in 0 until InputRouter.MAX_PLAYERS) {
                        inputRouter.unregisterVirtualPlayer(sessionId)
                        val newSlot = inputRouter.registerVirtualPlayer(sessionId, targetPlayer)
                        val session = activeSessions[sessionId]
                        if (session != null) {
                            session.playerIndex = newSlot
                            if (newSlot != -1) {
                                playerToSessionMap[newSlot] = sessionId
                            }
                            serverScope.launch {
                                val reply = JSONObject().apply {
                                    put("type", "playerChanged")
                                    put("player", newSlot + 1)
                                }
                                session.sendFrame(reply.toString())
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message from session $sessionId: $payload", e)
        }
    }

    /**
     * Helper to read Android assets and stream them via Ktor response.
     */
    private suspend fun io.ktor.server.application.ApplicationCall.serveAssetFile(
        context: Context,
        assetPath: String,
        mimeType: String
    ) {
        var stream: InputStream? = null
        try {
            stream = context.assets.open(assetPath)
            val bytes = stream.readBytes()
            response.headers.append("Cache-Control", "no-cache, must-revalidate")
            respondBytes(bytes, ContentType.parse(mimeType), HttpStatusCode.OK)
        } catch (e: Exception) {
            respondText("Asset not found: $assetPath", ContentType.Text.Plain, HttpStatusCode.NotFound)
        } finally {
            stream?.close()
        }
    }

    private fun resolveMimeType(path: String): String {
        return when {
            path.endsWith(".html", ignoreCase = true) -> "text/html"
            path.endsWith(".css", ignoreCase = true) -> "text/css"
            path.endsWith(".js", ignoreCase = true) -> "application/javascript"
            path.endsWith(".json", ignoreCase = true) -> "application/json"
            path.endsWith(".png", ignoreCase = true) -> "image/png"
            path.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
            path.endsWith(".ico", ignoreCase = true) -> "image/x-icon"
            path.endsWith(".woff2", ignoreCase = true) -> "font/woff2"
            else -> "application/octet-stream"
        }
    }

    /**
     * Detects the TV's local IPv4 address on the Wi-Fi or local Ethernet network.
     */
    fun getLocalIpAddress(context: Context): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val candidateList = mutableListOf<String>()

            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue

                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        // Prioritize Wi-Fi and Ethernet interfaces
                        val name = intf.name.lowercase()
                        if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")) {
                            return hostAddress
                        }
                        candidateList.add(hostAddress)
                    }
                }
            }

            if (candidateList.isNotEmpty()) {
                return candidateList.first()
            }

            // Fallback via WifiManager
            val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.connectionInfo?.ipAddress?.let { ipInt ->
                if (ipInt != 0) {
                    return String.format(
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving local IP address", e)
        }
        return "127.0.0.1"
    }

    /**
     * Returns the formatted URL that mobile devices can navigate to.
     */
    fun getServerUrl(ctx: Context = appContext): String {
        val ip = getLocalIpAddress(ctx)
        return "http://$ip:$port"
    }

    fun getLocalServerUrl(): String = getServerUrl()
}
