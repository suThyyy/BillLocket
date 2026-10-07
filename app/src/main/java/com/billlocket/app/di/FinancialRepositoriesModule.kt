package com.billlocket.app.di

import com.billlocket.app.data.repository.*
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.IdGenerator
import com.billlocket.app.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FinancialRepositoriesModule {
    @Binds @Singleton abstract fun bindTransactions(implementation: TransactionRepositoryImpl): TransactionRepository
    @Binds @Singleton abstract fun bindAccounts(implementation: AccountRepositoryImpl): AccountRepository
    @Binds @Singleton abstract fun bindCategories(implementation: CategoryRepositoryImpl): CategoryRepository
    @Binds @Singleton abstract fun bindTransfers(implementation: TransferRepositoryImpl): TransferRepository
    @Binds @Singleton abstract fun bindBudgets(implementation: BudgetRepositoryImpl): BudgetRepository
    @Binds @Singleton abstract fun bindInsights(implementation: InsightsRepositoryImpl): InsightsRepository
    @Binds @Singleton abstract fun bindSettings(implementation: SettingsRepositoryImpl): SettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object RuntimeSourcesModule {
    @Provides @Singleton fun provideClock(): Clock = Clock(System::currentTimeMillis)
    @Provides @Singleton fun provideIdGenerator(): IdGenerator = IdGenerator { UUID.randomUUID().toString() }
}
