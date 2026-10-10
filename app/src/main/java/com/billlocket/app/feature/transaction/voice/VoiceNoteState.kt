package com.billlocket.app.feature.transaction.voice

sealed interface VoiceNoteState {
    data object Unavailable : VoiceNoteState
    data object Idle : VoiceNoteState
    data object RequestingPermission : VoiceNoteState
    data class Listening(val partialText: String = "") : VoiceNoteState
    data object Processing : VoiceNoteState
    data class Completed(val text: String) : VoiceNoteState
    data class Error(val error: VoiceRecognitionError) : VoiceNoteState
}
