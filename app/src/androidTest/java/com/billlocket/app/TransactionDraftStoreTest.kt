package com.billlocket.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.CandidateLocation
import com.billlocket.app.feature.transaction.draft.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    @Test
    fun updateImageLocationChangesOnlyTargetImageAndTimestamp() = runBlocking {
        val id = DraftId("draft-location")
        val original = draftWithTwoImages(id)
        store.save(original)
        val location = PhotoLocation(
            point = GeoPoint(10.7769, 106.7009),
            source = LocationSource.CURRENT,
            confirmedAtEpochMillis = 1_700_000_000_500,
        )

        assertEquals(true, store.updateImageLocation(id, "image-2", location))

        val updated = requireNotNull(store.load(id))
        assertEquals(original.amountText, updated.amountText)
        assertEquals(original.note, updated.note)
        assertEquals(original.categoryId, updated.categoryId)
        assertEquals(original.accountId, updated.accountId)
        assertEquals(original.images.map { it.id }, updated.images.map { it.id })
        assertEquals(null, updated.images[0].confirmedLocation)
        assertEquals(location, updated.images[1].confirmedLocation)
        assertEquals(original.suggestionSemanticKey, updated.suggestionSemanticKey)
        assertEquals(true, updated.updatedAtEpochMillis > original.updatedAtEpochMillis)
    }

    @Test
    fun updateImageLocationDoesNotRecreateMissingImage() = runBlocking {
        val id = DraftId("draft-missing-image")
        val original = draftWithTwoImages(id).copy(images = draftWithTwoImages(id).images.take(1))
        store.save(original)

        val changed = store.updateImageLocation(
            id,
            "image-2",
            PhotoLocation(GeoPoint(10.0, 106.0), LocationSource.CURRENT, 123L),
        )

        assertEquals(false, changed)
        assertEquals(original, store.load(id))
    }

    @Test
    fun concurrentSuggestionAndLocationUpdatesPreserveBothChanges() = runBlocking {
        val id = DraftId("draft-atomic-updates")
        store.save(draftWithTwoImages(id))
        val location = PhotoLocation(
            point = GeoPoint(21.0285, 105.8542),
            source = LocationSource.CURRENT,
            confirmedAtEpochMillis = 1_700_000_000_700,
        )
        val metadata = DraftCategorySuggestionMetadata(
            analyzedPrimaryImageKey = "image-1:key",
            suggestionSemanticKey = "FOOD_DRINK",
            suggestionConfidence = 0.88f,
            suggestionSupportingLabels = listOf("coffee"),
        )

        listOf(
            async { store.updateImageLocation(id, "image-1", location) },
            async { store.updateCategorySuggestionMetadata(id, metadata) },
        ).awaitAll()

        val updated = requireNotNull(store.load(id))
        assertEquals(location, updated.images.first().confirmedLocation)
        assertEquals("FOOD_DRINK", updated.suggestionSemanticKey)
        assertEquals(listOf("coffee"), updated.suggestionSupportingLabels)
        assertEquals("125000", updated.amountText)
        assertEquals("Keep all fields", updated.note)
    }

    private fun draftWithTwoImages(id: DraftId): TransactionDraft = TransactionDraft(
        id = id,
        type = TransactionType.EXPENSE,
        amountText = "125000",
        categoryId = CategoryId("category-1"),
        accountId = AccountId("account-1"),
        note = "Keep all fields",
        occurred = InstantAtZone(1_700_000_000_000, 19_675, "Asia/Ho_Chi_Minh"),
        images = listOf(
            draftImage("image-1", "operation-1"),
            draftImage("image-2", "operation-2"),
        ),
        dirty = true,
        updatedAtEpochMillis = 1_700_000_000_000,
        analyzedPrimaryImageKey = "old-key",
        suggestionSemanticKey = "HOME",
        suggestionConfidence = 0.75f,
        suggestionSupportingLabels = listOf("table"),
    )

    private fun draftImage(imageId: String, operationId: String) = DraftImage(
        id = imageId,
        kind = DraftImageKind.STAGED,
        previewUri = "content://com.billlocket.app.media/drafts/$operationId/$imageId",
        mediaUri = "content://com.billlocket.app.media/images/$imageId",
        operationId = operationId,
        widthPx = 100,
        heightPx = 200,
        sizeBytes = 300,
        checksumSha256 = imageId.padEnd(64, 'a'),
    )
}
