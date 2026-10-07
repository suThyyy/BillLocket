package com.billlocket.app.feature.transaction.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.MediaStorePort
import com.billlocket.app.domain.repository.*
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.usecase.*
import com.billlocket.app.feature.transaction.draft.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.*
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TransactionEditorState(
    val draftId: DraftId,
    val sourceTransactionId: TransactionId? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val selectedCategoryId: CategoryId? = null,
    val selectedAccountId: AccountId? = null,
    val note: String = "",
    val occurred: InstantAtZone? = null,
    val images: List<DraftImage> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val currency: CurrencyCode = CurrencyCode.of("VND"),
    val fieldErrors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val dirty: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val fatalError: String? = null,
) {
    val canSave: Boolean
        get() = !isLoading && !isSaving && fatalError == null && amountText.isNotBlank() &&
            selectedCategoryId != null && selectedAccountId != null
}

sealed interface TransactionEditorAction {
    data class TypeChanged(val value: TransactionType) : TransactionEditorAction
    data class AmountChanged(val value: String) : TransactionEditorAction
    data class CategorySelected(val id: CategoryId) : TransactionEditorAction
    data class AccountSelected(val id: AccountId) : TransactionEditorAction
    data class NoteChanged(val value: String) : TransactionEditorAction
    data class DateSelected(val epochDay: Long) : TransactionEditorAction
    data class TimeSelected(val hour: Int, val minute: Int) : TransactionEditorAction
    data class RemoveImage(val id: String) : TransactionEditorAction
    data class MoveImage(val from: Int, val to: Int) : TransactionEditorAction
    data object AddImages : TransactionEditorAction
    data object RefreshImages : TransactionEditorAction
    data object PickDate : TransactionEditorAction
    data object PickTime : TransactionEditorAction
    data object Save : TransactionEditorAction
    data object Back : TransactionEditorAction
    data object KeepEditing : TransactionEditorAction
    data object Discard : TransactionEditorAction
}

sealed interface TransactionEditorEffect {
    data object LaunchDatePicker : TransactionEditorEffect
    data object LaunchTimePicker : TransactionEditorEffect
    data class NavigateCapture(val draftId: DraftId) : TransactionEditorEffect
    data class NavigateDetail(val transactionId: TransactionId, val edited: Boolean) : TransactionEditorEffect
    data object NavigateBack : TransactionEditorEffect
}

