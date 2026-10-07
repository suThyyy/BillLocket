package com.billlocket.app.feature.finance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.repository.*
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.usecase.UpdateAmountVisibility
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AccountPeriodTab { THIS_MONTH, THIS_YEAR, ALL }

data class AccountBalanceItem(val account: Account, val balance: Money?)
data class AccountsState(
    val items: List<AccountBalanceItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val archiveTarget: Account? = null,
    val amountVisibility: AmountVisibility = AmountVisibility.SHOWN,
    val periodTab: AccountPeriodTab = AccountPeriodTab.THIS_MONTH,
    val anchorMonth: YearMonth = YearMonth.now(),
    val periodIncome: Money? = null,
    val periodExpense: Money? = null,
    val periodNet: Money? = null,
    val isReorderMode: Boolean = false,
)

sealed interface AccountsAction {
    data class Move(val id: AccountId, val delta: Int) : AccountsAction
    data class AskArchive(val account: Account?) : AccountsAction
    data object ConfirmArchive : AccountsAction
    data class SetPeriodTab(val tab: AccountPeriodTab) : AccountsAction
    data object PreviousMonth : AccountsAction
    data object NextMonth : AccountsAction
    data object ToggleReorderMode : AccountsAction
    data object ToggleAmountVisibility : AccountsAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repository: AccountRepository,
    private val insightsRepository: InsightsRepository,
    private val settingsRepository: SettingsRepository,
    private val updateAmountVisibility: UpdateAmountVisibility,
) : ViewModel() {
    private val _periodTab = MutableStateFlow(AccountPeriodTab.THIS_MONTH)
    private val _anchorMonth = MutableStateFlow(YearMonth.now())
    private val _state = MutableStateFlow(AccountsState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll(includeArchived = true).flatMapLatest { accounts ->
                if (accounts.isEmpty()) flowOf(emptyList())
                else combine(accounts.map { account -> repository.observeBalance(account.id).map { AccountBalanceItem(account, it) } }) { it.toList() }
            }.catch { _state.update { state -> state.copy(isLoading = false, errorMessage = "Không thể tải tài khoản") } }
                .collect { items -> _state.update { it.copy(items = items, isLoading = false) } }
        }

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(amountVisibility = settings.amountVisibility) }
            }
        }

        viewModelScope.launch {
            combine(_periodTab, _anchorMonth) { tab, month -> tab to month }
                .flatMapLatest { (tab, month) ->
                    val range = when (tab) {
                        AccountPeriodTab.THIS_MONTH -> DateRange(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())
                        AccountPeriodTab.THIS_YEAR -> DateRange(LocalDate.of(month.year, 1, 1).toEpochDay(), LocalDate.of(month.year, 12, 31).toEpochDay())
                        AccountPeriodTab.ALL -> DateRange(LocalDate.of(2000, 1, 1).toEpochDay(), LocalDate.of(2099, 12, 31).toEpochDay())
                    }
                    insightsRepository.observePeriod(InsightsQuery(range = range))
                }
                .catch { }
                .collect { insights ->
                    _state.update {
                        it.copy(
                            periodIncome = insights.income,
                            periodExpense = insights.expense,
                            periodNet = insights.net,
                        )
                    }
                }
        }
    }

    fun onAction(action: AccountsAction) {
        when (action) {
            is AccountsAction.AskArchive -> _state.update { it.copy(archiveTarget = action.account) }
            AccountsAction.ConfirmArchive -> viewModelScope.launch {
                val target = _state.value.archiveTarget ?: return@launch
                when (repository.archive(target.id)) {
                    is Outcome.Success -> _state.update { it.copy(archiveTarget = null) }
                    is Outcome.Failure -> _state.update { it.copy(archiveTarget = null, errorMessage = "Không thể lưu trữ tài khoản") }
                }
            }
            is AccountsAction.Move -> viewModelScope.launch {
                val active = _state.value.items.map(AccountBalanceItem::account).filterNot(Account::isArchived).sortedBy(Account::sortOrder).toMutableList()
                val from = active.indexOfFirst { it.id == action.id }
                val to = (from + action.delta).coerceIn(0, active.lastIndex)
                if (from >= 0 && from != to) {
                    val item = active.removeAt(from)
                    active.add(to, item)
                    if (repository.reorder(active.map(Account::id)) is Outcome.Failure) {
                        _state.update { it.copy(errorMessage = "Không thể sắp xếp tài khoản") }
                    }
                }
            }
            is AccountsAction.SetPeriodTab -> {
                _periodTab.value = action.tab
                _state.update { it.copy(periodTab = action.tab) }
            }
            AccountsAction.PreviousMonth -> {
                _anchorMonth.update { it.minusMonths(1) }
                _state.update { it.copy(anchorMonth = _anchorMonth.value) }
            }
            AccountsAction.NextMonth -> {
                _anchorMonth.update { it.plusMonths(1) }
                _state.update { it.copy(anchorMonth = _anchorMonth.value) }
            }
            AccountsAction.ToggleReorderMode -> _state.update { it.copy(isReorderMode = !it.isReorderMode) }
            AccountsAction.ToggleAmountVisibility -> viewModelScope.launch {
                val next = if (_state.value.amountVisibility == AmountVisibility.SHOWN) AmountVisibility.HIDDEN else AmountVisibility.SHOWN
                updateAmountVisibility(next)
            }
        }
    }
}

