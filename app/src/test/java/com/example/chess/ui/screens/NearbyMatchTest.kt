package com.example.chess.ui.screens

import com.example.chess.engine.Game
import com.example.chess.net.Link
import com.example.chess.net.Nearby
import com.example.chess.net.NearbySession
import com.example.chess.net.TcpListener
import com.example.chess.net.tcpConnect
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Two real chess games kept in step over a real connection, through the same glue the match screen uses. */
class NearbyMatchTest {
    private val t0 = System.nanoTime()
    private var skew = 0L
    private fun now() = (System.nanoTime() - t0) / 1_000_000 + skew

    private val code = listOf(2, 3, 5, 8)
    private val listener = TcpListener(0)
    private val sessions = ArrayList<NearbySession>()

    @After
    fun tearDown() {
        sessions.forEach { it.leave() }
        listener.stop()
    }

    /** A stand-in for a match controller: a real chess game, with no screen. */
    private class Side : NearbyGame {
        var game = Game()
        var changes = 0
        var lostCalls = 0
        lateinit var match: NearbyMatch

        override fun wires() = game.moves.map { it.uci() }
        override val isOver: Boolean get() = game.isOver

        override fun playRemote(wire: String, animate: Boolean): Boolean {
            val move = game.position.legalMoves().firstOrNull { it.uci() == wire } ?: return false
            game = game.play(move)
            return true
        }

        override fun rebuild(wires: List<String>): Boolean {
            var g = Game()
            for (w in wires) g = g.play(g.position.legalMoves().firstOrNull { it.uci() == w } ?: return false)
            game = g
            return true
        }

        override fun afterRemoteMoves() { changes++ }
        override fun onConnection(live: Boolean) { if (!live) lostCalls++ }
        override fun onRematchDeclined() {}
        override fun onNewGame(iAmFirst: Boolean) { game = Game() }
        override fun onFriendLeft() {}

        /** The player moves: it happens here, then the other tablet is told. */
        fun play(uci: String) {
            game = game.play(game.position.legalMoves().first { it.uci() == uci })
            match.sendMove(uci)
        }

        /** The same move, but it never leaves this tablet (as if it were lost on the way). */
        fun playUnsent(uci: String) {
            game = game.play(game.position.legalMoves().first { it.uci() == uci })
        }
    }

    private class Pair2(val host: NearbySession, val guest: NearbySession, val h: Side, val g: Side, val guestLink: AtomicReference<Link?>)

    private fun pump(vararg s: NearbySession, timeoutMs: Long = 8000, until: () -> Boolean) {
        val end = System.nanoTime() + timeoutMs * 1_000_000
        while (!until()) {
            val n = now()
            for (x in s) x.update(n)
            if (System.nanoTime() > end) fail("timed out waiting")
            Thread.sleep(2)
        }
    }

    private fun connected(): Pair2 {
        val host = NearbySession.host(Nearby.CHESS, true, code, listener).also { sessions += it }
        val linkOut = AtomicReference<Link?>(null)
        val guest = NearbySession.guest(code) { cb -> tcpConnect("127.0.0.1", listener.port) { linkOut.set(it); cb(it) } }.also { sessions += it }
        val hs = Side()
        val gs = Side()
        // Like the lobby does: take the Joined event, then hand the session to the game.
        var hostIn = false
        var guestIn = false
        host.attach { if (it is NearbySession.Event.Joined) { host.attach(null); hostIn = true } }
        guest.attach { if (it is NearbySession.Event.Joined) { guest.attach(null); guestIn = true } }
        pump(host, guest) { hostIn && guestIn }
        hs.match = NearbyMatch(host, hs)
        gs.match = NearbyMatch(guest, gs)
        return Pair2(host, guest, hs, gs, linkOut)
    }

    @Test
    fun aWholeGameStaysInStepOnBothTablets() {
        val p = connected()
        // Scholar's mate. White is the host.
        val script = listOf("e2e4" to true, "e7e5" to false, "f1c4" to true, "b8c6" to false, "d1h5" to true, "g8f6" to false, "h5f7" to true)
        for ((uci, byHost) in script) {
            val mover = if (byHost) p.h else p.g
            val other = if (byHost) p.g else p.h
            val before = other.wires().size
            mover.play(uci)
            pump(p.host, p.guest) { other.wires().size == before + 1 }
        }
        assertEquals(p.h.wires(), p.g.wires())
        assertTrue(p.h.isOver && p.g.isOver)
        assertEquals(true, p.g.game.outcome?.whiteWins)
    }

    @Test
    fun aMoveLostOnTheWayIsCaughtUpAfterReconnecting() {
        val p = connected()
        p.h.play("e2e4")
        pump(p.host, p.guest) { p.g.wires().size == 1 }
        p.g.play("e7e5")
        pump(p.host, p.guest) { p.h.wires().size == 2 }

        p.guestLink.get()!!.close()
        pump(p.host, p.guest) { !p.host.live && !p.guest.live }
        // The host's next move goes nowhere: the link is down.
        p.h.playUnsent("g1f3")
        assertEquals(3, p.h.wires().size)
        assertEquals(2, p.g.wires().size)

        skew += Nearby.RETRY_MS + 500
        pump(p.host, p.guest) { p.g.wires().size == 3 }
        assertEquals(p.h.wires(), p.g.wires())
        assertTrue(p.host.live && p.guest.live)

        // And play carries on from there.
        p.g.play("b8c6")
        pump(p.host, p.guest) { p.h.wires().size == 4 }
        assertEquals(p.h.wires(), p.g.wires())
    }

    @Test
    fun ifTheGamesDisagreeTheGuestFollowsTheHost() {
        val p = connected()
        p.h.play("e2e4")
        pump(p.host, p.guest) { p.g.wires().size == 1 }
        p.g.play("e7e5")
        pump(p.host, p.guest) { p.h.wires().size == 2 }

        p.guestLink.get()!!.close()
        pump(p.host, p.guest) { !p.host.live && !p.guest.live }
        // Both tablets made a different third move while apart.
        p.h.playUnsent("d2d4")
        p.g.playUnsent("g1f3")

        skew += Nearby.RETRY_MS + 500
        pump(p.host, p.guest) { p.host.live && p.guest.live && p.g.wires() == p.h.wires() }
        assertEquals(listOf("e2e4", "e7e5", "d2d4"), p.g.wires())
    }

    @Test
    fun aGapInTheMoveNumbersMakesTheOtherTabletCatchUp() {
        val p = connected()
        // The guest never heard move 1, then hears move 2 (as if the first had been lost): it asks for the whole list.
        p.h.playUnsent("e2e4")
        p.h.play("e7e5")
        pump(p.host, p.guest) { p.g.wires().size == 2 }
        assertEquals(p.h.wires(), p.g.wires())
        assertFalse(p.g.wires().isEmpty())
    }
}
