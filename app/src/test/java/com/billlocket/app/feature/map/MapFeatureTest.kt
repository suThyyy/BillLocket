package com.billlocket.app.feature.map

import com.billlocket.app.domain.model.CurrencyCode
import com.billlocket.app.domain.model.GeoPoint
import com.billlocket.app.domain.model.InstantAtZone
import com.billlocket.app.domain.model.LocationSource
import com.billlocket.app.domain.model.MapPhoto
import com.billlocket.app.domain.model.Money
import com.billlocket.app.domain.model.PhotoLocation
import com.billlocket.app.domain.model.TransactionId
import com.billlocket.app.domain.model.TransactionImageId
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFeatureTest {
    @Test fun `default tier retains legacy grid cluster membership`() {
        val photos = listOf(
            photo("a", 21.0201, 105.8401, placeName = "A"),
            photo("b", 21.0203, 105.8403, placeName = "B"),
            photo("c", 21.0310, 105.8510, placeName = "C"),
            photo("d", 21.0410, 105.8610, placeName = "D"),
        )

        val expected = legacyClusterIds(photos)
        val actual = clusterMapPhotos(photos, MapClusterZoomTier.MEDIUM)
            .map { it.photos.map { photo -> photo.imageId.value }.toSet() }
            .toSet()

        assertEquals(expected, actual)
        assertEquals(MapClusterZoomTier.MEDIUM, mapClusterZoomTier(11f))
        assertEquals(MapClusterZoomTier.MEDIUM, mapClusterZoomTier(13.5f))
    }

    @Test fun `zooming in separates different places from one legacy cell`() {
        val photos = listOf(
            photo("a", 21.0200, 105.8400, placeName = "Cafe A"),
            photo("b", 21.0230, 105.8400, placeName = "Cafe B"),
        )

        assertEquals(1, clusterMapPhotos(photos, MapClusterZoomTier.MEDIUM).size)
        assertEquals(2, clusterMapPhotos(photos, MapClusterZoomTier.NEAR).size)
    }

    @Test fun `zooming out groups markers split by legacy grid`() {
        val photos = listOf(
            photo("a", 21.0249, 105.8400, placeName = "Cafe A"),
            photo("b", 21.0351, 105.8400, placeName = "Cafe B"),
        )

        assertEquals(2, clusterMapPhotos(photos, MapClusterZoomTier.MEDIUM).size)
        assertEquals(1, clusterMapPhotos(photos, MapClusterZoomTier.OUT).size)
    }

    @Test fun `grid clustering cannot chain across regional cells`() {
        val photos = listOf(
            photo("a", 21.0850, 105.8400, placeName = "A"),
            photo("b", 21.0960, 105.8400, placeName = "B"),
            photo("c", 21.1070, 105.8400, placeName = "C"),
        )

        val clusters = clusterMapPhotos(photos, MapClusterZoomTier.OUT)

        assertEquals(2, clusters.size)
        assertEquals(setOf(setOf("a"), setOf("b", "c")), clusterMembers(clusters))
    }

    @Test fun `hierarchical tiers only split when zooming in`() {
        val photos = (0 until 80).map { index ->
            photo(
                id = "p-$index",
                latitude = 20.85 + (index % 20) * 0.0137,
                longitude = 105.65 + (index / 20) * 0.0173,
                placeName = "Place $index",
            )
        }
        val tiers = MapClusterZoomTier.entries

        tiers.zipWithNext().forEach { (coarser, finer) ->
            val coarseClusters = clusterMembers(clusterMapPhotos(photos, coarser))
            val fineClusters = clusterMembers(clusterMapPhotos(photos, finer))
            assertTrue("$finer must only split $coarser", fineClusters.all { fine ->
                coarseClusters.any { coarse -> coarse.containsAll(fine) }
            })
        }
    }

    @Test fun `legacy boundary stays split when zooming in and merges when zooming out`() {
        val photos = listOf(
            photo("left", 21.02499, 105.8400, placeName = "Left"),
            photo("right", 21.02501, 105.8400, placeName = "Right"),
        )

        assertEquals(1, clusterMapPhotos(photos, MapClusterZoomTier.OUT).size)
        assertEquals(2, clusterMapPhotos(photos, MapClusterZoomTier.MEDIUM).size)
        assertEquals(2, clusterMapPhotos(photos, MapClusterZoomTier.NEAR).size)
        assertEquals(2, clusterMapPhotos(photos, MapClusterZoomTier.CLOSE).size)
    }

    @Test fun `same real place remains grouped at detail zoom`() {
        val photos = listOf(
            photo("a", 21.02000, 105.84000, transactionId = "tx-1", placeName = "Cafe"),
            photo("b", 21.02005, 105.84005, transactionId = "tx-1", placeName = "Cafe"),
        )

        val cluster = clusterMapPhotos(photos, MapClusterZoomTier.DETAIL).single()

        assertEquals(listOf("a", "b"), cluster.photos.map { it.imageId.value })
    }

    @Test fun `same place fallback cannot chain beyond its own distance limit`() {
        val photos = listOf(
            photo("a", 21.000000, 105.8400, placeName = "Cafe"),
            photo("b", 21.000135, 105.8400, placeName = "Cafe"),
            photo("c", 21.000270, 105.8400, placeName = "Cafe"),
        )

        assertEquals(2, groupMapPhotosByPlace(photos).size)
        assertEquals(2, clusterMapPhotos(photos, MapClusterZoomTier.DETAIL).size)
    }

    @Test fun `cluster center is an unweighted real place medoid`() {
        val photos = buildList {
            repeat(8) { index ->
                add(photo("a-$index", 21.0200, 105.8400, transactionId = "tx-a", placeName = "A"))
            }
            add(photo("b", 21.0210, 105.8400, placeName = "B"))
            add(photo("c", 21.0220, 105.8400, placeName = "C"))
        }

        val cluster = clusterMapPhotos(photos, MapClusterZoomTier.MEDIUM).single()

        assertEquals(21.0210, cluster.center.latitude, 0.000001)
        assertEquals(105.8400, cluster.center.longitude, 0.000001)
    }

    @Test fun `all photos and selected photo remain present across tiers`() {
        val photos = (0 until 100).map { index ->
            photo(
                id = "image-${index.toString().padStart(3, '0')}",
                latitude = 21.0 + (index % 20) * 0.001,
                longitude = 105.8 + (index / 20) * 0.001,
                placeName = "Place $index",
            )
        }
        val expected = photos.map { it.imageId.value }.toSet()
        val selectedId = "image-057"

        MapClusterZoomTier.entries.forEach { tier ->
            val clusters = clusterMapPhotos(photos, tier)
            assertEquals(expected, clusters.flatMap { it.photos }.map { it.imageId.value }.toSet())
            assertTrue(clusters.any { cluster -> cluster.photos.any { it.imageId.value == selectedId } })
        }
    }

    @Test fun `cluster identity is stable for reordered input within a tier`() {
        val photos = listOf(
            photo("c", 21.0203, 105.8403, placeName = "C"),
            photo("a", 21.0201, 105.8401, placeName = "A"),
            photo("b", 21.0202, 105.8402, placeName = "B"),
        )

        val first = clusterMapPhotos(photos, MapClusterZoomTier.MEDIUM).map { it.id }
        val reordered = clusterMapPhotos(photos.reversed(), MapClusterZoomTier.MEDIUM).map { it.id }

        assertEquals(first, reordered)
    }

    private fun clusterMembers(clusters: List<MapCluster>): Set<Set<String>> = clusters
        .map { cluster -> cluster.photos.map { it.imageId.value }.toSet() }
        .toSet()

    private fun legacyClusterIds(photos: List<MapPhoto>): Set<Set<String>> = photos
        .groupBy { photo ->
            (photo.location.point.latitude / 0.01).roundToInt() to
                (photo.location.point.longitude / 0.01).roundToInt()
        }
        .values
        .map { group -> group.map { it.imageId.value }.toSet() }
        .toSet()

    private fun photo(
        id: String,
        latitude: Double,
        longitude: Double,
        transactionId: String = "transaction-$id",
        placeName: String? = null,
        accuracyMeters: Double? = 5.0,
    ) = MapPhoto(
        imageId = TransactionImageId(id),
        transactionId = TransactionId(transactionId),
        mediaUri = "content://billlocket/$id",
        location = PhotoLocation(
            point = GeoPoint(latitude, longitude),
            source = LocationSource.MANUAL,
            confirmedAtEpochMillis = 1L,
            accuracyMeters = accuracyMeters,
            placeName = placeName,
        ),
        amount = Money(10_000, CurrencyCode.of("VND")),
        categoryName = "Ăn uống",
        occurred = InstantAtZone(1L, 0L, "UTC"),
    )
}
