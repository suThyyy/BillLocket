package com.billlocket.app.feature.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.repository.*
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.usecase.EvaluateBudgetThresholds
import com.billlocket.app.ui.common.ObservationResult
import com.billlocket.app.ui.common.asObservationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class PeriodKind { WEEK, MONTH, YEAR }

data class StatisticsState(
    val kind: PeriodKind = PeriodKind.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val insights: PeriodInsights? = null,
    val accounts: List<Account> = emptyList(),
    val accountId: AccountId? = null,
    val amountVisibility: AmountVisibility = AmountVisibility.SHOWN,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val recentTransactions: List<TransactionSummary> = emptyList(),
    val trendGranularity: TrendGranularity = TrendGranularity.DAY,
    val selectedDayEpoch: Long? = null,
    val yearlyTrend: List<TrendPoint> = emptyList(),
)
sealed interface StatisticsAction {
    data class KindChanged(val value: PeriodKind) : StatisticsAction
    data class AccountChanged(val value: AccountId?) : StatisticsAction
    data object Previous : StatisticsAction
    data object Next : StatisticsAction
    data object Today : StatisticsAction
    data class TrendGranularityChanged(val value: TrendGranularity) : StatisticsAction
    data class DaySelected(val epochDay: Long?) : StatisticsAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val insights: InsightsRepository,
    accounts: AccountRepository,
    settings: SettingsRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {
    private val kind = MutableStateFlow(PeriodKind.MONTH)
    private val anchor = MutableStateFlow(LocalDate.now())
    private val account = MutableStateFlow<AccountId?>(null)
    private val _state = MutableStateFlow(StatisticsState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(accounts.observeAll(), settings.settings) { list, config -> list to config }.collect { (list, config) ->
                _state.update { it.copy(accounts = list, amountVisibility = config.amountVisibility) }
            }
        }
        viewModelScope.launch {
            combine(kind, anchor, account) { selectedKind, selectedAnchor, selectedAccount -> Triple(selectedKind, selectedAnchor, selectedAccount) }
                .flatMapLatest { (selectedKind, selectedAnchor, selectedAccount) ->
                    _state.update { it.copy(kind = selectedKind, anchor = selectedAnchor, accountId = selectedAccount, isLoading = true) }
                    insights.observePeriod(periodQuery(selectedKind, selectedAnchor, selectedAccount))
                        .asObservationResult()
                }
                .collect { result ->
                    when (result) {
                        is ObservationResult.Error -> _state.update {
                            it.copy(isLoading = false, errorMessage = "Không thể tải thống kê")
                        }
                        is ObservationResult.Value -> {
                            val query = periodQuery(kind.value, anchor.value, account.value)
                            val todayEpochDay = LocalDate.now().toEpochDay()
                            val recentRange = DateRange(query.range.startEpochDay, minOf(query.range.endInclusiveEpochDay, todayEpochDay))
                            val recentOutcome = transactions.page(
                                TransactionFilter(range = recentRange, accountId = account.value),
                                PageRequest(limit = 5),
                            )
                            val recent = (recentOutcome as? Outcome.Success)?.value?.items ?: emptyList()
                            _state.update {
                                it.copy(
                                    insights = result.value,
                                    recentTransactions = recent,
                                    isLoading = false,
                                    errorMessage = null,
                                )
                            }
                        }
                    }
                }
        }
        viewModelScope.launch {
            combine(anchor, account) { selectedAnchor, selectedAccount -> selectedAnchor.year to selectedAccount }
                .distinctUntilChanged()
                .flatMapLatest { (year, selectedAccount) ->
                    val yearRange = DateRange(LocalDate.of(year, 1, 1).toEpochDay(), LocalDate.of(year, 12, 31).toEpochDay())
                    insights.observePeriod(InsightsQuery(yearRange, accountId = selectedAccount, granularity = TrendGranularity.MONTH))
                        .asObservationResult()
                }
                .collect { result ->
                    if (result is ObservationResult.Value) {
                        _state.update { it.copy(yearlyTrend = result.value.trend) }
                    }
                }
        }
    }

    fun onAction(action: StatisticsAction) {
        when (action) {
            is StatisticsAction.KindChanged -> kind.value = action.value
            is StatisticsAction.AccountChanged -> account.value = action.value
            StatisticsAction.Previous -> anchor.value = shift(anchor.value, kind.value, -1)
            StatisticsAction.Next -> anchor.value = shift(anchor.value, kind.value, 1)
            StatisticsAction.Today -> anchor.value = LocalDate.now()
            is StatisticsAction.TrendGranularityChanged -> _state.update { it.copy(trendGranularity = action.value) }
            is StatisticsAction.DaySelected -> _state.update { it.copy(selectedDayEpoch = action.epochDay) }
        }
    }
}

fun periodQuery(kind: PeriodKind, anchor: LocalDate, accountId: AccountId? = null): InsightsQuery {
    val current = when (kind) {
        PeriodKind.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { DateRange(it.toEpochDay(), it.plusDays(6).toEpochDay()) }
        PeriodKind.MONTH -> YearMonth.from(anchor).let { DateRange(it.atDay(1).toEpochDay(), it.atEndOfMonth().toEpochDay()) }
        PeriodKind.YEAR -> DateRange(LocalDate.of(anchor.year, 1, 1).toEpochDay(), LocalDate.of(anchor.year, 12, 31).toEpochDay())
    }
    val comparison = when (kind) {
        PeriodKind.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
            .let { DateRange(it.toEpochDay(), it.plusDays(6).toEpochDay()) }
        PeriodKind.MONTH -> YearMonth.from(anchor).minusMonths(1)
            .let { DateRange(it.atDay(1).toEpochDay(), it.atEndOfMonth().toEpochDay()) }
        PeriodKind.YEAR -> DateRange(
            LocalDate.of(anchor.year - 1, 1, 1).toEpochDay(),
            LocalDate.of(anchor.year - 1, 12, 31).toEpochDay(),
        )
    }
    return InsightsQuery(current, comparison, accountId, when (kind) {
        PeriodKind.WEEK, PeriodKind.MONTH -> TrendGranularity.DAY
        PeriodKind.YEAR -> TrendGranularity.MONTH
    })
}

