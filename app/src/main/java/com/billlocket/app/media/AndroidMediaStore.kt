package com.billlocket.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.JsonReader
import android.util.JsonWriter
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.billlocket.app.data.local.dao.TransactionDao
import com.billlocket.app.domain.model.MediaCompressionPolicy
import com.billlocket.app.domain.model.MediaJournalEntry
import com.billlocket.app.domain.model.MediaJournalState
import com.billlocket.app.domain.model.OwnedMediaReference
import com.billlocket.app.domain.port.*
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidMediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
    private val clock: Clock,
) : MediaStorePort {
    private val paths = MediaPaths(context)

    override suspend fun stageCapture(operationId: MediaOperationId, imageId: MediaImageId) =
        mediaOutcome("stage_capture") {
            val staged = validated(operationId, imageId)
            paths.stagingFile(staged).apply {
                parentFile?.mkdirs()
                if (exists()) check(delete())
                check(createNewFile())
            }
            staged
        }

    /** Transient CameraX output only; callers must never persist this path. */
    fun captureOutputFile(staged: StagedMedia): File = paths.stagingFile(validated(staged.operationId, staged.imageId))

    override suspend fun stageImport(
        operationId: MediaOperationId,
        imageId: MediaImageId,
        declaredMimeType: String?,
        input: InputStream,
    ) = mediaOutcome("stage_import") {
        declaredMimeType?.let(::requireSupportedMime)
        val staged = validated(operationId, imageId)
        val target = paths.stagingFile(staged)
        target.parentFile?.mkdirs()
        input.use { source ->
            BufferedInputStream(source).use { buffered ->
                BufferedOutputStream(FileOutputStream(target, false)).use { copyBounded(buffered, it) }
            }
        }
        staged
    }

    override suspend fun inspectDraft(staged: StagedMedia) =
        mediaOutcome("inspect_draft") { inspect(paths.stagingFile(validated(staged.operationId, staged.imageId))) }

    override suspend fun normalize(staged: StagedMedia) = mediaOutcome("normalize_media") {
        val safe = validated(staged.operationId, staged.imageId)
        val source = paths.stagingFile(safe)
        val inspection = inspect(source)
        check(source.length() in 1..MAX_SOURCE_BYTES)
        val decoded = decodeBounded(source, inspection.widthPx, inspection.heightPx)
        val oriented = orient(decoded, inspection.orientationDegrees, inspection.mirrored)
        if (oriented !== decoded) decoded.recycle()
        var outputBitmap = scaleLongEdge(oriented, MediaCompressionPolicy.PRIMARY_LONG_EDGE)
        if (outputBitmap !== oriented) oriented.recycle()
        val output = File(source.parentFile, "${safe.imageId.value}.normalized")
        compress(outputBitmap, output, MediaCompressionPolicy.PRIMARY_QUALITY)
        if (output.length() > MediaCompressionPolicy.MAX_OUTPUT_BYTES) {
            compress(outputBitmap, output, MediaCompressionPolicy.FALLBACK_QUALITY)
        }
        if (output.length() > MediaCompressionPolicy.MAX_OUTPUT_BYTES) {
            val reduced = scaleLongEdge(outputBitmap, MediaCompressionPolicy.FALLBACK_LONG_EDGE)
            compress(reduced, output, MediaCompressionPolicy.FALLBACK_QUALITY)
            if (reduced !== outputBitmap) {
                outputBitmap.recycle()
                outputBitmap = reduced
            }
        }
        val width = outputBitmap.width
        val height = outputBitmap.height
        outputBitmap.recycle()
        check(output.length() in 1..MediaCompressionPolicy.MAX_OUTPUT_BYTES)
        moveReplacing(output, source)
        NormalizedMedia(
            staged = safe,
            canonicalUri = OwnedMediaReference.uri(safe.imageId.value),
            widthPx = width,
            heightPx = height,
            mimeType = JPEG_MIME,
            sizeBytes = source.length(),
            checksumSha256 = sha256(source),
            capturedAtEpochMillis = inspection.capturedAtEpochMillis,
            locationCandidate = inspection.locationCandidate,
        )
    }

    override fun previewUri(staged: StagedMedia): String {
        val safe = validated(staged.operationId, staged.imageId)
        return "content://$MEDIA_AUTHORITY/drafts/${safe.operationId.value}/${safe.imageId.value}"
    }

    override suspend fun discardStaged(staged: StagedMedia) = mediaOutcome("discard_staged") {
        val safe = validated(staged.operationId, staged.imageId)
        paths.removeOperationArtifacts(safe.operationId.value)
    }

    override suspend fun promote(media: NormalizedMedia) = mediaOutcome("promote_media") {
        val staged = validated(media.staged.operationId, media.staged.imageId)
        check(media.canonicalUri == OwnedMediaReference.uri(staged.imageId.value))
        val source = paths.stagingFile(staged)
        check(source.isFile && sha256(source) == media.checksumSha256)
        var journal = MediaJournalEntry(
            staged.operationId.value,
            staged.imageId.value,
            media.canonicalUri,
            media.checksumSha256,
            MediaJournalState.PREPARED,
            clock.nowEpochMillis(),
        )
        paths.writeJournal(journal)
        val destination = paths.finalFile(staged.imageId.value)
        check(!destination.exists()) { "Final media already exists" }
        moveReplacing(source, destination)
        journal = journal.transitionTo(MediaJournalState.FILE_READY, clock.nowEpochMillis())
        paths.writeJournal(journal)
        media.canonicalUri
    }

    override suspend fun markCommitted(staged: StagedMedia) = mediaOutcome("commit_media") {
        val safe = validated(staged.operationId, staged.imageId)
        val journal = requireNotNull(paths.readJournal(safe.operationId.value))
        paths.writeJournal(journal.transitionTo(MediaJournalState.COMMITTED, clock.nowEpochMillis()))
        paths.removeOperationArtifacts(safe.operationId.value)
    }

    override suspend fun exists(canonicalUri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { paths.finalFile(OwnedMediaReference.imageId(canonicalUri)).isFile }.getOrDefault(false)
    }

    override suspend fun openRead(canonicalUri: String) = mediaOutcome<InputStream>("open_media") {
        val file = paths.finalFile(OwnedMediaReference.imageId(canonicalUri))
        check(file.isFile)
        FileInputStream(file)
    }

    override suspend fun moveToTrash(canonicalUri: String, operationId: MediaOperationId) =
        mediaOutcome("trash_media") { moveToTrashChecked(canonicalUri, operationId) }

    override suspend fun deleteOwned(uris: List<String>) = mediaOutcome("delete_media") {
        val unique = uris.distinct()
        unique.forEach(OwnedMediaReference::imageId)
        check(unique.none { transactionDao.imageReferenceCount(it) > 0 }) { "Referenced media cannot be deleted" }
        val operation = MediaOperationId(UUID.randomUUID().toString())
        unique.forEach { moveToTrashChecked(it, operation) }
        paths.deleteTree(paths.trashOperation(operation.value))
    }

    override suspend fun prepareShare(canonicalUri: String) = mediaOutcome("share_media") {
        val imageId = OwnedMediaReference.imageId(canonicalUri)
        val source = paths.finalFile(imageId)
        check(source.isFile)
        val target = paths.shareFile("$imageId-${UUID.randomUUID()}.jpg")
        target.parentFile?.mkdirs()
        source.inputStream().use { input -> target.outputStream().use(input::copyTo) }
        FileProvider.getUriForFile(context, SHARE_AUTHORITY, target).toString()
    }

    override suspend fun reconcile(nowEpochMillis: Long) = mediaOutcome("reconcile_media") {
        val referenced = transactionDao.allImageUris().toSet()
        var stagingRemoved = 0
        var orphansRemoved = 0
        var temporaryRemoved = 0
        var protected = 0
        paths.operationDirectories().forEach { directory ->
            if (Files.isSymbolicLink(directory.toPath())) return@forEach
            val journal = paths.readJournal(directory.name)
            val old = nowEpochMillis - directory.lastModified() >= SAFETY_WINDOW_MILLIS
            when {
                journal?.state == MediaJournalState.COMMITTED -> {
                    paths.removeOperationArtifacts(directory.name)
                    stagingRemoved++
                }
                journal?.state == MediaJournalState.FILE_READY && journal.canonicalUri in referenced -> {
                    paths.removeOperationArtifacts(directory.name)
                    stagingRemoved++
                }
                old -> {
                    journal?.canonicalUri?.let { uri ->
                        if (uri in referenced) protected++
                        else {
                            val final = paths.finalFile(OwnedMediaReference.imageId(uri))
                            if (final.exists() && final.delete()) orphansRemoved++
                        }
                    }
                    paths.removeOperationArtifacts(directory.name)
                    stagingRemoved++
                }
            }
        }
        paths.finalFiles().forEach { file ->
            if (Files.isSymbolicLink(file.toPath())) return@forEach
            val uri = OwnedMediaReference.uri(file.nameWithoutExtension)
            if (uri in referenced) protected++
            else if (nowEpochMillis - file.lastModified() >= SAFETY_WINDOW_MILLIS && file.delete()) orphansRemoved++
        }
        paths.expiredTemporaryEntries(nowEpochMillis, SAFETY_WINDOW_MILLIS).forEach {
            paths.deleteTree(it)
            temporaryRemoved++
        }
        val missing = referenced.count { !paths.finalFile(OwnedMediaReference.imageId(it)).isFile }
        MediaReconciliationReport(stagingRemoved, orphansRemoved, temporaryRemoved, missing, protected)
    }

    override suspend fun measureStorage() = mediaOutcome("measure_media") {
        MediaStorageUsage(
            paths.sizeOf(paths.finalRoot),
            paths.sizeOf(paths.stagingRoot) + paths.sizeOf(paths.trashRoot) + paths.sizeOf(paths.recoveryRoot),
            paths.sizeOf(paths.thumbnailRoot) + paths.sizeOf(paths.shareRoot) + paths.sizeOf(File(context.cacheDir, "image_cache")),
        )
    }

    private suspend fun moveToTrashChecked(uri: String, operation: MediaOperationId) {
        val imageId = OwnedMediaReference.imageId(uri)
        OwnedMediaReference.requireId(operation.value)
        check(transactionDao.imageReferenceCount(uri) == 0) { "Referenced media cannot be trashed" }
        val source = paths.finalFile(imageId)
        if (source.exists()) moveReplacing(source, paths.trashFile(operation.value, imageId))
    }

    private fun validated(operation: MediaOperationId, image: MediaImageId) = StagedMedia(
        MediaOperationId(OwnedMediaReference.requireId(operation.value)),
        MediaImageId(OwnedMediaReference.requireId(image.value)),
    )

    private fun inspect(file: File): DraftInspection {
        check(file.isFile && file.length() > 0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0)
        check(bounds.outWidth.toLong() * bounds.outHeight <= MAX_SOURCE_PIXELS)
        val mime = bounds.outMimeType ?: error("Unknown image MIME")
        requireSupportedMime(mime)
        val exif = ExifInterface(file)
        val orientationTag = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val orientation = when (orientationTag) {
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        val mirrored = orientationTag in setOf(
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE,
        )
        val location = exif.latLong?.let { CandidateLocation(it[0], it[1]) }
        return DraftInspection(bounds.outWidth, bounds.outHeight, mime, orientation, mirrored, captureTime(exif), location)
    }

    private fun captureTime(exif: ExifInterface): Long? {
        val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return null
        val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) ?: return null
        return runCatching {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss XXX", Locale.US).parse("$date $offset")?.time
        }.getOrNull()
    }

    private fun decodeBounded(file: File, width: Int, height: Int): Bitmap {
        var sample = 1
        while (maxOf(width, height) / sample > MediaCompressionPolicy.PRIMARY_LONG_EDGE * 2) sample *= 2
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }))
    }

    private fun orient(bitmap: Bitmap, degrees: Int, mirrored: Boolean): Bitmap {
        if (degrees == 0 && !mirrored) return bitmap
        val matrix = Matrix().apply {
            if (mirrored) postScale(-1f, 1f)
            if (degrees != 0) postRotate(degrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleLongEdge(bitmap: Bitmap, maximum: Int): Bitmap {
        val edge = maxOf(bitmap.width, bitmap.height)
        if (edge <= maximum) return bitmap
        val scale = maximum.toDouble() / edge
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }

    private fun compress(bitmap: Bitmap, file: File, quality: Int) {
        file.parentFile?.mkdirs()
        FileOutputStream(file, false).use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)) }
    }

    private fun requireSupportedMime(value: String) {
        require(value.lowercase(Locale.ROOT) in SUPPORTED_MIME_TYPES) { "Unsupported image MIME" }
    }

    private fun copyBounded(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_SOURCE_BYTES) { "Image input is too large" }
            output.write(buffer, 0, count)
        }
    }

    private fun moveReplacing(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun <T> mediaOutcome(operation: String, block: suspend () -> T): Outcome<T> = withContext(Dispatchers.IO) {
        try {
            Outcome.Success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            Outcome.Failure(DomainFailure.StorageUnavailable("$operation:invalid_input"))
        } catch (_: IllegalStateException) {
            Outcome.Failure(DomainFailure.StorageUnavailable(operation))
        } catch (_: IOException) {
            Outcome.Failure(DomainFailure.StorageUnavailable(operation))
        } catch (_: SecurityException) {
            Outcome.Failure(DomainFailure.StorageUnavailable(operation))
        }
    }

    private companion object {
        const val JPEG_MIME = "image/jpeg"
        const val SHARE_AUTHORITY = "com.billlocket.app.fileprovider"
        const val MEDIA_AUTHORITY = "com.billlocket.app.media"
        const val MAX_SOURCE_BYTES = 32L * 1024 * 1024
        const val MAX_SOURCE_PIXELS = 100_000_000L
        const val SAFETY_WINDOW_MILLIS = 24L * 60 * 60 * 1000
        val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
    }
}

