package com.billlocket.app.feature.transaction.draft

import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.*
import com.billlocket.app.domain.usecase.PreparedTransactionImage

enum class DraftImageKind { STAGED, EXISTING }

data class DraftImage(
    val id: String,
    val kind: DraftImageKind,
    val previewUri: String,
    val mediaUri: String,
    val operationId: String? = null,
    val mimeType: String = "image/jpeg",
    val widthPx: Int,
    val heightPx: Int,
    val sizeBytes: Long,
    val capturedAt: Long? = null,
    val checksumSha256: String? = null,
    val locationCandidate: CandidateLocation? = null,
    val confirmedLocation: PhotoLocation? = null,
    val isMissing: Boolean = false,
) {
    fun stagedMedia(): StagedMedia? = operationId?.let {
        StagedMedia(MediaOperationId(it), MediaImageId(id))
    }

    fun asPrepared(): PreparedTransactionImage? {
        val staged = stagedMedia() ?: return null
        val checksum = checksumSha256 ?: return null
        return PreparedTransactionImage(
            media = NormalizedMedia(
                staged = staged,
                canonicalUri = mediaUri,
                widthPx = widthPx,
                heightPx = heightPx,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                checksumSha256 = checksum,
                capturedAtEpochMillis = capturedAt,
                locationCandidate = locationCandidate,
            ),
            confirmedLocation = confirmedLocation,
        )
    }

    fun asExistingInput(): ImageInput? = if (kind == DraftImageKind.EXISTING) {
        ImageInput(
            id = TransactionImageId(id),
            mediaUri = mediaUri,
            mimeType = mimeType,
            widthPx = widthPx,
            heightPx = heightPx,
            sizeBytes = sizeBytes,
            capturedAt = capturedAt,
            location = confirmedLocation,
        )
    } else null

    companion object {
        fun fromNormalized(media: NormalizedMedia, previewUri: String) = DraftImage(
            id = media.staged.imageId.value,
            kind = DraftImageKind.STAGED,
            previewUri = previewUri,
            mediaUri = media.canonicalUri,
            operationId = media.staged.operationId.value,
            mimeType = media.mimeType,
            widthPx = media.widthPx,
            heightPx = media.heightPx,
            sizeBytes = media.sizeBytes,
            capturedAt = media.capturedAtEpochMillis,
            checksumSha256 = media.checksumSha256,
            locationCandidate = media.locationCandidate,
        )

        fun fromPersisted(image: TransactionImage) = DraftImage(
            id = image.id.value,
            kind = DraftImageKind.EXISTING,
            previewUri = image.mediaUri,
            mediaUri = image.mediaUri,
            mimeType = image.mimeType,
            widthPx = image.widthPx,
            heightPx = image.heightPx,
            sizeBytes = image.sizeBytes,
            capturedAt = image.capturedAt,
            confirmedLocation = image.location,
        )
    }
}

data class DraftCategorySuggestionMetadata(
    val analyzedPrimaryImageKey: String? = null,
    val suggestionSemanticKey: String? = null,
    val suggestionConfidence: Float? = null,
    val suggestionSupportingLabels: List<String> = emptyList(),
    val suggestionDismissedForImageKey: String? = null,
    val categoryWasManuallySelected: Boolean = false,
)

data class TransactionDraft(
    val id: DraftId,
    val sourceTransactionId: TransactionId? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val categoryId: CategoryId? = null,
    val accountId: AccountId? = null,
    val note: String = "",
    val occurred: InstantAtZone,
    val images: List<DraftImage> = emptyList(),
    val dirty: Boolean = false,
    val updatedAtEpochMillis: Long,
    val analyzedPrimaryImageKey: String? = null,
    val suggestionSemanticKey: String? = null,
    val suggestionConfidence: Float? = null,
    val suggestionSupportingLabels: List<String> = emptyList(),
    val suggestionDismissedForImageKey: String? = null,
    val categoryWasManuallySelected: Boolean = false,
)

interface TransactionDraftStore {
    suspend fun load(id: DraftId): TransactionDraft?
    suspend fun save(draft: TransactionDraft)
    suspend fun delete(id: DraftId)
    suspend fun updateCategorySuggestionMetadata(
        id: DraftId,
        metadata: DraftCategorySuggestionMetadata,
    ) {
        val existing = load(id) ?: return
        save(
            existing.copy(
                analyzedPrimaryImageKey = metadata.analyzedPrimaryImageKey,
                suggestionSemanticKey = metadata.suggestionSemanticKey,
                suggestionConfidence = metadata.suggestionConfidence,
                suggestionSupportingLabels = metadata.suggestionSupportingLabels,
                suggestionDismissedForImageKey = metadata.suggestionDismissedForImageKey,
                categoryWasManuallySelected = metadata.categoryWasManuallySelected,
            )
        )
    }
}
