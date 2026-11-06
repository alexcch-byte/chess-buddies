package com.example.chess.engine

import com.example.chess.engine.Piece.BISHOP
import com.example.chess.engine.Piece.KING
import com.example.chess.engine.Piece.KNIGHT
import com.example.chess.engine.Piece.PAWN
import com.example.chess.engine.Piece.QUEEN
import com.example.chess.engine.Piece.ROOK
import kotlin.math.abs

/** Simple position checks that let the app give kid-friendly advice. */
object Coach {

    /**
     * Pieces of the given color that can be won: attacked and either unprotected,
     * or attacked by something worth less.
     */
    fun hangingPieces(pos: Position, white: Boolean): List<Int> {
        val result = ArrayList<Int>()
        for (sq in 0 until 64) {
            val p = pos[sq]
            if (p == 0 || (p > 0) != white || abs(p) == KING) continue
            val attackers = pos.attackers(sq, !white)
            if (attackers.isEmpty()) continue
            val defended = pos.attackers(sq, white).isNotEmpty()
            // A king can't capture a protected piece, so it never counts as a cheap attacker.
            // Points (not centipawns) keep even trades like Knight-for-Bishop from counting.
            val cheapest = attackers.minOf { a -> if (abs(pos[a]) == KING) Int.MAX_VALUE else Piece.points(abs(pos[a])) }
            if (!defended || cheapest < Piece.points(abs(p))) result += sq
        }
        return result
    }

    /** A move that checkmates right away, if the side to move has one. */
    fun mateInOne(pos: Position): Move? =
        pos.legalMoves().firstOrNull { pos.makeMove(it).isCheckmate() }

    /** If it were the opponent's turn, could they checkmate immediately? Returns their mating move. */
    fun opponentMateThreat(pos: Position): Move? {
        if (pos.inCheck()) return null
        return mateInOne(pos.passTurn())
    }

    /** Explains, in plain words, why a move from [from] to [to] isn't allowed. */
    fun explainIllegal(pos: Position, from: Int, to: Int): String {
        val p = pos[from]
        val type = abs(p)
        val white = p > 0
        if (pos.pseudoLegalMoves().any { it.from == from && it.to == to }) {
            return when {
                pos.inCheck() -> "Your King is in check! First you have to get your King out of danger."
                type == KING -> "The King can't step into danger. That square is attacked!"
                else -> "Careful! That move would leave your King in danger."
            }
        }
        val target = pos[to]
        if (target != 0 && (target > 0) == white) return "You can't capture your own piece!"

        val df = Square.file(to) - Square.file(from)
        val dr = Square.rank(to) - Square.rank(from)
        val forward = if (white) dr else -dr
        val straight = df == 0 || dr == 0
        val diagonal = abs(df) == abs(dr)
        return when (type) {
            PAWN -> when {
                forward <= 0 -> "Pawns can only move forward, never backward or sideways."
                abs(df) == 1 && forward == 1 -> "Pawns can only move diagonally when they capture something."
                df == 0 && target != 0 -> "Pawns can't capture straight ahead. That square is blocked!"
                df == 0 && forward == 2 -> "A pawn can only jump two squares on its very first move, and both squares must be empty."
                else -> "Pawns move straight forward, one square at a time."
            }
            KNIGHT -> "Knights move in an L shape: two squares one way, then one square to the side."
            BISHOP -> if (diagonal) "Something is in the Bishop's way!" else "Bishops only move diagonally."
            ROOK -> if (straight) "Something is in the Rook's way!" else "Rooks only move in straight lines: up, down, left or right."
            QUEEN -> if (straight || diagonal) "Something is in the Queen's way!" else "The Queen moves in straight lines and diagonals, but not in an L shape."
            KING -> if (dr == 0 && abs(df) == 2) "You can't castle right now." else "The King moves just one square at a time."
            else -> "That move isn't allowed."
        }
    }
}
