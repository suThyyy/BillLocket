package com.billlocket.app.feature.transaction.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billlocket.app.R

@Composable
fun VoicePermissionRationaleDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Mic, null, tint = Color(0xFFFFB800), modifier = Modifier.size(28.dp))
        },
        title = {
            Text(
                stringResource(R.string.voice_permission_dialog_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                stringResource(R.string.voice_permission_dialog_body),
                color = Color(0xFFCCCCCC),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(
                    stringResource(R.string.voice_permission_continue),
                    color = Color(0xFFFFB800),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.voice_cancel),
                    color = Color(0xFF8E8B85),
                )
            }
        },
        containerColor = Color(0xFF191815),
    )
}

@Composable
fun VoiceListeningCapsule(
    partialText: String,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Color(0xFF2A2415), RoundedCornerShape(19.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color(0xFFFFB800).copy(alpha = pulseAlpha),
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (partialText.isBlank()) {
                Text(
                    text = stringResource(R.string.voice_listening),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFB800),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = "“$partialText”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFE082),
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(
            onClick = onStop,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.voice_stop),
                tint = Color(0xFF81C784),
                modifier = Modifier.size(18.dp),
            )
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.voice_cancel),
                tint = Color(0xFFE57373),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun VoiceProcessingCapsule(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Color(0xFF2A2415), RoundedCornerShape(19.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = Color(0xFFFFB800),
        )
        Text(
            text = stringResource(R.string.voice_processing),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFFB800),
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.voice_cancel),
                tint = Color(0xFFE57373),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun VoiceErrorBanner(
    error: VoiceRecognitionError,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF261818),
        border = BorderStroke(1.dp, Color(0xFF5C2626)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = mapVoiceError(error),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFB4B4),
                modifier = Modifier.weight(1f),
            )
            if (error is VoiceRecognitionError.PermissionPermanentlyDenied) {
                TextButton(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        stringResource(R.string.voice_open_settings),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFB800),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.voice_cancel),
                    tint = Color(0xFFB0A8A0),
                    modifier = Modifier.size(14.dp),
                )
            }
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
