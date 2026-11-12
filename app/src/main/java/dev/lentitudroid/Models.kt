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

enum class Phase { Idle, Connecting, Connected }
