package com.example.chess.checkers

import com.example.chess.engine.Square
import com.example.chess.lessons.LessonInfo
import com.example.chess.lessons.LessonUnit

/** What the child has to do to finish a checkers challenge. The child always plays Red. */
sealed interface CheckersGoal {
    /** Multi-move goals are played by the child alone; the black checkers stand still. */
    val multiMove: Boolean get() = false

    data class CollectStars(val stars: Set<Int>) : CheckersGoal {
        override val multiMove get() = true
    }

    data object CaptureAll : CheckersGoal {
        override val multiMove get() = true
    }

    data object Crown : CheckersGoal {
        override val multiMove get() = true
    }

    /** Capture at least [atLeast] checkers in a single move. */
    data class Capture(val atLeast: Int) : CheckersGoal

    /** Move so that Black has no jump. */
    data object SafeMove : CheckersGoal

    /** Leave Black with no checkers or no moves. */
    data object Win : CheckersGoal

    /**
     * Two steps: offer a checker so Black is forced to jump, then jump back for [atLeast] checkers.
     */
    data class Shot(val atLeast: Int) : CheckersGoal
}

data class CheckersChallenge(
    val position: CheckersPosition,
    val instruction: String,
    val goal: CheckersGoal,
    val wrongText: String? = null,
)

data class CheckersLesson(
    override val id: String,
    override val title: String,
    override val tagline: String,
    /** Checker drawn on the lesson card. */
    val icon: Int,
    override val intro: List<String>,
    val challenges: List<CheckersChallenge>,
) : LessonInfo {
    override val challengeCount: Int get() = challenges.size
}

object CheckersRules {

    /** For one-shot goals: does this move solve the puzzle? */
    fun solves(goal: CheckersGoal, move: CheckersMove, after: CheckersPosition): Boolean = when (goal) {
        is CheckersGoal.Capture -> move.captured.size >= goal.atLeast
        CheckersGoal.SafeMove -> after.captureMoves().isEmpty()
        CheckersGoal.Win -> after.legalMoves().isEmpty()
        is CheckersGoal.Shot -> shotWorks(after, goal.atLeast)
        else -> false
    }

    /** True if every reply Black can make lets Red jump at least [atLeast] checkers next move. */
    fun shotWorks(after: CheckersPosition, atLeast: Int): Boolean {
        val replies = after.legalMoves()
        if (replies.isEmpty() || !replies.first().isCapture) return false
        return replies.all { r -> after.makeMove(r).captureMoves().any { it.captured.size >= atLeast } }
    }

    /** For multi-move goals: is the job done? [starsLeft] already excludes the landing square. */
    fun finished(goal: CheckersGoal, before: CheckersPosition, move: CheckersMove, after: CheckersPosition, starsLeft: Set<Int>): Boolean =
        when (goal) {
            is CheckersGoal.CollectStars -> starsLeft.isEmpty()
            CheckersGoal.CaptureAll -> after.pieceCount(red = false) == 0
            CheckersGoal.Crown -> !Checker.isKing(before[move.from]) && Checker.isKing(after[move.to])
            else -> false
        }

    fun wrongAnswerText(challenge: CheckersChallenge, move: CheckersMove, after: CheckersPosition): String {
        challenge.wrongText?.let { return it }
        return when (val goal = challenge.goal) {
            is CheckersGoal.Capture -> "You captured ${move.captured.size}, but you can capture ${goal.atLeast}! Try again."
            CheckersGoal.SafeMove -> "Uh-oh, Black can jump that checker! Find a move where your checker stays safe."
            CheckersGoal.Win ->
                if (after.captureMoves().isNotEmpty()) "Oops, now Black can jump you! Try again."
                else "Black can still move. Find a move that leaves Black stuck!"
            is CheckersGoal.Shot -> "That doesn't set up a double jump. Which checker could you give away?"
            else -> "Not quite! Try again."
        }
    }

    fun squares(names: String): Set<Int> = names.split(' ').filter { it.isNotBlank() }.map { Square.parse(it) }.toSet()
}

/** Finds the best way through a challenge, for star ratings and hints. */
object CheckersSolver {
    data class Plan(val firstMove: CheckersMove, val length: Int)

