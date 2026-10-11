package com.billlocket.app.feature.transaction.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billlocket.app.R
import com.billlocket.app.domain.model.Category

private val LocketYellow = Color(0xFFFFD600)
private val LocketDarkCard = Color(0xFF1C1C1E)
private val LocketBorder = Color(0x33FFD600)
private val LocketMuted = Color(0xFF9E9E9E)

@Composable
fun CategorySuggestionCard(
    suggestionState: CategorySuggestionState,
    categories: List<Category>,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggested = suggestionState as? CategorySuggestionState.Suggested
    val targetCategory = suggested?.let { state ->
        categories.firstOrNull { it.id == state.resolvedCategoryId }
    }

    AnimatedVisibility(
        visible = targetCategory != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        if (targetCategory != null) {
            val cardDescription = stringResource(R.string.ai_suggestion_cd, targetCategory.name)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = cardDescription },
                shape = RoundedCornerShape(16.dp),
                color = LocketDarkCard,
                border = BorderStroke(1.dp, LocketBorder),
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(LocketYellow.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = LocketYellow,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.ai_suggestion_title, targetCategory.name),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(
                                text = stringResource(R.string.ai_processed_on_device),
                                color = LocketMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = 40.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.ai_dismiss),
                                color = LocketMuted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }

                        Button(
                            onClick = onApply,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocketYellow,
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.heightIn(min = 40.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.ai_apply),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
