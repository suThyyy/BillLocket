package com.billlocket.app.feature.transaction.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Singleton
class AndroidMlKitImageCategorySuggestionEngine(
    @ApplicationContext private val context: Context,
    private val dispatcher: CoroutineDispatcher,
) : ImageCategorySuggestionEngine {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Dispatchers.IO)

    override suspend fun suggest(imageUri: String): CategorySuggestion? = withContext(dispatcher) {
        if (imageUri.isBlank()) return@withContext null

        val uri = parseUri(imageUri) ?: return@withContext null
        val labeler = try {
            ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        } catch (_: Throwable) {
            return@withContext null
        }

        val rotationDegrees = extractRotationDegrees(uri)
        val bitmap = decodeSampledBitmap(uri, MAX_INFERENCE_DIMENSION)
        if (bitmap == null) {
            try {
                labeler.close()
            } catch (_: Throwable) {
                // Ignore close error
            }
            return@withContext null
        }

        val resources = InferenceResources(bitmap, labeler)
        val labels = try {
            processWithResourceManagement(resources, rotationDegrees)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } ?: return@withContext null

        val recognized = labels.map { label ->
            RecognizedLabel(
                text = label.text,
                confidence = label.confidence,
            )
        }

        ImageLabelClassifier.classify(recognized)
    }

    private fun parseUri(imageUri: String): Uri? {
        return try {
            val parsed = Uri.parse(imageUri)
            if (parsed.scheme != null) parsed
            else Uri.fromFile(File(imageUri))
        } catch (_: Throwable) {
            null
        }
    }

    private fun openInputStream(uri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractRotationDegrees(uri: Uri): Int {
        var stream: InputStream? = null
        return try {
            stream = openInputStream(uri) ?: return 0
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Throwable) {
            0
        } finally {
            try {
                stream?.close()
            } catch (_: Throwable) {
                // Ignore close error
            }
        }
    }

    private fun decodeSampledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        var boundsStream: InputStream? = null
        var decodeStream: InputStream? = null
        return try {
            boundsStream = openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(boundsStream, null, options)
            boundsStream.close()
            boundsStream = null

            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return null

            val inSampleSize = calculateInSampleSize(width, height, maxDimension)

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            decodeStream = openInputStream(uri) ?: return null
            BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
        } catch (_: Throwable) {
            null
        } finally {
            try {
                boundsStream?.close()
            } catch (_: Throwable) {
                // Ignore close error
            }
            try {
                decodeStream?.close()
            } catch (_: Throwable) {
                // Ignore close error
            }
        }
    }

    private suspend fun processWithResourceManagement(
        resources: InferenceResources,
        rotationDegrees: Int,
    ): List<ImageLabel>? = suspendCancellableCoroutine { continuation ->
        try {
            val inputImage = InputImage.fromBitmap(resources.bitmap, rotationDegrees)
            val task = resources.labeler.process(inputImage)
            task.addOnCompleteListener { completedTask ->
                try {
                    if (continuation.isActive) {
                        if (completedTask.isSuccessful) {
                            continuation.resume(completedTask.result)
                        } else {
                            continuation.resume(null)
                        }
                    }
                } finally {
                    resources.cleanup()
                }
            }
        } catch (_: Throwable) {
            resources.cleanup()
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }

    companion object {
        const val MAX_INFERENCE_DIMENSION = 1024
    }
}

internal class InferenceResources(
    val bitmap: Bitmap,
    val labeler: ImageLabeler,
) {
    private val cleanedUp = AtomicBoolean(false)

    fun cleanup() {
        if (cleanedUp.compareAndSet(false, true)) {
            try {
                bitmap.recycle()
            } catch (_: Throwable) {
                // Ignore recycle error
            }
            try {
                labeler.close()
            } catch (_: Throwable) {
                // Ignore close error
            }
        }
    }
}

internal fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
    var sampleSize = 1
    val maxDim = maxOf(width, height)
    while (Math.ceil(maxDim.toDouble() / sampleSize) > maxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}
