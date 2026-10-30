package com.example.chitgame.game

import com.example.chitgame.model.Chit
import com.example.chitgame.model.Fruits
import kotlin.random.Random

/**
 * Pure, host-side game logic. No networking, no Android.
 *
 * N players -> N objects -> N chits per object -> N*N chits; every player is dealt N.
 * `seats` is the circle order: seats[i] passes to seats[(i+1) % N].
 */
class GameEngine(val seats: List<Int>, rng: Random = Random.Default) {
    val n = seats.size
    val objects: List<String> = Fruits.names.shuffled(rng).take(n)

    private val hands: Map<Int, MutableList<Chit>> = seats.associateWith { mutableListOf() }
    private val picks = LinkedHashMap<Int, Int>()   // playerId -> chitId chosen this round

    var round = 1
        private set

    init {
        require(n >= 2) { "Need at least 2 seats" }
        var id = 0
        val deck = buildList {
            objects.forEach { o -> repeat(n) { add(Chit(id++, o)) } }
        }.shuffled(rng)
        seats.forEachIndexed { i, pid -> hands.getValue(pid).addAll(deck.subList(i * n, (i + 1) * n)) }
    }

    fun hand(pid: Int): List<Chit> = hands.getValue(pid).toList()
    fun nextOf(pid: Int): Int = seats[(seats.indexOf(pid) + 1) % n]
    fun previousOf(pid: Int): Int = seats[(seats.indexOf(pid) - 1 + n) % n]
    fun selected(pid: Int): Int? = picks[pid]
    val readyCount: Int get() = picks.size
    fun roundReady(): Boolean = picks.size == n

    /** Records a player's chit for this round. One pick per player per round. */
    fun pick(pid: Int, chitId: Int): Boolean {
        if (pid in picks) return false
        if (hands[pid]?.any { it.id == chitId } != true) return false
        picks[pid] = chitId
        return true
    }

    /** All players pass simultaneously: remove everything first, then add. */
    fun resolveRound() {
        check(roundReady())
        val moving = seats.map { pid -> hands.getValue(pid).first { it.id == picks.getValue(pid) } }
        seats.forEachIndexed { i, pid -> hands.getValue(pid).remove(moving[i]) }
        seats.forEachIndexed { i, _ -> hands.getValue(seats[(i + 1) % n]).add(moving[i]) }
        picks.clear()
        round++
    }

    /** Returns the object name if the player holds N chits of one kind, else null. */
    fun winningObject(pid: Int): String? {
        val h = hands.getValue(pid)
        return h.map { it.obj }.distinct().singleOrNull()?.takeIf { h.size == n }
    }
}
