package com.example.chess.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

/** A game another tablet is hosting on the same Wi-Fi, as heard from its beacon. */
class FoundHost(val ip: String, val port: Int, val kind: String, val avatar: Int, val sid: String, var lastSeen: Long)

/**
 * Host side of Wi-Fi discovery. Once a second the host shouts a small beacon to the whole home network: game, version,
 * port and a random per-game id. It says nothing about the picture code.
 */
class BeaconSender(private val targets: List<InetAddress>, private val udpPort: Int = Nearby.BEACON_PORT) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start(payload: () -> String) {
        if (running) return
        running = true
        thread = Thread({
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                while (running) {
                    val data = payload().toByteArray(Charsets.UTF_8)
                    for (t in targets) {
                        try { socket.send(DatagramPacket(data, data.size, t, udpPort)) } catch (_: Exception) { }
                    }
                    Thread.sleep(1000)
                }
            } catch (_: Exception) {
            } finally {
                try { socket?.close() } catch (_: Exception) { }
            }
        }, "nearby-beacon-send").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
    }

    companion object {
        fun payload(kind: String, port: Int, sid: String, avatar: Int): String =
            Wire.msg(Nearby.GAME_ID, Nearby.VERSION, kind, port, sid, avatar)

        /** The limited broadcast address, plus the broadcast address of every network that is up (some Android builds need those). */
        fun broadcastTargets(): List<InetAddress> {
            val out = ArrayList<InetAddress>()
            try { out.add(InetAddress.getByName("255.255.255.255")) } catch (_: Exception) { }
            try {
                for (ni in NetworkInterface.getNetworkInterfaces()) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (ia in ni.interfaceAddresses) ia.broadcast?.let { out.add(it) }
                }
            } catch (_: Exception) { }
            return out
        }

        /** This tablet's address on the home network (what a grown-up can type in if finding games doesn't work), or null. */
        fun localAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses.map { a -> a.address } }
                .filterIsInstance<Inet4Address>()
                .sortedByDescending { it.isSiteLocalAddress }
                .firstOrNull()?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}

class BeaconReceiver(private val udpPort: Int = Nearby.BEACON_PORT) {
    private val found = ConcurrentHashMap<String, FoundHost>()
    @Volatile private var running = false
    private var socket: DatagramSocket? = null

    fun start() {
        if (running) return
        running = true
        Thread({
            try {
                val s = DatagramSocket(null)
                s.reuseAddress = true
                s.bind(InetSocketAddress(udpPort))
                s.soTimeout = 500
                socket = s
                val buffer = ByteArray(512)
                while (running) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        s.receive(packet)
                        val ip = packet.address.hostAddress ?: continue
                        parse(String(packet.data, 0, packet.length, Charsets.UTF_8), ip)?.let { found["${it.ip}:${it.port}"] = it }
                    } catch (_: java.net.SocketTimeoutException) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { socket?.close() } catch (_: Exception) { }
            }
        }, "nearby-beacon-recv").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) { }
        found.clear()
    }

    /** Games heard in the last few seconds, newest game first. */
    fun hosts(now: Long = System.currentTimeMillis()): List<FoundHost> =
        found.values.filter { now - it.lastSeen < 5000 }.sortedWith(compareBy({ it.ip }, { it.port }))

    companion object {
        fun parse(text: String, ip: String): FoundHost? {
            val p = Wire.parse(text)
            if (p.size < 6 || p[0] != Nearby.GAME_ID || p[1].toIntOrNull() != Nearby.VERSION) return null
            val port = p[3].toIntOrNull() ?: return null
            val avatar = (p[5].toIntOrNull() ?: 0).coerceIn(0, PairCode.AVATARS.size - 1)
            return FoundHost(ip, port, p[2], avatar, p[4], System.currentTimeMillis())
        }
    }
}