    fun solveMultiMove(pos: CheckersPosition, goal: CheckersGoal, starsLeft: Set<Int>, maxStates: Int = 100_000): Plan? {
        class Node(val pos: CheckersPosition, val stars: Set<Int>, val first: CheckersMove?, val depth: Int)

        val seen = HashSet<String>()
        val queue = ArrayDeque<Node>()
        queue.add(Node(pos, starsLeft, null, 0))
        seen.add(pos.key() + starsLeft.sorted())
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for (m in node.pos.legalMoves()) {
                val after = node.pos.makeMove(m)
                val stars = node.stars - m.to
                val first = node.first ?: m
                if (CheckersRules.finished(goal, node.pos, m, after, stars)) return Plan(first, node.depth + 1)
                val next = after.passTurn()
                if (seen.add(next.key() + stars.sorted())) {
                    if (seen.size > maxStates) return null
                    queue.add(Node(next, stars, first, node.depth + 1))
                }
            }
        }
        return null
    }

    fun solveOneShot(pos: CheckersPosition, goal: CheckersGoal): CheckersMove? =
        pos.legalMoves().firstOrNull { CheckersRules.solves(goal, it, pos.makeMove(it)) }
}

object CheckersLessonCatalog {
    private fun stars(names: String) = CheckersGoal.CollectStars(CheckersRules.squares(names))
    private fun setup(red: String = "", black: String = "", redKings: String = "") =
        CheckersPosition.setup(red = red, black = black, redKings = redKings)

    private val moving = CheckersLesson(
        id = "ck_moves",
        title = "How Checkers Move",
        tagline = "Diagonal steps on dark squares",
        icon = Checker.RED,
        intro = listOf(
            "Welcome to checkers! Every checker lives on the dark squares.",
            "A checker moves one step diagonally forward, to an empty dark square.",
            "Red always moves first. You'll play the Red checkers!",
        ),
        challenges = listOf(
            CheckersChallenge(setup(red = "c3"), "Tap your checker, then tap the star. Checkers move diagonally forward!", stars("d4")),
            CheckersChallenge(setup(red = "c1"), "Collect both stars. Only forward, only diagonal!", stars("e3 g5")),
            CheckersChallenge(setup(red = "a3 g3"), "You have two checkers. Grab both stars!", stars("b4 h4")),
        ),
    )

    private val jumping = CheckersLesson(
        id = "ck_jump",
        title = "Jumping",
        tagline = "Hop over to capture",
        icon = Checker.BLACK,
        intro = listOf(
            "In checkers, you capture by JUMPING!",
            "If an enemy checker is diagonally in front of yours, and the square behind it is empty, hop over it and it's out of the game.",
        ),
        challenges = listOf(
            CheckersChallenge(setup(red = "c3", black = "d4"), "An enemy checker is right in front of yours. Jump over it!", CheckersGoal.Capture(1)),
            CheckersChallenge(setup(red = "b2", black = "c3 f6"), "Capture both black checkers! You may need to move closer first.", CheckersGoal.CaptureAll),
            CheckersChallenge(
                setup(red = "c3", black = "b4 d4 f6 g7"),
                "You can jump two different ways. Pick the jump where Black can't jump you back!",
                CheckersGoal.SafeMove,
                wrongText = "Oops! The black checker on f6 can jump you back. Find the safe jump!",
            ),
        ),
    )

    private val doubleJumps = CheckersLesson(
        id = "ck_double",
        title = "Double Jumps",
        tagline = "Keep on jumping!",
        icon = Checker.RED,
        intro = listOf(
            "Here's the exciting part: after a jump, if your checker can jump AGAIN, it keeps going!",
            "That's called a double jump. Sometimes you can even do a triple jump!",
            "Tap each square you land on, one hop at a time.",
        ),
        challenges = listOf(
            CheckersChallenge(setup(red = "c1", black = "d2 f4"), "Jump, then jump again! Capture two checkers in one turn.", CheckersGoal.Capture(2)),
            CheckersChallenge(setup(red = "b2", black = "c3 e5 e7"), "Can you find a TRIPLE jump?", CheckersGoal.Capture(3)),
            CheckersChallenge(
                setup(red = "e3", black = "b6 d4 f4"),
                "Two different jumps! Pick the one that captures the most.",
                CheckersGoal.Capture(2),
                wrongText = "You got one, but there was a double jump! Try again.",
            ),
            CheckersChallenge(setup(red = "c3", black = "d4 d6"), "Zig-zag! Jump one way, then the other way.", CheckersGoal.Capture(2)),
        ),
    )

