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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.billlocket.app.domain.model.*
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                    Text("Nhật ký chi tiêu bằng hình ảnh", style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { viewModel.onAction(HomeAction.Search) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Tìm giao dịch")
                }
            }
        }
        item {
            MonthTotals(state, viewModel::onAction)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(state.typeFilter == null, { viewModel.onAction(HomeAction.TypeFilterChanged(null)) }, { Text("Tất cả") }) }
                item { FilterChip(state.typeFilter == TransactionType.EXPENSE, { viewModel.onAction(HomeAction.TypeFilterChanged(TransactionType.EXPENSE)) }, { Text("Chi") }) }
                item { FilterChip(state.typeFilter == TransactionType.INCOME, { viewModel.onAction(HomeAction.TypeFilterChanged(TransactionType.INCOME)) }, { Text("Thu") }) }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tháng trước")
                }
                Text("Tháng ${state.month.monthValue}/${state.month.year}", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { viewModel.onAction(HomeAction.NextMonth) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Tháng sau")
                }
            }
            TextButton(onClick = { viewModel.onAction(HomeAction.Today) }) { Text("Hôm nay") }
        }
        item { CalendarGrid(state, summaries, viewModel::onAction) }
        if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.errorMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LocalDate.ofEpochDay(state.selectedEpochDay).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val isFuture = state.selectedEpochDay > LocalDate.now().toEpochDay()
                FilledIconButton(
                    onClick = { viewModel.onAction(HomeAction.CreateForSelectedDay) },
                    enabled = !isFuture,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Tạo giao dịch cho ngày đã chọn")
                }
            }
        }
        if (state.selectedTransactions.isEmpty() && !state.isLoading) {
            item { Text("Ngày này chưa có giao dịch.", modifier = Modifier.padding(vertical = 20.dp)) }
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
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tổng trong tháng", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onAction(HomeAction.ToggleTotals) }) {
                    Icon(
                        if (state.amountVisibility == AmountVisibility.SHOWN) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (state.amountVisibility == AmountVisibility.SHOWN) "Ẩn số tiền" else "Hiện số tiền",
                    )
                }
            }
            Text("Thu: ${formatAmount(income, state.amountVisibility)}")
            Text("Chi: ${formatAmount(expense, state.amountVisibility)}")
        }
    }
}

@Composable
private fun CalendarGrid(state: HomeState, summaries: Map<Long, CalendarDaySummary>, onAction: (HomeAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
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
                                AsyncImage(it, contentDescription = "Ảnh giao dịch ngày ${date.dayOfMonth}", modifier = Modifier.align(Alignment.BottomCenter).size(38.dp).clip(MaterialTheme.shapes.extraSmall))
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
    ListItem(
        headlineContent = { Text(item.note ?: item.categoryName) },
        supportingContent = { Text("${item.categoryName} • ${item.accountName}") },
        leadingContent = { item.representativeImageUri?.let { AsyncImage(it, null, Modifier.size(52.dp).clip(MaterialTheme.shapes.small)) } },
        trailingContent = { Text(formatAmount(item.amount.minorUnits, visibility)) },
        modifier = Modifier.clickable { onOpen(item.id) },
    )
    HorizontalDivider()
}

private fun formatAmount(value: Long, visibility: AmountVisibility): String = when (visibility) {
    AmountVisibility.SHOWN -> NumberFormat.getNumberInstance(Locale("vi", "VN")).format(value) + " ₫"
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
    CollectEffects(viewModel.effects) { effect -> if (effect is DayGalleryEffect.OpenCapture) onOpenCapture(effect.draftId) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(LocalDate.ofEpochDay(state.epochDay).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
                actions = {
                    val isFuture = state.epochDay > LocalDate.now().toEpochDay()
                    IconButton(
                        onClick = { viewModel.onAction(DayGalleryAction.Create) },
                        enabled = !isFuture,
                    ) { Icon(Icons.Filled.Add, "Tạo giao dịch") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp)) {
            if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.errorMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!state.isLoading && state.items.isEmpty()) item { Text("Ngày này chưa có giao dịch.", Modifier.padding(24.dp)) }
            items(state.items, key = { it.id.value }) { LegacyTransactionRow(it, AmountVisibility.SHOWN, onOpenTransaction) }
        }
    }
}
