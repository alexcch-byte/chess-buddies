package com.example.chess.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.chess.audio.SoundFx
import com.example.chess.audio.SoundFx.Effect
import com.example.chess.checkers.Checker
import com.example.chess.checkers.CheckersChallenge
import com.example.chess.checkers.CheckersCoach
import com.example.chess.checkers.CheckersGoal
import com.example.chess.checkers.CheckersLesson
import com.example.chess.checkers.CheckersMove
import com.example.chess.checkers.CheckersPosition
import com.example.chess.checkers.CheckersRules
import com.example.chess.checkers.CheckersSolver
import com.example.chess.data.GameKind
import com.example.chess.data.ProgressStore
import com.example.chess.lessons.LessonInfo
import com.example.chess.ui.board.CheckersAnimation
import com.example.chess.ui.board.CheckersBoard
import com.example.chess.ui.board.CheckersDecor
import com.example.chess.ui.components.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs one checkers lesson. The child always plays Red. */
class CheckersLessonController(
    override val lesson: CheckersLesson,
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
    var position by mutableStateOf(lesson.challenges.first().position)
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
    var animation by mutableStateOf<CheckersAnimation?>(null)
        private set
    var lastMove by mutableStateOf<CheckersMove?>(null)
        private set
    var hint by mutableStateOf<CheckersMove?>(null)
        private set
    var wrongSquare by mutableStateOf<Int?>(null)
        private set
    override var earned by mutableIntStateOf(0)
        private set

    private var mistakes = 0
    private var usedHint = false
    private var job: Job? = null
    private var hintJob: Job? = null
    private var token = 0
    private var animationId = 0L

    /** For a two-step trap puzzle: 0 = offer a checker, 1 = take the multi-jump. */
    private var stage = 0
    private var stageStart: CheckersPosition = position

    val challenge: CheckersChallenge get() = lesson.challenges[index]
    override val challengeCount: Int get() = lesson.challenges.size
    override val isLastIntroPage: Boolean get() = introPage == lesson.intro.lastIndex
    override val isMultiMove: Boolean get() = challenge.goal.multiMove

    override val goalLabel: String
        get() = when (val goal = challenge.goal) {
            is CheckersGoal.CollectStars -> "⭐ ${goal.stars.size - starsLeft.size} of ${goal.stars.size} stars"
            CheckersGoal.CaptureAll -> "Capture them all!"
            CheckersGoal.Crown -> "Get crowned"
            is CheckersGoal.Capture -> if (goal.atLeast == 1) "Make the jump" else "Jump ${goal.atLeast} checkers at once"
            CheckersGoal.SafeMove -> "Find a safe move"
            CheckersGoal.Win -> "Win the game"
            is CheckersGoal.Shot -> if (stage == 0) "Set the trap" else "Spring the trap!"
        }

    /** The first red checker, demonstrated during the intro. */
    val introPreviewSquare: Int?
        get() = (0 until 64).firstOrNull { position[it] > 0 }

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
            coachText = "You finished \"${lesson.title}\"! You're becoming a checkers champion!"
            sound.play(Effect.WIN)
        }
    }

    private fun startChallenge(i: Int) {
        job?.cancel()
        hintJob?.cancel()
        val myToken = ++token
        index = i
        mistakes = 0
        usedHint = false
        stage = 0
        resetBoard()
        val c = challenge
        position = c.position
        stageStart = c.position
        starsLeft = (c.goal as? CheckersGoal.CollectStars)?.stars.orEmpty()
        coachText = c.instruction
        mood = Mood.NORMAL
        par = null
        phase = LessonPhase.PLAYING
        if (c.goal.multiMove) {
            val start = c.position
            val stars = starsLeft
            job = scope.launch {
                val best = withContext(Dispatchers.Default) { CheckersSolver.solveMultiMove(start, c.goal, stars)?.length }
                if (token == myToken) par = best
            }
        }
    }

    private fun resetBoard() {
        moves = 0
        hint = null
        wrongSquare = null
        lastMove = null
        animation = null
    }

    fun onSelect() = sound.play(Effect.SELECT)

    fun onIllegal(from: Int, to: Int) {
        if (phase != LessonPhase.PLAYING) return
        sound.play(Effect.ILLEGAL)
        mood = Mood.OOPS
        coachText = CheckersCoach.explainIllegal(position, from, to)
    }

    override fun showHint() {
        if (phase != LessonPhase.PLAYING) return
        usedHint = true
        val pos = position
        val goal = currentGoal()
        val stars = starsLeft
        hintJob?.cancel()
        hintJob = scope.launch {
            val move = withContext(Dispatchers.Default) {
                if (goal.multiMove) CheckersSolver.solveMultiMove(pos, goal, stars)?.firstMove
                else CheckersSolver.solveOneShot(pos, goal)
            }
            if (move != null && position === pos) {
                hint = move
                mood = Mood.NORMAL
                coachText = "Follow the green arrow!"
            }
        }
    }

    /** The goal for the current step (the second step of a trap puzzle is a plain multi-jump). */
    private fun currentGoal(): CheckersGoal {
        val goal = challenge.goal
        return if (goal is CheckersGoal.Shot && stage == 1) CheckersGoal.Capture(goal.atLeast) else goal
    }

    fun onBoardMove(move: CheckersMove, animate: Boolean) {
        if (phase != LessonPhase.PLAYING) return
        val before = position
        val after = before.makeMove(move)
        hint = null
        wrongSquare = null
        lastMove = move
        animation = if (animate) CheckersAnimation(move, before, ++animationId) else null
        moves++
        val crowned = !Checker.isKing(before[move.from]) && Checker.isKing(after[move.to])
        sound.play(
            when {
                crowned -> Effect.PROMOTE
                move.isCapture -> Effect.CAPTURE
                else -> Effect.MOVE
            }
        )
        val goal = currentGoal()
        if (goal.multiMove) multiMoveStep(goal, before, move, after) else checkAnswer(goal, move, after)
    }

    private fun multiMoveStep(goal: CheckersGoal, before: CheckersPosition, move: CheckersMove, after: CheckersPosition) {
        val collected = move.to in starsLeft
        val stars = starsLeft - move.to
        if (collected) sound.play(Effect.STAR)
        starsLeft = stars
        if (CheckersRules.finished(goal, before, move, after, stars)) {
            position = after
            solved()
            return
        }
        position = after.passTurn()
        mood = Mood.NORMAL
        val crowned = !Checker.isKing(before[move.from]) && Checker.isKing(after[move.to])
        coachText = when {
            crowned -> "Crowned! Now your king can move backward too."
            collected -> if (stars.size == 1) "Got it! Just 1 more star." else "Got it! ${stars.size} more stars to go."
            move.isCapture && goal == CheckersGoal.CaptureAll -> {
                val left = after.pieceCount(red = false)
                if (left == 1) "Nice jump! Just 1 more to capture." else "Nice jump! $left more to go."
            }
            else -> challenge.instruction
        }
    }

    private fun checkAnswer(goal: CheckersGoal, move: CheckersMove, after: CheckersPosition) {
        position = after
        if (CheckersRules.solves(goal, move, after)) {
            val shot = challenge.goal as? CheckersGoal.Shot
            if (shot != null && stage == 0) springTrap(after, shot)
            else solved()
            return
        }
        mistakes++
        phase = LessonPhase.BUSY
        mood = Mood.OOPS
        coachText = CheckersRules.wrongAnswerText(challenge, move, after)
        wrongSquare = move.to
        val retryFrom = stageStart
        job = scope.launch {
            sound.play(Effect.OOPS)
            delay(900)
            // If Black can now jump, show it happening.
            val reply = after.captureMoves().maxByOrNull { it.captured.size }
            if (reply != null) {
                slide(after, reply)
                sound.play(Effect.CAPTURE)
                delay(350L + 260L * reply.captured.size)
            }
            delay(500)
            resetBoard()
            position = retryFrom
            starsLeft = (challenge.goal as? CheckersGoal.CollectStars)?.stars.orEmpty()
            phase = LessonPhase.PLAYING
        }
    }

    /** First half of a trap puzzle done: Black is forced to jump, then the child jumps back. */
    private fun springTrap(after: CheckersPosition, shot: CheckersGoal.Shot) {
        phase = LessonPhase.BUSY
        mood = Mood.HAPPY
        coachText = "Black has to jump. That's the rule!"
        job = scope.launch {
            delay(900)
            val reply = after.legalMoves().first()
            slide(after, reply)
            sound.play(Effect.CAPTURE)
            delay(700)
            stage = 1
            stageStart = position
            moves = 0
            mood = Mood.NORMAL
            coachText = "The trap is ready! Now jump ${shot.atLeast} black checkers in one move."
            phase = LessonPhase.PLAYING
        }
    }

    private fun slide(from: CheckersPosition, move: CheckersMove) {
        position = from.makeMove(move)
        lastMove = move
        animation = CheckersAnimation(move, from, ++animationId)
    }

    private fun solved() {
        phase = LessonPhase.SOLVED
        val best = par
        var stars = if (challenge.goal.multiMove) {
            when {
                best == null || moves <= best -> 3
                moves <= best + 2 -> 2
                else -> 1
            }
        } else {
            3
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
        val PRAISE = listOf("Great job!", "You did it!", "Super smart move!", "Wow, you're a natural!", "Fantastic!", "Jump-tastic!")
    }
}

@Composable
fun CheckersLessonScreen(
    lesson: CheckersLesson,
    progress: ProgressStore,
    sound: SoundFx,
    onNextLesson: (LessonInfo) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val c = remember(lesson.id) { CheckersLessonController(lesson, scope, sound, progress) }
    LessonScreen(c, progress, GameKind.CHECKERS.nextLessonAfter(lesson.id), onNextLesson, onBack) { modifier ->
        CheckersBoard(
            position = c.position,
            onMove = c::onBoardMove,
            modifier = modifier,
            theme = progress.boardTheme,
            interactive = c.phase == LessonPhase.PLAYING,
            showLegalMoves = progress.showLegalMoves,
            showCoordinates = progress.showCoordinates,
            decor = CheckersDecor(
                lastMove = c.lastMove,
                stars = c.starsLeft,
                hint = c.hint,
                wrongSquare = c.wrongSquare,
                previewSquare = if (c.phase == LessonPhase.INTRO) c.introPreviewSquare else null,
            ),
            animation = c.animation,
            onSelect = c::onSelect,
            onIllegal = c::onIllegal,
        )
    }
}
