package com.billlocket.app.feature.transaction.voice

object VoiceNoteTextMerger {
    const val MAX_NOTE_LENGTH = 500

    /**
     * Normalizes recognized text by trimming leading/trailing whitespace and collapsing
     * consecutive whitespace into a single space, while preserving normal Vietnamese and English text.
     */
    fun normalize(text: String): String {
        return text.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Merges recognized text into the current note according to the BL-046 specifications:
     * - If [recognizedText] is blank after normalization, returns [currentNote] untouched.
     * - If [currentNote] is empty or blank, inserts normalized recognized text (capped at [maxLimit]).
     * - If [currentNote] contains text, trims trailing whitespace from [currentNote] and appends
     *   the normalized recognized text with exactly one separating space.
     * - If the combined result exceeds [maxLimit] (default 500 characters), it is safely truncated to [maxLimit].
     */
    fun merge(currentNote: String, recognizedText: String, maxLimit: Int = MAX_NOTE_LENGTH): String {
        val normalizedSpoken = normalize(recognizedText)
        if (normalizedSpoken.isEmpty()) return currentNote

        val trimmedCurrent = currentNote.trimEnd()
        val merged = if (trimmedCurrent.isEmpty()) {
            normalizedSpoken
        } else {
            "$trimmedCurrent $normalizedSpoken"
        }

        return if (merged.length > maxLimit) {
            merged.take(maxLimit)
        } else {
            merged
        }
    }
}
