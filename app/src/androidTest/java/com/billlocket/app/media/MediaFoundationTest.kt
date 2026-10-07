package com.billlocket.app.media

import android.content.Context
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.billlocket.app.data.local.BillLocketDatabase
import com.billlocket.app.data.local.DatabaseInitializer
import com.billlocket.app.data.local.entity.TransactionEntity
import com.billlocket.app.data.local.entity.TransactionImageEntity
import com.billlocket.app.data.local.entity.TransactionSearchEntity
import com.billlocket.app.domain.model.OwnedMediaReference
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.MediaImageId
import com.billlocket.app.domain.port.MediaOperationId
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.model.CurrencyCode
import com.billlocket.app.domain.model.Money
import com.billlocket.app.feature.recap.AndroidMonthlyRecapExporter
import com.billlocket.app.feature.recap.ShareableMonthlyRecap
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaFoundationTest {
    private lateinit var context: Context
    private lateinit var database: BillLocketDatabase
    private lateinit var store: AndroidMediaStore
    private val clock = TestClock(NOW)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearOwnedTestRoots()
        database = Room.inMemoryDatabaseBuilder(context, BillLocketDatabase::class.java)
            .allowMainThreadQueries().addCallback(DatabaseInitializer.callback).build()
        database.openHelper.writableDatabase
        store = AndroidMediaStore(context, database.transactionDao(), clock)
    }

    @After fun tearDown() { database.close(); clearOwnedTestRoots() }

    @Test fun captureTargetNormalizesOrientationStripsExifAndSharesReadOnly() = runBlocking {
        val staged = success(store.stageCapture(MediaOperationId("capture-op"), MediaImageId("capture-image")))
        val output = store.captureOutputFile(staged)
        writeJpeg(output, 80, 40)
        ExifInterface(output).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_MAKE, "private-device")
            setLatLong(10.77, 106.69)
            saveAttributes()
        }
        val normalized = success(store.normalize(staged))
        assertEquals(40, normalized.widthPx)
        assertEquals(80, normalized.heightPx)
        assertNotNull(normalized.locationCandidate)
        val canonical = success(store.promote(normalized))
        val finalExif = success(store.openRead(canonical)).use(::ExifInterface)
        assertNull(finalExif.latLong)
        assertNull(finalExif.getAttribute(ExifInterface.TAG_MAKE))
        val shareUri = success(store.prepareShare(canonical))
        val intent = MediaShareIntentFactory.create(shareUri)
        assertEquals("image/jpeg", intent.type)
        assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertTrue(context.contentResolver.openInputStream(android.net.Uri.parse(shareUri))!!.use { it.read() >= 0 })
        assertTrue(store.markCommitted(staged) is Outcome.Success)
    }

    @Test fun pickerCopyOwnsInputAndRejectsUnsafeIdentifiers() = runBlocking {
        val bytes = jpegBytes(32, 24)
        val staged = success(store.stageImport(MediaOperationId("picker-op"), MediaImageId("picker-image"), "image/jpeg", bytes.inputStream()))
        assertEquals(bytes.size.toLong(), store.captureOutputFile(staged).length())
        assertTrue(store.stageCapture(MediaOperationId("../escape"), MediaImageId("safe")) is Outcome.Failure)
        assertTrue(store.stageImport(MediaOperationId("safe"), MediaImageId("unsafe/path"), "image/jpeg", bytes.inputStream()) is Outcome.Failure)
    }

    @Test fun reconciliationRemovesOldOrphanButNeverReferencedMedia() = runBlocking {
        val orphan = createPromoted("orphan-op", "orphan-image")
        ageOperation("orphan-op", "orphan-image")
        assertTrue(success(store.reconcile(NOW)).removedOrphanFiles >= 1)
        assertFalse(store.exists(orphan))

        val referenced = createPromoted("reference-op", "reference-image")
        insertReferencedImage(referenced, "reference-image")
        ageOperation("reference-op", "reference-image")
        assertTrue(store.deleteOwned(listOf(referenced)) is Outcome.Failure)
        assertTrue(success(store.reconcile(NOW)).protectedReferencedFiles >= 1)
        assertTrue(store.exists(referenced))
    }

    @Test fun missingReferencedFileIsReportedWithoutDeletingTransaction() = runBlocking {
        insertReferencedImage(OwnedMediaReference.uri("missing-image"), "missing-image")
        assertEquals(1, success(store.reconcile(NOW)).missingReferencedFiles)
        assertNotNull(database.transactionDao().detail(TRANSACTION_ID))
    }

    @Test fun manifestDisablesPlatformBackupAndKeepsShareProviderPrivate() {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(0, appInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
        val provider = requireNotNull(context.packageManager.resolveContentProvider("com.billlocket.app.fileprovider", 0))
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        assertThrows(IllegalArgumentException::class.java) {
            MediaShareIntentFactory.create(OwnedMediaReference.uri("not-a-share-uri"))
        }
    }

    @Test fun monthlyRecapExportsReadableJpegThroughReadOnlyShareProvider() = runBlocking {
        val currency = CurrencyCode.of("VND")
        val recap = ShareableMonthlyRecap(
            month = YearMonth.of(2026, 10),
            income = Money(1_000_000, currency),
            expense = Money(250_000, currency),
            net = Money(750_000, currency),
            transactionCount = 3,
            topCategoryName = "Ăn uống",
            photos = emptyList(),
        )

        val shareUri = AndroidMonthlyRecapExporter(context).export(recap)
        val bitmap = context.contentResolver.openInputStream(android.net.Uri.parse(shareUri))
            ?.use { input -> android.graphics.BitmapFactory.decodeStream(input) }
        assertNotNull(bitmap)
        assertEquals(1080, bitmap!!.width)
        assertEquals(1350, bitmap.height)
        bitmap.recycle()

        val intent = MediaShareIntentFactory.create(shareUri)
        assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    private suspend fun createPromoted(operation: String, image: String): String {
        val staged = success(store.stageImport(MediaOperationId(operation), MediaImageId(image), "image/jpeg", jpegBytes(40, 30).inputStream()))
        return success(store.promote(success(store.normalize(staged))))
    }

    private suspend fun insertReferencedImage(uri: String, imageId: String) {
        val transaction = TransactionEntity(
            TRANSACTION_ID, "EXPENSE", 10_000, "VND", DatabaseInitializer.FOOD_CATEGORY_ID,
            DatabaseInitializer.INITIAL_ACCOUNT_ID, null, NOW, 20_000, "Asia/Ho_Chi_Minh", NOW, NOW, null,
        )
        val image = TransactionImageEntity(
            imageId, TRANSACTION_ID, uri, 0, "image/jpeg", 40, 30, 100, null,
            null, null, null, null, null, null, NOW, NOW,
        )
        database.transactionDao().create(transaction, listOf(image), TransactionSearchEntity(TRANSACTION_ID, ""))
    }

    private fun ageOperation(operation: String, image: String) {
        val old = NOW - 25L * 60 * 60 * 1000
        File(context.filesDir, "media/images/$image.jpg").setLastModified(old)
        File(context.noBackupFilesDir, "media-staging/$operation").setLastModified(old)
        File(context.noBackupFilesDir, "recovery/$operation").setLastModified(old)
    }

    private fun writeJpeg(file: File, width: Int, height: Int) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
            bitmap.recycle()
        }
    }

    private fun jpegBytes(width: Int, height: Int): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        bitmap.recycle()
        output.toByteArray()
    }

    private fun clearOwnedTestRoots() {
        listOf(
            File(context.filesDir, "media"), File(context.noBackupFilesDir, "media-staging"),
            File(context.noBackupFilesDir, "media-trash"), File(context.noBackupFilesDir, "recovery"),
            File(context.cacheDir, "thumbnails"), File(context.cacheDir, "share"),
        ).forEach { it.deleteRecursively() }
    }

    private fun <T> success(outcome: Outcome<T>): T = (outcome as Outcome.Success).value
    private class TestClock(private val value: Long) : Clock { override fun nowEpochMillis() = value }

    private companion object {
        const val NOW = 1_730_000_000_000L
        const val TRANSACTION_ID = "media-test-transaction"
    }
}
