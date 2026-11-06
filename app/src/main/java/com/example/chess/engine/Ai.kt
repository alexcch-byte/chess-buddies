package com.example.chess.engine

import kotlin.math.abs
import kotlin.random.Random

/** The computer opponents, from gentlest to toughest. */
enum class Bot(
    val id: String,
    val displayName: String,
    val emoji: String,
    val blurb: String,
    val level: Int,
    /** Chance of playing a completely random legal move. */
    val randomMoveChance: Double,
    /** How many moves ahead to look (plus captures). */
    val depth: Int,
    /** Random wobble added to each move's score, in centipawns. Bigger = more mistakes. */
    val noise: Int,
    val timeLimitMs: Long,
) {
    BUNNY("bunny", "Bubbles the Bunny", "🐰", "Just learning! Hops around and makes lots of mistakes.", 1, 0.7, 1, 250, 800),
    KITTEN("kitten", "Kiki the Kitten", "🐱", "Loves to pounce on pieces, but forgets to look out.", 2, 0.15, 1, 120, 1000),
    FOX("fox", "Felix the Fox", "🦊", "Sneaky! Spots captures and simple tricks.", 3, 0.0, 2, 60, 1500),
    OWL("owl", "Professor Owl", "🦉", "Thinks a few moves ahead. A real test!", 4, 0.0, 3, 20, 2500),
    DRAGON("dragon", "Drake the Dragon", "🐉", "The forest champion. Can you beat the Dragon?", 5, 0.0, 6, 0, 2500),
    ;

    companion object {
        fun byId(id: String): Bot = entries.firstOrNull { it.id == id } ?: BUNNY
    }
}

object Ai {
    const val MATE = 100_000
    private const val INF = 1_000_000
    private const val MAX_PLY = 40

    /** Picks a move for [bot]. Never returns null if the side to move has a legal move. */
    fun chooseMove(pos: Position, bot: Bot, random: Random = Random.Default): Move? {
        val legal = pos.legalMoves()
        if (legal.isEmpty()) return null
        if (legal.size == 1) return legal[0]
        if (random.nextDouble() < bot.randomMoveChance) return legal[random.nextInt(legal.size)]
        if (bot.noise == 0) return bestMove(pos, bot.depth, bot.timeLimitMs)?.move ?: legal[0]

        val searcher = Searcher(System.currentTimeMillis() + bot.timeLimitMs)
        val ordered = orderMoves(pos, legal)
        var best: Move? = null
        var bestScore = -INF
        for (m in ordered) {
            val raw = -searcher.negamax(pos.makeMove(m), bot.depth - 1, -INF, INF, 1)
            if (searcher.aborted) break
            val score = raw + if (abs(raw) >= MATE - MAX_PLY) 0 else random.nextInt(-bot.noise, bot.noise + 1)
            if (score > bestScore) {
                bestScore = score
                best = m
            }
        }
        return best ?: ordered[0]
    }

    data class SearchResult(val move: Move, val score: Int, val depth: Int)

    /** Iterative-deepening alpha-beta search. Used by the strongest bot and for hints. */
    fun bestMove(pos: Position, maxDepth: Int, timeLimitMs: Long): SearchResult? {
        val legal = pos.legalMoves()
        if (legal.isEmpty()) return null
        if (legal.size == 1) return SearchResult(legal[0], 0, 0)
        val searcher = Searcher(System.currentTimeMillis() + timeLimitMs)
        var rootMoves = orderMoves(pos, legal)
        var result: SearchResult? = null
        for (depth in 1..maxDepth) {
            var alpha = -INF
            var bestThisDepth: Move? = null
            for (m in rootMoves) {
                val score = -searcher.negamax(pos.makeMove(m), depth - 1, -INF, -alpha, 1)
                if (searcher.aborted) break
                if (score > alpha) {
                    alpha = score
                    bestThisDepth = m
                }
            }
            if (searcher.aborted) {
                // A partial search still beats the previous depth if the new best move was found first.
                if (result == null && bestThisDepth != null) result = SearchResult(bestThisDepth, alpha, depth)
                break
            }
            if (bestThisDepth == null) break
            result = SearchResult(bestThisDepth, alpha, depth)
            rootMoves = listOf(bestThisDepth) + rootMoves.filter { it != bestThisDepth }
            if (alpha >= MATE - MAX_PLY) break
        }
        return result ?: SearchResult(rootMoves[0], 0, 0)
    }

    private fun orderMoves(pos: Position, moves: List<Move>): List<Move> =
        moves.sortedByDescending { moveOrderScore(pos, it) }

    private fun moveOrderScore(pos: Position, m: Move): Int {
        var s = 0
        if (m.isCapture) {
            val victim = if (m.isEnPassant) Piece.PAWN else abs(pos[m.to])
            s += 10_000 + Evaluator.VALUE[victim] * 10 - Evaluator.VALUE[abs(pos[m.from])] / 10
        }
        if (m.isPromotion) s += 9_000 + Evaluator.VALUE[m.promotion]
        return s
    }

    private class Searcher(private val deadline: Long) {
        var aborted = false
        private var nodes = 0

        private fun tick() {
            if ((++nodes and 1023) == 0 && System.currentTimeMillis() > deadline) aborted = true
        }

        fun negamax(pos: Position, depthIn: Int, alphaIn: Int, beta: Int, ply: Int): Int {
            tick()
            if (aborted) return 0
            if (pos.halfmoveClock >= 100) return 0
            val inCheck = pos.inCheck()
            var depth = depthIn
            if (depth <= 0) {
                if (!inCheck || ply >= MAX_PLY) return quiesce(pos, alphaIn, beta, ply)
                depth = 1 // Look one more move when in check so mates aren't missed.
            }
            var alpha = alphaIn
            var best = -INF
            var legal = 0
            for (m in orderMoves(pos, pos.pseudoLegalMoves())) {
                val child = pos.makeMove(m)
                if (child.moverLeftKingInCheck()) continue
                legal++
                val score = -negamax(child, depth - 1, -beta, -alpha, ply + 1)
                if (aborted) return 0
                if (score > best) best = score
                if (score > alpha) alpha = score
                if (alpha >= beta) break
            }
            if (legal == 0) return if (inCheck) -MATE + ply else 0
            return best
        }

        private fun quiesce(pos: Position, alphaIn: Int, beta: Int, ply: Int): Int {
            tick()
            if (aborted) return 0
            val standPat = Evaluator.evaluate(pos)
            if (standPat >= beta || ply >= MAX_PLY) return standPat
            var alpha = maxOf(alphaIn, standPat)
            for (m in orderMoves(pos, pos.pseudoLegalMoves(capturesOnly = true))) {
                val child = pos.makeMove(m)
                if (child.moverLeftKingInCheck()) continue
                val score = -quiesce(child, -beta, -alpha, ply + 1)
                if (aborted) return 0
                if (score >= beta) return score
                if (score > alpha) alpha = score
            }
            return alpha
        }
    }
}
