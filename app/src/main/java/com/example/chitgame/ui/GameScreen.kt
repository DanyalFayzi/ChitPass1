package com.example.chitgame.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chitgame.model.Chit
import com.example.chitgame.model.Fruits
import com.example.chitgame.model.GameView
import com.example.chitgame.net.GameSession

@Composable
fun GameScreen(session: GameSession, view: GameView) {
    // Local, not-yet-sent selection. Resets automatically when the round advances.
    var selected by remember(view.round) { mutableStateOf<Int?>(null) }
    val locked = view.selectedChitId != null                      // already passed this round
    val hand = remember(view.hand) { view.hand.sortedWith(compareBy({ it.obj }, { it.id })) }
    val canDeclare = hand.isNotEmpty() && hand.map { it.obj }.distinct().size == 1

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CircleHeader(view)
        Text(
            "Round ${view.round}  -  ${view.readyCount}/${view.total} have passed",
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(92.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 14.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(hand, key = { it.id }) { chit ->
                val inTransit = locked && view.selectedChitId == chit.id
                ChitCard(
                    chit = chit,
                    selected = inTransit || (!locked && selected == chit.id),
                    enabled = !locked,
                    faded = inTransit,
                    onClick = { selected = if (selected == chit.id) null else chit.id }
                )
            }
        }

        Button(
            onClick = { selected?.let(session::pick) },
            enabled = !locked && selected != null,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                when {
                    locked -> "Passed - waiting for others..."
                    selected == null -> "Tap a chit to pass"
                    else -> "Pass to ${view.passTo}  ➜"
                }
            )
        }

        Button(
            onClick = session::declare,
            enabled = canDeclare,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) { Text("DECLARE WIN!", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

/** from ➜ YOU ➜ to */
@Composable
private fun CircleHeader(view: GameView) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NeighbourCard("Receiving from", view.receiveFrom, Modifier.weight(1f))
        Text("➜", fontSize = 20.sp)
        Card(
            Modifier.weight(0.6f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text("YOU", Modifier.padding(vertical = 14.dp).fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        }
        Text("➜", fontSize = 20.sp)
        NeighbourCard("Passing to", view.passTo, Modifier.weight(1f))
    }
}

@Composable
private fun NeighbourCard(label: String, name: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** A folded paper slip. Selected chits lift up and get a green border. */
@Composable
private fun ChitCard(chit: Chit, selected: Boolean, enabled: Boolean, faded: Boolean, onClick: () -> Unit) {
    val lift by animateDpAsState(if (selected) (-10).dp else 0.dp, label = "lift")
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .offset(y = lift)
            .alpha(if (faded) 0.5f else 1f)
            .aspectRatio(0.8f)
            .shadow(if (selected) 10.dp else 3.dp, shape)
            .clip(shape)
            .background(Color(0xFFFFF8E1))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFF2E7D32) else Color(0xFFBCAAA4), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val s = size.width * 0.28f                                    // dog-eared corner
            val corner = Path().apply {
                moveTo(size.width - s, 0f); lineTo(size.width, s); lineTo(size.width - s, s); close()
            }
            drawPath(corner, Color(0xFFD7CCC8))
            drawLine(Color(0x22000000), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Fruits.emoji(chit.obj), fontSize = 34.sp)
            Text(chit.obj, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3E2723))
        }
    }
}
