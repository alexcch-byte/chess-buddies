package com.example.chess.net

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Runs two [NearbySession]s against each other over real sockets on this computer, with a clock the test can push forward. */
class NearbySessionTest {
    private val t0 = System.nanoTime()
    private var skew = 0L
    private fun now() = (System.nanoTime() - t0) / 1_000_000 + skew

    private val code = listOf(1, 4, 7, 9)
    private val wrongCode = listOf(0, 2, 3, 5)
    private val listener = TcpListener(0)
    private val toClose = ArrayList<NearbySession>()

    @After
    fun tearDown() {
        toClose.forEach { it.leave() }
        listener.stop()
    }

    private class Inbox(session: NearbySession) {
        val events = CopyOnWriteArrayList<NearbySession.Event>()
        init { session.attach { events.add(it) } }
        inline fun <reified E : NearbySession.Event> has() = events.any { it is E }
        inline fun <reified E : NearbySession.Event> count() = events.count { it is E }
        inline fun <reified E : NearbySession.Event> first(): E = events.filterIsInstance<E>().first()
    }

    private fun host(hostIsFirst: Boolean = true, kind: String = Nearby.CHESS) =
        NearbySession.host(kind, hostIsFirst, code, listener).also { toClose += it }

    private fun guest(with: List<Int> = code, port: Int = listener.port, linkOut: AtomicReference<Link?>? = null) =
        NearbySession.guest(with) { cb ->
            tcpConnect("127.0.0.1", port) { l ->
                linkOut?.set(l)
                cb(l)
            }
        }.also { toClose += it }

    private fun pump(vararg sessions: NearbySession, timeoutMs: Long = 8000, until: () -> Boolean) {
        val end = System.nanoTime() + timeoutMs * 1_000_000
        while (!until()) {
            val n = now()
            for (s in sessions) s.update(n)
            if (System.nanoTime() > end) fail("timed out waiting")
            Thread.sleep(2)
        }
    }

    /** Keep both running for a moment, for things that should NOT happen. */
    private fun settle(vararg sessions: NearbySession, ms: Long = 200) {
        val end = System.nanoTime() + ms * 1_000_000
        while (System.nanoTime() < end) {
            val n = now()
            for (s in sessions) s.update(n)
            Thread.sleep(2)
        }
    }

    private fun joined(h: NearbySession, g: NearbySession): Pair<Inbox, Inbox> {
        val hi = Inbox(h)
        val gi = Inbox(g)
        pump(h, g) { hi.has<NearbySession.Event.Joined>() && gi.has<NearbySession.Event.Joined>() }
        return hi to gi
    }

    @Test
    fun correctCodeJoinsAndTheGuestLearnsGameAndSide() {
        val h = host(hostIsFirst = true, kind = Nearby.CHECKERS)
        val g = guest()
        val (hi, gi) = joined(h, g)
        assertTrue(h.live && g.live)
        assertEquals(true, hi.first<NearbySession.Event.Joined>().iAmFirst)
        assertEquals(false, gi.first<NearbySession.Event.Joined>().iAmFirst)
        assertEquals(Nearby.CHECKERS, gi.first<NearbySession.Event.Joined>().kind)
        assertEquals(Nearby.CHECKERS, g.kind)
    }

    @Test
    fun hostCanPlayTheSecondSide() {
        val h = host(hostIsFirst = false)
        val g = guest()
        val (hi, gi) = joined(h, g)
        assertEquals(false, hi.first<NearbySession.Event.Joined>().iAmFirst)
        assertEquals(true, gi.first<NearbySession.Event.Joined>().iAmFirst)
    }

    @Test
    fun theOrderPicturesAreTappedInDoesNotMatter() {
        val h = host()
        val g = guest(with = code.reversed())
        joined(h, g)
        assertTrue(g.live)
    }

    @Test
    fun wrongCodeIsRefusedAndTheHostKeepsWaiting() {
        val h = host()
        val hi = Inbox(h)
        val bad = guest(with = wrongCode)
        val bi = Inbox(bad)
        pump(h, bad) { bi.has<NearbySession.Event.Rejected>() }
        assertEquals("code", bi.first<NearbySession.Event.Rejected>().reason)
        assertEquals(NearbySession.State.CLOSED, bad.state)
        assertEquals(NearbySession.State.WAITING, h.state)
        assertFalse(hi.has<NearbySession.Event.Joined>())

        // A friend with the right pictures can still join afterwards.
        val good = guest()
        val gi = Inbox(good)
        pump(h, good) { gi.has<NearbySession.Event.Joined>() }
        assertTrue(h.live)
    }

