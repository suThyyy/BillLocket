package com.billlocket.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.billlocket.app.R
import com.billlocket.app.domain.model.*
import com.billlocket.app.locale.BillLocketFormatters
import com.billlocket.app.locale.activeAppLocale
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import java.time.LocalDate
import java.util.Locale

@Composable
private fun localizeHomeError(message: String?): String? = when (message) {
    "Không thể tải lịch" -> stringResource(R.string.home_error_load_calendar)
    "Không thể thêm giao dịch cho ngày tương lai." -> stringResource(R.string.home_error_future_date)
    "Không thể tải giao dịch trong ngày" -> stringResource(R.string.home_error_load_day_tx)
    "Không thể tải giao dịch" -> stringResource(R.string.home_error_load_tx)
    else -> message
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LegacyHomeScreen(
    onOpenDay: (Long) -> Unit,
    onOpenCapture: (DraftId) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    val locale = activeAppLocale()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is HomeEffect.OpenDay -> onOpenDay(effect.epochDay)
            is HomeEffect.OpenCapture -> onOpenCapture(effect.draftId)
            HomeEffect.OpenSearch -> onOpenSearch()
        }
    }
    val summaries = state.days.associateBy(CalendarDaySummary::epochDay)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BillLocket", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { viewModel.onAction(HomeAction.Search) }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.home_search))
                }
            }
        }
        item {
            MonthTotals(state, viewModel::onAction)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(state.typeFilter == null, { viewModel.onAction(HomeAction.TypeFilterChanged(null)) }, { Text(stringResource(R.string.home_filter_all)) }) }
                item { FilterChip(state.typeFilter == TransactionType.EXPENSE, { viewModel.onAction(HomeAction.TypeFilterChanged(TransactionType.EXPENSE)) }, { Text(stringResource(R.string.home_expense)) }) }
                item { FilterChip(state.typeFilter == TransactionType.INCOME, { viewModel.onAction(HomeAction.TypeFilterChanged(TransactionType.INCOME)) }, { Text(stringResource(R.string.home_income)) }) }
                items(state.accounts, key = { it.id.value }) { account ->
                    FilterChip(
                        selected = state.accountFilter == account.id,
                        onClick = { viewModel.onAction(HomeAction.AccountFilterChanged(account.id.takeUnless { it == state.accountFilter })) },
                        label = { Text(account.name) },
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { viewModel.onAction(HomeAction.PreviousMonth) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.home_previous_month))
                }
                Text(BillLocketFormatters.formatMonthYear(state.month, locale), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { viewModel.onAction(HomeAction.NextMonth) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.home_next_month))
                }
            }
            TextButton(onClick = { viewModel.onAction(HomeAction.Today) }) { Text(stringResource(R.string.home_today)) }
        }
        item { CalendarGrid(state, summaries, viewModel::onAction) }
        if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.errorMessage?.let { message ->
            item {
                val localized = localizeHomeError(message)
                if (localized != null) {
                    Text(localized, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    BillLocketFormatters.formatDate(LocalDate.ofEpochDay(state.selectedEpochDay), locale),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val isFuture = state.selectedEpochDay > LocalDate.now().toEpochDay()
                FilledIconButton(
                    onClick = { viewModel.onAction(HomeAction.CreateForSelectedDay) },
                    enabled = !isFuture,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_create_for_selected_day))
                }
            }
        }
        if (state.selectedTransactions.isEmpty() && !state.isLoading) {
            item { Text(stringResource(R.string.home_no_transactions_for_day), modifier = Modifier.padding(vertical = 20.dp)) }
        }
        items(state.selectedTransactions, key = { it.id.value }) { item ->
            TransactionRow(item, state.amountVisibility, onOpenTransaction)
        }
    }
}

