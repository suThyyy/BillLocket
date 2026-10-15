package com.billlocket.app.feature.transaction.editor

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.billlocket.app.feature.transaction.voice.*
import com.billlocket.app.feature.transaction.ai.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun TransactionEditorScreen(
    onNavigateCapture: (DraftId) -> Unit,
    onNavigateDetail: (TransactionId, Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateLocation: (DraftId, TransactionImageId) -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectUiState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Voice dictation coordinator (BL-046)
    val coroutineScope = rememberCoroutineScope()
    val transcriber = remember(context) { AndroidSpeechRecognizerTranscriber(context) }
    val voiceCoordinator = remember(transcriber, coroutineScope) { VoiceNoteCoordinator(transcriber, coroutineScope) }
    val voiceState by voiceCoordinator.state.collectAsState()
    val appLocale = activeAppLocale()
    val speechLocaleTag = remember(appLocale) { VoiceLocaleMapper.resolveLocaleTag(appLocale) }
    val useSpeechActivityFallback = remember {
        Build.MANUFACTURER.equals("realme", ignoreCase = true) ||
            Build.MANUFACTURER.equals("oppo", ignoreCase = true) ||
            Build.MANUFACTURER.equals("oneplus", ignoreCase = true)
    }

    val speechActivityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognizedText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (recognizedText.isNotEmpty()) {
                val mergedNote = VoiceNoteTextMerger.merge(state.note, recognizedText)
                viewModel.onAction(TransactionEditorAction.NoteChanged(mergedNote))
            }
        }
    }

    var showPermissionRationale by remember { mutableStateOf(false) }
    var hasRequestedPermissionOnce by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRequestedPermissionOnce = true
        val activity = context as? Activity
        val permanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        voiceCoordinator.onPermissionResult(granted, permanentlyDenied, speechLocaleTag)
    }

    DisposableEffect(voiceCoordinator) {
        onDispose {
            voiceCoordinator.close()
        }
    }

    // Automatically merge recognized speech into draft note upon completion
    LaunchedEffect(voiceState) {
        val currentVoiceState = voiceState
        if (currentVoiceState is VoiceNoteState.Completed) {
            val mergedNote = VoiceNoteTextMerger.merge(state.note, currentVoiceState.text)
            viewModel.onAction(TransactionEditorAction.NoteChanged(mergedNote))
            voiceCoordinator.dismissCompleted()
        }
    }

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
                        val selectedDate = LocalDate.of(year, month + 1, day)
                        viewModel.onAction(TransactionEditorAction.DateSelected(selectedDate.toEpochDay()))
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
                TimePickerDialog(context, { _, hour, minute ->
                    viewModel.onAction(TransactionEditorAction.TimeSelected(hour, minute))
                }, time.hour, time.minute, true).show()
            }
            is TransactionEditorEffect.NavigateCapture -> onNavigateCapture(effect.draftId)
            is TransactionEditorEffect.NavigateDetail -> onNavigateDetail(effect.transactionId, effect.edited)
            TransactionEditorEffect.NavigateBack -> onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val isVoiceActive = voiceState is VoiceNoteState.Listening || voiceState is VoiceNoteState.Processing
    BackHandler(enabled = isVoiceActive) { voiceCoordinator.cancel() }

    val imeVisible = WindowInsets.isImeVisible
    BackHandler(enabled = !imeVisible && !isVoiceActive) { viewModel.onAction(TransactionEditorAction.Back) }

    Scaffold(containerColor = Color(0xFF0F0E0D)) { padding ->
        if (state.fatalError != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                val errorMsg = if (state.fatalError == "Không tìm thấy giao dịch") stringResource(R.string.destination_not_found) else state.fatalError.orEmpty()
                Text(errorMsg, color = Color.White)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Minimal Header
                EditorHeader(
                    isEditing = state.sourceTransactionId != null,
                    onBack = { viewModel.onAction(TransactionEditorAction.Back) },
                )

                // Middle Content (Fits zero-scroll, scrollable if screen is extremely small)
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val currentCategory = state.categories.firstOrNull { it.id == state.selectedCategoryId }
                    val currentAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }

                    // Category semantic visual tint
                    val baseTint = if (currentCategory != null && currentCategory.colorArgb != 0L) {
                        Color(currentCategory.colorArgb)
                    } else {
                        if (state.type == TransactionType.EXPENSE) MaterialTheme.colorScheme.primary else Color(0xFF4ECDC4)
                    }
                    val animatedTint by animateColorAsState(targetValue = baseTint, animationSpec = tween(350), label = "heroTint")

                    // 1. Hero Card: Category icon/photo, large formatted amount, inline note
                    HeroCard(
                        state = state,
                        category = currentCategory,
                        animatedTint = animatedTint,
                        focusRequester = focusRequester,
                        voiceState = voiceState,
                        onStartVoice = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                            if (useSpeechActivityFallback) {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                    )
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocaleTag)
                                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                }
                                speechActivityLauncher.launch(intent)
                            } else {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                                val activity = context as? Activity
                                val permanentlyDenied = !hasPermission && activity != null &&
                                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO) &&
                                    hasRequestedPermissionOnce

                                if (!hasPermission && !permanentlyDenied) {
                                    showPermissionRationale = true
                                } else {
                                    voiceCoordinator.onMicTapped(
                                        hasPermission = hasPermission,
                                        permanentlyDenied = permanentlyDenied,
                                        localeTag = speechLocaleTag,
                                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                    )
                                }
                            }
                        },
                        onStopVoice = { voiceCoordinator.stop() },
                        onCancelVoice = { voiceCoordinator.cancel() },
                        onDismissVoiceError = { voiceCoordinator.dismissError() },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        onAction = viewModel::onAction,
                        onAddPhoto = { viewModel.onAction(TransactionEditorAction.AddImages) },
                        onDone = {
                            if (state.canSave) {
                                keyboard?.hide()
                                focusManager.clearFocus()
                                viewModel.onAction(TransactionEditorAction.Save)
                            }
                        },
                    )

                    // 2. Selectors: Category & Account dropdown pills
                    CategoryAndAccountSelectors(
                        state = state,
                        currentCategory = currentCategory,
                        currentAccount = currentAccount,
                        animatedTint = animatedTint,
                        onCategorySelected = { viewModel.onAction(TransactionEditorAction.CategorySelected(it)) },
                        onAccountSelected = { viewModel.onAction(TransactionEditorAction.AccountSelected(it)) },
                    )

                    // 2b. Optional AI Category Suggestion (BL-047)
                    CategorySuggestionCard(
                        suggestionState = state.suggestionState,
                        categories = state.categories,
                        onApply = { viewModel.onAction(TransactionEditorAction.ApplyCategorySuggestion) },
                        onDismiss = { viewModel.onAction(TransactionEditorAction.DismissCategorySuggestion) },
                    )

                    // 3. Segmented Type Toggle & Date Selector Pill
                    TypeAndDateSelectors(
                        state = state,
                        onTypeChanged = { viewModel.onAction(TransactionEditorAction.TypeChanged(it)) },
                        onPickDate = { viewModel.onAction(TransactionEditorAction.PickDate) },
                    )

                    // 4. Compact photo strip (only if staged images exist)
                    if (state.images.isNotEmpty()) {
                        CompactPhotoStrip(
                            images = state.images,
                            onAdd = { viewModel.onAction(TransactionEditorAction.AddImages) },
                            onRemove = { viewModel.onAction(TransactionEditorAction.RemoveImage(it)) },
                            onLocation = { onNavigateLocation(state.draftId, TransactionImageId(it)) },
                        )
                    }

                    // Field error banner if any
                    state.fieldErrors.values.firstOrNull()?.let {
                        val message = when (it) {
                            "Hãy chọn tài khoản" -> stringResource(R.string.editor_error_account)
                            "Hãy chọn danh mục phù hợp" -> stringResource(R.string.editor_error_category)
                            "Ngày giao dịch không thể lớn hơn hôm nay." -> stringResource(R.string.editor_error_future_date)
                            "Không thể lưu giao dịch. Vui lòng thử lại." -> stringResource(R.string.editor_error_save_failed)
                            "Số tiền phải lớn hơn 0" -> stringResource(R.string.editor_error_amount)
                            "Ngày giờ không hợp lệ" -> stringResource(R.string.editor_error_date)
                            "Hãy xóa ảnh bị thiếu trước khi lưu" -> stringResource(R.string.editor_error_missing_images)
                            else -> it
                        }
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                // Bottom Action Row: Cancel and Dominant Save Button
                BottomActionRow(
                    canSave = state.canSave,
                    isSaving = state.isSaving,
                    onCancel = { viewModel.onAction(TransactionEditorAction.Back) },
                    onSave = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        viewModel.onAction(TransactionEditorAction.Save)
                    },
                )
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

    if (showPermissionRationale) {
        VoicePermissionRationaleDialog(
            onContinue = {
                showPermissionRationale = false
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = {
                showPermissionRationale = false
            },
        )
    }
}

