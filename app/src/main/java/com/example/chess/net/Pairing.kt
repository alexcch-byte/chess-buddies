package com.example.chess.net

import java.security.MessageDigest
import java.util.Random

/** Constants shared by both tablets. Bump [VERSION] whenever a message changes shape. */
object Nearby {
    const val GAME_ID = "chessbuddies"
    const val VERSION = 1
    const val TCP_PORT = 47791
    const val BEACON_PORT = 47792

    const val HEARTBEAT_MS = 1000L
    /** No news for this long means the other tablet has dropped out. */
    const val LOST_AFTER_MS = 5000L
    const val RETRY_MS = 2000L
    const val HANDSHAKE_TIMEOUT_MS = 10_000L
    /** Wrong picture codes allowed inside [BAD_WINDOW_MS] before joining is switched off for [LOCKOUT_MS]. */
    const val MAX_BAD_TRIES = 5
    const val BAD_WINDOW_MS = 60_000L
    const val LOCKOUT_MS = 30_000L

    const val CHESS = "chess"
    const val CHECKERS = "checkers"
}

/**
 * The picture code that pairs two tablets, as in the Gus & Leo detective game: the host shows [SIZE] pictures, the joining
 * child taps the same ones. The code itself never goes over the air. The host sends a random number and the guest answers with
 * a hash of that number plus the code ([proof]), so a snooper learns nothing, and a wrong guess is simply refused.
 */
object PairCode {
    const val SIZE = 4
    val PICTURES = listOf("⭐", "🌳", "🍎", "🚀", "🎈", "🌈", "🍌", "🏠", "☕", "⚽", "🎸", "🍦")
    /** The host's cheerful name tag, shown on the host's screen and in the list of games a friend sees. */
    val AVATARS = listOf("🦊", "🐰", "🐱", "🦉", "🐲", "🐻", "🐼", "🐸")

    fun random(rng: Random = Random()): List<Int> {
        val pool = PICTURES.indices.toMutableList()
        return List(SIZE) { pool.removeAt(rng.nextInt(pool.size)) }.sorted()
    }

    fun randomAvatar(rng: Random = Random()): Int = rng.nextInt(AVATARS.size)

    fun newNonce(rng: Random = Random()): String = (0 until 8).joinToString("") { "%02x".format(rng.nextInt(256)) }

    /** The order the pictures were tapped in doesn't matter. */
    fun canonical(code: Collection<Int>): String = code.sorted().joinToString(",")

    fun proof(nonce: String, code: Collection<Int>): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$nonce|${canonical(code)}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/**
 * Messages are one line each, with fields separated by "|". Nothing in a field may contain "|" (move names, numbers and
 * hex strings never do), so no escaping is needed.
 */
object Wire {
    fun msg(vararg parts: Any): String = parts.joinToString("|")
    fun parse(line: String): List<String> = line.split('|')
}
