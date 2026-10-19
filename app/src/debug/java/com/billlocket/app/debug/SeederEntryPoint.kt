package com.billlocket.app.debug

import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.BackupPort
import com.billlocket.app.domain.port.MediaStorePort
import com.billlocket.app.domain.repository.AccountRepository
import com.billlocket.app.domain.repository.CategoryRepository
import com.billlocket.app.domain.repository.SettingsRepository
import com.billlocket.app.domain.repository.TransactionRepository
import com.billlocket.app.domain.usecase.CreateTransactionWithMedia
import com.billlocket.app.domain.usecase.FinalizeTransactionDeletion
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SeederEntryPoint {
    fun database(): com.billlocket.app.data.local.BillLocketDatabase
    fun backup(): BackupPort
    fun transactionRepository(): TransactionRepository
    fun accountRepository(): AccountRepository
    fun categoryRepository(): CategoryRepository
    fun settingsRepository(): SettingsRepository
    fun mediaStore(): MediaStorePort
    fun createTransaction(): CreateTransactionWithMedia
    fun finalizeTransactionDeletion(): FinalizeTransactionDeletion
    fun clock(): Clock
}
