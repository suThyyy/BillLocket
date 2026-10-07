package com.billlocket.app.widget

import com.billlocket.app.domain.model.CurrencyCode
import com.billlocket.app.domain.model.Money
import com.billlocket.app.domain.model.TransactionId
import com.billlocket.app.domain.model.TransactionImageId
import com.billlocket.app.domain.model.WidgetPhoto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillLocketWidgetTest {
    @Test fun `missing newest media falls back to next eligible photo`() = runBlocking {
        val selected = selectEligibleWidgetPhoto(listOf(photo("new", "missing"), photo("old", "existing"))) { it == "existing" }
        assertEquals("old", selected?.imageId?.value)
    }

    @Test fun `no readable media produces empty state`() = runBlocking {
        assertNull(selectEligibleWidgetPhoto(listOf(photo("one", "missing"))) { false })
    }

    @Test fun `widget image decoding uses a bounded power of two sample`() {
        assertEquals(4, widgetDecodeSampleSize(2_560, 1_920, 512))
        assertEquals(1, widgetDecodeSampleSize(600, 400, 512))
        assertEquals(1, widgetDecodeSampleSize(512, 512, 512))
        assertEquals(1, widgetDecodeSampleSize(0, 0, 512))
    }

    private fun photo(id: String, uri: String) = WidgetPhoto(
        TransactionId("transaction-$id"), TransactionImageId(id), uri,
        Money(12_000, CurrencyCode.of("VND")), "Ăn uống",
    )
}