private class MediaPaths(context: Context) {
    val finalRoot = File(context.filesDir, "media/images")
    val stagingRoot = File(context.noBackupFilesDir, "media-staging")
    val trashRoot = File(context.noBackupFilesDir, "media-trash")
    val recoveryRoot = File(context.noBackupFilesDir, "recovery")
    val thumbnailRoot = File(context.cacheDir, "thumbnails")
    val shareRoot = File(context.cacheDir, "share")

    init { listOf(finalRoot, stagingRoot, trashRoot, recoveryRoot, thumbnailRoot, shareRoot).forEach(File::mkdirs) }

    fun stagingFile(value: StagedMedia) = safeChild(safeChild(stagingRoot, value.operationId.value), "${value.imageId.value}.tmp")
    fun finalFile(id: String) = safeChild(finalRoot, "${OwnedMediaReference.requireId(id)}.jpg")
    fun trashOperation(operation: String) = safeChild(trashRoot, OwnedMediaReference.requireId(operation))
    fun trashFile(operation: String, id: String) = safeChild(trashOperation(operation), "${OwnedMediaReference.requireId(id)}.jpg")
    fun shareFile(name: String) = safeChild(shareRoot, name)
    fun operationDirectories() = stagingRoot.listFiles()?.filter(File::isDirectory).orEmpty()
    fun finalFiles() = finalRoot.listFiles()?.filter { it.isFile && it.extension == "jpg" }.orEmpty()

