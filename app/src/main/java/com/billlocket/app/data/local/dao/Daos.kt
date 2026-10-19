package com.billlocket.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.billlocket.app.data.local.entity.AccountEntity
import com.billlocket.app.data.local.entity.BudgetEntity
import com.billlocket.app.data.local.entity.BudgetThresholdEventEntity
import com.billlocket.app.data.local.entity.CategoryEntity
import com.billlocket.app.data.local.entity.TransactionEntity
import com.billlocket.app.data.local.entity.TransactionImageEntity
import com.billlocket.app.data.local.entity.TransactionSearchEntity
import com.billlocket.app.data.local.entity.TransferEntity
import com.billlocket.app.data.local.model.AccountBalance
import com.billlocket.app.data.local.model.BudgetProgress
import com.billlocket.app.data.local.model.CategoryTotal
import com.billlocket.app.data.local.model.DayTotals
import com.billlocket.app.data.local.model.MapImageMarker
import com.billlocket.app.data.local.model.PeriodTotals
import com.billlocket.app.data.local.model.TransactionSummary
import com.billlocket.app.data.local.model.TransactionWithImages
import com.billlocket.app.data.local.model.WidgetCandidate
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts ORDER BY is_archived, sort_order, id")
    suspend fun listAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts ORDER BY is_archived, sort_order, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE account_id = :id")
    suspend fun transactionReferenceCount(id: String): Int

    @Query(
        """
        SELECT a.id AS account_id, a.currency_code,
          a.opening_balance_minor
          + COALESCE((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amount_minor ELSE -t.amount_minor END)
              FROM transactions t WHERE t.account_id = a.id AND t.deleted_at IS NULL), 0)
          + COALESCE((SELECT SUM(tr.amount_minor) FROM transfers tr
              WHERE tr.to_account_id = a.id AND tr.deleted_at IS NULL), 0)
          - COALESCE((SELECT SUM(tr.amount_minor) FROM transfers tr
              WHERE tr.from_account_id = a.id AND tr.deleted_at IS NULL), 0) AS balance_minor
        FROM accounts a WHERE a.id = :id
        """
    )
    suspend fun balance(id: String): AccountBalance?

    @Query(
        """
        SELECT a.id AS account_id, a.currency_code,
          a.opening_balance_minor
          + COALESCE((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amount_minor ELSE -t.amount_minor END)
              FROM transactions t WHERE t.account_id = a.id AND t.deleted_at IS NULL), 0)
          + COALESCE((SELECT SUM(tr.amount_minor) FROM transfers tr
              WHERE tr.to_account_id = a.id AND tr.deleted_at IS NULL), 0)
          - COALESCE((SELECT SUM(tr.amount_minor) FROM transfers tr
              WHERE tr.from_account_id = a.id AND tr.deleted_at IS NULL), 0) AS balance_minor
        FROM accounts a WHERE a.id = :id
        """
    )
    fun observeBalance(id: String): Flow<AccountBalance?>

    @Query(
        """
        SELECT a.id AS account_id, a.currency_code,
          a.opening_balance_minor
          + COALESCE(tx.balance_delta, 0)
          + COALESCE(incoming.total_minor, 0)
          - COALESCE(outgoing.total_minor, 0) AS balance_minor
        FROM accounts a
        LEFT JOIN (
          SELECT account_id,
            SUM(CASE WHEN type = 'INCOME' THEN amount_minor ELSE -amount_minor END) AS balance_delta
          FROM transactions
          WHERE deleted_at IS NULL
          GROUP BY account_id
        ) tx ON tx.account_id = a.id
        LEFT JOIN (
          SELECT to_account_id AS account_id, SUM(amount_minor) AS total_minor
          FROM transfers
          WHERE deleted_at IS NULL
          GROUP BY to_account_id
        ) incoming ON incoming.account_id = a.id
        LEFT JOIN (
          SELECT from_account_id AS account_id, SUM(amount_minor) AS total_minor
          FROM transfers
          WHERE deleted_at IS NULL
          GROUP BY from_account_id
        ) outgoing ON outgoing.account_id = a.id
        WHERE (:includeArchived OR a.is_archived = 0)
        ORDER BY a.is_archived, a.sort_order, a.id
        """
    )
    fun observeAllBalances(includeArchived: Boolean): Flow<List<AccountBalance>>

    @Query("UPDATE accounts SET sort_order = :position, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: String, position: Int, updatedAt: Long): Int

    @Transaction
    suspend fun reorder(ids: List<String>, updatedAt: Long) {
        ids.forEachIndexed { position, id -> check(updateSortOrder(id, position, updatedAt) == 1) }
    }
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY transaction_type, is_archived, sort_order, id")
    suspend fun listAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories ORDER BY transaction_type, is_archived, sort_order, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :id")
    suspend fun transactionReferenceCount(id: String): Int

    @Query("UPDATE categories SET sort_order = :position, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: String, position: Int, updatedAt: Long): Int

    @Transaction
    suspend fun reorder(ids: List<String>, updatedAt: Long) {
        ids.forEachIndexed { position, id -> check(updateSortOrder(id, position, updatedAt) == 1) }
    }
}

@Dao
interface TransactionDao {
    @Query("DELETE FROM transaction_search")
    suspend fun deleteAllSearch()

    @Query("DELETE FROM transaction_images")
    suspend fun deleteAllImages()

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImages(images: List<TransactionImageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: TransactionSearchEntity)

    @Query("DELETE FROM transaction_search WHERE transaction_id = :transactionId")
    suspend fun deleteSearch(transactionId: String)

    @Query("DELETE FROM transaction_images WHERE transaction_id = :transactionId")
    suspend fun deleteImages(transactionId: String)

    @Query("SELECT content_uri FROM transaction_images")
    suspend fun allImageUris(): List<String>

    @Query("SELECT COUNT(*) FROM transaction_images WHERE content_uri = :contentUri")
    suspend fun imageReferenceCount(contentUri: String): Int

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun hardDelete(transactionId: String): Int

    @Query("UPDATE transactions SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :transactionId AND deleted_at IS NULL")
    suspend fun softDelete(transactionId: String, deletedAt: Long): Int

    @Query("UPDATE transactions SET deleted_at = NULL, updated_at = :updatedAt WHERE id = :transactionId AND deleted_at IS NOT NULL")
    suspend fun restore(transactionId: String, updatedAt: Long): Int

    @Transaction
    suspend fun create(
        transaction: TransactionEntity,
        images: List<TransactionImageEntity>,
        search: TransactionSearchEntity,
    ) {
        insertTransaction(transaction)
        if (images.isNotEmpty()) insertImages(images)
        insertSearch(search)
    }

    @Transaction
    suspend fun replace(
        transaction: TransactionEntity,
        images: List<TransactionImageEntity>,
        search: TransactionSearchEntity,
    ) {
        updateTransaction(transaction)
        deleteImages(transaction.id)
        if (images.isNotEmpty()) insertImages(images)
        deleteSearch(transaction.id)
        insertSearch(search)
    }

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun detail(id: String): TransactionWithImages?

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeDetail(id: String): Flow<TransactionWithImages?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findEntity(id: String): TransactionEntity?

    @Transaction
    @Query("SELECT * FROM transactions WHERE deleted_at IS NULL ORDER BY occurred_at DESC, id DESC")
    fun observeActive(): Flow<List<TransactionWithImages>>

    @Transaction
    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NULL
          AND local_date_epoch_day BETWEEN :fromDay AND :toDay
          AND (:type IS NULL OR type = :type)
          AND (:accountId IS NULL OR account_id = :accountId)
        ORDER BY occurred_at DESC, id DESC
        """
    )
    fun observeCalendarRange(
        fromDay: Long,
        toDay: Long,
        type: String? = null,
        accountId: String? = null,
    ): Flow<List<TransactionWithImages>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE deleted_at IS NULL ORDER BY occurred_at DESC, id DESC")
    suspend fun listActive(): List<TransactionWithImages>

    @Query(
        """
        SELECT t.id, t.type, t.amount_minor, t.currency_code, t.note, t.occurred_at,
          t.local_date_epoch_day, t.zone_id, t.account_id, a.name AS account_name,
          t.category_id, c.name AS category_name,
          (SELECT i.content_uri FROM transaction_images i WHERE i.transaction_id = t.id ORDER BY i.position, i.id LIMIT 1) AS thumbnail_uri
        FROM transactions t
        JOIN accounts a ON a.id = t.account_id
        JOIN categories c ON c.id = t.category_id
        WHERE t.deleted_at IS NULL
          AND (:fromDay IS NULL OR t.local_date_epoch_day >= :fromDay)
          AND (:toDay IS NULL OR t.local_date_epoch_day <= :toDay)
          AND (:type IS NULL OR t.type = :type)
          AND (:accountId IS NULL OR t.account_id = :accountId)
          AND (:categoryId IS NULL OR t.category_id = :categoryId)
          AND (:beforeEpochMillis IS NULL OR t.occurred_at < :beforeEpochMillis
            OR (t.occurred_at = :beforeEpochMillis AND t.id < :beforeId))
        ORDER BY t.occurred_at DESC, t.id DESC
        LIMIT :limit
        """
    )
    suspend fun pageFiltered(
        fromDay: Long?,
        toDay: Long?,
        type: String?,
        accountId: String?,
        categoryId: String?,
        beforeEpochMillis: Long?,
        beforeId: String?,
        limit: Int,
    ): List<TransactionSummary>

    @Query(
        """
        SELECT t.id, t.type, t.amount_minor, t.currency_code, t.note, t.occurred_at,
          t.local_date_epoch_day, t.zone_id, t.account_id, a.name AS account_name,
          t.category_id, c.name AS category_name,
          (SELECT i.content_uri FROM transaction_images i WHERE i.transaction_id = t.id ORDER BY i.position, i.id LIMIT 1) AS thumbnail_uri
        FROM transaction_search
        JOIN transactions t ON t.id = transaction_search.transaction_id
        JOIN accounts a ON a.id = t.account_id
        JOIN categories c ON c.id = t.category_id
        WHERE transaction_search MATCH :query AND t.deleted_at IS NULL
          AND (:fromDay IS NULL OR t.local_date_epoch_day >= :fromDay)
          AND (:toDay IS NULL OR t.local_date_epoch_day <= :toDay)
          AND (:type IS NULL OR t.type = :type)
          AND (:accountId IS NULL OR t.account_id = :accountId)
          AND (:categoryId IS NULL OR t.category_id = :categoryId)
          AND (:beforeEpochMillis IS NULL OR t.occurred_at < :beforeEpochMillis
            OR (t.occurred_at = :beforeEpochMillis AND t.id < :beforeId))
        ORDER BY t.occurred_at DESC, t.id DESC
        LIMIT :limit
        """
    )
    suspend fun searchFiltered(
        query: String,
        fromDay: Long?,
        toDay: Long?,
        type: String?,
        accountId: String?,
        categoryId: String?,
        beforeEpochMillis: Long?,
        beforeId: String?,
        limit: Int,
    ): List<TransactionSummary>
}

@Dao
interface TransferDao {
    @Query("DELETE FROM transfers")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transfer: TransferEntity)

    @Update
    suspend fun update(transfer: TransferEntity)

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun findById(id: String): TransferEntity?

    @Query("SELECT * FROM transfers WHERE id = :id")
    fun observeById(id: String): Flow<TransferEntity?>

    @Query("SELECT * FROM transfers ORDER BY occurred_at DESC, id DESC")
    suspend fun listAll(): List<TransferEntity>

    @Query(
        """
        SELECT * FROM transfers
        WHERE deleted_at IS NULL
          AND (:accountId IS NULL OR from_account_id = :accountId OR to_account_id = :accountId)
          AND (:fromDay IS NULL OR local_date_epoch_day >= :fromDay)
          AND (:toDay IS NULL OR local_date_epoch_day <= :toDay)
          AND (:beforeEpochMillis IS NULL OR occurred_at < :beforeEpochMillis
            OR (occurred_at = :beforeEpochMillis AND id < :beforeId))
        ORDER BY occurred_at DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun pageFiltered(
        accountId: String?,
        fromDay: Long?,
        toDay: Long?,
        beforeEpochMillis: Long?,
        beforeId: String?,
        limit: Int,
    ): List<TransferEntity>

    @Query("UPDATE transfers SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :id AND deleted_at IS NULL")
    suspend fun softDelete(id: String, deletedAt: Long): Int

    @Query("UPDATE transfers SET deleted_at = NULL, updated_at = :updatedAt WHERE id = :id AND deleted_at IS NOT NULL")
    suspend fun restore(id: String, updatedAt: Long): Int
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: BudgetEntity)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun findById(id: String): BudgetEntity?

    @Query("SELECT * FROM budget_threshold_events WHERE budget_id = :budgetId")
    suspend fun thresholdEvents(budgetId: String): List<BudgetThresholdEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordThreshold(event: BudgetThresholdEventEntity): Long

    @Query("DELETE FROM budget_threshold_events WHERE budget_id = :budgetId AND threshold_percent = :thresholdPercent")
    suspend fun clearThreshold(budgetId: String, thresholdPercent: Int): Int

    @Query("SELECT * FROM budgets WHERE month_start_epoch_day = :monthStart ORDER BY scope_key, id")
    fun observeMonth(monthStart: Long): Flow<List<BudgetEntity>>
}

