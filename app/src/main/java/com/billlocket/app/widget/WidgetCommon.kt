package com.billlocket.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.billlocket.app.MainActivity
import com.billlocket.app.data.local.dao.CategoryDao
import com.billlocket.app.data.local.dao.ProjectionDao
import com.billlocket.app.data.local.dao.TransactionDao
import com.billlocket.app.feature.insights.PeriodKind
import com.billlocket.app.domain.model.WidgetPhoto
import com.billlocket.app.domain.port.MediaStorePort
import com.billlocket.app.domain.repository.InsightsRepository
import com.billlocket.app.domain.repository.SettingsRepository
import com.billlocket.app.domain.repository.TransactionRepository
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.feature.insights.periodQuery
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.billlocket.app.locale.BillLocketFormatters
import com.billlocket.app.locale.localizedContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

const val WIDGET_ACTION_EXTRA = "billlocket.widget.action"
const val WIDGET_ID_EXTRA = "billlocket.widget.id"
const val WIDGET_ACTION_DETAIL = "detail"
const val WIDGET_ACTION_CAPTURE = "capture"
const val WIDGET_ACTION_TODAY = "today"
const val WIDGET_ACTION_WEEKLY = "weekly"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun transactions(): TransactionRepository
    fun settings(): SettingsRepository
    fun media(): MediaStorePort
    fun transactionDao(): TransactionDao
    fun categoryDao(): CategoryDao
    fun projectionDao(): ProjectionDao
    fun insights(): InsightsRepository
}

data class LatestTransactionWidgetData(
    val transactionId: String?,
    val categoryName: String,
    val amountMinor: Long,
    val isExpense: Boolean,
    val occurredAt: Long,
    val image: Bitmap?,
    val hasContent: Boolean,
)

data class TodayRecapWidgetData(
    val todayEpochDay: Long,
    val transactionCount: Int,
    val totalExpenseMinor: Long,
    val images: List<Bitmap>,
    val calendarIcon: Bitmap? = null,
)

fun generateDynamicCalendarBitmap(date: LocalDate, locale: Locale = Locale.getDefault()): Bitmap {
    val sizePx = 96
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cornerRadius = 18f
    val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())

    // 1. Draw rounded white background
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

    // 2. Draw red top header bar
    val headerHeight = 30f
    val headerPath = Path().apply {
        val topRect = RectF(0f, 0f, sizePx.toFloat(), headerHeight)
        addRoundRect(
            topRect,
            floatArrayOf(
                cornerRadius, cornerRadius, // top-left
                cornerRadius, cornerRadius, // top-right
                0f, 0f,                     // bottom-right
                0f, 0f                      // bottom-left
            ),
            Path.Direction.CW
        )
    }
    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E53935")
        style = Paint.Style.FILL
    }
    canvas.drawPath(headerPath, headerPaint)

    // 3. Month text (e.g. "Th 10" or "OCT")
    val monthText = runCatching {
        if (locale.language == "vi") {
            "Th ${date.monthValue}"
        } else {
            date.month.name.take(3)
        }
    }.getOrDefault("Th ${date.monthValue}").uppercase(locale)

    val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(monthText, sizePx / 2f, 21f, monthPaint)

    // 4. Day number text (e.g. "4", "17", "28")
    val dayText = date.dayOfMonth.toString()
    val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1A1A1A")
        textSize = if (dayText.length > 2) 34f else 40f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val fontMetrics = dayPaint.fontMetrics
    val dayCenterY = headerHeight + (sizePx - headerHeight) / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
    canvas.drawText(dayText, sizePx / 2f, dayCenterY, dayPaint)

    // 5. Calendar binder pins at the top
    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#424242")
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(22f, 3f, 28f, 10f), 3f, 3f, pinPaint)
    canvas.drawRoundRect(RectF(68f, 3f, 74f, 10f), 3f, 3f, pinPaint)

    // 6. Subtle outer border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#26000000")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

    return bitmap
}