data class AccountEditorState(
    val id: AccountId? = null,
    val name: String = "",
    val type: AccountType = AccountType.CASH,
    val openingBalanceText: String = "0",
    val currency: CurrencyCode = CurrencyCode.of("VND"),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
sealed interface AccountEditorAction {
    data class NameChanged(val value: String) : AccountEditorAction
    data class TypeChanged(val value: AccountType) : AccountEditorAction
    data class OpeningBalanceChanged(val value: String) : AccountEditorAction
    data object Save : AccountEditorAction
}
sealed interface AccountEditorEffect { data object Saved : AccountEditorEffect }

@HiltViewModel
class AccountEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accounts: AccountRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val id = savedStateHandle.get<String>("accountId")?.takeIf(String::isNotBlank)?.let(::AccountId)
    private val _state = MutableStateFlow(AccountEditorState(id = id))
    val state = _state.asStateFlow()
    private val effectChannel = Channel<AccountEditorEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch { settings.settings.collect { value -> _state.update { it.copy(currency = value.currency) } } }
        id?.let { accountId ->
            viewModelScope.launch {
                accounts.observeDetails(accountId).filterNotNull().take(1).collect { details ->
                    _state.update { it.copy(name = details.account.name, type = details.account.type, openingBalanceText = details.account.openingBalance.minorUnits.toString()) }
                }
            }
        }
    }

    fun onAction(action: AccountEditorAction) {
        when (action) {
            is AccountEditorAction.NameChanged -> _state.update { it.copy(name = action.value) }
            is AccountEditorAction.TypeChanged -> _state.update { it.copy(type = action.value) }
            is AccountEditorAction.OpeningBalanceChanged -> if (action.value.matches(Regex("-?\\d{0,15}"))) _state.update { it.copy(openingBalanceText = action.value) }
            AccountEditorAction.Save -> save()
        }
    }

    private fun save() = viewModelScope.launch {
        val current = _state.value
        val amount = current.openingBalanceText.toLongOrNull()
        if (current.name.isBlank() || amount == null) {
            _state.update { it.copy(errorMessage = "Vui lòng nhập tên và số dư hợp lệ") }
            return@launch
        }
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        val result = current.id?.let { accounts.update(UpdateAccountCommand(it, current.name, current.type)) }
            ?: accounts.create(CreateAccountCommand(current.name, current.type, Money(amount, current.currency)))
        when (result) {
            is Outcome.Success -> effectChannel.send(AccountEditorEffect.Saved)
            is Outcome.Failure -> _state.update { it.copy(isSaving = false, errorMessage = "Không thể lưu tài khoản; tên có thể đã tồn tại") }
        }
    }
}

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: AccountRepository,
) : ViewModel() {
    private val id = AccountId(checkNotNull(savedStateHandle["accountId"]))
    val state: StateFlow<AccountDetails?> = repository.observeDetails(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class CategoriesState(
    val selectedType: TransactionType = TransactionType.EXPENSE,
    val items: List<Category> = emptyList(),
    val editing: Category? = null,
    val name: String = "",
    val colorArgb: Long = 0xFFFFB300L,
    val iconKey: String? = null,
    val archiveTarget: Category? = null,
    val showEditor: Boolean = false,
    val errorMessage: String? = null,
)
sealed interface CategoriesAction {
    data class SelectType(val value: TransactionType) : CategoriesAction
    data class Edit(val value: Category?) : CategoriesAction
    data class NameChanged(val value: String) : CategoriesAction
    data class ColorChanged(val value: Long) : CategoriesAction
    data class IconKeyChanged(val value: String?) : CategoriesAction
    data object Save : CategoriesAction
    data class Move(val id: CategoryId, val delta: Int) : CategoriesAction
    data class AskArchive(val value: Category?) : CategoriesAction
    data object ConfirmArchive : CategoriesAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CategoryRepository,
) : ViewModel() {
    private val initialType = savedStateHandle.get<String>("transactionType")?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() } ?: TransactionType.EXPENSE
    private val selectedType = MutableStateFlow(initialType)
    private val _state = MutableStateFlow(CategoriesState(selectedType = initialType))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            selectedType.flatMapLatest { repository.observe(it, includeArchived = true) }.collect { rows -> _state.update { it.copy(items = rows) } }
        }
    }

    fun onAction(action: CategoriesAction) {
        when (action) {
            is CategoriesAction.SelectType -> {
                selectedType.value = action.value
                _state.update { it.copy(selectedType = action.value, editing = null, name = "", iconKey = null, showEditor = false, errorMessage = null) }
            }
            is CategoriesAction.Edit -> _state.update {
                it.copy(
                    editing = action.value,
                    name = action.value?.name.orEmpty(),
                    colorArgb = action.value?.colorArgb ?: (if (it.selectedType == TransactionType.EXPENSE) 0xFFFFB300L else 0xFF26A69AL),
                    iconKey = action.value?.iconKey ?: (if (it.selectedType == TransactionType.EXPENSE) "restaurant" else "payments"),
                    showEditor = action.value != null || !it.showEditor,
                    errorMessage = null,
                )
            }
            is CategoriesAction.NameChanged -> _state.update { it.copy(name = action.value, errorMessage = null) }
            is CategoriesAction.ColorChanged -> _state.update { it.copy(colorArgb = action.value) }
            is CategoriesAction.IconKeyChanged -> _state.update { it.copy(iconKey = action.value) }
            CategoriesAction.Save -> viewModelScope.launch {
                val current = _state.value
                val trimmedName = current.name.trim()
                if (trimmedName.isEmpty()) {
                    _state.update { it.copy(errorMessage = "Vui lòng nhập tên danh mục") }
                    return@launch
                }
                val result = current.editing?.let {
                    repository.update(UpdateCategoryCommand(it.id, trimmedName, current.selectedType, current.colorArgb, current.iconKey))
                } ?: repository.create(CreateCategoryCommand(trimmedName, current.selectedType, current.colorArgb, current.iconKey))
                when (result) {
                    is Outcome.Success -> _state.update { it.copy(editing = null, name = "", iconKey = null, showEditor = false, errorMessage = null) }
                    is Outcome.Failure -> _state.update { it.copy(errorMessage = "Không thể lưu danh mục; tên có thể đã tồn tại") }
                }
            }
            is CategoriesAction.Move -> viewModelScope.launch {
                val active = _state.value.items.filterNot(Category::isArchived).sortedBy(Category::sortOrder).toMutableList()
                val from = active.indexOfFirst { it.id == action.id }
                val to = (from + action.delta).coerceIn(0, active.lastIndex)
                if (from >= 0 && from != to) {
                    val item = active.removeAt(from)
                    active.add(to, item)
                    repository.reorder(_state.value.selectedType, active.map(Category::id))
                }
            }
            is CategoriesAction.AskArchive -> _state.update { it.copy(archiveTarget = action.value) }
            CategoriesAction.ConfirmArchive -> viewModelScope.launch {
                _state.value.archiveTarget?.let { repository.archive(it.id) }
                _state.update { it.copy(archiveTarget = null) }
            }
        }
    }
}