@Dao
interface ProjectionDao {
    @Query("SELECT (SELECT COUNT(*) FROM transactions) + (SELECT COUNT(*) FROM transfers) + (SELECT COUNT(*) FROM budgets)")
    suspend fun financialRecordCount(): Int

    @Query("SELECT (SELECT COUNT(*) FROM transactions) + (SELECT COUNT(*) FROM transfers) + (SELECT COUNT(*) FROM budgets)")
    fun observeFinancialVersion(): Flow<Int>
    @Query(
        """
        SELECT t.local_date_epoch_day,
          COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount_minor ELSE 0 END), 0) AS income_minor,
          COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount_minor ELSE 0 END), 0) AS expense_minor,
          (SELECT i.content_uri FROM transaction_images i
             JOIN transactions newest ON newest.id = i.transaction_id
             WHERE newest.deleted_at IS NULL AND newest.local_date_epoch_day = t.local_date_epoch_day
             ORDER BY newest.occurred_at DESC, newest.id DESC, i.position, i.id LIMIT 1) AS representative_uri
        FROM transactions t
        WHERE t.deleted_at IS NULL AND t.local_date_epoch_day BETWEEN :fromDay AND :toDay
          AND (:accountId IS NULL OR t.account_id = :accountId)
        GROUP BY t.local_date_epoch_day
        ORDER BY t.local_date_epoch_day
        """
    )
    suspend fun dayTotals(fromDay: Long, toDay: Long, accountId: String? = null): List<DayTotals>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_minor ELSE 0 END), 0) AS income_minor,
          COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_minor ELSE 0 END), 0) AS expense_minor
        FROM transactions
        WHERE deleted_at IS NULL AND local_date_epoch_day BETWEEN :fromDay AND :toDay
          AND (:accountId IS NULL OR account_id = :accountId)
        """
    )
    suspend fun periodTotals(fromDay: Long, toDay: Long, accountId: String? = null): PeriodTotals

    @Query(
        """
        SELECT c.id AS category_id, c.name AS category_name, COALESCE(SUM(t.amount_minor), 0) AS total_minor
        FROM categories c JOIN transactions t ON t.category_id = c.id
        WHERE t.deleted_at IS NULL AND t.type = :type
          AND t.local_date_epoch_day BETWEEN :fromDay AND :toDay
          AND (:accountId IS NULL OR t.account_id = :accountId)
        GROUP BY c.id, c.name ORDER BY total_minor DESC, c.id
        """
    )
    suspend fun categoryTotals(type: String, fromDay: Long, toDay: Long, accountId: String? = null): List<CategoryTotal>

    @Query(
        """
        SELECT b.id AS budget_id, b.limit_minor, b.currency_code, b.warning_percent,
          COALESCE(SUM(t.amount_minor), 0) AS spent_minor
        FROM budgets b LEFT JOIN transactions t
          ON t.deleted_at IS NULL AND t.type = 'EXPENSE'
          AND t.local_date_epoch_day >= b.month_start_epoch_day
          AND t.local_date_epoch_day < :nextMonthStart
          AND t.local_date_epoch_day <= :maxDay
          AND (b.category_id IS NULL OR t.category_id = b.category_id)
        WHERE b.month_start_epoch_day = :monthStart
        GROUP BY b.id, b.limit_minor, b.currency_code, b.warning_percent
        ORDER BY b.scope_key, b.id
        """
    )
    suspend fun budgetProgress(monthStart: Long, nextMonthStart: Long, maxDay: Long): List<BudgetProgress>

    @Query(
        """
        SELECT i.id AS image_id, i.transaction_id, i.content_uri, i.latitude, i.longitude,
          i.place_name, t.occurred_at
        FROM transaction_images i JOIN transactions t ON t.id = i.transaction_id
        WHERE t.deleted_at IS NULL AND i.latitude IS NOT NULL AND i.longitude IS NOT NULL
          AND i.location_confirmed_at IS NOT NULL
        ORDER BY t.occurred_at DESC, t.id DESC, i.position, i.id
        """
    )
    suspend fun mapMarkers(): List<MapImageMarker>

    @Query(
        """
        SELECT t.id AS transaction_id, i.id AS image_id, i.content_uri, t.occurred_at,
          t.amount_minor, t.currency_code, c.name AS category_name
        FROM transactions t JOIN transaction_images i ON i.transaction_id = t.id
          JOIN categories c ON c.id = t.category_id
        WHERE t.deleted_at IS NULL
        ORDER BY t.occurred_at DESC, t.id DESC, i.position, i.id
        LIMIT :limit
        """
    )
    suspend fun widgetCandidates(limit: Int): List<WidgetCandidate>
}
