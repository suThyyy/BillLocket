package com.billlocket.app.feature.transaction.voice

enum class VoiceListeningPhase { STARTING, READY, SPEECH_DETECTED }

sealed interface VoiceNoteState {
    data object Unavailable : VoiceNoteState
    data object Idle : VoiceNoteState
    data object RequestingPermission : VoiceNoteState
    data class Listening(
        val partialText: String = "",
        val phase: VoiceListeningPhase = VoiceListeningPhase.STARTING,
        val inputLevel: Float = 0f,
    ) : VoiceNoteState {
        init {
            require(inputLevel.isFinite() && inputLevel in 0f..1f) {
                "Voice input level must be finite and normalized"
            }
        }
    }
    data object Processing : VoiceNoteState
    data class Completed(val text: String) : VoiceNoteState
    data class Error(val error: VoiceRecognitionError) : VoiceNoteState
}
