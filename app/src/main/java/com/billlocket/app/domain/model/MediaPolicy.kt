package com.billlocket.app.domain.model

enum class MediaJournalState { PREPARED, FILE_READY, COMMITTED }

data class MediaJournalEntry(
    val operationId: String,
    val imageId: String,
    val canonicalUri: String,
    val checksumSha256: String,
    val state: MediaJournalState,
    val updatedAtEpochMillis: Long,
) {
    fun transitionTo(next: MediaJournalState, atEpochMillis: Long): MediaJournalEntry {
        val allowed =
            when (state) {
                MediaJournalState.PREPARED -> next == MediaJournalState.FILE_READY
                MediaJournalState.FILE_READY -> next == MediaJournalState.COMMITTED
                MediaJournalState.COMMITTED -> false
            }
        require(allowed) { "Invalid media journal transition: $state -> $next" }
        require(atEpochMillis >= updatedAtEpochMillis) { "Journal time must be monotonic" }
        return copy(state = next, updatedAtEpochMillis = atEpochMillis)
    }
}

data class CompressionPlan(val longEdgePx: Int, val jpegQuality: Int)

object MediaCompressionPolicy {
    const val PRIMARY_LONG_EDGE = 2560
    const val FALLBACK_LONG_EDGE = 2048
    const val PRIMARY_QUALITY = 88
    const val FALLBACK_QUALITY = 75
    const val MAX_OUTPUT_BYTES = 4L * 1024 * 1024

    fun firstPlan(sourceWidth: Int, sourceHeight: Int): CompressionPlan {
        require(sourceWidth > 0 && sourceHeight > 0)
        return CompressionPlan(minOf(maxOf(sourceWidth, sourceHeight), PRIMARY_LONG_EDGE), PRIMARY_QUALITY)
    }

    fun fallbackPlan(): CompressionPlan = CompressionPlan(PRIMARY_LONG_EDGE, FALLBACK_QUALITY)

    fun reducedPlan(): CompressionPlan = CompressionPlan(FALLBACK_LONG_EDGE, FALLBACK_QUALITY)
}

object OwnedMediaReference {
    const val AUTHORITY = "com.billlocket.app.media"
    private val idPattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")

    fun requireId(value: String): String {
        require(idPattern.matches(value)) { "Invalid media identifier" }
        require(value != "." && value != "..") { "Invalid media identifier" }
        return value
    }

    fun uri(imageId: String): String = "content://$AUTHORITY/images/${requireId(imageId)}"

    fun imageId(uri: String): String {
        val prefix = "content://$AUTHORITY/images/"
        require(uri.startsWith(prefix)) { "URI is not BillLocket-owned media" }
        val id = uri.removePrefix(prefix)
        require(!id.contains('/')) { "Invalid owned media URI" }
        return requireId(id)
    }
}
