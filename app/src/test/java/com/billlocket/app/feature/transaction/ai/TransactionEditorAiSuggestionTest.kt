package com.billlocket.app.feature.transaction.ai

import androidx.lifecycle.SavedStateHandle
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.*
import com.billlocket.app.domain.repository.*
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.usecase.*
import com.billlocket.app.feature.transaction.MainDispatcherRule
import com.billlocket.app.feature.transaction.draft.*
import com.billlocket.app.feature.transaction.editor.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TransactionEditorAiSuggestionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val foodCategory = Category(
        id = CategoryId(BuiltInCategoryIds.FOOD),
        name = "Ăn uống",
        type = TransactionType.EXPENSE,
        colorArgb = 0xFFFFCC00,
        iconKey = "food",
        sortOrder = 0,
        isDefault = true,
        isArchived = false,
    )

    private val shoppingCategory = Category(
        id = CategoryId(BuiltInCategoryIds.SHOPPING),
        name = "Mua sắm",
        type = TransactionType.EXPENSE,
        colorArgb = 0xFFFF9900,
        iconKey = "shopping",
        sortOrder = 1,
        isDefault = false,
        isArchived = false,
    )

    private val salaryCategory = Category(
        id = CategoryId("salary-cat-id"),
        name = "Lương",
        type = TransactionType.INCOME,
        colorArgb = 0xFF00CC00,
        iconKey = "salary",
        sortOrder = 0,
        isDefault = true,
        isArchived = false,
    )

    private val defaultAccount = Account(
        id = AccountId("acc-cash-1"),
        name = "Tiền mặt",
        type = AccountType.CASH,
        openingBalance = Money(0L, CurrencyCode.of("VND")),
        sortOrder = 0,
        isArchived = false,
    )

    private fun sampleDraftImage(id: String, uri: String = "content://media/img_$id.jpg"): DraftImage = DraftImage(
        id = id,
        kind = DraftImageKind.STAGED,
        previewUri = uri,
        mediaUri = uri,
        operationId = "op-$id",
        mimeType = "image/jpeg",
        widthPx = 800,
        heightPx = 600,
        sizeBytes = 1024L,
        checksumSha256 = "hash-$id",
    )

    private fun createViewModel(
        draftStore: FakeDraftStore = FakeDraftStore(),
        engine: FakeSuggestionEngine = FakeSuggestionEngine(),
        initialDraft: TransactionDraft? = null,
        routeTxId: TransactionId? = null,
        existingTransactions: Map<TransactionId, TransactionDetails> = emptyMap(),
    ): Pair<TransactionEditorViewModel, FakeSuggestionEngine> {
        val draftId = DraftId("test-draft-id")
        if (initialDraft != null) {
            draftStore.stored = initialDraft
        }

        val fakeTransactions = FakeTransactionRepository(existingTransactions)
        val fakeAccounts = FakeAccountRepository(listOf(defaultAccount))
        val fakeCategories = FakeCategoryRepository(listOf(foodCategory, shoppingCategory, salaryCategory))
        val fakeSettings = FakeSettingsRepository()
        val fakeMedia = FakeMediaStore()
        val fakeWidget = WidgetUpdatePort { Outcome.Success(Unit) }
        val clock = Clock { 1700000000000L }

        val createWithMedia = CreateTransactionWithMedia(fakeTransactions, fakeMedia, fakeWidget)
        val updateWithMedia = UpdateTransactionWithMedia(fakeTransactions, fakeMedia, fakeWidget)
        val evalBudget = EvaluateBudgetThresholds(FakeBudgetRepository(), object : NotificationPort {
            override suspend fun publishBudgetThreshold(budgetId: String, percent: Int): Outcome<Unit> = Outcome.Success(Unit)
        }, clock)

        val handleMap = mutableMapOf<String, Any>("draftId" to draftId.value)
        if (routeTxId != null) {
            handleMap["transactionId"] = routeTxId.value
        }

        val vm = TransactionEditorViewModel(
            savedStateHandle = SavedStateHandle(handleMap),
            drafts = draftStore,
            transactions = fakeTransactions,
            accountsRepository = fakeAccounts,
            categoriesRepository = fakeCategories,
            settingsRepository = fakeSettings,
            createTransaction = createWithMedia,
            updateTransaction = updateWithMedia,
            evaluateBudgetThresholds = evalBudget,
            mediaStore = fakeMedia,
            clock = clock,
            suggestionEngine = engine,
        )

        return vm to engine
    }

    @Test
    fun `primary image triggers one analysis`() = runTest {
        val image = sampleDraftImage("1")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertEquals(1, engine.suggestCalls)
        val state = vm.state.value
        assertTrue(state.suggestionState is CategorySuggestionState.Suggested)
        val suggested = state.suggestionState as CategorySuggestionState.Suggested
        assertEquals(foodCategory.id, suggested.resolvedCategoryId)
    }

    @Test
    fun `repeated RefreshImages does not rerun same completed image`() = runTest {
        val image = sampleDraftImage("1")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertEquals(1, engine.suggestCalls)

        vm.onAction(TransactionEditorAction.RefreshImages)
        advanceUntilIdle()

        assertEquals(1, engine.suggestCalls)
    }

    @Test
    fun `restored draft result does not rerun`() = runTest {
        val image = sampleDraftImage("1")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
            analyzedPrimaryImageKey = "1:hash-1",
            suggestionSemanticKey = "FOOD_DRINK",
            suggestionConfidence = 0.85f,
            suggestionSupportingLabels = listOf("food"),
        )
        val engine = FakeSuggestionEngine()
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertEquals(0, engine.suggestCalls)
        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Suggested)
    }

    @Test
    fun `failure does not block Save`() = runTest {
        val image = sampleDraftImage("1")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(shouldThrow = true)
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertNull(vm.state.value.fatalError)
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `no suggestion does not block Save`() = runTest {
        val image = sampleDraftImage("1")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(suggestionToReturn = null)
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.NoSuggestion)
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `manual category selection before result wins`() = runTest {
        val image = sampleDraftImage("1")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val deferred = CompletableDeferred<CategorySuggestion?>()
        val engine = FakeSuggestionEngine(deferred = deferred)
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)

        // Wait for draft restoration and for engine inference to begin
        testScheduler.runCurrent()
        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Analyzing)

        // User manually selects SHOPPING before the engine result arrives
        vm.onAction(TransactionEditorAction.CategorySelected(shoppingCategory.id))

        // Engine completes with a FOOD suggestion
        deferred.complete(CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food")))
        advanceUntilIdle()

        assertEquals(shoppingCategory.id, vm.state.value.selectedCategoryId)
        assertFalse(vm.state.value.suggestionState is CategorySuggestionState.Suggested)
    }

    @Test
    fun `manual category selection after suggestion hides suggestion`() = runTest {
        val image = sampleDraftImage("1")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Suggested)

        // User manually picks shopping
        vm.onAction(TransactionEditorAction.CategorySelected(shoppingCategory.id))
        advanceUntilIdle()

        assertEquals(shoppingCategory.id, vm.state.value.selectedCategoryId)
        assertFalse(vm.state.value.suggestionState is CategorySuggestionState.Suggested)
    }

    @Test
    fun `Apply changes category only after explicit action`() = runTest {
        val image = sampleDraftImage("1")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            categoryId = shoppingCategory.id,
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Suggested)
        assertEquals(shoppingCategory.id, vm.state.value.selectedCategoryId)

        vm.onAction(TransactionEditorAction.ApplyCategorySuggestion)
        advanceUntilIdle()

        assertEquals(foodCategory.id, vm.state.value.selectedCategoryId)
        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Dismissed)
    }

    @Test
    fun `Dismiss prevents repeated display for same image`() = runTest {
        val image = sampleDraftImage("1")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Suggested)

        vm.onAction(TransactionEditorAction.DismissCategorySuggestion)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Dismissed)

        vm.onAction(TransactionEditorAction.RefreshImages)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Dismissed)
    }

    @Test
    fun `removing primary image invalidates stale result`() = runTest {
        val img1 = sampleDraftImage("1")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(img1),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Suggested)

        vm.onAction(TransactionEditorAction.RemoveImage("1"))
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.NotRequested)
    }

    @Test
    fun `switching to INCOME suppresses suggestion`() = runTest {
        val image = sampleDraftImage("1")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Suggested)

        vm.onAction(TransactionEditorAction.TypeChanged(TransactionType.INCOME))
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.NotRequested)
    }

    @Test
    fun `manual selection persists through recreation`() = runTest {
        val image = sampleDraftImage("1")
        val draftStore = FakeDraftStore()
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm1, _) = createViewModel(draftStore = draftStore, engine = engine, initialDraft = draft)
        advanceUntilIdle()

        vm1.onAction(TransactionEditorAction.CategorySelected(shoppingCategory.id))
        advanceUntilIdle()

        assertTrue(draftStore.stored?.categoryWasManuallySelected == true)

        val engine2 = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm2, _) = createViewModel(draftStore = draftStore, engine = engine2, initialDraft = draftStore.stored)
        advanceUntilIdle()

        assertEquals(shoppingCategory.id, vm2.state.value.selectedCategoryId)
        assertFalse(vm2.state.value.suggestionState is CategorySuggestionState.Suggested)
    }

    @Test
    fun `existing transaction edit protects saved category`() = runTest {
        val image = sampleDraftImage("1")
        val txId = TransactionId("tx-saved-1")
        val existingTx = TransactionDetails(
            id = txId,
            type = TransactionType.EXPENSE,
            amount = Money(75000L, CurrencyCode.of("VND")),
            categoryId = shoppingCategory.id,
            accountId = defaultAccount.id,
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            note = "Bought clothes",
            images = listOf(
                TransactionImage(
                    id = TransactionImageId("img-1"),
                    mediaUri = image.mediaUri,
                    position = 0,
                    mimeType = "image/jpeg",
                    widthPx = 800,
                    heightPx = 600,
                    sizeBytes = 1024L,
                    capturedAt = null,
                    location = null,
                )
            ),
            deletedAt = null,
        )
        val draftStore = FakeDraftStore()
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm, _) = createViewModel(
            draftStore = draftStore,
            engine = engine,
            routeTxId = txId,
            existingTransactions = mapOf(txId to existingTx)
        )
        advanceUntilIdle()

        assertEquals(shoppingCategory.id, vm.state.value.selectedCategoryId)
        assertFalse(vm.state.value.suggestionState is CategorySuggestionState.Suggested)
        assertTrue(draftStore.stored?.categoryWasManuallySelected == true)
    }

    @Test
    fun `switching from INCOME to EXPENSE triggers analysis sequentially`() = runTest {
        val image = sampleDraftImage("1")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.INCOME,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.NotRequested)

        vm.onAction(TransactionEditorAction.TypeChanged(TransactionType.EXPENSE))
        advanceUntilIdle()

        assertEquals(TransactionType.EXPENSE, vm.state.value.type)
        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Suggested)
    }

    @Test
    fun `changing type clears manual dismissal and restores cached suggestion`() = runTest {
        val image = sampleDraftImage("1")
        val imageKey = "1:hash-1"
        val draftStore = FakeDraftStore()
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            categoryId = shoppingCategory.id,
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
            analyzedPrimaryImageKey = imageKey,
            suggestionSemanticKey = SemanticCategoryKey.FOOD_DRINK.name,
            suggestionConfidence = 0.85f,
            suggestionSupportingLabels = listOf("food"),
            suggestionDismissedForImageKey = imageKey,
            categoryWasManuallySelected = true,
        )
        val (vm, engine) = createViewModel(draftStore = draftStore, initialDraft = draft)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Dismissed)

        vm.onAction(TransactionEditorAction.TypeChanged(TransactionType.INCOME))
        advanceUntilIdle()
        vm.onAction(TransactionEditorAction.TypeChanged(TransactionType.EXPENSE))
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Suggested)
        assertNull(draftStore.stored?.suggestionDismissedForImageKey)
        assertFalse(draftStore.stored?.categoryWasManuallySelected ?: true)
        assertEquals(0, engine.suggestCalls)
    }

    @Test
    fun `cancellation is not persisted as a completed no-suggestion result`() = runTest {
        val image = sampleDraftImage("1")
        val draftStore = FakeDraftStore()
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(shouldCancel = true)
        val (vm, _) = createViewModel(draftStore = draftStore, engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Analyzing)
        assertNull(draftStore.stored?.analyzedPrimaryImageKey)
    }

    @Test
    fun `reordering images triggers analysis for new primary image`() = runTest {
        val img1 = sampleDraftImage("1", "content://media/1.jpg")
        val img2 = sampleDraftImage("2", "content://media/2.jpg")
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(img1, img2),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm, _) = createViewModel(engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertEquals(1, engine.suggestCalls)

        vm.onAction(TransactionEditorAction.MoveImage(1, 0))
        advanceUntilIdle()

        assertEquals(2, engine.suggestCalls)
        val primary = vm.state.value.images.first()
        assertEquals("2", primary.id)
    }

    @Test
    fun `dismissal preserves financial fields and does not mark dirty`() = runTest {
        val image = sampleDraftImage("1")
        val draftStore = FakeDraftStore()
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "150000",
            note = "Important note",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            dirty = false,
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm, _) = createViewModel(draftStore = draftStore, engine = engine, initialDraft = draft)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Suggested)
        assertFalse(vm.state.value.dirty)

        vm.onAction(TransactionEditorAction.DismissCategorySuggestion)
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestionState is CategorySuggestionState.Dismissed)
        assertFalse(vm.state.value.dirty)
        assertEquals("150000", draftStore.stored?.amountText)
        assertEquals("Important note", draftStore.stored?.note)
        assertNotNull(draftStore.stored?.suggestionDismissedForImageKey)
    }

    @Test
    fun `AI metadata update cannot overwrite newer amount and note user edits`() = runTest {
        val image = sampleDraftImage("1")
        val draftStore = FakeDraftStore()
        val draft = TransactionDraft(
            id = DraftId("test-draft-id"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            note = "Initial note",
            occurred = InstantAtZone(1700000000000L, 19680L, "Asia/Ho_Chi_Minh"),
            images = listOf(image),
            updatedAtEpochMillis = 1700000000000L,
        )
        val engine = FakeSuggestionEngine(
            CategorySuggestion(SemanticCategoryKey.FOOD_DRINK, 0.85f, listOf("food"))
        )
        val (vm, _) = createViewModel(draftStore = draftStore, engine = engine, initialDraft = draft)

        vm.onAction(TransactionEditorAction.AmountChanged("200000"))
        vm.onAction(TransactionEditorAction.NoteChanged("Updated note"))
        advanceUntilIdle()

        assertEquals("200000", draftStore.stored?.amountText)
        assertEquals("Updated note", draftStore.stored?.note)
    }
}

