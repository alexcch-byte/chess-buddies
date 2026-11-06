package com.example.chess.net

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * A two-way connection that carries one line of text per message. Wi-Fi and Bluetooth both end up as a [Link], so the
 * game code never knows which one it is using. Nothing here blocks: [poll] returns null when nothing has arrived.
 */
interface Link {
    val alive: Boolean
    fun send(line: String)
    /** The next line that has arrived, or null. */
    fun poll(): String?
    /** Sends everything already queued, then hangs up. */
    fun close()
}

/** Something that waits for friends to connect (a TCP port, or a Bluetooth service). */
interface Listener {
    /** The TCP port, or 0 when this isn't TCP. */
    val port: Int
    fun poll(): Link?
    fun stop()
}

/** A [Link] over any pair of streams. Reading and writing happen on their own threads so the screen never waits on the network. */
class StreamLink(input: InputStream, output: OutputStream, private val onClose: () -> Unit) : Link {
    private val inbox = ConcurrentLinkedQueue<String>()
    private val outbox = LinkedBlockingQueue<String>()
    /** Queued after the last real message to tell the writer to hang up. (Compared by identity; a queue can't hold null.) */
    private val end = String(charArrayOf('\u0000'))
    @Volatile private var closed = false

    init {
        Thread({
            try {
                val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    inbox.add(line)
                }
            } catch (_: Exception) {
            }
            hangUp()
        }, "nearby-read").also { it.isDaemon = true; it.start() }

        Thread({
            try {
                while (true) {
                    val line = outbox.take()
                    if (line === end) break
                    output.write((line + "\n").toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            } catch (_: Exception) {
            }
            hangUp()
        }, "nearby-write").also { it.isDaemon = true; it.start() }
    }

    override val alive: Boolean get() = !closed
    override fun send(line: String) {
        if (!closed) outbox.add(line)
    }
    override fun poll(): String? = inbox.poll()

    override fun close() {
        if (closed) return
        closed = true
        outbox.add(end)
    }

    private fun hangUp() {
        closed = true
        outbox.add(end)
        try { onClose() } catch (_: Exception) { }
    }
}

// ---------------------------------------------------------------- TCP (Wi-Fi)

fun tcpLink(socket: Socket): Link {
    socket.tcpNoDelay = true
    try { socket.keepAlive = true } catch (_: Exception) { }
    return StreamLink(socket.getInputStream(), socket.getOutputStream()) { try { socket.close() } catch (_: Exception) { } }
}

class TcpListener(requestedPort: Int) : Listener {
    private val server = ServerSocket().also { it.reuseAddress = true; it.bind(InetSocketAddress(requestedPort)) }
    private val queue = ConcurrentLinkedQueue<Link>()
    @Volatile private var stopped = false
    override val port: Int get() = server.localPort

    init {
        Thread({
            while (!stopped) {
                try {
                    queue.add(tcpLink(server.accept()))
                } catch (_: Exception) {
                    if (stopped) break
                }
            }
        }, "nearby-accept").also { it.isDaemon = true; it.start() }
    }

    override fun poll(): Link? = queue.poll()
    override fun stop() {
        stopped = true
        try { server.close() } catch (_: Exception) { }
    }
}

/** Connects in the background; [done] gets the link, or null if the other tablet could not be reached. */
fun tcpConnect(host: String, port: Int, done: (Link?) -> Unit) {
    Thread({
        val link = try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 3000)
            tcpLink(s)
        } catch (_: Exception) {
            null
        }
        done(link)
    }, "nearby-connect").also { it.isDaemon = true; it.start() }
}
