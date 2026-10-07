package com.billlocket.app.feature.transaction.search

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.billlocket.app.domain.model.*
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import com.billlocket.app.ui.common.BillLocketEmptyState
import com.billlocket.app.ui.common.BillLocketErrorBanner
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TransactionSearchScreen(
    onBack: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    viewModel: TransactionSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is TransactionSearchEffect.LaunchDatePicker -> {
                val selected = if (effect.start) state.startEpochDay else state.endEpochDay
                val date = selected?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        viewModel.onAction(
                            TransactionSearchAction.DateChanged(effect.start, LocalDate.of(year, month + 1, day).toEpochDay()),
                        )
                    },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth,
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tìm giao dịch") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus(); keyboard?.hide() } },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.onAction(TransactionSearchAction.QueryChanged(it)) },
                label = { Text("Tìm trong ghi chú") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = state.type == null,
                        onClick = { viewModel.onAction(TransactionSearchAction.TypeChanged(null)) },
                        label = { Text("Tất cả") },
                    )
                }
                item {
                    FilterChip(
                        selected = state.type == TransactionType.EXPENSE,
                        onClick = { viewModel.onAction(TransactionSearchAction.TypeChanged(TransactionType.EXPENSE)) },
                        label = { Text("Chi") },
                    )
                }
                item {
                    FilterChip(
                        selected = state.type == TransactionType.INCOME,
                        onClick = { viewModel.onAction(TransactionSearchAction.TypeChanged(TransactionType.INCOME)) },
                        label = { Text("Thu") },
                    )
                }
                items(state.accounts, key = { it.id.value }) { account ->
                    FilterChip(
                        selected = state.accountId == account.id,
                        onClick = {
                            viewModel.onAction(TransactionSearchAction.AccountChanged(account.id.takeUnless { it == state.accountId }))
                        },
                        label = { Text(account.name) },
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val compatible = state.categories.filter { state.type == null || it.type == state.type }
                items(compatible, key = { it.id.value }) { category ->
                    FilterChip(
                        selected = state.categoryId == category.id,
                        onClick = {
                            viewModel.onAction(TransactionSearchAction.CategoryChanged(category.id.takeUnless { it == state.categoryId }))
                        },
                        label = { Text(category.name) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.onAction(TransactionSearchAction.PickStartDate) }) {
                    Text(state.startEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "Từ ngày")
                }
                OutlinedButton(onClick = { viewModel.onAction(TransactionSearchAction.PickEndDate) }) {
                    Text(state.endEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "Đến ngày")
                }
                if (state.startEpochDay != null || state.endEpochDay != null) {
                    TextButton(onClick = {
                        viewModel.onAction(TransactionSearchAction.DateChanged(true, null))
                        viewModel.onAction(TransactionSearchAction.DateChanged(false, null))
                    }) { Text("Xóa lọc") }
                }
            }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.errorMessage?.let { BillLocketErrorBanner(it) }
            if (!state.isLoading && state.results.isEmpty()) {
                BillLocketEmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Không tìm thấy giao dịch", "Thử đổi từ khóa, khoảng ngày hoặc bộ lọc.")
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.results, key = { it.id.value }) { result ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenTransaction(result.id) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (result.representativeImageUri != null) {
                                AsyncImage(result.representativeImageUri, "Ảnh ${result.categoryName}", Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                            } else {
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), contentColor = MaterialTheme.colorScheme.primary) {
                                    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) { Text(result.categoryName.take(1), style = MaterialTheme.typography.titleLarge) }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(result.note ?: result.categoryName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text("${result.categoryName} • ${result.accountName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(NumberFormat.getNumberInstance(Locale("vi", "VN")).format(result.amount.minorUnits) + " ₫", style = MaterialTheme.typography.labelLarge, color = if (result.type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color(0xFF66D19E))
                        }
                    }
                }
            }
        }
    }
}
