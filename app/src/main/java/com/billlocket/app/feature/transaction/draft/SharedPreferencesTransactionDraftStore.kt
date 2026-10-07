package com.billlocket.app.feature.transaction.draft

import android.content.SharedPreferences
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.CandidateLocation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class SharedPreferencesTransactionDraftStore @Inject constructor(
    private val preferences: SharedPreferences,
) : TransactionDraftStore {
    override suspend fun load(id: DraftId): TransactionDraft? = withContext(Dispatchers.IO) {
        preferences.getString(key(id), null)?.let { encoded ->
            runCatching { JSONObject(encoded).toDraft(id) }.getOrNull()
        }
    }

    override suspend fun save(draft: TransactionDraft) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString(key(draft.id), draft.toJson().toString()).commit())
    }

    override suspend fun delete(id: DraftId) = withContext(Dispatchers.IO) {
        check(preferences.edit().remove(key(id)).commit())
    }

    private fun key(id: DraftId) = "transaction_draft.${id.value}"
}

private fun TransactionDraft.toJson() = JSONObject().apply {
    put("sourceTransactionId", sourceTransactionId?.value)
    put("type", type.name)
    put("amountText", amountText)
    put("categoryId", categoryId?.value)
    put("accountId", accountId?.value)
    put("note", note)
    put("occurredAt", occurred.epochMillis)
    put("epochDay", occurred.localDateEpochDay)
    put("zoneId", occurred.zoneId)
    put("dirty", dirty)
    put("updatedAt", updatedAtEpochMillis)
    put("images", JSONArray().apply { images.forEach { put(it.toJson()) } })
}

private fun DraftImage.toJson() = JSONObject().apply {
    put("id", id)
    put("kind", kind.name)
    put("previewUri", previewUri)
    put("mediaUri", mediaUri)
    put("operationId", operationId)
    put("mimeType", mimeType)
    put("widthPx", widthPx)
    put("heightPx", heightPx)
    put("sizeBytes", sizeBytes)
    put("capturedAt", capturedAt)
    put("checksum", checksumSha256)
    put("candidateLat", locationCandidate?.latitude)
    put("candidateLon", locationCandidate?.longitude)
    put("candidateSource", locationCandidate?.source?.name)
    put("locationLat", confirmedLocation?.point?.latitude)
    put("locationLon", confirmedLocation?.point?.longitude)
    put("locationSource", confirmedLocation?.source?.name)
    put("locationConfirmedAt", confirmedLocation?.confirmedAtEpochMillis)
    put("locationAccuracy", confirmedLocation?.accuracyMeters)
    put("placeName", confirmedLocation?.placeName)
    put("missing", isMissing)
}

private fun JSONObject.toDraft(id: DraftId): TransactionDraft = TransactionDraft(
    id = id,
    sourceTransactionId = stringOrNull("sourceTransactionId")?.let(::TransactionId),
    type = TransactionType.valueOf(getString("type")),
    amountText = getString("amountText"),
    categoryId = stringOrNull("categoryId")?.let(::CategoryId),
    accountId = stringOrNull("accountId")?.let(::AccountId),
    note = getString("note"),
    occurred = InstantAtZone(getLong("occurredAt"), getLong("epochDay"), getString("zoneId")),
    images = getJSONArray("images").let { array -> List(array.length()) { array.getJSONObject(it).toDraftImage() } },
    dirty = getBoolean("dirty"),
    updatedAtEpochMillis = getLong("updatedAt"),
)

private fun JSONObject.toDraftImage(): DraftImage {
    val confirmedLocation = doubleOrNull("locationLat")?.let { latitude ->
        PhotoLocation(
            point = GeoPoint(latitude, getDouble("locationLon")),
            source = LocationSource.valueOf(getString("locationSource")),
            confirmedAtEpochMillis = getLong("locationConfirmedAt"),
            accuracyMeters = doubleOrNull("locationAccuracy"),
            placeName = stringOrNull("placeName"),
        )
    }
    val candidate = doubleOrNull("candidateLat")?.let { latitude ->
        CandidateLocation(
            latitude = latitude,
            longitude = getDouble("candidateLon"),
            source = LocationSource.valueOf(getString("candidateSource")),
        )
    }
    return DraftImage(
        id = getString("id"),
        kind = DraftImageKind.valueOf(getString("kind")),
        previewUri = getString("previewUri"),
        mediaUri = getString("mediaUri"),
        operationId = stringOrNull("operationId"),
        mimeType = getString("mimeType"),
        widthPx = getInt("widthPx"),
        heightPx = getInt("heightPx"),
        sizeBytes = getLong("sizeBytes"),
        capturedAt = longOrNull("capturedAt"),
        checksumSha256 = stringOrNull("checksum"),
        locationCandidate = candidate,
        confirmedLocation = confirmedLocation,
        isMissing = optBoolean("missing", false),
    )
}

private fun JSONObject.stringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)

private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)

private fun JSONObject.doubleOrNull(name: String): Double? = if (isNull(name)) null else getDouble(name)
