package com.example.chitgame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chitgame.net.GameSession
import com.example.chitgame.net.Phase
import com.example.chitgame.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { App() } } }
    }
}

@Composable
fun App(vm: GameViewModel = viewModel()) {
    val session by vm.session.collectAsState()
    val s = session
    KeepScreenOn()                                   // host server must stay alive during play
    BackHandler(enabled = s != null) { vm.leave() }
    if (s == null) HomeScreen(vm) else SessionScreen(s, onLeave = vm::leave)
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
}

@Composable
private fun SessionScreen(session: GameSession, onLeave: () -> Unit) {
    val st by session.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(st.message) {
        st.message?.let { snackbar.showSnackbar(it); session.clearMessage() }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        androidx.compose.foundation.layout.Box(Modifier.padding(pad)) {
            val game = st.game
            val result = st.result
            when {
                st.phase == Phase.CLOSED -> ClosedScreen(onLeave)
                st.phase == Phase.PLAYING && game != null -> GameScreen(session, game)
                st.phase == Phase.RESULT && result != null -> ResultScreen(session, st, result, onLeave)
                else -> LobbyScreen(session, st, onLeave)
            }
        }
    }
}
