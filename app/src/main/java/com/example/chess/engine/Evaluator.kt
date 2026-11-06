package com.example.chess.engine

import kotlin.math.abs
import kotlin.math.max

/** Static evaluation in centipawns from the side to move's point of view. */
object Evaluator {
    /** Indexed by piece type. */
    val VALUE = intArrayOf(0, 100, 310, 325, 500, 900, 0)

    // Tables are written as White sees the board: rank 8 on top, a-file on the left.
    private val PAWN_T = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        60, 60, 60, 60, 60, 60, 60, 60,
        15, 15, 25, 35, 35, 25, 15, 15,
        6, 6, 12, 26, 26, 12, 6, 6,
        2, 2, 6, 22, 22, 4, 2, 2,
        4, 0, -4, 4, 4, -6, 0, 4,
        4, 8, 8, -18, -18, 8, 8, 4,
        0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val KNIGHT_T = intArrayOf(
        -50, -35, -28, -28, -28, -28, -35, -50,
        -35, -18, 0, 4, 4, 0, -18, -35,
        -28, 4, 12, 16, 16, 12, 4, -28,
        -28, 6, 16, 22, 22, 16, 6, -28,
        -28, 2, 16, 22, 22, 16, 2, -28,
        -28, 6, 12, 14, 14, 12, 6, -28,
        -35, -18, 0, 6, 6, 0, -18, -35,
        -50, -32, -28, -28, -28, -28, -32, -50,
    )
    private val BISHOP_T = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 6, 10, 10, 6, 0, -10,
        -10, 6, 6, 10, 10, 6, 6, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 6, 0, 0, 0, 0, 6, -10,
        -20, -10, -12, -10, -10, -12, -10, -20,
    )
    private val ROOK_T = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        10, 15, 15, 15, 15, 15, 15, 10,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 0, 6, 6, 4, 0, 0,
    )
    private val QUEEN_T = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20,
    )
    private val KING_MG = intArrayOf(
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        15, 15, -5, -5, -5, -5, 15, 15,
        20, 30, 10, 0, 0, 10, 30, 20,
    )
    private val KING_EG = intArrayOf(
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50,
    )
    private val TABLES = arrayOf(IntArray(64), PAWN_T, KNIGHT_T, BISHOP_T, ROOK_T, QUEEN_T)
    private val PHASE_WEIGHT = intArrayOf(0, 0, 1, 1, 2, 4, 0)

    private fun tableIndex(sq: Int, white: Boolean): Int =
        if (white) (7 - Square.rank(sq)) * 8 + Square.file(sq) else sq

    fun evaluate(pos: Position): Int {
        var score = 0
        var phase = 0
        var whiteKing = Square.NONE
        var blackKing = Square.NONE
        var whiteBishops = 0
        var blackBishops = 0
        var whiteMaterial = 0
        var blackMaterial = 0
        for (sq in 0 until 64) {
            val p = pos[sq]
            if (p == 0) continue
            val type = abs(p)
            val white = p > 0
            if (type == Piece.KING) {
                if (white) whiteKing = sq else blackKing = sq
                continue
            }
            phase += PHASE_WEIGHT[type]
            val v = VALUE[type] + TABLES[type][tableIndex(sq, white)]
            if (white) {
                score += v
                whiteMaterial += VALUE[type]
                if (type == Piece.BISHOP) whiteBishops++
            } else {
                score -= v
                blackMaterial += VALUE[type]
                if (type == Piece.BISHOP) blackBishops++
            }
        }
        if (whiteBishops >= 2) score += 30
        if (blackBishops >= 2) score -= 30

        val mg = phase.coerceAtMost(24)
        if (whiteKing != Square.NONE) {
            val i = tableIndex(whiteKing, true)
            score += (KING_MG[i] * mg + KING_EG[i] * (24 - mg)) / 24
        }
        if (blackKing != Square.NONE) {
            val i = tableIndex(blackKing, false)
            score -= (KING_MG[i] * mg + KING_EG[i] * (24 - mg)) / 24
        }

        // Help the stronger side drive a lone king into the corner to finish the game.
        if (whiteKing != Square.NONE && blackKing != Square.NONE) {
            if (blackMaterial == 0 && whiteMaterial >= VALUE[Piece.ROOK]) score += mopUp(whiteKing, blackKing)
            if (whiteMaterial == 0 && blackMaterial >= VALUE[Piece.ROOK]) score -= mopUp(blackKing, whiteKing)
        }
        return if (pos.whiteToMove) score else -score
    }

    private fun mopUp(winnerKing: Int, loserKing: Int): Int {
        val lf = Square.file(loserKing)
        val lr = Square.rank(loserKing)
        val centerDistance = max(3 - lf, lf - 4) + max(3 - lr, lr - 4)
        val kingDistance = abs(lf - Square.file(winnerKing)) + abs(lr - Square.rank(winnerKing))
        return 10 * centerDistance + 4 * (14 - kingDistance)
    }
}
