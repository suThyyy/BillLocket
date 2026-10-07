package com.billlocket.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.billlocket.app.data.local.entity.AccountEntity
import com.billlocket.app.data.local.entity.BudgetEntity
import com.billlocket.app.data.local.entity.CategoryEntity
import com.billlocket.app.data.local.entity.TransactionEntity
import com.billlocket.app.data.local.entity.TransactionImageEntity
import com.billlocket.app.data.local.entity.TransactionSearchEntity
import com.billlocket.app.data.local.entity.TransferEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BillLocketDatabaseTest {
    private lateinit var database: BillLocketDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, BillLocketDatabase::class.java)
                .allowMainThreadQueries()
                .addCallback(DatabaseInitializer.callback)
                .build()
        database.openHelper.writableDatabase
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun freshDatabaseSeedsDefaultsAndPassesForeignKeyCheck() = runBlocking {
        val accounts = database.accountDao().observeAll().first()
        val categories = database.categoryDao().observeAll().first()

        assertEquals(listOf(DatabaseInitializer.INITIAL_ACCOUNT_ID), accounts.map { it.id })
        assertEquals(6, categories.size)
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }

    @Test
    fun guardsRejectInvalidAmountsTransfersAndLocations() = runBlocking {
        val expense = expenseCategory()
        val account = initialAccount()
        val transaction = transaction(expense.id, account.id)
        database.transactionDao().create(transaction, emptyList(), TransactionSearchEntity(transaction.id, "coffee"))

        assertConstraint {
            database.transactionDao().insertTransaction(transaction(expense.id, account.id).copy(id = "bad", amountMinor = 0))
        }
        assertConstraint {
            database.transferDao().insert(transfer(account.id, account.id))
        }
        assertConstraint {
            database.transactionDao().insertImages(
                listOf(image(transaction.id).copy(latitude = 91.0, longitude = 106.0, locationSource = "CURRENT", locationConfirmedAt = 2))
            )
        }
    }

    @Test
    fun transactionWriteIsAtomicSearchableAndCascadesMetadata() = runBlocking {
        val transaction = transaction(expenseCategory().id, initialAccount().id)
        val image = image(transaction.id)
        database.transactionDao().create(transaction, listOf(image), TransactionSearchEntity(transaction.id, "cà phê sáng"))

        val detail = database.transactionDao().detail(transaction.id)
        assertNotNull(detail)
        assertEquals(listOf(image.id), detail?.images?.map { it.id })
        assertEquals(listOf(transaction.id), database.transactionDao().search("phê", 20).map { it.id })

        assertEquals(1, database.transactionDao().hardDelete(transaction.id))
        assertEquals(null, database.transactionDao().detail(transaction.id))
        assertTrue(database.transactionDao().search("phê", 20).isEmpty())
    }

    @Test
    fun balanceStatisticsBudgetAndMapProjectionsExcludeSoftDeletedRows() = runBlocking {
        val accountDao = database.accountDao()
        val second = account(id = "account-2", name = "Ngân hàng", normalizedName = "ngân hàng", type = "BANK")
        accountDao.insert(second)
        val expense = expenseCategory()
        val transaction = transaction(expense.id, initialAccount().id)
        database.transactionDao().create(
            transaction,
            listOf(
                image(transaction.id).copy(
                    latitude = 10.77,
                    longitude = 106.69,
                    locationSource = "CURRENT",
                    locationConfirmedAt = 2,
                )
            ),
            TransactionSearchEntity(transaction.id, "lunch"),
        )
        database.transferDao().insert(transfer(initialAccount().id, second.id))
        database.budgetDao().insert(
            BudgetEntity(
                id = "budget-1",
                monthStartEpochDay = 20_000,
                categoryId = null,
                scopeKey = "OVERALL",
                limitMinor = 500_000,
                currencyCode = "VND",
                createdAt = 1,
                updatedAt = 1,
            )
        )

        assertEquals(-150_000, accountDao.balance(initialAccount().id)?.balanceMinor)
        assertEquals(100_000, database.projectionDao().periodTotals(20_000, 20_030).expenseMinor)
        assertEquals(100_000, database.projectionDao().budgetProgress(20_000, 20_031).single().spentMinor)
        assertEquals(1, database.projectionDao().mapMarkers().size)

        database.transactionDao().softDelete(transaction.id, 3)
        assertEquals(-50_000, accountDao.balance(initialAccount().id)?.balanceMinor)
        assertEquals(0, database.projectionDao().periodTotals(20_000, 20_030).expenseMinor)
        assertTrue(database.projectionDao().mapMarkers().isEmpty())
    }

    private suspend fun assertConstraint(block: suspend () -> Unit) {
        var thrown = false
        try {
            block()
        } catch (_: SQLiteException) {
            thrown = true
        }
        assertTrue("Expected SQLite to reject an invalid row", thrown)
    }

    private suspend fun initialAccount(): AccountEntity =
        requireNotNull(database.accountDao().findById(DatabaseInitializer.INITIAL_ACCOUNT_ID))

    private suspend fun expenseCategory(): CategoryEntity =
        requireNotNull(database.categoryDao().findById(DatabaseInitializer.FOOD_CATEGORY_ID))

    private fun account(
        id: String,
        name: String,
        normalizedName: String,
        type: String,
    ) = AccountEntity(id, name, normalizedName, type, 0, "VND", 1, false, 1, 1)

    private fun transaction(categoryId: String, accountId: String) =
        TransactionEntity(
            id = "transaction-1",
            type = "EXPENSE",
            amountMinor = 100_000,
            currencyCode = "VND",
            categoryId = categoryId,
            accountId = accountId,
            note = "cà phê sáng",
            occurredAt = 1_700_000_000_000,
            localDateEpochDay = 20_000,
            zoneId = "Asia/Ho_Chi_Minh",
            createdAt = 1,
            updatedAt = 1,
            deletedAt = null,
        )

    private fun image(transactionId: String) =
        TransactionImageEntity(
            id = "image-1",
            transactionId = transactionId,
            contentUri = "content://com.billlocket.app.media/image-1",
            position = 0,
            mimeType = "image/jpeg",
            widthPx = 1200,
            heightPx = 1600,
            sizeBytes = 50_000,
            capturedAt = null,
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            placeName = null,
            locationSource = null,
            locationConfirmedAt = null,
            createdAt = 1,
            updatedAt = 1,
        )

    private fun transfer(fromAccountId: String, toAccountId: String) =
        TransferEntity(
            id = "transfer-1",
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amountMinor = 50_000,
            currencyCode = "VND",
            note = null,
            occurredAt = 1_700_000_000_000,
            localDateEpochDay = 20_000,
            zoneId = "Asia/Ho_Chi_Minh",
            createdAt = 1,
            updatedAt = 1,
            deletedAt = null,
        )
}