@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val drafts: TransactionDraftStore,
    private val transactions: TransactionRepository,
    private val accountsRepository: AccountRepository,
    private val categoriesRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val createTransaction: CreateTransactionWithMedia,
    private val updateTransaction: UpdateTransactionWithMedia,
    private val evaluateBudgetThresholds: EvaluateBudgetThresholds,
    private val mediaStore: MediaStorePort,
    private val clock: Clock,
) : ViewModel() {
    private val draftId = DraftId(checkNotNull(savedStateHandle["draftId"]))
    private val routeTransactionId = savedStateHandle.get<String>("transactionId")?.let(::TransactionId)
    private val _state = MutableStateFlow(TransactionEditorState(draftId))
    val state = _state.asStateFlow()
    private val effectChannel = Channel<TransactionEditorEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var lastUsedCategoryName: String? = null
    private var lastUsedAccountName: String? = null
    private var baseline: FormBaseline? = null

    private data class FormBaseline(
        val type: TransactionType,
        val amountText: String,
        val categoryId: CategoryId?,
        val accountId: AccountId?,
        val note: String,
        val occurredEpochMillis: Long?,
        val imageIds: List<String>,
    )

    private fun isFormDirty(current: TransactionEditorState = _state.value): Boolean {
        val b = baseline ?: return current.dirty
        if (current.sourceTransactionId == null) {
            return current.amountText.isNotBlank() || current.note.isNotBlank() || current.images.isNotEmpty()
        }
        val amountChanged = current.amountText.trim() != b.amountText.trim()
        val typeChanged = current.type != b.type
        val categoryChanged = current.selectedCategoryId != b.categoryId
        val accountChanged = current.selectedAccountId != b.accountId
        val noteChanged = current.note.trim() != b.note.trim()
        val dateChanged = current.occurred?.epochMillis != b.occurredEpochMillis
        val imagesChanged = current.images.map { it.id } != b.imageIds
        return amountChanged || typeChanged || categoryChanged || accountChanged || noteChanged || dateChanged || imagesChanged
    }
    private fun resolveDefaults(
        type: TransactionType,
        currentCat: CategoryId?,
        currentAcc: AccountId?,
        categories: List<Category>,
        accounts: List<Account>,
    ): Pair<CategoryId?, AccountId?> {
        val compatible = categories.filter { it.type == type }
        val defaultCat = compatible.firstOrNull { it.isDefault }?.id ?: compatible.firstOrNull()?.id
        val lastUsedCatId = lastUsedCategoryName?.let { name -> compatible.firstOrNull { it.name == name }?.id }
        val resolvedCat = currentCat?.takeIf { id -> compatible.any { it.id == id } }
            ?: lastUsedCatId
            ?: defaultCat

        val primaryAccount = accounts.firstOrNull { it.type == AccountType.CASH }?.id ?: accounts.firstOrNull()?.id
        val lastUsedAccId = lastUsedAccountName?.let { name -> accounts.firstOrNull { it.name == name }?.id }
        val resolvedAcc = currentAcc?.takeIf { id -> accounts.any { it.id == id } }
            ?: primaryAccount
            ?: lastUsedAccId
            ?: accounts.firstOrNull()?.id

        return resolvedCat to resolvedAcc
    }

    init {
        viewModelScope.launch {
            val todayEpochDay = Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            val recentOutcome = transactions.page(TransactionFilter(range = DateRange(0L, todayEpochDay)), PageRequest(10))
            if (recentOutcome is Outcome.Success) {
                val lastExpense = recentOutcome.value.items.firstOrNull { it.type == TransactionType.EXPENSE }
                lastUsedCategoryName = lastExpense?.categoryName
                lastUsedAccountName = recentOutcome.value.items.firstOrNull()?.accountName
            }
            restoreDraft()
        }
        viewModelScope.launch {
            combine(
                accountsRepository.observeAll(),
                categoriesRepository.observe(),
                settingsRepository.settings,
            ) { accounts, categories, settings -> Triple(accounts, categories, settings.currency) }
                .collect { (accounts, categories, currency) ->
                    val current = _state.value
                    val (resolvedCat, resolvedAcc) = resolveDefaults(
                        current.type,
                        current.selectedCategoryId,
                        current.selectedAccountId,
                        categories,
                        accounts,
                    )
                    _state.value = current.copy(
                        accounts = accounts,
                        categories = categories,
                        currency = currency,
                        selectedAccountId = resolvedAcc,
                        selectedCategoryId = resolvedCat,
                    )
                }
        }
    }

    fun onAction(action: TransactionEditorAction) {
        when (action) {
            is TransactionEditorAction.TypeChanged -> mutate {
                val compatible = categories.filter { it.type == action.value }
                val defaultCat = compatible.firstOrNull { it.isDefault }?.id ?: compatible.firstOrNull()?.id
                copy(
                    type = action.value,
                    selectedCategoryId = defaultCat,
                    fieldErrors = fieldErrors - "category",
                )
            }
            is TransactionEditorAction.AmountChanged -> if (action.value.length <= 18) mutate {
                copy(amountText = action.value, fieldErrors = fieldErrors - "amount")
            }
            is TransactionEditorAction.CategorySelected -> mutate {
                copy(selectedCategoryId = action.id, fieldErrors = fieldErrors - "category")
            }
            is TransactionEditorAction.AccountSelected -> mutate {
                copy(selectedAccountId = action.id, fieldErrors = fieldErrors - "account")
            }
            is TransactionEditorAction.NoteChanged -> if (action.value.length <= 500) mutate { copy(note = action.value) }
            is TransactionEditorAction.DateSelected -> mutate { copy(occurred = occurred?.withDate(action.epochDay), fieldErrors = fieldErrors - "date") }
            is TransactionEditorAction.TimeSelected -> mutate { copy(occurred = occurred?.withTime(action.hour, action.minute)) }
            is TransactionEditorAction.RemoveImage -> removeImage(action.id)
            is TransactionEditorAction.MoveImage -> moveImage(action.from, action.to)
            TransactionEditorAction.AddImages -> emit(TransactionEditorEffect.NavigateCapture(draftId))
            TransactionEditorAction.RefreshImages -> refreshImages()
            TransactionEditorAction.PickDate -> emit(TransactionEditorEffect.LaunchDatePicker)
            TransactionEditorAction.PickTime -> emit(TransactionEditorEffect.LaunchTimePicker)
            TransactionEditorAction.Save -> save()
            TransactionEditorAction.Back -> {
                if (isFormDirty()) _state.value = _state.value.copy(showDiscardConfirmation = true)
                else emit(TransactionEditorEffect.NavigateBack)
            }
            TransactionEditorAction.KeepEditing -> _state.value = _state.value.copy(showDiscardConfirmation = false)
            TransactionEditorAction.Discard -> discard()
        }
    }

    private suspend fun restoreDraft() {
        var draft = drafts.load(draftId)
        if (draft == null && routeTransactionId != null) {
            val existing = transactions.observe(routeTransactionId).first()
            if (existing == null || existing.deletedAt != null) {
                _state.value = _state.value.copy(isLoading = false, fatalError = "Không tìm thấy giao dịch")
                return
            }
            draft = existing.toDraft(draftId, clock.nowEpochMillis())
            drafts.save(draft)
        }
        if (draft == null) {
            draft = newDraft()
            drafts.save(draft)
        }
        val verifiedImages = draft.images.map { image ->
            val exists = when (image.kind) {
                DraftImageKind.EXISTING -> mediaStore.exists(image.mediaUri)
                DraftImageKind.STAGED -> image.stagedMedia()?.let { mediaStore.inspectDraft(it) is Outcome.Success } == true
            }
            image.copy(isMissing = !exists)
        }
        val restored = draft.copy(images = verifiedImages)
        drafts.save(restored)
        val current = _state.value
        val (resolvedCat, resolvedAcc) = resolveDefaults(
            restored.type,
            restored.categoryId,
            restored.accountId,
            current.categories,
            current.accounts,
        )
        baseline = FormBaseline(
            type = restored.type,
            amountText = restored.amountText,
            categoryId = resolvedCat,
            accountId = resolvedAcc,
            note = restored.note,
            occurredEpochMillis = restored.occurred?.epochMillis,
            imageIds = restored.images.map { it.id },
        )
        val initialDirty = if (restored.sourceTransactionId != null) false else restored.dirty
        _state.value = current.copy(
            sourceTransactionId = restored.sourceTransactionId,
            type = restored.type,
            amountText = restored.amountText,
            selectedCategoryId = resolvedCat,
            selectedAccountId = resolvedAcc,
            note = restored.note,
            occurred = restored.occurred,
            images = restored.images,
            dirty = initialDirty,
            isLoading = false,
        )
    }

    private fun save() = viewModelScope.launch {
        if (_state.value.isSaving) return@launch
        val current = _state.value
        val amount = parseVndAmount(current.amountText)
        val account = current.accounts.firstOrNull { it.id == current.selectedAccountId }
        val category = current.categories.firstOrNull { it.id == current.selectedCategoryId }
        val zone = current.occurred?.zoneId?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
        val todayEpochDay = Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(zone).toLocalDate().toEpochDay()
        val isFutureDate = current.occurred != null && current.occurred.localDateEpochDay > todayEpochDay
        val errors = buildMap {
            if (amount == null || amount <= 0) put("amount", "Số tiền phải lớn hơn 0")
            if (account == null) put("account", "Hãy chọn tài khoản")
            if (category == null) put("category", "Hãy chọn danh mục phù hợp")
            if (current.occurred == null) put("date", "Ngày giờ không hợp lệ")
            else if (isFutureDate) put("date", "Ngày giao dịch không thể lớn hơn hôm nay.")
            if (current.images.any { it.isMissing }) put("images", "Hãy xóa ảnh bị thiếu trước khi lưu")
        }
        if (errors.isNotEmpty()) {
            _state.value = current.copy(fieldErrors = errors)
            return@launch
        }
        _state.value = current.copy(isSaving = true, fieldErrors = emptyMap())
        val command = CreateTransactionCommand(
            type = current.type,
            amount = Money(requireNotNull(amount), current.currency),
            category = CategoryRef(requireNotNull(category).id, category.type, !category.isArchived),
            account = AccountRef(requireNotNull(account).id, account.type, !account.isArchived, account.openingBalance.currency),
            note = current.note.trim().takeIf(String::isNotEmpty),
            occurred = requireNotNull(current.occurred),
            images = emptyList(),
        )
        val sourceId = current.sourceTransactionId
        val result = if (sourceId == null) {
            val prepared = current.images.mapNotNull(DraftImage::asPrepared)
            createTransaction(command, prepared)
        } else {
            val ordered = current.images.mapNotNull { image ->
                image.asExistingInput()?.let(TransactionMediaUpdate::Existing)
                    ?: image.asPrepared()?.let(TransactionMediaUpdate::Prepared)
            }
            when (val updated = updateTransaction(
                UpdateTransactionCommand(
                    id = sourceId,
                    type = command.type,
                    amount = command.amount,
                    category = command.category,
                    account = command.account,
                    note = command.note,
                    occurred = command.occurred,
                    images = emptyList(),
                ),
                ordered,
            )) {
                is Outcome.Success -> Outcome.Success(sourceId)
                is Outcome.Failure -> updated
            }
        }
        when (result) {
            is Outcome.Success -> {
                val monthStart = YearMonth.from(LocalDate.ofEpochDay(command.occurred.localDateEpochDay)).atDay(1).toEpochDay()
                evaluateBudgetThresholds(monthStart)
                drafts.delete(draftId)
                effectChannel.send(TransactionEditorEffect.NavigateDetail(result.value, sourceId != null))
            }
            is Outcome.Failure -> _state.value = _state.value.copy(
                isSaving = false,
                fieldErrors = mapOf("save" to "Không thể lưu giao dịch. Vui lòng thử lại."),
            )
        }
    }

    private fun removeImage(id: String) = viewModelScope.launch {
        val image = _state.value.images.firstOrNull { it.id == id }
        image?.stagedMedia()?.let { mediaStore.discardStaged(it) }
        mutateNow { copy(images = images.filterNot { it.id == id }, fieldErrors = fieldErrors - "images") }
    }

    private fun refreshImages() = viewModelScope.launch {
        drafts.load(draftId)?.let { draft -> _state.update { it.copy(images = draft.images, dirty = draft.dirty) } }
    }

    private fun moveImage(from: Int, to: Int) {
        val images = _state.value.images
        if (from !in images.indices || to !in images.indices || from == to) return
        val reordered = images.toMutableList().apply { add(to, removeAt(from)) }
        mutate { copy(images = reordered) }
    }

    private fun discard() = viewModelScope.launch {
        _state.value.images.mapNotNull(DraftImage::stagedMedia).forEach { mediaStore.discardStaged(it) }
        drafts.delete(draftId)
        effectChannel.send(TransactionEditorEffect.NavigateBack)
    }

    private fun mutate(transform: TransactionEditorState.() -> TransactionEditorState) {
        viewModelScope.launch { mutateNow(transform) }
    }

    private suspend fun mutateNow(transform: TransactionEditorState.() -> TransactionEditorState) {
        val candidate = _state.value.transform()
        val next = candidate.copy(dirty = isFormDirty(candidate))
        _state.value = next
        if (!next.isLoading && next.occurred != null) drafts.save(next.toDraft(clock.nowEpochMillis()))
    }

    private fun emit(effect: TransactionEditorEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }

    private fun newDraft(): TransactionDraft {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(clock.nowEpochMillis())
        return TransactionDraft(
            id = draftId,
            sourceTransactionId = routeTransactionId,
            occurred = InstantAtZone(now.toEpochMilli(), now.atZone(zone).toLocalDate().toEpochDay(), zone.id),
            updatedAtEpochMillis = now.toEpochMilli(),
        )
    }
}

