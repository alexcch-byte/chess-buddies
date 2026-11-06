package com.example.chess.engine

import kotlin.math.abs

object Notation {
    /** Standard algebraic notation, e.g. "Nf3", "exd5", "O-O", "e8=Q#". */
    fun san(pos: Position, move: Move): String {
        val sb = StringBuilder()
        val type = abs(pos[move.from])
        if (move.isCastle) {
            sb.append(if (move.to > move.from) "O-O" else "O-O-O")
        } else {
            if (type == Piece.PAWN) {
                if (move.isCapture) sb.append('a' + Square.file(move.from))
            } else {
                sb.append(Piece.letter(type))
                val rivals = pos.legalMoves().filter {
                    it.to == move.to && it.from != move.from && abs(pos[it.from]) == type
                }
                if (rivals.isNotEmpty()) {
                    val sameFile = rivals.any { Square.file(it.from) == Square.file(move.from) }
                    val sameRank = rivals.any { Square.rank(it.from) == Square.rank(move.from) }
                    when {
                        !sameFile -> sb.append('a' + Square.file(move.from))
                        !sameRank -> sb.append('1' + Square.rank(move.from))
                        else -> sb.append(Square.name(move.from))
                    }
                }
            }
            if (move.isCapture) sb.append('x')
            sb.append(Square.name(move.to))
            if (move.isPromotion) sb.append('=').append(Piece.letter(move.promotion))
        }
        val after = pos.makeMove(move)
        if (after.inCheck()) sb.append(if (after.hasLegalMove()) "+" else "#")
        return sb.toString()
    }

    /** A sentence a child can follow, e.g. "Knight from g1 to f3" or "Pawn takes Bishop on e5". */
    fun describe(pos: Position, move: Move): String {
        val type = abs(pos[move.from])
        val name = Piece.name(type)
        if (move.isCastle) return if (move.to > move.from) "Castle on the King's side" else "Castle on the Queen's side"
        val target = Square.name(move.to)
        val base = when {
            move.isEnPassant -> "Pawn takes Pawn en passant on $target"
            move.isCapture -> "$name takes ${Piece.name(abs(pos[move.to]))} on $target"
            else -> "$name from ${Square.name(move.from)} to $target"
        }
        return if (move.isPromotion) "$base and becomes a ${Piece.name(move.promotion)}" else base
    }
}
