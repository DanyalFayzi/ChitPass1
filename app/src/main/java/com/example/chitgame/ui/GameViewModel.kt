package com.example.chitgame.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chitgame.net.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val nsd = NsdHelper(app)
    private val _session = MutableStateFlow<GameSession?>(null)
    val session: StateFlow<GameSession?> = _session.asStateFlow()
    private val _rooms = MutableStateFlow<List<DiscoveredRoom>>(emptyList())
    val rooms: StateFlow<List<DiscoveredRoom>> = _rooms.asStateFlow()
    private var discoveryJob: Job? = null

    /** Turn the hotspot ON before calling this so NSD advertises on the right interface. */
    fun createRoom(playerName: String) {
        val room = "${playerName.trim().ifBlank { "Host" }}'s room"
        val host = GameHost(viewModelScope, playerName, room)
        host.start()
        nsd.register(room, host.port)
        _session.value = host
    }

    fun joinRoom(playerName: String, host: String, port: Int = 8080) {
        stopDiscovery()
        val c = GameClient(viewModelScope, playerName.trim().ifBlank { "Player" }, host.trim(), port)
        c.connect()
        _session.value = c
    }

    fun hotspotGateway(): String? = NetUtil.gatewayIp(getApplication())

    fun startDiscovery() {
        discoveryJob?.cancel()
        _rooms.value = emptyList()
        discoveryJob = viewModelScope.launch {
            nsd.discover().catch { }.collect { r ->
                _rooms.update { list -> list.filterNot { it.host == r.host } + r }
            }
        }
    }

    fun stopDiscovery() { discoveryJob?.cancel(); discoveryJob = null }

    fun leave() {
        _session.value?.close()
        nsd.unregister()
        _session.value = null
    }

    override fun onCleared() { stopDiscovery(); leave() }
}
