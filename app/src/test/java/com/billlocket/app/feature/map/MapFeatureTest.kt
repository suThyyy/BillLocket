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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFeatureTest {
    @Test fun `far zoom groups nearby places while detail zoom separates them`() {
        val photos = listOf(
            photo("a", 10.8000, 106.6000, placeName = "Cafe A"),
            photo("b", 10.8020, 106.6000, placeName = "Cafe B"),
        )
        assertEquals(1, clusterMapPhotos(photos, MapClusterZoomTier.FAR).size)
        assertEquals(2, clusterMapPhotos(photos, MapClusterZoomTier.DETAIL).size)
    }

    @Test fun `photos from same transaction and place stay grouped at detail zoom`() {
        val photos = listOf(
            photo("a", 10.80000, 106.60000, transactionId = "tx-1"),
            photo("b", 10.80005, 106.60005, transactionId = "tx-1"),
        )
        val cluster = clusterMapPhotos(photos, MapClusterZoomTier.DETAIL).single()
        assertEquals(listOf("a", "b"), cluster.photos.map { it.imageId.value })
        assertEquals("a|b", cluster.id)
    }

    @Test fun `normalized place name groups close photos across transactions`() {
        val photos = listOf(
            photo("a", 10.80000, 106.60000, placeName = "  The Coffee House "),
            photo("b", 10.80004, 106.60004, placeName = "the   coffee house"),
        )
        assertEquals(1, groupMapPhotosByPlace(photos).size)
        assertEquals(1, clusterMapPhotos(photos, MapClusterZoomTier.DETAIL).size)
    }

    @Test fun `different named places are not treated as the same real place`() {
        val photos = listOf(
            photo("a", 10.80000, 106.60000, placeName = "Cafe A"),
            photo("b", 10.80000, 106.60000, placeName = "Cafe B"),
        )
        assertEquals(2, groupMapPhotosByPlace(photos).size)
        assertEquals(2, clusterMapPhotos(photos, MapClusterZoomTier.DETAIL).size)
        assertEquals(setOf("a", "b"), allPhotoIds(clusterMapPhotos(photos, MapClusterZoomTier.DETAIL)))
    }

    @Test fun `zoom tiers are stable within thresholds`() {
        assertEquals(MapClusterZoomTier.MEDIUM, mapClusterZoomTier(12.0f))
        assertEquals(MapClusterZoomTier.MEDIUM, mapClusterZoomTier(14.49f))
        assertEquals(MapClusterZoomTier.NEAR, mapClusterZoomTier(14.5f))
        val photos = listOf(photo("b", 10.8, 106.6), photo("a", 10.801, 106.601))
        assertEquals(
            clusterMapPhotos(photos, mapClusterZoomTier(12.1f)).map { it.id },
            clusterMapPhotos(photos, mapClusterZoomTier(14.4f)).map { it.id },
        )
    }

    @Test fun `clustering preserves every photo and selected photo across zoom levels`() {
        val photos = (0 until 300).map { index ->
            photo(
                id = "image-${index.toString().padStart(3, '0')}",
                latitude = 10.7 + (index % 30) * 0.001,
                longitude = 106.5 + (index / 30) * 0.001,
                placeName = "Place $index",
            )
        }
        val expected = photos.map { it.imageId.value }.toSet()
        val selectedId = "image-157"
        MapClusterZoomTier.entries.forEach { tier ->
            val clusters = clusterMapPhotos(photos, tier)
            assertEquals(expected, allPhotoIds(clusters))
            assertTrue(clusters.any { cluster -> cluster.photos.any { it.imageId.value == selectedId } })
        }
    }

    @Test fun `empty input returns no clusters`() {
        assertTrue(clusterMapPhotos(emptyList()).isEmpty())
    }

    private fun allPhotoIds(clusters: List<MapCluster>) =
        clusters.flatMap { cluster -> cluster.photos.map { it.imageId.value } }.toSet()

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
