package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.p2p.CallPhase
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.screens.CallScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.GuideAndSettingsScreen
import com.example.ui.screens.RadarScreen

object Routes {
    const val RADAR = "radar"
    const val CHATS = "chats"
    const val CALLS = "calls"
    const val GUIDE = "guide"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveLinkApp(viewModel: WaveLinkViewModel = viewModel()) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.RADAR

    val selectedTransport by viewModel.selectedTransport.collectAsStateWithLifecycle()
    val linkStatus by viewModel.linkStatus.collectAsStateWithLifecycle()
    val statusError by viewModel.statusError.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val connectedPeer by viewModel.connectedPeer.collectAsStateWithLifecycle()
    val callSession by viewModel.callSession.collectAsStateWithLifecycle()
    val remoteVideoBitmap by viewModel.remoteVideoBitmap.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val recentThreads by viewModel.recentThreads.collectAsStateWithLifecycle()
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()
    val activeChatPeerId by viewModel.activeChatPeerId.collectAsStateWithLifecycle()
    val activeChatPeerName by viewModel.activeChatPeerName.collectAsStateWithLifecycle()
    val activeChatMessages by viewModel.activeChatMessages.collectAsStateWithLifecycle()

    val strings = remember(userSettings.language) { stringsFor(userSettings.language) }

    var permissionVersion by remember { mutableIntStateOf(0) }
    val hasDiscoveryPermissions = remember(permissionVersion) {
        PermissionHelper.hasDiscoveryPermissions(context)
    }

    var pendingCallAction by remember { mutableStateOf<Boolean?>(null) }

    val discoveryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionVersion++
        viewModel.refreshPairedBluetoothPeers()
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionVersion++
        val wantsVideo = pendingCallAction
        pendingCallAction = null
        if (wantsVideo != null && PermissionHelper.hasCallPermissions(context, wantsVideo)) {
            if (wantsVideo) viewModel.startVideoCall() else viewModel.startAudioCall()
        }
    }

    LaunchedEffect(hasDiscoveryPermissions) {
        if (hasDiscoveryPermissions) {
            viewModel.refreshPairedBluetoothPeers()
        }
    }

    fun launchCallWithPermissionCheck(isVideo: Boolean) {
        if (PermissionHelper.hasCallPermissions(context, isVideo)) {
            if (isVideo) viewModel.startVideoCall() else viewModel.startAudioCall()
        } else {
            pendingCallAction = isVideo
            mediaPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
        }
    }

    // Full-screen Call Overlay when ringing or in call
    if (callSession.phase != CallPhase.IDLE) {
        CallScreen(
            strings = strings,
            callSession = callSession,
            transport = connectedPeer?.transport ?: selectedTransport,
            remoteVideoBitmap = remoteVideoBitmap,
            onAccept = {
                if (PermissionHelper.hasCallPermissions(context, callSession.isVideo)) {
                    viewModel.acceptIncomingCall()
                } else {
                    mediaPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
                }
            },
            onDecline = viewModel::declineIncomingCall,
            onHangUp = viewModel::endCall,
            onToggleMute = viewModel::toggleMute,
            onToggleSpeaker = viewModel::toggleSpeaker,
            onToggleCamera = viewModel::toggleCamera,
            onFlipCamera = viewModel::flipCamera,
            onToggleVideoMode = viewModel::toggleVideoUpgrade,
            onCameraFrame = viewModel::sendCameraFrame
        )
        return
    }

    val navItems = listOf(
        Triple(Routes.RADAR, strings.radarTab, Icons.Rounded.Radar),
        Triple(Routes.CHATS, strings.chatsTab, Icons.AutoMirrored.Rounded.Chat),
        Triple(Routes.CALLS, strings.callsTab, Icons.Rounded.History),
        Triple(Routes.GUIDE, strings.guideTab, Icons.Rounded.Settings)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = viewModel::toggleLanguage,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("language_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = strings.languageLabel
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        navItems.forEach { (route, label, icon) ->
                            NavigationBarItem(
                                selected = currentRoute == route,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(Routes.RADAR) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_$route")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalArrangement = Arrangement.Start
            ) {
                if (useRail) {
                    NavigationRail {
                        navItems.forEach { (route, label, icon) ->
                            NavigationRailItem(
                                selected = currentRoute == route,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(Routes.RADAR) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.RADAR,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.RADAR) {
                        RadarScreen(
                            strings = strings,
                            selectedTransport = selectedTransport,
                            linkStatus = linkStatus,
                            statusError = statusError,
                            peers = peers,
                            connectedPeer = connectedPeer,
                            hasDiscoveryPermissions = hasDiscoveryPermissions,
                            onRequestPermissions = {
                                discoveryPermissionLauncher.launch(PermissionHelper.allRequiredPermissions())
                            },
                            onSelectTransport = viewModel::selectTransport,
                            onStartScan = viewModel::startScan,
                            onStartListen = viewModel::startListening,
                            onConnectPeer = viewModel::connectToPeer,
                            onDisconnect = viewModel::disconnect,
                            onOpenChat = { peer ->
                                viewModel.openChatThread(peer.id, peer.name)
                                navController.navigate(Routes.CHATS) {
                                    popUpTo(Routes.RADAR) { saveState = true }
                                    launchSingleTop = true
                                }
                            },
                            onStartAudioCall = { launchCallWithPermissionCheck(isVideo = false) },
                            onStartVideoCall = { launchCallWithPermissionCheck(isVideo = true) }
                        )
                    }

                    composable(Routes.CHATS) {
                        ChatScreen(
                            strings = strings,
                            connectedPeer = connectedPeer,
                            activeChatPeerId = activeChatPeerId,
                            activeChatPeerName = activeChatPeerName,
                            messages = activeChatMessages,
                            recentThreads = recentThreads,
                            onSelectThread = { id, name -> viewModel.openChatThread(id, name) },
                            onClearActiveThread = { viewModel.openChatThread("", "") },
                            onSendMessage = viewModel::sendMessage,
                            onStartAudioCall = { launchCallWithPermissionCheck(isVideo = false) },
                            onStartVideoCall = { launchCallWithPermissionCheck(isVideo = true) }
                        )
                    }

                    composable(Routes.CALLS) {
                        CallHistoryScreen(
                            strings = strings,
                            callLogs = callLogs
                        )
                    }

                    composable(Routes.GUIDE) {
                        GuideAndSettingsScreen(
                            strings = strings,
                            settings = userSettings,
                            onUpdateAlias = viewModel::updateDeviceAlias,
                            onUpdateQuality = viewModel::updateVideoQuality,
                            onUpdateLanguage = viewModel::updateLanguage
                        )
                    }
                }
            }
        }
    }
}
