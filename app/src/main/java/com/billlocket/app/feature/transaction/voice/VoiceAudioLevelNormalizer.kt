package com.billlocket.app.feature.transaction.voice

/**
 * Converts SpeechRecognizer RMS callbacks into a stable UI-only 0..1 level.
 * It never receives or retains microphone buffers.
 */
internal class VoiceAudioLevelNormalizer(
    private val nowMillis: () -> Long,
) {
    private var smoothedLevel = 0f
    private var lastEmissionMillis: Long? = null

    fun sample(rmsDb: Float): Float? {
        if (!rmsDb.isFinite()) return null

        val now = nowMillis()
        val lastEmission = lastEmissionMillis
        if (lastEmission != null && now >= lastEmission && now - lastEmission < EMISSION_INTERVAL_MILLIS) {
            return null
        }

        val raw = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
        smoothedLevel = (smoothedLevel * PREVIOUS_LEVEL_WEIGHT + raw * NEW_LEVEL_WEIGHT)
            .coerceIn(0f, 1f)
        lastEmissionMillis = now
        return smoothedLevel
    }

    fun reset() {
        smoothedLevel = 0f
        lastEmissionMillis = null
    }

    companion object {
        const val EMISSION_INTERVAL_MILLIS = 50L
        private const val PREVIOUS_LEVEL_WEIGHT = 0.72f
        private const val NEW_LEVEL_WEIGHT = 0.28f
    }
}
