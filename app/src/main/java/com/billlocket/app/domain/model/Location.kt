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
