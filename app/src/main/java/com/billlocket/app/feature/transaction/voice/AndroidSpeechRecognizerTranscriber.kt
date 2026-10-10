package com.billlocket.app.feature.transaction.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Android implementation of [VoiceNoteTranscriber] using the system [SpeechRecognizer].
 *
 * Guarantees:
 * - All operations on the Android [SpeechRecognizer] occur on the main thread.
 * - Audio is never recorded, saved, cached, or retained by BillLocket.
 * - On API 31+, prefers [SpeechRecognizer.createOnDeviceSpeechRecognizer] when available.
 * - Destroys the underlying recognizer on [close].
 */
class AndroidSpeechRecognizerTranscriber(
    private val context: Context,
) : VoiceNoteTranscriber {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _events = MutableSharedFlow<VoiceTranscriberEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<VoiceTranscriberEvent> = _events.asSharedFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isCancelled = false
    private var isClosed = false
    private var activeGeneration = 0L

    override fun isAvailable(): Boolean {
        return runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
    }

    override fun start(localeTag: String) {
        runOnMainThread {
            if (isClosed) return@runOnMainThread
            isCancelled = false
            val generation = ++activeGeneration

            cleanupRecognizer()

            val recognizer = createRecognizer()
            if (recognizer == null) {
                _events.tryEmit(VoiceTranscriberEvent.Error(VoiceRecognitionError.ServiceUnavailable))
                return@runOnMainThread
            }
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(createListener(generation))

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            try {
                recognizer.startListening(intent)
            } catch (t: Throwable) {
                if (generation != activeGeneration) return@runOnMainThread
                cleanupRecognizer()
                _events.tryEmit(VoiceTranscriberEvent.Error(VoiceRecognitionError.ClientError))
            }
        }
    }

    override fun stop() {
        runOnMainThread {
            if (isClosed || isCancelled) return@runOnMainThread
            runCatching { speechRecognizer?.stopListening() }
        }
    }

    override fun cancel() {
        runOnMainThread {
            if (isClosed) return@runOnMainThread
            isCancelled = true
            activeGeneration++
            runCatching { speechRecognizer?.cancel() }
            cleanupRecognizer()
        }
    }

    override fun close() {
        runOnMainThread {
            if (isClosed) return@runOnMainThread
            isClosed = true
            isCancelled = true
            activeGeneration++
            cleanupRecognizer()
        }
    }

    private fun createRecognizer(): SpeechRecognizer? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        ) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
                .getOrNull()
                ?.let { return it }
        }
        return runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
    }

    private fun cleanupRecognizer() {
        runCatching {
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun createListener(generation: Long): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (!isCurrentGeneration(generation)) return
                _events.tryEmit(VoiceTranscriberEvent.Ready)
            }

            override fun onBeginningOfSpeech() {
                if (!isCurrentGeneration(generation)) return
                _events.tryEmit(VoiceTranscriberEvent.Listening)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Not recording audio
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // BillLocket MUST NOT record, save, cache, upload, or retain microphone audio.
            }

            override fun onEndOfSpeech() {
                if (!isCurrentGeneration(generation)) return
                _events.tryEmit(VoiceTranscriberEvent.Processing)
            }

            override fun onError(error: Int) {
                if (!isCurrentGeneration(generation)) return
                val mapped = mapSpeechRecognizerError(error)
                _events.tryEmit(VoiceTranscriberEvent.Error(mapped))
                cleanupRecognizer()
            }

            override fun onResults(results: Bundle?) {
                if (!isCurrentGeneration(generation)) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    _events.tryEmit(VoiceTranscriberEvent.FinalResult(text))
                } else {
                    _events.tryEmit(VoiceTranscriberEvent.Error(VoiceRecognitionError.NoSpeechMatch))
                }
                cleanupRecognizer()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isCurrentGeneration(generation)) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    _events.tryEmit(VoiceTranscriberEvent.PartialResult(text))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Ignored
            }
        }
    }

    private fun isCurrentGeneration(generation: Long): Boolean {
        return !isClosed && !isCancelled && generation == activeGeneration
    }

    private fun mapSpeechRecognizerError(error: Int): VoiceRecognitionError {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> VoiceRecognitionError.AudioError
            SpeechRecognizer.ERROR_CLIENT -> VoiceRecognitionError.ClientError
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceRecognitionError.PermissionDenied
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceRecognitionError.NetworkError
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceRecognitionError.NoSpeechMatch
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceRecognitionError.RecognizerBusy
            SpeechRecognizer.ERROR_SERVER -> VoiceRecognitionError.ServiceUnavailable
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> VoiceRecognitionError.ServiceUnavailable
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> VoiceRecognitionError.RecognizerBusy
            12 /* ERROR_LANGUAGE_NOT_SUPPORTED */,
            13 /* ERROR_LANGUAGE_UNAVAILABLE */ -> VoiceRecognitionError.ServiceUnavailable
            15 /* ERROR_CANNOT_CHECK_SUPPORT */ -> VoiceRecognitionError.ClientError
            else -> VoiceRecognitionError.Unknown
        }
    }
}
