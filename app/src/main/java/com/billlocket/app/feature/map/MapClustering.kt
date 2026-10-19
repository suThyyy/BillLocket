package com.billlocket.app.feature.map

import com.billlocket.app.domain.model.GeoPoint
import com.billlocket.app.domain.model.MapPhoto
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class MapCluster(val id: String, val center: GeoPoint, val photos: List<MapPhoto>)

enum class MapClusterZoomTier(val maximumDiameterMeters: Double?) {
    FAR(4_000.0),
    OUT(2_000.0),
    /** Matches the pre-dynamic-clustering 0.01-degree grid at the default zoom. */
    MEDIUM(null),
    NEAR(250.0),
    CLOSE(60.0),
    DETAIL(0.0),
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
 * default tier retains the historical 0.01-degree grid. Distance tiers use
 * complete-linkage so every pair stays within the tier's maximum diameter;
 * a chain of nearby places therefore cannot create an unbounded cluster.
 */
fun clusterMapPhotos(
    photos: List<MapPhoto>,
    zoomTier: MapClusterZoomTier = MapClusterZoomTier.MEDIUM,
): List<MapCluster> {
    if (photos.isEmpty()) return emptyList()
    val places = groupMapPhotosByPlace(photos)
    val groups = when (zoomTier) {
        MapClusterZoomTier.MEDIUM -> places.indices
            .groupBy { index -> legacyCell(places[index].center) }
            .values
            .map { it.sorted() }
        MapClusterZoomTier.DETAIL -> places.indices.map(::listOf)
        else -> boundedDistanceGroups(places, requireNotNull(zoomTier.maximumDiameterMeters))
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

private fun legacyCell(point: GeoPoint) =
    (point.latitude / LEGACY_CELL_DEGREES).roundToInt() to
        (point.longitude / LEGACY_CELL_DEGREES).roundToInt()

private fun boundedDistanceGroups(
    places: List<MapPlaceGroup>,
    maximumDiameterMeters: Double,
): List<List<Int>> {
    val groups = mutableListOf<MutableList<Int>>()
    places.indices.forEach { candidate ->
        val compatible = groups.withIndex()
            .filter { (_, group) ->
                group.all { member ->
                    geoDistanceMeters(places[candidate].center, places[member].center) <= maximumDiameterMeters
                }
            }
            .minWithOrNull(
                compareBy<IndexedValue<MutableList<Int>>> {
                    geoDistanceMeters(
                        places[candidate].center,
                        medoidOf(it.value.map { index -> places[index].center }),
                    )
                }.thenBy { it.index },
            )
        if (compatible == null) groups += mutableListOf(candidate)
        else compatible.value += candidate
    }
    return groups.map { it.toList() }
}

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
