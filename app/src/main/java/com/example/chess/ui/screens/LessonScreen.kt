package com.example.chess.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.chess.audio.Narrator
import com.example.chess.audio.SoundFx
import com.example.chess.checkers.CheckersLesson
import com.example.chess.data.GameKind
import com.example.chess.data.ProgressStore
import com.example.chess.lessons.Lesson
import com.example.chess.lessons.LessonInfo
import com.example.chess.theme.Kid
import com.example.chess.ui.board.BoardDecor
import com.example.chess.ui.board.CheckerIcon
import com.example.chess.ui.board.ChessBoard
import com.example.chess.ui.board.PieceIcon
import com.example.chess.ui.components.CoachBubble
import com.example.chess.ui.components.Confetti
import com.example.chess.ui.components.KidButton
import com.example.chess.ui.components.KidCard
import com.example.chess.ui.components.KidScreen
import com.example.chess.ui.components.KidVictoryBanner
import com.example.chess.ui.components.Mood
import com.example.chess.ui.components.PromotionDialog
import com.example.chess.ui.components.RoundIconButton
import com.example.chess.ui.components.SirHopsCoachAvatar
import com.example.chess.ui.components.StarRow
import com.example.chess.ui.components.TopBar

/** The picture on a lesson card: a chess piece or a checker. */
@Composable
fun LessonIcon(lesson: LessonInfo, modifier: Modifier = Modifier) {
    when (lesson) {
        is Lesson -> PieceIcon(lesson.icon, modifier)
        is CheckersLesson -> CheckerIcon(lesson.icon, modifier)
    }
}

@Composable
fun ChessLessonScreen(
    lesson: Lesson,
    progress: ProgressStore,
    sound: SoundFx,
    onNextLesson: (LessonInfo) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val c = remember(lesson.id) { LessonController(lesson, scope, sound, progress) }
    LessonScreen(c, progress, GameKind.CHESS.nextLessonAfter(lesson.id), onNextLesson, onBack) { modifier ->
        ChessBoard(
            position = c.position,
            onMove = c::onBoardMove,
            modifier = modifier,
            theme = progress.boardTheme,
            interactive = c.phase == LessonPhase.PLAYING,
            showLegalMoves = progress.showLegalMoves,
            showCoordinates = progress.showCoordinates,
            decor = BoardDecor(
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
    c.pendingPromotion?.let {
        PromotionDialog(white = c.position.whiteToMove, onPick = c::choosePromotion, onDismiss = c::cancelPromotion)
    }
}

/** Shared lesson layout: the board on one side, Sir Hops and the controls on the other. */
@Composable
fun LessonScreen(
    session: LessonSession,
    progress: ProgressStore,
    nextLesson: LessonInfo?,
    onNextLesson: (LessonInfo) -> Unit,
    onBack: () -> Unit,
    board: @Composable (Modifier) -> Unit,
) {
    val narrator = Narrator.get(LocalContext.current)
    DisposableEffect(session) {
        narrator.attach(session)
        onDispose { narrator.detach(session) }
    }
    // Everything Sir Hops says in a lesson goes through coachText, so reading that out loud covers the intro pages,
    // each challenge, hints, mistakes and praise.
    LaunchedEffect(session.coachText, progress.readAloud) {
        if (progress.readAloud) narrator.say(session.coachText)
    }
    KidScreen {
        Column(Modifier.fillMaxSize()) {
            TopBar(session.lesson.title, onBack) {
                if (session.phase != LessonPhase.INTRO) ChallengeDots(session.challengeCount, session.index, session.phase)
            }
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                if (maxWidth > maxHeight) {
                    val boardSize = min(maxHeight, maxWidth * 0.58f)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        board(Modifier.size(boardSize))
                        SidePanel(session, progress, nextLesson, onNextLesson, onBack, Modifier.weight(1f).fillMaxSize())
                    }
                } else {
                    val boardSize = min(maxWidth, maxHeight * 0.6f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        board(Modifier.size(boardSize))
                        Spacer(Modifier.height(12.dp))
                        SidePanel(session, progress, nextLesson, onNextLesson, onBack, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
        if (session.phase == LessonPhase.SOLVED || session.phase == LessonPhase.FINISHED) {
            Confetti(key = "${session.index}-${session.phase}")
        }
    }
}

@Composable
private fun SidePanel(
    s: LessonSession,
    progress: ProgressStore,
    nextLesson: LessonInfo?,
    onNextLesson: (LessonInfo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val narrator = Narrator.get(LocalContext.current)
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoachBubble(s.coachText, mood = s.mood, modifier = Modifier.weight(1f))
            if (narrator.available) {
                Spacer(Modifier.width(10.dp))
                RoundIconButton("🔊", { narrator.say(s.coachText, force = true) }, description = "Listen again")
            }
        }
        when (s.phase) {
            LessonPhase.INTRO -> IntroControls(s)
            LessonPhase.PLAYING, LessonPhase.BUSY -> PlayControls(s)
            LessonPhase.SOLVED -> SolvedCard(s)
            LessonPhase.FINISHED -> FinishedCard(s, progress, nextLesson, onNextLesson, onBack)
        }
    }
}

@Composable
private fun IntroControls(s: LessonSession) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(s.lesson.intro.size) { i ->
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (i <= s.introPage) Kid.Purple else Color(0xFFD9D4EA))
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KidButton(
                if (s.isLastIntroPage) "Let's go!" else "Next",
                s::nextIntroPage,
                emoji = if (s.isLastIntroPage) "🚀" else "➡️",
            )
            if (!s.isLastIntroPage) {
                KidButton("Skip", s::skipIntro, color = Color(0xFF9A97B0), shade = Color(0xFF77748D), small = true)
            }
        }
    }
}

@Composable
private fun PlayControls(s: LessonSession) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KidCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Challenge ${s.index + 1} of ${s.challengeCount}", style = MaterialTheme.typography.labelMedium, color = Kid.InkSoft)
                    Text(s.goalLabel, style = MaterialTheme.typography.titleMedium, color = Kid.Ink)
                }
                if (s.isMultiMove) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Moves: ${s.moves}", style = MaterialTheme.typography.titleMedium, color = Kid.Ink)
                        s.par?.let { Text("Best: $it", style = MaterialTheme.typography.labelMedium, color = Kid.InkSoft) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KidButton("Hint", s::showHint, emoji = "💡", color = Kid.Orange, shade = Kid.OrangeDark, small = true)
            KidButton("Start over", s::restartChallenge, emoji = "🔄", color = Kid.Blue, shade = Color(0xFF0767B3), small = true)
        }
    }
}

