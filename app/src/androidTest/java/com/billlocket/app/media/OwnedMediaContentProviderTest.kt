package com.billlocket.app.media

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnedMediaContentProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun providerReadsFinalAndDraftMediaButRejectsWriteMode() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val final = File(context.filesDir, "media/images/provider-test.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }
        val draft = File(context.noBackupFilesDir, "media-staging/provider-operation/provider-image.tmp").apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }
        try {
            val finalUri = Uri.parse("content://com.billlocket.app.media/images/provider-test")
            val draftUri = Uri.parse("content://com.billlocket.app.media/drafts/provider-operation/provider-image")
            assertArrayEquals(bytes, context.contentResolver.openInputStream(finalUri)!!.use { it.readBytes() })
            assertArrayEquals(bytes, context.contentResolver.openInputStream(draftUri)!!.use { it.readBytes() })
            assertThrows(FileNotFoundException::class.java) {
                context.contentResolver.openFileDescriptor(finalUri, "w")
            }
        } finally {
            final.delete()
            draft.delete()
            draft.parentFile?.delete()
        }
    }
}
