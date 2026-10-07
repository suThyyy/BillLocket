package com.billlocket.app.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.billlocket.app.data.local.BillLocketDatabase
import com.billlocket.app.data.local.DatabaseInitializer
import com.billlocket.app.data.local.entity.TransactionEntity
import com.billlocket.app.data.local.entity.TransactionImageEntity
import com.billlocket.app.data.local.entity.TransactionSearchEntity
import com.billlocket.app.domain.model.OwnedMediaReference
import com.billlocket.app.domain.port.BackupOptions
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.WidgetUpdatePort
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.media.AndroidMediaStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortableBackupRoundTripTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: BillLocketDatabase
    private lateinit var backup: AndroidPortableBackup
    private val imageId = "30000000-0000-4000-8000-000000000001"
    private val transactionId = "40000000-0000-4000-8000-000000000001"

    @Before fun setup() {
        File(context.filesDir, "media/images").deleteRecursively()
        database = Room.inMemoryDatabaseBuilder(context, BillLocketDatabase::class.java).allowMainThreadQueries().addCallback(DatabaseInitializer.callback).build()
        database.openHelper.writableDatabase
        val media = AndroidMediaStore(context, database.transactionDao(), Clock { 2_000_000_000_000 })
        val preferences = context.getSharedPreferences("backup-test-${System.nanoTime()}", Context.MODE_PRIVATE)
        backup = AndroidPortableBackup(context, database, preferences, media, WidgetUpdatePort { Outcome.Success(Unit) })
    }
    @After fun close() { database.close(); File(context.filesDir, "media/images").deleteRecursively() }

    @Test fun representativeArchiveRoundTripsDataImageAndLocation() = runBlocking {
        val uri = OwnedMediaReference.uri(imageId)
        database.transactionDao().create(
            TransactionEntity(transactionId, "EXPENSE", 125_000, "VND", DatabaseInitializer.FOOD_CATEGORY_ID, DatabaseInitializer.INITIAL_ACCOUNT_ID, "Bữa trưa", 1_700_000_000_000, 19_675, "Asia/Ho_Chi_Minh", 1, 1, null),
            listOf(TransactionImageEntity(imageId, transactionId, uri, 0, "image/jpeg", 20, 10, 4, null, 10.77, 106.69, null, "Quận 1", "MANUAL", 1, 1, 1)),
            TransactionSearchEntity(transactionId, "Bữa trưa"),
        )
        val finalFile = File(context.filesDir, "media/images/$imageId.jpg").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val output = ByteArrayOutputStream()

        assertTrue(backup.export(BackupOptions(), output) is Outcome.Success)
        database.transactionDao().hardDelete(transactionId)
        finalFile.delete()
        assertTrue(backup.importReplace(ByteArrayInputStream(output.toByteArray())) is Outcome.Success)

        val restored = database.transactionDao().detail(transactionId)
        assertEquals(125_000, restored?.transaction?.amountMinor)
        assertEquals("Quận 1", restored?.images?.single()?.placeName)
        assertTrue(finalFile.isFile)
    }

    @Test fun unknownVersionAndTraversalArchivesAreRejected() = runBlocking {
        assertTrue(backup.inspect(ByteArrayInputStream(archive(2, emptyMap()))) is Outcome.Failure)
        assertTrue(backup.inspect(ByteArrayInputStream(archive(1, mapOf("../escape" to byteArrayOf(1))))) is Outcome.Failure)
    }

    @Test fun corruptedChecksumIsRejectedWithoutChangingData() = runBlocking {
        val invalid = archive(1, emptyMap(), checksum = "0".repeat(64))
        assertTrue(backup.inspect(ByteArrayInputStream(invalid)) is Outcome.Failure)
        assertEquals(1, database.accountDao().listAll().size)
    }

    private fun archive(version: Int, extras: Map<String, ByteArray>, checksum: String = "0".repeat(64)): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val manifest = """{"format":"BillLocket","formatVersion":$version,"accountCount":0,"transactionCount":0,"imageCount":0,"includesImages":false,"includesLocations":false,"currency":"VND"}""".toByteArray()
            val data = "{}".toByteArray()
            listOf("manifest.json" to manifest, "data.json" to data, "checksums.sha256" to "$checksum  data.json\n".toByteArray()).forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            extras.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
        return output.toByteArray()
    }
}
