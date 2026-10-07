package com.billlocket.app.backup

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.util.JsonWriter
import androidx.sqlite.db.SupportSQLiteDatabase
import com.billlocket.app.data.local.BillLocketDatabase
import com.billlocket.app.domain.model.OwnedMediaReference
import com.billlocket.app.domain.port.*
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.widget.BillLocketWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.*
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val FORMAT_VERSION = 1
private const val MAX_ENTRIES = 20_100
private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
private const val MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024
private val SAFE_MEDIA = Regex("media/[A-Za-z0-9._~-]{1,128}\\.jpg")

fun isSafeBackupEntry(name: String): Boolean = name.isNotBlank() && !name.startsWith('/') && !name.startsWith('\\') &&
    !name.contains("..") && !name.contains('\\') && name.split('/').none { it.isBlank() || it == "." || it == ".." }

@Singleton
class AndroidPortableBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: BillLocketDatabase,
    private val preferences: SharedPreferences,
    private val media: MediaStorePort,
    private val widget: WidgetUpdatePort,
) : BackupPort {
    init { recoverInterruptedImports() }

    override suspend fun export(options: BackupOptions, destination: OutputStream): Outcome<BackupSummary> = backupOutcome("backup_export") {
        val work = tempDirectory("export")
        try {
            val dataFile = File(work, "data.json")
            val result = writePortableData(database.openHelper.readableDatabase, dataFile, options, preferences)
            val checksums = linkedMapOf("data.json" to sha256(dataFile))
            ZipOutputStream(BufferedOutputStream(destination)).use { zip ->
                val manifest = JSONObject().apply {
                    put("format", "BillLocket")
                    put("formatVersion", FORMAT_VERSION)
                    put("databaseSchemaVersion", 1)
                    put("exportedAt", Instant.now().toString())
                    put("sourceTimezone", ZoneId.systemDefault().id)
                    put("currency", preferences.getString("settings.currency", "VND"))
                    put("accountCount", result.summary.accountCount)
                    put("transactionCount", result.summary.transactionCount)
                    put("imageCount", result.summary.imageCount)
                    put("includesImages", options.includeImages)
                    put("includesLocations", options.includeLocations)
                    put("checksumAlgorithm", "SHA-256")
                }.toString().toByteArray()
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
                zipFile(zip, "data.json", dataFile)
                if (options.includeImages) result.media.forEach { (uri, name) ->
                    when (val opened = media.openRead(uri)) {
                        is Outcome.Success -> {
                            val digest = MessageDigest.getInstance("SHA-256")
                            zip.putNextEntry(ZipEntry(name))
                            opened.value.use { input -> copyDigest(input, zip, digest, MAX_ENTRY_BYTES) }
                            zip.closeEntry()
                            checksums[name] = digest.hex()
                        }
                        is Outcome.Failure -> throw IOException("Missing declared media")
                    }
                }
                val checksumText = checksums.entries.joinToString("\n", postfix = "\n") { "${it.value}  ${it.key}" }.toByteArray()
                zip.putNextEntry(ZipEntry("checksums.sha256")); zip.write(checksumText); zip.closeEntry()
            }
            result.summary
        } finally { work.deleteRecursively() }
    }

    override suspend fun inspect(source: InputStream): Outcome<BackupSummary> = backupOutcome("backup_inspect") {
        val extracted = extractAndValidate(source)
        try { extracted.summary } finally { extracted.root.deleteRecursively() }
    }

    override suspend fun importReplace(source: InputStream): Outcome<BackupSummary> = backupOutcome("backup_import") {
        val extracted = extractAndValidate(source)
        val recovery = File(context.noBackupFilesDir, "recovery/import-${UUID.randomUUID()}")
        val currentImages = File(context.filesDir, "media/images")
        val previousImages = File(recovery, "images")
        val incomingImages = File(extracted.root, "media")
        recovery.mkdirs()
        val fingerprint = transactionFingerprint(extracted.data.getJSONArray("transactions"))
        writeImportJournal(recovery, "PREPARED", fingerprint)
        try {
            database.runInTransaction {
                if (currentImages.exists()) moveDirectory(currentImages, previousImages)
                if (incomingImages.exists()) moveDirectory(incomingImages, currentImages) else currentImages.mkdirs()
                try {
                    replaceDatabase(database.openHelper.writableDatabase, extracted.data)
                    writeImportJournal(recovery, "DATABASE_READY", fingerprint)
                }
                catch (failure: Throwable) {
                    currentImages.deleteRecursively()
                    if (previousImages.exists()) moveDirectory(previousImages, currentImages)
                    throw failure
                }
            }
            writeImportJournal(recovery, "COMMITTED", fingerprint)
            applySettings(extracted.data.optJSONObject("settings"))
            widget.requestRefresh()
            extracted.summary
        } catch (failure: Throwable) {
            if (previousImages.exists()) {
                currentImages.deleteRecursively()
                moveDirectory(previousImages, currentImages)
            }
            throw failure
        } finally {
            recovery.deleteRecursively()
            extracted.root.deleteRecursively()
        }
    }

    private fun extractAndValidate(source: InputStream): ExtractedBackup {
        val root = tempDirectory("import")
        val names = hashSetOf<String>()
        var total = 0L
        ZipInputStream(BufferedInputStream(source)).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                require(count <= MAX_ENTRIES && !entry.isDirectory && isSafeBackupEntry(entry.name) && names.add(entry.name))
                require(entry.name in setOf("manifest.json", "data.json", "checksums.sha256") || SAFE_MEDIA.matches(entry.name))
                val target = File(root, entry.name)
                require(target.canonicalPath.startsWith(root.canonicalPath + File.separator))
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { output -> total += copyBounded(zip, output, MAX_ENTRY_BYTES) }
                require(total <= MAX_ARCHIVE_BYTES)
                zip.closeEntry()
            }
        }
        require(names.containsAll(listOf("manifest.json", "data.json", "checksums.sha256")))
        val manifest = JSONObject(File(root, "manifest.json").readText())
        require(manifest.getString("format") == "BillLocket" && manifest.getInt("formatVersion") == FORMAT_VERSION)
        val checksums = parseChecksums(File(root, "checksums.sha256"))
        require(checksums.keys == names.filter { it == "data.json" || it.startsWith("media/") }.toSet())
        checksums.forEach { (name, checksum) -> require(sha256(File(root, name)) == checksum) }
        val data = JSONObject(File(root, "data.json").readText())
        validateData(data, manifest, names)
        return ExtractedBackup(root, data, summary(manifest))
    }

    private fun tempDirectory(prefix: String) = File(context.noBackupFilesDir, "import-staging/$prefix-${UUID.randomUUID()}").apply { mkdirs() }

    private fun recoverInterruptedImports() {
        val root = File(context.noBackupFilesDir, "recovery")
        root.listFiles()?.filter { it.isDirectory && it.name.startsWith("import-") }?.forEach { recovery ->
            val previous = File(recovery, "images")
            val current = File(context.filesDir, "media/images")
            val journal = runCatching { JSONObject(File(recovery, "journal.json").readText()) }.getOrNull()
            val committed = journal?.optString("state") == "COMMITTED"
            val databaseMatches = journal?.optString("state") == "DATABASE_READY" && journal.optString("fingerprint") == currentTransactionFingerprint(database.openHelper.readableDatabase)
            if (!committed && !databaseMatches && previous.exists()) {
                current.deleteRecursively()
                moveDirectory(previous, current)
            }
            recovery.deleteRecursively()
        }
    }

    private fun applySettings(settings: JSONObject?) {
        if (settings == null) return
        preferences.edit()
            .putString("settings.currency", settings.optString("currency", "VND"))
            .putString("settings.theme", settings.optString("theme", "SYSTEM"))
            .putString("settings.amount_visibility", settings.optString("amountVisibility", "SHOWN"))
            .putBoolean("settings.widget.show_amount", settings.optBoolean("widgetShowAmount", false))
            .putBoolean("settings.widget.show_category", settings.optBoolean("widgetShowCategory", true))
            .putString("settings.widget.open_action", settings.optString("widgetOpenAction", "TRANSACTION_DETAIL"))
            .commit()
    }

    private suspend fun <T> backupOutcome(operation: String, block: suspend () -> T): Outcome<T> = withContext(Dispatchers.IO) {
        try { Outcome.Success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Throwable) { Outcome.Failure(DomainFailure.StorageUnavailable(operation)) }
    }
}

