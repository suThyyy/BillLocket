package com.billlocket.app.feature.transaction.voice

import kotlinx.coroutines.flow.Flow

/**
 * Narrow platform abstraction for speech-to-text note dictation.
 * Pure Kotlin contract; independent from Android framework or SpeechRecognizer types.
 */
interface VoiceNoteTranscriber {
    fun isAvailable(): Boolean
    fun start(localeTag: String)
    fun stop()
    fun cancel()
    fun close()
    val events: Flow<VoiceTranscriberEvent>
}

sealed interface VoiceTranscriberEvent {
    data object Ready : VoiceTranscriberEvent
    data object SpeechStarted : VoiceTranscriberEvent
    data class AudioLevel(val normalized: Float) : VoiceTranscriberEvent
    data class PartialResult(val text: String) : VoiceTranscriberEvent
    data object Processing : VoiceTranscriberEvent
    data class FinalResult(val text: String) : VoiceTranscriberEvent
    data class Error(val error: VoiceRecognitionError) : VoiceTranscriberEvent
    data object Cancelled : VoiceTranscriberEvent
}

sealed interface VoiceRecognitionError {
    data object PermissionDenied : VoiceRecognitionError
    data object PermissionPermanentlyDenied : VoiceRecognitionError
    data object NoSpeechMatch : VoiceRecognitionError
    data object RecognizerBusy : VoiceRecognitionError
    data object ServiceUnavailable : VoiceRecognitionError
    data object NetworkError : VoiceRecognitionError
    data object AudioError : VoiceRecognitionError
    data object ClientError : VoiceRecognitionError
    data object Unknown : VoiceRecognitionError
}
