package com.billlocket.app.feature.transaction.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.repository.*
import com.billlocket.app.domain.result.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TransactionSearchState(
    val query: String = "",
    val type: TransactionType? = null,
    val accountId: AccountId? = null,
    val categoryId: CategoryId? = null,
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val results: List<TransactionSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface TransactionSearchAction {
    data class QueryChanged(val value: String) : TransactionSearchAction
    data class TypeChanged(val value: TransactionType?) : TransactionSearchAction
    data class AccountChanged(val value: AccountId?) : TransactionSearchAction
    data class CategoryChanged(val value: CategoryId?) : TransactionSearchAction
    data class DateChanged(val start: Boolean, val epochDay: Long?) : TransactionSearchAction
    data object PickStartDate : TransactionSearchAction
    data object PickEndDate : TransactionSearchAction
}

sealed interface TransactionSearchEffect {
    data class LaunchDatePicker(val start: Boolean) : TransactionSearchEffect
}

@HiltViewModel
class TransactionSearchViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TransactionSearchState())
    val state = _state.asStateFlow()
    private val _effects = MutableSharedFlow<TransactionSearchEffect>()
    val effects = _effects.asSharedFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            combine(accounts.observeAll(), categories.observe()) { accountList, categoryList -> accountList to categoryList }
                .collect { (accountList, categoryList) ->
                    _state.value = _state.value.copy(accounts = accountList, categories = categoryList)
                }
        }
        refresh(immediate = true)
    }

    fun onAction(action: TransactionSearchAction) {
        when (action) {
            is TransactionSearchAction.QueryChanged -> if (action.value.length <= 200) {
                _state.value = _state.value.copy(query = action.value)
                refresh()
            }
            is TransactionSearchAction.TypeChanged -> update { copy(type = action.value, categoryId = null) }
            is TransactionSearchAction.AccountChanged -> update { copy(accountId = action.value) }
            is TransactionSearchAction.CategoryChanged -> update { copy(categoryId = action.value) }
            is TransactionSearchAction.DateChanged -> update {
                if (action.start) copy(startEpochDay = action.epochDay) else copy(endEpochDay = action.epochDay)
            }
            TransactionSearchAction.PickStartDate -> emit(TransactionSearchEffect.LaunchDatePicker(true))
            TransactionSearchAction.PickEndDate -> emit(TransactionSearchEffect.LaunchDatePicker(false))
        }
    }

    private fun update(transform: TransactionSearchState.() -> TransactionSearchState) {
        _state.value = _state.value.transform()
        refresh(immediate = true)
    }

    private fun refresh(immediate: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediate) delay(250)
            val current = _state.value
            val range = when {
                current.startEpochDay != null && current.endEpochDay != null && current.startEpochDay <= current.endEpochDay ->
                    DateRange(current.startEpochDay, current.endEpochDay)
                current.startEpochDay != null && current.endEpochDay == null -> DateRange(current.startEpochDay, Long.MAX_VALUE)
                current.endEpochDay != null && current.startEpochDay == null -> DateRange(Long.MIN_VALUE, current.endEpochDay)
                else -> null
            }
            _state.value = current.copy(isLoading = true, errorMessage = null)
            when (val result = transactions.page(
                TransactionFilter(
                    range = range,
                    type = current.type,
                    accountId = current.accountId,
                    categoryId = current.categoryId,
                    noteQuery = current.query.trim().takeIf(String::isNotEmpty),
                ),
                PageRequest(limit = PageRequest.MAX_LIMIT),
            )) {
                is Outcome.Success -> _state.value = _state.value.copy(results = result.value.items, isLoading = false)
                is Outcome.Failure -> _state.value = _state.value.copy(isLoading = false, errorMessage = "Không thể tải giao dịch")
            }
        }
    }

    private fun emit(effect: TransactionSearchEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
