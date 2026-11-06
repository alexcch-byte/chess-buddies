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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.data.GameKind
import com.example.chess.data.ProgressStore
import com.example.chess.lessons.LessonInfo
import com.example.chess.theme.Kid
import com.example.chess.ui.components.Badge
import com.example.chess.ui.components.CountPill
import com.example.chess.ui.components.KidScreen
import com.example.chess.ui.components.TopBar

private val UnitColors = listOf(Kid.Purple, Kid.Orange, Kid.Red, Kid.Blue, Kid.Green)

@Composable
fun LessonMapScreen(game: GameKind, progress: ProgressStore, onOpenLesson: (LessonInfo) -> Unit, onBack: () -> Unit) {
    val next = progress.nextLesson(game)
    KidScreen {
        Column(Modifier.fillMaxSize()) {
            TopBar("${game.title} Lessons", onBack) {
                CountPill("⭐", "${progress.totalStars(game)} / ${progress.maxStars(game)}")
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                var number = 0
                game.units.forEachIndexed { unitIndex, unit ->
                    val color = UnitColors[unitIndex % UnitColors.size]
                    item(span = { GridItemSpan(maxLineSpan) }, key = "unit-${unit.title}") {
                        Row(
                            Modifier.padding(top = if (unitIndex == 0) 4.dp else 14.dp, start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(unit.emoji, fontSize = 26.sp)
                            Spacer(Modifier.width(10.dp))
                            Text("Unit ${unitIndex + 1}: ${unit.title}", style = MaterialTheme.typography.titleLarge, color = Kid.Ink)
                        }
                    }
                    val numbered = unit.lessons.map { ++number to it }
                    items(numbered, key = { it.second.id }) { (n, lesson) ->
                        LessonCard(
                            number = n,
                            lesson = lesson,
                            color = color,
                            stars = progress.lessonStars(lesson),
                            maxStars = progress.lessonMaxStars(lesson),
                            isNext = lesson == next,
                            finished = progress.isLessonFinished(lesson),
                            onClick = { onOpenLesson(lesson) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonCard(
    number: Int,
    lesson: LessonInfo,
    color: Color,
    stars: Int,
    maxStars: Int,
    isNext: Boolean,
    finished: Boolean,
    onClick: () -> Unit,
) {
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .then(if (isNext) Modifier.border(3.dp, Kid.Gold, RoundedCornerShape(22.dp)) else Modifier)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                LessonIcon(lesson, Modifier.size(58.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Lesson $number", style = MaterialTheme.typography.labelMedium, color = color)
                Text(lesson.title, style = MaterialTheme.typography.titleLarge, color = Kid.Ink)
                Text(lesson.tagline, style = MaterialTheme.typography.bodyMedium, color = Kid.InkSoft, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { if (maxStars == 0) 0f else stars.toFloat() / maxStars },
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Kid.Gold,
                        trackColor = Color(0xFFF0ECF7),
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("⭐ $stars/$maxStars", style = MaterialTheme.typography.labelMedium, color = Kid.InkSoft)
                }
            }
        }
        when {
            isNext -> Badge("Next!", Kid.Orange, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 10.dp))
            finished -> Badge(if (stars == maxStars) "Perfect!" else "Done!", Kid.Green, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 10.dp))
        }
    }
}
