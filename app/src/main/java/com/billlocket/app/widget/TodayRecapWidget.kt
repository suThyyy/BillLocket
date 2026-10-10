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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.billlocket.app.R
import com.billlocket.app.locale.localizedContext
import com.billlocket.app.locale.resolveAppLocale

class TodayRecapWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadTodayRecapData(context)
        provideContent {
            TodayRecapContent(context, data)
        }
    }
}

@Composable
fun TodayRecapContent(context: Context, data: TodayRecapWidgetData) {
    val locCtx = context.localizedContext()
    val locale = resolveAppLocale(context)
    val openTodayIntent = widgetIntent(context, WIDGET_ACTION_TODAY, data.todayEpochDay.toString())

    // Outer subtle warm border (1.5dp)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(ColorProvider(Color(0xFF3E3122)))
            .padding(1.5.dp),
    ) {
        // Main dark container
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(18.5.dp)
                .background(ColorProvider(Color(0xFF14110E)))
                .padding(horizontal = 5.dp, vertical = 6.dp)
                .clickable(actionStartActivity(openTodayIntent)),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // Top section: Multi-photo collage
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .cornerRadius(12.dp)
                        .background(ColorProvider(Color(0xFF1A1612))),
                ) {
                    when (data.images.size) {
                        0 -> {
                            Column(
                                modifier = GlanceModifier.fillMaxSize().padding(6.dp),
                                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                                verticalAlignment = Alignment.Vertical.CenterVertically,
                            ) {
                                Text(text = "📸", style = TextStyle(fontSize = 24.sp))
                                Spacer(GlanceModifier.height(4.dp))
                                Text(
                                    text = locCtx.getString(R.string.widget_today_no_transactions),
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFF9E9487)),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                    ),
                                    maxLines = 1,
                                )
                            }
                        }
                        1 -> {
                            Image(
                                provider = ImageProvider(data.images[0]),
                                contentDescription = locCtx.getString(R.string.widget_today_photo_desc),
                                contentScale = ContentScale.Crop,
                                modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp),
                            )
                        }
                        2 -> {
                            Row(modifier = GlanceModifier.fillMaxSize()) {
                                Image(
                                    provider = ImageProvider(data.images[0]),
                                    contentDescription = locCtx.getString(R.string.widget_today_photo_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(10.dp),
                                )
                                Spacer(GlanceModifier.width(3.5.dp))
                                Image(
                                    provider = ImageProvider(data.images[1]),
                                    contentDescription = locCtx.getString(R.string.widget_today_photo_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(10.dp),
                                )
                            }
                        }
                        3 -> {
                            Row(modifier = GlanceModifier.fillMaxSize()) {
                                Image(
                                    provider = ImageProvider(data.images[0]),
                                    contentDescription = locCtx.getString(R.string.widget_today_photo_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(10.dp),
                                )
                                Spacer(GlanceModifier.width(3.5.dp))
                                Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                                    Image(
                                        provider = ImageProvider(data.images[1]),
                                        contentDescription = locCtx.getString(R.string.widget_today_photo_desc),
                                        contentScale = ContentScale.Crop,
                                        modifier = GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(8.dp),
                                    )
                                    Spacer(GlanceModifier.height(3.5.dp))
                                    Image(
                                        provider = ImageProvider(data.images[2]),
                                        contentDescription = locCtx.getString(R.string.widget_today_photo_desc),
                                        contentScale = ContentScale.Crop,
                                        modifier = GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(8.dp),
                                    )
                                }
                            }
                        }
                        else -> { // 4 or more (Matches Image 2 exactly!)
                            Row(modifier = GlanceModifier.fillMaxSize()) {
                                Image(
                                    provider = ImageProvider(data.images[0]),
                                    contentDescription = locCtx.getString(R.string.widget_today_photo_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(10.dp),
                                )
                                Spacer(GlanceModifier.width(3.5.dp))
                                Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                                    Image(
                                        provider = ImageProvider(data.images[1]),
                                        contentDescription = locCtx.getString(R.string.widget_today_photo_desc),
                                        contentScale = ContentScale.Crop,
                                        modifier = GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(8.dp),
                                    )
                                    Spacer(GlanceModifier.height(3.5.dp))
                                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                        Image(
                                            provider = ImageProvider(data.images[2]),
                                            contentDescription = locCtx.getString(R.string.widget_today_photo_desc),
                                            contentScale = ContentScale.Crop,
                                            modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(7.dp),
                                        )
                                        Spacer(GlanceModifier.width(3.5.dp))
                                        Image(
                                            provider = ImageProvider(data.images[3]),
                                            contentDescription = locCtx.getString(R.string.widget_today_photo_desc),
                                            contentScale = ContentScale.Crop,
                                            modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(7.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(GlanceModifier.height(4.dp))

                // Bottom card: Dark glass summary surface with comfortable breathing room
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(12.dp)
                        .background(ColorProvider(Color(0xFF1F1A14)))
                        .padding(horizontal = 4.dp, vertical = 4.5.dp),
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                    ) {
                        // Left: Dynamic calendar badge + "Hôm nay" + transaction count
                        Row(
                            modifier = GlanceModifier.defaultWeight(),
                            verticalAlignment = Alignment.Vertical.CenterVertically,
                        ) {
                            if (data.calendarIcon != null) {
                                Image(
                                    provider = ImageProvider(data.calendarIcon),
                                    contentDescription = locCtx.getString(R.string.widget_calendar_today_desc),
                                    modifier = GlanceModifier.size(22.dp),
                                )
                            } else {
                                Box(
                                    modifier = GlanceModifier
                                        .size(22.dp)
                                        .cornerRadius(5.dp)
                                        .background(ColorProvider(Color(0xFF2C2217))),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${data.todayEpochDay % 31 + 1}",
                                        style = TextStyle(
                                            color = ColorProvider(Color.White),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                }
                            }
                            Spacer(GlanceModifier.width(3.dp))
                            Column(
                                verticalAlignment = Alignment.Vertical.CenterVertically,
                            ) {
                                Text(
                                    text = locCtx.getString(R.string.widget_today_title),
                                    style = TextStyle(
                                        color = ColorProvider(Color.White),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    maxLines = 1,
                                )
                                Text(
                                    text = locCtx.getString(R.string.widget_today_transactions_count, data.transactionCount),
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFF9E9487)),
                                        fontSize = 7.5.sp,
                                    ),
                                    maxLines = 1,
                                )
                            }
                        }

                        // Divider
                        Box(
                            modifier = GlanceModifier
                                .width(1.dp)
                                .height(18.dp)
                                .background(ColorProvider(Color(0xFF382E22))),
                        ) {}

                        Spacer(GlanceModifier.width(3.dp))

                        // Right: Total spent
                        Column(horizontalAlignment = Alignment.Horizontal.End) {
                            Text(
                                text = locCtx.getString(R.string.widget_today_spent),
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF9E9487)),
                                    fontSize = 7.sp,
                                ),
                                maxLines = 1,
                            )
                            Text(
                                text = formatWidgetAmount(data.totalExpenseMinor, locale),
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFFFB800)),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
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

class TodayRecapWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayRecapWidget()
}