@Composable
private fun EditorHeader(isEditing: Boolean, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
        ) {
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(18.dp))
            }
        }
        Text(
            if (isEditing) stringResource(R.string.action_edit) else stringResource(R.string.editor_title_new),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Spacer(Modifier.size(38.dp))
    }
}

@Composable
private fun HeroCard(
    state: TransactionEditorState,
    category: Category?,
    animatedTint: Color,
    focusRequester: FocusRequester,
    voiceState: VoiceNoteState,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onDismissVoiceError: () -> Unit,
    onOpenSettings: () -> Unit,
    onAction: (TransactionEditorAction) -> Unit,
    onAddPhoto: () -> Unit,
    onDone: () -> Unit,
) {
    val isExpense = state.type == TransactionType.EXPENSE
    val sign = if (isExpense) "−" else "+"
    val signColor = if (isExpense) Color(0xFFFF6B6B) else Color(0xFF4ECDC4)
    val keyboard = LocalSoftwareKeyboardController.current

    var amountTextFieldValue by remember {
        val initialShouldSelect = state.sourceTransactionId != null && state.amountText.isNotEmpty()
        mutableStateOf(
            TextFieldValue(
                text = state.amountText,
                selection = if (initialShouldSelect) TextRange(0, state.amountText.length) else TextRange(state.amountText.length),
            ),
        )
    }
    var hasAutoSelectedOnFirstFocus by rememberSaveable(state.draftId.value) {
        mutableStateOf(false)
    }

    LaunchedEffect(state.amountText) {
        if (state.amountText != amountTextFieldValue.text) {
            val shouldSelectAll = !hasAutoSelectedOnFirstFocus && state.sourceTransactionId != null && state.amountText.isNotEmpty()
            if (shouldSelectAll) {
                hasAutoSelectedOnFirstFocus = true
            }
            amountTextFieldValue = TextFieldValue(
                text = state.amountText,
                selection = if (shouldSelectAll) {
                    TextRange(0, state.amountText.length)
                } else {
                    TextRange(state.amountText.length)
                },
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF181615),
        border = BorderStroke(1.dp, animatedTint.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            animatedTint.copy(alpha = 0.22f),
                            animatedTint.copy(alpha = 0.04f),
                        ),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Top: Category Icon / Photo thumbnail & Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.images.isNotEmpty()) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, animatedTint, RoundedCornerShape(14.dp))
                                .clickable(onClick = onAddPhoto),
                        ) {
                            AsyncImage(
                                state.images.first().previewUri,
                                null,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                shape = RoundedCornerShape(topStart = 8.dp),
                                color = Color.Black.copy(alpha = 0.7f),
                            ) {
                                Text(
                                    "${state.images.size}",
                                    Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = animatedTint.copy(alpha = 0.25f),
                            contentColor = animatedTint,
                            modifier = Modifier.size(46.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    categoryIcon(category?.name.orEmpty(), category?.iconKey),
                                    null,
                                    Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                    Text(
                        category?.name ?: (if (isExpense) stringResource(R.string.editor_type_expense) else stringResource(R.string.editor_type_income)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }

                // Center: VERY LARGE HERO AMOUNT (Dominant visual element, live formatted with seamless first-focus replacement)
                val editorLocale = activeAppLocale()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "$sign ",
                            style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
                            color = signColor,
                        )
                        BasicTextField(
                            value = amountTextFieldValue,
                            onValueChange = { newValue ->
                                val digits = newValue.text.filter(Char::isDigit)
                                if (digits.length <= 15) {
                                    val adjustedSelection = if (digits == newValue.text) {
                                        newValue.selection
                                    } else {
                                        TextRange(digits.length)
                                    }
                                    amountTextFieldValue = newValue.copy(text = digits, selection = adjustedSelection)
                                    hasAutoSelectedOnFirstFocus = true
                                    if (digits != state.amountText) {
                                        onAction(TransactionEditorAction.AmountChanged(digits))
                                    }
                                }
                            },
                            modifier = Modifier
                                .widthIn(min = 36.dp)
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && !hasAutoSelectedOnFirstFocus) {
                                        if (state.sourceTransactionId != null && amountTextFieldValue.text.isNotEmpty()) {
                                            hasAutoSelectedOnFirstFocus = true
                                            amountTextFieldValue = amountTextFieldValue.copy(
                                                selection = TextRange(0, amountTextFieldValue.text.length),
                                            )
                                        }
                                    }
                                },
                            textStyle = TextStyle(
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (amountTextFieldValue.text.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.35f),
                                textAlign = TextAlign.Center,
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { onDone() }),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = remember(editorLocale) { VndAmountVisualTransformation(editorLocale) },
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.Center) {
                                    if (amountTextFieldValue.text.isEmpty()) {
                                        Text(
                                            text = "0",
                                            style = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.Bold),
                                            color = Color.White.copy(alpha = 0.35f),
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "₫",
                            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Bottom: Inline Note Input Capsule with Voice Dictation (BL-046)
                when (val vs = voiceState) {
                    is VoiceNoteState.Listening -> {
                        VoiceListeningCapsule(
                            partialText = vs.partialText,
                            phase = vs.phase,
                            inputLevel = vs.inputLevel,
                            onStop = onStopVoice,
                            onCancel = onCancelVoice,
                        )
                    }
                    VoiceNoteState.Processing -> {
                        VoiceProcessingCapsule(
                            onCancel = onCancelVoice,
                        )
                    }
                    else -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                                .padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                null,
                                Modifier.size(15.dp),
                                tint = Color.White.copy(alpha = 0.6f),
                            )
                            BasicTextField(
                                value = state.note,
                                onValueChange = { onAction(TransactionEditorAction.NoteChanged(it)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { onDone() }),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (state.note.isEmpty()) {
                                            Text(
                                                stringResource(R.string.transaction_note_hint),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.45f),
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                            IconButton(
                                onClick = onStartVoice,
                                enabled = vs !is VoiceNoteState.Unavailable,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = stringResource(R.string.voice_dictate_note),
                                    tint = if (vs is VoiceNoteState.Unavailable) Color.White.copy(alpha = 0.25f) else Color(0xFFFFB800),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                if (voiceState is VoiceNoteState.Error) {
                    Spacer(Modifier.height(8.dp))
                    VoiceErrorBanner(
                        error = (voiceState as VoiceNoteState.Error).error,
                        onDismiss = onDismissVoiceError,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryAndAccountSelectors(
    state: TransactionEditorState,
    currentCategory: Category?,
    currentAccount: Account?,
    animatedTint: Color,
    onCategorySelected: (CategoryId) -> Unit,
    onAccountSelected: (AccountId) -> Unit,
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Category Dropdown Pill
        Box(Modifier.weight(1f)) {
            SelectorPill(
                icon = categoryIcon(currentCategory?.name.orEmpty(), currentCategory?.iconKey),
                iconTint = animatedTint,
                label = currentCategory?.name ?: stringResource(R.string.transaction_category_label),
                onClick = { categoryMenuExpanded = true },
            )
            DropdownMenu(
                expanded = categoryMenuExpanded,
                onDismissRequest = { categoryMenuExpanded = false },
                modifier = Modifier.background(Color(0xFF1E1D1B)),
            ) {
                val compatibleCategories = state.categories.filter { it.type == state.type }
                compatibleCategories.forEach { cat ->
                    val isSelected = cat.id == state.selectedCategoryId
                    val catColor = if (cat.colorArgb != 0L) Color(cat.colorArgb) else MaterialTheme.colorScheme.primary
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                categoryIcon(cat.name, cat.iconKey),
                                null,
                                tint = catColor,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        text = {
                            Text(
                                cat.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                            )
                        },
                        trailingIcon = if (isSelected) {
                            { Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            onCategorySelected(cat.id)
                            categoryMenuExpanded = false
                        },
                    )
                }
            }
        }

        // Account Dropdown Pill
        Box(Modifier.weight(1f)) {
            SelectorPill(
                icon = accountIcon(currentAccount?.type ?: AccountType.CASH),
                iconTint = MaterialTheme.colorScheme.primary,
                label = currentAccount?.name ?: stringResource(R.string.transaction_account_label),
                onClick = { accountMenuExpanded = true },
            )
            DropdownMenu(
                expanded = accountMenuExpanded,
                onDismissRequest = { accountMenuExpanded = false },
                modifier = Modifier.background(Color(0xFF1E1D1B)),
            ) {
                state.accounts.forEach { acc ->
                    val isSelected = acc.id == state.selectedAccountId
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                accountIcon(acc.type),
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        text = {
                            Text(
                                acc.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                            )
                        },
                        trailingIcon = if (isSelected) {
                            { Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            onAccountSelected(acc.id)
                            accountMenuExpanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeAndDateSelectors(
    state: TransactionEditorState,
    onTypeChanged: (TransactionType) -> Unit,
    onPickDate: () -> Unit,
) {
    val locale = activeAppLocale()
    val todayLabel = stringResource(R.string.editor_relative_today)
    val yesterdayLabel = stringResource(R.string.editor_relative_yesterday)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Segmented Type Toggle [ Chi | Thu ]
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF1E1D1B),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypeSegment(
                    label = stringResource(R.string.home_expense),
                    selected = state.type == TransactionType.EXPENSE,
                    selectedColor = Color(0xFFFF6B6B),
                    modifier = Modifier.weight(1f),
                ) { onTypeChanged(TransactionType.EXPENSE) }

                TypeSegment(
                    label = stringResource(R.string.home_income),
                    selected = state.type == TransactionType.INCOME,
                    selectedColor = Color(0xFF4ECDC4),
                    modifier = Modifier.weight(1f),
                ) { onTypeChanged(TransactionType.INCOME) }
            }
        }

        // Date Selector Pill
        val zoned = state.occurred?.let { Instant.ofEpochMilli(it.epochMillis).atZone(ZoneId.of(it.zoneId)) }
        val dateText = remember(zoned, locale, todayLabel, yesterdayLabel) {
            if (zoned == null) todayLabel
            else {
                val today = LocalDate.now(zoned.zone)
                val date = zoned.toLocalDate()
                when (date) {
                    today -> todayLabel
                    today.minusDays(1) -> yesterdayLabel
                    else -> BillLocketFormatters.formatDate(date, locale)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            SelectorPill(
                icon = Icons.Filled.CalendarMonth,
                iconTint = MaterialTheme.colorScheme.primary,
                label = dateText,
                onClick = onPickDate,
            )
        }
    }
}

@Composable
private fun SelectorPill(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF1E1D1B),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = iconTint)
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(20.dp), tint = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun TypeSegment(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(19.dp),
        color = if (selected) selectedColor.copy(alpha = 0.22f) else Color.Transparent,
        contentColor = if (selected) selectedColor else Color.White.copy(alpha = 0.6f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun CompactPhotoStrip(
    images: List<DraftImage>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onLocation: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF181615), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(images, key = { _, image -> image.id }) { _, image ->
                val locationDesc = stringResource(
                    if (image.confirmedLocation != null) R.string.editor_location_view else R.string.editor_location_add,
                )
                val deleteDesc = stringResource(R.string.action_delete)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .background(Color(0xFF181615))
                            .clickable(
                                onClick = { onLocation(image.id) },
                                onClickLabel = locationDesc,
                            )
                            .semantics { contentDescription = locationDesc },
                    ) {
                        AsyncImage(
                            image.previewUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(2.dp),
                            shape = CircleShape,
                            color = if (image.confirmedLocation != null) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.75f),
                            contentColor = if (image.confirmedLocation != null) Color(0xFF1E1400) else Color.White,
                        ) {
                            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    if (image.confirmedLocation != null) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = if (image.confirmedLocation != null) Color(0xFF1E1400) else Color.White,
                                )
                            }
                        }
                    }
                    Surface(
                        onClick = { onRemove(image.id) },
                        modifier = Modifier.semantics { contentDescription = deleteDesc },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White,
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
        Surface(
            onClick = onAdd,
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.1f),
            contentColor = Color.White,
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AddAPhoto, stringResource(R.string.editor_add_photo), Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun BottomActionRow(
    canSave: Boolean,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Cancel circle button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(
                onClick = onCancel,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                contentColor = Color.White,
            ) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_cancel), Modifier.size(22.dp))
                }
            }
            Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }

        // Dominant Save circle button (~72dp)
        Surface(
            onClick = onSave,
            enabled = canSave && !isSaving,
            shape = CircleShape,
            color = if (canSave) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
            contentColor = if (canSave) Color(0xFF1E1400) else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(68.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isSaving) {
                    CircularProgressIndicator(
                        Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = Color(0xFF1E1400),
                    )
                } else {
                    Icon(
                        Icons.Filled.Check,
                        stringResource(R.string.editor_save_transaction),
                        Modifier.size(34.dp),
                    )
                }
            }
        }
    }
}

private fun categoryIcon(name: String, iconKey: String?): ImageVector {
    val key = (iconKey ?: name).lowercase()
    return when {
        "ăn" in key || "food" in key || "uống" in key || "dining" in key -> Icons.Filled.Restaurant
        "mua" in key || "shop" in key -> Icons.Filled.ShoppingCart
        "xe" in key || "di chuyển" in key || "transport" in key -> Icons.Filled.DirectionsCar
        "nhà" in key || "hóa đơn" in key || "bill" in key || "điện" in key -> Icons.Filled.Receipt
        "giải trí" in key || "chơi" in key || "ent" in key -> Icons.Filled.ConfirmationNumber
        "y tế" in key || "thuốc" in key || "sức khỏe" in key -> Icons.Filled.LocalHospital
        "lương" in key || "salary" in key || "thu nhập" in key -> Icons.Filled.Payments
        "thưởng" in key || "bonus" in key -> Icons.Filled.CardGiftcard
        "đầu tư" in key || "invest" in key -> Icons.Filled.ShowChart
        else -> Icons.Filled.Category
    }
}

private fun accountIcon(type: AccountType): ImageVector = when (type) {
    AccountType.CASH -> Icons.Filled.Payments
    AccountType.BANK -> Icons.Filled.AccountBalance
    AccountType.EWALLET -> Icons.Filled.Wallet
}

private fun formatVndDisplay(digits: String, locale: Locale): String {
    val clean = digits.filter(Char::isDigit)
    val num = clean.toLongOrNull() ?: return "0"
    return NumberFormat.getNumberInstance(locale).format(num)
}

class VndAmountVisualTransformation(private val locale: Locale) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val clean = raw.filter(Char::isDigit)
        if (clean.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val formatted = formatVndDisplay(clean, locale)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, raw.length)
                var digitCount = 0
                var transIndex = 0
                while (transIndex < formatted.length && digitCount < clamped) {
                    if (formatted[transIndex].isDigit()) {
                        digitCount++
                    }
                    transIndex++
                }
                while (transIndex < formatted.length && !formatted[transIndex].isDigit()) {
                    transIndex++
                }
                return transIndex.coerceIn(0, formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, formatted.length)
                var digitCount = 0
                for (i in 0 until clamped) {
                    if (formatted[i].isDigit()) {
                        digitCount++
                    }
                }
                return digitCount.coerceIn(0, raw.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