    fun writeJournal(entry: MediaJournalEntry) {
        listOf(
            safeChild(safeChild(stagingRoot, entry.operationId), "operation.json"),
            safeChild(safeChild(recoveryRoot, entry.operationId), "journal.json"),
        ).forEach { file ->
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            JsonWriter(OutputStreamWriter(FileOutputStream(temporary), Charsets.UTF_8)).use { writer ->
                writer.beginObject()
                writer.name("operationId").value(entry.operationId)
                writer.name("imageId").value(entry.imageId)
                writer.name("canonicalUri").value(entry.canonicalUri)
                writer.name("checksumSha256").value(entry.checksumSha256)
                writer.name("state").value(entry.state.name)
                writer.name("updatedAtEpochMillis").value(entry.updatedAtEpochMillis)
                writer.endObject()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun readJournal(operationId: String): MediaJournalEntry? {
        val id = OwnedMediaReference.requireId(operationId)
        val file = listOf(
            safeChild(safeChild(recoveryRoot, id), "journal.json"),
            safeChild(safeChild(stagingRoot, id), "operation.json"),
        ).firstOrNull(File::isFile) ?: return null
        var operation = ""; var image = ""; var uri = ""; var checksum = ""; var state = ""; var updated = -1L
        JsonReader(file.reader(Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) when (reader.nextName()) {
                "operationId" -> operation = reader.nextString()
                "imageId" -> image = reader.nextString()
                "canonicalUri" -> uri = reader.nextString()
                "checksumSha256" -> checksum = reader.nextString()
                "state" -> state = reader.nextString()
                "updatedAtEpochMillis" -> updated = reader.nextLong()
                else -> reader.skipValue()
            }
            reader.endObject()
        }
        check(operation == id && OwnedMediaReference.imageId(uri) == image)
        check(checksum.matches(Regex("[0-9a-f]{64}")))
        return MediaJournalEntry(operation, image, uri, checksum, MediaJournalState.valueOf(state), updated)
    }

    fun removeOperationArtifacts(operation: String) {
        val id = OwnedMediaReference.requireId(operation)
        deleteTree(safeChild(stagingRoot, id)); deleteTree(safeChild(recoveryRoot, id))
    }

    fun expiredTemporaryEntries(now: Long, age: Long) = listOf(trashRoot, thumbnailRoot, shareRoot).flatMap { root ->
        root.listFiles()?.filter { !Files.isSymbolicLink(it.toPath()) && now - it.lastModified() >= age }.orEmpty()
    }

    fun sizeOf(root: File): Long {
        if (!root.exists() || Files.isSymbolicLink(root.toPath())) return 0
        return root.listFiles()?.sumOf {
            when { Files.isSymbolicLink(it.toPath()) -> 0; it.isDirectory -> sizeOf(it); it.isFile -> it.length(); else -> 0 }
        } ?: 0
    }

    fun deleteTree(target: File) {
        val path = target.toPath().toAbsolutePath().normalize()
        val allowed = listOf(stagingRoot, recoveryRoot, trashRoot, thumbnailRoot, shareRoot).any {
            path.startsWith(it.toPath().toAbsolutePath().normalize())
        }
        check(allowed) { "Unsafe media deletion" }
        if (Files.isSymbolicLink(path)) return
        target.listFiles()?.forEach { if (!Files.isSymbolicLink(it.toPath())) deleteTree(it) }
        if (target.exists()) check(target.delete())
    }

    private fun safeChild(root: File, name: String): File {
        require(name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name)
        val base = root.toPath().toAbsolutePath().normalize()
        val resolved = base.resolve(name).normalize()
        require(resolved.parent == base) { "Unsafe media path" }
        return resolved.toFile()
    }
}
