package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.p2p.CallPhase
import com.example.p2p.CallSessionState
import com.example.p2p.TransportType
import com.example.ui.UiStrings
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.PulseGreen
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SonarCyan
import java.util.concurrent.Executors

@Composable
fun CallScreen(
    strings: UiStrings,
    callSession: CallSessionState,
    transport: TransportType,
    remoteVideoBitmap: Bitmap?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleVideoMode: () -> Unit,
    onCameraFrame: (ImageProxy) -> Unit
) {
    BackHandler {
        if (callSession.phase == CallPhase.INCOMING_RINGING) {
            onDecline()
        } else {
            onHangUp()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepNavy, SlateSurface, DeepNavy)
                )
            )
    ) {
        // Remote Video Surface (when in Video Call)
        if (callSession.phase == CallPhase.IN_CALL && callSession.isVideo && remoteVideoBitmap != null) {
            Image(
                bitmap = remoteVideoBitmap.asImageBitmap(),
                contentDescription = callSession.peerName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Audio / Ringing Animated Sonar Centerpiece
            AudioSonarCenterpiece(
                peerName = callSession.peerName,
                subtitle = when (callSession.phase) {
                    CallPhase.INCOMING_RINGING -> "${strings.incomingCallTitle} • ${if (callSession.isVideo) strings.videoCall else strings.audioCall}"
                    CallPhase.OUTGOING_RINGING -> strings.outgoingRinging
                    CallPhase.IN_CALL -> formatCallTimer(callSession.durationSeconds)
                    CallPhase.IDLE -> ""
                },
                transport = transport,
                isAnimating = callSession.phase != CallPhase.IDLE
            )
        }

        // Top Status Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = DeepNavy.copy(alpha = 0.72f),
                shape = CircleShape
            ) {
                Text(
                    text = "${transport.name} • ${if (callSession.isVideo) strings.videoCall else strings.audioCall}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SonarCyan,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }

            if (callSession.phase == CallPhase.IN_CALL && callSession.isVideo) {
                Surface(
                    color = DeepNavy.copy(alpha = 0.72f),
                    shape = CircleShape
                ) {
                    Text(
                        text = formatCallTimer(callSession.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Local CameraX PiP Preview during active Video Call
        if (callSession.phase == CallPhase.IN_CALL && callSession.isVideo && callSession.isCameraEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 60.dp, end = 16.dp)
                    .size(width = 112.dp, height = 156.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black)
            ) {
                LocalCameraPreviewBinder(
                    isFrontCamera = callSession.isFrontCamera,
                    onFrame = onCameraFrame
                )
            }
        }

        // Bottom Controls Dock
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            if (callSession.phase == CallPhase.INCOMING_RINGING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = onDecline,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = CoralRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("decline_call_button")
                        ) {
                            Icon(
                                Icons.Rounded.CallEnd,
                                contentDescription = strings.declineCall,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(strings.declineCall, style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = onAccept,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = PulseGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("accept_call_button")
                        ) {
                            Icon(
                                if (callSession.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                contentDescription = strings.acceptCall,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(strings.acceptCall, style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            } else {
                Surface(
                    color = DeepNavy.copy(alpha = 0.84f),
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CallControlButton(
                            icon = if (callSession.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            label = strings.muteMic,
                            active = callSession.isMuted,
                            contentDesc = stringResource(R.string.cd_toggle_mute),
                            onClick = onToggleMute,
                            testTag = "mute_button"
                        )

                        CallControlButton(
                            icon = if (callSession.isSpeakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                            label = strings.speakerOn,
                            active = callSession.isSpeakerOn,
                            contentDesc = stringResource(R.string.cd_toggle_speaker),
                            onClick = onToggleSpeaker,
                            testTag = "speaker_button"
                        )

                        CallControlButton(
                            icon = if (callSession.isVideo && callSession.isCameraEnabled) {
                                Icons.Rounded.Videocam
                            } else {
                                Icons.Rounded.VideocamOff
                            },
                            label = strings.cameraOn,
                            active = callSession.isVideo && callSession.isCameraEnabled,
                            contentDesc = stringResource(R.string.cd_toggle_camera),
                            onClick = {
                                if (!callSession.isVideo) onToggleVideoMode() else onToggleCamera()
                            },
                            testTag = "toggle_camera_button"
                        )

                        if (callSession.isVideo) {
                            CallControlButton(
                                icon = Icons.Rounded.Cameraswitch,
                                label = strings.flipCamera,
                                active = false,
                                contentDesc = stringResource(R.string.cd_flip_camera),
                                onClick = onFlipCamera,
                                testTag = "flip_camera_button"
                            )
                        }

                        FilledIconButton(
                            onClick = onHangUp,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = CoralRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(60.dp)
                                .testTag("hangup_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CallEnd,
                                contentDescription = stringResource(R.string.cd_hang_up),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    contentDesc: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) SonarCyan else Color.White.copy(alpha = 0.14f),
                contentColor = if (active) DeepNavy else Color.White
            ),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(48.dp)
                .testTag(testTag)
        ) {
            Icon(imageVector = icon, contentDescription = contentDesc)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun AudioSonarCenterpiece(
    peerName: String,
    subtitle: String,
    transport: TransportType,
    isAnimating: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "callPulse")
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isAnimating) {
            Canvas(modifier = Modifier.size(280.dp)) {
                val maxR = size.minDimension / 2f
                drawCircle(
                    color = SonarCyan.copy(alpha = (1f - ring1) * 0.55f),
                    radius = maxR * ring1,
                    style = Stroke(width = 3.dp.toPx())
                )
                val ring2 = (ring1 + 0.5f) % 1f
                drawCircle(
                    color = SonarCyan.copy(alpha = (1f - ring2) * 0.4f),
                    radius = maxR * ring2,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = SonarCyan.copy(alpha = 0.18f),
                modifier = Modifier.size(104.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = SonarCyan,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }
            Text(
                text = peerName.ifBlank { "WaveLink Peer" },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = SonarCyan
            )
            Text(
                text = transport.name,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun LocalCameraPreviewBinder(
    isFrontCamera: Boolean,
    onFrame: (ImageProxy) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(isFrontCamera) {
        val future = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null
        val listener = Runnable {
            runCatching {
                val provider = future.get()
                boundProvider = provider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor) { image ->
                            onFrame(image)
                        }
                    }
                val selector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { boundProvider?.unbindAll() }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }
}

private fun formatCallTimer(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}