fun parseVndAmount(raw: String): Long? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.any { !it.isDigit() && it !in " .," }) return null
    val digits = trimmed.filter(Char::isDigit)
    return digits.takeIf(String::isNotEmpty)?.toLongOrNull()
}

private fun InstantAtZone.withDate(epochDay: Long): InstantAtZone {
    val zone = ZoneId.of(zoneId)
    val current = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val changed = LocalDate.ofEpochDay(epochDay).atTime(current.toLocalTime()).atZone(zone)
    return InstantAtZone(changed.toInstant().toEpochMilli(), epochDay, zoneId)
}

private fun InstantAtZone.withTime(hour: Int, minute: Int): InstantAtZone {
    val zone = ZoneId.of(zoneId)
    val changed = LocalDate.ofEpochDay(localDateEpochDay).atTime(hour, minute).atZone(zone)
    return InstantAtZone(changed.toInstant().toEpochMilli(), localDateEpochDay, zoneId)
}

private fun TransactionDetails.toDraft(draftId: DraftId, now: Long) = TransactionDraft(
    id = draftId,
    sourceTransactionId = id,
    type = type,
    amountText = amount.minorUnits.toString(),
    categoryId = categoryId,
    accountId = accountId,
    note = note.orEmpty(),
    occurred = occurred,
    images = images.map(DraftImage::fromPersisted),
    dirty = false,
    updatedAtEpochMillis = now,
)

private fun TransactionEditorState.toDraft(now: Long) = TransactionDraft(
    id = draftId,
    sourceTransactionId = sourceTransactionId,
    type = type,
    amountText = amountText,
    categoryId = selectedCategoryId,
    accountId = selectedAccountId,
    note = note,
    occurred = requireNotNull(occurred),
    images = images,
    dirty = dirty,
    updatedAtEpochMillis = now,
)