data class TransferListItem(val transfer: Transfer, val fromName: String, val toName: String)
data class TransfersState(val items: List<TransferListItem> = emptyList(), val isLoading: Boolean = true, val errorMessage: String? = null)

@HiltViewModel
class TransfersViewModel @Inject constructor(
    private val transfers: TransferRepository,
    private val accounts: AccountRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TransfersState())
    val state = _state.asStateFlow()
    init { refresh() }
    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        val names = accounts.observeAll(includeArchived = true).first().associate { it.id to it.name }
        when (val result = transfers.page(null, null, PageRequest(PageRequest.MAX_LIMIT))) {
            is Outcome.Success -> _state.value = TransfersState(result.value.items.map { TransferListItem(it, names[it.fromAccountId].orEmpty(), names[it.toAccountId].orEmpty()) }, false)
            is Outcome.Failure -> _state.value = TransfersState(isLoading = false, errorMessage = "Không thể tải lịch sử chuyển tiền")
        }
    }
}

data class TransferEditorState(
    val id: TransferId? = null,
    val accounts: List<Account> = emptyList(),
    val fromId: AccountId? = null,
    val toId: AccountId? = null,
    val amountText: String = "",
    val note: String = "",
    val occurred: InstantAtZone = Instant.now().let { now -> ZoneId.systemDefault().let { zone -> InstantAtZone(now.toEpochMilli(), now.atZone(zone).toLocalDate().toEpochDay(), zone.id) } },
    val currency: CurrencyCode = CurrencyCode.of("VND"),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
sealed interface TransferEditorAction {
    data class FromChanged(val id: AccountId) : TransferEditorAction
    data class ToChanged(val id: AccountId) : TransferEditorAction
    data class AmountChanged(val value: String) : TransferEditorAction
    data class NoteChanged(val value: String) : TransferEditorAction
    data object Save : TransferEditorAction
}
sealed interface TransferEditorEffect { data object Saved : TransferEditorEffect }

@HiltViewModel
class TransferEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transfers: TransferRepository,
    accounts: AccountRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val id = savedStateHandle.get<String>("transferId")?.takeIf(String::isNotBlank)?.let(::TransferId)
    private val _state = MutableStateFlow(TransferEditorState(id = id))
    val state = _state.asStateFlow()
    private val effectChannel = Channel<TransferEditorEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(accounts.observeAll(), settings.settings) { accountList, config -> accountList to config }.collect { (list, config) ->
                _state.update { it.copy(accounts = list, currency = config.currency, fromId = it.fromId ?: list.firstOrNull()?.id, toId = it.toId ?: list.drop(1).firstOrNull()?.id) }
            }
        }
        id?.let { transferId -> viewModelScope.launch {
            transfers.observe(transferId).filterNotNull().take(1).collect { value ->
                _state.update { it.copy(fromId = value.fromAccountId, toId = value.toAccountId, amountText = value.amount.minorUnits.toString(), note = value.note.orEmpty(), occurred = value.occurred) }
            }
        } }
    }

    fun onAction(action: TransferEditorAction) {
        when (action) {
            is TransferEditorAction.FromChanged -> _state.update { it.copy(fromId = action.id) }
            is TransferEditorAction.ToChanged -> _state.update { it.copy(toId = action.id) }
            is TransferEditorAction.AmountChanged -> if (action.value.all(Char::isDigit)) _state.update { it.copy(amountText = action.value) }
            is TransferEditorAction.NoteChanged -> if (action.value.length <= 500) _state.update { it.copy(note = action.value) }
            TransferEditorAction.Save -> save()
        }
    }

    private fun save() = viewModelScope.launch {
        val current = _state.value
        val from = current.accounts.firstOrNull { it.id == current.fromId }
        val to = current.accounts.firstOrNull { it.id == current.toId }
        val amount = current.amountText.toLongOrNull()
        if (from == null || to == null || from.id == to.id || amount == null || amount <= 0) {
            _state.update { it.copy(errorMessage = "Chọn hai tài khoản khác nhau và nhập số tiền lớn hơn 0") }
            return@launch
        }
        val command = CreateTransferCommand(
            AccountRef(from.id, from.type, !from.isArchived, from.openingBalance.currency),
            AccountRef(to.id, to.type, !to.isArchived, to.openingBalance.currency),
            Money(amount, current.currency), current.note, current.occurred,
        )
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        val result = current.id?.let { transfers.update(UpdateTransferCommand(it, command)) } ?: transfers.create(command)
        when (result) {
            is Outcome.Success -> effectChannel.send(TransferEditorEffect.Saved)
            is Outcome.Failure -> _state.update { it.copy(isSaving = false, errorMessage = "Không thể lưu chuyển tiền") }
        }
    }
}