private data class ExportData(val summary: BackupSummary, val media: List<Pair<String, String>>)
private data class ExtractedBackup(val root: File, val data: JSONObject, val summary: BackupSummary)

private fun writePortableData(db: SupportSQLiteDatabase, target: File, options: BackupOptions, preferences: SharedPreferences): ExportData {
    val tables = linkedMapOf(
        "accounts" to "SELECT * FROM accounts ORDER BY sort_order, id",
        "categories" to "SELECT * FROM categories ORDER BY transaction_type, sort_order, id",
        "transactions" to "SELECT * FROM transactions WHERE deleted_at IS NULL ORDER BY occurred_at, id",
        "images" to if (options.includeImages) "SELECT i.* FROM transaction_images i JOIN transactions t ON t.id=i.transaction_id WHERE t.deleted_at IS NULL ORDER BY i.transaction_id, i.position" else null,
        "transfers" to "SELECT * FROM transfers WHERE deleted_at IS NULL ORDER BY occurred_at, id",
        "budgets" to "SELECT * FROM budgets ORDER BY month_start_epoch_day, scope_key, id",
        "thresholdEvents" to "SELECT e.* FROM budget_threshold_events e JOIN budgets b ON b.id=e.budget_id ORDER BY e.budget_id, e.period_start_epoch_day, e.threshold_percent",
    )
    var accounts = 0; var transactions = 0; var images = 0
    val media = mutableListOf<Pair<String, String>>()
    JsonWriter(OutputStreamWriter(FileOutputStream(target), Charsets.UTF_8)).use { writer ->
        writer.beginObject(); writer.name("formatVersion").value(FORMAT_VERSION.toLong())
        tables.forEach { (name, sql) ->
            writer.name(name).beginArray()
            if (sql != null) db.query(sql).use { cursor ->
                while (cursor.moveToNext()) {
                    writer.beginObject()
                    cursor.columnNames.forEachIndexed { index, column ->
                        writer.name(column)
                        if (!options.includeLocations && name == "images" && column in LOCATION_COLUMNS) writer.nullValue()
                        else writeCursorValue(writer, cursor, index)
                    }
                    writer.endObject()
                    when (name) { "accounts" -> accounts++; "transactions" -> transactions++; "images" -> { images++; val uri = cursor.getString(cursor.getColumnIndexOrThrow("content_uri")); val id = OwnedMediaReference.imageId(uri); media += uri to "media/$id.jpg" } }
                }
            }
            writer.endArray()
        }
        writer.name("settings").beginObject()
        writer.name("currency").value(preferences.getString("settings.currency", "VND") ?: "VND")
        writer.name("theme").value(preferences.getString("settings.theme", "SYSTEM") ?: "SYSTEM")
        writer.name("amountVisibility").value(preferences.getString("settings.amount_visibility", "SHOWN") ?: "SHOWN")
        writer.name("widgetShowAmount").value(preferences.getBoolean("settings.widget.show_amount", false))
        writer.name("widgetShowCategory").value(preferences.getBoolean("settings.widget.show_category", true))
        writer.name("widgetOpenAction").value(preferences.getString("settings.widget.open_action", "TRANSACTION_DETAIL") ?: "TRANSACTION_DETAIL")
        writer.endObject(); writer.endObject()
    }
    return ExportData(BackupSummary(accounts, transactions, images, options.includeImages, options.includeLocations), media)
}

