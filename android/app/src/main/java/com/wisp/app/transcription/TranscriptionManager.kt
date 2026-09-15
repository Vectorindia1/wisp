package com.wisp.app.transcription

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Feeds the rolling context buffer from mic input -- the Android
 * counterpart to the desktop app's backend/transcription/whisper_stream.py,
 * but deliberately NOT a whisper.cpp port. See docs/progress.md (session
 * 6) for the full reasoning; short version: compiling whisper.cpp via the
 * NDK blind, with zero emulator/device access to verify the native build
 * actually links and runs, was judged too risky for a v1 -- a broken JNI
 * bridge fails in ways that are extremely hard to diagnose without a
 * device in hand. Android's SpeechRecognizer needs no native toolchain,
 * has a real on-device mode on API 31+ (isOnDeviceRecognitionAvailable),
 * and gets the same end result (a live rolling transcript) through a
 * fully-supported platform API. Revisit whisper.cpp once this ships and
 * someone can test on real hardware.
 *
 * SpeechRecognizer is built for discrete utterances (start -> result ->
 * stop), not continuous dictation -- this fakes continuity by immediately
 * restarting listening after every result or recoverable error, same
 * pattern most "always listening" Android apps use.
 *
 * Must be constructed and used from the main thread (SpeechRecognizer
 * requirement); OverlayService does this via a Handler(Looper.getMainLooper()).
 */
class TranscriptionManager(
    private val context: Context,
    private val buffer: RollingTranscriptBuffer,
) {
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            android.util.Log.w(TAG, "SpeechRecognizer not available on this device -- continuing without audio context")
            return
        }
        running = true
        mainHandler.post { createAndStartRecognizer() }
    }

    fun stop() {
        running = false
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
    }

    private fun createAndStartRecognizer() {
        if (!running) return
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    buffer.add(text)
                    android.util.Log.d(TAG, "[transcript] $text")
                }
                restart()
            }

            override fun onError(error: Int) {
                // ERROR_NO_MATCH / ERROR_SPEECH_TIMEOUT fire constantly during
                // normal silence -- not a real failure, just restart and keep
                // listening. Anything else still restarts (best-effort, matches
                // the desktop transcription loop's "never fatal" posture) but is
                // logged in case it's a persistent problem (e.g. no permission).
                android.util.Log.d(TAG, "SpeechRecognizer error: $error")
                restart()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        r.startListening(intent)
    }

    private fun restart() {
        recognizer?.destroy()
        recognizer = null
        if (running) mainHandler.post { createAndStartRecognizer() }
    }

    companion object {
        private const val TAG = "TranscriptionManager"
    }
}
