package com.billlocket.app.domain.port

import com.billlocket.app.domain.model.LocationSource
import com.billlocket.app.domain.result.Outcome
import java.io.InputStream

fun interface Clock { fun nowEpochMillis(): Long }
fun interface IdGenerator { fun nextId(): String }
fun interface WidgetUpdatePort { suspend fun requestRefresh(): Outcome<Unit> }
fun interface NotificationPort { suspend fun publishBudgetThreshold(budgetId: String, percent: Int): Outcome<Unit> }
fun interface MediaCleanupPort { suspend fun deleteOwned(uris: List<String>): Outcome<Unit> }

data class MediaOperationId(val value: String)

data class MediaImageId(val value: String)

data class StagedMedia(
    val operationId: MediaOperationId,
    val imageId: MediaImageId,
)

data class CandidateLocation(
    val latitude: Double,
    val longitude: Double,
    val source: LocationSource = LocationSource.EXIF,
)

data class DraftInspection(
    val widthPx: Int,
    val heightPx: Int,
    val mimeType: String,
    val orientationDegrees: Int,
    val mirrored: Boolean,
    val capturedAtEpochMillis: Long?,
    val locationCandidate: CandidateLocation?,
)

data class NormalizedMedia(
    val staged: StagedMedia,
    val canonicalUri: String,
    val widthPx: Int,
    val heightPx: Int,
    val mimeType: String,
    val sizeBytes: Long,
    val checksumSha256: String,
    val capturedAtEpochMillis: Long?,
    val locationCandidate: CandidateLocation?,
)

data class MediaStorageUsage(
    val authoritativeBytes: Long,
    val temporaryBytes: Long,
    val cacheBytes: Long,
)

data class MediaReconciliationReport(
    val removedStagingFiles: Int,
    val removedOrphanFiles: Int,
    val removedTrashOrCacheFiles: Int,
    val missingReferencedFiles: Int,
    val protectedReferencedFiles: Int,
)

interface MediaStorePort : MediaCleanupPort {
    suspend fun stageCapture(operationId: MediaOperationId, imageId: MediaImageId): Outcome<StagedMedia>
    suspend fun stageImport(
        operationId: MediaOperationId,
        imageId: MediaImageId,
        declaredMimeType: String?,
        input: InputStream,
    ): Outcome<StagedMedia>
    suspend fun inspectDraft(staged: StagedMedia): Outcome<DraftInspection>
    suspend fun normalize(staged: StagedMedia): Outcome<NormalizedMedia>
    fun previewUri(staged: StagedMedia): String
    suspend fun discardStaged(staged: StagedMedia): Outcome<Unit>
    suspend fun promote(media: NormalizedMedia): Outcome<String>
    suspend fun markCommitted(staged: StagedMedia): Outcome<Unit>
    suspend fun exists(canonicalUri: String): Boolean
    suspend fun openRead(canonicalUri: String): Outcome<InputStream>
    suspend fun moveToTrash(canonicalUri: String, operationId: MediaOperationId): Outcome<Unit>
    suspend fun prepareShare(canonicalUri: String): Outcome<String>
    suspend fun reconcile(nowEpochMillis: Long): Outcome<MediaReconciliationReport>
    suspend fun measureStorage(): Outcome<MediaStorageUsage>
}

data class BackupOptions(val includeImages: Boolean = true, val includeLocations: Boolean = true)
data class BackupSummary(val accountCount: Int, val transactionCount: Int, val imageCount: Int, val includesImages: Boolean, val includesLocations: Boolean)

interface BackupPort {
    suspend fun export(options: BackupOptions, destination: java.io.OutputStream): Outcome<BackupSummary>
    suspend fun inspect(source: java.io.InputStream): Outcome<BackupSummary>
    suspend fun importReplace(source: java.io.InputStream): Outcome<BackupSummary>
}
