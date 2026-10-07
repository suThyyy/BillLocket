package com.billlocket.app.di

import android.content.Context
import androidx.room.Room
import com.billlocket.app.data.local.BillLocketDatabase
import com.billlocket.app.data.local.DatabaseInitializer
import com.billlocket.app.data.local.dao.AccountDao
import com.billlocket.app.data.local.dao.BudgetDao
import com.billlocket.app.data.local.dao.CategoryDao
import com.billlocket.app.data.local.dao.ProjectionDao
import com.billlocket.app.data.local.dao.TransactionDao
import com.billlocket.app.data.local.dao.TransferDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BillLocketDatabase =
        Room.databaseBuilder(context, BillLocketDatabase::class.java, BillLocketDatabase.NAME)
            .addCallback(DatabaseInitializer.callback)
            .build()

    @Provides fun provideAccountDao(database: BillLocketDatabase): AccountDao = database.accountDao()

    @Provides fun provideCategoryDao(database: BillLocketDatabase): CategoryDao = database.categoryDao()

    @Provides fun provideTransactionDao(database: BillLocketDatabase): TransactionDao = database.transactionDao()

    @Provides fun provideTransferDao(database: BillLocketDatabase): TransferDao = database.transferDao()

    @Provides fun provideBudgetDao(database: BillLocketDatabase): BudgetDao = database.budgetDao()

    @Provides fun provideProjectionDao(database: BillLocketDatabase): ProjectionDao = database.projectionDao()
}
