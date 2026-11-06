package com.example.chess.engine

enum class EndReason {
    CHECKMATE,
    STALEMATE,
    INSUFFICIENT_MATERIAL,
    FIFTY_MOVES,
    REPETITION,
}

/** [whiteWins] is null for a draw. */
data class Outcome(val whiteWins: Boolean?, val reason: EndReason)

/** An immutable game record: every position from the start, plus the moves between them. */
class Game private constructor(
    val positions: List<Position>,
    val moves: List<Move>,
) {
    constructor(start: Position = Position.start()) : this(listOf(start), emptyList())

    val position: Position get() = positions.last()
    val lastMove: Move? get() = moves.lastOrNull()

    /** Lazily computed because finding legal moves isn't free. */
    val outcome: Outcome? by lazy { computeOutcome() }

    val isOver: Boolean get() = outcome != null

    fun play(move: Move): Game = Game(positions + position.makeMove(move), moves + move)

    /** Takes back up to [count] moves (never past the starting position). */
    fun undo(count: Int = 1): Game {
        val n = count.coerceAtMost(moves.size)
        if (n == 0) return this
        return Game(positions.dropLast(n), moves.dropLast(n))
    }

    private fun computeOutcome(): Outcome? {
        val pos = position
        if (!pos.hasLegalMove()) {
            return if (pos.inCheck()) Outcome(whiteWins = !pos.whiteToMove, reason = EndReason.CHECKMATE)
            else Outcome(null, EndReason.STALEMATE)
        }
        if (pos.isInsufficientMaterial()) return Outcome(null, EndReason.INSUFFICIENT_MATERIAL)
        if (pos.halfmoveClock >= 100) return Outcome(null, EndReason.FIFTY_MOVES)
        val key = pos.repetitionKey()
        if (positions.count { it.repetitionKey() == key } >= 3) return Outcome(null, EndReason.REPETITION)
        return null
    }
}
