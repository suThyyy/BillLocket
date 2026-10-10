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
import com.billlocket.app.R
import com.billlocket.app.domain.model.*
import com.billlocket.app.feature.transaction.draft.DraftImage
import com.billlocket.app.locale.BillLocketFormatters
import com.billlocket.app.locale.activeAppLocale
import com.billlocket.app.ui.common.CollectEffects
import com.billlocket.app.ui.common.collectUiState
import androidx.compose.ui.res.stringResource
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
                title = { Text(if (state.sourceTransactionId == null) stringResource(R.string.editor_title_new) else stringResource(R.string.editor_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onAction(TransactionEditorAction.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val locale = activeAppLocale()
        if (state.fatalError != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                val errorMsg = if (state.fatalError == "Không tìm thấy giao dịch") stringResource(R.string.destination_not_found) else state.fatalError.orEmpty()
                Text(errorMsg)
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
                        label = { Text(stringResource(R.string.home_expense)) },
                    )
                    FilterChip(
                        selected = state.type == TransactionType.INCOME,
                        onClick = { viewModel.onAction(TransactionEditorAction.TypeChanged(TransactionType.INCOME)) },
                        label = { Text(stringResource(R.string.home_income)) },
                    )
                }
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = { viewModel.onAction(TransactionEditorAction.AmountChanged(it)) },
                    label = { Text("${stringResource(R.string.transaction_amount_label)} (${state.currency.value})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = "amount" in state.fieldErrors,
                    supportingText = { state.fieldErrors["amount"]?.let { msg -> localizeEditorError(msg)?.let { Text(it) } } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.transaction_category_label), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = state.categories.filter { it.type == state.type },
                    selected = state.selectedCategoryId,
                    id = Category::id,
                    label = Category::name,
                    onSelected = { viewModel.onAction(TransactionEditorAction.CategorySelected(it)) },
                )
                state.fieldErrors["category"]?.let { msg -> localizeEditorError(msg)?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                Text(stringResource(R.string.transaction_account_label), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = state.accounts,
                    selected = state.selectedAccountId,
                    id = Account::id,
                    label = Account::name,
                    onSelected = { viewModel.onAction(TransactionEditorAction.AccountSelected(it)) },
                )
                state.fieldErrors["account"]?.let { msg -> localizeEditorError(msg)?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.onAction(TransactionEditorAction.NoteChanged(it)) },
                    label = { Text(stringResource(R.string.transaction_note_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.occurred?.let { occurred ->
                    val zoned = Instant.ofEpochMilli(occurred.epochMillis).atZone(ZoneId.of(occurred.zoneId))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.onAction(TransactionEditorAction.PickDate) }) {
                            Text(BillLocketFormatters.formatDate(zoned.toLocalDate(), locale))
                        }
                        OutlinedButton(onClick = { viewModel.onAction(TransactionEditorAction.PickTime) }) {
                            Text(BillLocketFormatters.formatTime(zoned.toLocalTime(), locale))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.detail_photos_count, state.images.size), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { viewModel.onAction(TransactionEditorAction.AddImages) }) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Text(stringResource(R.string.editor_add_photo))
                    }
                }
                EditorImageStrip(
                    state.images,
                    onRemove = { viewModel.onAction(TransactionEditorAction.RemoveImage(it)) },
                    onMove = { from, to -> viewModel.onAction(TransactionEditorAction.MoveImage(from, to)) },
                    onLocation = { onNavigateLocation(state.draftId, TransactionImageId(it)) },
                )
                state.fieldErrors["images"]?.let { msg ->
                    localizeEditorError(msg)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                state.fieldErrors["save"]?.let { msg ->
                    localizeEditorError(msg)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                Button(
                    onClick = { viewModel.onAction(TransactionEditorAction.Save) },
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.editor_save_transaction))
                }
            }
        }
    }

    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(TransactionEditorAction.KeepEditing) },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(TransactionEditorAction.Discard) }) {
                    Text(stringResource(R.string.editor_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(TransactionEditorAction.KeepEditing) }) {
                    Text(stringResource(R.string.editor_continue_editing))
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
        Text(stringResource(R.string.home_no_photos_attached), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(images, key = { _, image -> image.id }) { index, image ->
            Card(modifier = Modifier.width(128.dp)) {
                AsyncImage(
                    model = image.previewUri,
                    contentDescription = stringResource(R.string.capture_photo_desc, index + 1),
                    modifier = Modifier.fillMaxWidth().height(88.dp),
                )
                if (image.isMissing) Text(stringResource(R.string.editor_missing_image), color = MaterialTheme.colorScheme.error)
                else if (image.locationCandidate != null) Text(stringResource(R.string.capture_has_location_hint), style = MaterialTheme.typography.labelSmall)
                Row {
                    IconButton(onClick = { onLocation(image.id) }) {
                        Icon(Icons.Filled.LocationOn, contentDescription = stringResource(R.string.editor_location_view))
                    }
                    IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.capture_move_left))
                    }
                    IconButton(onClick = { onRemove(image.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.capture_delete_photo))
                    }
                    IconButton(onClick = { onMove(index, index + 1) }, enabled = index < images.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.capture_move_right))
                    }
                }
            }
        }
    }
}

@Composable
private fun localizeEditorError(error: String?): String? = when (error) {
    "Số tiền phải lớn hơn 0" -> stringResource(R.string.editor_error_amount)
    "Hãy chọn tài khoản" -> stringResource(R.string.editor_error_account)
    "Hãy chọn danh mục phù hợp" -> stringResource(R.string.editor_error_category)
    "Ngày giờ không hợp lệ" -> stringResource(R.string.editor_error_date)
    "Ngày giao dịch không thể lớn hơn hôm nay." -> stringResource(R.string.editor_error_future_date)
    "Hãy xóa ảnh bị thiếu trước khi lưu" -> stringResource(R.string.editor_error_missing_images)
    "Không thể lưu giao dịch. Vui lòng thử lại." -> stringResource(R.string.editor_error_save_failed)
    else -> error
}
