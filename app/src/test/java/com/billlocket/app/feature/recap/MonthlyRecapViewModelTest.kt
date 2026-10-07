package com.billlocket.app.feature.recap

import androidx.lifecycle.SavedStateHandle
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.repository.CategoryRepository
import com.billlocket.app.domain.repository.InsightsRepository
import com.billlocket.app.domain.repository.TransactionRepository
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.feature.transaction.MainDispatcherRule
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyRecapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedToday = LocalDate.of(2026, 10, 6)
    private val clock = Clock {
        fixedToday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    private val currency = CurrencyCode.of("VND")

    private val dummyInsights = PeriodInsights(
        income = Money(2_000_000, currency),
        expense = Money(1_000_000, currency),
        net = Money(1_000_000, currency),
        categories = listOf(CategoryDistribution(CategoryId("food"), "Ăn uống", Money(1_000_000, currency))),
        trend = emptyList(),
    )

    private val fakeTransactions = object : TransactionRepository {
        override fun observeRecapTransactions(range: DateRange): Flow<List<TransactionDetails>> = flowOf(emptyList())
        override fun observe(id: TransactionId): Flow<TransactionDetails?> = flowOf(null)
        override fun observeCalendar(range: DateRange, filter: TransactionFilter): Flow<List<CalendarDaySummary>> = flowOf(emptyList())
        override fun observeMapPhotos(filter: TransactionFilter): Flow<List<MapPhoto>> = flowOf(emptyList())
        override fun observeWidgetPhoto(selection: WidgetSelection): Flow<WidgetPhoto?> = flowOf(null)
        override suspend fun widgetPhotoCandidates(limit: Int): List<WidgetPhoto> = emptyList()
        override suspend fun page(filter: TransactionFilter, page: PageRequest): Outcome<Page<TransactionSummary>> =
            Outcome.Success(Page(emptyList(), null))
        override suspend fun create(command: CreateTransactionCommand): Outcome<TransactionId> = Outcome.Success(TransactionId("tx-1"))
        override suspend fun update(command: UpdateTransactionCommand): Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun markDeleted(id: TransactionId, deletedAt: Long): Outcome<DeletedTransaction> =
            Outcome.Success(DeletedTransaction(id, deletedAt))
        override suspend fun restore(id: TransactionId): Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun purgeDeleted(id: TransactionId): Outcome<PurgedTransactionMedia> =
            Outcome.Success(PurgedTransactionMedia(emptyList()))
    }

    private val fakeInsights = object : InsightsRepository {
        override fun observePeriod(query: InsightsQuery): Flow<PeriodInsights> = flowOf(dummyInsights)
    }

    private val fakeCategories = object : CategoryRepository {
        override fun observe(type: TransactionType?, includeArchived: Boolean): Flow<List<Category>> = flowOf(
            listOf(Category(CategoryId("food"), "Ăn uống", TransactionType.EXPENSE, 0xFFFFB800, "icon_food", 0, isDefault = true, isArchived = false)),
        )
        override suspend fun create(command: CreateCategoryCommand): Outcome<CategoryId> = Outcome.Success(CategoryId("c1"))
        override suspend fun update(command: UpdateCategoryCommand): Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun archive(id: CategoryId): Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun reorder(type: TransactionType, ids: List<CategoryId>): Outcome<Unit> = Outcome.Success(Unit)
    }

    @Test
    fun `initializes with provided month epoch day coerced to current month`() = runTest {
        val pastDay = LocalDate.of(2026, 9, 15).toEpochDay()
        val handle = SavedStateHandle(mapOf("monthEpochDay" to pastDay))
        val exporter = MonthlyRecapExporter { "content://billlocket/share.jpg" }

        val viewModel = MonthlyRecapViewModel(handle, fakeTransactions, fakeInsights, fakeCategories, clock, exporter)
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 9), viewModel.state.value.month)
        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.recap)
    }

    @Test
    fun `cannot exceed current month when initializing or navigating next`() = runTest {
        val futureDay = LocalDate.of(2026, 12, 1).toEpochDay()
        val handle = SavedStateHandle(mapOf("monthEpochDay" to futureDay))
        val exporter = MonthlyRecapExporter { "uri" }

        val viewModel = MonthlyRecapViewModel(handle, fakeTransactions, fakeInsights, fakeCategories, clock, exporter)
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 10), viewModel.state.value.month)

        viewModel.onAction(MonthlyRecapAction.NextMonth)
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 10), viewModel.state.value.month)
    }

    @Test
    fun `navigates previous and next month correctly`() = runTest {
        val handle = SavedStateHandle(mapOf("monthEpochDay" to LocalDate.of(2026, 10, 1).toEpochDay()))
        val exporter = MonthlyRecapExporter { "uri" }

        val viewModel = MonthlyRecapViewModel(handle, fakeTransactions, fakeInsights, fakeCategories, clock, exporter)
        advanceUntilIdle()

        viewModel.onAction(MonthlyRecapAction.PreviousMonth)
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 9), viewModel.state.value.month)

        viewModel.onAction(MonthlyRecapAction.NextMonth)
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 10), viewModel.state.value.month)
    }

    @Test
    fun `privacy toggles update state`() = runTest {
        val handle = SavedStateHandle()
        val exporter = MonthlyRecapExporter { "uri" }
        val viewModel = MonthlyRecapViewModel(handle, fakeTransactions, fakeInsights, fakeCategories, clock, exporter)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.privacy.showAmounts)
        assertFalse(viewModel.state.value.privacy.showNotes)
        assertFalse(viewModel.state.value.privacy.showPlaces)

        viewModel.onAction(MonthlyRecapAction.ShowAmounts(true))
        viewModel.onAction(MonthlyRecapAction.ShowNotes(true))
        viewModel.onAction(MonthlyRecapAction.ShowPlaces(true))

        assertTrue(viewModel.state.value.privacy.showAmounts)
        assertTrue(viewModel.state.value.privacy.showNotes)
        assertTrue(viewModel.state.value.privacy.showPlaces)
    }

    @Test
    fun `export invokes exporter and sends Share effect with uri`() = runTest {
        val handle = SavedStateHandle()
        var exportCount = 0
        val exporter = MonthlyRecapExporter {
            exportCount++
            "content://com.billlocket.app.fileprovider/share/monthly-recap-2026-10.jpg"
        }
        val viewModel = MonthlyRecapViewModel(handle, fakeTransactions, fakeInsights, fakeCategories, clock, exporter)
        advanceUntilIdle()

        val effectDeferred = async { viewModel.effects.first() }
        viewModel.onAction(MonthlyRecapAction.Export)
        advanceUntilIdle()

        assertEquals(1, exportCount)
        val effect = effectDeferred.await()
        assertTrue(effect is MonthlyRecapEffect.Share)
        assertEquals("content://com.billlocket.app.fileprovider/share/monthly-recap-2026-10.jpg", (effect as MonthlyRecapEffect.Share).uri)
        assertFalse(viewModel.state.value.isExporting)
    }
}
