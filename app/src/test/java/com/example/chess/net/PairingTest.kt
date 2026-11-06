package com.example.chess.net

import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {
    @Test
    fun aCodeHasFourDifferentPicturesInOrder() {
        repeat(200) {
            val code = PairCode.random(Random(it.toLong()))
            assertEquals(PairCode.SIZE, code.size)
            assertEquals(code.sorted(), code)
            assertEquals(PairCode.SIZE, code.toSet().size)
            assertTrue(code.all { i -> i in PairCode.PICTURES.indices })
        }
    }

    @Test
    fun theProofDependsOnTheCodeAndTheNumberButNotOnTheTappingOrder() {
        val a = listOf(1, 4, 7, 9)
        assertEquals(PairCode.proof("abc", a), PairCode.proof("abc", a.reversed()))
        assertNotEquals(PairCode.proof("abc", a), PairCode.proof("abd", a))
        assertNotEquals(PairCode.proof("abc", a), PairCode.proof("abc", listOf(1, 4, 7, 8)))
        // The proof never contains the code itself.
        assertTrue(!PairCode.proof("abc", a).contains("1,4,7,9"))
    }

    @Test
    fun messagesRoundTrip() {
        assertEquals(listOf("move", "12", "e7e8q"), Wire.parse(Wire.msg("move", 12, "e7e8q")))
        assertEquals(listOf("sync", ""), Wire.parse(Wire.msg("sync", "")))
    }

    @Test
    fun reconcileFollowsTheRules() {
        val a = listOf("e2e4", "e7e5")
        val b = listOf("e2e4", "e7e5", "g1f3")
        assertEquals(Reconcile.Keep, reconcile(a, a, iAmHost = true))
        assertEquals(Reconcile.Append(listOf("g1f3")), reconcile(a, b, iAmHost = true))
        assertEquals(Reconcile.Append(listOf("g1f3")), reconcile(a, b, iAmHost = false))
        assertEquals(Reconcile.Keep, reconcile(b, a, iAmHost = false))
        val other = listOf("d2d4", "d7d5")
        assertEquals(Reconcile.Keep, reconcile(a, other, iAmHost = true))
        assertEquals(Reconcile.Replace(other), reconcile(a, other, iAmHost = false))
        assertEquals(Reconcile.Append(listOf("e2e4")), reconcile(emptyList(), listOf("e2e4"), iAmHost = false))
    }

    @Test
    fun beaconTextIsUnderstood() {
        val text = BeaconSender.payload(Nearby.CHECKERS, 47791, "sid1", 3)
        val h = BeaconReceiver.parse(text, "192.168.1.20")!!
        assertEquals("192.168.1.20", h.ip)
        assertEquals(47791, h.port)
        assertEquals(Nearby.CHECKERS, h.kind)
        assertEquals(3, h.avatar)
        assertNull(BeaconReceiver.parse("someothergame|1|chess|1|x|0", "1.2.3.4"))
        assertNull(BeaconReceiver.parse("chessbuddies|99|chess|1|x|0", "1.2.3.4"))
        assertNull(BeaconReceiver.parse("garbage", "1.2.3.4"))
        assertEquals(PairCode.AVATARS.size - 1, BeaconReceiver.parse("chessbuddies|1|chess|1|x|999", "1.2.3.4")!!.avatar)
    }

    @Test
    fun aBeaconSentOverTheNetworkIsHeard() {
        val port = DatagramSocket(0).use { it.localPort }
        val receiver = BeaconReceiver(port)
        val sender = BeaconSender(listOf(InetAddress.getByName("127.0.0.1")), port)
        receiver.start()
        sender.start { BeaconSender.payload(Nearby.CHESS, 4242, "abc", 1) }
        try {
            val deadline = System.currentTimeMillis() + 6000
            while (receiver.hosts().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
            val hosts = receiver.hosts()
            assertEquals(1, hosts.size)
            assertNotNull(hosts.first())
            assertEquals(4242, hosts.first().port)
            assertEquals("127.0.0.1", hosts.first().ip)
        } finally {
            sender.stop()
            receiver.stop()
        }
    }
}
