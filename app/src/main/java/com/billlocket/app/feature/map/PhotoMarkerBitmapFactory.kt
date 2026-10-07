package com.billlocket.app.feature.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.LruCache
import kotlin.math.max

enum class MapMarkerZoomTier { OUT, MEDIUM, IN }

fun mapMarkerZoomTier(zoom: Float): MapMarkerZoomTier = when {
    zoom < 11.5f -> MapMarkerZoomTier.OUT
    zoom >= 15.0f -> MapMarkerZoomTier.IN
    else -> MapMarkerZoomTier.MEDIUM
}

/** Creates bounded, app-owned compact marker images matching Image C design. */
internal object PhotoMarkerBitmapFactory {
    private const val MAX_CACHE_BYTES = 12 * 1024 * 1024
    private const val MAX_DECODE_EDGE_PX = 256
    private const val GOLD = 0xFFFFB800.toInt()
    private const val NORMAL_BORDER = 0xFFF0EFEB.toInt()
    private const val SURFACE = 0xFF171612.toInt()
    private const val BADGE_BG = 0xFF141311.toInt()

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun fallback(
        context: Context,
        cluster: MapCluster,
        isSelected: Boolean = false,
        zoomTier: MapMarkerZoomTier = MapMarkerZoomTier.MEDIUM,
    ): Bitmap = cached(context, cluster, fallbackOnly = true, isSelected, zoomTier) { null }

    fun representative(
        context: Context,
        cluster: MapCluster,
        isSelected: Boolean = false,
        zoomTier: MapMarkerZoomTier = MapMarkerZoomTier.MEDIUM,
    ): Bitmap = cached(context, cluster, fallbackOnly = false, isSelected, zoomTier) {
        decodeBounded(context, cluster.photos.first().mediaUri)
    }

    private fun cached(
        context: Context,
        cluster: MapCluster,
        fallbackOnly: Boolean,
        isSelected: Boolean,
        zoomTier: MapMarkerZoomTier,
        source: () -> Bitmap?,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val key = buildString {
            append(if (fallbackOnly) "fallback" else "photo")
            append('|').append(context.resources.displayMetrics.densityDpi)
            append('|').append(cluster.id)
            append('|').append(cluster.photos.size)
            append('|').append(if (isSelected) "selected" else "normal")
            append('|').append(zoomTier.name)
            if (!fallbackOnly) append('|').append(cluster.photos.first().mediaUri)
        }
        synchronized(cache) { cache.get(key)?.let { return it } }
        val marker = drawMarker(density, cluster.photos.size, source(), isSelected, zoomTier)
        synchronized(cache) { cache.put(key, marker) }
        return marker
    }

    private fun decodeBounded(context: Context, value: String): Bitmap? = runCatching {
        val uri = Uri.parse(value)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_DECODE_EDGE_PX * 2) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.getOrNull()

    private fun drawMarker(
        density: Float,
        count: Int,
        source: Bitmap?,
        isSelected: Boolean,
        zoomTier: MapMarkerZoomTier,
    ): Bitmap {
        fun dp(value: Float) = value * density
        val scale = when (zoomTier) {
            MapMarkerZoomTier.OUT -> 0.85f
            MapMarkerZoomTier.MEDIUM -> 1.0f
            MapMarkerZoomTier.IN -> 1.08f
        } * (if (isSelected) 1.22f else 1.0f)

        // Compact base proportions: width 46dp, height 54dp
        val baseWidth = dp(46f * scale)
        val baseHeight = dp(54f * scale)
        val width = baseWidth.toInt().coerceAtLeast(40)
        val height = baseHeight.toInt().coerceAtLeast(48)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cardHeight = height - dp(8f * scale)
        val cornerRadius = dp(11f * scale)
        val borderWidth = dp((if (isSelected) 2.6f else 1.8f) * scale)

        val outer = RectF(borderWidth / 2f, borderWidth / 2f, width - borderWidth / 2f, cardHeight)
        val inner = RectF(
            outer.left + borderWidth,
            outer.top + borderWidth,
            outer.right - borderWidth,
            outer.bottom - borderWidth,
        )

        val pointerWidth = dp(10f * scale)
        val pointerHeight = dp(6f * scale)
        val pointer = Path().apply {
            moveTo(width / 2f - pointerWidth / 2f, cardHeight - dp(1f))
            lineTo(width / 2f, height - dp(2f))
            lineTo(width / 2f + pointerWidth / 2f, cardHeight - dp(1f))
            close()
        }

        // Draw pin border & background
        paint.style = Paint.Style.FILL
        paint.color = if (isSelected) GOLD else NORMAL_BORDER
        canvas.drawRoundRect(outer, cornerRadius, cornerRadius, paint)
        canvas.drawPath(pointer, paint)

        // Draw photo content or fallback inside
        if (source != null) {
            val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val imgScale = max(inner.width() / source.width, inner.height() / source.height)
            val matrix = Matrix().apply {
                setScale(imgScale, imgScale)
                postTranslate(
                    inner.left + (inner.width() - source.width * imgScale) / 2f,
                    inner.top + (inner.height() - source.height * imgScale) / 2f,
                )
            }
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            canvas.drawRoundRect(inner, cornerRadius - dp(1.5f), cornerRadius - dp(1.5f), paint)
            paint.shader = null
            source.recycle()
        } else {
            paint.color = SURFACE
            canvas.drawRoundRect(inner, cornerRadius - dp(1.5f), cornerRadius - dp(1.5f), paint)
            drawFallbackGlyph(canvas, inner, paint, density * scale, isSelected)
        }

        // For selected marker, draw a subtle target center ring below pointer
        if (isSelected) {
            paint.style = Paint.Style.FILL
            paint.color = GOLD
            canvas.drawCircle(width / 2f, height - dp(2f), dp(2f), paint)
        }

        // Draw count badge on top right
        if (count > 1) {
            drawCountBadge(canvas, count, paint, density * scale, width)
        }

        return output
    }

    private fun drawFallbackGlyph(
        canvas: Canvas,
        bounds: RectF,
        paint: Paint,
        density: Float,
        isSelected: Boolean,
    ) {
        fun dp(value: Float) = value * density
        paint.color = if (isSelected) GOLD else NORMAL_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val glyph = Path().apply {
            moveTo(bounds.left + dp(8f), bounds.bottom - dp(10f))
            lineTo(bounds.left + dp(15f), bounds.top + dp(14f))
            lineTo(bounds.left + dp(21f), bounds.top + dp(20f))
            lineTo(bounds.right - dp(8f), bounds.top + dp(12f))
        }
        canvas.drawPath(glyph, paint)
        canvas.drawCircle(bounds.right - dp(10f), bounds.top + dp(10f), dp(3f), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawCountBadge(
        canvas: Canvas,
        count: Int,
        paint: Paint,
        density: Float,
        width: Int,
    ) {
        fun dp(value: Float) = value * density
        val radius = dp(8.5f)
        val centerX = width - radius - dp(1f)
        val centerY = radius + dp(1f)

        // Dark background circle with subtle border
        paint.color = BADGE_BG
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, radius, paint)

        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        canvas.drawCircle(centerX, centerY, radius, paint)

        // Count text
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = dp(if (count > 99) 7.5f else 8.5f)
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val label = if (count > 99) "99+" else count.toString()
        val baseline = centerY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        canvas.drawText(label, centerX, baseline, paint)
    }
}