data class WeeklyRecapWidgetData(
    val weekTransactionCount: Int,
    val totalExpenseMinor: Long,
    val topCategoryName: String?,
    val topCategoryPercent: Int,
    val images: List<Bitmap>,
)

fun widgetIntent(context: Context, action: String, id: String?): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(WIDGET_ACTION_EXTRA, action)
        id?.let { putExtra(WIDGET_ID_EXTRA, it) }
    }

/** BL-045: Formats using the current active locale instead of hard-coded vi-VN. */
fun formatWidgetAmount(amountMinor: Long, locale: Locale = Locale.getDefault()): String =
    BillLocketFormatters.formatMoney(amountMinor, locale)

/**
 * BL-045: Formats using the current active locale.
 * Callers must supply [todayLabel] and [yesterdayLabel] from localized string resources.
 */
fun formatWidgetTime(
    occurredAtMillis: Long,
    todayLabel: String,
    yesterdayLabel: String,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = BillLocketFormatters.formatWidgetRelativeTime(
    occurredAtMillis, zone, locale, todayLabel, yesterdayLabel,
)

suspend fun decodeWidgetBitmap(media: MediaStorePort, uri: String, maxEdge: Int = 320): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    when (val opened = media.openRead(uri)) {
        is Outcome.Success -> opened.value.use { BitmapFactory.decodeStream(it, null, bounds) }
        is Outcome.Failure -> return null
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = widgetDecodeSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = when (val opened = media.openRead(uri)) {
        is Outcome.Success -> opened.value.use { BitmapFactory.decodeStream(it, null, options) }
        is Outcome.Failure -> null
    } ?: return null

    val longest = max(decoded.width, decoded.height)
    if (longest <= maxEdge) return decoded

    val scale = maxEdge.toFloat() / longest
    val scaled = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * scale).roundToInt().coerceAtLeast(1),
        (decoded.height * scale).roundToInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== decoded) decoded.recycle()
    scaled
}.getOrNull()

fun widgetDecodeSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
    val longest = max(width, height)
    var sample = 1
    while (longest / (sample * 2) >= maxEdge) sample *= 2
    return sample
}

suspend fun selectEligibleWidgetPhoto(
    candidates: List<WidgetPhoto>,
    exists: suspend (String) -> Boolean,
): WidgetPhoto? = candidates.firstOrNull { exists(it.mediaUri) }

suspend fun loadLatestTransactionData(context: Context): LatestTransactionWidgetData = runCatching {
    val locContext = context.localizedContext()
    val defaultCategoryName = locContext.getString(com.billlocket.app.R.string.home_transactions_header)
    val now = System.currentTimeMillis()
    val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    val candidates = entry.projectionDao().widgetCandidates(15).filter { it.occurredAt <= now }
    val eligible = candidates.firstOrNull { entry.media().exists(it.contentUri) }
    if (eligible == null) {
        val todayEpochDay = java.time.LocalDate.now().toEpochDay()
        val recentTx = entry.transactionDao().observeCalendarRange(0L, todayEpochDay).first().firstOrNull { it.transaction.occurredAt <= now }
        if (recentTx != null) {
            val catName = recentTx.transaction.categoryId?.let { entry.categoryDao().findById(it)?.name }.orEmpty()
            LatestTransactionWidgetData(
                transactionId = recentTx.transaction.id,
                categoryName = catName.ifBlank { defaultCategoryName },
                amountMinor = recentTx.transaction.amountMinor,
                isExpense = recentTx.transaction.type != "INCOME",
                occurredAt = recentTx.transaction.occurredAt,
                image = null,
                hasContent = true,
            )
        } else {
            LatestTransactionWidgetData(
                transactionId = null,
                categoryName = "",
                amountMinor = 0L,
                isExpense = true,
                occurredAt = 0L,
                image = null,
                hasContent = false,
            )
        }
    } else {
        val bitmap = decodeWidgetBitmap(entry.media(), eligible.contentUri, maxEdge = 320)
        val txEntity = entry.transactionDao().findEntity(eligible.transactionId)
        val isExpense = txEntity?.type != "INCOME"
        LatestTransactionWidgetData(
            transactionId = eligible.transactionId,
            categoryName = eligible.categoryName.ifBlank { defaultCategoryName },
            amountMinor = eligible.amountMinor,
            isExpense = isExpense,
            occurredAt = eligible.occurredAt,
            image = bitmap,
            hasContent = true,
        )
    }
}.getOrElse {
    LatestTransactionWidgetData(
        transactionId = null,
        categoryName = "",
        amountMinor = 0L,
        isExpense = true,
        occurredAt = 0L,
        image = null,
        hasContent = false,
    )
}

