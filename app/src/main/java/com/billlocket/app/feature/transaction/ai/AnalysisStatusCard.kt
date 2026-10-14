package com.billlocket.app.feature.transaction.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.billlocket.app.R
import com.billlocket.app.domain.model.ThemeMode
import com.billlocket.app.ui.theme.AppTheme

sealed interface AnalysisStatusUiState {
    data object Analyzing : AnalysisStatusUiState
    data class Suggested(
        val headline: String,
        val evidence: String? = null,
    ) : AnalysisStatusUiState
    data object NoSuggestion : AnalysisStatusUiState
    data object Failed : AnalysisStatusUiState
}

@Composable
fun AnalysisStatusCard(
    state: AnalysisStatusUiState,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val yellow = Color(0xFFFFD600)
    val isSuggested = state is AnalysisStatusUiState.Suggested
    val title = when (state) {
        AnalysisStatusUiState.Analyzing -> stringResource(R.string.ai_analyzing)
        is AnalysisStatusUiState.Suggested -> state.headline
        AnalysisStatusUiState.NoSuggestion -> stringResource(R.string.ai_no_suggestion)
        AnalysisStatusUiState.Failed -> stringResource(R.string.ai_analysis_failed)
    }
    val description = stringResource(R.string.ai_analysis_status_cd, title)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (state is AnalysisStatusUiState.Failed) {
                MaterialTheme.colorScheme.outlineVariant
            } else yellow.copy(alpha = 0.25f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                AnalysisStatusUiState.Analyzing -> CircularProgressIndicator(
                    modifier = Modifier.width(20.dp),
                    strokeWidth = 2.dp,
                    color = yellow,
                )
                is AnalysisStatusUiState.Suggested -> Icon(Icons.Filled.AutoAwesome, null, tint = yellow)
                AnalysisStatusUiState.NoSuggestion -> Icon(
                    Icons.Filled.Info,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnalysisStatusUiState.Failed -> Icon(
                    Icons.Filled.Info,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val supporting = when (state) {
                    is AnalysisStatusUiState.Suggested -> state.evidence
                        ?: stringResource(R.string.ai_processed_on_device)
                    else -> stringResource(R.string.ai_processed_on_device)
                }
                Text(
                    text = supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isSuggested) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.ai_dismiss))
                }
                Button(
                    onClick = onApply,
                    colors = ButtonDefaults.buttonColors(containerColor = yellow, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(stringResource(R.string.ai_apply), fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.ai_dismiss))
                }
            }
        }
    }
}

@Preview(name = "Analysis suggested dark", showBackground = true, backgroundColor = 0xFF080807)
@Composable
private fun AnalysisStatusSuggestedDarkPreview() {
    AppTheme(mode = ThemeMode.DARK) {
        AnalysisStatusCard(
            state = AnalysisStatusUiState.Suggested("AI gợi ý: Ăn uống", "Nhận diện “cà phê”"),
            onApply = {},
            onDismiss = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Analysis states light", showBackground = true)
@Composable
private fun AnalysisStatusLightPreview() {
    AppTheme(mode = ThemeMode.LIGHT) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnalysisStatusCard(AnalysisStatusUiState.Analyzing, {}, {})
            AnalysisStatusCard(AnalysisStatusUiState.NoSuggestion, {}, {})
            AnalysisStatusCard(AnalysisStatusUiState.Failed, {}, {})
        }
    }
}
