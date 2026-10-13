package com.billlocket.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.billlocket.app.data.local.BillLocketDatabase
import com.billlocket.app.data.local.DatabaseInitializer
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.IdGenerator
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.locale.FinancialNameLocalizer
import com.billlocket.app.locale.AppLocaleControllerImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryContractTest {
    private lateinit var database: BillLocketDatabase
    private lateinit var settings: SettingsRepositoryImpl
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var categories: CategoryRepositoryImpl
    private lateinit var transactions: TransactionRepositoryImpl
    private lateinit var transfers: TransferRepositoryImpl
    private lateinit var budgets: BudgetRepositoryImpl
    private lateinit var insights: InsightsRepositoryImpl
    private val clock = MutableClock(1_700_000_000_000)
    private val ids = SequenceIds()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BillLocketDatabase::class.java)
            .allowMainThreadQueries().addCallback(DatabaseInitializer.callback).build()
        database.openHelper.writableDatabase
        val preferences = context.getSharedPreferences("repository-contract-${System.nanoTime()}", Context.MODE_PRIVATE)
        val financialNames = FinancialNameLocalizer(context, AppLocaleControllerImpl(context))
        settings = SettingsRepositoryImpl(preferences, database.projectionDao())
        accounts = AccountRepositoryImpl(database.accountDao(), settings, ids, clock, financialNames)
        categories = CategoryRepositoryImpl(database.categoryDao(), ids, clock, financialNames)
        transactions = TransactionRepositoryImpl(database.transactionDao(), database.accountDao(), database.categoryDao(), database.projectionDao(), settings, ids, clock, financialNames)
        transfers = TransferRepositoryImpl(database.transferDao(), database.accountDao(), settings, ids, clock)
        budgets = BudgetRepositoryImpl(database.budgetDao(), database.projectionDao(), database.categoryDao(), settings, ids, clock)
        insights = InsightsRepositoryImpl(database.projectionDao(), settings, financialNames)
    }

    @After fun tearDown() = database.close()

    @Test
    fun transactionVisibilityArchiveAndTypedFailures() = runBlocking {
        val command = transactionCommand()
        val created = transactions.create(command) as Outcome.Success
        assertEquals(1, (transactions.page(TransactionFilter(), PageRequest()) as Outcome.Success).value.items.size)

        transactions.markDeleted(created.value, clock.nowEpochMillis())
        assertTrue((transactions.page(TransactionFilter(), PageRequest()) as Outcome.Success).value.items.isEmpty())
        transactions.restore(created.value)
        assertEquals(1, (transactions.page(TransactionFilter(), PageRequest()) as Outcome.Success).value.items.size)

        accounts.archive(command.account.id)
        val rejected = transactions.create(command)
        assertTrue(rejected is Outcome.Failure && rejected.reason is DomainFailure.InactiveReference)
    }

    @Test
    fun transfersChangeAccountBalancesButRemainNetNeutral() = runBlocking {
        val secondId = (accounts.create(CreateAccountCommand("Ngân hàng", AccountType.BANK, money(0))) as Outcome.Success).value
        val cash = accountRef(DatabaseInitializer.INITIAL_ACCOUNT_ID)
        val bank = accountRef(secondId.value)
        val result = transfers.create(CreateTransferCommand(cash, bank, money(25_000), null, occurred()))
        assertTrue(result is Outcome.Success)

        assertEquals(-25_000, accounts.observeBalance(cash.id).first()?.minorUnits)
        assertEquals(25_000, accounts.observeBalance(bank.id).first()?.minorUnits)
        assertEquals(0, accounts.observeNetWorth().first().minorUnits)
        assertEquals(0, insights.observePeriod(InsightsQuery(DateRange(DAY, DAY))).first().net.minorUnits)
    }

    @Test
    fun budgetsStatisticsAndCurrencyLockUseActiveFinancialRows() = runBlocking {
        val transactionId = (transactions.create(transactionCommand()) as Outcome.Success).value
        val expense = categoryRef(DatabaseInitializer.FOOD_CATEGORY_ID)
        val budgetId = (budgets.upsert(SaveBudgetCommand(MONTH, expense, money(200_000), 80)) as Outcome.Success).value

        val progress = budgets.observeMonth(MONTH).first().single { it.id == budgetId }
        assertEquals(100_000, progress.spent.minorUnits)
        assertEquals(100_000, insights.observePeriod(InsightsQuery(DateRange(DAY, DAY))).first().expense.minorUnits)
        assertTrue(settings.updateCurrency("USD") is Outcome.Failure)

        transactions.markDeleted(transactionId, clock.nowEpochMillis())
        assertEquals(0, insights.observePeriod(InsightsQuery(DateRange(DAY, DAY))).first().expense.minorUnits)
    }

    @Test
    fun settingsPersistAndInvalidCurrencyReturnsTypedFailure() = runBlocking {
        assertTrue(settings.updateTheme(ThemeMode.DARK) is Outcome.Success)
        assertTrue(settings.updateAmountVisibility(AmountVisibility.HIDDEN) is Outcome.Success)
        val current = settings.settings.first()
        assertEquals(ThemeMode.DARK, current.theme)
        assertEquals(AmountVisibility.HIDDEN, current.amountVisibility)
        assertTrue(settings.updateCurrency("bad-code") is Outcome.Failure)
    }

    private suspend fun transactionCommand(): CreateTransactionCommand =
        CreateTransactionCommand(
            TransactionType.EXPENSE,
            money(100_000),
            categoryRef(DatabaseInitializer.FOOD_CATEGORY_ID),
            accountRef(DatabaseInitializer.INITIAL_ACCOUNT_ID),
            "cà phê",
            occurred(),
            emptyList(),
        )

    private suspend fun accountRef(id: String): AccountRef {
        val account = requireNotNull(database.accountDao().findById(id))
        return AccountRef(AccountId(id), AccountType.valueOf(account.type), !account.isArchived, CurrencyCode.of(account.currencyCode))
    }

    private suspend fun categoryRef(id: String): CategoryRef {
        val category = requireNotNull(database.categoryDao().findById(id))
        return CategoryRef(CategoryId(id), TransactionType.valueOf(category.transactionType), !category.isArchived)
    }

    private fun money(amount: Long) = Money(amount, CurrencyCode.of("VND"))
    private fun occurred() = InstantAtZone(clock.nowEpochMillis(), DAY, "Asia/Ho_Chi_Minh")

    private class MutableClock(private var value: Long) : Clock {
        override fun nowEpochMillis() = value++
    }

    private class SequenceIds : IdGenerator {
        private var next = 0
        override fun nextId() = "test-id-${next++}"
    }

    private companion object {
        const val MONTH = 19_997L
        const val DAY = 20_000L
    }
}
