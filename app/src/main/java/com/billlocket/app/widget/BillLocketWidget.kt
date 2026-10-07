package com.billlocket.app.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import com.billlocket.app.domain.port.WidgetUpdatePort
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

class BillLocketWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadLatestTransactionData(context)
        provideContent {
            LatestTransactionContent(context, data)
        }
    }
}

class BillLocketWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BillLocketWidget()
}

@Singleton
class AndroidWidgetUpdatePort @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetUpdatePort {
    override suspend fun requestRefresh(): Outcome<Unit> = runCatching {
        LatestTransactionWidget().updateAll(context)
        TodayRecapWidget().updateAll(context)
        WeeklyRecapWidget().updateAll(context)
        BillLocketWidget().updateAll(context)
    }.fold(
        { Outcome.Success(Unit) },
        { Outcome.Failure(DomainFailure.StorageUnavailable("widget_refresh")) },
    )
}