    private val kings = CheckersLesson(
        id = "ck_king",
        title = "Crown a King",
        tagline = "Kings can go backward",
        icon = Checker.RED_KING,
        intro = listOf(
            "When your checker reaches the very last row, it gets crowned. Now it's a KING!",
            "Kings are extra powerful: they can move and jump backward as well as forward.",
        ),
        challenges = listOf(
            CheckersChallenge(setup(red = "c5"), "March your checker to the last row to crown it!", CheckersGoal.Crown),
            CheckersChallenge(setup(redKings = "d4"), "Kings can move backward! Collect the stars behind your king.", stars("c3 d2 e3")),
            CheckersChallenge(setup(red = "g7"), "Get crowned, then use your new king to grab the star behind you!", stars("e5")),
            CheckersChallenge(setup(redKings = "e5", black = "b4 d4"), "Kings can jump backward too. Find the double jump!", CheckersGoal.Capture(2)),
        ),
    )

    private val mustJump = CheckersLesson(
        id = "ck_mustjump",
        title = "You Must Jump!",
        tagline = "The rule that sets traps",
        icon = Checker.BLACK_KING,
        intro = listOf(
            "Here's a big checkers rule: if you CAN jump, you MUST jump!",
            "Sneaky players use this rule to set traps. They give away one checker so they can jump two!",
            "Now it's your turn to set the trap.",
        ),
        challenges = listOf(
            CheckersChallenge(setup(red = "a3 c3 g3", black = "f4"), "One of your checkers MUST jump. Which one?", CheckersGoal.Capture(1)),
            CheckersChallenge(
                setup(red = "c1 d2 e3 g3", black = "e5 g5"),
                "Set a trap! Give Black one checker, so you can jump two of theirs.",
                CheckersGoal.Shot(2),
            ),
            CheckersChallenge(
                setup(red = "a1 b2 c3 e3", black = "c5 e5"),
                "Try that trick again! Which checker should you offer this time?",
                CheckersGoal.Shot(2),
            ),
        ),
    )

    private val safety = CheckersLesson(
        id = "ck_safe",
        title = "Stay Safe",
        tagline = "Don't get jumped",
        icon = Checker.RED,
        intro = listOf(
            "Before you move, always ask: can my checker be jumped?",
            "Checkers on the edge of the board can never be jumped. And checkers that stand together protect each other!",
        ),
        challenges = listOf(
            CheckersChallenge(setup(red = "c3 e3", black = "c5"), "Make a move where Black can't jump you.", CheckersGoal.SafeMove),
            CheckersChallenge(
                setup(red = "b4 d4", black = "b6 d6 f6"),
                "Only ONE move is safe here. Hint: the edge is a safe place!",
                CheckersGoal.SafeMove,
            ),
        ),
    )

    private val winning = CheckersLesson(
        id = "ck_win",
        title = "Winning the Game",
        tagline = "Capture them all, or trap them",
        icon = Checker.RED_KING,
        intro = listOf(
            "You win at checkers when your opponent has no checkers left...",
            "...or when they're stuck and can't make any move at all!",
        ),
        challenges = listOf(
            CheckersChallenge(setup(red = "c3", black = "d4 f6"), "Capture Black's last two checkers to win!", CheckersGoal.Win),
            CheckersChallenge(setup(red = "f2 h2", black = "h4"), "Black has one checker left. Block it so it can't move!", CheckersGoal.Win),
            CheckersChallenge(setup(red = "a3 c3", black = "a5"), "Trap the last black checker again!", CheckersGoal.Win),
        ),
    )

    val units: List<LessonUnit<CheckersLesson>> = listOf(
        LessonUnit("Checkers Basics", "🔴", listOf(moving, jumping, doubleJumps)),
        LessonUnit("Kings and Tricks", "👑", listOf(kings, mustJump)),
        LessonUnit("Play to Win", "🏆", listOf(safety, winning)),
    )

    val lessons: List<CheckersLesson> = units.flatMap { it.lessons }

    fun byId(id: String): CheckersLesson? = lessons.firstOrNull { it.id == id }

    fun next(id: String): CheckersLesson? {
        val i = lessons.indexOfFirst { it.id == id }
        return lessons.getOrNull(i + 1)
    }
}