@Composable
private fun SolvedCard(s: LessonSession) {
    KidVictoryBanner(
        stars = s.earned,
        seed = s.index,
        onNext = s::nextChallenge,
        onRetry = if (s.earned < 3) s::restartChallenge else null,
        nextLabel = if (s.index + 1 < s.challengeCount) "Next ➔" else "Finish 🏆",
    )
}

@Composable
private fun FinishedCard(
    s: LessonSession,
    progress: ProgressStore,
    next: LessonInfo?,
    onNextLesson: (LessonInfo) -> Unit,
    onBack: () -> Unit,
) {
    val stars = progress.lessonStars(s.lesson)
    val max = progress.lessonMaxStars(s.lesson)
    KidCard(Modifier.fillMaxWidth(), color = Color(0xFFFFFBEA)) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEEA8)),
                contentAlignment = Alignment.Center,
            ) {
                SirHopsCoachAvatar(mood = Mood.HAPPY, modifier = Modifier.size(68.dp), celebrating = true)
            }
            Spacer(Modifier.height(8.dp))
            Text("🎉 Lesson Complete! 🎉", style = MaterialTheme.typography.headlineSmall, color = Kid.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("⭐ $stars of $max stars", style = MaterialTheme.typography.titleMedium, color = Kid.InkSoft)
            Spacer(Modifier.height(16.dp))
            if (next != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LessonIcon(next, Modifier.size(40.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Up next: ${next.title}", style = MaterialTheme.typography.titleMedium, color = Kid.Ink)
                }
            } else {
                Text(
                    "You finished every lesson! Try a Practice Match to use your new skills.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Kid.Ink,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KidButton("All lessons", onBack, emoji = "📚", color = Kid.Purple, shade = Kid.PurpleDark, small = true)
                if (next != null) {
                    KidButton("Next lesson", { onNextLesson(next) }, emoji = "➡️", color = Kid.Green, shade = Kid.GreenDark, small = true)
                }
            }
        }
    }
}

@Composable
private fun ChallengeDots(count: Int, current: Int, phase: LessonPhase) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val done = i < current || (i == current && (phase == LessonPhase.SOLVED || phase == LessonPhase.FINISHED))
            val dotSize: Dp = if (i == current) 18.dp else 14.dp
            Box(
                Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(
                        when {
                            done -> Kid.Green
                            i == current -> Kid.Purple
                            else -> Color(0xFFD9D4EA)
                        }
                    )
            )
        }
    }
}
