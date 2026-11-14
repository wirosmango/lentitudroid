package dev.lentitudroid

import kotlinx.serialization.Serializable

@Serializable
data class ServerMsg(
    val type: String,
    val nick: String? = null,
    val text: String? = null,
    val time: String? = null,
)

@Serializable
data class OutMsg(
    val type: String,
    val nick: String? = null,
    val text: String? = null,
)

sealed interface Line {
    data class Chat(val nick: String, val text: String) : Line
    data class System(val text: String) : Line
    data class Error(val text: String) : Line
}

/** Reconnecting — соединение потеряно, идёт автоматическое переподключение (чат остаётся открытым). */
enum class Phase { Idle, Connecting, Connected, Reconnecting }

/** Разовые события для Activity (диалог сохранения файла, всплывающие подсказки). */
sealed interface UiEvent {
    data class Export(val fileName: String, val content: String) : UiEvent
    data class Toast(val text: String) : UiEvent
}
