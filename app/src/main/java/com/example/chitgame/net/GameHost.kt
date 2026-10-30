package com.example.chitgame.net

import com.example.chitgame.game.GameEngine
import com.example.chitgame.model.*
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Authoritative server. Also plays as player 0 (the host) without using a socket.
 * All game mutations run under [mutex], so "first valid DECLARE wins" is race-free.
 */
class GameHost(
    private val scope: CoroutineScope,
    hostName: String,
    roomName: String,
    val port: Int = 8080
) : GameSession(SessionState(isHost = true, connected = true, myId = HOST_ID, roomName = roomName)) {

    companion object { const val HOST_ID = 0; const val MIN = 4; const val MAX = 10 }

    private val mutex = Mutex()
    private val players = LinkedHashMap<Int, PlayerInfo>()          // insertion order = seating order
    private val sockets = HashMap<Int, WebSocketServerSession>()
    private var engine: GameEngine? = null
    private var nextId = 1
    private var server: ApplicationEngine? = null

    init { players[HOST_ID] = PlayerInfo(HOST_ID, hostName.trim().take(16).ifBlank { "Host" }) }

    fun start() {
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets) { pingPeriodMillis = 15_000; timeoutMillis = 30_000 }
            routing { webSocket("/game") { handleConnection(this) } }
        }.start(wait = false)
        scope.launch { mutex.withLock { broadcastLobby() } }
    }

    // ---------- connection handling ----------

    private suspend fun handleConnection(s: WebSocketServerSession) {
        var myId = -1
        try {
            for (frame in s.incoming) {
                if (frame !is Frame.Text) continue
                val msg = runCatching {
                    WireJson.decodeFromString(ClientMsg.serializer(), frame.readText())
                }.getOrNull() ?: continue

                if (myId == -1) {
                    if (msg is ClientMsg.Join) {
                        myId = mutex.withLock { admit(s, msg.name) }
                        if (myId == -1) { s.close(CloseReason(CloseReason.Codes.NORMAL, "rejected")); return }
                    }
                } else {
                    mutex.withLock { handle(myId, msg) }
                }
            }
        } finally {
            if (myId != -1) mutex.withLock { onDisconnect(myId) }
        }
    }

    private suspend fun admit(s: WebSocketServerSession, rawName: String): Int {
        if (_state.value.phase != Phase.LOBBY) { sendRaw(s, ServerMsg.Rejected("Game already started")); return -1 }
        if (players.size >= MAX) { sendRaw(s, ServerMsg.Rejected("Room is full ($MAX players)")); return -1 }
        val id = nextId++
        var name = rawName.trim().take(16).ifBlank { "Player $id" }
        if (players.values.any { it.name.equals(name, true) }) name = "$name $id"
        players[id] = PlayerInfo(id, name)
        sockets[id] = s
        sendRaw(s, ServerMsg.Welcome(id, _state.value.roomName))
        broadcastLobby()
        return id
    }

    private suspend fun onDisconnect(id: Int) {
        sockets.remove(id)
        val left = players.remove(id) ?: return
        if (_state.value.phase == Phase.PLAYING) {
            engine = null
            broadcast(ServerMsg.Aborted("${left.name} left - game cancelled"))
        }
        broadcastLobby()
    }

    // ---------- game flow ----------

    private suspend fun handle(pid: Int, msg: ClientMsg) {
        val e = engine ?: return
        if (_state.value.phase != Phase.PLAYING) return
        when (msg) {
            is ClientMsg.Pick -> {
                if (!e.pick(pid, msg.chitId)) return
                if (e.roundReady()) e.resolveRound()      // everyone passed -> rotate simultaneously
                sendStates()
            }
            ClientMsg.Declare -> {
                val obj = e.winningObject(pid)
                if (obj == null) send(pid, ServerMsg.Rejected("That's not a winning hand!"))
                else broadcast(ServerMsg.GameOver(pid, players.getValue(pid).name, obj))
            }
            is ClientMsg.Join -> Unit
        }
    }

    override fun startGame() { scope.launch { mutex.withLock { beginGame() } } }
    override fun playAgain() = startGame()

    private suspend fun beginGame() {
        if (_state.value.phase == Phase.PLAYING) return
        if (players.size !in MIN..MAX) { dispatch(ServerMsg.Rejected("Need $MIN-$MAX players")); return }
        engine = GameEngine(players.keys.toList())
        sendStates()
    }

    override fun pick(chitId: Int) { scope.launch { mutex.withLock { handle(HOST_ID, ClientMsg.Pick(chitId)) } } }
    override fun declare() { scope.launch { mutex.withLock { handle(HOST_ID, ClientMsg.Declare) } } }

    override fun close() {
        _state.update { it.copy(phase = Phase.CLOSED) }
        val srv = server; server = null
        Thread { runCatching { srv?.stop(200, 500) } }.start()   // stop() blocks; keep it off the main thread
    }

    // ---------- sending ----------

    private suspend fun sendStates() {
        val e = engine ?: return
        e.seats.forEach { pid ->
            val view = GameView(
                hand = e.hand(pid),
                receiveFrom = players[e.previousOf(pid)]?.name ?: "?",
                passTo = players[e.nextOf(pid)]?.name ?: "?",
                round = e.round,
                selectedChitId = e.selected(pid),
                readyCount = e.readyCount,
                total = e.n
            )
            send(pid, ServerMsg.State(view))
        }
    }

    private suspend fun broadcast(m: ServerMsg) = players.keys.toList().forEach { send(it, m) }
    private suspend fun broadcastLobby() = broadcast(ServerMsg.Lobby(players.values.toList()))

    private suspend fun send(pid: Int, m: ServerMsg) {
        if (pid == HOST_ID) dispatch(m) else sockets[pid]?.let { sendRaw(it, m) }
    }

    private suspend fun sendRaw(s: WebSocketServerSession, m: ServerMsg) {
        runCatching { s.send(Frame.Text(WireJson.encodeToString(ServerMsg.serializer(), m))) }
    }
}
