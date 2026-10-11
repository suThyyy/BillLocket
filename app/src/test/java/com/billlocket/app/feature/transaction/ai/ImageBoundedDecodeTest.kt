package com.billlocket.app.feature.transaction.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageBoundedDecodeTest {

    @Test
    fun `calculateInSampleSize guarantees longest side is within maxDimension`() {
        assertEquals(4, calculateInSampleSize(4000, 3000, 1024))
        assertEquals(4, calculateInSampleSize(3000, 4000, 1024))
        assertEquals(2, calculateInSampleSize(2048, 1024, 1024))
        assertEquals(1, calculateInSampleSize(1024, 1024, 1024))
        assertEquals(1, calculateInSampleSize(800, 600, 1024))
    }

    @Test
    fun `calculateInSampleSize handles invalid dimensions safely`() {
        assertEquals(1, calculateInSampleSize(0, 0, 1024))
        assertEquals(1, calculateInSampleSize(-100, 500, 1024))
        assertEquals(1, calculateInSampleSize(800, -50, 1024))
        assertEquals(1, calculateInSampleSize(800, 600, 0))
        assertEquals(1, calculateInSampleSize(800, 600, -10))
    }

    @Test
    fun `calculateInSampleSize produces powers of two strictly satisfying the bound`() {
        val testSizes = listOf(
            4000 to 3000,
            3000 to 4000,
            2048 to 1024,
            2049 to 1024,
            1025 to 768,
            1024 to 1024,
            800 to 600,
            5000 to 5000,
        )
        val maxDimension = 1024

        for ((w, h) in testSizes) {
            val sampleSize = calculateInSampleSize(w, h, maxDimension)
            // Must be power of 2
            assertTrue((sampleSize and (sampleSize - 1)) == 0)
            val decodedW = Math.ceil(w.toDouble() / sampleSize).toInt()
            val decodedH = Math.ceil(h.toDouble() / sampleSize).toInt()
            val maxDecoded = maxOf(decodedW, decodedH)
            assertTrue("Expected maxDecoded <= $maxDimension for ${w}x${h} with sampleSize $sampleSize, got $maxDecoded", maxDecoded <= maxDimension)
        }
    }
}
