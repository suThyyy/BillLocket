package com.billlocket.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.CandidateLocation
import com.billlocket.app.feature.transaction.draft.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDraftStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("draft-store-test", Context.MODE_PRIVATE)
    private val store = SharedPreferencesTransactionDraftStore(preferences)

    @Before
    fun clear() {
        preferences.edit().clear().commit()
    }

    @Test
    fun draftRoundTripRestoresFieldsImagesAndLocationCandidate() = runBlocking {
        val id = DraftId("draft-1")
        val draft = TransactionDraft(
            id = id,
            type = TransactionType.EXPENSE,
            amountText = "125000",
            note = "Bữa trưa",
            occurred = InstantAtZone(1_700_000_000_000, 19_675, "Asia/Ho_Chi_Minh"),
            images = listOf(
                DraftImage(
                    id = "image-1",
                    kind = DraftImageKind.STAGED,
                    previewUri = "content://com.billlocket.app.media/drafts/operation-1/image-1",
                    mediaUri = "content://com.billlocket.app.media/images/image-1",
                    operationId = "operation-1",
                    widthPx = 100,
                    heightPx = 200,
                    sizeBytes = 300,
                    checksumSha256 = "a".repeat(64),
                    locationCandidate = CandidateLocation(10.0, 106.0),
                ),
            ),
            dirty = true,
            updatedAtEpochMillis = 1_700_000_000_001,
        )

        store.save(draft)
        val restored = requireNotNull(store.load(id))

        assertEquals(draft, restored)
        store.delete(id)
        assertEquals(null, store.load(id))
    }

    @Test
    fun updateCategorySuggestionMetadataPreservesFinancialFields() = runBlocking {
        val id = DraftId("draft-concurrency")
        val initialDraft = TransactionDraft(
            id = id,
            type = TransactionType.EXPENSE,
            amountText = "250000",
            note = "Coffee with friends",
            occurred = InstantAtZone(1_700_000_000_000, 19_675, "Asia/Ho_Chi_Minh"),
            dirty = true,
            updatedAtEpochMillis = 1_700_000_000_000,
        )
        store.save(initialDraft)

        val metadata = DraftCategorySuggestionMetadata(
            analyzedPrimaryImageKey = "img-primary",
            suggestionSemanticKey = "FOOD_DRINK",
            suggestionConfidence = 0.91f,
            suggestionSupportingLabels = listOf("coffee", "beverage"),
            suggestionDismissedForImageKey = null,
            categoryWasManuallySelected = true,
        )

        store.updateCategorySuggestionMetadata(id, metadata)

        val updated = requireNotNull(store.load(id))
        assertEquals("250000", updated.amountText)
        assertEquals("Coffee with friends", updated.note)
        assertEquals("img-primary", updated.analyzedPrimaryImageKey)
        assertEquals("FOOD_DRINK", updated.suggestionSemanticKey)
        assertEquals(0.91f, updated.suggestionConfidence!!, 0.001f)
        assertEquals(listOf("coffee", "beverage"), updated.suggestionSupportingLabels)
        assertEquals(true, updated.categoryWasManuallySelected)
    }
}
