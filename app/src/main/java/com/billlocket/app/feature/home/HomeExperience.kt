package com.billlocket.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.billlocket.app.R
import com.billlocket.app.domain.model.*
import com.billlocket.app.locale.BillLocketFormatters
import com.billlocket.app.locale.activeAppLocale
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenDay: (Long) -> Unit,
    onOpenCapture: (DraftId) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onOpenMonthlyRecap: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is HomeEffect.OpenDay -> onOpenDay(effect.epochDay)
            is HomeEffect.OpenCapture -> onOpenCapture(effect.draftId)
            HomeEffect.OpenSearch -> onOpenSearch()
        }
    }
    val summaries = state.days.associateBy(CalendarDaySummary::epochDay)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HomeHeader { viewModel.onAction(HomeAction.Search) } }
        item { MonthTotalsCard(state, viewModel::onAction) }
        item { HomeFilters(state, viewModel::onAction) }
        item { CalendarCard(state, summaries, viewModel::onAction) }
        item {
            val allMonthPhotos = summaries.values.flatMap { it.thumbnailUris }
            HomeMonthlyRecapBanner(
                month = state.month,
                photoCount = allMonthPhotos.size,
                thumbnails = allMonthPhotos.take(2),
                onClick = { onOpenMonthlyRecap(state.month.atDay(1).toEpochDay()) },
            )
        }
        if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape)) }
        state.errorMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item {
            val isFuture = state.selectedEpochDay > LocalDate.now().toEpochDay()
            val hasPhotos = summaries[state.selectedEpochDay]?.thumbnailUris?.isNotEmpty() == true ||
                state.selectedTransactions.any { it.representativeImageUri != null }
            SelectedDayHeader(
                state.selectedEpochDay,
                hasPhotos,
                isFuture,
                { viewModel.onAction(HomeAction.OpenSelectedDay) },
                { viewModel.onAction(HomeAction.CreateForSelectedDay) },
            )
        }
        if (state.selectedTransactions.isEmpty() && !state.isLoading) {
            val isFuture = state.selectedEpochDay > LocalDate.now().toEpochDay()
            item { EmptyDayCard(isFuture) { viewModel.onAction(HomeAction.CreateForSelectedDay) } }
        }
        items(state.selectedTransactions, key = { it.id.value }) { item ->
            TransactionRow(item, state.amountVisibility, onOpenTransaction)
        }
    }
}

@Composable
private fun HomeHeader(onSearch: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("BillLocket", style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(onClick = onSearch, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Search, stringResource(R.string.home_search), Modifier.size(22.dp)) }
        }
    }
}

@Composable
private fun MonthTotalsCard(state: HomeState, onAction: (HomeAction) -> Unit) {
    val income = state.days.sumOf { it.income.minorUnits }
    val expense = state.days.sumOf { it.expense.minorUnits }
    val locale = activeAppLocale()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_month_expense_total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(money(expense, state.amountVisibility, locale), style = MaterialTheme.typography.displaySmall)
                    Text(stringResource(R.string.home_total_spent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(onClick = { onAction(HomeAction.ToggleTotals) }, shape = CircleShape, color = MaterialTheme.colorScheme.background.copy(alpha = 0.65f)) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.amountVisibility == AmountVisibility.SHOWN) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            stringResource(if (state.amountVisibility == AmountVisibility.SHOWN) R.string.home_hide_amount else R.string.home_show_amount),
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TotalPill(stringResource(R.string.home_expense), expense, state.amountVisibility, Icons.Filled.ArrowUpward, Color(0xFFFF716D), Modifier.weight(1f))
                TotalPill(stringResource(R.string.home_income), income, state.amountVisibility, Icons.Filled.ArrowDownward, Color(0xFF66D19E), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TotalPill(label: String, value: Long, visibility: AmountVisibility, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier) {
    val locale = activeAppLocale()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(shape = CircleShape, color = accent.copy(alpha = 0.16f), contentColor = accent) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(17.dp)) }
        }
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(value, visibility, locale), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun HomeFilters(state: HomeState, onAction: (HomeAction) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { BillFilterChip(stringResource(R.string.home_filter_all), state.typeFilter == null) { onAction(HomeAction.TypeFilterChanged(null)) } }
        item { BillFilterChip(stringResource(R.string.home_expense), state.typeFilter == TransactionType.EXPENSE) { onAction(HomeAction.TypeFilterChanged(TransactionType.EXPENSE)) } }
        item { BillFilterChip(stringResource(R.string.home_income), state.typeFilter == TransactionType.INCOME) { onAction(HomeAction.TypeFilterChanged(TransactionType.INCOME)) } }
        items(state.accounts, key = { it.id.value }) { account ->
            BillFilterChip(account.name, state.accountFilter == account.id) { onAction(HomeAction.AccountFilterChanged(account.id.takeUnless { it == state.accountFilter })) }
        }
    }
}

