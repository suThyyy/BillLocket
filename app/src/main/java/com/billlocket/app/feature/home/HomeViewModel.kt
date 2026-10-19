package com.billlocket.app.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.repository.AccountRepository
import com.billlocket.app.domain.repository.SettingsRepository
import com.billlocket.app.domain.repository.TransactionRepository
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.usecase.UpdateAmountVisibility
import com.billlocket.app.feature.transaction.draft.TransactionDraft
import com.billlocket.app.feature.transaction.draft.TransactionDraftStore
import com.billlocket.app.navigation.newDraftId
import com.billlocket.app.ui.common.ObservationResult
import com.billlocket.app.ui.common.asObservationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeState(
    val month: YearMonth = YearMonth.now(),
    val selectedEpochDay: Long = LocalDate.now().toEpochDay(),
    val days: List<CalendarDaySummary> = emptyList(),
    val selectedTransactions: List<TransactionSummary> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val typeFilter: TransactionType? = null,
    val accountFilter: AccountId? = null,
    val amountVisibility: AmountVisibility = AmountVisibility.SHOWN,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

sealed interface HomeAction {
    data object PreviousMonth : HomeAction
    data object NextMonth : HomeAction
    data object Today : HomeAction
    data class DaySelected(val epochDay: Long, val openViewerIfHasPhotos: Boolean = false) : HomeAction
    data class TypeFilterChanged(val type: TransactionType?) : HomeAction
    data class AccountFilterChanged(val accountId: AccountId?) : HomeAction
    data object ToggleTotals : HomeAction
    data object OpenSelectedDay : HomeAction
    data object CreateForSelectedDay : HomeAction
    data object Search : HomeAction
}

sealed interface HomeEffect {
    data class OpenDay(val epochDay: Long) : HomeEffect
    data class OpenCapture(val draftId: DraftId) : HomeEffect
    data object OpenSearch : HomeEffect
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    settings: SettingsRepository,
    private val updateAmountVisibility: UpdateAmountVisibility,
    private val draftStore: TransactionDraftStore,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val typeFilter = MutableStateFlow<TransactionType?>(null)
    private val accountFilter = MutableStateFlow<AccountId?>(null)
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()
    private val effectsChannel = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = effectsChannel.receiveAsFlow()
    private var dayJob: Job? = null

    init {
        viewModelScope.launch {
            combine(accounts.observeAll(), settings.settings) { accountList, preferences -> accountList to preferences }
                .collect { (accountList, preferences) ->
                    _state.update { it.copy(accounts = accountList, amountVisibility = preferences.amountVisibility) }
                }
        }
        viewModelScope.launch {
            combine(month, typeFilter, accountFilter) { value, type, account -> Triple(value, type, account) }
                .flatMapLatest { (value, type, account) ->
                    _state.update { it.copy(month = value, isLoading = true, errorMessage = null) }
                    transactions.observeCalendar(value.dateRange(), TransactionFilter(type = type, accountId = account))
                        .asObservationResult()
                }
                .collect { result ->
                    when (result) {
                        is ObservationResult.Error -> _state.update {
                            it.copy(isLoading = false, errorMessage = "Không thể tải lịch")
                        }
                        is ObservationResult.Value -> {
                            _state.update { it.copy(days = result.value, isLoading = false, errorMessage = null) }
                            loadSelectedDay()
                        }
                    }
                }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.PreviousMonth -> selectMonth(month.value.minusMonths(1))
            HomeAction.NextMonth -> selectMonth(month.value.plusMonths(1))
            HomeAction.Today -> selectMonth(YearMonth.now(), LocalDate.now().toEpochDay())
            is HomeAction.DaySelected -> {
                _state.update { it.copy(selectedEpochDay = action.epochDay) }
                loadSelectedDay()
                if (action.openViewerIfHasPhotos) {
                    emit(HomeEffect.OpenDay(action.epochDay))
                }
            }
            is HomeAction.TypeFilterChanged -> {
                typeFilter.value = action.type
                _state.update { it.copy(typeFilter = action.type) }
            }
            is HomeAction.AccountFilterChanged -> {
                accountFilter.value = action.accountId
                _state.update { it.copy(accountFilter = action.accountId) }
            }
            HomeAction.ToggleTotals -> viewModelScope.launch {
                val next = if (_state.value.amountVisibility == AmountVisibility.SHOWN) AmountVisibility.HIDDEN else AmountVisibility.SHOWN
                updateAmountVisibility(next)
            }
            HomeAction.OpenSelectedDay -> emit(HomeEffect.OpenDay(_state.value.selectedEpochDay))
            HomeAction.CreateForSelectedDay -> {
                val todayEpochDay = LocalDate.now().toEpochDay()
                if (_state.value.selectedEpochDay <= todayEpochDay) {
                    createDraft(_state.value.selectedEpochDay)
                } else {
                    _state.update { it.copy(errorMessage = "Không thể thêm giao dịch cho ngày tương lai.") }
                }
            }
            HomeAction.Search -> emit(HomeEffect.OpenSearch)
        }
    }

    private fun selectMonth(value: YearMonth, selected: Long = value.atDay(1).toEpochDay()) {
        _state.update { it.copy(month = value, selectedEpochDay = selected) }
        month.value = value
    }

    private fun loadSelectedDay() {
        dayJob?.cancel()
        dayJob = viewModelScope.launch {
            val current = _state.value
            val result = transactions.page(
                TransactionFilter(
                    range = DateRange(current.selectedEpochDay, current.selectedEpochDay),
                    type = current.typeFilter,
                    accountId = current.accountFilter,
                ),
                PageRequest(PageRequest.MAX_LIMIT),
            )
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(selectedTransactions = result.value.items)
                    is Outcome.Failure -> it.copy(selectedTransactions = emptyList(), errorMessage = "Không thể tải giao dịch trong ngày")
                }
            }
        }
    }

    private fun createDraft(epochDay: Long) = viewModelScope.launch {
        val id = newDraftId()
        draftStore.save(newDraftForDay(id, epochDay))
        effectsChannel.send(HomeEffect.OpenCapture(id))
    }

    private fun emit(effect: HomeEffect) = viewModelScope.launch { effectsChannel.send(effect) }
}

