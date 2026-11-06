package com.example.chess.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.chess.data.GameKind
import com.example.chess.net.BluetoothSupport
import com.example.chess.net.PairCode
import com.example.chess.theme.Kid
import com.example.chess.ui.components.KidButton
import com.example.chess.ui.components.KidCard
import com.example.chess.ui.components.KidScreen
import com.example.chess.ui.components.SectionLabel
import com.example.chess.ui.components.TopBar

/** Holds the thing to do once a permission or "switch Bluetooth on" question has been answered. */
private class PendingStep {
    var action: (() -> Unit)? = null
}

@Composable
fun NearbyScreen(
    controller: NearbyController,
    game: GameKind,
    onMatchReady: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val step = remember { PendingStep() }
    val stage = controller.stage

    // Both the lobby and the list live inside this screen; going back goes up one level, not out of the app.
    val goBack: () -> Unit = {
        when (stage) {
            NearbyStage.CHOOSE -> onBack()
            NearbyStage.PICKING -> controller.backToList()
            else -> controller.leave()
        }
    }
    BackHandler(onBack = goBack)

    LaunchedEffect(stage) {
        if (stage == NearbyStage.LIVE) onMatchReady()
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val next = step.action
        step.action = null
        if (granted.values.all { it }) next?.invoke()
        else controller.message = "Bluetooth needs your OK to find the other tablet. Try again and tap Allow."
    }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val next = step.action
        step.action = null
        if (BluetoothSupport.isEnabled(context)) next?.invoke() else controller.message = "Bluetooth is switched off. Switch it on and try again."
    }
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    /** Makes sure Bluetooth is there, allowed and on; then runs [then]. */
    fun withBluetooth(perms: Array<String>, then: () -> Unit) {
        if (!BluetoothSupport.isAvailable(context)) {
            controller.message = "This tablet doesn't have Bluetooth. Try Wi-Fi instead."
            return
        }
        if (perms.isNotEmpty() && !BluetoothSupport.hasPermissions(context, perms)) {
            step.action = { withBluetooth(perms, then) }
            permissions.launch(perms)
            return
        }
        if (!BluetoothSupport.isEnabled(context)) {
            step.action = { withBluetooth(perms, then) }
            try {
                enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: Exception) {
                controller.message = "Switch Bluetooth on in Settings, then try again."
            }
            return
        }
        then()
    }

    fun makeVisible() {
        try {
            discoverable.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            )
        } catch (_: Exception) {
        }
    }

    val startHosting: () -> Unit = {
        if (controller.transport == Transport.WIFI) {
            controller.startHosting(game)
        } else {
            withBluetooth(BluetoothSupport.connectPermissions()) {
                controller.startHosting(game)
                if (controller.stage == NearbyStage.HOSTING) makeVisible()
            }
        }
    }
    val startJoining: () -> Unit = {
        if (controller.transport == Transport.WIFI) {
            controller.startSearching()
        } else {
            withBluetooth(BluetoothSupport.scanPermissions()) { controller.startSearching() }
        }
    }

    KidScreen {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                when (stage) {
                    NearbyStage.CHOOSE -> "Play Nearby"
                    NearbyStage.HOSTING -> "Start a game"
                    NearbyStage.SEARCHING, NearbyStage.PICKING, NearbyStage.CONNECTING -> "Join a game"
                    NearbyStage.LIVE -> "Play Nearby"
                },
                goBack,
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    Modifier.widthIn(max = 620.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (stage) {
                        NearbyStage.CHOOSE, NearbyStage.LIVE -> ChooseStage(controller, game, startHosting, startJoining)
                        NearbyStage.HOSTING -> HostStage(controller, ::makeVisible)
                        NearbyStage.SEARCHING -> SearchStage(controller, onRescan = { withBluetooth(BluetoothSupport.scanPermissions()) { controller.startBluetoothScan() } })
                        NearbyStage.PICKING -> PickStage(controller)
                        NearbyStage.CONNECTING -> {
                            Spacer(Modifier.height(30.dp))
                            Waiting("Joining the game")
                        }
                    }
                    controller.message?.let { MessageCard(it) }
                }
            }
        }
    }
}

// -------------------------------------------------------------------- choose