@Composable
private fun BillFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(16.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun CalendarCard(state: HomeState, summaries: Map<Long, CalendarDaySummary>, onAction: (HomeAction) -> Unit) {
    val locale = activeAppLocale()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(BillLocketFormatters.formatMonthYear(state.month, locale), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onAction(HomeAction.Today) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(stringResource(R.string.home_today), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onAction(HomeAction.PreviousMonth) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.home_previous_month), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onAction(HomeAction.NextMonth) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.home_next_month), modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                BillLocketFormatters.weekdayAbbreviations(locale).forEach {
                    Text(
                        it,
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            calendarCells(state.month).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { date ->
                        CalendarCell(
                            date,
                            state.selectedEpochDay,
                            date?.toEpochDay()?.let(summaries::get),
                            onAction,
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(date: LocalDate?, selectedEpochDay: Long, summary: CalendarDaySummary?, onAction: (HomeAction) -> Unit, modifier: Modifier) {
    val selected = date?.toEpochDay() == selectedEpochDay
    val hasPhotos = summary?.thumbnailUris?.isNotEmpty() == true
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .aspectRatio(1.0f)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .then(
                if (date != null) {
                    Modifier.clickable {
                        onAction(HomeAction.DaySelected(date.toEpochDay(), openViewerIfHasPhotos = hasPhotos))
                    }
                } else Modifier
            ),
    ) {
        summary?.thumbnailUris?.firstOrNull()?.let {
            AsyncImage(it, stringResource(R.string.home_day_photo_desc, date?.dayOfMonth ?: 0), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.20f)))
        }
        if (date != null) {
            Text(
                date.dayOfMonth.toString(),
                Modifier.padding(5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (hasPhotos) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            if (summary != null && summary.transactionCount > 1) {
                Surface(
                    Modifier.align(Alignment.BottomEnd).padding(3.dp),
                    CircleShape,
                    MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        "${summary.transactionCount}",
                        Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDayHeader(
    epochDay: Long,
    hasPhotos: Boolean,
    isFuture: Boolean,
    onOpenDay: () -> Unit,
    onAdd: () -> Unit,
) {
    val locale = activeAppLocale()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_transactions_header), style = MaterialTheme.typography.titleLarge)
            Text(BillLocketFormatters.formatDateWithMonthName(LocalDate.ofEpochDay(epochDay), locale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hasPhotos) {
            Surface(
                onClick = onOpenDay,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Filled.Collections, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.home_memories), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            TextButton(onClick = onOpenDay) { Text(stringResource(R.string.home_view_all)) }
        }
        FilledIconButton(
            onClick = onAdd,
            enabled = !isFuture,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            ),
        ) {
            Icon(Icons.Filled.Add, stringResource(R.string.home_create_transaction))
        }
    }
}

@Composable
private fun EmptyDayCard(isFuture: Boolean, onAdd: () -> Unit) {
    Surface(
        onClick = onAdd,
        enabled = !isFuture,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = CircleShape,
                color = if (isFuture) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                contentColor = if (isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary,
            ) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isFuture) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = null,
                    )
                }
            }
            Column {
                Text(
                    text = stringResource(if (isFuture) R.string.home_future_day else R.string.home_no_transactions_for_day),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isFuture) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(if (isFuture) R.string.home_future_day_hint else R.string.home_tap_to_record_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun TransactionRow(item: TransactionSummary, visibility: AmountVisibility, onOpen: (TransactionId) -> Unit) {
    val locale = activeAppLocale()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(item.id) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item.representativeImageUri != null) {
                AsyncImage(item.representativeImageUri, null, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            } else {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Text(item.categoryName.take(1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(item.note?.takeIf { it.isNotBlank() } ?: item.categoryName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text("${item.categoryName} • ${item.accountName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            val amountStr = if (visibility == AmountVisibility.SHOWN) {
                (if (item.type == TransactionType.EXPENSE) "−" else "+") + money(item.amount.minorUnits, visibility, locale)
            } else {
                money(item.amount.minorUnits, visibility, locale)
            }
            Text(
                amountStr,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (item.type == TransactionType.EXPENSE) Color(0xFFFF8A84) else Color(0xFF66D19E),
            )
        }
    }
}

private fun money(
    value: Long,
    visibility: AmountVisibility,
    locale: Locale = Locale.getDefault(),
): String = when (visibility) {
    AmountVisibility.SHOWN -> BillLocketFormatters.formatMoney(value, locale)
    AmountVisibility.HIDDEN -> "••••"
    AmountVisibility.OBSCURED -> "*** ₫"
}

@Composable
fun DayGalleryScreen(
    onBack: () -> Unit,
    onOpenCapture: (DraftId) -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    viewModel: DayGalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    val locale = activeAppLocale()
    CollectEffects(viewModel.effects) { if (it is DayGalleryEffect.OpenCapture) onOpenCapture(it.draftId) }

    val date = LocalDate.ofEpochDay(state.epochDay)
    val dayOfMonth = date.dayOfMonth
    val monthValue = date.monthValue
    val year = date.year
    val isFuture = date.isAfter(LocalDate.now())

    Scaffold(
        containerColor = Color(0xFF0F0E0D),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White,
                ) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.home_close), modifier = Modifier.size(20.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$year",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.60f),
                    )
                    Text(
                        text = BillLocketFormatters.formatDayAndMonth(date, locale),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
                Surface(
                    onClick = { viewModel.onAction(DayGalleryAction.Create) },
                    enabled = !isFuture,
                    shape = CircleShape,
                    color = if (isFuture) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary,
                    contentColor = if (isFuture) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_create_transaction), modifier = Modifier.size(22.dp))
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            state.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            }

            if (!state.isLoading && state.photos.isNotEmpty()) {
                val pagerState = rememberPagerState { state.photos.size }
                val coroutineScope = rememberCoroutineScope()
                val activePhoto = state.photos.getOrNull(pagerState.currentPage) ?: state.photos.first()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 1. Pager Section
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        contentPadding = PaddingValues(horizontal = 30.dp),
                        pageSpacing = 16.dp,
                        key = { state.photos[it].imageUri },
                    ) { page ->
                        val photo = state.photos[page]
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                        val scale = lerp(1f, 0.90f, pageOffset.coerceIn(0f, 1f))
                        val alpha = lerp(1f, 0.60f, pageOffset.coerceIn(0f, 1f))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.0f)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                                .clip(RoundedCornerShape(36.dp))
                                .background(Color(0xFF1E1C1A))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(36.dp))
                                .clickable { onOpenTransaction(photo.transactionId) },
                        ) {
                            AsyncImage(
                                model = photo.imageUri,
                                contentDescription = stringResource(R.string.home_photo_memory),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            if (photo.totalImagesInTransaction > 1) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(14.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.65f),
                                ) {
                                    Text(
                                        text = "${photo.imageIndex + 1}/${photo.totalImagesInTransaction}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // 2. Metadata Section (under photo, matching Locket's timestamp location)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val timeStr = formatInstantTime(activePhoto.occurred, locale)
                        val amountStr = (if (activePhoto.type == TransactionType.EXPENSE) "− " else "+ ") + money(activePhoto.amount.minorUnits, AmountVisibility.SHOWN, locale)
                        val amountColor = if (activePhoto.type == TransactionType.EXPENSE) Color(0xFFFF8A84) else Color(0xFF66D19E)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.70f),
                            )
                            Text("  •  ", color = Color.White.copy(alpha = 0.35f))
                            Text(
                                text = activePhoto.categoryName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text("  •  ", color = Color.White.copy(alpha = 0.35f))
                            Text(
                                text = amountStr,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = amountColor,
                            )
                        }

                        if (!activePhoto.note.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                            ) {
                                Text(
                                    text = "“${activePhoto.note}”",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                    color = Color.White.copy(alpha = 0.90f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.home_tap_photo_for_details),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.40f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // 3. Bottom Thumbnail Strip (if multiple photos)
                    if (state.photos.size > 1) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.10f),
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${state.photos.size}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.70f),
                                    fontWeight = FontWeight.SemiBold,
                                    )
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                itemsIndexed(state.photos, key = { index, item -> "${item.imageUri}_$index" }) { index, photo ->
                                    val isSelected = pagerState.currentPage == index
                                    val size = if (isSelected) 56.dp else 46.dp
                                    val border = if (isSelected) BorderStroke(2.5.dp, Color(0xFFFFB300)) else null

                                    Box(
                                        modifier = Modifier
                                            .size(size)
                                            .clip(RoundedCornerShape(14.dp))
                                            .then(if (border != null) Modifier.border(border, RoundedCornerShape(14.dp)) else Modifier)
                                            .clickable {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(index)
                                                }
                                            },
                                    ) {
                                        AsyncImage(
                                            model = photo.imageUri,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer { alpha = if (isSelected) 1f else 0.5f },
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(40.dp))
                    }
                }
            } else if (!state.isLoading && state.photos.isEmpty()) {
                // Fallback for dates without photos
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.home_no_photos_attached),
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    if (state.items.isEmpty()) {
                        item { EmptyDayCard(isFuture) { viewModel.onAction(DayGalleryAction.Create) } }
                    }
                    items(state.items, key = { it.id.value }) { TransactionRow(it, AmountVisibility.SHOWN, onOpenTransaction) }
                }
            }
        }
    }
}