@Composable
private fun MonthTotals(state: HomeState, onAction: (HomeAction) -> Unit) {
    val income = state.days.sumOf { it.income.minorUnits }
    val expense = state.days.sumOf { it.expense.minorUnits }
    val locale = activeAppLocale()
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_month_summary_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onAction(HomeAction.ToggleTotals) }) {
                    Icon(
                        if (state.amountVisibility == AmountVisibility.SHOWN) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = stringResource(if (state.amountVisibility == AmountVisibility.SHOWN) R.string.home_hide_amount else R.string.home_show_amount),
                    )
                }
            }
            Text("${stringResource(R.string.home_income)}: ${formatAmount(income, state.amountVisibility, locale)}")
            Text("${stringResource(R.string.home_expense)}: ${formatAmount(expense, state.amountVisibility, locale)}")
        }
    }
}

@Composable
private fun CalendarGrid(state: HomeState, summaries: Map<Long, CalendarDaySummary>, onAction: (HomeAction) -> Unit) {
    val locale = activeAppLocale()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            BillLocketFormatters.weekdayAbbreviations(locale).forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        calendarCells(state.month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { date ->
                    val selected = date?.toEpochDay() == state.selectedEpochDay
                    val summary = date?.toEpochDay()?.let(summaries::get)
                    Box(
                        modifier = Modifier.weight(1f).height(68.dp).clip(MaterialTheme.shapes.small)
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .then(if (date != null) Modifier.clickable { onAction(HomeAction.DaySelected(date.toEpochDay())) } else Modifier)
                            .padding(4.dp),
                    ) {
                        if (date != null) {
                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
                            summary?.thumbnailUris?.firstOrNull()?.let {
                                AsyncImage(it, contentDescription = stringResource(R.string.home_day_photo_desc, date.dayOfMonth), modifier = Modifier.align(Alignment.BottomCenter).size(38.dp).clip(MaterialTheme.shapes.extraSmall))
                            }
                            if (summary != null && summary.thumbnailUris.isEmpty()) {
                                Text(summary.transactionCount.toString(), modifier = Modifier.align(Alignment.BottomEnd), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyTransactionRow(item: TransactionSummary, visibility: AmountVisibility, onOpen: (TransactionId) -> Unit) {
    val locale = activeAppLocale()
    ListItem(
        headlineContent = { Text(item.note ?: item.categoryName) },
        supportingContent = { Text("${item.categoryName} • ${item.accountName}") },
        leadingContent = { item.representativeImageUri?.let { AsyncImage(it, null, Modifier.size(52.dp).clip(MaterialTheme.shapes.small)) } },
        trailingContent = { Text(formatAmount(item.amount.minorUnits, visibility, locale)) },
        modifier = Modifier.clickable { onOpen(item.id) },
    )
    HorizontalDivider()
}

private fun formatAmount(value: Long, visibility: AmountVisibility, locale: Locale): String = when (visibility) {
    AmountVisibility.SHOWN -> BillLocketFormatters.formatMoney(value, locale)
    AmountVisibility.HIDDEN -> "••••"
    AmountVisibility.OBSCURED -> "*** ₫"
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LegacyDayGalleryScreen(
    onBack: () -> Unit,
    onOpenCapture: (DraftId) -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    viewModel: DayGalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    val locale = activeAppLocale()
    CollectEffects(viewModel.effects) { effect -> if (effect is DayGalleryEffect.OpenCapture) onOpenCapture(effect.draftId) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(BillLocketFormatters.formatDate(LocalDate.ofEpochDay(state.epochDay), locale)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    val isFuture = state.epochDay > LocalDate.now().toEpochDay()
                    IconButton(
                        onClick = { viewModel.onAction(DayGalleryAction.Create) },
                        enabled = !isFuture,
                    ) { Icon(Icons.Filled.Add, stringResource(R.string.home_create_transaction)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp)) {
            if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.errorMessage?.let { msg ->
                item {
                    val localized = localizeHomeError(msg)
                    if (localized != null) {
                        Text(localized, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (!state.isLoading && state.items.isEmpty()) item { Text(stringResource(R.string.home_no_transactions_for_day), Modifier.padding(24.dp)) }
            items(state.items, key = { it.id.value }) { LegacyTransactionRow(it, AmountVisibility.SHOWN, onOpenTransaction) }
        }
    }
}
