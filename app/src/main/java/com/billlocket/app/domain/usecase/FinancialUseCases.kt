package com.billlocket.app.domain.usecase

import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.MediaCleanupPort
import com.billlocket.app.domain.port.MediaStorePort
import com.billlocket.app.domain.port.NormalizedMedia
import com.billlocket.app.domain.port.NotificationPort
import com.billlocket.app.domain.port.WidgetUpdatePort
import com.billlocket.app.domain.repository.*
import com.billlocket.app.domain.result.Outcome
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class CreateTransaction @Inject constructor(
    private val repository: TransactionRepository,
    private val widget: WidgetUpdatePort,
) {
    suspend operator fun invoke(command: CreateTransactionCommand): Outcome<TransactionId> {
        val result = repository.create(command)
        if (result is Outcome.Success) widget.requestRefresh()
        return result
    }
}

data class PreparedTransactionImage(
    val media: NormalizedMedia,
    val confirmedLocation: PhotoLocation? = null,
)

/** Coordinates file-first creation while preserving the independent no-image financial path. */
class CreateTransactionWithMedia @Inject constructor(
    private val repository: TransactionRepository,
    private val mediaStore: MediaStorePort,
    private val widget: WidgetUpdatePort,
) {
    suspend operator fun invoke(
        command: CreateTransactionCommand,
        preparedImages: List<PreparedTransactionImage>,
    ): Outcome<TransactionId> {
        if (preparedImages.isEmpty()) {
            val result = repository.create(command.copy(images = emptyList()))
            if (result is Outcome.Success) widget.requestRefresh()
            return result
        }
        val promoted = mutableListOf<String>()
        preparedImages.forEach { prepared ->
            when (val result = mediaStore.promote(prepared.media)) {
                is Outcome.Failure -> {
                    mediaStore.deleteOwned(promoted)
                    return result
                }
                is Outcome.Success -> promoted += result.value
            }
        }
        val images = preparedImages.mapIndexed { index, prepared ->
            val value = prepared.media
            ImageInput(
                id = TransactionImageId(value.staged.imageId.value),
                mediaUri = promoted[index],
                mimeType = value.mimeType,
                widthPx = value.widthPx,
                heightPx = value.heightPx,
                sizeBytes = value.sizeBytes,
                capturedAt = value.capturedAtEpochMillis,
                location = prepared.confirmedLocation,
            )
        }
        return when (val stored = repository.create(command.copy(images = images))) {
            is Outcome.Failure -> {
                mediaStore.deleteOwned(promoted)
                stored
            }
            is Outcome.Success -> {
                preparedImages.forEach { mediaStore.markCommitted(it.media.staged) }
                widget.requestRefresh()
                stored
            }
        }
    }
}

class UpdateTransaction @Inject constructor(
    private val repository: TransactionRepository,
    private val widget: WidgetUpdatePort,
) {
    suspend operator fun invoke(command: UpdateTransactionCommand): Outcome<Unit> {
        val result = repository.update(command)
        if (result is Outcome.Success) widget.requestRefresh()
        return result
    }
}

sealed interface TransactionMediaUpdate {
    data class Existing(val image: ImageInput) : TransactionMediaUpdate
    data class Prepared(val image: PreparedTransactionImage) : TransactionMediaUpdate
}

class UpdateTransactionWithMedia @Inject constructor(
    private val repository: TransactionRepository,
    private val mediaStore: MediaStorePort,
    private val widget: WidgetUpdatePort,
) {
    suspend operator fun invoke(
        command: UpdateTransactionCommand,
        orderedImages: List<TransactionMediaUpdate>,
    ): Outcome<Unit> {
        val promoted = mutableListOf<String>()
        val finalImages = mutableListOf<ImageInput>()
        val prepared = mutableListOf<PreparedTransactionImage>()
        orderedImages.forEach { item ->
            when (item) {
                is TransactionMediaUpdate.Existing -> finalImages += item.image
                is TransactionMediaUpdate.Prepared -> {
                    when (val result = mediaStore.promote(item.image.media)) {
                        is Outcome.Failure -> {
                            mediaStore.deleteOwned(promoted)
                            return result
                        }
                        is Outcome.Success -> {
                            promoted += result.value
                            prepared += item.image
                            val media = item.image.media
                            finalImages += ImageInput(
                                id = TransactionImageId(media.staged.imageId.value),
                                mediaUri = result.value,
                                mimeType = media.mimeType,
                                widthPx = media.widthPx,
                                heightPx = media.heightPx,
                                sizeBytes = media.sizeBytes,
                                capturedAt = media.capturedAtEpochMillis,
                                location = item.image.confirmedLocation,
                            )
                        }
                    }
                }
            }
        }
        return when (val stored = repository.update(command.copy(images = finalImages))) {
            is Outcome.Failure -> {
                mediaStore.deleteOwned(promoted)
                stored
            }
            is Outcome.Success -> {
                prepared.forEach { mediaStore.markCommitted(it.media.staged) }
                widget.requestRefresh()
                stored
            }
        }
    }
}

