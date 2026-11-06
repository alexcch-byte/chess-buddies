package com.example.chess.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.engine.Bot
import com.example.chess.engine.Piece
import com.example.chess.theme.Kid
import com.example.chess.ui.board.PieceArt.drawPiece

/**
 * Adorable cartoon vector characters designed specifically to motivate and engage 4-year-olds.
 * Includes Bubbles the Bunny, Kiki the Kitten, Felix the Fox, Professor Owl, Drake the Dragon,
 * and celebratory animated versions of Sir Hops.
 */
object CartoonArt {

    private fun parsePath(d: String): Path = PathParser().parsePathString(d).toPath()

    // --- BUBBLES THE BUNNY ---
    private val BunnyLeftEar = parsePath("M34,42 C26,26 22,6 34,4 C44,2 45,22 41,42 Z")
    private val BunnyLeftInner = parsePath("M33,38 C29,26 26,11 34,9 C41,8 41,23 38,38 Z")
    private val BunnyRightEar = parsePath("M64,42 C72,26 76,6 64,4 C54,2 53,22 57,42 Z")
    private val BunnyRightInner = parsePath("M65,38 C69,26 72,11 64,9 C57,8 57,23 60,38 Z")
    private val BunnyNose = parsePath("M50,65 L46,61 L54,61 Z")
    private val BunnyBowLeft = parsePath("M50,91 L40,84 L40,98 Z")
    private val BunnyBowRight = parsePath("M50,91 L60,84 L60,98 Z")

