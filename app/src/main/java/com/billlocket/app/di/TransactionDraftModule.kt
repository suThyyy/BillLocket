package com.billlocket.app.di

import com.billlocket.app.feature.transaction.draft.SharedPreferencesTransactionDraftStore
import com.billlocket.app.feature.transaction.draft.TransactionDraftStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TransactionDraftModule {
    @Binds
    @Singleton
    abstract fun bindTransactionDraftStore(
        implementation: SharedPreferencesTransactionDraftStore,
    ): TransactionDraftStore
}