class FakeSuggestionEngine(
    private val suggestionToReturn: CategorySuggestion? = null,
    private val shouldThrow: Boolean = false,
    private val shouldCancel: Boolean = false,
    private val deferred: CompletableDeferred<CategorySuggestion?>? = null,
) : ImageCategorySuggestionEngine {
    var suggestCalls = 0

    override suspend fun suggest(imageUri: String): CategorySuggestion? {
        suggestCalls++
        if (shouldCancel) throw CancellationException("Obsolete inference")
        if (shouldThrow) throw RuntimeException("Inference failure")
        deferred?.let { return it.await() }
        return suggestionToReturn
    }
}

class FakeDraftStore : TransactionDraftStore {
    var stored: TransactionDraft? = null
    override suspend fun load(id: DraftId): TransactionDraft? = stored
    override suspend fun save(draft: TransactionDraft) { stored = draft }
    override suspend fun delete(id: DraftId) { stored = null }
    override suspend fun updateCategorySuggestionMetadata(
        id: DraftId,
        metadata: DraftCategorySuggestionMetadata,
    ) {
        stored = stored?.copy(
            analyzedPrimaryImageKey = metadata.analyzedPrimaryImageKey,
            suggestionSemanticKey = metadata.suggestionSemanticKey,
            suggestionConfidence = metadata.suggestionConfidence,
            suggestionSupportingLabels = metadata.suggestionSupportingLabels,
            suggestionDismissedForImageKey = metadata.suggestionDismissedForImageKey,
            categoryWasManuallySelected = metadata.categoryWasManuallySelected,
        )
    }
}

