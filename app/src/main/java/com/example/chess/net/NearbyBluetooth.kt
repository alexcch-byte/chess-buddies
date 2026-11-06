package com.example.chess.net

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/** The Bluetooth "service" a hosting tablet offers; a joining tablet looks for it by this number. */
val NEARBY_BT_UUID: UUID = UUID.fromString("5c1e7f0a-3b94-4c3e-9a55-2d7e8f6b1a42")
private const val NEARBY_BT_NAME = "ChessBuddies"

/** Adapter state and the permissions each Android version asks for. Every call is safe to make without permission. */
@SuppressLint("MissingPermission")
object BluetoothSupport {
    fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isAvailable(context: Context) = adapter(context) != null

    fun isEnabled(context: Context) = try { adapter(context)?.isEnabled == true } catch (_: SecurityException) { false }

    /** Needed to host or to connect. Android 11 and older ask for nothing at run time here. */
    fun connectPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        else emptyArray()

    /** Needed to look for tablets that aren't paired yet. */
    fun scanPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermissions(context: Context, perms: Array<String>): Boolean =
        perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun bondedDevices(context: Context): List<BluetoothDevice> = try {
        adapter(context)?.bondedDevices?.toList() ?: emptyList()
    } catch (_: SecurityException) {
        emptyList()
    }

    fun deviceName(device: BluetoothDevice): String = try {
        device.name ?: device.address
    } catch (_: SecurityException) {
        device.address
    }

    fun localName(context: Context): String = try {
        adapter(context)?.name ?: Build.MODEL
    } catch (_: SecurityException) {
        Build.MODEL
    }
}

private fun bluetoothLink(socket: BluetoothSocket): Link =
    StreamLink(socket.inputStream, socket.outputStream) { try { socket.close() } catch (_: Exception) { } }

/**
 * Waits for a friend over Bluetooth. It listens on a secure and an insecure RFCOMM socket at once: a secure one insists that
 * the tablets are paired first, and Android's automatic pairing is too quick for a child to tap "OK", so the insecure one is
 * what normally gets used. Whoever connects through either is handed to the session, which turns away extras.
 */
@SuppressLint("MissingPermission")
class BluetoothHostListener(private val context: Context) : Listener {
    override val port: Int get() = 0

    private val queue = ConcurrentLinkedQueue<Link>()
    @Volatile private var running = false
    private var secure: BluetoothServerSocket? = null
    private var insecure: BluetoothServerSocket? = null

    /** False when Bluetooth is off, missing or not allowed. */
    fun start(): Boolean {
        if (running) return true
        val adapter = BluetoothSupport.adapter(context) ?: return false
        secure = try { adapter.listenUsingRfcommWithServiceRecord(NEARBY_BT_NAME, NEARBY_BT_UUID) } catch (_: Exception) { null }
        insecure = try { adapter.listenUsingInsecureRfcommWithServiceRecord(NEARBY_BT_NAME, NEARBY_BT_UUID) } catch (_: Exception) { null }
        if (secure == null && insecure == null) return false
        running = true
        secure?.let { s -> Thread({ acceptLoop(s) }, "nearby-bt-accept-secure").also { it.isDaemon = true; it.start() } }
        insecure?.let { s -> Thread({ acceptLoop(s) }, "nearby-bt-accept-insecure").also { it.isDaemon = true; it.start() } }
        return true
    }

    private fun acceptLoop(server: BluetoothServerSocket) {
        while (running) {
            val socket = try { server.accept() } catch (_: IOException) { break }
            try {
                queue.add(bluetoothLink(socket))
            } catch (_: IOException) {
                try { socket.close() } catch (_: Exception) { }
            }
        }
    }

    override fun poll(): Link? = queue.poll()

    override fun stop() {
        running = false
        try { secure?.close() } catch (_: Exception) { }
        try { insecure?.close() } catch (_: Exception) { }
        secure = null
        insecure = null
    }
}

/**
 * Connects to a hosting tablet in the background. It tries the normal ways first (insecure, then secure, looking the service
 * up by its number), then connects straight to RFCOMM channel 1, which gets around tablets whose service lookup is unreliable.
 */
@SuppressLint("MissingPermission", "DiscouragedPrivateApi")
fun bluetoothConnect(context: Context, device: BluetoothDevice, done: (Link?) -> Unit) {
    Thread({
        try { BluetoothSupport.adapter(context)?.cancelDiscovery() } catch (_: SecurityException) { }
        // Connecting while discovery is still winding down is a common cause of "read failed".
        try { Thread.sleep(200) } catch (_: InterruptedException) { }

        fun channelOne(secure: Boolean): BluetoothSocket {
            val method = device.javaClass.getMethod(if (secure) "createRfcommSocket" else "createInsecureRfcommSocket", Int::class.javaPrimitiveType)
            return method.invoke(device, 1) as BluetoothSocket
        }

        val attempts: List<() -> BluetoothSocket> = listOf(
            { device.createInsecureRfcommSocketToServiceRecord(NEARBY_BT_UUID) },
            { device.createRfcommSocketToServiceRecord(NEARBY_BT_UUID) },
            { channelOne(false) },
            { channelOne(true) },
        )
        var link: Link? = null
        for ((i, make) in attempts.withIndex()) {
            val socket = try { make() } catch (_: Exception) { continue }
            try {
                socket.connect()
                link = bluetoothLink(socket)
                break
            } catch (_: Exception) {
                try { socket.close() } catch (_: Exception) { }
                if (i < attempts.lastIndex) try { Thread.sleep(250) } catch (_: InterruptedException) { }
            }
        }
        done(link)
    }, "nearby-bt-connect").also { it.isDaemon = true; it.start() }
}

/** Looks for nearby Bluetooth tablets. The caller must already hold the scan permissions. */
@SuppressLint("MissingPermission")
class BluetoothScanner(
    private val context: Context,
    private val onFound: (BluetoothDevice) -> Unit,
    private val onFinished: () -> Unit,
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    @Suppress("DEPRECATION")
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) onFound(device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> onFinished()
            }
        }
    }

    fun start(): Boolean {
        val adapter = BluetoothSupport.adapter(context) ?: return false
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
        return try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (_: SecurityException) {
            false
        }
    }

    fun stop() {
        try { BluetoothSupport.adapter(context)?.cancelDiscovery() } catch (_: SecurityException) { }
        if (registered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
            registered = false
        }
    }
}
