package com.example.chitgame.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chitgame.model.Fruits
import com.example.chitgame.model.ServerMsg
import com.example.chitgame.net.GameHost
import com.example.chitgame.net.GameSession
import com.example.chitgame.net.NetUtil
import com.example.chitgame.net.SessionState
import kotlinx.coroutines.delay

// ---------------- Home ----------------

@Composable
fun HomeScreen(vm: GameViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var joining by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text("📜 Chit Pass", style = MaterialTheme.typography.displaySmall)
        Text("Collect every chit of one kind. Pass the rest. Be quick!", textAlign = TextAlign.Center)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(16) },
            label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        if (!joining) {
            Button(
                onClick = { vm.createRoom(name) }, enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Create Room (Host)") }
            Text("Turn your hotspot ON first, then tap Create Room.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { joining = true; vm.startDiscovery() }, enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Join Room") }
        } else {
            JoinPanel(vm, name, onBack = { joining = false; vm.stopDiscovery() })
        }
    }
}

@Composable
private fun JoinPanel(vm: GameViewModel, name: String, onBack: () -> Unit) {
    val rooms by vm.rooms.collectAsState()
    var manualIp by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("1. Connect this phone to the host's hotspot in Wi-Fi settings.\n2. Pick the room below.",
            style = MaterialTheme.typography.bodyMedium)
        Text("Rooms found", style = MaterialTheme.typography.titleMedium)
        if (rooms.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Searching...")
            }
        }
        rooms.forEach { r ->
            ElevatedCard(onClick = { vm.joinRoom(name, r.host, r.port) }, modifier = Modifier.fillMaxWidth()) {
                ListItem(headlineContent = { Text(r.name) }, supportingContent = { Text("${r.host}:${r.port}") })
            }
        }
        HorizontalDivider()
        Text("Not showing up? (Some phones block mDNS on hotspots)", style = MaterialTheme.typography.bodySmall)
        FilledTonalButton(
            onClick = { vm.hotspotGateway()?.let { vm.joinRoom(name, it) } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Auto-join hotspot host") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manualIp, onValueChange = { manualIp = it }, singleLine = true,
                label = { Text("Host IP") }, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Button(onClick = { vm.joinRoom(name, manualIp) }, enabled = manualIp.isNotBlank()) { Text("Join") }
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

// ---------------- Lobby ----------------

@Composable
fun LobbyScreen(session: GameSession, st: SessionState, onLeave: () -> Unit) {
    val count = st.players.size
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(st.roomName.ifBlank { "Connecting..." }, style = MaterialTheme.typography.headlineMedium)

        if (st.isHost) {
            val ips by produceState(emptyList<String>()) { while (true) { value = NetUtil.localIpv4(); delay(3000) } }
            Text(
                if (ips.isEmpty()) "No hotspot/Wi-Fi address yet - turn your hotspot on."
                else "Host IP: ${ips.joinToString()}  (port 8080)",
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (!st.connected) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("Connecting...")
            }
        }

        Text("Players ($count/${GameHost.MAX}) - seating order = passing order",
            style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(st.players) { i, p ->
                ListItem(
                    leadingContent = { Text("${i + 1}", fontSize = 18.sp) },
                    headlineContent = {
                        Text(p.name + if (p.id == GameHost.HOST_ID) " (host)" else if (p.id == st.myId) " (you)" else "")
                    }
                )
            }
        }

        if (st.isHost) {
            Button(
                onClick = session::startGame, enabled = count in GameHost.MIN..GameHost.MAX,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (count < GameHost.MIN) "Need ${GameHost.MIN - count} more player(s)" else "Start Game")
            }
        } else if (st.connected) Text("Waiting for the host to start...")
        TextButton(onClick = onLeave, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Leave") }
    }
}

// ---------------- Result / Closed ----------------

@Composable
fun ResultScreen(session: GameSession, st: SessionState, r: ServerMsg.GameOver, onLeave: () -> Unit) {
    val iWon = r.winnerId == st.myId
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Text(if (iWon) "🏆 You won!" else "🏁 Game over", style = MaterialTheme.typography.displaySmall)
        Text("${r.winnerName} collected all the", style = MaterialTheme.typography.titleMedium)
        Text("${Fruits.emoji(r.obj)} ${r.obj}", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        if (st.isHost) {
            Button(onClick = session::playAgain, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Play Again") }
        } else Text("Waiting for the host to start another round...")
        TextButton(onClick = onLeave) { Text("Leave room") }
    }
}

@Composable
fun ClosedScreen(onLeave: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Text("Disconnected", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onLeave) { Text("Back to home") }
    }
}
