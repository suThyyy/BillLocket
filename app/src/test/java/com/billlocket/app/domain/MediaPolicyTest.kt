package com.billlocket.app.domain

import com.billlocket.app.domain.model.MediaCompressionPolicy
import com.billlocket.app.domain.model.MediaJournalEntry
import com.billlocket.app.domain.model.MediaJournalState
import com.billlocket.app.domain.model.OwnedMediaReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaPolicyTest {
    @Test
    fun ownedUriRoundTripsOnlySafeIds() {
        val uri = OwnedMediaReference.uri("image_01-safe")
        assertEquals("image_01-safe", OwnedMediaReference.imageId(uri))
        listOf("", ".", "..", "../escape", "a/b", "a\\b", "with space").forEach { unsafe ->
            assertThrows(IllegalArgumentException::class.java) { OwnedMediaReference.requireId(unsafe) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            OwnedMediaReference.imageId("content://another.provider/images/image_01-safe")
        }
    }

    @Test
    fun journalAllowsOnlyForwardMonotonicTransitions() {
        val prepared = MediaJournalEntry(
            "operation-1",
            "image-1",
            OwnedMediaReference.uri("image-1"),
            "a".repeat(64),
            MediaJournalState.PREPARED,
            10,
        )
        val ready = prepared.transitionTo(MediaJournalState.FILE_READY, 11)
        assertEquals(MediaJournalState.COMMITTED, ready.transitionTo(MediaJournalState.COMMITTED, 12).state)
        assertThrows(IllegalArgumentException::class.java) { prepared.transitionTo(MediaJournalState.COMMITTED, 11) }
        assertThrows(IllegalArgumentException::class.java) { ready.transitionTo(MediaJournalState.PREPARED, 12) }
        assertThrows(IllegalArgumentException::class.java) { ready.transitionTo(MediaJournalState.COMMITTED, 9) }
    }

    @Test
    fun compressionPlansNeverUpscaleAndUseApprovedFallbacks() {
        assertEquals(1200, MediaCompressionPolicy.firstPlan(800, 1200).longEdgePx)
        assertEquals(2560, MediaCompressionPolicy.firstPlan(6000, 4000).longEdgePx)
        assertEquals(88, MediaCompressionPolicy.firstPlan(6000, 4000).jpegQuality)
        assertEquals(75, MediaCompressionPolicy.fallbackPlan().jpegQuality)
        assertEquals(2048, MediaCompressionPolicy.reducedPlan().longEdgePx)
    }
}
