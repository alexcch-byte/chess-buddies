package com.example.chess.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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
import com.example.chess.ui.components.KidButton
import com.example.chess.ui.components.KidScreen
import com.example.chess.ui.components.SectionLabel
import com.example.chess.ui.components.TopBar
import kotlin.random.Random

private enum class ColorChoice { FIRST, SECOND, RANDOM }

/** Pick an opponent and a side. [onStart] gets whether the player takes the side that moves first. */
@Composable
fun PracticeSetupScreen(game: GameKind, progress: ProgressStore, onStart: (Bot, playerFirst: Boolean) -> Unit, onBack: () -> Unit) {
    var botId by rememberSaveable { mutableStateOf(Bot.BUNNY.id) }
    var color by rememberSaveable { mutableStateOf(ColorChoice.FIRST) }
    val bot = Bot.byId(botId)

    KidScreen {
        Column(Modifier.fillMaxSize()) {
            TopBar("${game.title} Practice", onBack)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Choose your opponent", Modifier.padding(start = 4.dp)) }
                items(Bot.entries, key = { it.id }) { b ->
                    BotCard(b, selected = b == bot, wins = progress.winsAgainst(game, b), onClick = { botId = b.id })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(Modifier.height(4.dp))
                        SectionLabel(if (game == GameKind.CHESS) "Choose your pieces" else "Choose your checkers", Modifier.padding(start = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (choice in ColorChoice.entries) {
                                ColorChip(game, choice, selected = choice == color, onClick = { color = choice }, modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        KidButton(
                            "Play ${bot.displayName.substringBefore(' ')}!",
                            onClick = {
                                val first = when (color) {
                                    ColorChoice.FIRST -> true
                                    ColorChoice.SECOND -> false
                                    ColorChoice.RANDOM -> Random.nextBoolean()
                                }
                                onStart(bot, first)
                            },
                            emoji = if (game == GameKind.CHESS) "♟️" else "🔴",
                            color = Kid.Green,
                            shade = Kid.GreenDark,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BotCard(bot: Bot, selected: Boolean, wins: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(if (selected) 10.dp else 4.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Color(0xFFF1ECFF) else Color.White)
            .border(if (selected) 3.dp else 0.dp, if (selected) Kid.Purple else Color.Transparent, RoundedCornerShape(22.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(when (bot) {
                    Bot.BUNNY -> Color(0xFFFFECEF)
                    Bot.KITTEN -> Color(0xFFFFF0E0)
                    Bot.FOX -> Color(0xFFFFF2DC)
                    Bot.OWL -> Color(0xFFEBF3FF)
                    Bot.DRAGON -> Color(0xFFE2F9F5)
                }),
            contentAlignment = Alignment.Center,
        ) {
            AnimalAvatar(bot, Modifier.size(72.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(bot.displayName, style = MaterialTheme.typography.titleMedium, color = Kid.Ink)
        Text(levelLabel(bot.level), style = MaterialTheme.typography.labelMedium, color = Kid.Purple)
        Spacer(Modifier.height(4.dp))
        Text(bot.blurb, style = MaterialTheme.typography.bodyMedium, color = Kid.InkSoft, minLines = 2)
        if (wins > 0) {
            Spacer(Modifier.height(6.dp))
            Text("🏆 You won $wins ${if (wins == 1) "time" else "times"}!", style = MaterialTheme.typography.labelMedium, color = Kid.GreenDark)
        }
    }
}

private fun levelLabel(level: Int): String = "★".repeat(level) + "☆".repeat(5 - level)

@Composable
private fun ColorChip(game: GameKind, choice: ColorChoice, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (choice) {
        ColorChoice.FIRST -> if (game == GameKind.CHESS) "White" else "Red"
        ColorChoice.SECOND -> "Black"
        ColorChoice.RANDOM -> "Surprise me"
    }
    Row(
        modifier
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Kid.Purple else Color.White)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when {
            choice == ColorChoice.RANDOM -> Text("🎲", fontSize = 26.sp)
            game == GameKind.CHESS -> PieceIcon(if (choice == ColorChoice.FIRST) Piece.KING else -Piece.KING, Modifier.size(36.dp))
            else -> CheckerIcon(if (choice == ColorChoice.FIRST) Checker.RED else Checker.BLACK, Modifier.size(36.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) Color.White else Kid.Ink)
    }
}
