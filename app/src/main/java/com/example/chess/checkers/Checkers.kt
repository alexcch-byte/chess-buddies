package com.example.chess.checkers

import com.example.chess.engine.Square
import kotlin.math.abs

/**
 * Checkers pieces, stored as signed ints like the chess engine: positive for Red, negative for Black.
 * Red starts at the bottom (ranks 1-3) and moves up the board.
 */
object Checker {
    const val EMPTY = 0
    const val RED = 1
    const val RED_KING = 2
    const val BLACK = -1
    const val BLACK_KING = -2

    fun isRed(piece: Int): Boolean = piece > 0
    fun isKing(piece: Int): Boolean = abs(piece) == 2
    fun crown(piece: Int): Int = if (piece > 0) RED_KING else BLACK_KING

    /** The rank a man must reach to be crowned. */
    fun kingRow(red: Boolean): Int = if (red) 7 else 0

    fun sideName(red: Boolean): String = if (red) "Red" else "Black"
}

/** One checkers move. A jump can hop several times; [path] lists every square landed on. */
data class CheckersMove(val path: List<Int>, val captured: List<Int> = emptyList()) {
    val from: Int get() = path.first()
    val to: Int get() = path.last()
    val isCapture: Boolean get() = captured.isNotEmpty()

    /** e.g. "c3-d4" or "c1xe3xg5". */
    fun notation(): String = path.joinToString(if (isCapture) "x" else "-") { Square.name(it) }
}

/** An immutable checkers position (American / English rules on an 8 x 8 board). */
class CheckersPosition private constructor(
    private val board: IntArray,
    val redToMove: Boolean,
    /** Plies in a row with only king moves and no captures, for the draw rule. */
    val quietPlies: Int,
) {
    operator fun get(sq: Int): Int = board[sq]

    /** Every legal move. Jumping is compulsory, so if any jump exists only jumps are returned. */
    fun legalMoves(): List<CheckersMove> {
        val jumps = captureMoves()
        return jumps.ifEmpty { simpleMoves() }
    }

    fun captureMoves(): List<CheckersMove> {
        val out = ArrayList<CheckersMove>()
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p != 0 && (p > 0) == redToMove) collectJumps(sq, out)
        }
        return out
    }

    fun simpleMoves(): List<CheckersMove> {
        val out = ArrayList<CheckersMove>()
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p == 0 || (p > 0) != redToMove) continue
            for ((df, dr) in directions(p)) {
                val f = Square.file(sq) + df
                val r = Square.rank(sq) + dr
                if (Square.onBoard(f, r) && board[Square.of(f, r)] == 0) out += CheckersMove(listOf(sq, Square.of(f, r)))
            }
        }
        return out
    }

    private fun collectJumps(origin: Int, out: MutableList<CheckersMove>) {
        val piece = board[origin]
        // The jumping piece has left its square, so a king may loop back through it.
        val b = board.copyOf()
        b[origin] = 0

        fun dfs(sq: Int, path: List<Int>, captured: List<Int>) {
            // A man that reaches the far row is crowned and its turn ends right there.
            if (path.size > 1 && !Checker.isKing(piece) && Square.rank(sq) == Checker.kingRow(piece > 0)) {
                out += CheckersMove(path, captured)
                return
            }
            var extended = false
            for ((df, dr) in directions(piece)) {
                val lf = Square.file(sq) + 2 * df
                val lr = Square.rank(sq) + 2 * dr
                if (!Square.onBoard(lf, lr)) continue
                val mid = Square.of(Square.file(sq) + df, Square.rank(sq) + dr)
                val land = Square.of(lf, lr)
                val m = b[mid]
                if (m == 0 || (m > 0) == (piece > 0) || mid in captured || b[land] != 0) continue
                extended = true
                dfs(land, path + land, captured + mid)
            }
            if (!extended && path.size > 1) out += CheckersMove(path, captured)
        }

        dfs(origin, listOf(origin), emptyList())
    }

    fun makeMove(m: CheckersMove): CheckersPosition {
        val b = board.copyOf()
        val p = b[m.from]
        b[m.from] = 0
        for (c in m.captured) b[c] = 0
        b[m.to] = if (!Checker.isKing(p) && Square.rank(m.to) == Checker.kingRow(p > 0)) Checker.crown(p) else p
        val quiet = if (m.isCapture || !Checker.isKing(p)) 0 else quietPlies + 1
        return CheckersPosition(b, !redToMove, quiet)
    }

    /** Hands the turn to the other side without moving (used by lessons where only one side plays). */
    fun passTurn(): CheckersPosition = CheckersPosition(board, !redToMove, quietPlies)

    fun pieceCount(red: Boolean): Int = board.count { it != 0 && (it > 0) == red }
    fun count(piece: Int): Int = board.count { it == piece }

    /** Pieces of the given side that the other side could jump if it were their turn. */
    fun piecesInDanger(red: Boolean): Set<Int> {
        val asOpponent = if (redToMove == red) passTurn() else this
        return asOpponent.captureMoves().flatMap { it.captured }.toSet()
    }

    /** Identifies the position for repetition checks and lesson searches. */
    fun key(): String {
        val sb = StringBuilder(66)
        for (p in board) sb.append(
            when (p) {
                Checker.RED -> 'r'
                Checker.RED_KING -> 'R'
                Checker.BLACK -> 'b'
                Checker.BLACK_KING -> 'B'
                else -> '.'
            }
        )
        sb.append(if (redToMove) 'r' else 'b')
        return sb.toString()
    }

    companion object {
        private val MAN_UP = listOf(-1 to 1, 1 to 1)
        private val MAN_DOWN = listOf(-1 to -1, 1 to -1)
        private val ALL = MAN_UP + MAN_DOWN

        private fun directions(piece: Int): List<Pair<Int, Int>> = when {
            Checker.isKing(piece) -> ALL
            piece > 0 -> MAN_UP
            else -> MAN_DOWN
        }

        /** Checkers live on the dark squares, where file + rank is even (a1 is dark). */
        fun isDark(sq: Int): Boolean = (Square.file(sq) + Square.rank(sq)) % 2 == 0

        fun start(): CheckersPosition {
            val b = IntArray(64)
            for (sq in 0 until 64) {
                if (!isDark(sq)) continue
                when (Square.rank(sq)) {
                    0, 1, 2 -> b[sq] = Checker.RED
                    5, 6, 7 -> b[sq] = Checker.BLACK
                }
            }
            return CheckersPosition(b, redToMove = true, quietPlies = 0)
        }

        /**
         * Builds a position from space-separated square names, e.g. `setup(red = "c3 e3", black = "d4")`.
         * Every square must be a dark square.
         */
        fun setup(
            red: String = "",
            black: String = "",
            redKings: String = "",
            blackKings: String = "",
            redToMove: Boolean = true,
        ): CheckersPosition {
            val b = IntArray(64)
            fun place(names: String, piece: Int) {
                for (name in names.split(' ').filter { it.isNotBlank() }) {
                    val sq = Square.parse(name)
                    require(sq != Square.NONE && isDark(sq)) { "$name is not a dark square" }
                    b[sq] = piece
                }
            }
            place(red, Checker.RED)
            place(black, Checker.BLACK)
            place(redKings, Checker.RED_KING)
            place(blackKings, Checker.BLACK_KING)
            return CheckersPosition(b, redToMove, 0)
        }
    }
}

