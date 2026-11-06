package com.example.chess.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.chess.audio.SoundFx
import com.example.chess.checkers.Checker
import com.example.chess.checkers.CheckersPosition
import com.example.chess.data.ProgressStore
import com.example.chess.engine.Bot
import com.example.chess.engine.Coach
import com.example.chess.engine.Piece
import com.example.chess.engine.Position
import com.example.chess.theme.Kid
import com.example.chess.ui.board.BoardDecor
import com.example.chess.ui.board.CheckerIcon
import com.example.chess.ui.board.CheckersBoard
import com.example.chess.ui.board.CheckersDecor
import com.example.chess.ui.board.ChessBoard
import com.example.chess.ui.board.PieceIcon
import com.example.chess.ui.components.AnimalAvatar
import com.example.chess.ui.components.CoachBubble
import com.example.chess.ui.components.Confetti
import com.example.chess.ui.components.KidButton
import com.example.chess.ui.components.KidCard
import com.example.chess.ui.components.KidConfirmDialog
import com.example.chess.ui.components.KidScreen
import com.example.chess.ui.components.Mood
import com.example.chess.ui.components.PromotionDialog
import com.example.chess.ui.components.SirHopsCoachAvatar
import com.example.chess.ui.components.TopBar

@Composable
fun ChessMatchScreen(bot: Bot?, playerWhite: Boolean, progress: ProgressStore, sound: SoundFx, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val c = remember(bot, playerWhite) { MatchController(bot, playerWhite, scope, sound, progress) }
    MatchScreen(
        session = c,
        title = if (bot == null) "Chess with a Friend" else "Chess Practice",
        onBack = onBack,
        board = { modifier ->
            val pos = c.position
            val danger = remember(pos, progress.dangerAlerts, c.isHumanTurn) {
                if (!progress.dangerAlerts || !c.isHumanTurn || c.bot == null) emptySet()
                else Coach.hangingPieces(pos, pos.whiteToMove).toSet()
            }
            ChessBoard(
                position = pos,
                onMove = c::onBoardMove,
                modifier = modifier,
                theme = progress.boardTheme,
                flipped = c.flipped,
                interactive = c.isHumanTurn,
                showLegalMoves = progress.showLegalMoves,
                showCoordinates = progress.showCoordinates,
                decor = BoardDecor(lastMove = c.game.lastMove, hint = c.hint, danger = danger),
                animation = c.animation,
                onSelect = c::onSelect,
                onIllegal = c::onIllegal,
            )
        },
        sideIcon = { white, modifier -> PieceIcon(Piece.make(Piece.KING, white), modifier) },
        captured = { white -> ChessCapturedRow(c.position, byWhite = white) },
    )
    c.pendingPromotion?.let {
        PromotionDialog(white = c.position.whiteToMove, onPick = c::choosePromotion, onDismiss = c::cancelPromotion)
    }
}

@Composable
fun CheckersMatchScreen(bot: Bot?, playerRed: Boolean, progress: ProgressStore, sound: SoundFx, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val c = remember(bot, playerRed) { CheckersMatchController(bot, playerRed, scope, sound, progress) }
    MatchScreen(
        session = c,
        title = if (bot == null) "Checkers with a Friend" else "Checkers Practice",
        onBack = onBack,
        board = { modifier ->
            val pos = c.position
            val danger = remember(pos, progress.dangerAlerts, c.isHumanTurn) {
                if (!progress.dangerAlerts || !c.isHumanTurn || c.bot == null) emptySet()
                else pos.piecesInDanger(pos.redToMove)
            }
            CheckersBoard(
                position = pos,
                onMove = c::onBoardMove,
                modifier = modifier,
                theme = progress.boardTheme,
                flipped = c.flipped,
                interactive = c.isHumanTurn,
                showLegalMoves = progress.showLegalMoves,
                showCoordinates = progress.showCoordinates,
                decor = CheckersDecor(lastMove = c.game.lastMove, hint = c.hint, danger = danger),
                animation = c.animation,
                onSelect = c::onSelect,
                onIllegal = c::onIllegal,
            )
        },
        sideIcon = { red, modifier -> CheckerIcon(if (red) Checker.RED else Checker.BLACK, modifier) },
        captured = { red -> CheckersCapturedRow(c.position, byRed = red) },
    )
}

