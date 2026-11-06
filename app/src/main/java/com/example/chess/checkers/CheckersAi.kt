package com.example.chess.checkers

import com.example.chess.engine.Bot
import com.example.chess.engine.Square
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/** Alpha-beta search for checkers. The same forest-friend bots play, with checkers-sized settings. */
object CheckersAi {
    const val WIN = 100_000
    private const val INF = 1_000_000
    private const val MAX_PLY = 64

    private class Settings(val randomMoveChance: Double, val depth: Int, val noise: Int, val timeLimitMs: Long)

    private fun settingsFor(bot: Bot): Settings = when (bot) {
        Bot.BUNNY -> Settings(0.6, 1, 150, 600)
        Bot.KITTEN -> Settings(0.15, 2, 80, 800)
        Bot.FOX -> Settings(0.0, 4, 30, 1200)
        Bot.OWL -> Settings(0.0, 6, 10, 2000)
        Bot.DRAGON -> Settings(0.0, 14, 0, 2200)
    }

    fun chooseMove(pos: CheckersPosition, bot: Bot, random: Random = Random.Default): CheckersMove? {
        val legal = pos.legalMoves()
        if (legal.isEmpty()) return null
        if (legal.size == 1) return legal[0]
        val s = settingsFor(bot)
        if (random.nextDouble() < s.randomMoveChance) return legal[random.nextInt(legal.size)]
        if (s.noise == 0) return bestMove(pos, s.depth, s.timeLimitMs) ?: legal[0]

        val searcher = Searcher(System.currentTimeMillis() + s.timeLimitMs)
        var best: CheckersMove? = null
        var bestScore = -INF
        for (m in ordered(legal)) {
            val raw = -searcher.negamax(pos.makeMove(m), s.depth - 1, -INF, INF, 1)
            if (searcher.aborted) break
            val score = raw + if (abs(raw) >= WIN - MAX_PLY) 0 else random.nextInt(-s.noise, s.noise + 1)
            if (score > bestScore) {
                bestScore = score
                best = m
            }
        }
        return best ?: legal[0]
    }

    /** Iterative-deepening search for the strongest bot and for hints. */
    fun bestMove(pos: CheckersPosition, maxDepth: Int, timeLimitMs: Long): CheckersMove? {
        val legal = pos.legalMoves()
        if (legal.isEmpty()) return null
        if (legal.size == 1) return legal[0]
        val searcher = Searcher(System.currentTimeMillis() + timeLimitMs)
        var rootMoves = ordered(legal)
        var result: CheckersMove? = null
        for (depth in 1..maxDepth) {
            var alpha = -INF
            var bestThisDepth: CheckersMove? = null
            for (m in rootMoves) {
                val score = -searcher.negamax(pos.makeMove(m), depth - 1, -INF, -alpha, 1)
                if (searcher.aborted) break
                if (score > alpha) {
                    alpha = score
                    bestThisDepth = m
                }
            }
            if (searcher.aborted) {
                if (result == null) result = bestThisDepth
                break
            }
            if (bestThisDepth == null) break
            result = bestThisDepth
            rootMoves = listOf(bestThisDepth) + rootMoves.filter { it != bestThisDepth }
            if (alpha >= WIN - MAX_PLY) break
        }
        return result ?: rootMoves[0]
    }

    /**
     * Scores every legal move with a short search, from the mover's point of view.
     * The coach uses this to notice when a move throws pieces away.
     */
    fun scoreMoves(pos: CheckersPosition, depth: Int, timeLimitMs: Long): Map<CheckersMove, Int> {
        val searcher = Searcher(System.currentTimeMillis() + timeLimitMs)
        val out = LinkedHashMap<CheckersMove, Int>()
        for (m in ordered(pos.legalMoves())) {
            val score = -searcher.negamax(pos.makeMove(m), depth - 1, -INF, INF, 1)
            if (searcher.aborted) break
            out[m] = score
        }
        return out
    }

    private fun ordered(moves: List<CheckersMove>): List<CheckersMove> = moves.sortedByDescending { it.captured.size }

    /** Static evaluation from the side to move's point of view. */
    fun evaluate(pos: CheckersPosition): Int {
        var score = 0
        var redMaterial = 0
        var blackMaterial = 0
        var pieces = 0
        for (sq in 0 until 64) {
            if (pos[sq] != 0) pieces++
        }
        val endgame = pieces <= 10
        for (sq in 0 until 64) {
            val p = pos[sq]
            if (p == 0) continue
            val red = p > 0
            val f = Square.file(sq)
            val r = Square.rank(sq)
            var v: Int
            if (Checker.isKing(p)) {
                v = 160
                if (f in 2..5 && r in 2..5) v += 8
                if (f == 0 || f == 7) v -= 6
            } else {
                v = 100
                val advance = if (red) r else 7 - r
                v += advance * if (endgame) 6 else 3
                // Men on the back row stop the other side from getting kings.
                if (!endgame && advance == 0) v += 8
                if (f in 2..5 && r in 2..5) v += 4
            }
            if (red) {
                score += v
                redMaterial += v
            } else {
                score -= v
                blackMaterial += v
            }
        }
        // When ahead, send the kings after the remaining enemy pieces so the game gets finished.
        if (endgame && redMaterial != blackMaterial) {
            val strongRed = redMaterial > blackMaterial
            score += (if (strongRed) 1 else -1) * huntBonus(pos, strongRed)
        }
        return if (pos.redToMove) score else -score
    }

    private fun huntBonus(pos: CheckersPosition, red: Boolean): Int {
        val targets = (0 until 64).filter { pos[it] != 0 && (pos[it] > 0) != red }
        if (targets.isEmpty()) return 0
        var bonus = 0
        for (sq in 0 until 64) {
            val p = pos[sq]
            if (p == 0 || (p > 0) != red || !Checker.isKing(p)) continue
            var nearest = 14
            for (t in targets) {
                nearest = min(nearest, maxOf(abs(Square.file(t) - Square.file(sq)), abs(Square.rank(t) - Square.rank(sq))))
            }
            bonus += (8 - nearest) * 3
        }
        return bonus
    }

    private class Searcher(private val deadline: Long) {
        var aborted = false
        private var nodes = 0

        fun negamax(pos: CheckersPosition, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
            if ((++nodes and 1023) == 0 && System.currentTimeMillis() > deadline) aborted = true
            if (aborted) return 0
            val moves = pos.legalMoves()
            if (moves.isEmpty()) return -WIN + ply
            // Jumps are forced, so keep following them past the depth limit (a natural quiescence search).
            if ((depth <= 0 && !moves[0].isCapture) || ply >= MAX_PLY) return evaluate(pos)
            var alpha = alphaIn
            var best = -INF
            for (m in ordered(moves)) {
                val score = -negamax(pos.makeMove(m), depth - 1, -beta, -alpha, ply + 1)
                if (aborted) return 0
                if (score > best) best = score
                if (score > alpha) alpha = score
                if (alpha >= beta) break
            }
            return best
        }
    }
}
