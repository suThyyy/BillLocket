package com.billlocket.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class LatestTransactionWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadLatestTransactionData(context)
        provideContent {
            LatestTransactionContent(context, data)
        }
    }
}

@Composable
fun LatestTransactionContent(context: Context, data: LatestTransactionWidgetData) {
    val size = LocalSize.current
    val isCompact = size.width < 170.dp || size.height < 85.dp
    val captureIntent = widgetIntent(context, WIDGET_ACTION_CAPTURE, null)
    val detailIntent = if (data.transactionId != null) {
        widgetIntent(context, WIDGET_ACTION_DETAIL, data.transactionId)
    } else {
        captureIntent
    }

    val photoSize = if (isCompact) 50.dp else 58.dp
    val cornerRadius = if (isCompact) 11.dp else 12.dp

    // Outer subtle warm border (1.5dp)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(18.dp)
            .background(ColorProvider(Color(0xFF3E3122)))
            .padding(1.5.dp),
    ) {
        // Main dark surface container
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.5.dp)
                .background(ColorProvider(Color(0xFF14110E)))
                .padding(horizontal = if (isCompact) 6.dp else 7.dp, vertical = if (isCompact) 4.5.dp else 6.dp)
                .clickable(actionStartActivity(detailIntent)),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                // Left: Hero transaction photo card (compact square, not ballooning)
                Box(
                    modifier = GlanceModifier
                        .size(photoSize)
                        .cornerRadius(cornerRadius)
                        .background(ColorProvider(Color(0xFF221C16))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (data.image != null) {
                        Image(
                            provider = ImageProvider(data.image),
                            contentDescription = "Ảnh giao dịch gần nhất",
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.fillMaxSize().cornerRadius(cornerRadius),
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                            verticalAlignment = Alignment.Vertical.CenterVertically,
                            modifier = GlanceModifier.fillMaxSize().padding(2.dp),
                        ) {
                            Text(
                                text = "📸",
                                style = TextStyle(fontSize = if (isCompact) 14.sp else 16.sp),
                            )
                            Spacer(GlanceModifier.height(1.dp))
                            Text(
                                text = if (data.hasContent) "Chưa có ảnh" else "Chưa có giao dịch",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF9E9487)),
                                    fontSize = 7.5.sp,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }

                Spacer(GlanceModifier.width(if (isCompact) 6.dp else 7.dp))

                // Right: Financial recap and actions
                Column(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    // Header row: Brand badge + quick action button
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.Vertical.CenterVertically,
                            modifier = GlanceModifier.defaultWeight(),
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .size(if (isCompact) 14.dp else 16.dp)
                                    .cornerRadius(4.dp)
                                    .background(ColorProvider(Color(0xFF2C2217))),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "👛",
                                    style = TextStyle(fontSize = if (isCompact) 8.sp else 9.sp),
                                )
                            }
                            Spacer(GlanceModifier.width(3.5.dp))
                            Column {
                                Text(
                                    text = "BillLocket",
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFFF5EFE6)),
                                        fontSize = if (isCompact) 10.5.sp else 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    maxLines = 1,
                                )
                                if (!isCompact) {
                                    Text(
                                        text = "Giao dịch gần nhất",
                                        style = TextStyle(
                                            color = ColorProvider(Color(0xFF9E9487)),
                                            fontSize = 7.5.sp,
                                        ),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }

                        // Circular amber plus button
                        Box(
                            modifier = GlanceModifier
                                .size(if (isCompact) 20.dp else 22.dp)
                                .cornerRadius(if (isCompact) 10.dp else 11.dp)
                                .background(ColorProvider(Color(0xFFC68A36)))
                                .clickable(actionStartActivity(captureIntent)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "＋",
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = if (isCompact) 11.sp else 12.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }

                    Spacer(GlanceModifier.height(if (isCompact) 2.dp else 3.dp))

                    // Category row
                    if (data.hasContent && data.categoryName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                            Text(
                                text = "🏷️",
                                style = TextStyle(fontSize = if (isCompact) 8.5.sp else 9.sp),
                            )
                            Spacer(GlanceModifier.width(2.dp))
                            Text(
                                text = data.categoryName,
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFE8DFD3)),
                                    fontSize = if (isCompact) 10.5.sp else 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                        }
                    } else if (!data.hasContent) {
                        Text(
                            text = "Chưa có giao dịch",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFE8DFD3)),
                                fontSize = if (isCompact) 9.5.sp else 10.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                        )
                    }

                    Spacer(GlanceModifier.height(1.5.dp))

                    // Amount row
                    if (data.hasContent) {
                        val sign = if (data.isExpense) "– " else "+ "
                        Text(
                            text = sign + formatWidgetAmount(data.amountMinor),
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFFFB800)),
                                fontSize = if (isCompact) 13.5.sp else 14.5.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            text = "Thêm giao dịch mới",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFFFB800)),
                                fontSize = if (isCompact) 10.sp else 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                        )
                    }

                    // Timestamp row
                    if (data.hasContent && data.occurredAt > 0) {
                        Spacer(GlanceModifier.height(1.5.dp))
                        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                            Text(
                                text = "🕒",
                                style = TextStyle(fontSize = 7.5.sp),
                            )
                            Spacer(GlanceModifier.width(2.dp))
                            Text(
                                text = formatWidgetTime(data.occurredAt),
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF9E9487)),
                                    fontSize = if (isCompact) 8.sp else 8.5.sp,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

class LatestTransactionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LatestTransactionWidget()
}
