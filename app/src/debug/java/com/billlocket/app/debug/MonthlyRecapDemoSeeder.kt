package com.billlocket.app.debug

import android.content.Context
import androidx.room.withTransaction
import com.billlocket.app.data.local.entity.TransactionEntity
import com.billlocket.app.data.local.entity.TransactionImageEntity
import com.billlocket.app.data.local.entity.TransactionSearchEntity
import com.billlocket.app.domain.model.AccountRef
import com.billlocket.app.domain.model.CategoryRef
import com.billlocket.app.domain.model.CreateTransactionCommand
import com.billlocket.app.domain.model.DateRange
import com.billlocket.app.domain.model.GeoPoint
import com.billlocket.app.domain.model.LocationSource
import com.billlocket.app.domain.model.Money
import com.billlocket.app.domain.model.PhotoLocation
import com.billlocket.app.domain.model.TransactionType
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.BackupOptions
import com.billlocket.app.domain.port.MediaImageId
import com.billlocket.app.domain.port.MediaOperationId
import com.billlocket.app.domain.port.MediaStorePort
import com.billlocket.app.domain.repository.AccountRepository
import com.billlocket.app.domain.repository.CategoryRepository
import com.billlocket.app.domain.repository.SettingsRepository
import com.billlocket.app.domain.repository.TransactionRepository
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.usecase.CreateTransactionWithMedia
import com.billlocket.app.domain.usecase.FinalizeTransactionDeletion
import com.billlocket.app.domain.usecase.PreparedTransactionImage
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

class MonthlyRecapDemoSeeder(private val context: Context) {

    private val entryPoint: SeederEntryPoint by lazy {
        EntryPointAccessors.fromApplication(context.applicationContext, SeederEntryPoint::class.java)
    }

    private val transactionRepository: TransactionRepository get() = entryPoint.transactionRepository()
    private val database get() = entryPoint.database()
    private val backup get() = entryPoint.backup()
    private val accountRepository: AccountRepository get() = entryPoint.accountRepository()
    private val categoryRepository: CategoryRepository get() = entryPoint.categoryRepository()
    private val settingsRepository: SettingsRepository get() = entryPoint.settingsRepository()
    private val mediaStore: MediaStorePort get() = entryPoint.mediaStore()
    private val createTransaction: CreateTransactionWithMedia get() = entryPoint.createTransaction()
    private val finalizeTransactionDeletion: FinalizeTransactionDeletion get() = entryPoint.finalizeTransactionDeletion()
    private val clock: Clock get() = entryPoint.clock()

    private val prefs by lazy {
        context.getSharedPreferences("monthly_recap_seed_debug", Context.MODE_PRIVATE)
    }

    data class SeederResult(
        val transactionCount: Int,
        val imageCount: Int,
        val locationCount: Int,
        val totalIncomeMinor: Long,
        val totalExpenseMinor: Long,
        val topCategoryName: String,
        val mostActiveDay: String,
        val isWarningFewImages: Boolean,
    )

