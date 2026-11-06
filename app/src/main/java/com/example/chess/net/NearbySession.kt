package com.example.chess.net

import java.util.concurrent.atomic.AtomicReference

/**
 * One game between two tablets, from either side. The host waits for a friend and owns the pairing code; the guest connects
 * and proves it knows the code. After that the two are equals: each tablet keeps its own copy of the game and they swap moves.
 *
 * This class knows nothing about chess or checkers. A move is just a short piece of text (its "wire" name) and a move number.
 * It is driven from one thread by calling [update] about twenty times a second, and reports what happened through [Event]s.
 * The clock is passed in, so tests can run it with a fake one.
 */
class NearbySession private constructor(
    val isHost: Boolean,
    kind: String,
    private val code: List<Int>,
    hostIsFirst: Boolean,
    private val listener: Listener?,
    private val connect: ((Link?) -> Unit) -> Unit,
) {
    enum class State { WAITING, LIVE, LOST, CLOSED }

    sealed interface Event {
        /** The very first time both tablets are connected. [iAmFirst] means White in chess, Red in checkers. */
        data class Joined(val iAmFirst: Boolean, val kind: String) : Event
        /** The link came back after a dropout. */
        data object Resumed : Event
        /** The other tablet went quiet. */
        data object Lost : Event
        /** The other player moved. [n] counts the moves played so far, including this one. */
        data class Move(val n: Int, val wire: String) : Event
        /** The other tablet's whole list of moves (sent after joining and after every reconnect). */
        data class Sync(val moves: List<String>) : Event
        data object RematchAsked : Event
        data object RematchDeclined : Event
        /** Both players agreed to play again; sides have been swapped. */
        data class NewGame(val iAmFirst: Boolean) : Event
        data object FriendLeft : Event
        /** Guest only. [reason] is code, version, busy or wait. The session is closed afterwards. */
        data class Rejected(val reason: String) : Event
        /** Guest only: the host could not be reached. */
        data object CouldNotConnect : Event
    }

    /** "chess" or "checkers". A guest learns it from the host. */
    var kind: String = kind
        private set
    var state: State = State.WAITING
        private set
    /** True when this tablet plays White (chess) or Red (checkers). A guest learns it when it joins. */
    var iAmFirst: Boolean = if (isHost) hostIsFirst else !hostIsFirst
        private set
    val live: Boolean get() = state == State.LIVE
    /** The TCP port a Wi-Fi host is listening on (0 for Bluetooth). */
    val port: Int get() = listener?.port ?: 0

    /** Asked for when the tablets (re)join, so the other side can catch up. Set by whoever owns the game. */
    var moveSource: () -> List<String> = { emptyList() }

    private var sink: ((Event) -> Unit)? = null
    private val queue = ArrayDeque<Event>()

    private var link: Link? = null
    private var lastRecv = 0L
    private var lastHb = 0L
    private var everLive = false
    private var iAsked = false
    private var theyAsked = false
    private var token = if (isHost) PairCode.newNonce() else ""

    // host only
    private class Pending(val link: Link, val nonce: String, val since: Long)
    private val pending = ArrayList<Pending>()
    private val badTries = ArrayList<Long>()
    private var lockUntil = Long.MIN_VALUE
    private var joinedOnce = false

    // guest only
    private var started = false
    private var connecting = false
    @Volatile private var connectDone = false
    private val connectResult = AtomicReference<Link?>(null)
    private var tries = 0
    private var lastTry = 0L
    private var handshakeDone = false
    private var linkSince = 0L

    /**
     * Starts receiving events. Anything that arrived while nobody was listening is delivered first, in order.
     * A handler may call attach(null) from inside an event to hold the rest back for the next listener.
     */
    fun attach(sink: ((Event) -> Unit)?) {
        this.sink = sink
        while (true) {
            val s = this.sink ?: break
            val e = queue.removeFirstOrNull() ?: break
            s(e)
        }
    }

    private fun emit(e: Event) {
        val s = sink
        if (s != null && queue.isEmpty()) s(e) else queue.addLast(e)
    }

    // ---------------------------------------------------------------- what the game can ask for

    fun sendMove(n: Int, wire: String) {
        if (state == State.LIVE) link?.send(Wire.msg("move", n, wire))
    }

    /** Tell the other tablet every move this one knows about. */
    fun sendSync() {
        link?.send(Wire.msg("sync", moveSource().joinToString(",")))
    }

    /** Ask the other tablet for its full list of moves (used when a move arrives out of order). */
    fun requestSync() {
        link?.send("needsync")
    }

    fun wantRematch() {
        if (state != State.LIVE) return
        link?.send("again")
        if (theyAsked) startNewGame() else iAsked = true
    }

    fun declineRematch() {
        theyAsked = false
        link?.send("no")
    }

    private fun startNewGame() {
        iAsked = false
        theyAsked = false
        iAmFirst = !iAmFirst
        emit(Event.NewGame(iAmFirst))
    }

    /** Say goodbye and shut everything down. */
    fun leave() {
        if (state != State.CLOSED) {
            link?.send("bye")
            link?.close()
        }
        link = null
        for (p in pending) p.link.close()
        pending.clear()
        listener?.stop()
        state = State.CLOSED
    }

    // ---------------------------------------------------------------- the heartbeat

    fun update(now: Long) {
        if (state == State.CLOSED) return
        if (isHost) updateHost(now) else updateGuest(now)
    }

    private fun updateHost(now: Long) {
        val l0 = listener ?: return
        while (true) {
            val l = l0.poll() ?: break
            val nonce = PairCode.newNonce()
            pending.add(Pending(l, nonce, now))
            l.send(Wire.msg("hello", Nearby.VERSION, kind, nonce))
        }
        val it = pending.iterator()
        while (it.hasNext()) {
            val p = it.next()
            var finished = false
            while (!finished) {
                val line = p.link.poll() ?: break
                val m = Wire.parse(line)
                if (m[0] == "join") {
                    onJoin(p, m, now)
                    finished = true
                }
            }
            if (!finished && (!p.link.alive || now - p.since > Nearby.HANDSHAKE_TIMEOUT_MS)) {
                p.link.close()
                finished = true
            }
            if (finished) it.remove()
        }
        pollLink(now)
    }

    private fun updateGuest(now: Long) {
        if (!started) {
            started = true
            startConnect(now)
        }
        if (connecting && connectDone) {
            connecting = false
            val l = connectResult.getAndSet(null)
            if (l != null) {
                link = l
                handshakeDone = false
                linkSince = now
                lastRecv = now
            } else if (!everLive && tries >= MAX_CONNECT_TRIES) {
                couldNotConnect()
                return
            }
        }
        pollLink(now)
        if (state == State.CLOSED) return
        val l = link
        if (l != null && !handshakeDone && now - linkSince > Nearby.HANDSHAKE_TIMEOUT_MS) {
            l.close()
            link = null
        }
        // Try again every couple of seconds: the first time to find the host, later to win a lost game back.
        if (!connecting && link == null && now - lastTry >= Nearby.RETRY_MS) {
            if (!everLive && tries >= MAX_CONNECT_TRIES) couldNotConnect() else startConnect(now)
        }
    }

    private fun startConnect(now: Long) {
        connecting = true
        connectDone = false
        connectResult.set(null)
        lastTry = now
        tries++
        connect { l ->
            connectResult.set(l)
            connectDone = true
        }
    }

    private fun couldNotConnect() {
        link?.close()
        link = null
        state = State.CLOSED
        emit(Event.CouldNotConnect)
    }

    private fun pollLink(now: Long) {
        val l = link ?: return
        while (true) {
            val line = l.poll() ?: break
            lastRecv = now
            val m = Wire.parse(line)
            if (!isHost && !handshakeDone) handleHandshake(m, now) else handleLive(m)
            if (state == State.CLOSED || link !== l) return
        }
        if (!l.alive) {
            lose()
            return
        }
        if (state == State.LIVE) {
            if (now - lastHb >= Nearby.HEARTBEAT_MS) {
                l.send("hb")
                lastHb = now
            }
            if (now - lastRecv > Nearby.LOST_AFTER_MS) lose()
        }
    }

    private fun lose() {
        link?.close()
        link = null
        handshakeDone = false
        iAsked = false
        theyAsked = false
        if (state == State.LIVE) {
            state = State.LOST
            emit(Event.Lost)
        }
    }

    // ---------------------------------------------------------------- joining

    private fun onJoin(p: Pending, m: List<String>, now: Long) {
        if (now < lockUntil) return reject(p, "wait")
        if (m.getOrNull(1)?.toIntOrNull() != Nearby.VERSION) return reject(p, "version")
        val proof = m.getOrNull(2).orEmpty()
        if (proof.isEmpty() || proof != PairCode.proof(p.nonce, code)) {
            badTries.add(now)
            badTries.removeAll { now - it > Nearby.BAD_WINDOW_MS }
            if (badTries.size >= Nearby.MAX_BAD_TRIES) {
                lockUntil = now + Nearby.LOCKOUT_MS
                badTries.clear()
            }
            return reject(p, "code")
        }
        // Once somebody has joined, only that same tablet may come back.
        if (joinedOnce && m.getOrNull(3).orEmpty() != token) return reject(p, "busy")
        badTries.clear()
        link?.close()
        link = p.link
        lastRecv = now
        lastHb = now
        val first = !joinedOnce
        joinedOnce = true
        state = State.LIVE
        iAsked = false
        theyAsked = false
        p.link.send(Wire.msg("welcome", if (iAmFirst) 0 else 1, token, kind))
        emit(if (first) Event.Joined(iAmFirst, kind) else Event.Resumed)
        sendSync()
    }

    private fun reject(p: Pending, reason: String) {
        p.link.send(Wire.msg("reject", reason))
        p.link.close()
    }

    private fun handleHandshake(m: List<String>, now: Long) {
        when (m[0]) {
            "hello" -> {
                if (m.getOrNull(1)?.toIntOrNull() != Nearby.VERSION) {
                    rejectedByHost("version")
                    return
                }
                val nonce = m.getOrNull(3) ?: return
                m.getOrNull(2)?.let { kind = it }
                link?.send(Wire.msg("join", Nearby.VERSION, PairCode.proof(nonce, code), token))
            }
            "welcome" -> {
                iAmFirst = m.getOrNull(1) == "1"
                token = m.getOrNull(2).orEmpty()
                m.getOrNull(3)?.let { kind = it }
                handshakeDone = true
                lastRecv = now
                lastHb = now
                state = State.LIVE
                val first = !everLive
                everLive = true
                emit(if (first) Event.Joined(iAmFirst, kind) else Event.Resumed)
                sendSync()
            }
            "reject" -> rejectedByHost(m.getOrNull(1) ?: "code")
        }
    }

    private fun rejectedByHost(reason: String) {
        link?.close()
        link = null
        state = State.CLOSED
        emit(Event.Rejected(reason))
    }

    // ---------------------------------------------------------------- playing

    private fun handleLive(m: List<String>) {
        when (m[0]) {
            "move" -> {
                val n = m.getOrNull(1)?.toIntOrNull()
                val wire = m.getOrNull(2)
                if (n != null && !wire.isNullOrEmpty() && state == State.LIVE) emit(Event.Move(n, wire))
            }
            "sync" -> emit(Event.Sync(m.getOrNull(1)?.takeIf { it.isNotEmpty() }?.split(',') ?: emptyList()))
            "needsync" -> sendSync()
            "again" -> if (iAsked) startNewGame() else {
                theyAsked = true
                emit(Event.RematchAsked)
            }
            "no" -> if (iAsked) {
                iAsked = false
                emit(Event.RematchDeclined)
            }
            "bye" -> {
                link?.close()
                link = null
                listener?.stop()
                state = State.CLOSED
                emit(Event.FriendLeft)
            }
        }
    }

    companion object {
        private const val MAX_CONNECT_TRIES = 3

        /** Wait for a friend. [kind] is Nearby.CHESS or Nearby.CHECKERS; [hostIsFirst] means the host plays White or Red. */
        fun host(kind: String, hostIsFirst: Boolean, code: List<Int>, listener: Listener) =
            NearbySession(true, kind, code, hostIsFirst, listener) { }

        /** Join a game. [connect] starts connecting in the background and calls back with the link (or null). */
        fun guest(code: List<Int>, connect: ((Link?) -> Unit) -> Unit) =
            NearbySession(false, "", code, true, null, connect)
    }
}

/** What a tablet should do with the other tablet's full list of moves. */
sealed interface Reconcile {
    /** Nothing to do. */
    data object Keep : Reconcile
    /** The other tablet is ahead: play these extra moves. */
    data class Append(val moves: List<String>) : Reconcile
    /** The games disagree and this tablet is the guest, so start over from the host's list. */
    data class Replace(val moves: List<String>) : Reconcile
}

fun reconcile(local: List<String>, remote: List<String>, iAmHost: Boolean): Reconcile = when {
    remote == local -> Reconcile.Keep
    remote.size > local.size && remote.subList(0, local.size) == local -> Reconcile.Append(remote.drop(local.size))
    local.size >= remote.size && local.subList(0, remote.size) == remote -> Reconcile.Keep
    iAmHost -> Reconcile.Keep
    else -> Reconcile.Replace(remote)
}
