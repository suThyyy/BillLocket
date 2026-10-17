package com.billlocket.app.feature.transaction.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.billlocket.app.R
import com.billlocket.app.domain.model.ThemeMode
import com.billlocket.app.ui.theme.AppTheme
import kotlinx.coroutines.delay

@Composable
fun VoicePermissionRationaleDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF1B1A18),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    VoiceStatusOrb(
                        icon = { Icon(Icons.Filled.Mic, null, Modifier.size(26.dp)) },
                        color = VoiceYellow,
                    )
                    Text(
                        stringResource(R.string.voice_permission_dialog_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.voice_permission_dialog_body),
                        color = VoiceMutedText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VoiceYellow,
                            contentColor = Color(0xFF17120A),
                        ),
                    ) {
                        Text(
                            stringResource(R.string.voice_permission_continue),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.voice_cancel),
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                }
            }
        }
    }
}

private val VoiceYellow = Color(0xFFFFC21C)
private val VoicePanel = Color(0xFF211819)
private val VoicePanelBorder = Color(0xFF7A233A)
private val VoiceMutedText = Color(0xFFBDB5B1)
private val VoiceSuccess = Color(0xFF4DDEA1)
private val VoiceError = Color(0xFFFF6178)

@Composable
private fun VoicePanelSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = VoicePanel,
        border = BorderStroke(1.dp, VoicePanelBorder.copy(alpha = 0.9f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun VoiceStatusOrb(
    icon: @Composable () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "voiceOrbPulse")
    val glowAlpha by transition.animateFloat(
        initialValue = if (pulsing) 0.18f else 0.14f,
        targetValue = if (pulsing) 0.46f else 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(780, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voiceOrbGlow",
    )
    Box(modifier = modifier.size(68.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = color.copy(alpha = glowAlpha),
        ) {}
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = color,
            contentColor = Color(0xFF17120A),
            border = BorderStroke(3.dp, color.copy(alpha = 0.35f)),
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
    }
}

@Composable
private fun VoiceTranscriptBox(
    text: String,
    placeholder: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF3A1D25).copy(alpha = 0.72f),
        border = BorderStroke(1.dp, VoicePanelBorder.copy(alpha = 0.7f)),
    ) {
        Text(
            text = text.ifBlank { placeholder.orEmpty() },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (text.isBlank()) VoiceMutedText else Color.White,
            textAlign = TextAlign.Center,
            fontStyle = if (text.isBlank()) FontStyle.Italic else FontStyle.Normal,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VoiceWaveform(
    inputLevel: Float,
    modifier: Modifier = Modifier,
) {
    val normalized = inputLevel.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    val weights = listOf(.20f, .34f, .48f, .68f, .84f, 1f, .76f, .58f, .92f, .72f, .50f, .36f, .24f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        weights.forEachIndexed { index, weight ->
            val base = if (normalized == 0f) 0.12f else normalized
            val activity = (base * weight + base * .10f * (index % 3)).coerceIn(0f, 1f)
            val height by animateDpAsState(
                targetValue = (5f + 27f * activity).dp,
                animationSpec = tween(110, easing = FastOutSlowInEasing),
                label = "voiceWaveBar$index",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height(height)
                    .background(VoiceYellow, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
fun VoiceListeningPanel(
    partialText: String,
    phase: VoiceListeningPhase,
    inputLevel: Float,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            elapsedSeconds++
        }
    }
    val statusText = when (phase) {
        VoiceListeningPhase.STARTING -> stringResource(R.string.voice_listening_starting)
        VoiceListeningPhase.READY -> stringResource(R.string.voice_listening)
        VoiceListeningPhase.SPEECH_DETECTED -> stringResource(R.string.voice_speech_detected)
    }

    VoicePanelSurface(modifier) {
        VoiceStatusOrb(
            icon = { Icon(Icons.Filled.Mic, null, Modifier.size(25.dp)) },
            color = VoiceYellow,
            pulsing = true,
        )
        Text(
            statusText,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.voice_note_only_hint),
            color = VoiceMutedText,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        if (partialText.isNotBlank()) {
            VoiceTranscriptBox(text = "“$partialText”")
        }
        VoiceWaveform(inputLevel = inputLevel)
        Text(
            "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
            color = VoiceMutedText,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text(stringResource(R.string.voice_cancel))
            }
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoiceYellow,
                    contentColor = Color(0xFF17120A),
                ),
            ) {
                Icon(Icons.Filled.Stop, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.voice_stop), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VoiceProcessingPanel(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VoicePanelSurface(modifier) {
        VoiceStatusOrb(
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(25.dp),
                    strokeWidth = 3.dp,
                    color = Color(0xFF17120A),
                )
            },
            color = VoiceYellow,
            pulsing = true,
        )
        Text(
            stringResource(R.string.voice_processing),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.voice_processing_hint),
            color = VoiceMutedText,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.voice_cancel), color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
fun VoiceResultPanel(
    recognizedText: String,
    onUseResult: () -> Unit,
    onRecordAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VoicePanelSurface(modifier) {
        VoiceStatusOrb(
            icon = { Icon(Icons.Filled.Check, null, Modifier.size(28.dp)) },
            color = VoiceSuccess,
        )
        Text(
            stringResource(R.string.voice_recognition_complete),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        VoiceTranscriptBox(text = "“$recognizedText”")
        Button(
            onClick = onUseResult,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VoiceYellow,
                contentColor = Color(0xFF17120A),
            ),
        ) {
            Text(stringResource(R.string.voice_use_result), fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onRecordAgain, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(17.dp), tint = Color.White.copy(alpha = 0.78f))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.voice_record_again), color = Color.White.copy(alpha = 0.78f))
        }
    }
}

@Composable
fun VoiceErrorPanel(
    error: VoiceRecognitionError,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissionBlocked = error is VoiceRecognitionError.PermissionPermanentlyDenied
    VoicePanelSurface(modifier) {
        VoiceStatusOrb(
            icon = { Icon(Icons.Filled.Warning, null, Modifier.size(27.dp)) },
            color = VoiceError,
        )
        Text(
            stringResource(R.string.voice_recognition_failed),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            mapVoiceError(error),
            color = VoiceMutedText,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = if (permissionBlocked) onOpenSettings else onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VoiceYellow,
                contentColor = Color(0xFF17120A),
            ),
        ) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (permissionBlocked) stringResource(R.string.voice_open_settings)
                else stringResource(R.string.voice_retry),
                fontWeight = FontWeight.Bold,
            )
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.voice_cancel), color = Color.White.copy(alpha = 0.72f))
        }
    }
}

