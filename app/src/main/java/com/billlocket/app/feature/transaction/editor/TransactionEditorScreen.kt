package com.billlocket.app.feature.transaction.editor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.billlocket.app.domain.model.*
import com.billlocket.app.feature.transaction.draft.DraftImage
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LegacyTransactionEditorScreen(
    onNavigateCapture: (DraftId) -> Unit,
    onNavigateDetail: (TransactionId, Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateLocation: (DraftId, TransactionImageId) -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAction(TransactionEditorAction.RefreshImages)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            TransactionEditorEffect.LaunchDatePicker -> state.occurred?.let { occurred ->
                val date = LocalDate.ofEpochDay(occurred.localDateEpochDay)
                val zone = ZoneId.of(occurred.zoneId)
                val today = LocalDate.now(zone)
                val initialDate = if (date.isAfter(today)) today else date
                val dialog = DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        viewModel.onAction(TransactionEditorAction.DateSelected(LocalDate.of(year, month + 1, day).toEpochDay()))
                    },
                    initialDate.year,
                    initialDate.monthValue - 1,
                    initialDate.dayOfMonth,
                )
                val todayEndMillis = today.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
                dialog.datePicker.maxDate = todayEndMillis
                dialog.show()
            }
            TransactionEditorEffect.LaunchTimePicker -> state.occurred?.let { occurred ->
                val time = Instant.ofEpochMilli(occurred.epochMillis).atZone(ZoneId.of(occurred.zoneId)).toLocalTime()
                TimePickerDialog(
                    context,
                    { _, hour, minute -> viewModel.onAction(TransactionEditorAction.TimeSelected(hour, minute)) },
                    time.hour,
                    time.minute,
                    true,
                ).show()
            }
            is TransactionEditorEffect.NavigateCapture -> onNavigateCapture(effect.draftId)
            is TransactionEditorEffect.NavigateDetail -> onNavigateDetail(effect.transactionId, effect.edited)
            TransactionEditorEffect.NavigateBack -> onNavigateBack()
        }
    }

    BackHandler { viewModel.onAction(TransactionEditorAction.Back) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.sourceTransactionId == null) "Giao dịch mới" else "Chỉnh sửa giao dịch") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onAction(TransactionEditorAction.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
            )
        },
    ) { padding ->
        if (state.fatalError != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(state.fatalError.orEmpty())
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.type == TransactionType.EXPENSE,
                        onClick = { viewModel.onAction(TransactionEditorAction.TypeChanged(TransactionType.EXPENSE)) },
                        label = { Text("Chi") },
                    )
                    FilterChip(
                        selected = state.type == TransactionType.INCOME,
                        onClick = { viewModel.onAction(TransactionEditorAction.TypeChanged(TransactionType.INCOME)) },
                        label = { Text("Thu") },
                    )
                }
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = { viewModel.onAction(TransactionEditorAction.AmountChanged(it)) },
                    label = { Text("Số tiền (${state.currency.value})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = "amount" in state.fieldErrors,
                    supportingText = { state.fieldErrors["amount"]?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Danh mục", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = state.categories.filter { it.type == state.type },
                    selected = state.selectedCategoryId,
                    id = Category::id,
                    label = Category::name,
                    onSelected = { viewModel.onAction(TransactionEditorAction.CategorySelected(it)) },
                )
                state.fieldErrors["category"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Tài khoản", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = state.accounts,
                    selected = state.selectedAccountId,
                    id = Account::id,
                    label = Account::name,
                    onSelected = { viewModel.onAction(TransactionEditorAction.AccountSelected(it)) },
                )
                state.fieldErrors["account"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.onAction(TransactionEditorAction.NoteChanged(it)) },
                    label = { Text("Ghi chú (không bắt buộc)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.occurred?.let { occurred ->
                    val zoned = Instant.ofEpochMilli(occurred.epochMillis).atZone(ZoneId.of(occurred.zoneId))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.onAction(TransactionEditorAction.PickDate) }) {
                            Text(zoned.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                        }
                        OutlinedButton(onClick = { viewModel.onAction(TransactionEditorAction.PickTime) }) {
                            Text(zoned.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ảnh (${state.images.size})", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { viewModel.onAction(TransactionEditorAction.AddImages) }) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Text("Thêm ảnh")
                    }
                }
                EditorImageStrip(
                    state.images,
                    onRemove = { viewModel.onAction(TransactionEditorAction.RemoveImage(it)) },
                    onMove = { from, to -> viewModel.onAction(TransactionEditorAction.MoveImage(from, to)) },
                    onLocation = { onNavigateLocation(state.draftId, TransactionImageId(it)) },
                )
                state.fieldErrors["images"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.fieldErrors["save"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { viewModel.onAction(TransactionEditorAction.Save) },
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Lưu giao dịch")
                }
            }
        }
    }

    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(TransactionEditorAction.KeepEditing) },
            title = { Text("Bỏ thay đổi?") },
            text = { Text("Các thay đổi chưa lưu sẽ không được áp dụng.") },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(TransactionEditorAction.Discard) }) {
                    Text("Bỏ thay đổi", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(TransactionEditorAction.KeepEditing) }) {
                    Text("Tiếp tục chỉnh sửa")
                }
            },
        )
    }
}

@Composable
private fun <T, ID> ChoiceRow(
    values: List<T>,
    selected: ID?,
    id: (T) -> ID,
    label: (T) -> String,
    onSelected: (ID) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values.size) { index ->
            val value = values[index]
            FilterChip(
                selected = id(value) == selected,
                onClick = { onSelected(id(value)) },
                label = { Text(label(value)) },
            )
        }
    }
}

@Composable
private fun EditorImageStrip(
    images: List<DraftImage>,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onLocation: (String) -> Unit,
) {
    if (images.isEmpty()) {
        Text("Giao dịch này chưa có ảnh.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(images, key = { _, image -> image.id }) { index, image ->
            Card(modifier = Modifier.width(128.dp)) {
                AsyncImage(
                    model = image.previewUri,
                    contentDescription = "Ảnh giao dịch ${index + 1}",
                    modifier = Modifier.fillMaxWidth().height(88.dp),
                )
                if (image.isMissing) Text("Ảnh bị thiếu", color = MaterialTheme.colorScheme.error)
                else if (image.locationCandidate != null) Text("Có gợi ý vị trí", style = MaterialTheme.typography.labelSmall)
                Row {
                    IconButton(onClick = { onLocation(image.id) }) {
                        Icon(Icons.Filled.LocationOn, contentDescription = "Chỉnh vị trí ảnh")
                    }
                    IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Đưa ảnh sang trái")
                    }
                    IconButton(onClick = { onRemove(image.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Xóa ảnh")
                    }
                    IconButton(onClick = { onMove(index, index + 1) }, enabled = index < images.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Đưa ảnh sang phải")
                    }
                }
            }
        }
    }
}
