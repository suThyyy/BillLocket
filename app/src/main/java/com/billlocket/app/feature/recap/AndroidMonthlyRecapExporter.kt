package com.billlocket.app.feature.recap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidMonthlyRecapExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) : MonthlyRecapExporter {
    override suspend fun export(recap: ShareableMonthlyRecap): String = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "share").apply { mkdirs() }
        directory.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) && it.name.endsWith(".jpg") }
            ?.forEach { it.delete() }
        val file = File(directory, "$FILE_PREFIX${recap.month}.jpg")
        render(recap).useBitmap { bitmap ->
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "Không thể ghi ảnh tổng kết" }
            }
        }
        FileProvider.getUriForFile(context, AUTHORITY, file).toString()
    }

    private fun render(recap: ShareableMonthlyRecap): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        val title = paint(Color.WHITE, 64f, true)
        val gold = paint(GOLD, 34f, true)
        val body = paint(Color.WHITE, 30f)
        val muted = paint(MUTED, 25f)
        canvas.drawText("BillLocket", PADDING.toFloat(), 100f, gold)
        canvas.drawText("Nhìn lại tháng ${recap.month.format(DateTimeFormatter.ofPattern("MM/yyyy"))}", PADDING.toFloat(), 180f, title)
        canvas.drawText("${recap.transactionCount} giao dịch", PADDING.toFloat(), 235f, muted)

        var y = 310f
        recap.expense?.let {
            canvas.drawText("Đã chi", PADDING.toFloat(), y, muted)
            canvas.drawText(formatMoney(it.minorUnits), PADDING.toFloat(), y + 55f, paint(EXPENSE, 48f, true))
            y += 105f
        }
        recap.income?.let {
            canvas.drawText("Đã thu ${formatMoney(it.minorUnits)}", PADDING.toFloat(), y, body)
            y += 55f
        }
        recap.topCategoryName?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText("Nhiều nhất: $it", PADDING.toFloat(), y, body)
            y += 60f
        }

        val imagesTop = maxOf(500, y.toInt() + 20)
        drawPhotos(canvas, recap.photos, imagesTop)
        canvas.drawText("Kỷ niệm chi tiêu, chỉ thuộc về bạn", PADDING.toFloat(), HEIGHT - 54f, muted)
        return bitmap
    }

    private fun drawPhotos(canvas: Canvas, photos: List<ShareableRecapPhoto>, top: Int) {
        val selected = photos.take(4)
        if (selected.isEmpty()) {
            val emptyPaint = paint(MUTED, 30f)
            canvas.drawText("Tháng này chưa có ảnh giao dịch", PADDING.toFloat(), top + 100f, emptyPaint)
            return
        }
        val gap = 18
        val cellWidth = (WIDTH - PADDING * 2 - gap) / 2
        val cellHeight = 300
        selected.forEachIndexed { index, photo ->
            val left = PADDING + (index % 2) * (cellWidth + gap)
            val rowTop = top + (index / 2) * (cellHeight + gap)
            val rect = Rect(left, rowTop, left + cellWidth, rowTop + cellHeight)
            val decoded = decodeBounded(photo.mediaUri, cellWidth, cellHeight)
            if (decoded != null) {
                drawCenterCrop(canvas, decoded, rect)
                decoded.recycle()
            } else {
                canvas.drawRoundRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), 28f, 28f, paint(SURFACE, 1f))
                canvas.drawText("Không đọc được ảnh", rect.left + 24f, rect.centerY().toFloat(), paint(MUTED, 24f))
            }
            canvas.drawRect(rect.left.toFloat(), rect.bottom - 112f, rect.right.toFloat(), rect.bottom.toFloat(), paint(0xC9000000.toInt(), 1f))
            canvas.drawText(photo.categoryName.ifBlank { "Giao dịch" }, rect.left + 18f, rect.bottom - 76f, paint(Color.WHITE, 24f, true))
            photo.amount?.let { canvas.drawText(formatMoney(it.minorUnits), rect.left + 18f, rect.bottom - 48f, paint(GOLD, 21f, true)) }
            val privateContext = listOfNotNull(photo.note, photo.placeName)
                .joinToString(" • ")
                .take(38)
            if (privateContext.isNotBlank()) {
                canvas.drawText(privateContext, rect.left + 18f, rect.bottom - 18f, paint(Color.WHITE, 19f))
            }
        }
    }

    private fun decodeBounded(rawUri: String, width: Int, height: Int): Bitmap? = runCatching {
        val uri = Uri.parse(rawUri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > width * 2 || bounds.outHeight / sample > height * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, target: Rect) {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        val targetRatio = target.width().toFloat() / target.height()
        val source = if (sourceRatio > targetRatio) {
            val width = (bitmap.height * targetRatio).toInt()
            val left = (bitmap.width - width) / 2
            Rect(left, 0, left + width, bitmap.height)
        } else {
            val height = (bitmap.width / targetRatio).toInt()
            val top = (bitmap.height - height) / 2
            Rect(0, top, bitmap.width, top + height)
        }
        canvas.drawBitmap(bitmap, source, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }

    private fun formatMoney(value: Long): String = NumberFormat.getNumberInstance(Locale("vi", "VN")).format(value) + " đ"

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T = try {
        block(this)
    } finally {
        recycle()
    }

    private companion object {
        const val AUTHORITY = "com.billlocket.app.fileprovider"
        const val FILE_PREFIX = "monthly-recap-"
        const val WIDTH = 1080
        const val HEIGHT = 1350
        const val PADDING = 64
        const val BACKGROUND = 0xFF0D0D0D.toInt()
        const val SURFACE = 0xFF24211B.toInt()
        const val GOLD = 0xFFFFB800.toInt()
        const val EXPENSE = 0xFFFF716D.toInt()
        const val MUTED = 0xFFA8A39A.toInt()
    }
}