@Composable
private fun ChooseStage(controller: NearbyController, game: GameKind, onHost: () -> Unit, onJoin: () -> Unit) {
    val firstName = if (game == GameKind.CHESS) "White" else "Red"
    Text("📡", fontSize = 56.sp)
    Text(
        "Play ${game.title} with a friend on another tablet. Both tablets need Chess Buddies.",
        style = MaterialTheme.typography.bodyLarge,
        color = Kid.InkSoft,
        textAlign = TextAlign.Center,
    )

    SectionLabel("How are the tablets connected?")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Chip("📶  Wi-Fi", controller.transport == Transport.WIFI, Modifier.weight(1f)) { controller.transport = Transport.WIFI }
        Chip("🔵  Bluetooth", controller.transport == Transport.BLUETOOTH, Modifier.weight(1f)) { controller.transport = Transport.BLUETOOTH }
    }
    Text(
        if (controller.transport == Transport.WIFI) "Both tablets must be on the same Wi-Fi."
        else "Keep the tablets close together. Bluetooth must be switched on.",
        style = MaterialTheme.typography.bodyMedium,
        color = Kid.InkSoft,
        textAlign = TextAlign.Center,
    )

    KidCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Start a game", style = MaterialTheme.typography.titleLarge, color = Kid.Ink)
            Text("I want to be…", style = MaterialTheme.typography.bodyMedium, color = Kid.InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Chip(firstName, controller.hostFirst, Modifier.weight(1f)) { controller.hostFirst = true }
                Chip("Black", !controller.hostFirst, Modifier.weight(1f)) { controller.hostFirst = false }
            }
            KidButton("Start a game", onHost, emoji = "🏠", color = Kid.Green, shade = Kid.GreenDark)
        }
    }
    KidCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Join a game", style = MaterialTheme.typography.titleLarge, color = Kid.Ink)
            Text("Your friend starts a game first. Then you find it here.", style = MaterialTheme.typography.bodyMedium, color = Kid.InkSoft, textAlign = TextAlign.Center)
            KidButton("Join a game", onJoin, emoji = "🔎", color = Kid.Orange, shade = Kid.OrangeDark)
        }
    }
}

// -------------------------------------------------------------------- host

@Composable
private fun HostStage(controller: NearbyController, onMakeVisible: () -> Unit) {
    val avatar = PairCode.AVATARS[controller.hostAvatar]
    Text(avatar, fontSize = 72.sp)
    Text(
        "You're the $avatar!",
        style = MaterialTheme.typography.headlineSmall,
        color = Kid.Ink,
        textAlign = TextAlign.Center,
    )
    Text(
        if (controller.transport == Transport.WIFI) "On your friend's tablet, tap Join a game, tap this $avatar game, then tap these 4 pictures:"
        else "On your friend's tablet, tap Join a game, tap this tablet's name, then tap these 4 pictures:",
        style = MaterialTheme.typography.bodyLarge,
        color = Kid.InkSoft,
        textAlign = TextAlign.Center,
    )
    KidCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (i in controller.hostCode) PictureTile(PairCode.PICTURES[i], selected = false, big = true, onClick = null)
        }
    }
    Waiting("Waiting for your friend")
    if (controller.transport == Transport.WIFI) {
        controller.hostAddress?.let {
            Text(
                "Grown-ups: if the game doesn't show up, type this number on the other tablet: $it",
                style = MaterialTheme.typography.bodyMedium,
                color = Kid.InkSoft,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        Text(
            "This tablet is called \"${controller.hostBluetoothName}\".",
            style = MaterialTheme.typography.bodyMedium,
            color = Kid.InkSoft,
            textAlign = TextAlign.Center,
        )
        KidButton("Let others see this tablet", onMakeVisible, emoji = "👀", color = Kid.Blue, shade = Color(0xFF0767B3), small = true)
    }
    KidButton("Stop waiting", controller::leave, color = Color(0xFF9A97B0), shade = Color(0xFF77748D), small = true)
}

// -------------------------------------------------------------------- join

@Composable
private fun SearchStage(controller: NearbyController, onRescan: () -> Unit) {
    var typing by remember { mutableStateOf(false) }
    if (controller.choices.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Waiting(if (controller.transport == Transport.WIFI) "Looking for games" else if (controller.scanning) "Looking for tablets" else "No tablets found yet")
        Text(
            if (controller.transport == Transport.WIFI) "Your friend needs to tap Start a game first."
            else "Your friend needs to tap Start a game first, and keep their tablet close.",
            style = MaterialTheme.typography.bodyMedium,
            color = Kid.InkSoft,
            textAlign = TextAlign.Center,
        )
    } else {
        SectionLabel(if (controller.transport == Transport.WIFI) "Tap the game your friend started" else "Tap your friend's tablet")
        for (c in controller.choices) ChoiceCard(c) { controller.choose(c) }
    }
    if (controller.transport == Transport.BLUETOOTH) {
        KidButton(if (controller.scanning) "Looking…" else "Look again", onRescan, emoji = "🔄", color = Kid.Blue, shade = Color(0xFF0767B3), small = true, enabled = !controller.scanning)
        Text(
            "Can't see it? On some tablets you need to switch on Location in Settings, or pair the two tablets in Bluetooth settings first.",
            style = MaterialTheme.typography.bodyMedium,
            color = Kid.InkSoft,
            textAlign = TextAlign.Center,
        )
    } else {
        Text(
            "Grown-ups: can't see the game? Type its number.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF6C5CE7),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { typing = true }
                .padding(8.dp),
        )
    }
    if (typing) TypeNumberDialog(onDone = { typing = false }, onTyped = { controller.chooseTyped(it) })
}

@Composable
private fun TypeNumberDialog(onDone: () -> Unit, onTyped: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDone) {
        KidCard {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Type the other tablet's number", style = MaterialTheme.typography.titleLarge, color = Kid.Ink, textAlign = TextAlign.Center)
                Text("It is shown on the tablet that started the game, like 192.168.1.20", style = MaterialTheme.typography.bodyMedium, color = Kid.InkSoft, textAlign = TextAlign.Center)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; bad = false },
                    singleLine = true,
                    isError = bad,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (bad) Text("That doesn't look like a number. It has four parts with dots.", style = MaterialTheme.typography.bodyMedium, color = Kid.Red, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KidButton("Cancel", onDone, color = Color(0xFF9A97B0), shade = Color(0xFF77748D), small = true)
                    KidButton("Go", { if (onTyped(text)) onDone() else bad = true }, color = Kid.Green, shade = Kid.GreenDark, small = true)
                }
            }
        }
    }
}