private class FakeCategoryRepository(private val items: List<Category>) : CategoryRepository {
    override fun observe(type: TransactionType?, includeArchived: Boolean): Flow<List<Category>> =
        flowOf(if (type != null) items.filter { it.type == type } else items)
    override suspend fun create(command: CreateCategoryCommand) = Outcome.Success(CategoryId("new-cat"))
    override suspend fun update(command: UpdateCategoryCommand) = Outcome.Success(Unit)
    override suspend fun archive(id: CategoryId) = Outcome.Success(Unit)
    override suspend fun reorder(type: TransactionType, ids: List<CategoryId>) = Outcome.Success(Unit)
}

private class FakeAccountRepository(private val items: List<Account>) : AccountRepository {
    override fun observeAll(includeArchived: Boolean) = flowOf(items)
    override fun observeAllDetails(includeArchived: Boolean) = flowOf(items.map { AccountDetails(it, it.openingBalance) })
    override fun observeDetails(id: AccountId) = flowOf(null)
    override fun observeBalance(id: AccountId) = flowOf(null)
    override fun observeNetWorth(): Flow<Money> = flowOf(Money(0L, CurrencyCode.of("VND")))
    override suspend fun create(command: CreateAccountCommand) = Outcome.Success(AccountId("new-acc"))
    override suspend fun update(command: UpdateAccountCommand) = Outcome.Success(Unit)
    override suspend fun archive(id: AccountId) = Outcome.Success(Unit)
    override suspend fun reorder(ids: List<AccountId>) = Outcome.Success(Unit)
}