    @Test
    fun tooManyWrongCodesLockJoiningUntilItIsOver() {
        val h = host()
        repeat(Nearby.MAX_BAD_TRIES) {
            val bad = guest(with = wrongCode)
            val bi = Inbox(bad)
            pump(h, bad) { bi.has<NearbySession.Event.Rejected>() }
            assertEquals("code", bi.first<NearbySession.Event.Rejected>().reason)
        }
        // Even the right pictures are turned away while locked.
        val locked = guest()
        val li = Inbox(locked)
        pump(h, locked) { li.has<NearbySession.Event.Rejected>() }
        assertEquals("wait", li.first<NearbySession.Event.Rejected>().reason)
        assertEquals(NearbySession.State.WAITING, h.state)

        skew += Nearby.LOCKOUT_MS + 1000
        val later = guest()
        val ai = Inbox(later)
        pump(h, later) { ai.has<NearbySession.Event.Joined>() }
        assertTrue(h.live)
    }

    @Test
    fun differentProtocolVersionIsRefused() {
        val h = host()
        val raw = AtomicReference<Link?>(null)
        tcpConnect("127.0.0.1", listener.port) { raw.set(it) }
        var hello: String? = null
        pump(h) {
            val l = raw.get()
            if (l != null && hello == null) hello = l.poll()
            hello != null
        }
        assertTrue(hello!!.startsWith("hello|${Nearby.VERSION}|chess|"))
        raw.get()!!.send(Wire.msg("join", 99, "whatever", ""))
        var reply: String? = null
        pump(h) {
            reply = raw.get()!!.poll()
            reply != null
        }
        assertEquals("reject|version", reply)
    }

    @Test
    fun aThirdTabletIsToldTheGameIsFull() {
        val h = host()
        val a = guest()
        joined(h, a)
        val b = guest()
        val bi = Inbox(b)
        pump(h, a, b) { bi.has<NearbySession.Event.Rejected>() }
        assertEquals("busy", bi.first<NearbySession.Event.Rejected>().reason)
        assertTrue(h.live && a.live)
    }

    @Test
    fun movesTravelBothWays() {
        val h = host()
        val g = guest()
        val (hi, gi) = joined(h, g)
        h.sendMove(1, "e2e4")
        pump(h, g) { gi.has<NearbySession.Event.Move>() }
        assertEquals(NearbySession.Event.Move(1, "e2e4"), gi.first<NearbySession.Event.Move>())
        g.sendMove(2, "e7e5")
        pump(h, g) { hi.has<NearbySession.Event.Move>() }
        assertEquals(NearbySession.Event.Move(2, "e7e5"), hi.first<NearbySession.Event.Move>())
    }

    @Test
    fun movesThatArriveWhileNobodyListensWaitForTheNextListener() {
        val h = host()
        val g = guest()
        val hi = Inbox(h)
        val lobby = CopyOnWriteArrayList<NearbySession.Event>()
        // Like the lobby screen: it takes the Joined event, then steps aside for the match screen.
        g.attach { e ->
            lobby.add(e)
            if (e is NearbySession.Event.Joined) g.attach(null)
        }
        pump(h, g) { hi.has<NearbySession.Event.Joined>() && lobby.any { it is NearbySession.Event.Joined } }
        h.sendMove(1, "d2d4")
        settle(h, g)
        assertEquals(1, lobby.size)

        val match = CopyOnWriteArrayList<NearbySession.Event>()
        g.attach { match.add(it) }
        assertTrue(match.any { it == NearbySession.Event.Move(1, "d2d4") })
    }