data class DeleteUndoToken(val transactionId: TransactionId, val deadlineEpochMillis: Long)

class DeleteTransaction @Inject constructor(
    private val repository: TransactionRepository,
    private val widget: WidgetUpdatePort,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: TransactionId, undoWindowMillis: Long = 5_000): Outcome<DeleteUndoToken> {
        val deletedAt = clock.nowEpochMillis()
        return when (val result = repository.markDeleted(id, deletedAt)) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                widget.requestRefresh()
                Outcome.Success(DeleteUndoToken(id, Math.addExact(deletedAt, undoWindowMillis)))
            }
        }
    }
}

class UndoDeleteTransaction @Inject constructor(
    private val repository: TransactionRepository,
    private val widget: WidgetUpdatePort,
    private val clock: Clock,
) {
    suspend operator fun invoke(token: DeleteUndoToken): Outcome<Unit> {
        if (clock.nowEpochMillis() > token.deadlineEpochMillis) return com.billlocket.app.domain.result.Outcome.Failure(com.billlocket.app.domain.result.DomainFailure.Conflict("undo_expired"))
        val result = repository.restore(token.transactionId)
        if (result is Outcome.Success) widget.requestRefresh()
        return result
    }
}

class FinalizeTransactionDeletion @Inject constructor(
    private val repository: TransactionRepository,
    private val media: MediaCleanupPort,
) {
    suspend operator fun invoke(id: TransactionId): Outcome<Unit> =
        when (val purged = repository.purgeDeleted(id)) {
            is Outcome.Failure -> purged
            is Outcome.Success -> media.deleteOwned(purged.value.mediaUris)
        }
}

class CreateTransfer @Inject constructor(private val repository: TransferRepository) {
    suspend operator fun invoke(command: CreateTransferCommand) = repository.create(command)
}

class SaveMonthlyBudget @Inject constructor(private val repository: BudgetRepository) {
    suspend operator fun invoke(command: SaveBudgetCommand) = repository.upsert(command)
}

class EvaluateBudgetThresholds @Inject constructor(
    private val budgets: BudgetRepository,
    private val notifications: NotificationPort,
    private val clock: Clock,
) {
    suspend operator fun invoke(monthStartEpochDay: Long): Outcome<Unit> {
        budgets.observeMonth(monthStartEpochDay).first().forEach { budget ->
            val percent = if (budget.limit.minorUnits <= 0) 0 else ((budget.spent.minorUnits.toDouble() / budget.limit.minorUnits) * 100).toInt()
            if (percent < budget.warningPercent) budgets.resetThresholdIfBelow(budget.id, budget.warningPercent)
            if (percent < 100) budgets.resetThresholdIfBelow(budget.id, 100)
        }
        return when (val pending = budgets.pendingThresholds(monthStartEpochDay)) {
            is Outcome.Failure -> pending
            is Outcome.Success -> {
                pending.value.forEach { crossing ->
                    if (notifications.publishBudgetThreshold(crossing.budgetId.value, crossing.percent) is Outcome.Success) {
                        budgets.markThresholdDelivered(crossing.budgetId, crossing.percent, clock.nowEpochMillis())
                    }
                }
                Outcome.Success(Unit)
            }
        }
    }
}

class ObserveSettings @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke() = repository.settings
}

class UpdateTheme @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(mode: ThemeMode) = repository.updateTheme(mode)
}

class UpdateAmountVisibility @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(value: AmountVisibility) = repository.updateAmountVisibility(value)
}

class UpdateWidgetSettings @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(value: WidgetSettings) = repository.updateWidgetSettings(value)
}

class UpdateCurrency @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(code: String) = repository.updateCurrency(code)
}
