package com.example.chess.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.chess.engine.Piece
import com.example.chess.theme.Kid
import com.example.chess.ui.board.PieceIcon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Full-screen gradient background that keeps content clear of system bars. */
@Composable
fun KidScreen(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Kid.background)
            .safeDrawingPadding(),
        content = content,
    )
}

/** A chunky, colorful button with a 3D-ish bottom edge. */
@Composable
fun KidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Kid.Purple,
    shade: Color = Kid.PurpleDark,
    emoji: String? = null,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val bg = if (enabled) color else Color(0xFFC9C6D6)
    val edge = if (enabled) shade else Color(0xFFA9A6B8)
    Box(
        modifier
            .heightIn(min = if (small) 44.dp else 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(edge)
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 22.dp, vertical = if (small) 8.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (emoji != null) {
                Text(emoji, fontSize = if (small) 18.sp else 22.sp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = Color.White,
                style = if (small) MaterialTheme.typography.labelMedium.copy(fontSize = 16.sp) else MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Round icon button used in top bars. */
@Composable
fun RoundIconButton(emoji: String, onClick: () -> Unit, modifier: Modifier = Modifier, description: String? = null) {
    Box(
        modifier
            .size(52.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 24.sp)
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton("⬅️", onBack, description = "Back")
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Kid.Ink, modifier = Modifier.weight(1f), maxLines = 1)
        trailing()
    }
}

/** A white rounded card. */
@Composable
fun KidCard(modifier: Modifier = Modifier, color: Color = Kid.Card, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = color,
        shadowElevation = 6.dp,
        content = content,
    )
}

/** A pill showing a count, such as stars or trophies. */
@Composable
fun CountPill(emoji: String, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Kid.Ink)
    }
}

enum class Mood { NORMAL, HAPPY, OOPS }

/** Sir Hops, the friendly knight coach, with a speech bubble. */
@Composable
fun CoachBubble(text: String, modifier: Modifier = Modifier, mood: Mood = Mood.NORMAL, compact: Boolean = false) {
    val bubbleColor = when (mood) {
        Mood.NORMAL -> Color.White
        Mood.HAPPY -> Color(0xFFE3FAF2)
        Mood.OOPS -> Color(0xFFFFECEC)
    }
    val borderColor = when (mood) {
        Mood.NORMAL -> Color(0xFFE2DDF5)
        Mood.HAPPY -> Kid.Green
        Mood.OOPS -> Kid.Red
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(if (compact) 56.dp else 72.dp)
                .clip(CircleShape)
                .background(Color(0xFFEDE9FF)),
            contentAlignment = Alignment.Center,
        ) {
            SirHopsCoachAvatar(mood = mood, modifier = Modifier.size(if (compact) 46.dp else 60.dp), celebrating = mood == Mood.HAPPY)
            Text(
                when (mood) {
                    Mood.NORMAL -> ""
                    Mood.HAPPY -> "🎉"
                    Mood.OOPS -> "😮"
                },
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        Spacer(Modifier.width(8.dp))
        // Little tail pointing at the coach.
        Canvas(Modifier.size(width = 10.dp, height = 18.dp)) {
            val p = Path().apply {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(p, bubbleColor)
        }
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.weight(1f),
            label = "coach",
        ) { shown ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(bubbleColor)
                    .border(2.dp, borderColor, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
            ) {
                Text(
                    shown,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = Kid.Ink,
                )
            }
        }
    }
}

/** Row of three stars, [filled] of them gold. */
@Composable
fun StarRow(filled: Int, modifier: Modifier = Modifier, starSize: Dp = 22.dp, total: Int = 3) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(total) { i -> StarShape(i < filled, Modifier.size(starSize)) }
    }
}

@Composable
fun StarShape(filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val outer = size.minDimension / 2
        val inner = outer * 0.47f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = -PI / 2 + i * PI / 5
            val x = c.x + (r * cos(a)).toFloat()
            val y = c.y + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, if (filled) Kid.Gold else Color(0xFFE4E0EE))
        drawPath(path, if (filled) Kid.GoldDark else Color(0xFFCFCADC), style = Stroke(width = size.minDimension * 0.07f))
    }
}

/** A burst of falling confetti that plays once each time [key] changes. */
@Composable
fun Confetti(key: Any, modifier: Modifier = Modifier) {
    val progress = remember(key) { Animatable(0f) }
    val pieces = remember(key) {
        val colors = listOf(Kid.Purple, Kid.Green, Kid.Orange, Kid.Pink, Kid.Blue, Kid.Gold)
        List(90) {
            ConfettiBit(
                x = Random.nextFloat(),
                delay = Random.nextFloat() * 0.35f,
                speed = 0.7f + Random.nextFloat() * 0.6f,
                drift = (Random.nextFloat() - 0.5f) * 0.25f,
                spin = (Random.nextFloat() - 0.5f) * 900f,
                color = colors[it % colors.size],
                wide = Random.nextBoolean(),
            )
        }
    }
    LaunchedEffect(key) { progress.animateTo(1f, tween(3200, easing = LinearEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val w = size.width
        val h = size.height
        val bit = (minOf(w, h) * 0.018f).coerceAtLeast(6f)
        for (c in pieces) {
            val local = ((p - c.delay) / (1f - c.delay)).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val x = (c.x + c.drift * local) * w
            val y = -bit * 2 + local * c.speed * (h + bit * 4)
            rotate(c.spin * local, pivot = Offset(x, y)) {
                val s = if (c.wide) Size(bit * 1.6f, bit * 0.8f) else Size(bit, bit)
                drawRect(c.color, Offset(x - s.width / 2, y - s.height / 2), s, alpha = 1f - local * 0.3f)
            }
        }
    }
}

private class ConfettiBit(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val drift: Float,
    val spin: Float,
    val color: Color,
    val wide: Boolean,
)

/** Lets the child pick what a pawn turns into. */
@Composable
fun PromotionDialog(white: Boolean, onPick: (type: Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        KidCard {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your pawn made it! 🎉", style = MaterialTheme.typography.titleLarge, color = Kid.Ink)
                Spacer(Modifier.size(6.dp))
                Text("What should it become?", style = MaterialTheme.typography.bodyLarge, color = Kid.InkSoft)
                Spacer(Modifier.size(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (type in listOf(Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT)) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (type == Piece.QUEEN) Color(0xFFFFF3C4) else Color(0xFFF1ECFF))
                                .clickable(role = Role.Button) { onPick(type) }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PieceIcon(Piece.make(type, white), Modifier.size(58.dp))
                            Text(Piece.name(type), style = MaterialTheme.typography.labelMedium, color = Kid.Ink)
                        }
                    }
                }
            }
        }
    }
}

/** Two-button confirmation dialog in the app's style. */
@Composable
fun KidConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "Cancel",
) {
    Dialog(onDismissRequest = onDismiss) {
        KidCard {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Kid.Ink, textAlign = TextAlign.Center)
                Spacer(Modifier.size(8.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge, color = Kid.InkSoft, textAlign = TextAlign.Center)
                Spacer(Modifier.size(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KidButton(dismissText, onDismiss, color = Color(0xFF9A97B0), shade = Color(0xFF77748D), small = true)
                    KidButton(confirmText, onConfirm, color = Kid.Red, shade = Color(0xFFB83B3B), small = true)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Kid.InkSoft, modifier = modifier)
}

/** Small rotated "badge" ribbon, e.g. "Next!". */
@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .rotate(-6f)
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}
