package com.billlocket.app.feature.location

import com.billlocket.app.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressResolverTest {
    @Test fun `saved detailed address is authoritative`() {
        val resolved = savedPlaceNameAddress(" 191 Bà Triệu, Hai Bà Trưng, Hà Nội ")

        assertEquals("191 Bà Triệu, Hai Bà Trưng, Hà Nội", resolved?.shortAddress)
        assertFalse(resolved!!.isFallback)
        assertNull(savedPlaceNameAddress("21.0113, 105.8495"))
    }

    @Test fun `city only geocoder result is not considered locally detailed`() {
        assertFalse(
            hasLocalAddressDetail(
                streetLine = null,
                ward = null,
                district = null,
                feature = "Hà Nội",
                city = "Hà Nội",
                country = "Việt Nam",
                cleanedLine0 = "Hà Nội",
            ),
        )
        assertTrue(
            hasLocalAddressDetail(
                streetLine = "Bà Triệu",
                ward = null,
                district = null,
                feature = null,
                city = "Hà Nội",
                country = "Việt Nam",
                cleanedLine0 = "Bà Triệu, Hà Nội",
            ),
        )
    }

    @Test fun `city only fallback includes coordinates and remains retryable`() {
        val resolved = cityLevelAddress(GeoPoint(21.0113, 105.8495), "Hà Nội")

        assertEquals("Hà Nội • 21.0113, 105.8495", resolved.shortAddress)
        assertEquals("21.0113, 105.8495", resolved.detailLocation)
        assertTrue(resolved.isFallback)
    }
}
