package com.billlocket.app.feature.capture

import android.content.Context
import android.content.SharedPreferences
import com.billlocket.app.domain.port.*
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

fun interface CameraCaptureTarget {
    fun outputFile(staged: StagedMedia): File
}

interface PhotoImportSource {
    suspend fun stage(uri: String, operationId: MediaOperationId, imageId: MediaImageId): Outcome<StagedMedia>
}

enum class CaptureLocationChoice { UNKNOWN, ENABLED, DISABLED }

interface CaptureLocationPreference {
    fun get(): CaptureLocationChoice
    fun set(choice: CaptureLocationChoice)
}

@Singleton
class SharedPreferencesCaptureLocationPreference @Inject constructor(
    private val preferences: SharedPreferences,
) : CaptureLocationPreference {
    override fun get(): CaptureLocationChoice = runCatching {
        CaptureLocationChoice.valueOf(preferences.getString(KEY, null) ?: CaptureLocationChoice.UNKNOWN.name)
    }.getOrDefault(CaptureLocationChoice.UNKNOWN)

    override fun set(choice: CaptureLocationChoice) {
        preferences.edit().putString(KEY, choice.name).apply()
    }

    private companion object { const val KEY = "capture.auto_location_choice" }
}

@Singleton
class AndroidPhotoImportSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStore: MediaStorePort,
) : PhotoImportSource {
    override suspend fun stage(
        uri: String,
        operationId: MediaOperationId,
        imageId: MediaImageId,
    ): Outcome<StagedMedia> {
        val parsed = runCatching { android.net.Uri.parse(uri) }.getOrNull()
            ?: return Outcome.Failure(DomainFailure.StorageUnavailable("photo_picker_uri"))
        val input = try {
            context.contentResolver.openInputStream(parsed)
        } catch (_: SecurityException) {
            null
        } catch (_: java.io.IOException) {
            null
        } ?: return Outcome.Failure(DomainFailure.StorageUnavailable("photo_picker_open"))
        return mediaStore.stageImport(operationId, imageId, context.contentResolver.getType(parsed), input)
    }
}