    fun getSeedDirectory(): File {
        val dir = context.getExternalFilesDir("monthly-recap-seed")
            ?: File(context.filesDir, "monthly-recap-seed")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun discoverInputImages(): List<File> {
        val dir = getSeedDirectory()
        val supportedExtensions = setOf("jpg", "jpeg", "png", "heic", "heif", "webp")
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in supportedExtensions && it.length() > 0 }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    suspend fun cleanupPreviousSeededData(onProgress: (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        onProgress("Đang kiểm tra và dọn dẹp dữ liệu demo cũ...")
        val septRange = DateRange(
            startEpochDay = LocalDate.of(2026, 9, 1).toEpochDay(),
            endInclusiveEpochDay = LocalDate.of(2026, 9, 30).toEpochDay(),
        )
        val octRange = DateRange(
            startEpochDay = LocalDate.of(2026, 10, 1).toEpochDay(),
            endInclusiveEpochDay = LocalDate.of(2026, 10, 31).toEpochDay(),
        )

        val demoTransactionsInPeriod = (
            transactionRepository.observeRecapTransactions(septRange).first() +
                transactionRepository.observeRecapTransactions(octRange).first()
            ).distinctBy { it.id }
        val recordedIds = prefs.getStringSet("seeded_ids", emptySet()) ?: emptySet()

        val demoTransactions = demoTransactionsInPeriod.filter {
            MonthlyRecapSeedPlanner.isDemoTransaction(it.note) || recordedIds.contains(it.id.value)
        }

        var deletedCount = 0
        val now = clock.nowEpochMillis()
        demoTransactions.forEach { tx ->
            onProgress("Đang xóa dữ liệu cũ: ${tx.note?.take(20) ?: tx.id.value}...")
            val markResult = transactionRepository.markDeleted(tx.id, now)
            if (markResult is Outcome.Success) {
                finalizeTransactionDeletion(tx.id)
                deletedCount++
            }
        }

        prefs.edit().remove("seeded_ids").apply()
        deletedCount
    }

    suspend fun seed(
        onProgress: (message: String, progress: Float) -> Unit,
    ): SeederResult = withContext(Dispatchers.IO) {
        val images = discoverInputImages()
        if (images.size != 49) {
            throw IllegalStateException(
                "Bộ ảnh demo phải có đúng 49 ảnh trong:\n${getSeedDirectory().absolutePath}\n" +
                    "Hiện có ${images.size} ảnh.",
            )
        }

        val plan = MonthlyRecapSeedPlanner.plan(images.map { it.name })
        val imageMap = images.associateBy { it.name }

        onProgress("Đang tạo và kiểm tra backup trước khi thay thế...", 0.02f)
        val backupDirectory = context.getExternalFilesDir("monthly-recap-backups")
            ?: throw IllegalStateException("Không tạo được thư mục backup trên thiết bị.")
        backupDirectory.mkdirs()
        val backupFile = File(backupDirectory, "before-demo-${System.currentTimeMillis()}.zip")
        backupFile.outputStream().use { output ->
            when (val result = backup.export(BackupOptions(), output)) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> throw IllegalStateException("Backup thất bại: ${result.reason}")
            }
        }
        backupFile.inputStream().use {
            when (val result = backup.inspect(it)) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> throw IllegalStateException("Kiểm tra backup thất bại: ${result.reason}")
            }
        }

        // Resolve active account
        onProgress("Đang thiết lập tài khoản và danh mục...", 0.1f)
        val accounts = accountRepository.observeAll().first()
        val activeAccount = accounts.firstOrNull { !it.isArchived }
            ?: throw IllegalStateException("Không tìm thấy tài khoản hợp lệ trong ứng dụng.")
        val configuredCurrency = settingsRepository.settings.first().currency
        val accountRef = AccountRef(activeAccount.id, activeAccount.type, true, configuredCurrency)

        // Resolve categories
        val expenseCategories = categoryRepository.observe(TransactionType.EXPENSE).first()
        val incomeCategories = categoryRepository.observe(TransactionType.INCOME).first()

        fun resolveCategory(type: TransactionType, preferred: String, fallback: String): CategoryRef {
            val pool = if (type == TransactionType.INCOME) incomeCategories else expenseCategories
            val found = pool.firstOrNull { it.name.equals(preferred, ignoreCase = true) }
                ?: pool.firstOrNull { it.name.equals(fallback, ignoreCase = true) }
                ?: pool.firstOrNull { !it.isArchived }
                ?: throw IllegalStateException("Không tìm thấy danh mục cho loại giao dịch $type")
            return CategoryRef(found.id, type, true)
        }

        data class ImportedRow(
            val transaction: TransactionEntity,
            val images: List<TransactionImageEntity>,
            val staged: List<com.billlocket.app.domain.port.StagedMedia>,
        )

        val importedRows = mutableListOf<ImportedRow>()
        val promotedUris = mutableListOf<String>()

        val totalTransactions = plan.transactions.size
        plan.transactions.forEachIndexed { index, plannedTx ->
            val progressFraction = 0.15f + (0.80f * (index.toFloat() / totalTransactions))
            onProgress(
                "Đang tạo giao dịch (${index + 1}/$totalTransactions): ${plannedTx.localDate}...",
                progressFraction,
            )

            val normalizedImages = mutableListOf<com.billlocket.app.domain.port.NormalizedMedia>()
            val stagedImages = mutableListOf<com.billlocket.app.domain.port.StagedMedia>()
            for (photo in plannedTx.photos) {
                val file = imageMap[photo.fileName] ?: continue
                val operationId = MediaOperationId(UUID.randomUUID().toString())
                val imageId = MediaImageId(UUID.randomUUID().toString())

                val declaredMime = when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "heic", "heif" -> "image/heic"
                    else -> "image/jpeg"
                }

                val stagedOutcome = file.inputStream().use { stream ->
                    mediaStore.stageImport(operationId, imageId, declaredMime, stream)
                }
                val staged = when (stagedOutcome) {
                    is Outcome.Success -> stagedOutcome.value
                    is Outcome.Failure -> throw IllegalStateException("Lỗi import ảnh ${photo.fileName}: ${stagedOutcome.reason}")
                }

                val normalizedOutcome = mediaStore.normalize(staged)
                val normalized = when (normalizedOutcome) {
                    is Outcome.Success -> normalizedOutcome.value
                    is Outcome.Failure -> {
                        mediaStore.discardStaged(staged)
                        throw IllegalStateException("Lỗi chuẩn hóa ảnh ${photo.fileName}: ${normalizedOutcome.reason}")
                    }
                }
                normalizedImages += normalized
                stagedImages += staged
            }

            val categoryRef = resolveCategory(plannedTx.type, plannedTx.preferredCategoryName, plannedTx.fallbackCategoryName)
            val transactionId = UUID.randomUUID().toString()
            val now = clock.nowEpochMillis()
            val transaction = TransactionEntity(
                id = transactionId,
                type = plannedTx.type.name,
                amountMinor = plannedTx.amountMinor,
                currencyCode = configuredCurrency.value,
                categoryId = categoryRef.id.value,
                accountId = accountRef.id.value,
                note = plannedTx.note,
                occurredAt = plannedTx.occurred.epochMillis,
                localDateEpochDay = plannedTx.occurred.localDateEpochDay,
                zoneId = plannedTx.occurred.zoneId,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
            val imageEntities = normalizedImages.mapIndexed { position, normalized ->
                val photo = plannedTx.photos[position]
                TransactionImageEntity(
                    id = normalized.staged.imageId.value,
                    transactionId = transactionId,
                    contentUri = normalized.canonicalUri,
                    position = position,
                    mimeType = normalized.mimeType,
                    widthPx = normalized.widthPx,
                    heightPx = normalized.heightPx,
                    sizeBytes = normalized.sizeBytes,
                    capturedAt = normalized.capturedAtEpochMillis,
                    latitude = photo.location.latitude,
                    longitude = photo.location.longitude,
                    accuracyMeters = 15.0,
                    placeName = photo.location.name,
                    locationSource = LocationSource.MANUAL.name,
                    locationConfirmedAt = plannedTx.occurred.epochMillis,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            normalizedImages.forEach { normalized ->
                when (val promoted = mediaStore.promote(normalized)) {
                    is Outcome.Success -> promotedUris += promoted.value
                    is Outcome.Failure -> throw IllegalStateException("Lỗi lưu ảnh: ${promoted.reason}")
                }
            }
            importedRows += ImportedRow(
                transaction,
                imageEntities,
                stagedImages,
            )
        }

        try {
            database.withTransaction {
                database.transactionDao().deleteAllSearch()
                database.transactionDao().deleteAllImages()
                database.transactionDao().deleteAllTransactions()
                database.transferDao().deleteAll()
                importedRows.forEach { row ->
                    database.transactionDao().insertTransaction(row.transaction)
                    database.transactionDao().insertImages(row.images)
                    database.transactionDao().insertSearch(TransactionSearchEntity(row.transaction.id, row.transaction.note.orEmpty()))
                }
            }
            importedRows.flatMap { it.staged }.forEach { mediaStore.markCommitted(it) }
            mediaStore.reconcile(clock.nowEpochMillis())
        } catch (failure: Throwable) {
            mediaStore.deleteOwned(promotedUris)
            importedRows.flatMap { it.staged }.forEach { mediaStore.discardStaged(it) }
            throw failure
        }

        onProgress("Hoàn thành khởi tạo dữ liệu!", 1.0f)

        SeederResult(
            transactionCount = importedRows.size,
            imageCount = importedRows.sumOf { it.images.size },
            locationCount = importedRows.sumOf { it.images.count { image -> image.latitude != null } },
            totalIncomeMinor = plan.totalIncomeMinor,
            totalExpenseMinor = plan.totalExpenseMinor,
            topCategoryName = "Ăn uống",
            mostActiveDay = "20/09/2026 (${plan.mostActiveDateTransactionCount} giao dịch)",
            isWarningFewImages = plan.isWarningFewImages,
        )
    }
}
