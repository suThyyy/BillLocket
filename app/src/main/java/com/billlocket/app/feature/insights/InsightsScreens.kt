package com.billlocket.app.feature.insights

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.billlocket.app.domain.model.*
import com.billlocket.app.ui.common.BillLocketEmptyState
import com.billlocket.app.ui.common.BillLocketErrorBanner
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun amount(value: Long, visibility: AmountVisibility = AmountVisibility.SHOWN): String = when (visibility) {
    AmountVisibility.SHOWN -> NumberFormat.getNumberInstance(Locale("vi", "VN")).format(value) + " ₫"
    AmountVisibility.HIDDEN -> "••••"
    AmountVisibility.OBSCURED -> "*** ₫"
}

private fun formatDateShort(value: InstantAtZone): String = runCatching {
    val zone = runCatching { ZoneId.of(value.zoneId) }.getOrElse { ZoneId.systemDefault() }
    Instant.ofEpochMilli(value.epochMillis).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
}.getOrElse { "" }

private fun categoryIcon(name: String): ImageVector {
    val key = name.lowercase()
    return when {
        "mua" in key || "shop" in key -> Icons.Filled.ShoppingCart
        "nhà" in key || "cửa" in key || "phòng" in key -> Icons.Filled.Home
        "ăn" in key || "uống" in key || "food" in key -> Icons.Filled.Restaurant
        "quần" in key || "áo" in key || "mặc" in key || "cloth" in key -> Icons.Filled.Checkroom
        "xe" in key || "di chuyển" in key || "xăng" in key -> Icons.Filled.DirectionsCar
        "giải trí" in key || "chơi" in key || "phim" in key -> Icons.Filled.ConfirmationNumber
        "y tế" in key || "thuốc" in key || "sức khỏe" in key -> Icons.Filled.LocalHospital
        "lương" in key || "thu nhập" in key || "salary" in key -> Icons.Filled.Payments
        "thưởng" in key -> Icons.Filled.CardGiftcard
        "đầu tư" in key -> Icons.Filled.ShowChart
        "hóa đơn" in key || "điện" in key || "nước" in key -> Icons.Filled.Receipt
        else -> Icons.Filled.Category
    }
}

private fun categoryColor(name: String): Pair<Color, Color> {
    val key = name.lowercase()
    return when {
        "mua" in key || "shop" in key -> Color(0x33FF6B6B) to Color(0xFFFF6B6B)
        "nhà" in key || "cửa" in key -> Color(0x339B51E0) to Color(0xFFBB86FC)
        "ăn" in key || "uống" in key -> Color(0x3327AE60) to Color(0xFF57CA85)
        "quần" in key || "áo" in key -> Color(0x332F80ED) to Color(0xFF64B5F6)
        "xe" in key || "di chuyển" in key -> Color(0x33E2B93B) to Color(0xFFFFB800)
        "giải trí" in key -> Color(0x33E91E63) to Color(0xFFFF4081)
        "y tế" in key -> Color(0x3300BCD4) to Color(0xFF4DD0E1)
        else -> Color(0x33888888) to Color(0xFFB0AEA7)
    }
}

data class MetricComparison(
    val arrow: String,
    val text: String,
    val isPositive: Boolean,
)

private fun computeComparison(
    current: Long,
    previous: Long?,
    isExpense: Boolean,
): MetricComparison? {
    if (previous == null) return null
    if (previous == 0L) {
        return if (current > 0L) {
            MetricComparison("↑", "100% so với kỳ trước", isPositive = !isExpense)
        } else {
            MetricComparison("", "0% so với kỳ trước", isPositive = true)
        }
    }
    val delta = current - previous
    val pct = (delta.toDouble() / abs(previous) * 100.0).roundToInt()
    val arrow = when {
        pct > 0 -> "↑"
        pct < 0 -> "↓"
        else -> ""
    }
    val absPct = abs(pct)
    val isPositive = if (isExpense) pct <= 0 else pct >= 0
    return MetricComparison(
        arrow = arrow,
        text = if (arrow.isNotEmpty()) "$arrow $absPct% so với kỳ trước" else "0% so với kỳ trước",
        isPositive = isPositive,
    )
}

