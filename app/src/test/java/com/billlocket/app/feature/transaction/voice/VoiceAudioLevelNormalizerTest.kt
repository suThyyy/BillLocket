package com.billlocket.app.feature.transaction.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAudioLevelNormalizerTest {
    private var nowMillis = 0L
    private val normalizer = VoiceAudioLevelNormalizer { nowMillis }

    @Test
    fun `maps quiet zero and loud RMS values into finite normalized levels`() {
        assertEquals(0f, normalizer.sample(-2f)!!, 0.0001f)

        nowMillis += VoiceAudioLevelNormalizer.EMISSION_INTERVAL_MILLIS
        assertEquals(0.28f * (2f / 12f), normalizer.sample(0f)!!, 0.0001f)

        nowMillis += VoiceAudioLevelNormalizer.EMISSION_INTERVAL_MILLIS
        val loud = normalizer.sample(20f)!!
        assertTrue(loud.isFinite())
        assertTrue(loud in 0f..1f)
    }

    @Test
    fun `negative RMS below mapping floor clamps to zero`() {
        assertEquals(0f, normalizer.sample(-100f)!!, 0.0001f)
    }

    @Test
    fun `non finite RMS is ignored without poisoning the next sample`() {
        assertNull(normalizer.sample(Float.NaN))
        assertNull(normalizer.sample(Float.POSITIVE_INFINITY))
        assertNull(normalizer.sample(Float.NEGATIVE_INFINITY))

        assertEquals(0.28f, normalizer.sample(10f)!!, 0.0001f)
    }

    @Test
    fun `emits no more than once per fifty milliseconds`() {
        assertEquals(0.28f, normalizer.sample(10f)!!, 0.0001f)

        nowMillis = 49L
        assertNull(normalizer.sample(10f))

        nowMillis = 50L
        assertEquals(0.4816f, normalizer.sample(10f)!!, 0.0001f)
    }

    @Test
    fun `smoothing uses previous and new sample weights`() {
        assertEquals(0.28f, normalizer.sample(10f)!!, 0.0001f)

        nowMillis += 50L
        assertEquals(0.2016f, normalizer.sample(-2f)!!, 0.0001f)
    }

    @Test
    fun `reset clears smoothing and throttle state`() {
        assertEquals(0.28f, normalizer.sample(10f)!!, 0.0001f)
        normalizer.reset()

        assertEquals(0f, normalizer.sample(-2f)!!, 0.0001f)
    }
}
