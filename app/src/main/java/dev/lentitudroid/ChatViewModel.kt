package dev.lentitudroid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val _phase = MutableStateFlow(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _startError = MutableStateFlow<String?>(null)
    val startError: StateFlow<String?> = _startError.asStateFlow()

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    private val _nick = MutableStateFlow("")
    val nick: StateFlow<String> = _nick.asStateFlow()

    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private var ws: WebSocket? = null
    private var gen = 0

    private fun nickError(n: String): String? =
        if (n.isEmpty() || n.toByteArray().size > 32 || !NICK_RE.matches(n))
            "ник: 1–32 байта, только буквы, цифры, _ и -" else null

    private fun normalizeUrl(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "ws://$s"
        val afterScheme = s.substringAfter("://")
        if (!afterScheme.contains('/')) s += "/ws"
        return s
    }

    fun connect(address: String, nickRaw: String) {
        val n = nickRaw.trim()
        nickError(n)?.let { _startError.value = it; return }
        val url = normalizeUrl(address) ?: run { _startError.value = "введите адрес сервера"; return }
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            _startError.value = "некорректный адрес"; return
        }

        ws?.cancel()
        val token = ++gen
        _nick.value = n
        _lines.value = emptyList()
        _startError.value = null
        _phase.value = Phase.Connecting

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (token != gen) return
                webSocket.send(json.encodeToString(OutMsg(type = "join", nick = n)))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (token != gen) return
                val msg = try { json.decodeFromString<ServerMsg>(text) } catch (e: Exception) { return }
                when (msg.type) {
                    "welcome" -> _phase.value = Phase.Connected
                    "message" -> add(Line.Chat(msg.nick ?: "?", msg.text.orEmpty()))
                    "system" -> add(Line.System(msg.text.orEmpty()))
                    "error" -> {
                        if (_phase.value == Phase.Connected) add(Line.Error(msg.text.orEmpty()))
                        else {
                            gen++
                            webSocket.close(1000, null)
                            _startError.value = msg.text.orEmpty()
                            _phase.value = Phase.Idle
                        }
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(token, "соединение закрыто")
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                lost(token, "ошибка соединения: ${t.message ?: t.javaClass.simpleName}")
        })
    }

    private fun lost(token: Int, why: String) {
        if (token != gen) return
        gen++
        _startError.value = why
        _phase.value = Phase.Idle
    }

    private fun add(line: Line) = _lines.update { it + line }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (t.contains('\n') || t.contains('\r')) { add(Line.Error("перевод строки запрещён")); return }
        if (t.toByteArray().size > 4096) { add(Line.Error("сообщение длиннее 4096 байт")); return }
        ws?.send(json.encodeToString(OutMsg(type = "message", text = t)))
    }

    fun disconnect() {
        gen++
        ws?.close(1000, null)
        ws = null
        _phase.value = Phase.Idle
    }

    fun upLocalServer(nickRaw: String) {
        val n = nickRaw.trim()
        nickError(n)?.let { _startError.value = it; return }
        _startError.value = null
        _phase.value = Phase.Connecting
        viewModelScope.launch(Dispatchers.IO) {
            val err = LocalServer.start(getApplication())
            if (err != null) {
                _startError.value = err
                _phase.value = Phase.Idle
                return@launch
            }
            delay(800) // даём серверу время начать слушать порт
            connect(LocalServer.URL, n)
        }
    }

    override fun onCleared() {
        ws?.cancel()
        LocalServer.stop()
    }

    private companion object {
        val NICK_RE = Regex("^[\\p{L}\\p{N}_-]+$")
    }
}