private class FakeSettingsRepository : SettingsRepository {
    override val settings = flowOf(UserSettings())
    override suspend fun updateCurrency(code: String) = Outcome.Success(Unit)
    override suspend fun updateTheme(mode: ThemeMode) = Outcome.Success(Unit)
    override suspend fun updateAmountVisibility(value: AmountVisibility) = Outcome.Success(Unit)
    override suspend fun updateWidgetSettings(value: WidgetSettings) = Outcome.Success(Unit)
}

private class FakeTransactionRepository(
    private val existing: Map<TransactionId, TransactionDetails> = emptyMap()
) : TransactionRepository {
    override fun observe(id: TransactionId) = flowOf(existing[id])
    override fun observeCalendar(range: DateRange, filter: TransactionFilter) = flowOf(emptyList<CalendarDaySummary>())
    override fun observeRecapTransactions(range: DateRange) = flowOf(emptyList<TransactionDetails>())
    override fun observeMapPhotos(filter: TransactionFilter) = flowOf(emptyList<MapPhoto>())
    override fun observeWidgetPhoto(selection: WidgetSelection) = flowOf(null)
    override suspend fun widgetPhotoCandidates(limit: Int) = emptyList<WidgetPhoto>()
    override suspend fun page(filter: TransactionFilter, page: PageRequest) = Outcome.Success(Page(emptyList<TransactionSummary>(), null))
    override suspend fun create(command: CreateTransactionCommand) = Outcome.Success(TransactionId("tx-1"))
    override suspend fun update(command: UpdateTransactionCommand) = Outcome.Success(Unit)
    override suspend fun markDeleted(id: TransactionId, deletedAt: Long) = Outcome.Success(DeletedTransaction(id, deletedAt))
    override suspend fun restore(id: TransactionId) = Outcome.Success(Unit)
    override suspend fun purgeDeleted(id: TransactionId) = Outcome.Success(PurgedTransactionMedia(emptyList()))
}

