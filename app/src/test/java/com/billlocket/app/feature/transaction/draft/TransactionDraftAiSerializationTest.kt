package com.billlocket.app.feature.transaction.draft

import com.billlocket.app.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TransactionDraftAiSerializationTest {

    @Test
    fun `old JSON without AI fields loads with default null and empty values`() {
        val oldJson = JSONObject().apply {
            put("type", "EXPENSE")
            put("amountText", "50000")
            put("categoryId", "cat-123")
            put("accountId", "acc-456")
            put("note", "Lunch")
            put("occurredAt", 1700000000000L)
            put("epochDay", 19680L)
            put("zoneId", "Asia/Ho_Chi_Minh")
            put("dirty", true)
            put("updatedAt", 1700000005000L)
            put("images", JSONArray())
        }

        val draftId = DraftId("draft-1")
        val draft = oldJson.toDraft(draftId)

        assertEquals(draftId, draft.id)
        assertEquals("50000", draft.amountText)
        assertEquals(CategoryId("cat-123"), draft.categoryId)
        assertNull(draft.analyzedPrimaryImageKey)
        assertNull(draft.suggestionSemanticKey)
        assertNull(draft.suggestionConfidence)
        assertTrue(draft.suggestionSupportingLabels.isEmpty())
        assertNull(draft.suggestionDismissedForImageKey)
        assertFalse(draft.categoryWasManuallySelected)
    }

    @Test
    fun `AI metadata and manual selection round-trip correctly`() {
        val draftId = DraftId("draft-ai-1")
        val originalDraft = TransactionDraft(
            id = draftId,
            type = TransactionType.EXPENSE,
            amountText = "120000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            updatedAtEpochMillis = 1700000005000L,
            analyzedPrimaryImageKey = "img-primary-key",
            suggestionSemanticKey = "FOOD_DRINK",
            suggestionConfidence = 0.88f,
            suggestionSupportingLabels = listOf("food", "beverage"),
            suggestionDismissedForImageKey = "img-dismissed-key",
            categoryWasManuallySelected = true,
        )

        val json = originalDraft.toJson()
        val restoredDraft = json.toDraft(draftId)

        assertEquals("img-primary-key", restoredDraft.analyzedPrimaryImageKey)
        assertEquals("FOOD_DRINK", restoredDraft.suggestionSemanticKey)
        assertEquals(0.88f, restoredDraft.suggestionConfidence!!, 0.001f)
        assertEquals(listOf("food", "beverage"), restoredDraft.suggestionSupportingLabels)
        assertEquals("img-dismissed-key", restoredDraft.suggestionDismissedForImageKey)
        assertTrue(restoredDraft.categoryWasManuallySelected)
    }
}
