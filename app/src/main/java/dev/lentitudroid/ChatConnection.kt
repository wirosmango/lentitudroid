package dev.lentitudroid

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Единственное соединение с сервером. Живёт на уровне процесса (а не Activity/ViewModel),
 * поэтому переживает сворачивание и закрытие экрана. Пока соединение активно,
 * процесс удерживает [ConnectionService] (foreground-сервис с уведомлением).
 * При обрыве сети соединение автоматически восстанавливается.
 */
object ChatConnection {
    private val _phase = MutableStateFlow(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _startError = MutableStateFlow<String?>(null)
    val startError: StateFlow<String?> = _startError.asStateFlow()

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    private val _nick = MutableStateFlow("")
    val nick: StateFlow<String> = _nick.asStateFlow()

    // pingInterval: клиент сам проверяет, что соединение живо, и быстро замечает обрыв
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var ws: WebSocket? = null
    private var request: Request? = null
    private var gen = 0                // токен: колбэки от старых сокетов игнорируются
    private var attempt = 0            // номер попытки переподключения (для backoff)
    private var retryJob: Job? = null
    private var lastReconnectError: String? = null
    private var ownsLocalServer = false
    private var serverLabel = ""

    private val NICK_RE = Regex("^[\\p{L}\\p{N}_-]+$")

    private fun nickError(n: String): String? =
        if (n.isEmpty() || n.toByteArray().size > 32 || !NICK_RE.matches(n))
            "ник: 1–32 байта, только буквы, цифры, _ и -" else null

    private fun normalizeUrl(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "ws://$s"
        if (!s.substringAfter("://").contains('/')) s += "/ws"
        return s
    }

    fun addLine(line: Line) = _lines.update { it + line }

    fun connect(ctx: Context, address: String, nickRaw: String) {
        val n = nickRaw.trim()
        nickError(n)?.let { _startError.value = it; return }
        val url = normalizeUrl(address) ?: run { _startError.value = "введите адрес сервера"; return }
        val req = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            _startError.value = "некорректный адрес"; return
        }

        HistoryStore.init(ctx)
        serverLabel = url
        retryJob?.cancel()
        gen++
        ws?.cancel()
        ws = null
        request = req
        attempt = 0
        lastReconnectError = null
        _nick.value = n
        _lines.value = emptyList()
        _startError.value = null
        _phase.value = Phase.Connecting

        ConnectionService.start(ctx.applicationContext) // держим процесс живым
        openSocket()
    }

    private fun openSocket() {
        val req = request ?: return
        val n = _nick.value
        val token = ++gen
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (token != gen) return
                webSocket.send(json.encodeToString(OutMsg(type = "join", nick = n)))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (token != gen) return
                val msg = try { json.decodeFromString<ServerMsg>(text) } catch (e: Exception) { return }
                when (msg.type) {
                    "welcome" -> {
                        when (_phase.value) {
                            Phase.Connecting -> HistoryStore.startSession(serverLabel, n)
                            Phase.Reconnecting -> {
                                addLine(Line.System("соединение восстановлено"))
                                HistoryStore.system("соединение восстановлено")
                            }
                            else -> {}
                        }
                        attempt = 0
                        lastReconnectError = null
                        _phase.value = Phase.Connected
                    }
                    "message" -> {
                        addLine(Line.Chat(msg.nick ?: "?", msg.text.orEmpty()))
                        HistoryStore.message(msg.nick ?: "?", msg.text.orEmpty())
                    }
                    "system" -> {
                        addLine(Line.System(msg.text.orEmpty()))
                        HistoryStore.system(msg.text.orEmpty())
                    }
                    "error" -> onServerError(webSocket, msg.text.orEmpty())
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onLost(token, "соединение закрыто")
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                onLost(token, "обрыв соединения")
        })
    }

    private fun onServerError(webSocket: WebSocket, text: String) {
        when (_phase.value) {
            Phase.Connected -> addLine(Line.Error(text))
            Phase.Connecting -> { // отказ при первом входе (ник занят, сервер полон…)
                gen++
                webSocket.close(1000, null)
                failToIdle(text)
            }
            Phase.Reconnecting -> { // например, ник ещё занят старой сессией — пробуем позже
                if (text != lastReconnectError) addLine(Line.Error(text))
                lastReconnectError = text
                scheduleRetry()
            }
            Phase.Idle -> {}
        }
    }

    private fun onLost(token: Int, why: String) {
        if (token != gen) return
        when (_phase.value) {
            Phase.Connected -> {
                addLine(Line.Error("$why, переподключение…"))
                HistoryStore.system("$why, переподключение…")
                _phase.value = Phase.Reconnecting
                attempt = 0
                scheduleRetry()
            }
            Phase.Reconnecting -> scheduleRetry()
            Phase.Connecting -> { gen++; failToIdle(if (why == "обрыв соединения") "не удалось подключиться" else why) }
            Phase.Idle -> {}
        }
    }

    private fun failToIdle(message: String) {
        _startError.value = message
        _phase.value = Phase.Idle
        stopLocalServerIfOwned()
    }

    /** Попытки с нарастающей паузой: 1, 2, 4, 8, 15 с (дальше каждые 15 с). */
    private fun scheduleRetry() {
        gen++ // колбэки текущего сокета больше не нужны
        ws?.cancel()
        ws = null
        retryJob?.cancel()
        val pause = minOf(1000L shl minOf(attempt, 4), 15_000L)
        attempt++
        retryJob = scope.launch {
            delay(pause)
            if (_phase.value == Phase.Reconnecting) openSocket()
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (t.contains('\n') || t.contains('\r')) { addLine(Line.Error("перевод строки запрещён")); return }
        if (t.toByteArray().size > 4096) { addLine(Line.Error("сообщение длиннее 4096 байт")); return }
        val socket = ws
        if (socket == null || _phase.value != Phase.Connected) {
            addLine(Line.Error("нет соединения, сообщение не отправлено"))
            return
        }
        socket.send(json.encodeToString(OutMsg(type = "message", text = t)))
    }

    /** Выход пользователем: закрывает сокет, останавливает сервис и (если запускали) локальный сервер. */
    fun disconnect() {
        gen++
        retryJob?.cancel()
        ws?.close(1000, null)
        ws = null
        _phase.value = Phase.Idle
        HistoryStore.endSession()
        stopLocalServerIfOwned()
    }

    fun upLocalServer(ctx: Context, nickRaw: String) {
        val n = nickRaw.trim()
        nickError(n)?.let { _startError.value = it; return }
        _startError.value = null
        _phase.value = Phase.Connecting
        scope.launch(Dispatchers.IO) {
            val err = LocalServer.start(ctx.applicationContext)
            if (err != null) {
                failToIdle(err)
                return@launch
            }
            ownsLocalServer = true
            delay(800) // даём серверу время начать слушать порт
            connect(ctx, LocalServer.URL, n)
        }
    }

    private fun stopLocalServerIfOwned() {
        if (ownsLocalServer) {
            LocalServer.stop()
            ownsLocalServer = false
        }
    }
}
