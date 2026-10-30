package com.example.chitgame.net

import com.example.chitgame.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Phase { LOBBY, PLAYING, RESULT, CLOSED }

data class SessionState(
    val phase: Phase = Phase.LOBBY,
    val isHost: Boolean = false,
    val connected: Boolean = false,
    val myId: Int = -1,
    val roomName: String = "",
    val players: List<PlayerInfo> = emptyList(),
    val game: GameView? = null,
    val result: ServerMsg.GameOver? = null,
    val message: String? = null
)

/** Single reducer shared by host (local player) and clients (network). */
fun SessionState.reduce(m: ServerMsg): SessionState = when (m) {
    is ServerMsg.Welcome -> copy(myId = m.playerId, roomName = m.roomName, connected = true)
    is ServerMsg.Lobby -> copy(players = m.players)
    is ServerMsg.State -> copy(phase = Phase.PLAYING, game = m.view, result = null)
    is ServerMsg.GameOver -> copy(phase = Phase.RESULT, result = m)
    is ServerMsg.Rejected -> copy(message = m.reason)
    is ServerMsg.Aborted -> copy(phase = Phase.LOBBY, game = null, result = null, message = m.reason)
}

/** The UI only talks to this; it doesn't care whether we're host or client. */
abstract class GameSession(initial: SessionState) {
    protected val _state = MutableStateFlow(initial)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    protected fun dispatch(m: ServerMsg) = _state.update { it.reduce(m) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    abstract fun pick(chitId: Int)
    abstract fun declare()
    abstract fun close()
    open fun startGame() {}
    open fun playAgain() {}
}
