package com.example.p2p

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class WifiDirectTransport(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPeersUpdated: (List<DiscoveredPeer>) -> Unit,
    private val onStatusChanged: (LinkStatus, String?) -> Unit,
    private val onSocketConnected: (peerId: String, peerName: String, input: InputStream, output: OutputStream) -> Unit,
    private val onPacketReceived: suspend (Byte, ByteArray) -> Unit,
    private val onDisconnected: (String?) -> Unit
) {
    companion object {
        const val PORT = 8988
    }

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var outputStream: OutputStream? = null
    private val writeLock = Any()

    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private var readJob: Job? = null
    private var receiverRegistered = false
    private var isWifiP2pEnabled = false

    private var pendingPeerName: String = "Wi-Fi Peer"
    private var pendingPeerAddress: String = "p2p"

    private val peerListListener = WifiP2pManager.PeerListListener { peerList: WifiP2pDeviceList ->
        val mapped = peerList.deviceList.map { dev ->
            DiscoveredPeer(
                id = "wfd_${dev.deviceAddress}",
                name = dev.deviceName?.takeIf { it.isNotBlank() } ?: dev.deviceAddress,
                address = dev.deviceAddress,
                transport = TransportType.WIFI_DIRECT,
                isPaired = false,
                signalHint = "Wi-Fi P2P"
            )
        }
        onPeersUpdated(mapped)
    }

    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        if (info == null || !info.groupFormed) return@ConnectionInfoListener
        if (activeSocket != null) return@ConnectionInfoListener

        if (info.isGroupOwner) {
            startTcpServer()
        } else {
            val hostAddress = info.groupOwnerAddress?.hostAddress ?: return@ConnectionInfoListener
            connectTcpClient(hostAddress)
        }
    }

    private val p2pReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            val p2pManager = manager ?: return
            val p2pChannel = channel ?: return

            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED
                    )
                    isWifiP2pEnabled = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (hasWifiP2pPermission()) {
                        p2pManager.requestPeers(p2pChannel, peerListListener)
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    if (hasWifiP2pPermission()) {
                        p2pManager.requestConnectionInfo(p2pChannel, connectionInfoListener)
                    }
                }
            }
        }
    }

    fun isAvailable(): Boolean = manager != null

    fun hasWifiP2pPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun ensureInitialized(): Boolean {
        val p2pManager = manager ?: return false
        if (channel == null) {
            channel = p2pManager.initialize(context, context.mainLooper, null)
        }
        registerReceiverIfNeeded()
        return channel != null
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasWifiP2pPermission()) {
            onStatusChanged(LinkStatus.ERROR, "Nearby Wi-Fi permission required")
            return
        }
        if (!ensureInitialized()) {
            onStatusChanged(LinkStatus.ERROR, "Wi-Fi Direct unavailable on this device")
            return
        }

        val p2pManager = manager ?: return
        val p2pChannel = channel ?: return
        onStatusChanged(LinkStatus.SCANNING, null)

        p2pManager.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Peers delivered via WIFI_P2P_PEERS_CHANGED_ACTION
            }

            override fun onFailure(reason: Int) {
                onStatusChanged(LinkStatus.ERROR, "Wi-Fi Direct discovery failed (code $reason)")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (!hasWifiP2pPermission()) {
            onStatusChanged(LinkStatus.ERROR, "Nearby Wi-Fi permission required")
            return
        }
        if (!ensureInitialized()) {
            onStatusChanged(LinkStatus.ERROR, "Wi-Fi Direct unavailable")
            return
        }

        val p2pManager = manager ?: return
        val p2pChannel = channel ?: return
        onStatusChanged(LinkStatus.LISTENING, null)

        startTcpServer()
        p2pManager.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {}
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(peer: DiscoveredPeer) {
        if (!hasWifiP2pPermission()) {
            onStatusChanged(LinkStatus.ERROR, "Nearby Wi-Fi permission required")
            return
        }
        if (!ensureInitialized()) {
            onStatusChanged(LinkStatus.ERROR, "Wi-Fi Direct unavailable")
            return
        }

        val p2pManager = manager ?: return
        val p2pChannel = channel ?: return

        pendingPeerName = peer.name
        pendingPeerAddress = peer.address
        onStatusChanged(LinkStatus.CONNECTING, null)

        val config = WifiP2pConfig().apply {
            deviceAddress = peer.address
            wps.setup = WpsInfo.PBC
        }

        p2pManager.connect(p2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Socket connects once WIFI_P2P_CONNECTION_CHANGED_ACTION fires
            }

            override fun onFailure(reason: Int) {
                onStatusChanged(LinkStatus.ERROR, "Wi-Fi Direct connect failed (code $reason)")
            }
        })
    }

    private fun startTcpServer() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(PORT)
                serverSocket = server
                val client = server.accept()
                runCatching { server.close() }
                serverSocket = null
                handleConnectedSocket(client)
            } catch (e: IOException) {
                if (isActive && activeSocket == null) {
                    onStatusChanged(LinkStatus.ERROR, e.message ?: "TCP server error")
                }
            }
        }
    }

    private fun connectTcpClient(host: String) {
        clientJob?.cancel()
        clientJob = scope.launch(Dispatchers.IO) {
            var lastError: Exception? = null
            repeat(4) { attempt ->
                if (!isActive) return@launch
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, PORT), 4000)
                    handleConnectedSocket(socket)
                    return@launch
                } catch (e: Exception) {
                    lastError = e
                    delay(450L * (attempt + 1))
                }
            }
            onStatusChanged(LinkStatus.ERROR, lastError?.message ?: "Failed to reach Group Owner")
        }
    }

    private fun handleConnectedSocket(socket: Socket) {
        activeSocket = socket
        socket.tcpNoDelay = true
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        outputStream = output

        val addr = socket.inetAddress?.hostAddress ?: pendingPeerAddress
        onSocketConnected("wfd_$addr", pendingPeerName, input, output)

        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            PacketFramer.readLoop(
                inputStream = input,
                isActive = { isActive && activeSocket != null },
                onPacket = onPacketReceived,
                onDisconnected = { reason ->
                    disconnectInternal()
                    onDisconnected(reason)
                }
            )
        }
    }

    fun sendPacket(type: Byte, payload: ByteArray): Boolean {
        val out = outputStream ?: return false
        return try {
            PacketFramer.writePacket(out, writeLock, type, payload)
            true
        } catch (_: IOException) {
            false
        }
    }

    fun disconnect() {
        disconnectInternal()
        runCatching {
            val ch = channel
            if (ch != null) {
                manager?.removeGroup(ch, null)
            }
        }
        onStatusChanged(LinkStatus.IDLE, null)
    }

    private fun disconnectInternal() {
        serverJob?.cancel()
        serverJob = null
        clientJob?.cancel()
        clientJob = null
        readJob?.cancel()
        readJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { activeSocket?.close() }
        activeSocket = null
        outputStream = null
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            p2pReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    fun release() {
        disconnectInternal()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(p2pReceiver) }
            receiverRegistered = false
        }
    }
}