private fun formatInstantTime(instantAtZone: InstantAtZone, locale: Locale = Locale.getDefault()): String = runCatching {
    val zone = runCatching { ZoneId.of(instantAtZone.zoneId) }.getOrElse { ZoneId.systemDefault() }
    BillLocketFormatters.formatTimeFromEpoch(instantAtZone.epochMillis, zone, locale)
}.getOrElse { "--:--" }

@Composable
private fun HomeMonthlyRecapBanner(
    month: java.time.YearMonth,
    photoCount: Int,
    thumbnails: List<String>,
    onClick: () -> Unit,
) {
    val isCurrentMonth = month == java.time.YearMonth.now()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF191815),
        border = BorderStroke(1.dp, Color(0xFF2A2824)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_recap_banner_title, month.monthValue),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = if (photoCount > 0) stringResource(R.string.home_recap_banner_photos_count, photoCount) else stringResource(R.string.home_recap_banner_no_photos),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA8A39A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(if (isCurrentMonth) R.string.home_recap_banner_interim else R.string.home_recap_banner_full),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF75736D),
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_recap_banner_action),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB800),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFFFFB800),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            if (thumbnails.isNotEmpty()) {
                Box(
                    modifier = Modifier.size(width = 84.dp, height = 72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumbnails.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF24221D),
                            border = BorderStroke(1.dp, Color(0xFF3D3A33)),
                            modifier = Modifier
                                .size(54.dp)
                                .offset(x = (-10).dp, y = (-4).dp),
                        ) {
                            AsyncImage(
                                model = thumbnails[1],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF24221D),
                        border = BorderStroke(1.5.dp, Color(0xFFFFB800).copy(alpha = 0.7f)),
                        modifier = Modifier
                            .size(58.dp)
                            .offset(x = 10.dp, y = 4.dp),
                    ) {
                        AsyncImage(
                            model = thumbnails[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFB800).copy(alpha = 0.15f),
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFFFB800), modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
