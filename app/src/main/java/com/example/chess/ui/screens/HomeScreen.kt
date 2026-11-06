package com.example.chess.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.checkers.Checker
import com.example.chess.data.GameKind
import com.example.chess.data.ProgressStore
import com.example.chess.engine.Bot
import com.example.chess.engine.Piece
import com.example.chess.theme.Kid
import com.example.chess.ui.board.CheckerIcon
import com.example.chess.ui.board.PieceIcon
import com.example.chess.ui.components.AnimalAvatar
import com.example.chess.ui.components.KidScreen
import com.example.chess.ui.components.Mood
import com.example.chess.ui.components.RoundIconButton
import com.example.chess.ui.components.SirHopsCoachAvatar

@Composable
fun HomeScreen(
    progress: ProgressStore,
    onLearn: (GameKind) -> Unit,
    onPractice: (GameKind) -> Unit,
    onFriend: (GameKind) -> Unit,
    onNearby: (GameKind) -> Unit,
    onSettings: () -> Unit,
) {
    val game = progress.selectedGame
    val next = progress.nextLesson(game)
    KidScreen {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight && maxWidth > 700.dp
            val menu: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GameSwitcher(game, onPick = { progress.selectedGame = it })
                    MenuCard(
                        emoji = "📚",
                        title = "Learn ${game.title}",
                        subtitle = next?.let { "Next up: ${it.title}" } ?: "All lessons done. Amazing!",
                        color = Kid.Purple,
                        shade = Kid.PurpleDark,
                        badge = "⭐ ${progress.totalStars(game)} / ${progress.maxStars(game)}",
                        onClick = { onLearn(game) },
                        avatar = { SirHopsCoachAvatar(mood = Mood.HAPPY, modifier = Modifier.size(52.dp)) },
                    )
                    MenuCard(
                        emoji = "🦊",
                        title = "Practice Match",
                        subtitle = "Play ${game.title.lowercase()} with the forest friends",
                        color = Kid.Green,
                        shade = Kid.GreenDark,
                        badge = "🏆 ${progress.totalWins(game)}",
                        onClick = { onPractice(game) },
                        avatar = { AnimalAvatar(Bot.FOX, Modifier.size(52.dp)) },
                    )
                    MenuCard(
                        emoji = "🧑‍🤝‍🧑",
                        title = "Play a Friend",
                        subtitle = "Two players, one screen",
                        color = Kid.Orange,
                        shade = Kid.OrangeDark,
                        badge = null,
                        onClick = { onFriend(game) },
                    )
                    MenuCard(
                        emoji = "📡",
                        title = "Play Nearby",
                        subtitle = "Two tablets, Wi-Fi or Bluetooth",
                        color = Kid.Blue,
                        shade = Color(0xFF0767B3),
                        badge = null,
                        onClick = { onNearby(game) },
                    )
                }
            }

            if (wide) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    Hero(game, Modifier.weight(1f))
                    menu(Modifier.weight(1f))
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(24.dp))
                    Hero(game, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(28.dp))
                    menu(Modifier.fillMaxWidth())
                }
            }

            RoundIconButton(
                "⚙️",
                onSettings,
                description = "Settings",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun GameSwitcher(game: GameKind, onPick: (GameKind) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (g in GameKind.entries) {
            val selected = g == game
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Kid.Purple else Color.Transparent)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab) { onPick(g) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (g) {
                    GameKind.CHESS -> PieceIcon(if (selected) Piece.KNIGHT else -Piece.KNIGHT, Modifier.size(34.dp))
                    GameKind.CHECKERS -> CheckerIcon(Checker.RED_KING, Modifier.size(34.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(g.title, style = MaterialTheme.typography.titleLarge, color = if (selected) Color.White else Kid.Ink)
            }
        }
    }
}

@Composable
private fun Hero(game: GameKind, modifier: Modifier = Modifier) {
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "bob",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(Color(0x226C5CE7))
            )
            Row(verticalAlignment = Alignment.Bottom) {
                when (game) {
                    GameKind.CHESS -> {
                        AnimalAvatar(Bot.BUNNY, Modifier.size(76.dp).offset(x = 16.dp, y = (4 * bob).dp))
                        SirHopsCoachAvatar(mood = Mood.HAPPY, modifier = Modifier.size(150.dp).offset(y = (-8 * bob).dp), celebrating = true)
                        AnimalAvatar(Bot.KITTEN, Modifier.size(76.dp).offset(x = (-16).dp, y = (-4 * bob).dp))
                    }
                    GameKind.CHECKERS -> {
                        AnimalAvatar(Bot.FOX, Modifier.size(74.dp).offset(x = 12.dp, y = (-12 + 4 * bob).dp))
                        CheckerIcon(Checker.RED_KING, Modifier.size(130.dp).offset(y = (-10 - 8 * bob).dp))
                        AnimalAvatar(Bot.BUNNY, Modifier.size(74.dp).offset(x = (-12).dp, y = (-12 - 4 * bob).dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Chess Buddies",
            style = MaterialTheme.typography.displaySmall,
            color = Kid.Purple,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Hi, I'm Sir Hops! Let's learn chess and checkers together.",
            style = MaterialTheme.typography.bodyLarge,
            color = Kid.InkSoft,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MenuCard(
    emoji: String,
    title: String,
    subtitle: String,
    color: Color,
    shade: Color,
    badge: String?,
    onClick: () -> Unit,
    avatar: (@Composable () -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(shade)
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(color)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) avatar() else Text(emoji, fontSize = 32.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
            }
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    badge,
                    style = MaterialTheme.typography.titleMedium,
                    color = Kid.Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
