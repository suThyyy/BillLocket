package com.billlocket.app.domain.model

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude" }
    }
}

fun isValidCoordinate(latitude: Double?, longitude: Double?): Boolean {
    if (latitude == null || longitude == null) return false
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
    if (latitude == 0.0 && longitude == 0.0) return false
    return true
}

data class PhotoLocation(
    val point: GeoPoint,
    val source: LocationSource,
    val confirmedAtEpochMillis: Long,
    val accuracyMeters: Double? = null,
    val placeName: String? = null,
) {
    init {
        require(confirmedAtEpochMillis >= 0) { "Confirmation time must be non-negative" }
        require(accuracyMeters == null || (accuracyMeters.isFinite() && accuracyMeters > 0.0)) {
            "Accuracy must be positive when present"
        }
        require(placeName == null || placeName.length <= 256) { "Place name is too long" }
    }
}
