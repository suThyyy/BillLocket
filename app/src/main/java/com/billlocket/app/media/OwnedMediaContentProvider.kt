package com.billlocket.app.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.billlocket.app.domain.model.OwnedMediaReference
import java.io.File
import java.io.FileNotFoundException

class OwnedMediaContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Media is read-only")
        val file = resolve(uri)
        if (!file.isFile) throw FileNotFoundException("Media is missing")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = resolve(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Media is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Media is read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Media is read-only")

    private fun resolve(uri: Uri): File {
        if (uri.authority != AUTHORITY) throw FileNotFoundException("Invalid authority")
        val appContext = requireNotNull(context)
        val parts = uri.pathSegments
        return when {
            parts.size == 2 && parts[0] == "images" -> {
                val id = OwnedMediaReference.requireId(parts[1])
                safeChild(File(appContext.filesDir, "media/images"), "$id.jpg")
            }
            parts.size == 3 && parts[0] == "drafts" -> {
                val operation = OwnedMediaReference.requireId(parts[1])
                val image = OwnedMediaReference.requireId(parts[2])
                safeChild(safeChild(File(appContext.noBackupFilesDir, "media-staging"), operation), "$image.tmp")
            }
            else -> throw FileNotFoundException("Invalid media URI")
        }
    }

    private fun safeChild(root: File, name: String): File {
        if (name.isBlank() || '/' in name || '\\' in name || name == "." || name == "..") {
            throw FileNotFoundException("Invalid media path")
        }
        val base = root.toPath().toAbsolutePath().normalize()
        val resolved = base.resolve(name).normalize()
        if (resolved.parent != base) throw FileNotFoundException("Invalid media path")
        return resolved.toFile()
    }

    private companion object {
        const val AUTHORITY = "com.billlocket.app.media"
    }
}
