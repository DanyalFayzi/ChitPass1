package com.example.chitgame.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One paper slip. `id` is unique across the whole deck (N*N chits). */
@Serializable data class Chit(val id: Int, val obj: String)

@Serializable data class PlayerInfo(val id: Int, val name: String)

/** What a single player is allowed to see of the game. */
@Serializable
data class GameView(
    val hand: List<Chit>,
    val receiveFrom: String,
    val passTo: String,
    val round: Int,
    val selectedChitId: Int?,   // chit this player already passed this round (null = not yet)
    val readyCount: Int,        // how many players have passed this round
    val total: Int
)

@Serializable
sealed interface ClientMsg {
    @Serializable @SerialName("join") data class Join(val name: String) : ClientMsg
    @Serializable @SerialName("pick") data class Pick(val chitId: Int) : ClientMsg
    @Serializable @SerialName("declare") data object Declare : ClientMsg
}

@Serializable
sealed interface ServerMsg {
    @Serializable @SerialName("welcome") data class Welcome(val playerId: Int, val roomName: String) : ServerMsg
    @Serializable @SerialName("lobby") data class Lobby(val players: List<PlayerInfo>) : ServerMsg
    @Serializable @SerialName("state") data class State(val view: GameView) : ServerMsg
    @Serializable @SerialName("over") data class GameOver(val winnerId: Int, val winnerName: String, val obj: String) : ServerMsg
    @Serializable @SerialName("rejected") data class Rejected(val reason: String) : ServerMsg
    @Serializable @SerialName("aborted") data class Aborted(val reason: String) : ServerMsg
}

val WireJson = Json { ignoreUnknownKeys = true }

object Fruits {
    private val table = linkedMapOf(
        "Apple" to "🍎", "Banana" to "🍌", "Mango" to "🥭", "Orange" to "🍊",
        "Grapes" to "🍇", "Pineapple" to "🍍", "Strawberry" to "🍓", "Watermelon" to "🍉",
        "Cherry" to "🍒", "Peach" to "🍑", "Lemon" to "🍋", "Kiwi" to "🥝"
    )
    val names: List<String> get() = table.keys.toList()
    fun emoji(name: String) = table[name] ?: "📜"
}
