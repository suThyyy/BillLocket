package com.billlocket.app.domain.model

import java.time.ZoneId

data class DateRange(
    val startEpochDay: Long,
    val endInclusiveEpochDay: Long,
) {
    init {
        require(startEpochDay <= endInclusiveEpochDay) { "Date range must be ordered" }
    }
}

data class InstantAtZone(
    val epochMillis: Long,
    val localDateEpochDay: Long,
    val zoneId: String,
) {
    init {
        require(zoneId.isNotBlank()) { "Zone ID must not be blank" }
        ZoneId.of(zoneId)
    }
}

data class PageCursor(
    val epochMillis: Long,
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "Cursor ID must not be blank" }
    }
}

data class PageRequest(
    val limit: Int = DEFAULT_LIMIT,
    val before: PageCursor? = null,
) {
    init {
        require(limit in 1..MAX_LIMIT) { "Page limit must be between 1 and $MAX_LIMIT" }
    }

    companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 200
    }
}

data class Page<T>(
    val items: List<T>,
    val next: PageRequest?,
)
