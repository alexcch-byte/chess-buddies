package com.example.chess.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.data.ProgressStore
import com.example.chess.theme.Kid
import com.example.chess.ui.board.BoardTheme
import com.example.chess.ui.components.KidButton
import com.example.chess.ui.components.KidCard
import com.example.chess.ui.components.KidConfirmDialog
import com.example.chess.ui.components.KidScreen
import com.example.chess.ui.components.SectionLabel
import com.example.chess.ui.components.TopBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(progress: ProgressStore, onBack: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    KidScreen {
        Column(Modifier.fillMaxSize()) {
            TopBar("Settings", onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionLabel("Helpers")
                KidCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        ToggleRow("🔊", "Sounds", "Clicks, dings and happy tunes", progress.soundOn) { progress.soundOn = it }
                        ToggleRow("🟢", "Show possible moves", "Dots show where a piece can go", progress.showLegalMoves) { progress.showLegalMoves = it }
                        ToggleRow("🧑‍🏫", "Coach tips", "Warnings about risky moves, plus helpful tips", progress.coachTips) { progress.coachTips = it }
                        ToggleRow("🚨", "Danger alerts", "Red circles on your pieces that can be captured", progress.dangerAlerts) { progress.dangerAlerts = it }
                        ToggleRow("🔤", "Board letters and numbers", "Show a-h and 1-8 around the board", progress.showCoordinates) { progress.showCoordinates = it }
                    }
                }

                SectionLabel("Board colors")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (theme in BoardTheme.entries) {
                        ThemeChoice(theme, selected = theme == progress.boardTheme) { progress.boardTheme = theme }
                    }
                }

                SectionLabel("For grown-ups")
                KidCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Progress is saved on this device: ${progress.allStars()} stars and ${progress.allWins()} practice wins.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Kid.InkSoft,
                        )
                        KidButton("Reset progress", { confirmReset = true }, emoji = "🗑️", color = Kid.Red, shade = Color(0xFFB83B3B), small = true)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (confirmReset) {
        KidConfirmDialog(
            title = "Reset all progress?",
            message = "This clears every lesson star and practice win. It can't be undone.",
            confirmText = "Reset",
            onConfirm = {
                progress.resetProgress()
                confirmReset = false
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun ToggleRow(emoji: String, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Kid.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Kid.InkSoft)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Kid.Green),
        )
    }
}

@Composable
private fun ThemeChoice(theme: BoardTheme, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0xFFF1ECFF) else Color.White)
            .border(if (selected) 3.dp else 0.dp, if (selected) Kid.Purple else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            val cell = size.width / 4
            for (r in 0 until 4) for (f in 0 until 4) {
                drawRect(if ((r + f) % 2 == 0) theme.light else theme.dark, Offset(f * cell, r * cell), Size(cell, cell))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(theme.label, style = MaterialTheme.typography.titleMedium, color = Kid.Ink)
    }
}
