package com.billlocket.app.feature.recap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billlocket.app.domain.model.InsightsQuery
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.repository.CategoryRepository
import com.billlocket.app.domain.repository.InsightsRepository
import com.billlocket.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MonthlyRecapState(
    val month: YearMonth,
    val recap: MonthlyRecap? = null,
    val privacy: RecapPrivacy = RecapPrivacy(),
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface MonthlyRecapAction {
    data object PreviousMonth : MonthlyRecapAction
    data object NextMonth : MonthlyRecapAction
    data class ShowAmounts(val value: Boolean) : MonthlyRecapAction
    data class ShowNotes(val value: Boolean) : MonthlyRecapAction
    data class ShowPlaces(val value: Boolean) : MonthlyRecapAction
    data object Export : MonthlyRecapAction
    data object Retry : MonthlyRecapAction
}

sealed interface MonthlyRecapEffect {
    data class Share(val uri: String) : MonthlyRecapEffect
}

fun interface MonthlyRecapExporter {
    suspend fun export(recap: ShareableMonthlyRecap): String
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonthlyRecapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val insights: InsightsRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
    private val exporter: MonthlyRecapExporter,
) : ViewModel() {
    private val today: LocalDate
        get() = Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(ZoneId.systemDefault()).toLocalDate()

    private val initialMonth = runCatching {
        savedStateHandle.get<Long>("monthEpochDay")
            ?.takeIf { it > 0 }
            ?.let(LocalDate::ofEpochDay)
            ?.let(YearMonth::from)
    }.getOrNull()
        ?.coerceAtMost(YearMonth.from(today))
        ?: YearMonth.from(today)
    private val selectedMonth = MutableStateFlow(initialMonth)
    private val reloadToken = MutableStateFlow(0)
    private val _state = MutableStateFlow(MonthlyRecapState(month = initialMonth))
    val state = _state.asStateFlow()
    private val effectChannel = Channel<MonthlyRecapEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(selectedMonth, reloadToken) { month, _ -> month }
                .flatMapLatest { month ->
                    val range = month.toRecapRange(today)
                    _state.update { it.copy(month = month, isLoading = true, errorMessage = null) }
                    combine(
                        transactions.observeRecapTransactions(range),
                        insights.observePeriod(InsightsQuery(range)),
                        categories.observe(includeArchived = true),
                    ) { rows, period, categoryRows ->
                        buildMonthlyRecap(month, period, rows, categoryRows.associate { it.id.value to it.name })
                    }
                        .catch { error ->
                            _state.update {
                                it.copy(isLoading = false, errorMessage = error.message ?: "Không thể tạo tổng kết tháng")
                            }
                        }
                    }
                .collect { recap ->
                    _state.update { it.copy(recap = recap, isLoading = false, errorMessage = null) }
                }
        }
    }

    fun onAction(action: MonthlyRecapAction) {
        when (action) {
            MonthlyRecapAction.PreviousMonth -> selectedMonth.update { it.minusMonths(1) }
            MonthlyRecapAction.NextMonth -> selectedMonth.update { current ->
                minOf(current.plusMonths(1), YearMonth.from(today))
            }
            is MonthlyRecapAction.ShowAmounts -> updatePrivacy { it.copy(showAmounts = action.value) }
            is MonthlyRecapAction.ShowNotes -> updatePrivacy { it.copy(showNotes = action.value) }
            is MonthlyRecapAction.ShowPlaces -> updatePrivacy { it.copy(showPlaces = action.value) }
            MonthlyRecapAction.Export -> export()
            MonthlyRecapAction.Retry -> reloadToken.update(Int::inc)
        }
    }

    private fun updatePrivacy(transform: (RecapPrivacy) -> RecapPrivacy) {
        _state.update { it.copy(privacy = transform(it.privacy)) }
    }

    private fun export() {
        val recap = _state.value.recap ?: return
        if (_state.value.isExporting) return
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, errorMessage = null) }
            runCatching { exporter.export(recap.redacted(_state.value.privacy)) }
                .onSuccess { uri -> effectChannel.send(MonthlyRecapEffect.Share(uri)) }
                .onFailure { error ->
                    _state.update { it.copy(errorMessage = error.message ?: "Không thể tạo ảnh tổng kết") }
                }
            _state.update { it.copy(isExporting = false) }
        }
    }
}
