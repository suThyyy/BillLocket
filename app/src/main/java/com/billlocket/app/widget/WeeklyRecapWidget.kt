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

class WeeklyRecapWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWeeklyRecapData(context)
        provideContent {
            WeeklyRecapContent(context, data)
        }
    }
}

@Composable
fun WeeklyRecapContent(context: Context, data: WeeklyRecapWidgetData) {
    val locCtx = context.localizedContext()
    val locale = resolveAppLocale(context)
    val openWeeklyIntent = widgetIntent(context, WIDGET_ACTION_WEEKLY, null)

    // Outer subtle warm border (1.5dp)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(ColorProvider(Color(0xFF3E3122)))
            .padding(1.5.dp),
    ) {
        // Main dark surface container
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.5.dp)
                .background(ColorProvider(Color(0xFF14110E)))
                .padding(9.dp)
                .clickable(actionStartActivity(openWeeklyIntent)),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                // Left Section (~45% width): Financial Metrics & Category Insight
                Column(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    // Header: Brand & subtle navigation indicator
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(22.dp)
                                .cornerRadius(5.dp)
                                .background(ColorProvider(Color(0xFF2C2217))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "👛", style = TextStyle(fontSize = 11.sp))
                        }
                        Spacer(GlanceModifier.width(5.dp))
                        Column {
                            Text(
                                text = "BillLocket",
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            )
                            Text(
                                text = locCtx.getString(R.string.widget_weekly_title),
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF9E9487)),
                                    fontSize = 9.sp,
                                ),
                            )
                        }
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            text = "›",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF9E9487)),
                                fontSize = 13.sp,
                            ),
                        )
                    }

                    Spacer(GlanceModifier.defaultWeight())

                    // Headline: EXACTLY "Tuần này" (No sparkle, no emoji)
                    Text(
                        text = locCtx.getString(R.string.widget_this_week),
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFFB800)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        text = locCtx.getString(R.string.widget_today_transactions_count, data.weekTransactionCount),
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )

                    Spacer(GlanceModifier.height(1.dp))

                    // Total amount
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(text = "💳", style = TextStyle(fontSize = 11.sp))
                        Spacer(GlanceModifier.width(3.dp))
                        Text(
                            text = formatWidgetAmount(data.totalExpenseMinor, locale),
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFFFB800)),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }

                    Spacer(GlanceModifier.defaultWeight())

                    // Category insight pill
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(9.dp)
                            .background(ColorProvider(Color(0xFF221C16)))
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Vertical.CenterVertically,
                        ) {
                            Text(text = "📊", style = TextStyle(fontSize = 10.sp))
                            Spacer(GlanceModifier.width(4.dp))
                            val insightText = if (data.topCategoryName != null && data.topCategoryPercent > 0) {
                                locCtx.getString(R.string.widget_weekly_top_category, data.topCategoryName, data.topCategoryPercent)
                            } else {
                                locCtx.getString(R.string.widget_weekly_no_spending)
                            }
                            Text(
                                text = insightText,
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFE0D7CC)),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            Text(
                                text = "›",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFFFB800)),
                                    fontSize = 11.sp,
                                ),
                            )
                        }
                    }
                }

                Spacer(GlanceModifier.width(8.dp))

                // Right Section (~55% width): Photo collage
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
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
                                Text(text = "📸", style = TextStyle(fontSize = 22.sp))
                                Spacer(GlanceModifier.height(4.dp))
                                Text(
                                    text = locCtx.getString(R.string.widget_weekly_no_photos),
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFF9E9487)),
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                    ),
                                )
                            }
                        }
                        1 -> {
                            Image(
                                provider = ImageProvider(data.images[0]),
                                contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                contentScale = ContentScale.Crop,
                                modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp),
                            )
                        }
                        2 -> {
                            Row(modifier = GlanceModifier.fillMaxSize()) {
                                Image(
                                    provider = ImageProvider(data.images[0]),
                                    contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(10.dp),
                                )
                                Spacer(GlanceModifier.width(3.dp))
                                Image(
                                    provider = ImageProvider(data.images[1]),
                                    contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(10.dp),
                                )
                            }
                        }
                        3 -> {
                            Row(modifier = GlanceModifier.fillMaxSize()) {
                                Image(
                                    provider = ImageProvider(data.images[0]),
                                    contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(10.dp),
                                )
                                Spacer(GlanceModifier.width(3.dp))
                                Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                                    Image(
                                        provider = ImageProvider(data.images[1]),
                                        contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                        contentScale = ContentScale.Crop,
                                        modifier = GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(8.dp),
                                    )
                                    Spacer(GlanceModifier.height(3.dp))
                                    Image(
                                        provider = ImageProvider(data.images[2]),
                                        contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                        contentScale = ContentScale.Crop,
                                        modifier = GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(8.dp),
                                    )
                                }
                            }
                        }
                        4 -> {
                            Row(modifier = GlanceModifier.fillMaxSize()) {
                                Image(
                                    provider = ImageProvider(data.images[0]),
                                    contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(10.dp),
                                )
                                Spacer(GlanceModifier.width(3.dp))
                                Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                                    Image(
                                        provider = ImageProvider(data.images[1]),
                                        contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                        contentScale = ContentScale.Crop,
                                        modifier = GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(8.dp),
                                    )
                                    Spacer(GlanceModifier.height(3.dp))
                                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                        Image(
                                            provider = ImageProvider(data.images[2]),
                                            contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                            contentScale = ContentScale.Crop,
                                            modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(7.dp),
                                        )
                                        Spacer(GlanceModifier.width(3.dp))
                                        Image(
                                            provider = ImageProvider(data.images[3]),
                                            contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                            contentScale = ContentScale.Crop,
                                            modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(7.dp),
                                        )
                                    }
                                }
                            }
                        }
                        else -> { // 5 or more (matches Image 3 exactly!)
                            Row(modifier = GlanceModifier.fillMaxSize()) {
                                Image(
                                    provider = ImageProvider(data.images[0]),
                                    contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(10.dp),
                                )
                                Spacer(GlanceModifier.width(3.dp))
                                Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                        Image(
                                            provider = ImageProvider(data.images[1]),
                                            contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                            contentScale = ContentScale.Crop,
                                            modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(7.dp),
                                        )
                                        Spacer(GlanceModifier.width(3.dp))
                                        Image(
                                            provider = ImageProvider(data.images[2]),
                                            contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                            contentScale = ContentScale.Crop,
                                            modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(7.dp),
                                        )
                                    }
                                    Spacer(GlanceModifier.height(3.dp))
                                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                        Image(
                                            provider = ImageProvider(data.images[3]),
                                            contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                            contentScale = ContentScale.Crop,
                                            modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(7.dp),
                                        )
                                        Spacer(GlanceModifier.width(3.dp))
                                        Image(
                                            provider = ImageProvider(data.images[4]),
                                            contentDescription = locCtx.getString(R.string.widget_weekly_photo_desc),
                                            contentScale = ContentScale.Crop,
                                            modifier = GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(7.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class WeeklyRecapWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeeklyRecapWidget()
}
