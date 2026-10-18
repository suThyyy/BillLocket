package com.billlocket.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionFtsQueryTest {
    @Test
    fun `search text becomes safe prefix terms`() {
        assertEquals("\"cà\"* AND \"phê\"*", "  cà, phê! ".toFtsMatchQuery())
        assertEquals("\"coffee\"*", "coffee".toFtsMatchQuery())
    }

    @Test
    fun `punctuation-only search has no FTS expression`() {
        assertNull(" -- !! ".toFtsMatchQuery())
    }
}