private val LOCATION_COLUMNS = setOf("latitude", "longitude", "accuracy_meters", "place_name", "location_source", "location_confirmed_at")

private fun validateData(data: JSONObject, manifest: JSONObject, names: Set<String>) {
    require(data.getInt("formatVersion") == FORMAT_VERSION)
    val accounts = ids(data.getJSONArray("accounts")); val categories = ids(data.getJSONArray("categories")); val transactions = ids(data.getJSONArray("transactions")); val images = ids(data.getJSONArray("images")); val transfers = ids(data.getJSONArray("transfers")); val budgets = ids(data.getJSONArray("budgets"))
    require(accounts.size == manifest.getInt("accountCount") && transactions.size == manifest.getInt("transactionCount") && images.size == manifest.getInt("imageCount"))
    val currency = manifest.getString("currency")
    data.getJSONArray("transactions").eachObject { require(it.getString("account_id") in accounts && it.getString("category_id") in categories && it.getLong("amount_minor") > 0 && it.getString("currency_code") == currency) }
    data.getJSONArray("images").eachObject {
        require(it.getString("transaction_id") in transactions)
        val id = it.getString("id"); require(OwnedMediaReference.imageId(it.getString("content_uri")) == id)
        if (manifest.getBoolean("includesImages")) require("media/$id.jpg" in names)
        val lat = it.optDouble("latitude", Double.NaN); val lon = it.optDouble("longitude", Double.NaN)
        require((lat.isNaN() && lon.isNaN()) || (lat in -90.0..90.0 && lon in -180.0..180.0 && it.optLong("location_confirmed_at", -1) >= 0))
    }
    val expectedMedia = if (manifest.getBoolean("includesImages")) images.map { "media/$it.jpg" }.toSet() else emptySet()
    require(names.filter { it.startsWith("media/") }.toSet() == expectedMedia)
    data.getJSONArray("transfers").eachObject { require(it.getString("from_account_id") in accounts && it.getString("to_account_id") in accounts && it.getString("from_account_id") != it.getString("to_account_id")) }
    data.getJSONArray("thresholdEvents").eachObject { require(it.getString("budget_id") in budgets) }
}