suspend fun loadTodayRecapData(context: Context): TodayRecapWidgetData = runCatching {
    val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    val today = LocalDate.now()
    val todayEpoch = today.toEpochDay()
    val transactionsToday = entry.transactionDao().observeCalendarRange(todayEpoch, todayEpoch).first()
    val count = transactionsToday.size
    val totalExpense = transactionsToday
        .filter { it.transaction.type != "INCOME" }
        .sumOf { it.transaction.amountMinor }
    val candidateUris = transactionsToday
        .flatMap { it.images }
        .map { it.contentUri }
        .distinct()
        .filter { entry.media().exists(it) }
        .take(4)
    val bitmaps = candidateUris.mapNotNull {
        decodeWidgetBitmap(entry.media(), it, maxEdge = 200)
    }
    val appLocale = com.billlocket.app.locale.resolveAppLocale(context)
    val calendarIcon = generateDynamicCalendarBitmap(today, appLocale)
    TodayRecapWidgetData(
        todayEpochDay = todayEpoch,
        transactionCount = count,
        totalExpenseMinor = totalExpense,
        images = bitmaps,
        calendarIcon = calendarIcon,
    )
}.getOrElse {
    val today = LocalDate.now()
    val appLocale = com.billlocket.app.locale.resolveAppLocale(context)
    TodayRecapWidgetData(
        todayEpochDay = today.toEpochDay(),
        transactionCount = 0,
        totalExpenseMinor = 0L,
        images = emptyList(),
        calendarIcon = generateDynamicCalendarBitmap(today, appLocale),
    )
}

suspend fun loadWeeklyRecapData(context: Context): WeeklyRecapWidgetData = runCatching {
    val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    val today = LocalDate.now()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekStart = monday.toEpochDay()
    val weekEnd = weekStart + 6
    val weekTransactions = entry.transactionDao().observeCalendarRange(weekStart, weekEnd).first()
    val count = weekTransactions.size
    val totalExpense = weekTransactions
        .filter { it.transaction.type != "INCOME" }
        .sumOf { it.transaction.amountMinor }

    val insightsQuery = periodQuery(PeriodKind.WEEK, today)
    val periodInsights = entry.insights().observePeriod(insightsQuery).first()
    val topCategory = periodInsights.categories.maxByOrNull { it.amount.minorUnits }
    val percent = if (totalExpense > 0 && topCategory != null) {
        (topCategory.amount.minorUnits.toDouble() / totalExpense * 100).roundToInt().coerceIn(1, 100)
    } else {
        0
    }

    val candidateUris = weekTransactions
        .flatMap { it.images }
        .map { it.contentUri }
        .distinct()
        .filter { entry.media().exists(it) }
        .take(5)

    val bitmaps = candidateUris.mapIndexedNotNull { index, uri ->
        val maxEdge = if (index == 0) 240 else 160
        decodeWidgetBitmap(entry.media(), uri, maxEdge = maxEdge)
    }

    WeeklyRecapWidgetData(
        weekTransactionCount = count,
        totalExpenseMinor = totalExpense,
        topCategoryName = topCategory?.name,
        topCategoryPercent = percent,
        images = bitmaps,
    )
}.getOrElse {
    WeeklyRecapWidgetData(
        weekTransactionCount = 0,
        totalExpenseMinor = 0L,
        topCategoryName = null,
        topCategoryPercent = 0,
        images = emptyList(),
    )
}