/** Shared match layout: the board, both players, Sir Hops and the game buttons. */
@Composable
fun MatchScreen(
    session: MatchSession,
    title: String,
    onBack: () -> Unit,
    board: @Composable (Modifier) -> Unit,
    sideIcon: @Composable (firstSide: Boolean, Modifier) -> Unit,
    captured: @Composable (firstSide: Boolean) -> Unit,
) {
    var confirmNewGame by remember { mutableStateOf(false) }

    KidScreen {
        Column(Modifier.fillMaxSize()) {
            TopBar(title, onBack)
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                if (maxWidth > maxHeight) {
                    val boardSize = min(maxHeight, maxWidth * 0.58f)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        board(Modifier.size(boardSize))
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PlayerStrip(session, top = true, sideIcon, captured)
                            CoachBubble(session.coachText, mood = session.mood, compact = true)
                            TakebackButtons(session)
                            Controls(session, onNewGame = { confirmNewGame = true })
                            PlayerStrip(session, top = false, sideIcon, captured)
                        }
                    }
                } else {
                    val boardSize = min(maxWidth, maxHeight * 0.62f)
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PlayerStrip(session, top = true, sideIcon, captured, Modifier.width(boardSize))
                        board(Modifier.size(boardSize))
                        PlayerStrip(session, top = false, sideIcon, captured, Modifier.width(boardSize))
                        CoachBubble(session.coachText, mood = session.mood, compact = true)
                        TakebackButtons(session)
                        Controls(session, onNewGame = { confirmNewGame = true })
                    }
                }
            }
        }
        if (session.result?.celebrate == true) Confetti(key = session.gameNumber)
    }

    if (confirmNewGame) {
        KidConfirmDialog(
            title = "Start a new game?",
            message = "This game will end and a fresh board will be set up.",
            confirmText = "New game",
            onConfirm = {
                confirmNewGame = false
                session.newGame()
            },
            onDismiss = { confirmNewGame = false },
        )
    }
    if (session.showResult) ResultDialog(session, onBack)
}

/** Name, avatar and captured pieces for one side. The top strip is whoever sits at the far side of the board. */
@Composable
private fun PlayerStrip(
    s: MatchSession,
    top: Boolean,
    sideIcon: @Composable (Boolean, Modifier) -> Unit,
    captured: @Composable (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomIsFirst = !s.flipped
    val first = if (top) !bottomIsFirst else bottomIsFirst
    val bot = s.bot
    val isBot = bot != null && first != s.playerFirst
    val name = when {
        isBot -> bot.displayName
        bot != null -> "You"
        else -> if (first) s.firstSideName else s.secondSideName
    }
    val toMove = s.result == null && s.firstSideToMove == first
    KidCard(modifier.fillMaxWidth(), color = if (toMove) Color(0xFFF1ECFF) else Color.White) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF3D6)),
                contentAlignment = Alignment.Center,
            ) {
                if (isBot) AnimalAvatar(bot, Modifier.size(42.dp), thinking = s.thinking)
                else sideIcon(first, Modifier.size(38.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = Kid.Ink, maxLines = 1)
                when {
                    isBot && s.thinking -> ThinkingDots()
                    toMove && !isBot -> Text("Your turn!", style = MaterialTheme.typography.labelMedium, color = Kid.Purple)
                    else -> Text(" ", style = MaterialTheme.typography.labelMedium)
                }
            }
            captured(first)
        }
    }
}

