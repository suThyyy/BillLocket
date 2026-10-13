package com.billlocket.app.feature.transaction.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pure Kotlin coordinator / state machine for note dictation.
 *
 * Guarantees:
 * - Only one active recognition session at any time.
 * - Rapid repeated taps are safely ignored while active.
 * - Cancel, Stop, and Close are strictly idempotent.
 * - Late callbacks from cancelled or closed sessions are safely ignored.
 * - Does not hold Android Context or Android framework objects.
 */
class VoiceNoteCoordinator(
    private val transcriber: VoiceNoteTranscriber,
    private val coroutineScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<VoiceNoteState>(
        if (transcriber.isAvailable()) VoiceNoteState.Idle else VoiceNoteState.Unavailable
    )
    val state: StateFlow<VoiceNoteState> = _state.asStateFlow()

    private var activeSession = false
    private var isClosed = false
    private var eventsJob: Job? = null
    private var processingTimeoutJob: Job? = null
    private var lastPartialText: String = ""

    init {
        eventsJob = coroutineScope.launch {
            transcriber.events.collect { event ->
                handleTranscriberEvent(event)
            }
        }
    }

    fun onMicTapped(
        hasPermission: Boolean,
        permanentlyDenied: Boolean,
        localeTag: String,
        onRequestPermission: () -> Unit,
    ) {
        if (isClosed) return
        val current = _state.value
        if (current is VoiceNoteState.Listening ||
            current is VoiceNoteState.Processing ||
            current is VoiceNoteState.RequestingPermission
        ) {
            return
        }
        if (!transcriber.isAvailable()) {
            _state.value = VoiceNoteState.Unavailable
            return
        }
        if (permanentlyDenied) {
            _state.value = VoiceNoteState.Error(VoiceRecognitionError.PermissionPermanentlyDenied)
            return
        }
        if (!hasPermission) {
            _state.value = VoiceNoteState.RequestingPermission
            onRequestPermission()
            return
        }
        startListening(localeTag)
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean, localeTag: String) {
        if (isClosed) return
        if (_state.value !is VoiceNoteState.RequestingPermission) return
        if (granted) {
            startListening(localeTag)
        } else if (permanentlyDenied) {
            _state.value = VoiceNoteState.Error(VoiceRecognitionError.PermissionPermanentlyDenied)
        } else {
            _state.value = VoiceNoteState.Error(VoiceRecognitionError.PermissionDenied)
        }
    }

    private fun startListening(localeTag: String) {
        if (activeSession || isClosed) return
        activeSession = true
        lastPartialText = ""
        processingTimeoutJob?.cancel()
        _state.value = VoiceNoteState.Listening(partialText = "")
        transcriber.start(localeTag)
    }

    fun stop() {
        if (isClosed || !activeSession) return
        _state.value = VoiceNoteState.Processing
        scheduleProcessingTimeout()
        transcriber.stop()
    }

    fun cancel() {
        if (isClosed || !activeSession) return
        activeSession = false
        lastPartialText = ""
        processingTimeoutJob?.cancel()
        transcriber.cancel()
        _state.value = if (transcriber.isAvailable()) VoiceNoteState.Idle else VoiceNoteState.Unavailable
    }

    fun dismissError() {
        if (isClosed) return
        if (_state.value is VoiceNoteState.Error) {
            _state.value = if (transcriber.isAvailable()) VoiceNoteState.Idle else VoiceNoteState.Unavailable
        }
    }

    fun dismissCompleted() {
        if (isClosed) return
        if (_state.value is VoiceNoteState.Completed) {
            _state.value = if (transcriber.isAvailable()) VoiceNoteState.Idle else VoiceNoteState.Unavailable
        }
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        activeSession = false
        lastPartialText = ""
        processingTimeoutJob?.cancel()
        eventsJob?.cancel()
        transcriber.close()
        _state.value = VoiceNoteState.Unavailable
    }

    private fun scheduleProcessingTimeout() {
        processingTimeoutJob?.cancel()
        processingTimeoutJob = coroutineScope.launch {
            delay(PROCESSING_TIMEOUT_MILLIS)
            if (activeSession && _state.value is VoiceNoteState.Processing) {
                activeSession = false
                transcriber.cancel()
                if (lastPartialText.isNotBlank()) {
                    _state.value = VoiceNoteState.Completed(lastPartialText)
                } else {
                    _state.value = VoiceNoteState.Error(VoiceRecognitionError.ServiceUnavailable)
                }
            }
        }
    }

    private fun handleTranscriberEvent(event: VoiceTranscriberEvent) {
        if (isClosed) return
        when (event) {
            VoiceTranscriberEvent.Ready -> {
                if (activeSession && _state.value is VoiceNoteState.Listening) {
                    val currentPartial = (_state.value as? VoiceNoteState.Listening)?.partialText.orEmpty()
                    _state.value = VoiceNoteState.Listening(partialText = currentPartial)
                }
            }
            VoiceTranscriberEvent.Listening -> {
                if (activeSession && _state.value is VoiceNoteState.Listening) {
                    val currentPartial = (_state.value as? VoiceNoteState.Listening)?.partialText.orEmpty()
                    _state.value = VoiceNoteState.Listening(partialText = currentPartial)
                }
            }
            is VoiceTranscriberEvent.PartialResult -> {
                if (activeSession && _state.value is VoiceNoteState.Listening) {
                    lastPartialText = event.text
                    _state.value = VoiceNoteState.Listening(partialText = event.text)
                }
            }
            VoiceTranscriberEvent.Processing -> {
                if (activeSession && _state.value is VoiceNoteState.Listening) {
                    _state.value = VoiceNoteState.Processing
                    scheduleProcessingTimeout()
                }
            }
            is VoiceTranscriberEvent.FinalResult -> {
                if (activeSession &&
                    (_state.value is VoiceNoteState.Listening || _state.value is VoiceNoteState.Processing)
                ) {
                    processingTimeoutJob?.cancel()
                    activeSession = false
                    _state.value = VoiceNoteState.Completed(event.text)
                }
            }
            is VoiceTranscriberEvent.Error -> {
                if (activeSession &&
                    (_state.value is VoiceNoteState.Listening || _state.value is VoiceNoteState.Processing)
                ) {
                    processingTimeoutJob?.cancel()
                    activeSession = false
                    _state.value = VoiceNoteState.Error(event.error)
                }
            }
            VoiceTranscriberEvent.Cancelled -> {
                // cancel() owns the synchronous state transition. Ignoring this acknowledgement
                // prevents a delayed cancellation callback from terminating a newer session.
            }
        }
    }

    companion object {
        const val PROCESSING_TIMEOUT_MILLIS = 8000L
    }
}
