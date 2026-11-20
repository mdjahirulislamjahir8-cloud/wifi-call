package com.example.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.p2p.DiscoveredPeer
import com.example.p2p.LinkStatus
import com.example.p2p.TransportType
import com.example.ui.UiStrings
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.PulseGreen
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SonarCyan

@Composable
fun RadarScreen(
    strings: UiStrings,
    selectedTransport: TransportType,
    linkStatus: LinkStatus,
    statusError: String?,
    peers: List<DiscoveredPeer>,
    connectedPeer: DiscoveredPeer?,
    hasDiscoveryPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onSelectTransport: (TransportType) -> Unit,
    onStartScan: () -> Unit,
    onStartListen: () -> Unit,
    onConnectPeer: (DiscoveredPeer) -> Unit,
    onDisconnect: () -> Unit,
    onOpenChat: (DiscoveredPeer) -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isScanningOrListening = linkStatus == LinkStatus.SCANNING || linkStatus == LinkStatus.LISTENING

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Radar Banner
            item {
                RadarHeroCard(
                    strings = strings,
                    selectedTransport = selectedTransport,
                    linkStatus = linkStatus,
                    isActive = isScanningOrListening
                )
            }

            // Permission Banner if needed
            if (!hasDiscoveryPermissions) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = strings.permissionsNeededTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = strings.permissionsNeededDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Button(
                                onClick = onRequestPermissions,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("grant_permissions_button")
                            ) {
                                Text(strings.grantPermissions)
                            }
                        }
                    }
                }
            }

            // Transport Mode Switcher (Bluetooth vs Wi-Fi Direct Signal)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TransportModeChip(
                        label = strings.bluetoothMode,
                        icon = Icons.Rounded.Bluetooth,
                        selected = selectedTransport == TransportType.BLUETOOTH,
                        enabled = connectedPeer == null,
                        onClick = { onSelectTransport(TransportType.BLUETOOTH) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("transport_bluetooth_chip")
                    )
                    TransportModeChip(
                        label = strings.wifiDirectMode,
                        icon = Icons.Rounded.WifiTethering,
                        selected = selectedTransport == TransportType.WIFI_DIRECT,
                        enabled = connectedPeer == null,
                        onClick = { onSelectTransport(TransportType.WIFI_DIRECT) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("transport_wifi_chip")
                    )
                }
            }

            // Connected Peer Banner
            if (connectedPeer != null) {
                item {
                    ConnectedPeerCard(
                        strings = strings,
                        peer = connectedPeer,
                        onOpenChat = { onOpenChat(connectedPeer) },
                        onAudioCall = onStartAudioCall,
                        onVideoCall = onStartVideoCall,
                        onDisconnect = onDisconnect
                    )
                }
            }

            // Primary Radio Controls
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!hasDiscoveryPermissions) onRequestPermissions()
                                else onStartScan()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("scan_peers_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Radar,
                                contentDescription = stringResource(R.string.cd_scan_peers),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (linkStatus == LinkStatus.SCANNING) strings.scanning else strings.startScan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                if (!hasDiscoveryPermissions) onRequestPermissions()
                                else onStartListen()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("listen_mode_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sensors,
                                contentDescription = stringResource(R.string.cd_listen_mode),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (linkStatus == LinkStatus.LISTENING) strings.listeningActive else strings.listenMode,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (selectedTransport == TransportType.BLUETOOTH) {
                        OutlinedButton(
                            onClick = {
                                val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                                }
                                runCatching { context.startActivity(discoverableIntent) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("make_discoverable_button")
                        ) {
                            Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.makeDiscoverable)
                        }
                    }

                    AnimatedVisibility(visible = !statusError.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${statusError.orEmpty()} — ${strings.bluetoothOffHint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Discovered / Paired Peers Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.discoveredPeersTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${peers.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (peers.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedTransport == TransportType.BLUETOOTH) {
                                    Icons.Rounded.BluetoothSearching
                                } else {
                                    Icons.Rounded.WifiTethering
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = strings.noPeersHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = strings.wifiDirectNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                items(peers, key = { it.id }) { peer ->
                    PeerDeviceCard(
                        strings = strings,
                        peer = peer,
                        isConnected = connectedPeer?.id == peer.id,
                        onConnect = { onConnectPeer(peer) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarHeroCard(
    strings: UiStrings,
    selectedTransport: TransportType,
    linkStatus: LinkStatus,
    isActive: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sonarPulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )

    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_radar_hero),
                contentDescription = stringResource(R.string.cd_radar_banner),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                DeepNavy.copy(alpha = 0.35f),
                                DeepNavy.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
            if (isActive) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxR = size.minDimension * 0.45f
                    drawCircle(
                        color = SonarCyan.copy(alpha = (1f - pulseRadius) * 0.65f),
                        radius = maxR * pulseRadius,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = DeepNavy.copy(alpha = 0.72f),
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (linkStatus) {
                                        LinkStatus.CONNECTED -> PulseGreen
                                        LinkStatus.SCANNING, LinkStatus.LISTENING, LinkStatus.CONNECTING -> SonarCyan
                                        LinkStatus.ERROR -> CoralRed
                                        LinkStatus.IDLE -> SignalAmber
                                    }
                                )
                        )
                        Text(
                            text = strings.offlineReadyBadge,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                Column {
                    Text(
                        text = if (selectedTransport == TransportType.BLUETOOTH) strings.bluetoothMode else strings.wifiDirectMode,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = when (linkStatus) {
                            LinkStatus.CONNECTED -> strings.connectedTo
                            LinkStatus.SCANNING -> strings.scanning
                            LinkStatus.LISTENING -> strings.listeningActive
                            LinkStatus.CONNECTING -> "..."
                            LinkStatus.ERROR -> strings.bluetoothOffHint
                            LinkStatus.IDLE -> strings.wifiDirectNote
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.84f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (selected) 3.dp else 1.dp,
        modifier = modifier
            .height(54.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConnectedPeerCard(
    strings: UiStrings,
    peer: DiscoveredPeer,
    onOpenChat: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = PulseGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "${strings.connectedTo} ${peer.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${peer.transport.name} • ${peer.address}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                }
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed),
                    modifier = Modifier.testTag("disconnect_button")
                ) {
                    Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.disconnect)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenChat,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("connected_open_chat_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.openChat, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onAudioCall,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("connected_audio_call_button")
                ) {
                    Icon(
                        Icons.Rounded.Call,
                        contentDescription = stringResource(R.string.cd_audio_call)
                    )
                }
                FilledTonalButton(
                    onClick = onVideoCall,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("connected_video_call_button")
                ) {
                    Icon(
                        Icons.Rounded.Videocam,
                        contentDescription = stringResource(R.string.cd_video_call)
                    )
                }
            }
        }
    }
}

@Composable
private fun PeerDeviceCard(
    strings: UiStrings,
    peer: DiscoveredPeer,
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("peer_card_${peer.address}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (peer.transport == TransportType.BLUETOOTH) {
                                Icons.Rounded.Bluetooth
                            } else {
                                Icons.Rounded.WifiTethering
                            },
                            contentDescription = if (peer.transport == TransportType.BLUETOOTH) {
                                stringResource(R.string.cd_bluetooth_icon)
                            } else {
                                stringResource(R.string.cd_wifi_direct_icon)
                            },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = peer.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (peer.isPaired) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = strings.pairedDevicesLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${peer.address} • ${peer.signalHint}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onConnect,
                enabled = !isConnected,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("connect_peer_${peer.address}")
            ) {
                Text(if (isConnected) "Linked" else strings.connect)
            }
        }
    }
}
