package com.example.chitgame.net

import com.example.chitgame.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameClient(
    private val scope: CoroutineScope,
    private val name: String,
    private val host: String,
    private val port: Int = 8080
) : GameSession(SessionState(isHost = false)) {

    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
        }
        install(WebSockets) { 
            pingInterval = 15_000 
        }
    }
    
    @Volatile private var ws: DefaultClientWebSocketSession? = null
    private var job: Job? = null

    fun connect() {
        job = scope.launch {
            try {
                http.webSocket(host = host, port = port, path = "/game") {
                    ws = this
                    send(Frame.Text(WireJson.encodeToString(ClientMsg.serializer(), ClientMsg.Join(name))))
                    for (f in incoming) {
                        if (f !is Frame.Text) continue
                        runCatching { WireJson.decodeFromString(ServerMsg.serializer(), f.readText()) }
                            .getOrNull()?.let(::dispatch)
                    }
                }
                _state.update { it.copy(phase = Phase.CLOSED, message = it.message ?: "Disconnected from host") }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _state.update { it.copy(phase = Phase.CLOSED, message = "Couldn't connect to $host: ${e.message}") }
            } finally { 
                ws = null 
            }
        }
    }

    private fun sendMsg(m: ClientMsg) {
        scope.launch { runCatching { ws?.send(Frame.Text(WireJson.encodeToString(ClientMsg.serializer(), m))) } }
    }
}