fun YearMonth.dateRange() = DateRange(atDay(1).toEpochDay(), atEndOfMonth().toEpochDay())

fun calendarCells(month: YearMonth): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val values = MutableList<LocalDate?>(leading) { null }
    repeat(month.lengthOfMonth()) { values += month.atDay(it + 1) }
    while (values.size % 7 != 0) values += null
    return values
}

fun newDraftForDay(id: DraftId, epochDay: Long, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): TransactionDraft {
    val date = LocalDate.ofEpochDay(epochDay)
    val localTime = now.atZone(zone).toLocalTime()
    val instant = date.atTime(localTime).atZone(zone).toInstant()
    return TransactionDraft(
        id = id,
        occurred = InstantAtZone(instant.toEpochMilli(), epochDay, zone.id),
        updatedAtEpochMillis = now.toEpochMilli(),
    )
}

data class DayPhotoMemory(
    val imageUri: String,
    val transactionId: TransactionId,
    val amount: Money,
    val type: TransactionType,
    val categoryName: String,
    val accountName: String,
    val note: String?,
    val occurred: InstantAtZone,
    val imageIndex: Int = 0,
    val totalImagesInTransaction: Int = 1,
)

data class DayGalleryState(
    val epochDay: Long,
    val items: List<TransactionSummary> = emptyList(),
    val photos: List<DayPhotoMemory> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

sealed interface DayGalleryAction { data object Refresh : DayGalleryAction; data object Create : DayGalleryAction }
sealed interface DayGalleryEffect { data class OpenCapture(val draftId: DraftId) : DayGalleryEffect }

@HiltViewModel
class DayGalleryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val draftStore: TransactionDraftStore,
) : ViewModel() {
    private val epochDay = checkNotNull(savedStateHandle.get<Long>("epochDay"))
    private val _state = MutableStateFlow(DayGalleryState(epochDay))
    val state = _state.asStateFlow()
    private val effectChannel = Channel<DayGalleryEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            transactions.observeCalendar(DateRange(epochDay, epochDay)).collect { load() }
        }
    }

    fun onAction(action: DayGalleryAction) {
        when (action) {
            DayGalleryAction.Refresh -> viewModelScope.launch { load() }
            DayGalleryAction.Create -> viewModelScope.launch {
                val todayEpochDay = LocalDate.now().toEpochDay()
                if (epochDay <= todayEpochDay) {
                    val id = newDraftId()
                    draftStore.save(newDraftForDay(id, epochDay))
                    effectChannel.send(DayGalleryEffect.OpenCapture(id))
                } else {
                    _state.update { it.copy(errorMessage = "Không thể thêm giao dịch cho ngày tương lai.") }
                }
            }
        }
    }

    private suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = transactions.page(TransactionFilter(DateRange(epochDay, epochDay)), PageRequest(PageRequest.MAX_LIMIT))) {
            is Outcome.Success -> {
                val items = result.value.items
                val photoList = mutableListOf<DayPhotoMemory>()
                for (item in items) {
                    if (item.representativeImageUri != null) {
                        val details = transactions.observe(item.id).firstOrNull()
                        if (details != null && details.images.isNotEmpty()) {
                            val sortedImages = details.images.sortedBy { it.position }
                            sortedImages.forEachIndexed { idx, img ->
                                photoList.add(
                                    DayPhotoMemory(
                                        imageUri = img.mediaUri,
                                        transactionId = item.id,
                                        amount = item.amount,
                                        type = item.type,
                                        categoryName = item.categoryName,
                                        accountName = item.accountName,
                                        note = item.note,
                                        occurred = item.occurred,
                                        imageIndex = idx,
                                        totalImagesInTransaction = sortedImages.size,
                                    )
                                )
                            }
                        } else {
                            photoList.add(
                                DayPhotoMemory(
                                    imageUri = item.representativeImageUri,
                                    transactionId = item.id,
                                    amount = item.amount,
                                    type = item.type,
                                    categoryName = item.categoryName,
                                    accountName = item.accountName,
                                    note = item.note,
                                    occurred = item.occurred,
                                    imageIndex = 0,
                                    totalImagesInTransaction = 1,
                                )
                            )
                        }
                    }
                }
                _state.update { it.copy(items = items, photos = photoList, isLoading = false) }
            }
            is Outcome.Failure -> _state.update { it.copy(isLoading = false, errorMessage = "Không thể tải giao dịch") }
        }
    }
}
