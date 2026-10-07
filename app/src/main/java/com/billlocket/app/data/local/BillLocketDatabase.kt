package com.billlocket.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.billlocket.app.data.local.dao.AccountDao
import com.billlocket.app.data.local.dao.BudgetDao
import com.billlocket.app.data.local.dao.CategoryDao
import com.billlocket.app.data.local.dao.ProjectionDao
import com.billlocket.app.data.local.dao.TransactionDao
import com.billlocket.app.data.local.dao.TransferDao
import com.billlocket.app.data.local.entity.AccountEntity
import com.billlocket.app.data.local.entity.BudgetEntity
import com.billlocket.app.data.local.entity.BudgetThresholdEventEntity
import com.billlocket.app.data.local.entity.CategoryEntity
import com.billlocket.app.data.local.entity.TransactionEntity
import com.billlocket.app.data.local.entity.TransactionImageEntity
import com.billlocket.app.data.local.entity.TransactionSearchEntity
import com.billlocket.app.data.local.entity.TransferEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionImageEntity::class,
        TransferEntity::class,
        BudgetEntity::class,
        BudgetThresholdEventEntity::class,
        TransactionSearchEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BillLocketDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun transferDao(): TransferDao

    abstract fun budgetDao(): BudgetDao

    abstract fun projectionDao(): ProjectionDao

    companion object {
        const val NAME = "billlocket.db"
    }
}