@Composable
private fun PickStage(controller: NearbyController) {
    val choice = controller.chosen
    Text(choice?.emoji ?: "🔎", fontSize = 56.sp)
    Text(
        "Look at your friend's tablet. Tap the same 4 pictures!",
        style = MaterialTheme.typography.headlineSmall,
        color = Kid.Ink,
        textAlign = TextAlign.Center,
    )
    // Slots that fill up as pictures are tapped.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (slot in 0 until PairCode.SIZE) {
            val pic = controller.picks.getOrNull(slot)
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (pic != null) Color(0xFFFFF3C4) else Color(0xFFE9E5F7)),
                contentAlignment = Alignment.Center,
            ) {
                if (pic != null) Text(PairCode.PICTURES[pic], fontSize = 30.sp)
            }
        }
    }
    for (row in PairCode.PICTURES.indices.chunked(4)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (i in row) PictureTile(PairCode.PICTURES[i], selected = i in controller.picks, big = false) { controller.togglePick(i) }
        }
    }
}

// -------------------------------------------------------------------- small pieces

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Kid.Purple else Color.White)
            .border(2.dp, if (selected) Kid.PurpleDark else Color(0xFFE2DDF5), RoundedCornerShape(50))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (selected) Color.White else Kid.Ink, maxLines = 1)
    }
}

@Composable
private fun PictureTile(emoji: String, selected: Boolean, big: Boolean, onClick: (() -> Unit)?) {
    val size = if (big) 66.dp else 74.dp
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0xFFFFE08A) else Color.White)
            .border(3.dp, if (selected) Kid.Orange else Color(0xFFE2DDF5), RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = if (big) 38.sp else 40.sp)
    }
}

@Composable
private fun ChoiceCard(choice: NearbyChoice, onClick: () -> Unit) {
    KidCard(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(choice.emoji, fontSize = 40.sp)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(choice.title, style = MaterialTheme.typography.titleLarge, color = Kid.Ink, maxLines = 1)
                Text(choice.subtitle, style = MaterialTheme.typography.bodyMedium, color = Kid.InkSoft, maxLines = 1)
            }
            Text("▶", fontSize = 22.sp, color = Kid.Purple)
        }
    }
}

@Composable
private fun MessageCard(text: String) {
    KidCard(Modifier.fillMaxWidth(), color = Color(0xFFFFECEC)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Kid.Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
    }
}

/** "Waiting for your friend…" with three dots that take turns lighting up. */
@Composable
fun Waiting(text: String, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "waiting").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "waiting",
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Kid.Purple)
        repeat(3) { i ->
            Text(".", style = MaterialTheme.typography.titleMedium, color = Kid.Purple, modifier = Modifier.alpha(if (t >= i) 1f else 0.2f))
        }
    }
}
