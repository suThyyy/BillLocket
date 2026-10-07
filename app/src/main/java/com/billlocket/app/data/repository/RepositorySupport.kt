package com.billlocket.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import com.billlocket.app.data.local.entity.AccountEntity
import com.billlocket.app.data.local.entity.CategoryEntity
import com.billlocket.app.data.local.entity.TransactionImageEntity
import com.billlocket.app.data.local.entity.TransferEntity
import com.billlocket.app.data.local.model.TransactionWithImages
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import kotlinx.coroutines.CancellationException

internal suspend fun <T> persistenceOutcome(
    operation: String,
    block: suspend () -> T,
): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SQLiteConstraintException) {
        Outcome.Failure(DomainFailure.Conflict("constraint:$operation"))
    } catch (_: SQLiteException) {
        Outcome.Failure(DomainFailure.DatabaseUnavailable(operation))
    } catch (_: IllegalStateException) {
        Outcome.Failure(DomainFailure.Conflict("stale:$operation"))
    }

internal fun AccountEntity.toDomain() =
    Account(
        AccountId(id),
        name,
        AccountType.valueOf(type),
        Money(openingBalanceMinor, CurrencyCode.of(currencyCode)),
        sortOrder,
        isArchived,
    )

internal fun CategoryEntity.toDomain() =
    Category(
        CategoryId(id),
        name,
        TransactionType.valueOf(transactionType),
        colorArgb,
        iconKey,
        sortOrder,
        isDefault,
        isArchived,
    )

internal fun TransferEntity.toDomain() =
    Transfer(
        TransferId(id),
        AccountId(fromAccountId),
        AccountId(toAccountId),
        Money(amountMinor, CurrencyCode.of(currencyCode)),
        note,
        InstantAtZone(occurredAt, localDateEpochDay, zoneId),
        deletedAt,
    )

internal fun TransactionImageEntity.toDomain() =
    TransactionImage(
        id = TransactionImageId(id),
        mediaUri = contentUri,
        position = position,
        mimeType = mimeType,
        widthPx = widthPx,
        heightPx = heightPx,
        sizeBytes = sizeBytes,
        capturedAt = capturedAt,
        location =
            latitude?.let { lat ->
                PhotoLocation(
                    point = GeoPoint(lat, requireNotNull(longitude)),
                    source = LocationSource.valueOf(requireNotNull(locationSource)),
                    confirmedAtEpochMillis = requireNotNull(locationConfirmedAt),
                    accuracyMeters = accuracyMeters,
                    placeName = placeName,
                )
            },
    )

internal fun TransactionWithImages.toDomain() =
    TransactionDetails(
        id = TransactionId(transaction.id),
        type = TransactionType.valueOf(transaction.type),
        amount = Money(transaction.amountMinor, CurrencyCode.of(transaction.currencyCode)),
        categoryId = CategoryId(transaction.categoryId),
        accountId = AccountId(transaction.accountId),
        note = transaction.note,
        occurred = InstantAtZone(transaction.occurredAt, transaction.localDateEpochDay, transaction.zoneId),
        images = images.sortedWith(compareBy(TransactionImageEntity::position, TransactionImageEntity::id)).map { it.toDomain() },
        deletedAt = transaction.deletedAt,
    )
