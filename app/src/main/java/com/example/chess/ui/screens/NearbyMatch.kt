package com.example.chess.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.chess.net.NearbySession
import com.example.chess.net.Reconcile
import com.example.chess.net.reconcile

/** What a running chess or checkers game has to offer so that moves from the other tablet can be played on it. */
interface NearbyGame {
    /** Every move played so far, by its wire name. */
    fun wires(): List<String>
    val isOver: Boolean
    /** Plays the other player's move. Returns false if it isn't legal here. */
    fun playRemote(wire: String, animate: Boolean): Boolean
    /** Throws the game away and replays [wires] from the start. Returns false if one of them isn't legal. */
    fun rebuild(wires: List<String>): Boolean
    /** Called once after one or more of the other player's moves have been played. */
    fun afterRemoteMoves()
    fun onConnection(live: Boolean)
    fun onRematchDeclined()
    fun onNewGame(iAmFirst: Boolean)
    fun onFriendLeft()
}

/**
 * Glue between a [NearbySession] and a running game: sends this player's moves, and plays the friend's moves in order,
 * catching up (or asking the friend to) when the two tablets have drifted apart, for example after a dropout.
 */
class NearbyMatch(private val session: NearbySession, private val game: NearbyGame) {
    /** False while the other tablet can't be reached. */
    var linkLive by mutableStateOf(session.live)
        private set
    /** A question from the friend that needs a yes or a no, such as "play again?". */
    var request by mutableStateOf<String?>(null)
        private set
    var friendLeft by mutableStateOf(false)
        private set

    init {
        session.moveSource = game::wires
        session.attach(::onEvent)
    }

    fun sendMove(wire: String) = session.sendMove(game.wires().size, wire)

    fun askRematch() = session.wantRematch()

    fun answerRequest(yes: Boolean) {
        request = null
        if (yes) session.wantRematch() else session.declineRematch()
    }

    fun leave() {
        session.attach(null)
        session.leave()
    }

    private fun onEvent(e: NearbySession.Event) {
        when (e) {
            is NearbySession.Event.Move -> {
                val have = game.wires().size
                when {
                    game.isOver || e.n <= have -> {}
                    e.n > have + 1 -> session.requestSync()
                    game.playRemote(e.wire, animate = true) -> game.afterRemoteMoves()
                    else -> session.requestSync()
                }
            }
            is NearbySession.Event.Sync -> when (val r = reconcile(game.wires(), e.moves, session.isHost)) {
                Reconcile.Keep -> {}
                is Reconcile.Append -> {
                    val played = r.moves.withIndex().all { (i, w) -> game.playRemote(w, animate = r.moves.size == 1 && i == 0) }
                    if (played) game.afterRemoteMoves() else session.requestSync()
                }
                is Reconcile.Replace -> if (game.rebuild(r.moves)) game.afterRemoteMoves()
            }
            NearbySession.Event.Lost -> {
                linkLive = false
                game.onConnection(false)
            }
            NearbySession.Event.Resumed -> {
                linkLive = true
                game.onConnection(true)
            }
            NearbySession.Event.RematchAsked -> request = "Your friend wants to play again!"
            NearbySession.Event.RematchDeclined -> game.onRematchDeclined()
            is NearbySession.Event.NewGame -> {
                request = null
                game.onNewGame(e.iAmFirst)
            }
            NearbySession.Event.FriendLeft -> {
                friendLeft = true
                game.onFriendLeft()
            }
            else -> {}
        }
    }
}
