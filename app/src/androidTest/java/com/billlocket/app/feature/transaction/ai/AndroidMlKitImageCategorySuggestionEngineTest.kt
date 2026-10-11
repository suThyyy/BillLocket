package com.billlocket.app.feature.transaction.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMlKitImageCategorySuggestionEngineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val engine = AndroidMlKitImageCategorySuggestionEngine(context, Dispatchers.IO)
    private val testDir by lazy { File(context.cacheDir, "mlkit_test_images").apply { mkdirs() } }

    @Before
    fun setup() {
        testDir.listFiles()?.forEach { it.delete() }
    }

    private fun createTestJpeg(width: Int, height: Int, filename: String): File {
        val file = File(testDir, filename)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        return file
    }

    @Test
    fun validGeneratedJpegCompletesWithoutCrashing() = runBlocking {
        val file = createTestJpeg(200, 200, "valid_test.jpg")
        val uri = Uri.fromFile(file).toString()

        val suggestion = engine.suggest(uri)
        // Synthetic single-color bitmap does not crash and completes safely
    }

    @Test
    fun corruptImageFileReturnsNullWithoutCrash() = runBlocking {
        val file = File(testDir, "corrupt.jpg")
        file.writeBytes(byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66))

        val result = engine.suggest(Uri.fromFile(file).toString())
        assertNull(result)
    }

    @Test
    fun missingUriOrFileReturnsNullWithoutCrash() = runBlocking {
        val result = engine.suggest("content://nonexistent.authority/images/missing.jpg")
        assertNull(result)

        val fileResult = engine.suggest(Uri.fromFile(File(testDir, "non_existent.jpg")).toString())
        assertNull(fileResult)

        val blankResult = engine.suggest("")
        assertNull(blankResult)
    }

    @Test
    fun oversizedImageSamplingCalculationBoundsDimensions() {
        assertEquals(4, calculateInSampleSize(4000, 3000, 1024))
        assertEquals(4, calculateInSampleSize(3000, 4000, 1024))
        assertEquals(2, calculateInSampleSize(2048, 1024, 1024))
        assertEquals(1, calculateInSampleSize(1024, 1024, 1024))
        assertEquals(1, calculateInSampleSize(800, 600, 1024))
        assertEquals(1, calculateInSampleSize(0, 0, 1024))
    }

    @Test
    fun cancellationSmokeTestCompletesSafelyWithoutLeakingCrash() = runBlocking {
        val file = createTestJpeg(300, 300, "cancel_test.jpg")
        val uri = Uri.fromFile(file).toString()

        val job = launch(Dispatchers.Default) {
            engine.suggest(uri)
        }
        job.cancelAndJoin()
    }
}