    @Test
    fun aQuietTabletIsNoticedAndAReturningOneResumesWithItsMoves() {
        val h = host()
        val linkOut = AtomicReference<Link?>(null)
        val g = guest(linkOut = linkOut)
        h.moveSource = { listOf("e2e4", "e7e5", "g1f3") }
        g.moveSource = { listOf("e2e4", "e7e5") }
        val (hi, gi) = joined(h, g)
        settle(h, g)

        // The guest's connection dies (walked out of range).
        linkOut.get()!!.close()
        pump(h, g) { hi.has<NearbySession.Event.Lost>() && gi.has<NearbySession.Event.Lost>() }
        assertEquals(NearbySession.State.LOST, h.state)
        assertEquals(NearbySession.State.LOST, g.state)

        // It tries again after a couple of seconds and is let back in without any new picture code.
        skew += Nearby.RETRY_MS + 500
        pump(h, g) { hi.has<NearbySession.Event.Resumed>() && gi.has<NearbySession.Event.Resumed>() }
        assertTrue(h.live && g.live)
        assertEquals(1, hi.count<NearbySession.Event.Joined>())
        // Each side was told the other's full list so it can catch up.
        pump(h, g) { hi.has<NearbySession.Event.Sync>() && gi.has<NearbySession.Event.Sync>() }
        assertEquals(listOf("e2e4", "e7e5"), hi.events.filterIsInstance<NearbySession.Event.Sync>().last().moves)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), gi.events.filterIsInstance<NearbySession.Event.Sync>().last().moves)
    }

    @Test
    fun silenceForTooLongCountsAsLost() {
        val h = host()
        val g = guest()
        val (hi, _) = joined(h, g)
        settle(h, g)
        // Only the host keeps running; the guest's heartbeats stop.
        skew += Nearby.LOST_AFTER_MS + 1000
        h.update(now())
        assertTrue(hi.has<NearbySession.Event.Lost>())
        assertEquals(NearbySession.State.LOST, h.state)
    }

    @Test
    fun playingAgainNeedsBothPlayersAndSwapsSides() {
        val h = host(hostIsFirst = true)
        val g = guest()
        val (hi, gi) = joined(h, g)

        g.wantRematch()
        pump(h, g) { hi.has<NearbySession.Event.RematchAsked>() }
        assertFalse(hi.has<NearbySession.Event.NewGame>())
        h.wantRematch()
        pump(h, g) { hi.has<NearbySession.Event.NewGame>() && gi.has<NearbySession.Event.NewGame>() }
        assertEquals(false, hi.first<NearbySession.Event.NewGame>().iAmFirst)
        assertEquals(true, gi.first<NearbySession.Event.NewGame>().iAmFirst)
        assertEquals(false, h.iAmFirst)
        assertEquals(true, g.iAmFirst)

        // And back again the next time.
        h.wantRematch()
        pump(h, g) { gi.has<NearbySession.Event.RematchAsked>() }
        g.wantRematch()
        pump(h, g) { hi.count<NearbySession.Event.NewGame>() == 2 && gi.count<NearbySession.Event.NewGame>() == 2 }
        assertEquals(true, h.iAmFirst)
    }

    @Test
    fun aRematchCanBeTurnedDown() {
        val h = host()
        val g = guest()
        val (hi, gi) = joined(h, g)
        g.wantRematch()
        pump(h, g) { hi.has<NearbySession.Event.RematchAsked>() }
        h.declineRematch()
        pump(h, g) { gi.has<NearbySession.Event.RematchDeclined>() }
        settle(h, g)
        assertFalse(gi.has<NearbySession.Event.NewGame>() || hi.has<NearbySession.Event.NewGame>())
    }

    @Test
    fun leavingTellsTheFriend() {
        val h = host()
        val g = guest()
        val (hi, _) = joined(h, g)
        g.leave()
        pump(h) { hi.has<NearbySession.Event.FriendLeft>() }
        assertEquals(NearbySession.State.CLOSED, h.state)
        assertEquals(NearbySession.State.CLOSED, g.state)
    }

    @Test
    fun aTabletCanAskTheOtherForItsWholeListOfMoves() {
        val h = host()
        val g = guest()
        val (_, gi) = joined(h, g)
        h.moveSource = { listOf("a2a3") }
        g.requestSync()
        pump(h, g) { gi.events.any { it is NearbySession.Event.Sync && it.moves == listOf("a2a3") } }
    }

    @Test
    fun guestGivesUpWhenNobodyIsThere() {
        val dead = TcpListener(0)
        val port = dead.port
        dead.stop()
        val g = guest(port = port)
        val gi = Inbox(g)
        var guard = 0
        pump(g) {
            if (guard++ % 25 == 0) skew += Nearby.RETRY_MS + 100
            gi.has<NearbySession.Event.CouldNotConnect>()
        }
        assertEquals(NearbySession.State.CLOSED, g.state)
    }
}
