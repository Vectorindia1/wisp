package com.wisp.app.transcription

/** Direct port of the desktop backend's context/buffer.py -- keeps the last
 * N seconds of transcribed speech, evicting anything older whenever a new
 * chunk arrives or the context is read. */
class RollingTranscriptBuffer(private val windowSeconds: Long = 120) {
    private data class Chunk(val text: String, val timestampMs: Long)

    private val chunks = ArrayDeque<Chunk>()

    @Synchronized
    fun add(text: String) {
        chunks.addLast(Chunk(text, System.currentTimeMillis()))
        evictOld()
    }

    @Synchronized
    fun getContext(): String {
        evictOld()
        return chunks.joinToString(" ") { it.text }
    }

    @Synchronized
    fun clear() = chunks.clear()

    private fun evictOld() {
        val cutoff = System.currentTimeMillis() - windowSeconds * 1000
        while (chunks.isNotEmpty() && chunks.first().timestampMs < cutoff) {
            chunks.removeFirst()
        }
    }
}
