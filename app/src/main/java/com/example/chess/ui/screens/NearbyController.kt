package com.example.chess.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import com.example.chess.data.GameKind
import com.example.chess.net.BeaconReceiver
import com.example.chess.net.BeaconSender
import com.example.chess.net.BluetoothHostListener
import com.example.chess.net.BluetoothScanner
import com.example.chess.net.BluetoothSupport
import com.example.chess.net.FoundHost
import com.example.chess.net.Link
import com.example.chess.net.Nearby
import com.example.chess.net.NearbySession
import com.example.chess.net.PairCode
import com.example.chess.net.TcpListener
import com.example.chess.net.bluetoothConnect
import com.example.chess.net.tcpConnect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Transport { WIFI, BLUETOOTH }

enum class NearbyStage { CHOOSE, HOSTING, SEARCHING, PICKING, CONNECTING, LIVE }

/** One game or tablet a child can tap in the "Join a game" list. */
class NearbyChoice(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val host: FoundHost? = null,
    val device: BluetoothDevice? = null,
)

/**
 * Everything about finding a friend's tablet and setting up the connection: hosting, looking for games, the picture code,
 * and turning that into a [NearbySession] that the match screen then plays. It lives for as long as the app, is driven from
 * the main thread, and keeps ticking the session while a game is going on.
 */
class NearbyController(private val app: Context, private val scope: CoroutineScope) {
    var stage by mutableStateOf(NearbyStage.CHOOSE)
        private set
    var transport by mutableStateOf(Transport.WIFI)
    var hostFirst by mutableStateOf(true)
    /** The game being set up (a joining tablet takes whatever the host chose). */
    var game by mutableStateOf(GameKind.CHESS)
        private set
    /** A friendly sentence to show when something didn't work. */
    var message by mutableStateOf<String?>(null)

    var hostCode by mutableStateOf<List<Int>>(emptyList())
        private set
    var hostAvatar by mutableIntStateOf(0)
        private set
    /** Shown small, for a grown-up, in case games can't be found automatically. */
    var hostAddress by mutableStateOf<String?>(null)
        private set
    var hostBluetoothName by mutableStateOf("")
        private set

    val choices = mutableStateListOf<NearbyChoice>()
    var picks by mutableStateOf<List<Int>>(emptyList())
        private set
    var chosen by mutableStateOf<NearbyChoice?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set

    var session by mutableStateOf<NearbySession?>(null)
        private set

    private var ticker: Job? = null
    private var beaconSender: BeaconSender? = null
    private var beaconReceiver: BeaconReceiver? = null
    private var btHost: BluetoothHostListener? = null
    private var btScanner: BluetoothScanner? = null
    private val btFound = LinkedHashMap<String, BluetoothDevice>()
    private var multicastLock: WifiManager.MulticastLock? = null

    // ---------------------------------------------------------------- hosting

    /** Starts waiting for a friend. For Bluetooth the caller must already hold the permissions and have Bluetooth switched on. */
    fun startHosting(kind: GameKind) {
        leave()
        game = kind
        message = null
        val code = PairCode.random()
        hostCode = code
        hostAvatar = PairCode.randomAvatar()
        val listener = when (transport) {
            Transport.WIFI -> {
                val address = BeaconSender.localAddress()
                if (address == null) {
                    message = NO_WIFI
                    return
                }
                hostAddress = address
                try { TcpListener(Nearby.TCP_PORT) } catch (_: Exception) {
                    try { TcpListener(0) } catch (_: Exception) { message = NO_WIFI; return }
                }
            }
            Transport.BLUETOOTH -> {
                val l = BluetoothHostListener(app)
                if (!l.start()) {
                    message = "Bluetooth isn't working right now. Check that it's switched on."
                    return
                }
                hostBluetoothName = BluetoothSupport.localName(app)
                hostAddress = null
                l
            }
        }
        val s = NearbySession.host(kind.id, hostFirst, code, listener)
        s.attach(::onLobbyEvent)
        session = s
        if (transport == Transport.WIFI) {
            lockMulticast(true)
            val sid = PairCode.newNonce()
            beaconSender = BeaconSender(BeaconSender.broadcastTargets()).also { b ->
                b.start { BeaconSender.payload(kind.id, s.port, sid, hostAvatar) }
            }
        } else {
            btHost = listener as? BluetoothHostListener
        }
        stage = NearbyStage.HOSTING
        startTicker()
    }

    // ---------------------------------------------------------------- joining

    /** Starts looking for games. For Bluetooth the caller must already hold the permissions and have Bluetooth switched on. */
    fun startSearching() {
        leave()
        message = null
        choices.clear()
        if (transport == Transport.WIFI) {
            if (BeaconSender.localAddress() == null) {
                message = NO_WIFI
                return
            }
            lockMulticast(true)
            beaconReceiver = BeaconReceiver().also { it.start() }
        } else {
            btFound.clear()
            refreshBluetoothChoices()
            startBluetoothScan()
        }
        stage = NearbyStage.SEARCHING
        startTicker()
    }

    /** Looks for Bluetooth tablets again (or for the first time, once the scan permission has been given). */
    fun startBluetoothScan() {
        btScanner?.stop()
        if (!BluetoothSupport.hasPermissions(app, BluetoothSupport.scanPermissions())) {
            scanning = false
            return
        }
        val scanner = BluetoothScanner(
            app,
            onFound = { d ->
                btFound[d.address] = d
                refreshBluetoothChoices()
            },
            onFinished = { scanning = false },
        )
        btScanner = scanner
        scanning = scanner.start()
    }

