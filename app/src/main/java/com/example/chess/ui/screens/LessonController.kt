package com.example.chess.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.chess.audio.SoundFx
import com.example.chess.audio.SoundFx.Effect
import com.example.chess.data.ProgressStore
import com.example.chess.engine.Coach
import com.example.chess.engine.Move
import com.example.chess.engine.Piece
import com.example.chess.engine.Position
import com.example.chess.lessons.Challenge
import com.example.chess.lessons.Goal
import com.example.chess.lessons.Lesson
import com.example.chess.lessons.LessonRules
import com.example.chess.lessons.LessonSolver
import com.example.chess.ui.board.MoveAnimation
import com.example.chess.ui.components.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Runs one lesson: the intro pages, then each challenge in turn. */
class LessonController(
    override val lesson: Lesson,
    private val scope: CoroutineScope,
    private val sound: SoundFx,
    private val progress: ProgressStore,
) : LessonSession {
    override var phase by mutableStateOf(LessonPhase.INTRO)
        private set
    override var introPage by mutableIntStateOf(0)
        private set
    override var index by mutableIntStateOf(0)
        private set
    var position by mutableStateOf(LessonRules.playablePosition(lesson.challenges.first()))
        private set
    var starsLeft by mutableStateOf<Set<Int>>(emptySet())
        private set
    override var moves by mutableIntStateOf(0)
        private set
    override var par by mutableStateOf<Int?>(null)
        private set
    override var coachText by mutableStateOf(lesson.intro.first())
        private set
    override var mood by mutableStateOf(Mood.NORMAL)
        private set
    var animation by mutableStateOf<MoveAnimation?>(null)
        private set
    var lastMove by mutableStateOf<Move?>(null)
        private set
    var hint by mutableStateOf<Move?>(null)
        private set
    var wrongSquare by mutableStateOf<Int?>(null)
        private set
    override var earned by mutableIntStateOf(0)
        private set
    var pendingPromotion by mutableStateOf<List<Move>?>(null)
        private set

    private var mistakes = 0
    private var usedHint = false
    private var job: Job? = null
    private var hintJob: Job? = null

    /** Changes whenever a challenge (re)starts, so late background results can be ignored. */
    private var token = 0
    private var animationId = 0L
    private var startingPosition: Position = position
    private var setupMove: Move? = null

    val challenge: Challenge get() = lesson.challenges[index]
    override val challengeCount: Int get() = lesson.challenges.size
    override val isLastIntroPage: Boolean get() = introPage == lesson.intro.lastIndex
    override val isMultiMove: Boolean get() = challenge.goal.multiMove

    override val goalLabel: String
        get() = when (val goal = challenge.goal) {
            is Goal.CollectStars -> "⭐ ${goal.stars.size - starsLeft.size} of ${goal.stars.size} stars"
            Goal.CaptureAll -> "Capture them all!"
            Goal.Promote -> "Promote your pawn"
            Goal.Checkmate -> "Find checkmate"
            Goal.GiveCheck -> "Give check"
            Goal.EscapeCheck -> "Escape from check"
            is Goal.CaptureOn -> "Find the best capture"
            is Goal.Castle -> "Castle!"
            Goal.EnPassant -> "Capture en passant"
            Goal.Fork -> "Find the fork"
        }

    /** A piece to demonstrate during the intro: the first piece of the side to move. */
    val introPreviewSquare: Int?
        get() = (0 until 64).firstOrNull { val p = position[it]; p != 0 && (p > 0) == position.whiteToMove }

    override fun nextIntroPage() {
        if (isLastIntroPage) {
            startChallenge(0)
        } else {
            introPage++
            coachText = lesson.intro[introPage]
        }
    }

    override fun skipIntro() = startChallenge(0)

    override fun restartChallenge() = startChallenge(index)

    override fun nextChallenge() {
        if (index + 1 < challengeCount) {
            startChallenge(index + 1)
        } else {
            phase = LessonPhase.FINISHED
            mood = Mood.HAPPY
            coachText = "You finished \"${lesson.title}\"! You're becoming a real chess player!"
            sound.play(Effect.WIN)
        }
    }

    fun startChallenge(i: Int) {
        job?.cancel()
        hintJob?.cancel()
        val myToken = ++token
        index = i
        mistakes = 0
        usedHint = false
        resetBoardState()
        val c = challenge
        coachText = c.instruction
        mood = Mood.NORMAL
        par = null
        val initial = LessonRules.initialPosition(c)
        setupMove = LessonRules.setupMove(c)
        startingPosition = setupMove?.let { initial.makeMove(it) } ?: initial
        position = initial
        starsLeft = (c.goal as? Goal.CollectStars)?.stars.orEmpty()
        phase = if (setupMove != null) LessonPhase.BUSY else LessonPhase.PLAYING
        job = scope.launch {
            setupMove?.let { m ->
                delay(800)
                slide(initial, m)
                sound.play(if (startingPosition.inCheck()) Effect.CHECK else Effect.MOVE)
                delay(350)
                phase = LessonPhase.PLAYING
            }
            if (c.goal.multiMove) {
                val start = startingPosition
                val stars = starsLeft
                val best = withContext(Dispatchers.Default) {
                    LessonSolver.solveMultiMove(start, c.goal, stars, c.safeOnly)?.length
                }
                if (token == myToken) par = best
            }
        }
    }

    private fun resetBoardState() {
        moves = 0
        hint = null
        wrongSquare = null
        lastMove = null
        animation = null
        pendingPromotion = null
    }

    fun onBoardMove(from: Int, to: Int, dragged: Boolean) {
        if (phase != LessonPhase.PLAYING) return
        val candidates = position.legalMoves().filter { it.from == from && it.to == to }
        when {
            candidates.isEmpty() -> return
            candidates.size > 1 -> {
                pendingDragged = dragged
                pendingPromotion = candidates
            }
            else -> play(candidates.first(), dragged)
        }
    }

    private var pendingDragged = false

    fun choosePromotion(type: Int) {
        val move = pendingPromotion?.firstOrNull { it.promotion == type } ?: return
        pendingPromotion = null
        play(move, pendingDragged)
    }

    fun cancelPromotion() {
        pendingPromotion = null
    }

    fun onIllegal(from: Int, to: Int) {
        if (phase != LessonPhase.PLAYING) return
        sound.play(Effect.ILLEGAL)
        mood = Mood.OOPS
        coachText = Coach.explainIllegal(position, from, to)
    }

    fun onSelect() = sound.play(Effect.SELECT)

    override fun showHint() {
        if (phase != LessonPhase.PLAYING) return
        usedHint = true
        val pos = position
        val c = challenge
        val stars = starsLeft
        hintJob?.cancel()
        hintJob = scope.launch {
            val move = withContext(Dispatchers.Default) {
                if (c.goal.multiMove) LessonSolver.solveMultiMove(pos, c.goal, stars, c.safeOnly)?.firstMove
                else LessonSolver.solveOneShot(pos, c.goal)
            }
            if (move != null && position === pos) {
                hint = move
                mood = Mood.NORMAL
                coachText = "Follow the green arrow! Move your ${Piece.name(abs(pos[move.from]))}."
            }
        }
    }

    private fun play(move: Move, dragged: Boolean) {
        val before = position
        val after = before.makeMove(move)
        hint = null
        wrongSquare = null
        lastMove = move
        animation = if (dragged) null else MoveAnimation(move, before, ++animationId)
        moves++
        sound.play(
            when {
                move.isPromotion -> Effect.PROMOTE
                move.isCapture -> Effect.CAPTURE
                else -> Effect.MOVE
            }
        )
        val goal = challenge.goal
        if (goal.multiMove) playMultiMoveStep(move, after) else checkPuzzleAnswer(before, move, after)
    }

    private fun playMultiMoveStep(move: Move, after: Position) {
        val c = challenge
        if (c.safeOnly && LessonRules.landedInDanger(after, move)) {
            position = after
            mistakes++
            phase = LessonPhase.BUSY
            mood = Mood.OOPS
            val attacker = after.attackers(move.to, byWhite = after.whiteToMove).minByOrNull { Piece.points(abs(after[it])) }
            val attackerName = attacker?.let { Piece.name(abs(after[it])) } ?: "enemy"
            coachText = "Oh no! The $attackerName can capture your ${Piece.name(abs(after[move.to]))} there. Let's try again!"
            job = scope.launch {
                delay(700)
                punish(after, move)
                delay(1500)
                retry()
            }
            return
        }
        val collected = move.to in starsLeft
        val stars = starsLeft - move.to
        if (collected) sound.play(Effect.STAR)
        starsLeft = stars
        if (LessonRules.finished(c.goal, move, after, stars)) {
            position = after
            solved()
            return
        }
        position = after.passTurn()
        mood = Mood.NORMAL
        coachText = when {
            collected -> if (stars.size == 1) "Got it! Just 1 more star." else "Got it! ${stars.size} more stars to go."
            move.isCapture && c.goal == Goal.CaptureAll -> {
                val left = after.pieceCount(white = after.whiteToMove)
                if (left == 1) "Yum! Just 1 more to capture." else "Nice capture! $left more to go."
            }
            else -> c.instruction
        }
    }

    private fun checkPuzzleAnswer(before: Position, move: Move, after: Position) {
        position = after
        val c = challenge
        if (LessonRules.solves(c.goal, before, move, after)) {
            if (after.inCheck()) sound.play(Effect.CHECK)
            solved()
            return
        }
        mistakes++
        phase = LessonPhase.BUSY
        mood = Mood.OOPS
        coachText = LessonRules.wrongAnswerText(c, move, after)
        wrongSquare = move.to
        job = scope.launch {
            sound.play(Effect.OOPS)
            delay(800)
            if (punish(after, move)) delay(1100) else delay(500)
            retry()
        }
    }

    /** If the opponent can capture the piece that just moved, show it happening. */
    private suspend fun punish(after: Position, move: Move): Boolean {
        val reply = after.legalMoves()
            .filter { it.to == move.to && it.isCapture }
            .minByOrNull { Piece.points(abs(after[it.from])) }
            ?: return false
        slide(after, reply)
        sound.play(Effect.CAPTURE)
        return true
    }

    /** Back to the start of the current challenge, keeping the mistake count. */
    private fun retry() {
        resetBoardState()
        position = startingPosition
        lastMove = setupMove
        starsLeft = (challenge.goal as? Goal.CollectStars)?.stars.orEmpty()
        phase = LessonPhase.PLAYING
    }

    private fun slide(from: Position, move: Move) {
        position = from.makeMove(move)
        lastMove = move
        animation = MoveAnimation(move, from, ++animationId)
    }

    private fun solved() {
        phase = LessonPhase.SOLVED
        val best = par
        var stars = when {
            challenge.goal.multiMove -> when {
                best == null || moves <= best -> 3
                moves <= best + 2 -> 2
                else -> 1
            }
            else -> 3
        }
        stars -= mistakes
        if (usedHint) stars = minOf(stars, 2)
        earned = stars.coerceIn(1, 3)
        progress.recordStars(lesson.id, index, earned)
        mood = Mood.HAPPY
        coachText = PRAISE.random()
        sound.play(Effect.SUCCESS)
    }

    private companion object {
        val PRAISE = listOf(
            "Great job!",
            "You did it!",
            "Super smart move!",
            "Wow, you're a natural!",
            "Fantastic!",
            "That's the way!",
        )
    }
}
