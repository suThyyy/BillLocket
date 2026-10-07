package com.billlocket.app.domain.model

data class AccountRef(
    val id: AccountId,
    val type: AccountType,
    val isActive: Boolean,
    val currency: CurrencyCode,
)

data class CategoryRef(
    val id: CategoryId,
    val type: TransactionType,
    val isActive: Boolean,
)

data class ImageInput(
    val id: TransactionImageId,
    val mediaUri: String,
    val mimeType: String = "image/jpeg",
    val widthPx: Int = 1,
    val heightPx: Int = 1,
    val sizeBytes: Long = 0,
    val capturedAt: Long? = null,
    val location: PhotoLocation? = null,
) {
    init {
        require(mediaUri.startsWith("content://com.billlocket.app.media/images/")) {
            "Image URI must be app-owned"
        }
        require(mimeType.startsWith("image/")) { "Image MIME type is required" }
        require(widthPx > 0 && heightPx > 0) { "Image dimensions must be positive" }
        require(sizeBytes >= 0) { "Image size must not be negative" }
    }
}

data class UpdateTransactionCommand(
    val id: TransactionId,
    val type: TransactionType,
    val amount: Money,
    val category: CategoryRef,
    val account: AccountRef,
    val note: String?,
    val occurred: InstantAtZone,
    val images: List<ImageInput>,
)

fun UpdateTransactionCommand.asCreateCommand() =
    CreateTransactionCommand(type, amount, category, account, note, occurred, images)

data class CreateTransactionCommand(
    val type: TransactionType,
    val amount: Money,
    val category: CategoryRef,
    val account: AccountRef,
    val note: String?,
    val occurred: InstantAtZone,
    val images: List<ImageInput>,
)

data class CreateTransferCommand(
    val from: AccountRef,
    val to: AccountRef,
    val amount: Money,
    val note: String?,
    val occurred: InstantAtZone,
)

data class SaveBudgetCommand(
    val monthStartEpochDay: Long,
    val category: CategoryRef?,
    val limit: Money,
    val warningPercent: Int = 80,
)

data class TransactionFilter(
    val range: DateRange? = null,
    val type: TransactionType? = null,
    val accountId: AccountId? = null,
    val categoryId: CategoryId? = null,
    val noteQuery: String? = null,
) {
    init {
        require(noteQuery == null || noteQuery.trim().length <= 200) { "Search query is too long" }
    }
}