    private fun refreshBluetoothChoices() {
        choices.clear()
        val paired = BluetoothSupport.bondedDevices(app)
        for (d in paired) choices.add(bluetoothChoice(d, "Paired"))
        for (d in btFound.values) {
            if (paired.none { it.address == d.address }) choices.add(bluetoothChoice(d, "Nearby"))
        }
    }

    private fun bluetoothChoice(d: BluetoothDevice, note: String) =
        NearbyChoice(d.address, "🔵", BluetoothSupport.deviceName(d), note, device = d)

    private fun refreshWifiChoices() {
        val hosts = beaconReceiver?.hosts() ?: return
        val ids = hosts.map { "${it.ip}:${it.port}" }
        if (choices.map { it.id } == ids) return
        choices.clear()
        for (h in hosts) {
            val gameName = GameKind.byId(h.kind).title
            choices.add(NearbyChoice("${h.ip}:${h.port}", PairCode.AVATARS[h.avatar], "$gameName game", "Tap if you see the ${PairCode.AVATARS[h.avatar]}", host = h))
        }
    }

    /** A grown-up typed the other tablet's number (like 192.168.1.20). */
    fun chooseTyped(text: String): Boolean {
        val t = text.trim()
        val ip = t.substringBefore(':')
        val port = t.substringAfter(':', "").toIntOrNull() ?: Nearby.TCP_PORT
        if (ip.split('.').size != 4 || ip.split('.').any { it.toIntOrNull() !in 0..255 }) return false
        choose(NearbyChoice("$ip:$port", "⌨️", "Tablet at $ip", "Typed in", host = FoundHost(ip, port, "", 0, "", 0L)))
        return true
    }

    fun choose(choice: NearbyChoice) {
        chosen = choice
        picks = emptyList()
        message = null
        stage = NearbyStage.PICKING
    }

    fun backToList() {
        picks = emptyList()
        chosen = null
        message = null
        stage = NearbyStage.SEARCHING
    }

    /** The child tapped a picture. At four pictures the code is tried straight away. */
    fun togglePick(index: Int) {
        if (stage != NearbyStage.PICKING) return
        picks = if (index in picks) picks - index else if (picks.size < PairCode.SIZE) picks + index else picks
        if (picks.size == PairCode.SIZE) connectWithPicks()
    }

    private fun connectWithPicks() {
        val choice = chosen ?: return
        val code = picks
        val connect: ((Link?) -> Unit) -> Unit = when {
            choice.host != null -> { done -> tcpConnect(choice.host.ip, choice.host.port, done) }
            choice.device != null -> { done -> bluetoothConnect(app, choice.device, done) }
            else -> return
        }
        val s = NearbySession.guest(code, connect)
        s.attach(::onLobbyEvent)
        session = s
        stage = NearbyStage.CONNECTING
    }

    // ---------------------------------------------------------------- events while setting up

    private fun onLobbyEvent(e: NearbySession.Event) {
        when (e) {
            is NearbySession.Event.Joined -> {
                // From here on the match screen listens; hold back anything that arrives in between.
                session?.attach(null)
                game = GameKind.byId(e.kind)
                stopDiscovery()
                stage = NearbyStage.LIVE
            }
            is NearbySession.Event.Rejected -> failedToJoin(rejectMessage(e.reason), askAgain = e.reason == "code")
            NearbySession.Event.CouldNotConnect -> failedToJoin(
                if (transport == Transport.WIFI) "I couldn't reach that tablet. Are you both on the same Wi-Fi?"
                else "I couldn't reach that tablet. Is its Chess Buddies waiting for a friend?",
                askAgain = false,
            )
            else -> {}
        }
    }

    private fun failedToJoin(text: String, askAgain: Boolean) {
        session?.leave()
        session = null
        picks = emptyList()
        message = text
        stage = if (askAgain && chosen != null) NearbyStage.PICKING else NearbyStage.SEARCHING
    }

    // ---------------------------------------------------------------- the session clock

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var i = 0
            while (isActive) {
                session?.update(SystemClock.elapsedRealtime())
                if (stage == NearbyStage.SEARCHING && transport == Transport.WIFI && i % 10 == 0) refreshWifiChoices()
                i++
                delay(50)
            }
        }
    }

    // ---------------------------------------------------------------- shutting down

    private fun stopDiscovery() {
        beaconSender?.stop()
        beaconSender = null
        beaconReceiver?.stop()
        beaconReceiver = null
        btScanner?.stop()
        btScanner = null
        scanning = false
        lockMulticast(false)
    }

    /** Stops everything: the game, the waiting, the searching. Safe to call at any time. */
    fun leave() {
        ticker?.cancel()
        ticker = null
        session?.leave()
        session = null
        stopDiscovery()
        btHost?.stop()
        btHost = null
        choices.clear()
        btFound.clear()
        picks = emptyList()
        chosen = null
        hostCode = emptyList()
        stage = NearbyStage.CHOOSE
    }

    @SuppressLint("WifiManagerLeak")
    private fun lockMulticast(on: Boolean) {
        try {
            if (on) {
                if (multicastLock == null) {
                    val wm = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    multicastLock = wm.createMulticastLock("chessbuddies-nearby").also { it.setReferenceCounted(false) }
                }
                multicastLock?.acquire()
            } else {
                multicastLock?.takeIf { it.isHeld }?.release()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val NO_WIFI = "I can't find a Wi-Fi connection. Switch Wi-Fi on and join the same network on both tablets."

        fun rejectMessage(reason: String) = when (reason) {
            "code" -> "Hmm, those pictures didn't match. Look at the other tablet and try again!"
            "version" -> "That tablet has a different version of Chess Buddies. Update both tablets and try again."
            "busy" -> "That game already has two players."
            "wait" -> "Too many tries. Wait a little bit, then try again."
            else -> "I couldn't join that game."
        }
    }
}
