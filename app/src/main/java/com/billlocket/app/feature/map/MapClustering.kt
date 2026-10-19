package com.billlocket.app.feature.map

import com.billlocket.app.domain.model.GeoPoint
import com.billlocket.app.domain.model.MapPhoto
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class MapCluster(val id: String, val center: GeoPoint, val photos: List<MapPhoto>)

enum class MapClusterZoomTier {
    FAR,
    OUT,
    /** Matches the pre-dynamic-clustering 0.01-degree grid at the default zoom. */
    MEDIUM,
    NEAR,
    CLOSE,
    DETAIL,
}

fun mapClusterZoomTier(zoom: Float): MapClusterZoomTier = when {
    zoom < 8.0f -> MapClusterZoomTier.FAR
    zoom < 10.0f -> MapClusterZoomTier.OUT
    zoom < 14.5f -> MapClusterZoomTier.MEDIUM
    zoom < 16.5f -> MapClusterZoomTier.NEAR
    zoom < 18.0f -> MapClusterZoomTier.CLOSE
    else -> MapClusterZoomTier.DETAIL
}

/**
 * Groups real places first, then builds deterministic display clusters. The
 * default tier retains the historical 0.01-degree grid. Every other display
 * tier is aligned to that grid, so zooming out only merges existing cells and
 * zooming in only splits them. Real-place grouping happens before the grid.
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
        places.indices
            .groupBy { index -> hierarchicalCell(places[index].center, zoomTier) }
            .values
            .map { it.sorted() }
    }
    return groups.map { indices ->
        val clusteredPhotos = indices.flatMap { places[it].photos }.sortedBy { it.imageId.value }
        MapCluster(
            id = clusteredPhotos.joinToString("|") { it.imageId.value },
            center = medoidOf(indices.map { places[it].center }),
            photos = clusteredPhotos,
        )
    }.sortedBy(MapCluster::id)
}

internal data class MapPlaceGroup(
    val id: String,
    val center: GeoPoint,
    val photos: List<MapPhoto>,
)

internal fun groupMapPhotosByPlace(photos: List<MapPhoto>): List<MapPlaceGroup> {
    val sorted = photos.sortedBy { it.imageId.value }
    val groups = completeLinkGroups(sorted.size) { left, right -> sameRealPlace(sorted[left], sorted[right]) }
    return groups.map { indices ->
        val placePhotos = indices.map(sorted::get).sortedBy { it.imageId.value }
        MapPlaceGroup(
            id = placePhotos.joinToString("|") { it.imageId.value },
            center = medoidOf(placePhotos.map { it.location.point }),
            photos = placePhotos,
        )
    }.sortedBy(MapPlaceGroup::id)
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

private data class GridCell(val latitude: Long, val longitude: Long)

private fun hierarchicalCell(point: GeoPoint, tier: MapClusterZoomTier): GridCell = when (tier) {
    MapClusterZoomTier.FAR -> coarseCell(point, legacyCellsPerSide = 20)
    MapClusterZoomTier.OUT -> coarseCell(point, legacyCellsPerSide = 10)
    MapClusterZoomTier.MEDIUM -> GridCell(legacyIndex(point.latitude).toLong(), legacyIndex(point.longitude).toLong())
    MapClusterZoomTier.NEAR -> fineCell(point, subdivisionsPerLegacyCell = 4)
    MapClusterZoomTier.CLOSE -> fineCell(point, subdivisionsPerLegacyCell = 20)
    MapClusterZoomTier.DETAIL -> error("Detail tier groups real places directly")
}

private fun coarseCell(point: GeoPoint, legacyCellsPerSide: Int) = GridCell(
    latitude = Math.floorDiv(legacyIndex(point.latitude), legacyCellsPerSide).toLong(),
    longitude = Math.floorDiv(legacyIndex(point.longitude), legacyCellsPerSide).toLong(),
)

private fun fineCell(point: GeoPoint, subdivisionsPerLegacyCell: Int) = GridCell(
    latitude = fineIndex(point.latitude, subdivisionsPerLegacyCell),
    longitude = fineIndex(point.longitude, subdivisionsPerLegacyCell),
)

private fun fineIndex(value: Double, subdivisions: Int): Long {
    val parent = legacyIndex(value)
    val lowerBoundary = (parent - 0.5) * LEGACY_CELL_DEGREES
    val childSize = LEGACY_CELL_DEGREES / subdivisions
    val child = floor((value - lowerBoundary) / childSize)
        .toInt()
        .coerceIn(0, subdivisions - 1)
    return parent.toLong() * subdivisions + child
}

private fun legacyIndex(value: Double): Int =
    (value / LEGACY_CELL_DEGREES).roundToInt()

private fun medoidOf(points: List<GeoPoint>): GeoPoint {
    require(points.isNotEmpty())
    return points.withIndex().minWithOrNull(
        compareBy<IndexedValue<GeoPoint>> { candidate ->
            points.sumOf { point -> geoDistanceMeters(candidate.value, point) }
        }.thenBy { it.index },
    )!!.value
}

private fun String?.normalizedPlaceName(): String? = this
    ?.trim()
    ?.lowercase(Locale.ROOT)
    ?.replace(Regex("\\s+"), " ")
    ?.takeIf(String::isNotEmpty)

private fun completeLinkGroups(size: Int, canShareGroup: (Int, Int) -> Boolean): List<List<Int>> {
    if (size == 0) return emptyList()
    val groups = mutableListOf<MutableList<Int>>()
    repeat(size) { candidate ->
        val compatible = groups.firstOrNull { group ->
            group.all { member -> canShareGroup(candidate, member) }
        }
        if (compatible == null) groups += mutableListOf(candidate)
        else compatible += candidate
    }
    return groups.map { it.toList() }
}

private const val LEGACY_CELL_DEGREES = 0.01
