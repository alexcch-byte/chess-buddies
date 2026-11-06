package com.example.chess.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.chess.audio.SoundFx
import com.example.chess.audio.SoundFx.Effect
import com.example.chess.checkers.Checker
import com.example.chess.checkers.CheckersAi
import com.example.chess.checkers.CheckersCoach
import com.example.chess.checkers.CheckersEndReason
import com.example.chess.checkers.CheckersGame
import com.example.chess.checkers.CheckersMove
import com.example.chess.checkers.CheckersOutcome
import com.example.chess.checkers.CheckersPosition
import com.example.chess.data.GameKind
import com.example.chess.data.ProgressStore
import com.example.chess.engine.Bot
import com.example.chess.engine.Square
import com.example.chess.net.NearbySession
import com.example.chess.ui.board.CheckersAnimation
import com.example.chess.ui.components.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A checkers game against a [bot], or between two players sharing the device when [bot] is null,
 * or against a friend on another tablet when [nearby] is set.
 */
class CheckersMatchController(
    override val bot: Bot?,
    playerRedAtStart: Boolean,
    private val scope: CoroutineScope,
    private val sound: SoundFx,
    private val progress: ProgressStore,
    private val nearby: NearbySession? = null,
) : MatchSession {
    /** Which colour the player has. In a game on two tablets the colours swap each time you play again. */
    var playerRed by mutableStateOf(playerRedAtStart)
        private set
    var game by mutableStateOf(CheckersGame())
        private set
    var animation by mutableStateOf<CheckersAnimation?>(null)
        private set
    override var thinking by mutableStateOf(false)
        private set
    override var coachText by mutableStateOf("")
        private set
    override var mood by mutableStateOf(Mood.NORMAL)
        private set
    var hint by mutableStateOf<CheckersMove?>(null)
        private set
    override var takeback by mutableStateOf<String?>(null)
        private set
    var outcome by mutableStateOf<CheckersOutcome?>(null)
        private set
    override var showResult by mutableStateOf(false)
    override var gameNumber by mutableIntStateOf(0)
        private set
    override var flipped by mutableStateOf(!playerRed)

    private var botJob: Job? = null
    private var hintJob: Job? = null
    private var animationId = 0L
    private var botMovesSinceTip = 0

    val position: CheckersPosition get() = game.position

    override val playerFirst: Boolean get() = playerRed
    override val firstSideName: String get() = "Red"
    override val secondSideName: String get() = "Black"
    override val firstSideToMove: Boolean get() = position.redToMove

    /** Set up in [init] for a game on two tablets. */
    private var link: NearbyMatch? = null

    override val nearbySideFirst: Boolean? get() = if (nearby != null) playerRed else null
    override val connectionNote: String?
        get() = if (link?.linkLive == false) "Waiting for your friend to come back…" else null
    override val friendRequest: String? get() = link?.request
    override val friendLeft: Boolean get() = link?.friendLeft == true
    override fun answerFriendRequest(yes: Boolean) {
        link?.answerRequest(yes)
    }

    /** True when "you" are one particular side, rather than two people sharing the screen. */
    private val oneSided: Boolean get() = bot != null || nearby != null

    override val isHumanTurn: Boolean
        get() = outcome == null && takeback == null && !thinking &&
            if (nearby != null) link?.linkLive == true && !friendLeft && position.redToMove == playerRed
            else (bot == null || position.redToMove == playerRed)

    override val canUndo: Boolean
        get() = if (nearby != null) false else if (bot == null) game.moves.isNotEmpty() else game.moves.size >= (if (playerRed) 1 else 2)

    override val result: MatchResult?
        get() = outcome?.let { o ->
            val won = o.redWins != null && (!oneSided || o.redWins == playerRed)
            MatchResult(
                headline = resultHeadline(o),
                explanation = resultExplanation(o),
                emoji = when {
                    o.redWins == null -> "🤝"
                    won -> "🏆"
                    else -> "💪"
                },
                celebrate = won,
            )
        }

    private val botName: String get() = bot?.displayName?.substringBefore(' ') ?: ""

    /** How many checkers the friend's last move jumped (0 for none), for the coach to mention. */
    private var friendJumped = 0

    private fun wireOf(m: CheckersMove) = m.path.joinToString("-")

    /** How a game on two tablets reaches into this controller. */
    private val nearbyGame = object : NearbyGame {
        override fun wires(): List<String> = game.moves.map { wireOf(it) }
        override val isOver: Boolean get() = game.isOver

        override fun playRemote(wire: String, animate: Boolean): Boolean {
            val move = position.legalMoves().firstOrNull { wireOf(it) == wire } ?: return false
            hintJob?.cancel()
            friendJumped = move.captured.size
            this@CheckersMatchController.apply(move, animate)
            return true
        }

        override fun rebuild(wires: List<String>): Boolean {
            var g = CheckersGame()
            for (w in wires) g = g.play(g.position.legalMoves().firstOrNull { wireOf(it) == w } ?: return false)
            game = g
            animation = null
            hint = null
            outcome = null
            showResult = false
            friendJumped = 0
            return true
        }

        override fun afterRemoteMoves() {
            if (game.isOver) finish() else nearbyCoach()
        }

        override fun onConnection(live: Boolean) {
            if (live) nearbyCoach() else {
                mood = Mood.OOPS
                coachText = "Your friend's tablet went quiet. Waiting for it to come back…"
            }
        }

        override fun onRematchDeclined() {
            mood = Mood.NORMAL
            coachText = "Your friend doesn't want to play again right now. You can look at the board."
        }

        override fun onNewGame(iAmFirst: Boolean) {
            playerRed = iAmFirst
            flipped = !iAmFirst
            resetGame()
        }

        override fun onFriendLeft() {
            mood = Mood.NORMAL
            coachText = "Your friend left the game."
        }
    }

    init {
        greet()
        if (bot != null && !playerRed) scheduleBotMove(null)
        if (nearby != null) link = NearbyMatch(nearby, nearbyGame)
    }

    private fun greet() {
        mood = Mood.NORMAL
        coachText = when {
            nearby != null -> if (playerRed) "You're playing a friend! You're Red, so you go first. Remember: if you can jump, you must!"
            else "You're playing a friend! You're Black, so your friend goes first."
            bot == null -> "Red moves first. Take turns, and remember: if you can jump, you must!"
            playerRed -> "$botName wants to play checkers! You're Red, so you go first."
            else -> "$botName wants to play checkers! You're Black, so $botName goes first."
        }
    }

    override fun newGame() {
        if (nearby != null) {
            // Both players have to agree. The board resets when the friend says yes.
            link?.askRematch()
            showResult = false
            mood = Mood.NORMAL
            coachText = "I asked your friend to play again. Waiting for a yes…"
        } else {
            resetGame()
        }
    }

    private fun resetGame() {
        botJob?.cancel()
        hintJob?.cancel()
        game = CheckersGame()
        animation = null
        thinking = false
        hint = null
        takeback = null
        outcome = null
        showResult = false
        botMovesSinceTip = 0
        gameNumber++
        greet()
        if (bot != null && !playerRed) scheduleBotMove(null)
    }

    fun onSelect() = sound.play(Effect.SELECT)

    fun onIllegal(from: Int, to: Int) {
        if (!isHumanTurn) return
        sound.play(Effect.ILLEGAL)
        mood = Mood.OOPS
        coachText = CheckersCoach.explainIllegal(position, from, to)
    }

    fun onBoardMove(move: CheckersMove, animate: Boolean) {
        if (!isHumanTurn) return
        hintJob?.cancel()
        val before = position
        apply(move, animate)
        link?.sendMove(wireOf(move))
        when {
            game.isOver -> finish()
            nearby != null -> {
                friendJumped = 0
                nearbyCoach()
            }
            bot == null -> friendCoach()
            else -> scheduleBotMove(if (progress.coachTips) before to move else null)
        }
    }

    override fun acceptTakeback() {
        takeback = null
        game = game.undo(1)
        animation = null
        mood = Mood.NORMAL
        coachText = "Okay! Look for a move where your checkers stay safe."
    }

    override fun declineTakeback() {
        takeback = null
        mood = Mood.NORMAL
        scheduleBotMove(null)
    }

    override fun undo() {
        if (!canUndo) return
        botJob?.cancel()
        hintJob?.cancel()
        thinking = false
        takeback = null
        outcome = null
        showResult = false
        hint = null
        animation = null
        var g = game.undo(1)
        if (bot != null) {
            while (g.moves.isNotEmpty() && g.position.redToMove != playerRed) g = g.undo(1)
        }
        game = g
        mood = Mood.NORMAL
        coachText = "Move taken back. Try something new!"
        if (bot != null && position.redToMove != playerRed) scheduleBotMove(null)
    }

    override fun requestHint() {
        if (!isHumanTurn) return
        val pos = position
        hintJob?.cancel()
        mood = Mood.NORMAL
        coachText = "Hmm, let me think… 🤔"
        hintJob = scope.launch {
            val move = withContext(Dispatchers.Default) { CheckersAi.bestMove(pos, maxDepth = 10, timeLimitMs = 1200) }
            if (move != null && position === pos) {
                hint = move
                coachText = if (move.isCapture) "💡 Jump with the checker on ${Square.name(move.from)}!"
                else "💡 How about moving the checker on ${Square.name(move.from)} to ${Square.name(move.to)}?"
            }
        }
    }

    private fun apply(move: CheckersMove, animate: Boolean) {
        val before = position
        game = game.play(move)
        hint = null
        animation = if (animate) CheckersAnimation(move, before, ++animationId) else null
        val crowned = !Checker.isKing(before[move.from]) && Checker.isKing(position[move.to])
        sound.play(
            when {
                crowned -> Effect.PROMOTE
                move.isCapture -> Effect.CAPTURE
                else -> Effect.MOVE
            }
        )
    }

    /**
     * Lets the bot reply. If [check] holds the child's last move, the coach first makes sure that
     * move didn't throw checkers away, and offers a take-back if it did.
     */
    private fun scheduleBotMove(check: Pair<CheckersPosition, CheckersMove>?) {
        val b = bot ?: return
        thinking = true
        val start = game
        botJob?.cancel()
        botJob = scope.launch {
            val begun = System.currentTimeMillis()
            if (check != null) {
                val warning = withContext(Dispatchers.Default) { blunderWarning(check.first, check.second) }
                if (warning != null && game === start) {
                    thinking = false
                    takeback = warning
                    mood = Mood.OOPS
                    coachText = warning
                    return@launch
                }
            }
            val move = withContext(Dispatchers.Default) { CheckersAi.chooseMove(start.position, b) }
            val wait = MIN_THINK_MS - (System.currentTimeMillis() - begun)
            if (wait > 0) delay(wait)
            if (game !== start || move == null) {
                thinking = false
                return@launch
            }
            val before = position
            apply(move, animate = true)
            thinking = false
            if (game.isOver) finish() else botCoach(before, move)
        }
    }

    /** Compares the child's move with the best one; a big drop means checkers were given away. */
    private fun blunderWarning(before: CheckersPosition, move: CheckersMove): String? {
        val scores = CheckersAi.scoreMoves(before, depth = 5, timeLimitMs = 700)
        val mine = scores[move] ?: return null
        val best = scores.values.maxOrNull() ?: return null
        if (best - mine < 90) return null
        val biggestJump = before.makeMove(move).captureMoves().maxOfOrNull { it.captured.size } ?: 0
        return when {
            biggestJump >= 2 -> "Careful! After that move, $botName can double jump you. Want to take it back?"
            biggestJump == 1 -> "Careful! $botName could jump your checker. Want to take that move back?"
            else -> "Hmm, that move could let $botName win a checker soon. Want to try a different move?"
        }
    }

    private fun botCoach(before: CheckersPosition, move: CheckersMove) {
        val pos = position
        val me = pos.redToMove
        val tips = progress.coachTips
        val myJumps = pos.captureMoves()
        val biggest = myJumps.maxOfOrNull { it.captured.size } ?: 0
        val botCrowned = !Checker.isKing(before[move.from]) && Checker.isKing(pos[move.to])
        mood = Mood.NORMAL
        coachText = when {
            move.isCapture -> {
                mood = Mood.OOPS
                val n = move.captured.size
                val took = if (n == 1) "jumped your checker" else "jumped $n of your checkers"
                "$botName $took!" + if (myJumps.isNotEmpty()) " You have a jump now. You must take it!" else ""
            }
            botCrowned -> "$botName got a king! Watch out: kings can move backward."
            biggest >= 2 -> {
                mood = Mood.HAPPY
                "✨ You have a double jump! Can you find it?"
            }
            myJumps.isNotEmpty() -> "You have a jump! In checkers, you must take it."
            tips && pos.piecesInDanger(me).isNotEmpty() -> {
                mood = Mood.OOPS
                "Careful! Your checker on ${Square.name(pos.piecesInDanger(me).first())} could be jumped."
            }
            tips && ++botMovesSinceTip >= 2 -> {
                botMovesSinceTip = 0
                TIPS.random()
            }
            else -> YOUR_TURN.random()
        }
    }

    /** What the coach says in a game on two tablets, after either player's move. */
    private fun nearbyCoach() {
        val pos = position
        val mine = pos.redToMove == playerRed
        mood = Mood.NORMAL
        coachText = when {
            !mine -> "Your friend is thinking…"
            friendJumped > 0 -> {
                mood = Mood.OOPS
                (if (friendJumped == 1) "Your friend jumped your checker!" else "Your friend jumped $friendJumped of your checkers!") +
                    if (pos.captureMoves().isNotEmpty()) " You have a jump now. You must take it!" else ""
            }
            pos.captureMoves().isNotEmpty() -> "You have a jump! In checkers, you must take it."
            else -> YOUR_TURN.random()
        }
    }

    private fun friendCoach() {
        val side = Checker.sideName(position.redToMove)
        mood = Mood.NORMAL
        coachText = if (position.captureMoves().isNotEmpty()) "$side's turn. $side has a jump, and must take it!" else "$side's turn!"
    }

    private fun finish() {
        val o = game.outcome ?: return
        outcome = o
        thinking = false
        hint = null
        val b = bot
        when {
            o.redWins == null -> {
                mood = Mood.NORMAL
                sound.play(Effect.SUCCESS)
            }
            (b == null && nearby == null) || o.redWins == playerRed -> {
                mood = Mood.HAPPY
                if (b != null) progress.recordWin(GameKind.CHECKERS, b)
                sound.play(Effect.WIN)
            }
            else -> {
                mood = Mood.OOPS
                sound.play(Effect.LOSE)
            }
        }
        coachText = resultHeadline(o) + " " + resultExplanation(o)
        scope.launch {
            delay(1100)
            showResult = true
        }
    }

    private fun resultHeadline(o: CheckersOutcome): String = when {
        o.redWins == null -> "It's a draw!"
        nearby != null -> if (o.redWins == playerRed) "You won!" else "Your friend won this time."
        bot == null -> if (o.redWins) "Red wins!" else "Black wins!"
        o.redWins == playerRed -> "You won!"
        else -> "$botName won this time."
    }

    private fun resultExplanation(o: CheckersOutcome): String {
        val playerWon = o.redWins != null && o.redWins == playerRed
        return when (o.reason) {
            CheckersEndReason.NO_PIECES -> when {
                nearby != null -> if (playerWon) "You captured all of your friend's checkers. Amazing!" else "Your friend captured all your checkers. Want a rematch?"
                bot == null -> "Every one of the other side's checkers was captured."
                playerWon -> "You captured all of $botName's checkers. Amazing!"
                else -> "$botName captured all your checkers. Want a rematch?"
            }
            CheckersEndReason.NO_MOVES -> when {
                nearby != null -> if (playerWon) "Your friend's checkers were trapped with no moves left!" else "Your checkers got trapped with no moves left. Want a rematch?"
                bot == null -> "The other side was trapped with no moves left."
                playerWon -> "$botName's checkers were trapped with no moves left!"
                else -> "Your checkers got trapped with no moves left. Want a rematch?"
            }
            CheckersEndReason.DRAW_QUIET -> "40 moves each went by with no jumps, so nobody wins."
            CheckersEndReason.DRAW_REPETITION -> "The same position happened three times."
        }
    }

    private companion object {
        const val MIN_THINK_MS = 650L

        val YOUR_TURN = listOf("Your turn!", "Your move! Take your time.", "What will you do next?", "Your turn. Can any of your checkers be jumped?")

        val TIPS = listOf(
            "Tip: Keep checkers on your back row as long as you can. Then the other side can't get kings!",
            "Tip: Checkers on the edge of the board can't be jumped.",
            "Tip: Try to get a king! Kings can move backward too.",
            "Tip: Move your checkers in groups so they protect each other.",
            "Tip: Before you move, check whether your checker could be jumped.",
            "Tip: Sometimes giving away one checker lets you jump two!",
        )
    }
}