private fun replaceDatabase(db: SupportSQLiteDatabase, data: JSONObject) {
    listOf("budget_threshold_events", "budgets", "transfers", "transaction_search", "transaction_images", "transactions", "categories", "accounts").forEach { db.execSQL("DELETE FROM $it") }
    val mapping = linkedMapOf("accounts" to "accounts", "categories" to "categories", "transactions" to "transactions", "images" to "transaction_images", "transfers" to "transfers", "budgets" to "budgets", "thresholdEvents" to "budget_threshold_events")
    mapping.forEach { (jsonName, table) -> data.getJSONArray(jsonName).eachObject { row -> require(db.insert(table, 0, row.toValues()) != -1L) } }
    db.execSQL("INSERT INTO transaction_search(rowid, transaction_id, note) SELECT rowid, id, COALESCE(note, '') FROM transactions")
}

private fun JSONObject.toValues() = ContentValues().also { values ->
    keys().forEach { key -> when (val value = get(key)) { JSONObject.NULL -> values.putNull(key); is String -> values.put(key, value); is Int -> values.put(key, value); is Long -> values.put(key, value); is Double -> values.put(key, value); is Boolean -> values.put(key, if (value) 1 else 0); else -> error("Unsupported backup value") } } }

private fun ids(array: JSONArray): Set<String> { val result = hashSetOf<String>(); array.eachObject { require(result.add(it.getString("id")) && it.getString("id").matches(Regex("[A-Za-z0-9._~-]{1,128}"))) }; return result }
private inline fun JSONArray.eachObject(block: (JSONObject) -> Unit) { for (index in 0 until length()) block(getJSONObject(index)) }
private fun summary(manifest: JSONObject) = BackupSummary(manifest.getInt("accountCount"), manifest.getInt("transactionCount"), manifest.getInt("imageCount"), manifest.getBoolean("includesImages"), manifest.getBoolean("includesLocations"))
private fun writeImportJournal(root: File, state: String, fingerprint: String) { File(root, "journal.json").writeText(JSONObject().put("state", state).put("fingerprint", fingerprint).toString()) }
private fun transactionFingerprint(array: JSONArray): String { val ids = mutableListOf<String>(); array.eachObject { ids += it.getString("id") }; return digestText(ids.sorted().joinToString("\n")) }
private fun currentTransactionFingerprint(db: SupportSQLiteDatabase): String { val ids = mutableListOf<String>(); db.query("SELECT id FROM transactions WHERE deleted_at IS NULL ORDER BY id").use { while (it.moveToNext()) ids += it.getString(0) }; return digestText(ids.joinToString("\n")) }
private fun digestText(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

private fun writeCursorValue(writer: JsonWriter, cursor: Cursor, index: Int) { when (cursor.getType(index)) { Cursor.FIELD_TYPE_NULL -> writer.nullValue(); Cursor.FIELD_TYPE_INTEGER -> writer.value(cursor.getLong(index)); Cursor.FIELD_TYPE_FLOAT -> writer.value(cursor.getDouble(index)); Cursor.FIELD_TYPE_STRING -> writer.value(cursor.getString(index)); else -> throw IOException("Unsupported database value") } }
private fun zipFile(zip: ZipOutputStream, name: String, file: File) { zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
private fun sha256(file: File): String = file.inputStream().use { input -> val digest = MessageDigest.getInstance("SHA-256"); copyDigest(input, null, digest, MAX_ENTRY_BYTES); digest.hex() }
private fun MessageDigest.hex() = digest().joinToString("") { "%02x".format(it) }
private fun copyDigest(input: InputStream, output: OutputStream?, digest: MessageDigest, limit: Long): Long { val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var total = 0L; while (true) { val read = input.read(buffer); if (read < 0) break; total += read; require(total <= limit); digest.update(buffer, 0, read); output?.write(buffer, 0, read) }; return total }
private fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long = copyDigest(input, output, MessageDigest.getInstance("SHA-256"), limit)
private fun parseChecksums(file: File): Map<String, String> = file.readLines().filter(String::isNotBlank).associate { line -> val parts = line.split("  ", limit = 2); require(parts.size == 2 && parts[0].matches(Regex("[0-9a-f]{64}")) && isSafeBackupEntry(parts[1])); parts[1] to parts[0] }
private fun moveDirectory(source: File, target: File) { target.parentFile?.mkdirs(); require(!target.exists()); if (!source.renameTo(target)) { source.copyRecursively(target); require(source.deleteRecursively()) } }
