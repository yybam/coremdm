package com.core.mdm.ws

import android.content.Context
import android.util.Log
import com.core.mdm.BuildConfig
import com.core.mdm.firebase.EnrollmentManager
import com.core.mdm.installer.SelfUpdateService
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Maintains a persistent WebSocket connection to the Node.js backend so the admin can
 * push a forced fleet update directly over the socket (instead of, or in addition to, FCM).
 *
 * The backend URL is baked in at build time via BuildConfig.WS_BACKEND_URL, sourced from
 * local.properties:
 *
 *   WS_BACKEND_URL=ws://YOUR_BACKEND_HOST:3000
 *
 * If the field is blank this client is a no-op, preserving the existing FCM + Firestore path.
 */
object WsCommandClient {

    private const val TAG              = "WsCommandClient"
    private const val RECONNECT_MIN_MS = 2_000L
    private const val RECONNECT_MAX_MS = 64_000L

    private val http = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // no read timeout — WS is long-lived
        .build()

    private var scope:       CoroutineScope? = null
    private var ws:          WebSocket?      = null
    private var connectJob:  Job?            = null
    private var backoffMs:   Long            = RECONNECT_MIN_MS
    private var appContext:  Context?        = null

    fun start(context: Context) {
        if (BuildConfig.WS_BACKEND_URL.isBlank()) return   // not configured — silent no-op
        if (scope != null) return                           // already running
        appContext = context.applicationContext
        scope     = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scheduleConnect(0)
    }

    fun stop() {
        connectJob?.cancel()
        ws?.close(1000, "Service stopped")
        ws       = null
        scope?.cancel()
        scope    = null
        backoffMs = RECONNECT_MIN_MS
    }

    private fun scheduleConnect(delayMs: Long) {
        val s = scope ?: return
        connectJob = s.launch {
            if (delayMs > 0) delay(delayMs)
            if (isActive) connect()
        }
    }

    private suspend fun connect() {
        val ctx  = appContext ?: return
        val user = Firebase.auth.currentUser ?: run {
            scheduleConnect(RECONNECT_MIN_MS)   // not yet signed in — retry shortly
            return
        }
        val idToken  = runCatching { user.getIdToken(false).await().token }
            .getOrNull() ?: run { scheduleConnect(RECONNECT_MIN_MS); return }
        val deviceId = EnrollmentManager.getHardwareId(ctx)

        val url = BuildConfig.WS_BACKEND_URL.trimEnd('/')
        val req = Request.Builder().url(url).build()

        http.newWebSocket(req, object : WebSocketListener() {

            override fun onOpen(socket: WebSocket, response: Response) {
                ws        = socket
                backoffMs = RECONNECT_MIN_MS
                socket.send(JSONObject().apply {
                    put("type",     "auth")
                    put("idToken",  idToken)
                    put("role",     "device")
                    put("deviceId", deviceId)
                }.toString())
                Log.i(TAG, "WS connected to backend")
            }

            override fun onMessage(socket: WebSocket, text: String) {
                runCatching { handleMessage(JSONObject(text)) }
                    .onFailure { Log.w(TAG, "Bad WS message: ${it.message}") }
            }

            override fun onClosing(socket: WebSocket, code: Int, reason: String) {
                socket.close(1000, null)
            }

            override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                ws = null
                Log.i(TAG, "WS closed (code=$code)")
                // code 1000 = intentional close from stop(); anything else → reconnect
                if (code != 1000) scheduleReconnect()
            }

            override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                ws = null
                Log.w(TAG, "WS failure: ${t.message}")
                scheduleReconnect()
            }
        })
    }

    private fun handleMessage(msg: JSONObject) {
        val ctx = appContext ?: return
        when (msg.optString("type")) {
            "auth_ok" ->
                Log.i(TAG, "WS authenticated (session=${msg.optString("sessionId")})")

            "force_update" -> {
                val url     = msg.optString("url")
                val version = msg.optString("version")
                if (url.isNotEmpty()) {
                    Log.i(TAG, "force_update: v$version → $url")
                    SelfUpdateService.start(ctx, apkUrl = url, version = version)
                }
            }

            "error" ->
                Log.w(TAG, "Server error: ${msg.optString("message")}")
        }
    }

    private fun scheduleReconnect() {
        Log.i(TAG, "Reconnecting in ${backoffMs}ms")
        scheduleConnect(backoffMs)
        backoffMs = (backoffMs * 2).coerceAtMost(RECONNECT_MAX_MS)
    }
}