private class FakeBudgetRepository : BudgetRepository {
    override fun observeMonth(monthStartEpochDay: Long) = flowOf(emptyList<BudgetProgress>())
    override suspend fun upsert(command: SaveBudgetCommand) = Outcome.Success(BudgetId("b1"))
    override suspend fun delete(id: BudgetId) = Outcome.Success(Unit)
    override suspend fun pendingThresholds(monthStartEpochDay: Long) = Outcome.Success(emptyList<ThresholdCrossing>())
    override suspend fun markThresholdDelivered(id: BudgetId, percent: Int, at: Long) = Outcome.Success(Unit)
    override suspend fun resetThresholdIfBelow(id: BudgetId, percent: Int) = Outcome.Success(Unit)
}

private class FakeMediaStore : MediaStorePort {
    override suspend fun stageCapture(operationId: MediaOperationId, imageId: MediaImageId) =
        Outcome.Success(StagedMedia(operationId, imageId))
    override suspend fun stageImport(operationId: MediaOperationId, imageId: MediaImageId, declaredMimeType: String?, input: InputStream) =
        Outcome.Success(StagedMedia(operationId, imageId))
    override suspend fun inspectDraft(staged: StagedMedia) = Outcome.Success(DraftInspection(100, 100, "image/jpeg", 0, false, null, null))
    override suspend fun normalize(staged: StagedMedia) = Outcome.Success(NormalizedMedia(staged, "content://uri", 100, 100, "image/jpeg", 100, "hash", null, null))
    override fun previewUri(staged: StagedMedia) = "content://preview"
    override suspend fun discardStaged(staged: StagedMedia) = Outcome.Success(Unit)
    override suspend fun promote(media: NormalizedMedia) = Outcome.Success(media.canonicalUri)
    override suspend fun markCommitted(staged: StagedMedia) = Outcome.Success(Unit)
    override suspend fun exists(canonicalUri: String) = true
    override suspend fun openRead(canonicalUri: String): Outcome<InputStream> = Outcome.Success(ByteArrayInputStream(byteArrayOf(1)))
    override suspend fun moveToTrash(canonicalUri: String, operationId: MediaOperationId) = Outcome.Success(Unit)
    override suspend fun prepareShare(canonicalUri: String) = Outcome.Success(canonicalUri)
    override suspend fun reconcile(nowEpochMillis: Long) = Outcome.Success(MediaReconciliationReport(0, 0, 0, 0, 0))
    override suspend fun measureStorage() = Outcome.Success(MediaStorageUsage(0, 0, 0))
    override suspend fun deleteOwned(uris: List<String>) = Outcome.Success(Unit)
}