@Composable
private fun ThinkingDots() {
    val t by rememberInfiniteTransition(label = "dots").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "dots",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Thinking", style = MaterialTheme.typography.labelMedium, color = Kid.Purple)
        repeat(3) { i ->
            Text(".", style = MaterialTheme.typography.labelMedium, color = Kid.Purple, modifier = Modifier.alpha(if (t >= i) 1f else 0.2f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapturedIcons(count: Int, lead: Int, icon: @Composable (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(Modifier.width(150.dp), horizontalArrangement = Arrangement.End) {
            repeat(count) { icon(it) }
        }
        if (lead > 0) {
            Spacer(Modifier.width(6.dp))
            Text("+$lead", style = MaterialTheme.typography.titleMedium, color = Kid.GreenDark)
        }
    }
}

/** Pieces [byWhite] has captured, plus a "+N" if that side is ahead in points. */
@Composable
private fun ChessCapturedRow(pos: Position, byWhite: Boolean) {
    val pieces = remember(pos, byWhite) {
        val start = mapOf(Piece.QUEEN to 1, Piece.ROOK to 2, Piece.BISHOP to 2, Piece.KNIGHT to 2, Piece.PAWN to 8)
        start.flatMap { (type, count) ->
            val piece = Piece.make(type, !byWhite)
            List((count - pos.count(piece)).coerceAtLeast(0)) { piece }
        }
    }
    CapturedIcons(pieces.size, pos.materialPoints(byWhite) - pos.materialPoints(!byWhite)) { i ->
        PieceIcon(pieces[i], Modifier.size(22.dp))
    }
}

/** Checkers [byRed] has captured, plus a "+N" if that side has more checkers. */
@Composable
private fun CheckersCapturedRow(pos: CheckersPosition, byRed: Boolean) {
    val taken = (12 - pos.pieceCount(red = !byRed)).coerceAtLeast(0)
    CapturedIcons(taken, pos.pieceCount(byRed) - pos.pieceCount(!byRed)) {
        CheckerIcon(if (byRed) Checker.BLACK else Checker.RED, Modifier.size(18.dp))
    }
}

@Composable
private fun TakebackButtons(s: MatchSession) {
    if (s.takeback == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KidButton("Take it back", s::acceptTakeback, emoji = "↩️", color = Kid.Orange, shade = Kid.OrangeDark, small = true)
        KidButton("Keep my move", s::declineTakeback, color = Color(0xFF9A97B0), shade = Color(0xFF77748D), small = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(s: MatchSession, onNewGame: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (s.bot != null) {
            KidButton("Hint", s::requestHint, emoji = "💡", color = Kid.Orange, shade = Kid.OrangeDark, small = true, enabled = s.isHumanTurn)
        }
        KidButton("Undo", s::undo, emoji = "↩️", color = Kid.Blue, shade = Color(0xFF0767B3), small = true, enabled = s.canUndo)
        if (s.bot == null) {
            KidButton("Flip board", { s.flipped = !s.flipped }, emoji = "🔃", color = Kid.Green, shade = Kid.GreenDark, small = true)
        }
        KidButton("New game", onNewGame, emoji = "✨", color = Kid.Purple, shade = Kid.PurpleDark, small = true)
    }
}

@Composable
private fun ResultDialog(s: MatchSession, onBack: () -> Unit) {
    val r = s.result ?: return
    Dialog(onDismissRequest = { s.showResult = false }) {
        KidCard {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (r.celebrate) {
                    SirHopsCoachAvatar(mood = Mood.HAPPY, modifier = Modifier.size(76.dp), celebrating = true)
                } else if (s.bot != null) {
                    AnimalAvatar(s.bot!!, Modifier.size(76.dp))
                } else {
                    Text(r.emoji, fontSize = 64.sp)
                }
                Text(r.headline, style = MaterialTheme.typography.headlineMedium, color = Kid.Ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(r.explanation, style = MaterialTheme.typography.bodyLarge, color = Kid.InkSoft, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KidButton("Play again", s::newGame, emoji = "🔄", color = Kid.Green, shade = Kid.GreenDark)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KidButton("See the board", { s.showResult = false }, color = Kid.Blue, shade = Color(0xFF0767B3), small = true)
                        KidButton(if (s.bot != null) "New opponent" else "Home", onBack, color = Kid.Purple, shade = Kid.PurpleDark, small = true)
                    }
                }
            }
        }
    }
}