data class TrendBarItem(
    val epochDay: Long,
    val label: String,
    val tooltipDate: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun StatisticsScreen(
    onOpenTransaction: (TransactionId) -> Unit = {},
    onOpenAllTransactions: () -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    val insights = state.insights

    val trendBars = remember(state.trendGranularity, state.anchor, insights?.trend, state.yearlyTrend) {
        when (state.trendGranularity) {
            TrendGranularity.DAY -> {
                val yearMonth = YearMonth.from(state.anchor)
                val daysInMonth = yearMonth.lengthOfMonth()
                val trendMap = insights?.trend.orEmpty().associateBy { it.epochDay }
                (1..daysInMonth).map { day ->
                    val date = state.anchor.withDayOfMonth(day)
                    val epoch = date.toEpochDay()
                    val point = trendMap[epoch]
                    val isAxisLabelDay = day == 1 || day == 5 || day == 10 || day == 15 || day == 20 || day == 25 || day == daysInMonth
                    TrendBarItem(
                        epochDay = epoch,
                        label = if (isAxisLabelDay) "$day/${date.monthValue}" else "",
                        tooltipDate = "$day/${date.monthValue}",
                        incomeMinor = point?.income?.minorUnits ?: 0L,
                        expenseMinor = point?.expense?.minorUnits ?: 0L,
                    )
                }
            }
            TrendGranularity.WEEK -> {
                val yearMonth = YearMonth.from(state.anchor)
                val daysInMonth = yearMonth.lengthOfMonth()
                val trendMap = insights?.trend.orEmpty().associateBy { it.epochDay }
                val weekRanges = listOf(
                    1 to 7,
                    8 to 14,
                    15 to 21,
                    22 to 28,
                    29 to daysInMonth,
                ).filter { it.first <= daysInMonth }

                weekRanges.mapIndexed { index, (startDay, endDay) ->
                    val actualEnd = minOf(endDay, daysInMonth)
                    var incomeSum = 0L
                    var expenseSum = 0L
                    for (d in startDay..actualEnd) {
                        val epoch = state.anchor.withDayOfMonth(d).toEpochDay()
                        trendMap[epoch]?.let {
                            incomeSum += it.income.minorUnits
                            expenseSum += it.expense.minorUnits
                        }
                    }
                    val weekNum = index + 1
                    TrendBarItem(
                        epochDay = state.anchor.withDayOfMonth(startDay).toEpochDay(),
                        label = "T$weekNum",
                        tooltipDate = "Tuần $weekNum ($startDay/${state.anchor.monthValue} - $actualEnd/${state.anchor.monthValue})",
                        incomeMinor = incomeSum,
                        expenseMinor = expenseSum,
                    )
                }
            }
            TrendGranularity.MONTH -> {
                val currentYear = state.anchor.year
                val monthMap = state.yearlyTrend.groupBy {
                    LocalDate.ofEpochDay(it.epochDay).monthValue
                }
                (1..12).map { month ->
                    val points = monthMap[month].orEmpty()
                    val incomeSum = points.sumOf { it.income.minorUnits }
                    val expenseSum = points.sumOf { it.expense.minorUnits }
                    TrendBarItem(
                        epochDay = LocalDate.of(currentYear, month, 1).toEpochDay(),
                        label = "T$month",
                        tooltipDate = "Tháng $month/$currentYear",
                        incomeMinor = incomeSum,
                        expenseMinor = expenseSum,
                    )
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Month Header & Period Navigator
        item {
            MonthHeaderSection(
                anchor = state.anchor,
                onPrevious = { viewModel.onAction(StatisticsAction.Previous) },
                onNext = { viewModel.onAction(StatisticsAction.Next) },
                onToday = { viewModel.onAction(StatisticsAction.Today) },
            )
        }

        // Account filter chips (if user has multiple accounts)
        if (state.accounts.size > 1) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        FilterChip(
                            selected = state.accountId == null,
                            onClick = { viewModel.onAction(StatisticsAction.AccountChanged(null)) },
                            label = { Text("Tất cả tài khoản") },
                        )
                    }
                    items(state.accounts, key = { it.id.value }) { account ->
                        FilterChip(
                            selected = state.accountId == account.id,
                            onClick = { viewModel.onAction(StatisticsAction.AccountChanged(account.id.takeUnless { it == state.accountId })) },
                            label = { Text(account.name) },
                        )
                    }
                }
            }
        }

        if (state.isLoading && insights == null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        state.errorMessage?.let { error ->
            item { BillLocketErrorBanner(error) }
        }

        if (insights != null) {
            // 2. Summary Metric Cards: Thu, Chi, Ròng
            item {
                SummaryMetricCards(
                    insights = insights,
                    visibility = state.amountVisibility,
                )
            }

            // 3. Period Comparison Insight
            item {
                PeriodComparisonInsightCard(
                    insights = insights,
                    onClick = onOpenBudgets,
                )
            }

            // 4. Chi theo danh mục
            item {
                CategorySpendingSection(
                    categories = insights.categories,
                    totalExpense = insights.expense.minorUnits,
                    visibility = state.amountVisibility,
                    onViewDetails = onOpenBudgets,
                )
            }

            // 5. Xu hướng chi tiêu (Trend Chart)
            item {
                SpendingTrendSection(
                    items = trendBars,
                    granularity = state.trendGranularity,
                    amountVisibility = state.amountVisibility,
                    onGranularityChanged = { viewModel.onAction(StatisticsAction.TrendGranularityChanged(it)) },
                )
            }

            // 6. Giao dịch gần đây
            item {
                RecentTransactionsSection(
                    transactions = state.recentTransactions,
                    visibility = state.amountVisibility,
                    onOpenTransaction = onOpenTransaction,
                    onViewAll = onOpenAllTransactions,
                )
            }
        }
    }
}

@Composable
private fun MonthHeaderSection(
    anchor: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Tháng trước",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Tháng ${anchor.monthValue}/${anchor.year}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color(0xFFFFB800),
                modifier = Modifier.size(20.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Tháng sau",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Surface(
                onClick = onToday,
                shape = RoundedCornerShape(12.dp),
                color = Color(0x1AFFB800),
                border = BorderStroke(1.dp, Color(0x4DFFB800)),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = "Hôm nay",
                        tint = Color(0xFFFFB800),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCards(
    insights: PeriodInsights,
    visibility: AmountVisibility,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val incomeComp = computeComparison(
            current = insights.income.minorUnits,
            previous = insights.comparison?.income?.minorUnits,
            isExpense = false,
        )
        val expenseComp = computeComparison(
            current = insights.expense.minorUnits,
            previous = insights.comparison?.expense?.minorUnits,
            isExpense = true,
        )
        val netComp = computeComparison(
            current = insights.net.minorUnits,
            previous = insights.comparison?.net?.minorUnits,
            isExpense = false,
        )

        MetricCard(
            label = "Thu",
            amountText = amount(insights.income.minorUnits, visibility),
            badgeColor = Color(0xFF66D19E),
            badgeIcon = Icons.Filled.ArrowUpward,
            comparison = incomeComp,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "Chi",
            amountText = amount(insights.expense.minorUnits, visibility),
            badgeColor = Color(0xFFFF8A84),
            badgeIcon = Icons.Filled.ArrowDownward,
            comparison = expenseComp,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "Ròng",
            amountText = amount(insights.net.minorUnits, visibility),
            badgeColor = Color(0xFFFFB800),
            badgeIcon = Icons.Filled.Savings,
            comparison = netComp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    amountText: String,
    badgeColor: Color,
    badgeIcon: ImageVector,
    comparison: MetricComparison?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF181714),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(badgeColor.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        badgeIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCCCCCC),
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = amountText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (comparison != null) {
                val arrowPrefix = if (comparison.arrow.isNotEmpty()) "${comparison.arrow} " else ""
                val restText = comparison.text.removePrefix(comparison.arrow).trim()
                val arrowColor = if (comparison.isPositive) Color(0xFF66D19E) else Color(0xFFFF8A84)
                val annotatedComparison = buildAnnotatedString {
                    if (arrowPrefix.isNotEmpty()) {
                        withStyle(SpanStyle(color = arrowColor, fontWeight = FontWeight.Bold, fontSize = 8.sp)) {
                            append(arrowPrefix)
                        }
                    }
                    withStyle(SpanStyle(color = Color(0xFF8E8B85), fontSize = 7.8.sp)) {
                        append(restText)
                    }
                }
                Text(
                    text = annotatedComparison,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(modifier = Modifier.height(13.dp))
            }
        }
    }
}

@Composable
private fun PeriodComparisonInsightCard(
    insights: PeriodInsights,
    onClick: () -> Unit,
) {
    val previous = insights.comparison
    val expenseDelta = if (previous != null) insights.expense.minorUnits - previous.expense.minorUnits else null

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1D19),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.BarChart,
                contentDescription = null,
                tint = Color(0xFFFFB800),
                modifier = Modifier.size(20.dp),
            )

            val textModifier = Modifier.weight(1f)
            if (expenseDelta != null) {
                when {
                    expenseDelta > 0 -> {
                        val text = buildAnnotatedString {
                            append("So với tháng trước, chi tiêu tăng ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFFB800))) {
                                append(amount(expenseDelta))
                            }
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = textModifier,
                        )
                    }
                    expenseDelta < 0 -> {
                        val text = buildAnnotatedString {
                            append("So với tháng trước, chi tiêu giảm ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF66D19E))) {
                                append(amount(-expenseDelta))
                            }
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = textModifier,
                        )
                    }
                    else -> {
                        Text(
                            "So với tháng trước, chi tiêu không thay đổi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = textModifier,
                        )
                    }
                }
            } else {
                Text(
                    "Chưa có dữ liệu tháng trước để so sánh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = textModifier,
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color(0x66FFFFFF),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CategorySpendingSection(
    categories: List<CategoryDistribution>,
    totalExpense: Long,
    visibility: AmountVisibility,
    onViewDetails: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Chi theo danh mục",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Surface(
                onClick = onViewDetails,
                shape = CircleShape,
                color = Color(0xFF22201D),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Xem chi tiết",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB0AEA7),
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFB0AEA7),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        if (categories.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF161513),
                border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Chưa có dữ liệu chi tiêu trong kỳ.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF161513),
                border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    categories.forEachIndexed { index, category ->
                        val ratio = if (totalExpense <= 0L) 0f else (category.amount.minorUnits.toFloat() / totalExpense).coerceIn(0f, 1f)
                        val percent = (ratio * 100).toInt()
                        val (bgColor, tintColor) = categoryColor(category.name)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(bgColor, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    categoryIcon(category.name),
                                    contentDescription = null,
                                    tint = tintColor,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF262421),
                                        ) {
                                            Text(
                                                text = "$percent%",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF9E9B94),
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = amount(category.amount.minorUnits, visibility),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Icon(
                                            Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            tint = Color(0x66FFFFFF),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFFFFB800),
                                    trackColor = Color(0xFF262421),
                                )
                            }
                        }

                        if (index < categories.size - 1) {
                            HorizontalDivider(
                                color = Color(0x14FFFFFF),
                                modifier = Modifier.padding(start = 50.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingTrendSection(
    items: List<TrendBarItem>,
    granularity: TrendGranularity,
    amountVisibility: AmountVisibility,
    onGranularityChanged: (TrendGranularity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Xu hướng chi tiêu",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Pill toggle [Ngày | Tuần | Tháng]
            Surface(
                shape = CircleShape,
                color = Color(0xFF22201D),
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TrendGranularity.entries.forEach { option ->
                        val isSelected = option == granularity
                        val title = when (option) {
                            TrendGranularity.DAY -> "Ngày"
                            TrendGranularity.WEEK -> "Tuần"
                            TrendGranularity.MONTH -> "Tháng"
                        }
                        Surface(
                            onClick = { onGranularityChanged(option) },
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFFFFB800) else Color.Transparent,
                        ) {
                            Text(
                                text = title,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else Color(0xFF8E8B85),
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161513),
            border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpendingTrendChart(
                    items = items,
                    granularity = granularity,
                    amountVisibility = amountVisibility,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                )
            }
        }
    }
}

@Composable
private fun SpendingTrendChart(
    items: List<TrendBarItem>,
    granularity: TrendGranularity,
    amountVisibility: AmountVisibility,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Chưa có dữ liệu xu hướng",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val defaultIndex = remember(items) {
        items.indexOfLast { it.incomeMinor > 0L || it.expenseMinor > 0L }.takeIf { it >= 0 } ?: (items.size / 2)
    }
    var selectedIndex by remember(items) { mutableStateOf<Int?>(defaultIndex) }

    val maxVal = remember(items) {
        items.maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor) }?.coerceAtLeast(100_000L) ?: 100_000L
    }
    val niceMax = remember(maxVal) {
        when {
            maxVal <= 100_000L -> 100_000L
            maxVal <= 500_000L -> ((maxVal + 99_999L) / 100_000L) * 100_000L
            maxVal <= 2_000_000L -> ((maxVal + 499_999L) / 500_000L) * 500_000L
            else -> ((maxVal + 999_999L) / 1_000_000L) * 1_000_000L
        }
    }

    val ticks = remember(niceMax) {
        listOf(niceMax, (niceMax * 3) / 4, niceMax / 2, niceMax / 4, 0L)
    }

    fun formatAxis(v: Long): String = when {
        v >= 1_000_000L -> if (v % 1_000_000L == 0L) "${v / 1_000_000L}M" else String.format(Locale.US, "%.1fM", v / 1_000_000.0)
        v >= 1_000L -> "${v / 1_000L}K"
        else -> "$v"
    }

    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier) {
        // Floating / Top Tooltip Card Area
        val selectedItem = selectedIndex?.let { items.getOrNull(it) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (selectedItem != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF22201D),
                    border = BorderStroke(1.dp, Color(0x33FFB800)),
                    modifier = Modifier.wrapContentSize(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = selectedItem.tooltipDate,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        if (selectedItem.incomeMinor > 0L) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF66D19E), CircleShape)
                                )
                                Text(
                                    text = "Thu: ${amount(selectedItem.incomeMinor, amountVisibility)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF66D19E),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        if (selectedItem.expenseMinor > 0L) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFFFF8A84), CircleShape)
                                )
                                Text(
                                    text = "Chi: ${amount(selectedItem.expenseMinor, amountVisibility)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF8A84),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        if (selectedItem.incomeMinor == 0L && selectedItem.expenseMinor == 0L) {
                            Text(
                                text = "Không có giao dịch",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8E8B85),
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(6.dp).background(Color(0xFF66D19E), CircleShape))
                        Text("Thu", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9E9B94))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(6.dp).background(Color(0xFFFF8A84), CircleShape))
                        Text("Chi", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9E9B94))
                    }
                }
            }
        }

        // Canvas plot area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(items) {
                    detectTapGestures { offset ->
                        val leftMarginPx = 36.dp.toPx()
                        val rightMarginPx = 8.dp.toPx()
                        val plotW = size.width - leftMarginPx - rightMarginPx
                        if (plotW > 0) {
                            val relX = offset.x - leftMarginPx
                            val idx = ((relX / plotW) * items.size).toInt().coerceIn(0, items.size - 1)
                            selectedIndex = idx
                        }
                    }
                }
                .pointerInput(items) {
                    detectDragGestures { change, _ ->
                        val leftMarginPx = 36.dp.toPx()
                        val rightMarginPx = 8.dp.toPx()
                        val plotW = size.width - leftMarginPx - rightMarginPx
                        if (plotW > 0) {
                            val relX = change.position.x - leftMarginPx
                            val idx = ((relX / plotW) * items.size).toInt().coerceIn(0, items.size - 1)
                            selectedIndex = idx
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val leftMarginPx = 36.dp.toPx()
                val rightMarginPx = 8.dp.toPx()
                val bottomMarginPx = 20.dp.toPx()
                val plotWidth = size.width - leftMarginPx - rightMarginPx
                val plotHeight = size.height - bottomMarginPx
                val plotBottom = plotHeight

                // Draw Y-axis horizontal gridlines and text labels
                ticks.forEach { tick ->
                    val y = plotBottom - (tick.toFloat() / niceMax * plotHeight)
                    drawLine(
                        color = Color(0x14FFFFFF),
                        start = Offset(leftMarginPx, y),
                        end = Offset(leftMarginPx + plotWidth, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    val labelText = formatAxis(tick)
                    val measured = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(fontSize = 9.sp, color = Color(0xFF75726B)),
                    )
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(leftMarginPx - measured.size.width - 6.dp.toPx(), y - measured.size.height / 2f),
                    )
                }

                if (items.isEmpty() || plotWidth <= 0) return@Canvas

                val step = plotWidth / items.size
                val barWidth = when (granularity) {
                    TrendGranularity.DAY -> (step * 0.38f).coerceIn(2.5.dp.toPx(), 6.dp.toPx())
                    TrendGranularity.WEEK -> 12.dp.toPx()
                    TrendGranularity.MONTH -> 8.dp.toPx()
                }
                val gap = 1.5.dp.toPx()

                // Draw bars for each item
                items.forEachIndexed { index, item ->
                    val centerX = leftMarginPx + (index + 0.5f) * step
                    val isSelected = index == selectedIndex

                    val incomeH = if (niceMax > 0) (item.incomeMinor.toFloat() / niceMax * plotHeight).coerceAtLeast(0f) else 0f
                    val expenseH = if (niceMax > 0) (item.expenseMinor.toFloat() / niceMax * plotHeight).coerceAtLeast(0f) else 0f

                    // Income bar (green)
                    if (incomeH > 0f) {
                        drawRoundRect(
                            color = Color(0xFF66D19E),
                            topLeft = Offset(centerX - barWidth - gap / 2f, plotBottom - incomeH),
                            size = Size(barWidth, incomeH),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        )
                    }

                    // Expense bar (coral)
                    if (expenseH > 0f) {
                        drawRoundRect(
                            color = Color(0xFFFF8A84),
                            topLeft = Offset(centerX + gap / 2f, plotBottom - expenseH),
                            size = Size(barWidth, expenseH),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        )
                    }

                    // Zero indicator pill
                    if (incomeH == 0f && expenseH == 0f) {
                        drawRoundRect(
                            color = Color(0x1FFFFFFF),
                            topLeft = Offset(centerX - 2.dp.toPx(), plotBottom - 2.5.dp.toPx()),
                            size = Size(4.dp.toPx(), 2.5.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                        )
                    }

                    // Selected item highlight
                    if (isSelected) {
                        // Dashed vertical amber line
                        drawLine(
                            color = Color(0xFFFFB800),
                            start = Offset(centerX, 0f),
                            end = Offset(centerX, plotBottom),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                        )

                        // Anchor glow dot at the top of the taller bar
                        val topY = (plotBottom - maxOf(incomeH, expenseH)).coerceAtMost(plotBottom - 4.dp.toPx())
                        drawCircle(
                            color = Color(0x55FFB800),
                            radius = 8.dp.toPx(),
                            center = Offset(centerX, topY),
                        )
                        drawCircle(
                            color = Color(0xFFFFB800),
                            radius = 4.dp.toPx(),
                            center = Offset(centerX, topY),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 1.8.dp.toPx(),
                            center = Offset(centerX, topY),
                        )
                    }

                    // X-axis label
                    if (item.label.isNotEmpty()) {
                        val measuredLabel = textMeasurer.measure(
                            text = item.label,
                            style = TextStyle(
                                fontSize = 9.5.sp,
                                color = if (isSelected) Color(0xFFFFB800) else Color(0xFF75726B),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                        drawText(
                            textLayoutResult = measuredLabel,
                            topLeft = Offset(centerX - measuredLabel.size.width / 2f, plotBottom + 4.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionsSection(
    transactions: List<TransactionSummary>,
    visibility: AmountVisibility,
    onOpenTransaction: (TransactionId) -> Unit,
    onViewAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Giao dịch gần đây",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Surface(
                onClick = onViewAll,
                shape = CircleShape,
                color = Color(0xFF22201D),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Xem tất cả",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB0AEA7),
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFB0AEA7),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        if (transactions.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF161513),
                border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Không có giao dịch nào trong kỳ này.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                transactions.forEach { item ->
                    val (bgColor, tintColor) = categoryColor(item.categoryName)
                    val isExpense = item.type == TransactionType.EXPENSE
                    val amountColor = if (isExpense) Color(0xFFFF8A84) else Color(0xFF66D19E)
                    val prefix = if (isExpense) "−" else "+"

                    Surface(
                        onClick = { onOpenTransaction(item.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF161513),
                        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (item.representativeImageUri != null) {
                                AsyncImage(
                                    model = item.representativeImageUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(bgColor, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        categoryIcon(item.categoryName),
                                        contentDescription = null,
                                        tint = tintColor,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = item.note?.takeIf { it.isNotBlank() } ?: item.categoryName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = formatDateShort(item.occurred),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8E8B85),
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = prefix + amount(item.amount.minorUnits, visibility),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = amountColor,
                                )
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0x66FFFFFF),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun periodName(kind: PeriodKind) = when (kind) { PeriodKind.WEEK -> "Tuần"; PeriodKind.MONTH -> "Tháng"; PeriodKind.YEAR -> "Năm" }
private fun periodLabel(kind: PeriodKind, anchor: LocalDate) = when (kind) { PeriodKind.WEEK -> "Tuần ${anchor.dayOfYear / 7 + 1}/${anchor.year}"; PeriodKind.MONTH -> "Tháng ${anchor.monthValue}/${anchor.year}"; PeriodKind.YEAR -> anchor.year.toString() }
fun trendDescription(point: TrendPoint) = "${LocalDate.ofEpochDay(point.epochDay)}: thu ${point.income.minorUnits}, chi ${point.expense.minorUnits}"
fun comparisonText(current: PeriodInsights, previous: PeriodComparison): String {
    val expenseDelta = current.expense.minorUnits - previous.expense.minorUnits
    val direction = when { expenseDelta > 0 -> "tăng"; expenseDelta < 0 -> "giảm"; else -> "không đổi" }
    return "So với kỳ trước, chi tiêu $direction ${amount(kotlin.math.abs(expenseDelta))}."
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BudgetsScreen(onBack: () -> Unit, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectUiState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> viewModel.onAction(BudgetsAction.NotificationCapability(granted)) }
    CollectEffects(viewModel.effects) {
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else viewModel.onAction(BudgetsAction.NotificationCapability(true))
    }
    LaunchedEffect(Unit) {
        viewModel.onAction(BudgetsAction.NotificationCapability(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED))
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ngân sách", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
                    }
                },
                actions = {
                    Surface(
                        onClick = { viewModel.onAction(BudgetsAction.Edit(null)) },
                        shape = CircleShape,
                        color = Color(0x1AFFB800),
                        border = BorderStroke(1.dp, Color(0x4DFFB800)),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, "Thêm ngân sách", tint = Color(0xFFFFB800), modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Month Navigator
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.onAction(BudgetsAction.PreviousMonth) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tháng trước", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        "Tháng ${state.month.monthValue}/${state.month.year}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = { viewModel.onAction(BudgetsAction.NextMonth) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Tháng sau", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Info Card matching Image 2 background
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1D19),
                    border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.BarChart,
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(24.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Theo dõi ngân sách vẫn hoạt động khi không có giao dịch.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "Đặt hạn mức chi tiêu để kiểm soát tài chính tốt hơn.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8E8B85),
                            )
                        }
                    }
                }
            }

            if (!state.notificationGranted) item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1D19),
                    border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Thông báo chưa được bật.", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        TextButton(onClick = { viewModel.onAction(BudgetsAction.EnableNotifications) }) {
                            Text("Bật cảnh báo ngưỡng", color = Color(0xFFFFB800), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            state.errorMessage?.let { item { BillLocketErrorBanner(it) } }

            if (state.budgets.isEmpty()) item {
                BillLocketEmptyState(
                    Icons.Filled.Savings,
                    "Chưa có ngân sách",
                    "Tạo ngân sách để theo dõi chi tiêu trong tháng.",
                    actionLabel = "Tạo ngân sách",
                    onAction = { viewModel.onAction(BudgetsAction.Edit(null)) },
                )
            }

            items(state.budgets, key = { it.id.value }) { budget ->
                val name = budget.categoryId?.let { id -> state.categories.firstOrNull { it.id == id }?.name } ?: "Tổng chi"
                val ratio = if (budget.limit.minorUnits == 0L) 0f else (budget.spent.minorUnits.toFloat() / budget.limit.minorUnits).coerceAtLeast(0f)
                Surface(
                    onClick = { viewModel.onAction(BudgetsAction.Edit(budget)) },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF161513),
                    border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier
                            .padding(16.dp)
                            .semantics { contentDescription = "$name đã dùng ${budget.spent.minorUnits} trên ${budget.limit.minorUnits}" },
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            IconButton(onClick = { viewModel.onAction(BudgetsAction.Delete(budget.id)) }) {
                                Icon(Icons.Filled.Delete, "Xóa ngân sách", tint = Color(0xFF8E8B85), modifier = Modifier.size(20.dp))
                            }
                        }
                        Text(
                            "${amount(budget.spent.minorUnits)} / ${amount(budget.limit.minorUnits)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        LinearProgressIndicator(
                            progress = { ratio.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (ratio >= (budget.warningPercent / 100f)) Color(0xFFFF8A84) else Color(0xFFFFB800),
                            trackColor = Color(0xFF262421),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("${(ratio * 100).toInt()}% đã dùng", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8E8B85))
                            Text("Cảnh báo ở ${budget.warningPercent}%", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8E8B85))
                        }
                    }
                }
            }
        }
    }

    if (state.showEditor) {
        BudgetBottomSheet(state, viewModel::onAction)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetBottomSheet(state: BudgetsState, onAction: (BudgetsAction) -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    var isLimitFocused by remember { mutableStateOf(false) }
    var isWarningFocused by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            keyboardController?.hide()
            focusManager.clearFocus()
            onAction(BudgetsAction.CloseEditor)
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF181714),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color(0xFF44423E), CircleShape),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(scrollState)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Row: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.editing == null) "Thêm ngân sách" else "Sửa ngân sách",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Surface(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onAction(BudgetsAction.CloseEditor)
                    },
                    shape = CircleShape,
                    color = Color(0xFF262421),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = Color(0xFFCCCCCC), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Scope Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Phạm vi",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFCCCCCC),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isOverall = state.categoryId == null
                    item {
                        Surface(
                            onClick = { onAction(BudgetsAction.CategoryChanged(null)) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isOverall) Color(0x26FFB800) else Color(0xFF22201D),
                            border = BorderStroke(1.dp, if (isOverall) Color(0xFFFFB800) else Color(0x1AFFFFFF)),
                            modifier = Modifier.height(38.dp),
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "Tổng chi",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isOverall) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isOverall) Color(0xFFFFB800) else Color(0xFFCCCCCC),
                                )
                            }
                        }
                    }
                    items(state.categories, key = { it.id.value }) { category ->
                        val isSelected = state.categoryId == category.id
                        Surface(
                            onClick = { onAction(BudgetsAction.CategoryChanged(category.id)) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0x26FFB800) else Color(0xFF22201D),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFB800) else Color(0x1AFFFFFF)),
                            modifier = Modifier.height(38.dp),
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    category.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFFFFB800) else Color(0xFFCCCCCC),
                                )
                            }
                        }
                    }
                }
            }

            // Budget Limit Field (Numeric Keyboard Only)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Hạn mức",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isLimitFocused) Color(0xFFFFB800) else Color(0xFFCCCCCC),
                )
                OutlinedTextField(
                    value = state.limitText,
                    onValueChange = { input ->
                        val digits = input.filter(Char::isDigit)
                        onAction(BudgetsAction.LimitChanged(digits))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isLimitFocused = it.isFocused },
                    textStyle = TextStyle(fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.SemiBold),
                    placeholder = { Text("0 đ", color = Color(0xFF75726B), fontSize = 17.sp) },
                    trailingIcon = {
                        if (state.limitText.isNotEmpty()) {
                            IconButton(onClick = { onAction(BudgetsAction.LimitChanged("")) }) {
                                Icon(Icons.Filled.Close, "Xóa", modifier = Modifier.size(18.dp), tint = Color(0xFF8E8B85))
                            }
                        }
                    },
                    suffix = {
                        Text("đ", color = if (isLimitFocused) Color(0xFFFFB800) else Color(0xFF8E8B85), fontWeight = FontWeight.Bold)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1D19),
                        unfocusedContainerColor = Color(0xFF1E1D19),
                        focusedBorderColor = Color(0xFFFFB800),
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        cursorColor = Color(0xFFFFB800),
                    ),
                    singleLine = true,
                )
                Text(
                    text = "Nhập số tiền hạn mức (chỉ nhập số)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8B85),
                )
            }

            // Warning Percentage Field (Numeric Keyboard Only)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Cảnh báo (%)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isWarningFocused) Color(0xFFFFB800) else Color(0xFFCCCCCC),
                )
                OutlinedTextField(
                    value = state.warningText,
                    onValueChange = { input ->
                        val digits = input.filter(Char::isDigit).take(3)
                        onAction(BudgetsAction.WarningChanged(digits))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isWarningFocused = it.isFocused },
                    textStyle = TextStyle(fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.SemiBold),
                    trailingIcon = {
                        if (state.warningText.isNotEmpty()) {
                            IconButton(onClick = { onAction(BudgetsAction.WarningChanged("")) }) {
                                Icon(Icons.Filled.Close, "Xóa", modifier = Modifier.size(18.dp), tint = Color(0xFF8E8B85))
                            }
                        }
                    },
                    suffix = {
                        Text("%", color = if (isWarningFocused) Color(0xFFFFB800) else Color(0xFF8E8B85), fontWeight = FontWeight.Bold)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1D19),
                        unfocusedContainerColor = Color(0xFF1E1D19),
                        focusedBorderColor = Color(0xFFFFB800),
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        cursorColor = Color(0xFFFFB800),
                    ),
                    singleLine = true,
                )
                Text(
                    text = "Nhận thông báo khi chi tiêu đạt đến mức này.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8B85),
                )
            }

            // Action Buttons [ Hủy | Lưu ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onAction(BudgetsAction.CloseEditor)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1D19),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Hủy", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onAction(BudgetsAction.Save)
                    },
                    enabled = !state.isSaving && state.limitText.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB800),
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0x33FFB800),
                        disabledContentColor = Color(0x66000000),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text("Lưu", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
