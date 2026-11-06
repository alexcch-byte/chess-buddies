package com.example.chess.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.chess.audio.SoundFx
import com.example.chess.audio.SoundFx.Effect
import com.example.chess.data.GameKind
import com.example.chess.data.ProgressStore
import com.example.chess.engine.Ai
import com.example.chess.engine.Bot
import com.example.chess.engine.Coach
import com.example.chess.engine.EndReason
import com.example.chess.engine.Game
import com.example.chess.engine.Move
import com.example.chess.engine.Notation
import com.example.chess.engine.Outcome
import com.example.chess.engine.Piece
import com.example.chess.engine.Position
import com.example.chess.engine.Square
import com.example.chess.net.NearbySession
import com.example.chess.ui.board.MoveAnimation
import com.example.chess.ui.components.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * A chess game against a [bot], or between two players sharing the device when [bot] is null,
 * or against a friend on another tablet when [nearby] is set.
 */
class MatchController(
    override val bot: Bot?,
    playerWhiteAtStart: Boolean,
    private val scope: CoroutineScope,
    private val sound: SoundFx,
    private val progress: ProgressStore,
    private val nearby: NearbySession? = null,
) : MatchSession {
    /** Which colour the player has. In a game on two tablets the colours swap each time you play again. */
    var playerWhite by mutableStateOf(playerWhiteAtStart)
        private set
    var game by mutableStateOf(Game())
        private set
    var animation by mutableStateOf<MoveAnimation?>(null)
        private set
    override var thinking by mutableStateOf(false)
        private set
    override var coachText by mutableStateOf("")
        private set
    override var mood by mutableStateOf(Mood.NORMAL)
        private set
    var hint by mutableStateOf<Move?>(null)
        private set
    var pendingPromotion by mutableStateOf<List<Move>?>(null)
        private set
    override var takeback by mutableStateOf<String?>(null)
        private set
    var outcome by mutableStateOf<Outcome?>(null)
        private set
    override var showResult by mutableStateOf(false)
    override var gameNumber by mutableIntStateOf(0)
        private set
    override var flipped by mutableStateOf(!playerWhite)

    private var botJob: Job? = null
    private var hintJob: Job? = null
    private var animationId = 0L
    private var pendingDragged = false
    private var botMovesSinceTip = 0

    val position: Position get() = game.position

    override val playerFirst: Boolean get() = playerWhite
    override val firstSideName: String get() = "White"
    override val secondSideName: String get() = "Black"
    override val firstSideToMove: Boolean get() = position.whiteToMove

    /** Set up in [init] for a game on two tablets. */
    private var link: NearbyMatch? = null

    override val nearbySideFirst: Boolean? get() = if (nearby != null) playerWhite else null
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
            if (nearby != null) link?.linkLive == true && !friendLeft && position.whiteToMove == playerWhite
            else (bot == null || position.whiteToMove == playerWhite)

    override val canUndo: Boolean
        get() = if (nearby != null) false else if (bot == null) game.moves.isNotEmpty() else game.moves.size >= (if (playerWhite) 1 else 2)

    override val result: MatchResult?
        get() = outcome?.let { o ->
            val won = o.whiteWins != null && (!oneSided || o.whiteWins == playerWhite)
            MatchResult(
                headline = resultHeadline(o),
                explanation = resultExplanation(o),
                emoji = when {
                    o.whiteWins == null -> "🤝"
                    won -> "🏆"
                    else -> "💪"
                },
                celebrate = won,
            )
        }

    private val botName: String get() = bot?.displayName?.substringBefore(' ') ?: ""

    /** The piece the friend just took from you (0 for none), for the coach to mention. */
    private var friendCapture = 0

    /** How a game on two tablets reaches into this controller. */
    private val nearbyGame = object : NearbyGame {
        override fun wires(): List<String> = game.moves.map { it.uci() }
        override val isOver: Boolean get() = game.isOver

        override fun playRemote(wire: String, animate: Boolean): Boolean {
            val move = position.legalMoves().firstOrNull { it.uci() == wire } ?: return false
            val before = position
            hintJob?.cancel()
            pendingPromotion = null
            friendCapture = if (move.isCapture) (if (move.isEnPassant) Piece.PAWN else abs(before[move.to])) else 0
            this@MatchController.apply(move, animate)
            return true
        }

        override fun rebuild(wires: List<String>): Boolean {
            var g = Game()
            for (w in wires) g = g.play(g.position.legalMoves().firstOrNull { it.uci() == w } ?: return false)
            game = g
            animation = null
            hint = null
            outcome = null
            showResult = false
            pendingPromotion = null
            friendCapture = 0
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
            playerWhite = iAmFirst
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
        if (bot != null && !playerWhite) scheduleBotMove()
        if (nearby != null) link = NearbyMatch(nearby, nearbyGame)
    }

    private fun greet() {
        mood = Mood.NORMAL
        coachText = when {
            nearby != null -> if (playerWhite) "You're playing a friend! You have the White pieces, so you go first."
            else "You're playing a friend! You have the Black pieces, so your friend goes first."
            bot == null -> "White moves first. Take turns, and have fun!"
            playerWhite -> "${bot.displayName} wants to play! You have the White pieces, so you go first."
            else -> "${bot.displayName} wants to play! You have the Black pieces, so $botName goes first."
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
        game = Game()
        animation = null
        thinking = false
        hint = null
        takeback = null
        outcome = null
        showResult = false
        pendingPromotion = null
        botMovesSinceTip = 0
        gameNumber++
        greet()
        if (bot != null && !playerWhite) scheduleBotMove()
    }

    fun onBoardMove(from: Int, to: Int, dragged: Boolean) {
        if (!isHumanTurn) return
        val candidates = position.legalMoves().filter { it.from == from && it.to == to }
        when {
            candidates.isEmpty() -> return
            candidates.size > 1 -> {
                pendingDragged = dragged
                pendingPromotion = candidates
            }
            else -> humanMove(candidates.first(), dragged)
        }
    }

    fun choosePromotion(type: Int) {
        val move = pendingPromotion?.firstOrNull { it.promotion == type } ?: return
        pendingPromotion = null
        humanMove(move, pendingDragged)
    }

    fun cancelPromotion() {
        pendingPromotion = null
    }

    fun onSelect() = sound.play(Effect.SELECT)

    fun onIllegal(from: Int, to: Int) {
        if (!isHumanTurn) return
        sound.play(Effect.ILLEGAL)
        mood = Mood.OOPS
        coachText = Coach.explainIllegal(position, from, to)
    }

    private fun humanMove(move: Move, dragged: Boolean) {
        hintJob?.cancel()
        val before = position
        apply(move, animate = !dragged)
        link?.sendMove(move.uci())
        if (game.isOver) {
            finish()
            return
        }
        if (nearby != null) {
            friendCapture = 0
            nearbyCoach()
            return
        }
        if (bot == null) {
            friendCoach()
            return
        }
        if (progress.coachTips) {
            val warning = blunderWarning(before, move, position)
            if (warning != null) {
                takeback = warning
                mood = Mood.OOPS
                coachText = warning
                return
            }
        }
        scheduleBotMove()
    }

    override fun acceptTakeback() {
        takeback = null
        game = game.undo(1)
        animation = null
        mood = Mood.NORMAL
        coachText = "Okay! Look around the board and find a safer move."
    }

    override fun declineTakeback() {
        takeback = null
        mood = Mood.NORMAL
        scheduleBotMove()
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
            while (g.moves.isNotEmpty() && g.position.whiteToMove != playerWhite) g = g.undo(1)
        }
        game = g
        mood = Mood.NORMAL
        coachText = "Move taken back. Try something new!"
        if (bot != null && position.whiteToMove != playerWhite) scheduleBotMove()
    }

    override fun requestHint() {
        if (!isHumanTurn) return
        val pos = position
        hintJob?.cancel()
        mood = Mood.NORMAL
        coachText = "Hmm, let me think… 🤔"
        hintJob = scope.launch {
            val result = withContext(Dispatchers.Default) { Ai.bestMove(pos, maxDepth = 4, timeLimitMs = 1200) }
            if (result != null && position === pos) {
                hint = result.move
                coachText = "💡 How about this: ${Notation.describe(pos, result.move)}?"
            }
        }
    }

    private fun apply(move: Move, animate: Boolean) {
        val before = position
        game = game.play(move)
        hint = null
        animation = if (animate) MoveAnimation(move, before, ++animationId) else null
        sound.play(
            when {
                position.inCheck() -> Effect.CHECK
                move.isPromotion -> Effect.PROMOTE
                move.isCapture -> Effect.CAPTURE
                else -> Effect.MOVE
            }
        )
    }

    private fun scheduleBotMove() {
        val b = bot ?: return
        thinking = true
        val start = game
        botJob?.cancel()
        botJob = scope.launch {
            val begun = System.currentTimeMillis()
            val move = withContext(Dispatchers.Default) { Ai.chooseMove(start.position, b) }
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

    /** What the coach says after the bot moves. */
    private fun botCoach(before: Position, move: Move) {
        val pos = position
        val me = pos.whiteToMove
        mood = Mood.NORMAL
        val tips = progress.coachTips
        val text = when {
            pos.inCheck() -> {
                mood = Mood.OOPS
                "Check! $botName is attacking your King. Get it to safety!"
            }
            move.isCapture -> {
                val taken = if (move.isEnPassant) Piece.PAWN else abs(before[move.to])
                val canRecapture = pos.legalMoves().any { it.to == move.to && it.isCapture }
                "$botName captured your ${Piece.name(taken)}!" + if (canRecapture && tips) " Can you capture back?" else ""
            }
            tips && Coach.mateInOne(pos) != null -> {
                mood = Mood.HAPPY
                "✨ Wow! You can checkmate in ONE move. Can you find it?"
            }
            tips && Coach.opponentMateThreat(pos) != null -> {
                mood = Mood.OOPS
                "⚠️ Watch out! $botName is threatening checkmate. Can you stop it?"
            }
            tips && freeCaptures(pos).isNotEmpty() -> "👀 Look closely: one of $botName's pieces isn't protected!"
            tips && valuableHanging(pos, me).isNotEmpty() -> {
                val sq = valuableHanging(pos, me).first()
                mood = Mood.OOPS
                "Careful! Your ${Piece.name(abs(pos[sq]))} on ${Square.name(sq)} is under attack."
            }
            tips && ++botMovesSinceTip >= 2 -> {
                botMovesSinceTip = 0
                tipFor(pos)
            }
            else -> YOUR_TURN.random()
        }
        coachText = text
    }

    /** What the coach says in a game on two tablets, after either player's move. */
    private fun nearbyCoach() {
        val mine = position.whiteToMove == playerWhite
        mood = Mood.NORMAL
        coachText = when {
            position.inCheck() && mine -> {
                mood = Mood.OOPS
                "Check! Your King is under attack. Protect it!"
            }
            position.inCheck() -> "Check! You are attacking your friend's King."
            !mine -> "Your friend is thinking…"
            friendCapture > 0 -> "Your friend captured your ${Piece.name(friendCapture)}!"
            else -> YOUR_TURN.random()
        }
    }

    private fun friendCoach() {
        val side = if (position.whiteToMove) "White" else "Black"
        mood = Mood.NORMAL
        coachText = if (position.inCheck()) {
            mood = Mood.OOPS
            "Check! $side's King is under attack. $side, protect your King!"
        } else {
            "$side's turn!"
        }
    }

    /** Enemy pieces worth at least a Knight that the side to move can simply take. */
    private fun freeCaptures(pos: Position): List<Int> {
        val legal = pos.legalMoves()
        return Coach.hangingPieces(pos, !pos.whiteToMove).filter { sq ->
            Piece.points(abs(pos[sq])) >= 3 && legal.any { it.to == sq && it.isCapture }
        }
    }

    /** Own pieces worth at least a Knight that the opponent could win. */
    private fun valuableHanging(pos: Position, white: Boolean): List<Int> =
        Coach.hangingPieces(pos, white).filter { Piece.points(abs(pos[it])) >= 3 }

    /** A warning if the child's move just gave something big away, or null if the move looks fine. */
    private fun blunderWarning(before: Position, move: Move, after: Position): String? {
        val me = before.whiteToMove
        if (Coach.mateInOne(after) != null) {
            return "Uh-oh! After that move, $botName can checkmate you right away. Want to take it back?"
        }
        // Compare in kid points so even trades (like Bishop for Knight) don't trigger a warning.
        val capturedPoints = if (move.isCapture) Piece.points(if (move.isEnPassant) Piece.PAWN else abs(before[move.to])) else 0
        val alreadyHanging = Coach.hangingPieces(before, me).toSet()
        val opponentMoves = after.legalMoves()
        val newlyHanging = Coach.hangingPieces(after, me).filter { sq ->
            val points = Piece.points(abs(after[sq]))
            points >= 3 &&
                points > capturedPoints &&
                (sq == move.to || sq !in alreadyHanging) &&
                opponentMoves.any { it.to == sq && it.isCapture }
        }
        val sq = newlyHanging.maxByOrNull { Piece.points(abs(after[it])) } ?: return null
        return "Careful! $botName could capture your ${Piece.name(abs(after[sq]))} on ${Square.name(sq)}. Want to take that move back?"
    }

    private fun tipFor(pos: Position): String {
        val pieces = pos.pieceCount(true) + pos.pieceCount(false)
        val list = when {
            pos.fullmoveNumber <= 8 -> OPENING_TIPS
            pieces <= 10 -> ENDGAME_TIPS
            else -> MIDDLE_TIPS
        }
        return list.random()
    }

    private fun finish() {
        val o = game.outcome ?: return
        outcome = o
        thinking = false
        hint = null
        val b = bot
        when {
            o.whiteWins == null -> {
                mood = Mood.NORMAL
                sound.play(Effect.SUCCESS)
            }
            (b == null && nearby == null) || o.whiteWins == playerWhite -> {
                mood = Mood.HAPPY
                if (b != null) progress.recordWin(GameKind.CHESS, b)
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

    private fun resultHeadline(o: Outcome): String = when {
        o.whiteWins == null -> "It's a draw!"
        nearby != null -> if (o.whiteWins == playerWhite) "You won!" else "Your friend won this time."
        bot == null -> if (o.whiteWins) "White wins!" else "Black wins!"
        o.whiteWins == playerWhite -> "You won!"
        else -> "$botName won this time."
    }

    private fun resultExplanation(o: Outcome): String = when (o.reason) {
        EndReason.CHECKMATE -> when {
            nearby != null -> if (o.whiteWins == playerWhite) "Checkmate! You trapped your friend's King. Amazing!"
            else "Checkmate. Every game makes you stronger. Want to play again?"
            bot == null -> "Checkmate! The King is trapped with no way out."
            o.whiteWins == playerWhite -> "Checkmate! You trapped $botName's King. Amazing!"
            else -> "Checkmate. Every game makes you stronger. Want to try again?"
        }
        EndReason.STALEMATE -> "Stalemate: the player to move has no legal moves but isn't in check."
        EndReason.INSUFFICIENT_MATERIAL -> "Neither side has enough pieces left to checkmate."
        EndReason.FIFTY_MOVES -> "50 moves went by with no captures or pawn moves."
        EndReason.REPETITION -> "The same position happened three times."
    }

    private companion object {
        const val MIN_THINK_MS = 700L

        val YOUR_TURN = listOf("Your turn!", "Your move! Take your time.", "What will you do next?", "Your turn. Look for captures and checks!")

        val OPENING_TIPS = listOf(
            "Tip: Control the center! Pawns and pieces in the middle of the board are strong.",
            "Tip: Get your Knights and Bishops out early.",
            "Tip: Castle early to keep your King safe.",
            "Tip: Don't bring your Queen out too early. She can get chased around!",
        )
        val MIDDLE_TIPS = listOf(
            "Tip: Before you move, ask yourself: what is my opponent attacking?",
            "Tip: Every move, look for checks, captures and threats.",
            "Tip: Rooks love open lines with no pawns in the way.",
            "Tip: A Knight on the edge of the board can't do much.",
            "Tip: Look for forks: one piece attacking two at once!",
        )
        val ENDGAME_TIPS = listOf(
            "Tip: In the endgame, your King becomes a fighter. Bring it toward the center!",
            "Tip: Push your pawns! A new Queen could win the game.",
            "Tip: When you're winning, watch out for stalemate.",
        )
    }
}