private fun shift(date: LocalDate, kind: PeriodKind, delta: Long) = when (kind) {
    PeriodKind.WEEK -> date.plusWeeks(delta)
    PeriodKind.MONTH -> date.plusMonths(delta)
    PeriodKind.YEAR -> date.plusYears(delta)
}

data class BudgetsState(
    val month: YearMonth = YearMonth.now(),
    val budgets: List<BudgetProgress> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currency: CurrencyCode = CurrencyCode.of("VND"),
    val showEditor: Boolean = false,
    val editing: BudgetProgress? = null,
    val categoryId: CategoryId? = null,
    val limitText: String = "",
    val warningText: String = "80",
    val notificationGranted: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
sealed interface BudgetsAction {
    data object PreviousMonth : BudgetsAction
    data object NextMonth : BudgetsAction
    data class Edit(val value: BudgetProgress?) : BudgetsAction
    data object CloseEditor : BudgetsAction
    data class CategoryChanged(val value: CategoryId?) : BudgetsAction
    data class LimitChanged(val value: String) : BudgetsAction
    data class WarningChanged(val value: String) : BudgetsAction
    data object Save : BudgetsAction
    data class Delete(val id: BudgetId) : BudgetsAction
    data class NotificationCapability(val granted: Boolean) : BudgetsAction
    data object EnableNotifications : BudgetsAction
}
sealed interface BudgetsEffect { data object RequestNotificationPermission : BudgetsEffect }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgets: BudgetRepository,
    categories: CategoryRepository,
    settings: SettingsRepository,
    private val evaluate: EvaluateBudgetThresholds,
) : ViewModel() {
    private val initial = savedStateHandle.get<Long>("monthEpochDay")?.takeIf { it >= 0 }?.let { YearMonth.from(LocalDate.ofEpochDay(it)) } ?: YearMonth.now()
    private val month = MutableStateFlow(initial)
    private val _state = MutableStateFlow(BudgetsState(month = initial))
    val state = _state.asStateFlow()
    private val effectChannel = Channel<BudgetsEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(categories.observe(TransactionType.EXPENSE), settings.settings) { list, config -> list to config }.collect { (list, config) ->
                _state.update { it.copy(categories = list, currency = config.currency) }
            }
        }
        viewModelScope.launch {
            month.flatMapLatest { selected -> budgets.observeMonth(selected.atDay(1).toEpochDay()) }.collect { rows ->
                _state.update { it.copy(month = month.value, budgets = rows) }
                if (_state.value.notificationGranted) evaluate(month.value.atDay(1).toEpochDay())
            }
        }
    }

    fun onAction(action: BudgetsAction) {
        when (action) {
            BudgetsAction.PreviousMonth -> month.value = month.value.minusMonths(1)
            BudgetsAction.NextMonth -> month.value = month.value.plusMonths(1)
            is BudgetsAction.Edit -> _state.update {
                it.copy(showEditor = true, editing = action.value, categoryId = action.value?.categoryId, limitText = action.value?.limit?.minorUnits?.toString().orEmpty(), warningText = action.value?.warningPercent?.toString() ?: "80")
            }
            BudgetsAction.CloseEditor -> _state.update { it.copy(showEditor = false, editing = null) }
            is BudgetsAction.CategoryChanged -> _state.update { it.copy(categoryId = action.value) }
            is BudgetsAction.LimitChanged -> if (action.value.all(Char::isDigit)) _state.update { it.copy(limitText = action.value) }
            is BudgetsAction.WarningChanged -> if (action.value.all(Char::isDigit)) _state.update { it.copy(warningText = action.value) }
            BudgetsAction.Save -> save()
            is BudgetsAction.Delete -> viewModelScope.launch { budgets.delete(action.id) }
            is BudgetsAction.NotificationCapability -> {
                _state.update { it.copy(notificationGranted = action.granted) }
                if (action.granted) viewModelScope.launch { evaluate(month.value.atDay(1).toEpochDay()) }
            }
            BudgetsAction.EnableNotifications -> viewModelScope.launch { effectChannel.send(BudgetsEffect.RequestNotificationPermission) }
        }
    }

    private fun save() = viewModelScope.launch {
        val current = _state.value
        val limit = current.limitText.toLongOrNull()
        val warning = current.warningText.toIntOrNull()
        if (limit == null || limit <= 0 || warning == null || warning !in 1..99) {
            _state.update { it.copy(errorMessage = "Hạn mức phải lớn hơn 0 và ngưỡng từ 1 đến 99") }
            return@launch
        }
        val category = current.categoryId?.let { id -> current.categories.firstOrNull { it.id == id } }?.let { CategoryRef(it.id, it.type, !it.isArchived) }
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        when (budgets.upsert(SaveBudgetCommand(month.value.atDay(1).toEpochDay(), category, Money(limit, current.currency), warning))) {
            is Outcome.Success -> {
                _state.update { it.copy(isSaving = false, showEditor = false, editing = null) }
                evaluate(month.value.atDay(1).toEpochDay())
            }
            is Outcome.Failure -> _state.update { it.copy(isSaving = false, errorMessage = "Không thể lưu ngân sách") }
        }
    }
}
