package com.billlocket.app.feature.transaction

import com.billlocket.app.domain.model.DraftId
import com.billlocket.app.domain.port.*
import com.billlocket.app.feature.transaction.draft.DraftImage
import com.billlocket.app.feature.transaction.editor.parseVndAmount
import org.junit.Assert.*
import org.junit.Test

class TransactionEditorParsingTest {
    @Test
    fun `vnd amount accepts common grouping separators`() {
        assertEquals(125_000L, parseVndAmount("125.000"))
        assertEquals(1_250_000L, parseVndAmount("1 250 000"))
        assertEquals(50_000L, parseVndAmount("50,000"))
    }

    @Test
    fun `vnd amount rejects invalid and overflowing values`() {
        assertNull(parseVndAmount(""))
        assertNull(parseVndAmount("12k"))
        assertNull(parseVndAmount("999999999999999999999999"))
    }

    @Test
    fun `normalized draft image can be restored without image bytes`() {
        val staged = StagedMedia(MediaOperationId("operation-1"), MediaImageId("image-1"))
        val normalized = NormalizedMedia(
            staged = staged,
            canonicalUri = "content://com.billlocket.app.media/images/image-1",
            widthPx = 1200,
            heightPx = 800,
            mimeType = "image/jpeg",
            sizeBytes = 1234,
            checksumSha256 = "a".repeat(64),
            capturedAtEpochMillis = 100,
            locationCandidate = null,
        )

        val draft = DraftImage.fromNormalized(
            normalized,
            "content://com.billlocket.app.media/drafts/operation-1/image-1",
        )

        assertEquals(staged, draft.stagedMedia())
        assertEquals(normalized, requireNotNull(draft.asPrepared()).media)
        assertNull(draft.asExistingInput())
    }
}
