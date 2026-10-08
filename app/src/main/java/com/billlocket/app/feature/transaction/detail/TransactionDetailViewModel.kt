package com.billlocket.app.feature.transaction.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.MediaStorePort
import com.billlocket.app.domain.repository.AccountRepository
import com.billlocket.app.domain.repository.CategoryRepository
import com.billlocket.app.domain.repository.TransactionRepository
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.billlocket.app.feature.location.AddressResolver
import com.billlocket.app.feature.location.ResolvedAddress
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DetailImage(val image: TransactionImage, val exists: Boolean)

data class TransactionDetailState(
    val transaction: TransactionDetails? = null,
    val images: List<DetailImage> = emptyList(),
    val selectedImageId: TransactionImageId? = null,
    val selectedLocation: PhotoLocation? = null,
    val categoryName: String = "",
    val categoryIconKey: String? = null,
    val accountName: String = "",
    val address: ResolvedAddress? = null,
    val isResolvingAddress: Boolean = false,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface TransactionDetailAction {
    data class SelectImage(val imageId: TransactionImageId) : TransactionDetailAction
    data object Edit : TransactionDetailAction
    data object RequestDelete : TransactionDetailAction
    data object CancelDelete : TransactionDetailAction
    data object ConfirmDelete : TransactionDetailAction
    data class UndoDelete(val token: DeleteUndoToken) : TransactionDetailAction
    data class FinishDelete(val token: DeleteUndoToken) : TransactionDetailAction
    data object Back : TransactionDetailAction
}

sealed interface TransactionDetailEffect {
    data class NavigateEdit(val transactionId: TransactionId, val draftId: DraftId) : TransactionDetailEffect
    data class ShowUndo(val token: DeleteUndoToken) : TransactionDetailEffect
    data object NavigateBack : TransactionDetailEffect
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val mediaStore: MediaStorePort,
    private val deleteTransaction: DeleteTransaction,
    private val undoDelete: UndoDeleteTransaction,
    private val finalizeDeletion: FinalizeTransactionDeletion,
    private val addressResolver: AddressResolver,
) : ViewModel() {
    private val transactionId = TransactionId(checkNotNull(savedStateHandle["transactionId"]))
    private val _state = MutableStateFlow(TransactionDetailState())
    val state = _state.asStateFlow()
    private val effectChannel = Channel<TransactionDetailEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(transactions.observe(transactionId), accounts.observeAll(true), categories.observe(null, true)) {
                transaction, accountList, categoryList -> Triple(transaction, accountList, categoryList)
            }.collect { (transaction, accountList, categoryList) ->
                if (transaction == null) {
                    _state.value = _state.value.copy(isLoading = false, notFound = true)
                } else {
                    val images = transaction.images.map { DetailImage(it, mediaStore.exists(it.mediaUri)) }
                    val currentSelectedId = _state.value.selectedImageId
                    val selectedImage = transaction.images.firstOrNull { it.id == currentSelectedId }
                        ?: transaction.images.firstOrNull()
                    val targetLocation = selectedImage?.location
                    val previousLocation = _state.value.selectedLocation
                    val cat = categoryList.firstOrNull { it.id == transaction.categoryId }
                    _state.value = _state.value.copy(
                        transaction = transaction,
                        images = images,
                        selectedImageId = selectedImage?.id,
                        selectedLocation = targetLocation,
                        categoryName = cat?.name.orEmpty(),
                        categoryIconKey = cat?.iconKey,
                        accountName = accountList.firstOrNull { it.id == transaction.accountId }?.name.orEmpty(),
                        isLoading = false,
                        notFound = false,
                        isResolvingAddress = targetLocation != null && (targetLocation != previousLocation || _state.value.address == null),
                        address = if (targetLocation == null) null else if (targetLocation == previousLocation) _state.value.address else null,
                    )
                    if (targetLocation != null && (_state.value.address == null || targetLocation != previousLocation)) {
                        viewModelScope.launch {
                            val resolved = addressResolver.resolve(targetLocation.point, targetLocation.placeName)
                            if (_state.value.selectedLocation == targetLocation) {
                                _state.value = _state.value.copy(
                                    address = resolved,
                                    isResolvingAddress = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun onAction(action: TransactionDetailAction) {
        when (action) {
            is TransactionDetailAction.SelectImage -> {
                val tx = _state.value.transaction ?: return
                val selectedImage = tx.images.firstOrNull { it.id == action.imageId } ?: return
                val targetLocation = selectedImage.location
                val previousLocation = _state.value.selectedLocation
                _state.value = _state.value.copy(
                    selectedImageId = action.imageId,
                    selectedLocation = targetLocation,
                    isResolvingAddress = targetLocation != null && (targetLocation != previousLocation || _state.value.address == null),
                    address = if (targetLocation == null) null else if (targetLocation == previousLocation) _state.value.address else null,
                )
                if (targetLocation != null && (_state.value.address == null || targetLocation != previousLocation)) {
                    viewModelScope.launch {
                        val resolved = addressResolver.resolve(targetLocation.point, targetLocation.placeName)
                        if (_state.value.selectedLocation == targetLocation) {
                            _state.value = _state.value.copy(
                                address = resolved,
                                isResolvingAddress = false,
                            )
                        }
                    }
                }
            }
            TransactionDetailAction.Edit -> emit(
                TransactionDetailEffect.NavigateEdit(transactionId, DraftId(java.util.UUID.randomUUID().toString())),
            )
            TransactionDetailAction.RequestDelete -> _state.value = _state.value.copy(showDeleteConfirmation = true)
            TransactionDetailAction.CancelDelete -> _state.value = _state.value.copy(showDeleteConfirmation = false)
            TransactionDetailAction.ConfirmDelete -> delete()
            is TransactionDetailAction.UndoDelete -> undo(action.token)
            is TransactionDetailAction.FinishDelete -> finish(action.token)
            TransactionDetailAction.Back -> emit(TransactionDetailEffect.NavigateBack)
        }
    }

    private fun delete() = viewModelScope.launch {
        if (_state.value.isDeleting) return@launch
        _state.value = _state.value.copy(isDeleting = true, showDeleteConfirmation = false)
        when (val result = deleteTransaction(transactionId)) {
            is Outcome.Success -> effectChannel.send(TransactionDetailEffect.ShowUndo(result.value))
            is Outcome.Failure -> _state.value = _state.value.copy(isDeleting = false, errorMessage = "Không thể xóa giao dịch")
        }
    }

    private fun undo(token: DeleteUndoToken) = viewModelScope.launch {
        when (undoDelete(token)) {
            is Outcome.Success -> _state.value = _state.value.copy(isDeleting = false, errorMessage = null)
            is Outcome.Failure -> finish(token)
        }
    }

    private fun finish(token: DeleteUndoToken) = viewModelScope.launch {
        finalizeDeletion(token.transactionId)
        effectChannel.send(TransactionDetailEffect.NavigateBack)
    }

    private fun emit(effect: TransactionDetailEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }
}
