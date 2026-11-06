package com.example.chess.engine

import com.example.chess.engine.Piece.BISHOP
import com.example.chess.engine.Piece.KING
import com.example.chess.engine.Piece.KNIGHT
import com.example.chess.engine.Piece.PAWN
import com.example.chess.engine.Piece.QUEEN
import com.example.chess.engine.Piece.ROOK
import kotlin.math.abs

/**
 * An immutable chess position. Making a move returns a new [Position].
 *
 * Positions don't need kings: lesson boards often hold a single piece. Without a king,
 * moves for that side are never filtered for check.
 */
class Position private constructor(
    private val board: IntArray,
    val whiteToMove: Boolean,
    /** Bit flags: [WHITE_KINGSIDE], [WHITE_QUEENSIDE], [BLACK_KINGSIDE], [BLACK_QUEENSIDE]. */
    val castling: Int,
    val epSquare: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    operator fun get(sq: Int): Int = board[sq]

    fun kingSquare(white: Boolean): Int {
        val king = Piece.make(KING, white)
        for (sq in 0 until 64) if (board[sq] == king) return sq
        return Square.NONE
    }

    fun inCheck(white: Boolean = whiteToMove): Boolean {
        val k = kingSquare(white)
        return k != Square.NONE && isAttacked(k, !white)
    }

    /** True if any piece of the given color attacks [sq]. */
    fun isAttacked(sq: Int, byWhite: Boolean): Boolean {
        val sign = if (byWhite) 1 else -1
        val f = Square.file(sq)
        val pawnRank = Square.rank(sq) + if (byWhite) -1 else 1
        if (pawnRank in 0..7) {
            if (f > 0 && board[Square.of(f - 1, pawnRank)] == sign * PAWN) return true
            if (f < 7 && board[Square.of(f + 1, pawnRank)] == sign * PAWN) return true
        }
        for (t in KNIGHT_TARGETS[sq]) if (board[t] == sign * KNIGHT) return true
        for (t in KING_TARGETS[sq]) if (board[t] == sign * KING) return true
        for (d in 0 until 8) {
            val straight = d < 4
            for (t in RAYS[d][sq]) {
                val p = board[t]
                if (p == 0) continue
                if (p == sign * QUEEN || p == sign * (if (straight) ROOK else BISHOP)) return true
                break
            }
        }
        return false
    }

    /** Squares holding pieces of the given color that attack [sq]. */
    fun attackers(sq: Int, byWhite: Boolean): List<Int> {
        val result = ArrayList<Int>(4)
        val sign = if (byWhite) 1 else -1
        val f = Square.file(sq)
        val pawnRank = Square.rank(sq) + if (byWhite) -1 else 1
        if (pawnRank in 0..7) {
            if (f > 0 && board[Square.of(f - 1, pawnRank)] == sign * PAWN) result += Square.of(f - 1, pawnRank)
            if (f < 7 && board[Square.of(f + 1, pawnRank)] == sign * PAWN) result += Square.of(f + 1, pawnRank)
        }
        for (t in KNIGHT_TARGETS[sq]) if (board[t] == sign * KNIGHT) result += t
        for (t in KING_TARGETS[sq]) if (board[t] == sign * KING) result += t
        for (d in 0 until 8) {
            val straight = d < 4
            for (t in RAYS[d][sq]) {
                val p = board[t]
                if (p == 0) continue
                if (p == sign * QUEEN || p == sign * (if (straight) ROOK else BISHOP)) result += t
                break
            }
        }
        return result
    }

    /** Squares the piece on [sq] attacks (or would capture on), regardless of whose turn it is. */
    fun attackedBy(sq: Int): List<Int> {
        val p = board[sq]
        if (p == 0) return emptyList()
        val result = ArrayList<Int>()
        val f = Square.file(sq)
        val r = Square.rank(sq)
        when (abs(p)) {
            PAWN -> {
                val tr = r + if (p > 0) 1 else -1
                if (tr in 0..7) {
                    if (f > 0) result += Square.of(f - 1, tr)
                    if (f < 7) result += Square.of(f + 1, tr)
                }
            }
            KNIGHT -> result.addAll(KNIGHT_TARGETS[sq].toList())
            KING -> result.addAll(KING_TARGETS[sq].toList())
            else -> {
                val dirs = when (abs(p)) {
                    ROOK -> 0 until 4
                    BISHOP -> 4 until 8
                    else -> 0 until 8
                }
                for (d in dirs) {
                    for (t in RAYS[d][sq]) {
                        result += t
                        if (board[t] != 0) break
                    }
                }
            }
        }
        return result
    }

    fun pseudoLegalMoves(capturesOnly: Boolean = false): MutableList<Move> {
        val moves = ArrayList<Move>(48)
        val white = whiteToMove
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p == 0 || (p > 0) != white) continue
            when (abs(p)) {
                PAWN -> genPawn(sq, white, capturesOnly, moves)
                KNIGHT -> genSteps(sq, white, KNIGHT_TARGETS[sq], capturesOnly, moves)
                BISHOP -> genSlides(sq, white, 4, 8, capturesOnly, moves)
                ROOK -> genSlides(sq, white, 0, 4, capturesOnly, moves)
                QUEEN -> genSlides(sq, white, 0, 8, capturesOnly, moves)
                KING -> {
                    genSteps(sq, white, KING_TARGETS[sq], capturesOnly, moves)
                    if (!capturesOnly) genCastles(sq, white, moves)
                }
            }
        }
        return moves
    }

    private fun genPawn(sq: Int, white: Boolean, capturesOnly: Boolean, moves: MutableList<Move>) {
        val r = Square.rank(sq)
        val lastRank = if (white) 7 else 0
        if (r == lastRank) return
        val dir = if (white) 8 else -8
        val startRank = if (white) 1 else 6
        val one = sq + dir
        val promoting = Square.rank(one) == lastRank
        if (board[one] == 0) {
            if (promoting) {
                addPromotions(sq, one, 0, moves)
            } else if (!capturesOnly) {
                moves += Move(sq, one)
                if (r == startRank && board[one + dir] == 0) moves += Move(sq, one + dir, flags = Move.DOUBLE_PUSH)
            }
        }
        val f = Square.file(sq)
        for (df in intArrayOf(-1, 1)) {
            if (f + df !in 0..7) continue
            val t = one + df
            val q = board[t]
            if (q != 0 && (q > 0) != white) {
                if (promoting) addPromotions(sq, t, Move.CAPTURE, moves) else moves += Move(sq, t, flags = Move.CAPTURE)
            } else if (q == 0 && t == epSquare) {
                moves += Move(sq, t, flags = Move.CAPTURE or Move.EN_PASSANT)
            }
        }
    }

    private fun addPromotions(from: Int, to: Int, flags: Int, moves: MutableList<Move>) {
        for (type in PROMOTION_TYPES) moves += Move(from, to, type, flags)
    }

    private fun genSteps(sq: Int, white: Boolean, targets: IntArray, capturesOnly: Boolean, moves: MutableList<Move>) {
        for (t in targets) {
            val q = board[t]
            if (q == 0) {
                if (!capturesOnly) moves += Move(sq, t)
            } else if ((q > 0) != white) {
                moves += Move(sq, t, flags = Move.CAPTURE)
            }
        }
    }

    private fun genSlides(sq: Int, white: Boolean, dirFrom: Int, dirTo: Int, capturesOnly: Boolean, moves: MutableList<Move>) {
        for (d in dirFrom until dirTo) {
            for (t in RAYS[d][sq]) {
                val q = board[t]
                if (q == 0) {
                    if (!capturesOnly) moves += Move(sq, t)
                    continue
                }
                if ((q > 0) != white) moves += Move(sq, t, flags = Move.CAPTURE)
                break
            }
        }
    }

    private fun genCastles(sq: Int, white: Boolean, moves: MutableList<Move>) {
        val home = if (white) 4 else 60
        if (sq != home) return
        val rook = Piece.make(ROOK, white)
        val kingside = if (white) WHITE_KINGSIDE else BLACK_KINGSIDE
        val queenside = if (white) WHITE_QUEENSIDE else BLACK_QUEENSIDE
        val enemy = !white
        if (castling and kingside != 0 &&
            board[home + 1] == 0 && board[home + 2] == 0 && board[home + 3] == rook &&
            !isAttacked(home, enemy) && !isAttacked(home + 1, enemy) && !isAttacked(home + 2, enemy)
        ) {
            moves += Move(home, home + 2, flags = Move.CASTLE)
        }
        if (castling and queenside != 0 &&
            board[home - 1] == 0 && board[home - 2] == 0 && board[home - 3] == 0 && board[home - 4] == rook &&
            !isAttacked(home, enemy) && !isAttacked(home - 1, enemy) && !isAttacked(home - 2, enemy)
        ) {
            moves += Move(home, home - 2, flags = Move.CASTLE)
        }
    }

    /** True if the side that just moved (i.e. not [whiteToMove]) left its own king attacked. */
    fun moverLeftKingInCheck(): Boolean = inCheck(!whiteToMove)

    fun isLegal(move: Move): Boolean = !makeMove(move).moverLeftKingInCheck()

    fun legalMoves(): List<Move> {
        val pseudo = pseudoLegalMoves()
        if (kingSquare(whiteToMove) == Square.NONE) return pseudo
        return pseudo.filter { isLegal(it) }
    }

    fun hasLegalMove(): Boolean {
        val pseudo = pseudoLegalMoves()
        if (kingSquare(whiteToMove) == Square.NONE) return pseudo.isNotEmpty()
        return pseudo.any { isLegal(it) }
    }

    fun isCheckmate(): Boolean = inCheck() && !hasLegalMove()
    fun isStalemate(): Boolean = !inCheck() && !hasLegalMove()

    /** Finds the legal move matching a UCI string such as "e2e4" or "e7e8q". */
    fun findMove(uci: String): Move? {
        val from = Square.parse(uci.take(2))
        val to = Square.parse(uci.drop(2).take(2))
        val promo = if (uci.length > 4) abs(Piece.fromLetter(uci[4])) else 0
        return legalMoves().firstOrNull { it.from == from && it.to == to && it.promotion == promo }
    }

    fun makeMove(m: Move): Position {
        val b = board.copyOf()
        val p = b[m.from]
        val white = p > 0
        val captured = b[m.to]
        b[m.to] = if (m.promotion != 0) Piece.make(m.promotion, white) else p
        b[m.from] = 0
        if (m.isEnPassant) b[m.to + if (white) -8 else 8] = 0
        if (m.isCastle) {
            if (m.to > m.from) {
                b[m.from + 1] = b[m.from + 3]
                b[m.from + 3] = 0
            } else {
                b[m.from - 1] = b[m.from - 4]
                b[m.from - 4] = 0
            }
        }
        val newCastling = castling and CASTLE_MASK[m.from] and CASTLE_MASK[m.to]
        val newEp = if (m.isDoublePush) (m.from + m.to) / 2 else Square.NONE
        val newHalf = if (abs(p) == PAWN || captured != 0) 0 else halfmoveClock + 1
        val newFull = if (!whiteToMove) fullmoveNumber + 1 else fullmoveNumber
        return Position(b, !whiteToMove, newCastling, newEp, newHalf, newFull)
    }

    /** Hands the turn to the other side without moving (used by lessons where only one side plays). */
    fun passTurn(): Position = Position(board, !whiteToMove, castling, Square.NONE, halfmoveClock, fullmoveNumber)

    fun count(piece: Int): Int = board.count { it == piece }

    /** Number of non-king pieces the given side has. */
    fun pieceCount(white: Boolean): Int = board.count { it != 0 && (it > 0) == white && abs(it) != KING }

    /** Total kid-style points (pawn = 1 ... queen = 9) for one side. */
    fun materialPoints(white: Boolean): Int =
        board.sumOf { if (it != 0 && (it > 0) == white) Piece.points(abs(it)) else 0 }

    fun squaresOf(piece: Int): List<Int> = (0 until 64).filter { board[it] == piece }

    fun isInsufficientMaterial(): Boolean {
        var minors = 0
        var lightBishops = 0
        var darkBishops = 0
        var knights = 0
        for (sq in 0 until 64) {
            when (abs(board[sq])) {
                PAWN, ROOK, QUEEN -> return false
                KNIGHT -> { minors++; knights++ }
                BISHOP -> {
                    minors++
                    if (Square.isLight(sq)) lightBishops++ else darkBishops++
                }
            }
        }
        if (minors <= 1) return true
        // Only bishops, all on the same color: no mate is possible.
        return knights == 0 && (lightBishops == 0 || darkBishops == 0)
    }

    /** Identifies the position for threefold-repetition checks. */
    fun repetitionKey(): String = toFen().split(' ').take(4).joinToString(" ")

    fun toFen(): String {
        val sb = StringBuilder()
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = board[Square.of(file, rank)]
                if (p == 0) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    val c = Piece.letter(abs(p))
                    sb.append(if (p > 0) c else c.lowercaseChar())
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }
        sb.append(if (whiteToMove) " w " else " b ")
        var c = ""
        if (castling and WHITE_KINGSIDE != 0) c += "K"
        if (castling and WHITE_QUEENSIDE != 0) c += "Q"
        if (castling and BLACK_KINGSIDE != 0) c += "k"
        if (castling and BLACK_QUEENSIDE != 0) c += "q"
        sb.append(c.ifEmpty { "-" })
        sb.append(' ')
        sb.append(if (epSquare == Square.NONE) "-" else Square.name(epSquare))
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber)
        return sb.toString()
    }

    override fun toString(): String = toFen()

    companion object {
        const val WHITE_KINGSIDE = 1
        const val WHITE_QUEENSIDE = 2
        const val BLACK_KINGSIDE = 4
        const val BLACK_QUEENSIDE = 8

        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        private val PROMOTION_TYPES = intArrayOf(QUEEN, ROOK, BISHOP, KNIGHT)

        /** Directions 0..3 are straight (rook), 4..7 are diagonal (bishop). */
        private val DIRECTIONS = arrayOf(
            intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1),
            intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1),
        )

        private val RAYS: Array<Array<IntArray>> = Array(8) { d ->
            Array(64) { sq ->
                val out = ArrayList<Int>()
                var f = Square.file(sq) + DIRECTIONS[d][0]
                var r = Square.rank(sq) + DIRECTIONS[d][1]
                while (Square.onBoard(f, r)) {
                    out += Square.of(f, r)
                    f += DIRECTIONS[d][0]
                    r += DIRECTIONS[d][1]
                }
                out.toIntArray()
            }
        }

        private fun stepTargets(offsets: List<Pair<Int, Int>>): Array<IntArray> = Array(64) { sq ->
            offsets.mapNotNull { (df, dr) ->
                val f = Square.file(sq) + df
                val r = Square.rank(sq) + dr
                if (Square.onBoard(f, r)) Square.of(f, r) else null
            }.toIntArray()
        }

        private val KNIGHT_TARGETS = stepTargets(
            listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
        )
        private val KING_TARGETS = stepTargets(
            listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
        )

        private val CASTLE_MASK = IntArray(64) { 15 }.also {
            it[4] = 15 and (WHITE_KINGSIDE or WHITE_QUEENSIDE).inv()
            it[0] = 15 and WHITE_QUEENSIDE.inv()
            it[7] = 15 and WHITE_KINGSIDE.inv()
            it[60] = 15 and (BLACK_KINGSIDE or BLACK_QUEENSIDE).inv()
            it[56] = 15 and BLACK_QUEENSIDE.inv()
            it[63] = 15 and BLACK_KINGSIDE.inv()
        }

        fun start(): Position = fromFen(START_FEN)

        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.isNotEmpty()) { "Empty FEN" }
            val board = IntArray(64)
            val ranks = parts[0].split('/')
            require(ranks.size == 8) { "FEN needs 8 ranks: $fen" }
            for ((i, row) in ranks.withIndex()) {
                val rank = 7 - i
                var file = 0
                for (c in row) {
                    if (c.isDigit()) {
                        file += c - '0'
                    } else {
                        require(file < 8) { "Too many squares in rank: $fen" }
                        board[Square.of(file, rank)] = Piece.fromLetter(c)
                        file++
                    }
                }
                require(file == 8) { "Bad rank '$row' in FEN: $fen" }
            }
            val white = parts.getOrElse(1) { "w" } == "w"
            var castling = 0
            val c = parts.getOrElse(2) { "-" }
            if ('K' in c) castling = castling or WHITE_KINGSIDE
            if ('Q' in c) castling = castling or WHITE_QUEENSIDE
            if ('k' in c) castling = castling or BLACK_KINGSIDE
            if ('q' in c) castling = castling or BLACK_QUEENSIDE
            val ep = parts.getOrElse(3) { "-" }.let { if (it == "-") Square.NONE else Square.parse(it) }
            val half = parts.getOrElse(4) { "0" }.toIntOrNull() ?: 0
            val full = parts.getOrElse(5) { "1" }.toIntOrNull() ?: 1
            return Position(board, white, castling, ep, half, full)
        }
    }
}