    fun DrawScope.drawBunny(alpha: Float = 1f) {
        val dark = Color(0xFF3A2D32)
        val outline = Stroke(2.8f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        val thinLine = Stroke(2.2f, cap = StrokeCap.Round)

        // Ears
        drawPath(BunnyLeftEar, Color(0xFFFFF9F5), alpha = alpha)
        drawPath(BunnyLeftEar, dark, alpha = alpha, style = outline)
        drawPath(BunnyLeftInner, Color(0xFFFFBCCB), alpha = alpha)

        drawPath(BunnyRightEar, Color(0xFFFFF9F5), alpha = alpha)
        drawPath(BunnyRightEar, dark, alpha = alpha, style = outline)
        drawPath(BunnyRightInner, Color(0xFFFFBCCB), alpha = alpha)

        // Head
        drawOval(Color(0xFFFFF9F5), topLeft = Offset(14f, 32f), size = Size(72f, 60f), alpha = alpha)
        drawOval(dark, topLeft = Offset(14f, 32f), size = Size(72f, 60f), alpha = alpha, style = outline)

        // Rosy Cheeks
        drawOval(Color(0xFFFFB4C4).copy(alpha = 0.65f * alpha), topLeft = Offset(21f, 62f), size = Size(14f, 10f))
        drawOval(Color(0xFFFFB4C4).copy(alpha = 0.65f * alpha), topLeft = Offset(65f, 62f), size = Size(14f, 10f))

        // Big Sparkly Cartoon Eyes
        drawOval(dark, topLeft = Offset(31.5f, 49f), size = Size(11f, 14f), alpha = alpha)
        drawCircle(Color.White, 2.8f, Offset(35f, 53f), alpha = alpha)
        drawCircle(Color.White, 1.4f, Offset(39f, 58f), alpha = alpha)

        drawOval(dark, topLeft = Offset(57.5f, 49f), size = Size(11f, 14f), alpha = alpha)
        drawCircle(Color.White, 2.8f, Offset(61f, 53f), alpha = alpha)
        drawCircle(Color.White, 1.4f, Offset(65f, 58f), alpha = alpha)

        // Nose
        drawPath(BunnyNose, Color(0xFFFF7B9B), alpha = alpha)

        // Smile & Buck Teeth
        val smileL = Path().apply { moveTo(46f, 65f); quadraticTo(41f, 72f, 36f, 69f) }
        val smileR = Path().apply { moveTo(54f, 65f); quadraticTo(59f, 72f, 64f, 69f) }
        drawPath(smileL, dark, alpha = alpha, style = thinLine)
        drawPath(smileR, dark, alpha = alpha, style = thinLine)

        // Two cute buck teeth
        drawRoundRect(Color.White, topLeft = Offset(47f, 66f), size = Size(6f, 5f), style = Stroke(1.8f))
        drawRoundRect(Color.White, topLeft = Offset(47f, 66f), size = Size(6f, 5f))
        drawRoundRect(dark, topLeft = Offset(47f, 66f), size = Size(6f, 5f), style = Stroke(1.8f))
        drawLine(dark, Offset(50f, 66f), Offset(50f, 71f), strokeWidth = 1.4f)

        // Bowtie
        drawPath(BunnyBowLeft, Color(0xFF58C6FF), alpha = alpha)
        drawPath(BunnyBowLeft, dark, alpha = alpha, style = Stroke(2f))
        drawPath(BunnyBowRight, Color(0xFF58C6FF), alpha = alpha)
        drawPath(BunnyBowRight, dark, alpha = alpha, style = Stroke(2f))
        drawCircle(Color(0xFFFFDD44), 3.5f, Offset(50f, 91f), alpha = alpha)
        drawCircle(dark, 3.5f, Offset(50f, 91f), alpha = alpha, style = Stroke(1.8f))
    }

    // --- KIKI THE KITTEN ---
    private val KittenEarL = parsePath("M20,48 L26,16 L46,38 Z")
    private val KittenInnerL = parsePath("M26,42 L30,22 L42,36 Z")
    private val KittenEarR = parsePath("M80,48 L74,16 L54,38 Z")
    private val KittenInnerR = parsePath("M74,42 L70,22 L58,36 Z")
    private val KittenNose = parsePath("M50,65 L46,61 L54,61 Z")

    fun DrawScope.drawKitten(alpha: Float = 1f) {
        val dark = Color(0xFF38241C)
        val outline = Stroke(2.8f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        val ginger = Color(0xFFFFA54A)
        val gingerStripe = Color(0xFFD96E14)

        // Ears
        drawPath(KittenEarL, ginger, alpha = alpha)
        drawPath(KittenEarL, dark, alpha = alpha, style = outline)
        drawPath(KittenInnerL, Color(0xFFFFB7B2), alpha = alpha)

        drawPath(KittenEarR, ginger, alpha = alpha)
        drawPath(KittenEarR, dark, alpha = alpha, style = outline)
        drawPath(KittenInnerR, Color(0xFFFFB7B2), alpha = alpha)

        // Head
        drawOval(ginger, topLeft = Offset(15f, 32f), size = Size(70f, 56f), alpha = alpha)
        drawOval(dark, topLeft = Offset(15f, 32f), size = Size(70f, 56f), alpha = alpha, style = outline)

        // Forehead stripes
        drawLine(gingerStripe, Offset(50f, 33f), Offset(50f, 43f), strokeWidth = 2.8f, cap = StrokeCap.Round)
        drawLine(gingerStripe, Offset(43f, 36f), Offset(45f, 44f), strokeWidth = 2.4f, cap = StrokeCap.Round)
        drawLine(gingerStripe, Offset(57f, 36f), Offset(55f, 44f), strokeWidth = 2.4f, cap = StrokeCap.Round)

        // White Muzzle patch
        drawOval(Color(0xFFFFF7EE), topLeft = Offset(33f, 55f), size = Size(34f, 26f), alpha = alpha)

        // Rosy cheeks
        drawOval(Color(0xFFFF7B9B).copy(alpha = 0.5f * alpha), topLeft = Offset(19.5f, 60.5f), size = Size(13f, 9f))
        drawOval(Color(0xFFFF7B9B).copy(alpha = 0.5f * alpha), topLeft = Offset(67.5f, 60.5f), size = Size(13f, 9f))

        // Big Emerald Kitten Eyes
        drawOval(Color(0xFF2ECC71), topLeft = Offset(29.5f, 46.5f), size = Size(13f, 15f), alpha = alpha)
        drawOval(dark, topLeft = Offset(29.5f, 46.5f), size = Size(13f, 15f), alpha = alpha, style = Stroke(2f))
        drawOval(Color(0xFF1B4D3E), topLeft = Offset(31.5f, 47.5f), size = Size(9f, 13f), alpha = alpha)
        drawCircle(Color.White, 2.8f, Offset(34f, 51f), alpha = alpha)
        drawCircle(Color.White, 1.3f, Offset(38f, 56f), alpha = alpha)

        drawOval(Color(0xFF2ECC71), topLeft = Offset(57.5f, 46.5f), size = Size(13f, 15f), alpha = alpha)
        drawOval(dark, topLeft = Offset(57.5f, 46.5f), size = Size(13f, 15f), alpha = alpha, style = Stroke(2f))
        drawOval(Color(0xFF1B4D3E), topLeft = Offset(59.5f, 47.5f), size = Size(9f, 13f), alpha = alpha)
        drawCircle(Color.White, 2.8f, Offset(62f, 51f), alpha = alpha)
        drawCircle(Color.White, 1.3f, Offset(66f, 56f), alpha = alpha)

        // Pink nose & smile
        drawPath(KittenNose, Color(0xFFFF758F), alpha = alpha)
        val mouthL = Path().apply { moveTo(46f, 65f); quadraticTo(42f, 72f, 38f, 70f) }
        val mouthR = Path().apply { moveTo(54f, 65f); quadraticTo(58f, 72f, 62f, 70f) }
        drawPath(mouthL, dark, alpha = alpha, style = Stroke(2.2f, cap = StrokeCap.Round))
        drawPath(mouthR, dark, alpha = alpha, style = Stroke(2.2f, cap = StrokeCap.Round))

        // Whiskers
        drawLine(dark, Offset(27f, 62f), Offset(11f, 59f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        drawLine(dark, Offset(27f, 66f), Offset(10f, 67f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        drawLine(dark, Offset(73f, 62f), Offset(89f, 59f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        drawLine(dark, Offset(73f, 66f), Offset(90f, 67f), strokeWidth = 1.8f, cap = StrokeCap.Round)

        // Collar & Golden Bell
        val collar = Path().apply { moveTo(33f, 84f); quadraticTo(50f, 92f, 67f, 84f) }
        drawPath(collar, Color(0xFFFF4757), alpha = alpha, style = Stroke(4.5f, cap = StrokeCap.Round))
        drawCircle(Color(0xFFFFD32A), 4.5f, Offset(50f, 89f), alpha = alpha)
        drawCircle(Color(0xFFB38F00), 4.5f, Offset(50f, 89f), alpha = alpha, style = Stroke(1.5f))
    }

    // --- FELIX THE FOX ---
    private val FoxEarL = parsePath("M16,42 L22,8 L48,32 Z")
    private val FoxTipL = parsePath("M20,15 L22,8 L28,12 Z")
    private val FoxEarR = parsePath("M84,42 L78,8 L52,32 Z")
    private val FoxTipR = parsePath("M80,15 L78,8 L72,12 Z")
    private val FoxHead = parsePath("M18,50 C14,70 34,84 50,86 C66,84 86,70 82,50 C80,36 20,36 18,50 Z")
    private val FoxCheekL = parsePath("M18,50 C18,72 36,83 50,86 C38,76 26,64 24,50 Z")
    private val FoxCheekR = parsePath("M82,50 C82,72 64,83 50,86 C62,76 74,64 76,50 Z")

    fun DrawScope.drawFox(alpha: Float = 1f) {
        val dark = Color(0xFF261C14)
        val foxOrange = Color(0xFFE65100)
        val outline = Stroke(2.8f, join = StrokeJoin.Round, cap = StrokeCap.Round)

        // Ears
        drawPath(FoxEarL, foxOrange, alpha = alpha)
        drawPath(FoxEarL, dark, alpha = alpha, style = outline)
        drawPath(FoxTipL, dark, alpha = alpha)

        drawPath(FoxEarR, foxOrange, alpha = alpha)
        drawPath(FoxEarR, dark, alpha = alpha, style = outline)
        drawPath(FoxTipR, dark, alpha = alpha)

        // Head
        drawPath(FoxHead, foxOrange, alpha = alpha)
        drawPath(FoxHead, dark, alpha = alpha, style = outline)

        // Fluffy white cheeks
        drawPath(FoxCheekL, Color.White, alpha = alpha)
        drawPath(FoxCheekR, Color.White, alpha = alpha)

        // Left Amber Eye
        drawOval(Color(0xFFFFB300), topLeft = Offset(28.5f, 43.5f), size = Size(11f, 13f), alpha = alpha)
        drawOval(dark, topLeft = Offset(28.5f, 43.5f), size = Size(11f, 13f), alpha = alpha, style = Stroke(2f))
        drawOval(dark, topLeft = Offset(30.5f, 44.5f), size = Size(7f, 11f), alpha = alpha)
        drawCircle(Color.White, 2.2f, Offset(32f, 48f), alpha = alpha)
        drawCircle(Color.White, 1.1f, Offset(36f, 52f), alpha = alpha)

        // Right Playful Winking Eye
        val wink = Path().apply { moveTo(60f, 52f); quadraticTo(66f, 44f, 72f, 52f) }
        drawPath(wink, dark, alpha = alpha, style = Stroke(2.8f, cap = StrokeCap.Round))

        // Black button nose & smile
        drawOval(dark, topLeft = Offset(45.5f, 70.5f), size = Size(9f, 7f), alpha = alpha)
        val smile = Path().apply { moveTo(46f, 78f); quadraticTo(50f, 82f, 54f, 78f) }
        drawPath(smile, dark, alpha = alpha, style = Stroke(2.2f, cap = StrokeCap.Round))

        // Whiskers
        drawLine(dark, Offset(22f, 64f), Offset(10f, 63f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        drawLine(dark, Offset(78f, 64f), Offset(90f, 63f), strokeWidth = 1.8f, cap = StrokeCap.Round)
    }

    // --- PROFESSOR OWL ---
    private val OwlHornL = parsePath("M26,38 L20,16 L38,28 Z")
    private val OwlHornR = parsePath("M74,38 L80,16 L62,28 Z")
    private val OwlCap = parsePath("M50,14 L74,22 L50,30 L26,22 Z")
    private val OwlBeak = parsePath("M50,56 L46,65 L54,65 Z")

    fun DrawScope.drawOwl(alpha: Float = 1f) {
        val dark = Color(0xFF3E2723)
        val outline = Stroke(2.8f, join = StrokeJoin.Round, cap = StrokeCap.Round)

        // Tufted horns
        drawPath(OwlHornL, Color(0xFF8D6E63), alpha = alpha)
        drawPath(OwlHornL, dark, alpha = alpha, style = Stroke(2.4f, join = StrokeJoin.Round))
        drawPath(OwlHornR, Color(0xFF8D6E63), alpha = alpha)
        drawPath(OwlHornR, dark, alpha = alpha, style = Stroke(2.4f, join = StrokeJoin.Round))

        // Head Body
        drawOval(Color(0xFFA1887F), topLeft = Offset(15f, 28f), size = Size(70f, 60f), alpha = alpha)
        drawOval(dark, topLeft = Offset(15f, 28f), size = Size(70f, 60f), alpha = alpha, style = outline)

        // Eye Feathers
        drawCircle(Color(0xFFD7CCC8), 17f, Offset(35f, 56f), alpha = alpha)
        drawCircle(Color(0xFFD7CCC8), 17f, Offset(65f, 56f), alpha = alpha)

        // Round Yellow Glasses
        drawCircle(Color.White, 14f, Offset(35f, 56f), alpha = alpha)
        drawCircle(Color(0xFFFFB300), 14f, Offset(35f, 56f), alpha = alpha, style = Stroke(3f))
        drawCircle(Color.White, 14f, Offset(65f, 56f), alpha = alpha)
        drawCircle(Color(0xFFFFB300), 14f, Offset(65f, 56f), alpha = alpha, style = Stroke(3f))
        drawLine(Color(0xFFFFB300), Offset(48f, 56f), Offset(52f, 56f), strokeWidth = 3.5f, cap = StrokeCap.Round)

        // Golden Owl Eyes
        drawCircle(Color(0xFFFFC107), 8.5f, Offset(35f, 56f), alpha = alpha)
        drawCircle(Color(0xFF212121), 5.5f, Offset(35f, 56f), alpha = alpha)
        drawCircle(Color.White, 2.5f, Offset(33f, 53f), alpha = alpha)

        drawCircle(Color(0xFFFFC107), 8.5f, Offset(65f, 56f), alpha = alpha)
        drawCircle(Color(0xFF212121), 5.5f, Offset(65f, 56f), alpha = alpha)
        drawCircle(Color.White, 2.5f, Offset(63f, 53f), alpha = alpha)

        // Orange Beak
        drawPath(OwlBeak, Color(0xFFFF9800), alpha = alpha)
        drawPath(OwlBeak, Color(0xFFE65100), alpha = alpha, style = Stroke(1.6f, join = StrokeJoin.Round))

        // Scholar Mortarboard / Graduation Cap
        drawPath(OwlCap, Color(0xFF1A237E), alpha = alpha)
        drawPath(OwlCap, Color(0xFF0D47A1), alpha = alpha, style = Stroke(1.8f))
        drawCircle(Color(0xFFFFD54F), 2.2f, Offset(50f, 22f), alpha = alpha)
        val tassel = Path().apply { moveTo(50f, 22f); quadraticTo(62f, 24f, 66f, 34f) }
        drawPath(tassel, Color(0xFFFFD54F), alpha = alpha, style = Stroke(1.8f))
        drawCircle(Color(0xFFFFD54F), 1.8f, Offset(66f, 34f), alpha = alpha)
    }

    // --- DRAKE THE DRAGON ---
    private val DragonHornL = parsePath("M28,34 C18,24 16,12 24,10 C28,14 31,24 33,35 Z")
    private val DragonHornR = parsePath("M72,34 C82,24 84,12 76,10 C72,14 69,24 67,35 Z")
    private val DragonSpike1 = parsePath("M50,10 L44,22 L56,22 Z")
    private val DragonSpike2 = parsePath("M62,15 L57,26 L69,26 Z")
    private val StarSpark = parsePath("M64,68 L66,64 L68,68 L72,70 L68,72 L66,76 L64,72 L60,70 Z")

    fun DrawScope.drawDragon(alpha: Float = 1f) {
        val dark = Color(0xFF0077B6)
        val outline = Stroke(2.8f, join = StrokeJoin.Round, cap = StrokeCap.Round)

        // Orange dorsal spikes
        drawPath(DragonSpike1, Color(0xFFFF7043), alpha = alpha)
        drawPath(DragonSpike1, Color(0xFF264653), alpha = alpha, style = Stroke(2f))
        drawPath(DragonSpike2, Color(0xFFFF7043), alpha = alpha)
        drawPath(DragonSpike2, Color(0xFF264653), alpha = alpha, style = Stroke(2f))

        // Golden horns
        drawPath(DragonHornL, Color(0xFFFFCA28), alpha = alpha)
        drawPath(DragonHornL, Color(0xFF264653), alpha = alpha, style = Stroke(2.2f, join = StrokeJoin.Round))
        drawPath(DragonHornR, Color(0xFFFFCA28), alpha = alpha)
        drawPath(DragonHornR, Color(0xFF264653), alpha = alpha, style = Stroke(2.2f, join = StrokeJoin.Round))

        // Cyan dragon head
        drawOval(Color(0xFF48CAE4), topLeft = Offset(17f, 30f), size = Size(66f, 56f), alpha = alpha)
        drawOval(dark, topLeft = Offset(17f, 30f), size = Size(66f, 56f), alpha = alpha, style = outline)

        // Snout
        drawOval(Color(0xFF90E0EF), topLeft = Offset(28f, 52f), size = Size(44f, 32f), alpha = alpha)
        drawOval(dark, topLeft = Offset(28f, 52f), size = Size(44f, 32f), alpha = alpha, style = Stroke(2.2f))

        // Rosy cheeks
        drawOval(Color(0xFFFF85A1).copy(alpha = 0.6f * alpha), topLeft = Offset(20f, 59f), size = Size(12f, 8f))
        drawOval(Color(0xFFFF85A1).copy(alpha = 0.6f * alpha), topLeft = Offset(68f, 59f), size = Size(12f, 8f))

        // Big Friendly Purple Cartoon Eyes
        drawOval(Color(0xFF7209B7), topLeft = Offset(30.5f, 43.5f), size = Size(13f, 15f), alpha = alpha)
        drawCircle(Color.White, 3f, Offset(35f, 48f), alpha = alpha)
        drawCircle(Color.White, 1.3f, Offset(39f, 53f), alpha = alpha)

        drawOval(Color(0xFF7209B7), topLeft = Offset(56.5f, 43.5f), size = Size(13f, 15f), alpha = alpha)
        drawCircle(Color.White, 3f, Offset(61f, 48f), alpha = alpha)
        drawCircle(Color.White, 1.3f, Offset(65f, 53f), alpha = alpha)

        // Nostrils
        drawOval(dark, topLeft = Offset(43f, 63.5f), size = Size(4f, 5f), alpha = alpha)
        drawOval(dark, topLeft = Offset(53f, 63.5f), size = Size(4f, 5f), alpha = alpha)

        // Gentle smile
        val smile = Path().apply { moveTo(44f, 74f); quadraticTo(50f, 78f, 56f, 74f) }
        drawPath(smile, dark, alpha = alpha, style = Stroke(2.4f, cap = StrokeCap.Round))

        // Cute golden star smoke puff
        drawPath(StarSpark, Color(0xFFFFD166), alpha = alpha)
    }

    // --- SIR HOPS PARTY HAT & CELEBRATION ---
    private val PartyHat = parsePath("M44,2 L32,24 L56,20 Z")
    private val Star1 = parsePath("M18,24 L20,20 L22,24 L26,26 L22,28 L20,32 L18,28 L14,26 Z")
    private val Star2 = parsePath("M78,36 L80,32 L82,36 L86,38 L82,40 L80,44 L78,40 L74,38 Z")

    fun DrawScope.drawPartyHatAndStars(alpha: Float = 1f) {
        val dark = Color(0xFF28232F)
        drawPath(PartyHat, Color(0xFFFF4757), alpha = alpha)
        drawPath(PartyHat, dark, alpha = alpha, style = Stroke(2.2f, join = StrokeJoin.Round))
        drawLine(Color(0xFFFFD32A), Offset(36f, 17f), Offset(50f, 15f), strokeWidth = 2.5f)
        drawLine(Color(0xFF58C6FF), Offset(39f, 10f), Offset(47f, 9f), strokeWidth = 2.5f)
        drawCircle(Color(0xFFFFD32A), 3.5f, Offset(44f, 2f), alpha = alpha)
        drawCircle(dark, 3.5f, Offset(44f, 2f), alpha = alpha, style = Stroke(1.8f))

        // Floating celebration stars
        drawPath(Star1, Color(0xFFFFD700), alpha = alpha)
        drawPath(Star2, Color(0xFFFFD700), alpha = alpha)
    }
}

/** Animated cartoon animal avatar for any opponent bot. */
@Composable
fun AnimalAvatar(
    bot: Bot,
    modifier: Modifier = Modifier,
    thinking: Boolean = false,
    celebrating: Boolean = false,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar")
    val bob by infiniteTransition.animateFloat(
        initialValue = if (thinking || celebrating) -3f else 0f,
        targetValue = if (thinking) 3f else if (celebrating) 6f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (celebrating) 240 else 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "avatarBob",
    )

    Canvas(modifier.offset(y = bob.dp)) {
        val s = minOf(size.width, size.height)
        withTransform({
            translate((size.width - s) / 2, (size.height - s) / 2)
            scale(s / 100f, s / 100f, pivot = Offset.Zero)
        }) {
            when (bot) {
                Bot.BUNNY -> with(CartoonArt) { drawBunny() }
                Bot.KITTEN -> with(CartoonArt) { drawKitten() }
                Bot.FOX -> with(CartoonArt) { drawFox() }
                Bot.OWL -> with(CartoonArt) { drawOwl() }
                Bot.DRAGON -> with(CartoonArt) { drawDragon() }
            }
        }
    }
}

/**
 * An expressive, motivating Sir Hops mascot for the Coach Bubble and celebrations.
 * Wears a cheerful party hat when happy/celebrating!
 */
@Composable
fun SirHopsCoachAvatar(
    mood: Mood = Mood.NORMAL,
    modifier: Modifier = Modifier,
    celebrating: Boolean = false,
) {
    val isHappy = mood == Mood.HAPPY || celebrating
    val infiniteTransition = rememberInfiniteTransition(label = "sirHops")
    val bounce by infiniteTransition.animateFloat(
        initialValue = if (isHappy) -4f else 0f,
        targetValue = if (isHappy) 4f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(280, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sirHopsBounce",
    )

    Box(modifier.offset(y = bounce.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = minOf(size.width, size.height)
            withTransform({
                translate((size.width - s) / 2, (size.height - s) / 2)
            }) {
                drawPiece(Piece.KNIGHT, Offset.Zero, s)
                if (isHappy) {
                    withTransform({
                        scale(s / 100f, s / 100f, pivot = Offset.Zero)
                    }) {
                        with(CartoonArt) { drawPartyHatAndStars() }
                    }
                }
            }
        }
    }
}

/** Motivational phrases specifically crafted to delight and motivate a 4-year-old child. */
object KidCheer {
    private val star3Quotes = listOf(
        "SUPERSTAR! ⭐⭐⭐",
        "HIGH FIVE! 🖐️",
        "YOU'RE AMAZING! 🎉",
        "SIR HOPS IS SO PROUD! 🐴",
        "WOW, LOOK AT YOU GO! 🚀",
        "PUZZLE CHAMPION! 🏆",
    )

    private val star2Quotes = listOf(
        "GREAT JOB! ⭐⭐",
        "YOU DID IT! 🌟",
        "SO CLOSE TO PERFECT! 🎈",
        "WAY TO GO, BUDDY! 🐾",
    )

    private val solvedQuotes = listOf(
        "YOU SOLVED IT! ⭐",
        "NICE WORK! 👏",
        "YOU'RE GETTING BETTER! 🌈",
    )

    fun cheerForStars(stars: Int, seed: Int): String {
        val list = when (stars) {
            3 -> star3Quotes
            2 -> star2Quotes
            else -> solvedQuotes
        }
        return list[(seed and 0x7FFFFFFF) % list.size]
    }
}

/**
 * Big, colorful celebration banner shown when a 4-year-old solves a challenge.
 * Features an animated dancing character and high-five button!
 */
@Composable
fun KidVictoryBanner(
    stars: Int,
    seed: Int,
    onNext: () -> Unit,
    onRetry: (() -> Unit)? = null,
    nextLabel: String = "Next ➔",
    modifier: Modifier = Modifier,
) {
    val cheer = KidCheer.cheerForStars(stars, seed)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.horizontalGradient(
                    colors = if (stars == 3) listOf(Color(0xFF7A5AF8), Color(0xFFEE46BC))
                    else listOf(Color(0xFF38B2AC), Color(0xFF4299E1))
                )
            )
            .padding(18.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Cheering Sir Hops
                Box(
                    Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    SirHopsCoachAvatar(mood = Mood.HAPPY, modifier = Modifier.size(56.dp), celebrating = true)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    StarRow(stars, starSize = 34.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        cheer,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onRetry != null && stars < 3) {
                    KidButton("Try again", onRetry, emoji = "🔄", color = Kid.Blue, shade = Color(0xFF0767B3), small = true)
                }
                KidButton(
                    nextLabel,
                    onClick = onNext,
                    emoji = "🎉",
                    color = Kid.Gold,
                    shade = Kid.GoldDark,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}