enum class CheckersEndReason { NO_PIECES, NO_MOVES, DRAW_QUIET, DRAW_REPETITION }

/** [redWins] is null for a draw. */
data class CheckersOutcome(val redWins: Boolean?, val reason: CheckersEndReason)

/** An immutable checkers game record. */
class CheckersGame private constructor(
    val positions: List<CheckersPosition>,
    val moves: List<CheckersMove>,
) {
    constructor(start: CheckersPosition = CheckersPosition.start()) : this(listOf(start), emptyList())

    val position: CheckersPosition get() = positions.last()
    val lastMove: CheckersMove? get() = moves.lastOrNull()

    val outcome: CheckersOutcome? by lazy { computeOutcome() }
    val isOver: Boolean get() = outcome != null

    fun play(move: CheckersMove): CheckersGame = CheckersGame(positions + position.makeMove(move), moves + move)

    fun undo(count: Int = 1): CheckersGame {
        val n = count.coerceAtMost(moves.size)
        if (n == 0) return this
        return CheckersGame(positions.dropLast(n), moves.dropLast(n))
    }

    private fun computeOutcome(): CheckersOutcome? {
        val pos = position
        if (pos.legalMoves().isEmpty()) {
            val reason = if (pos.pieceCount(pos.redToMove) == 0) CheckersEndReason.NO_PIECES else CheckersEndReason.NO_MOVES
            return CheckersOutcome(redWins = !pos.redToMove, reason = reason)
        }
        if (pos.quietPlies >= DRAW_PLIES) return CheckersOutcome(null, CheckersEndReason.DRAW_QUIET)
        val key = pos.key()
        if (positions.count { it.key() == key } >= 3) return CheckersOutcome(null, CheckersEndReason.DRAW_REPETITION)
        return null
    }

    companion object {
        /** 40 moves each with only kings moving and nothing captured. */
        const val DRAW_PLIES = 80
    }
}
