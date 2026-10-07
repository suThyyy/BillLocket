package com.billlocket.app.feature.map

import com.billlocket.app.domain.model.CategoryId
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
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MapFeatureTest {
    @Test fun `nearby photos are clustered and retained`() {
        val photos = listOf(photo("b", 10.8002, 106.6002), photo("a", 10.8001, 106.6001))

        val clusters = clusterMapPhotos(photos, cellDegrees = 0.01)

        assertEquals(1, clusters.size)
        assertEquals(setOf("a", "b"), clusters.single().photos.map { it.imageId.value }.toSet())
        assertEquals("a", clusters.single().photos.first().imageId.value)
        assertEquals("a|b", clusters.single().id)
    }

    @Test fun `distant photos remain separate markers`() {
        val clusters = clusterMapPhotos(listOf(photo("a", 10.8, 106.6), photo("b", 11.8, 107.6)))

        assertEquals(2, clusters.size)
        assertNotEquals(clusters[0].id, clusters[1].id)
    }

    private fun photo(id: String, latitude: Double, longitude: Double) = MapPhoto(
        imageId = TransactionImageId(id),
        transactionId = TransactionId("transaction-$id"),
        mediaUri = "content://billlocket/$id",
        location = PhotoLocation(GeoPoint(latitude, longitude), LocationSource.MANUAL, 1L),
        amount = Money(10_000, CurrencyCode.of("VND")),
        categoryName = "Ăn uống",
        occurred = InstantAtZone(1L, 0L, "UTC"),
    )
}
