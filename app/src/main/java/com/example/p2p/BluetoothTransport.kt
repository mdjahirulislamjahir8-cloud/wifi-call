package com.example.p2p

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothTransport(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPeersUpdated: (List<DiscoveredPeer>) -> Unit,
    private val onStatusChanged: (LinkStatus, String?) -> Unit,
    private val onSocketConnected: (peerId: String, peerName: String, input: InputStream, output: OutputStream) -> Unit,
    private val onPacketReceived: suspend (Byte, ByteArray) -> Unit,
    private val onDisconnected: (String?) -> Unit
) {
    companion object {
        private const val SERVICE_NAME = "WaveLinkP2P"
        val SERVICE_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val discoveredMap = linkedMapOf<String, DiscoveredPeer>()
    private val deviceMap = linkedMapOf<String, BluetoothDevice>()

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val writeLock = Any()

    private var acceptJob: Job? = null
    private var connectJob: Job? = null
    private var readJob: Job? = null
    private var receiverRegistered = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    if (!hasConnectPermission()) return
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null && !device.address.isNullOrBlank()) {
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() }
                            ?: "Bluetooth Device"
                        val isBonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }
                            .getOrDefault(false)
                        deviceMap[device.address] = device
                        discoveredMap[device.address] = DiscoveredPeer(
                            id = "bt_${device.address}",
                            name = name,
                            address = device.address,
                            transport = TransportType.BLUETOOTH,
                            isPaired = isBonded,
                            signalHint = if (rssi != Short.MIN_VALUE) "${rssi} dBm" else "BT Classic"
                        )
                        onPeersUpdated(discoveredMap.values.toList())
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (activeSocket == null && acceptJob == null) {
                        onStatusChanged(LinkStatus.IDLE, null)
                    }
                }
            }
        }
    }

    fun isBluetoothAvailable(): Boolean = adapter != null

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun loadBondedPeers() {
        val btAdapter = adapter ?: return
        if (!hasConnectPermission()) return
        runCatching {
            btAdapter.bondedDevices?.forEach { device ->
                deviceMap[device.address] = device
                val name = device.name?.takeIf { it.isNotBlank() } ?: device.address
                discoveredMap[device.address] = DiscoveredPeer(
                    id = "bt_${device.address}",
                    name = name,
                    address = device.address,
                    transport = TransportType.BLUETOOTH,
                    isPaired = true,
                    signalHint = "Paired"
                )
            }
            onPeersUpdated(discoveredMap.values.toList())
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val btAdapter = adapter
        if (btAdapter == null || !btAdapter.isEnabled) {
            onStatusChanged(LinkStatus.ERROR, "Bluetooth is off or unavailable")
            return
        }
        if (!hasScanPermission() || !hasConnectPermission()) {
            onStatusChanged(LinkStatus.ERROR, "Bluetooth permissions required")
            return
        }

        registerReceiverIfNeeded()
        loadBondedPeers()
        onStatusChanged(LinkStatus.SCANNING, null)

        runCatching {
            if (btAdapter.isDiscovering) {
                btAdapter.cancelDiscovery()
            }
            btAdapter.startDiscovery()
        }.onFailure {
            onStatusChanged(LinkStatus.ERROR, it.message)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!hasScanPermission()) return
        runCatching {
            if (adapter?.isDiscovering == true) {
                adapter.cancelDiscovery()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        val btAdapter = adapter
        if (btAdapter == null || !btAdapter.isEnabled) {
            onStatusChanged(LinkStatus.ERROR, "Bluetooth is off or unavailable")
            return
        }
        if (!hasConnectPermission()) {
            onStatusChanged(LinkStatus.ERROR, "Bluetooth connect permission required")
            return
        }

        stopListening()
        onStatusChanged(LinkStatus.LISTENING, null)

        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val server = btAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                serverSocket = server
                while (isActive) {
                    val socket = server.accept()
                    if (socket != null) {
                        runCatching { server.close() }
                        serverSocket = null
                        handleConnectedSocket(socket)
                        break
                    }
                }
            } catch (e: IOException) {
                if (isActive && activeSocket == null) {
                    onStatusChanged(LinkStatus.ERROR, e.message ?: "Bluetooth listen failed")
                }
            } catch (e: SecurityException) {
                onStatusChanged(LinkStatus.ERROR, e.message ?: "Bluetooth permission denied")
            }
        }
    }

    fun stopListening() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(peer: DiscoveredPeer) {
        val btAdapter = adapter
        if (btAdapter == null || !btAdapter.isEnabled) {
            onStatusChanged(LinkStatus.ERROR, "Bluetooth is off")
            return
        }
        if (!hasConnectPermission()) {
            onStatusChanged(LinkStatus.ERROR, "Bluetooth connect permission missing")
            return
        }
        val device = deviceMap[peer.address] ?: runCatching {
            btAdapter.getRemoteDevice(peer.address)
        }.getOrNull()

        if (device == null) {
            onStatusChanged(LinkStatus.ERROR, "Device not reachable")
            return
        }

        stopScan()
        stopListening()
        connectJob?.cancel()
        onStatusChanged(LinkStatus.CONNECTING, null)

        connectJob = scope.launch(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()
                handleConnectedSocket(socket)
            } catch (_: Exception) {
                runCatching { socket?.close() }
                // Fallback RFCOMM channel 1 reflection for OEM SDP quirks
                try {
                    val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    val fallback = method.invoke(device, 1) as BluetoothSocket
                    fallback.connect()
                    handleConnectedSocket(fallback)
                } catch (fallbackErr: Exception) {
                    onStatusChanged(
                        LinkStatus.ERROR,
                        fallbackErr.message ?: "Bluetooth connection failed"
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleConnectedSocket(socket: BluetoothSocket) {
        activeSocket = socket
        val input = socket.inputStream
        val output = socket.outputStream
        outputStream = output

        val remote = socket.remoteDevice
        val peerAddress = remote?.address ?: "unknown"
        val peerName = if (hasConnectPermission()) {
            runCatching { remote?.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: peerAddress
        } else {
            peerAddress
        }

        onSocketConnected("bt_$peerAddress", peerName, input, output)

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
        onStatusChanged(LinkStatus.IDLE, null)
    }

    private fun disconnectInternal() {
        connectJob?.cancel()
        connectJob = null
        readJob?.cancel()
        readJob = null
        stopListening()
        runCatching { activeSocket?.close() }
        activeSocket = null
        outputStream = null
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(
            context,
            discoveryReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    fun release() {
        stopScan()
        disconnectInternal()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(discoveryReceiver) }
            receiverRegistered = false
        }
    }
}
