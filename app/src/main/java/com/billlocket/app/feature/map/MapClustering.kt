package com.billlocket.app.feature.map

import com.billlocket.app.domain.model.GeoPoint
import com.billlocket.app.domain.model.MapPhoto
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class MapCluster(val id: String, val center: GeoPoint, val photos: List<MapPhoto>)

enum class MapClusterZoomTier(val maximumDistanceMeters: Double) {
    FAR(12_000.0),
    OUT(3_500.0),
    MEDIUM(900.0),
    NEAR(250.0),
    CLOSE(60.0),
    DETAIL(0.0),
}

fun mapClusterZoomTier(zoom: Float): MapClusterZoomTier = when {
    zoom < 10.0f -> MapClusterZoomTier.FAR
    zoom < 12.0f -> MapClusterZoomTier.OUT
    zoom < 14.5f -> MapClusterZoomTier.MEDIUM
    zoom < 16.5f -> MapClusterZoomTier.NEAR
    zoom < 18.0f -> MapClusterZoomTier.CLOSE
    else -> MapClusterZoomTier.DETAIL
}

/**
 * Builds stable display clusters in two steps: conservative real-place grouping,
 * followed by zoom-dependent spatial grouping. BillLocket has no provider place ID,
 * so place names, transaction identity, GPS accuracy and distance are only fallbacks.
 */
fun clusterMapPhotos(
    photos: List<MapPhoto>,
    zoomTier: MapClusterZoomTier = MapClusterZoomTier.MEDIUM,
): List<MapCluster> {
    if (photos.isEmpty()) return emptyList()
    val places = groupMapPhotosByPlace(photos)
    val groups = if (zoomTier == MapClusterZoomTier.DETAIL) {
        places.indices.map(::listOf)
    } else {
        unionGroups(places.size) { left, right ->
            geoDistanceMeters(places[left].center, places[right].center) <= zoomTier.maximumDistanceMeters
        }
    }
    return groups.map { indices ->
        val clusteredPhotos = indices.flatMap { places[it].photos }.sortedBy { it.imageId.value }
        MapCluster(
            id = clusteredPhotos.joinToString("|") { it.imageId.value },
            center = centerOf(clusteredPhotos),
            photos = clusteredPhotos,
        )
    }.sortedBy(MapCluster::id)
}

internal data class MapPlaceGroup(val center: GeoPoint, val photos: List<MapPhoto>)

internal fun groupMapPhotosByPlace(photos: List<MapPhoto>): List<MapPlaceGroup> {
    val sorted = photos.sortedBy { it.imageId.value }
    val groups = unionGroups(sorted.size) { left, right -> sameRealPlace(sorted[left], sorted[right]) }
    return groups.map { indices ->
        val placePhotos = indices.map(sorted::get).sortedBy { it.imageId.value }
        MapPlaceGroup(centerOf(placePhotos), placePhotos)
    }
}

internal fun sameRealPlace(left: MapPhoto, right: MapPhoto): Boolean {
    val distance = geoDistanceMeters(left.location.point, right.location.point)
    val leftName = left.location.placeName.normalizedPlaceName()
    val rightName = right.location.placeName.normalizedPlaceName()
    val accuracyAllowance = maxOf(left.location.accuracyMeters ?: 0.0, right.location.accuracyMeters ?: 0.0)

    if (left.transactionId == right.transactionId) {
        return distance <= maxOf(20.0, accuracyAllowance.coerceAtMost(35.0))
    }
    if (leftName != null && rightName != null) {
        if (leftName != rightName) return false
        return distance <= maxOf(20.0, accuracyAllowance.coerceAtMost(40.0))
    }
    return distance <= 8.0
}

internal fun geoDistanceMeters(left: GeoPoint, right: GeoPoint): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(left.latitude)
    val lat2 = Math.toRadians(right.latitude)
    val deltaLat = lat2 - lat1
    val deltaLng = Math.toRadians(right.longitude - left.longitude)
    val haversine = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(lat1) * cos(lat2) * sin(deltaLng / 2) * sin(deltaLng / 2)
    return earthRadiusMeters * 2 * atan2(sqrt(haversine), sqrt((1 - haversine).coerceAtLeast(0.0)))
}

private fun centerOf(photos: List<MapPhoto>) = GeoPoint(
    photos.map { it.location.point.latitude }.average(),
    photos.map { it.location.point.longitude }.average(),
)

private fun String?.normalizedPlaceName(): String? = this
    ?.trim()
    ?.lowercase(Locale.ROOT)
    ?.replace(Regex("\\s+"), " ")
    ?.takeIf(String::isNotEmpty)

private fun unionGroups(size: Int, shouldUnion: (Int, Int) -> Boolean): List<List<Int>> {
    if (size == 0) return emptyList()
    val parents = IntArray(size) { it }

    fun root(index: Int): Int {
        var current = index
        while (parents[current] != current) {
            parents[current] = parents[parents[current]]
            current = parents[current]
        }
        return current
    }

    for (left in 0 until size) {
        for (right in left + 1 until size) {
            if (shouldUnion(left, right)) {
                val leftRoot = root(left)
                val rightRoot = root(right)
                if (leftRoot != rightRoot) parents[rightRoot] = leftRoot
            }
        }
    }
    return (0 until size).groupBy(::root).values.map { it.sorted() }
}