@Composable
fun mapVoiceError(error: VoiceRecognitionError): String = when (error) {
    VoiceRecognitionError.PermissionDenied -> stringResource(R.string.voice_permission_denied)
    VoiceRecognitionError.PermissionPermanentlyDenied -> stringResource(R.string.voice_permission_permanently_denied)
    VoiceRecognitionError.NoSpeechMatch -> stringResource(R.string.voice_error_no_match)
    VoiceRecognitionError.RecognizerBusy -> stringResource(R.string.voice_error_busy)
    VoiceRecognitionError.NetworkError -> stringResource(R.string.voice_error_network)
    VoiceRecognitionError.AudioError -> stringResource(R.string.voice_error_audio)
    VoiceRecognitionError.ServiceUnavailable -> stringResource(R.string.voice_error_service)
    VoiceRecognitionError.ClientError,
    VoiceRecognitionError.Unknown -> stringResource(R.string.voice_error_generic)
}

@Preview(name = "Voice listening panel", widthDp = 360, showBackground = true, backgroundColor = 0xFF080807)
@Composable
private fun VoiceListeningPanelPreview() {
    AppTheme(mode = ThemeMode.DARK) {
        VoiceListeningPanel(
            partialText = "ăn trưa cùng đồng nghiệp",
            phase = VoiceListeningPhase.SPEECH_DETECTED,
            inputLevel = 0.72f,
            onStop = {},
            onCancel = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Preview(name = "Voice result panel", widthDp = 360, showBackground = true, backgroundColor = 0xFF080807)
@Composable
private fun VoiceResultPanelPreview() {
    AppTheme(mode = ThemeMode.DARK) {
        VoiceResultPanel(
            recognizedText = "Ăn trưa cùng đồng nghiệp",
            onUseResult = {},
            onRecordAgain = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}
