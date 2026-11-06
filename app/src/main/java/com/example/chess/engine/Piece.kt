package com.example.chess.engine

import kotlin.math.abs

/**
 * Pieces are stored as signed ints: positive for White, negative for Black, 0 for an empty square.
 * The absolute value is the piece type.
 */
object Piece {
    const val EMPTY = 0
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6

    fun type(piece: Int): Int = abs(piece)
    fun isWhite(piece: Int): Boolean = piece > 0
    fun isBlack(piece: Int): Boolean = piece < 0
    fun make(type: Int, white: Boolean): Int = if (white) type else -type

    /** Classic kid-friendly point values (pawn = 1). */
    fun points(type: Int): Int = when (type) {
        PAWN -> 1
        KNIGHT -> 3
        BISHOP -> 3
        ROOK -> 5
        QUEEN -> 9
        else -> 0
    }

    fun name(type: Int): String = when (type) {
        PAWN -> "Pawn"
        KNIGHT -> "Knight"
        BISHOP -> "Bishop"
        ROOK -> "Rook"
        QUEEN -> "Queen"
        KING -> "King"
        else -> ""
    }

    fun letter(type: Int): Char = when (type) {
        PAWN -> 'P'
        KNIGHT -> 'N'
        BISHOP -> 'B'
        ROOK -> 'R'
        QUEEN -> 'Q'
        KING -> 'K'
        else -> '?'
    }

    fun fromLetter(c: Char): Int {
        val type = when (c.uppercaseChar()) {
            'P' -> PAWN
            'N' -> KNIGHT
            'B' -> BISHOP
            'R' -> ROOK
            'Q' -> QUEEN
            'K' -> KING
            else -> return EMPTY
        }
        return make(type, c.isUpperCase())
    }
}

/** Squares are 0..63 with a1 = 0, h1 = 7, a8 = 56, h8 = 63. */
object Square {
    const val NONE = -1

    fun of(file: Int, rank: Int): Int = rank * 8 + file
    fun file(sq: Int): Int = sq and 7
    fun rank(sq: Int): Int = sq shr 3
    fun onBoard(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7
    fun isLight(sq: Int): Boolean = (file(sq) + rank(sq)) % 2 == 1

    fun name(sq: Int): String = "${'a' + file(sq)}${rank(sq) + 1}"

    fun parse(name: String): Int {
        if (name.length != 2) return NONE
        val f = name[0] - 'a'
        val r = name[1] - '1'
        return if (onBoard(f, r)) of(f, r) else NONE
    }
}

data class Move(
    val from: Int,
    val to: Int,
    /** Piece type to promote to (positive), or 0. */
    val promotion: Int = 0,
    val flags: Int = 0,
) {
    val isCapture: Boolean get() = flags and CAPTURE != 0
    val isEnPassant: Boolean get() = flags and EN_PASSANT != 0
    val isCastle: Boolean get() = flags and CASTLE != 0
    val isDoublePush: Boolean get() = flags and DOUBLE_PUSH != 0
    val isPromotion: Boolean get() = promotion != 0

    fun uci(): String {
        val promo = if (promotion != 0) Piece.letter(promotion).lowercaseChar().toString() else ""
        return Square.name(from) + Square.name(to) + promo
    }

    companion object {
        const val CAPTURE = 1
        const val EN_PASSANT = 2
        const val CASTLE = 4
        const val DOUBLE_PUSH = 8
    }
}
