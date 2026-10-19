package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Manages voice interaction for the "Tendon" AI Robot Assistant:
 * 1. Speech-to-Text (listening to student voice instructions in real time)
 * 2. Text-to-Speech (replying in natural spoken voice form)
 */
class TendonVoiceManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingSpeechText: String? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceVoiceEnabled = MutableStateFlow(true)
    val isVoiceReplyEnabled: StateFlow<Boolean> = _voiceVoiceEnabled.asStateFlow()

    private val _statusText = MutableStateFlow("Tap Mic to Talk to Tendon")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                    tts?.setPitch(1.04f)
                    tts?.setSpeechRate(1.0f)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                            _statusText.value = "Tendon is speaking..."
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            _statusText.value = "Tap Mic to Talk to Tendon"
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            _statusText.value = "Tap Mic to Talk to Tendon"
                        }
                    })
                    isTtsReady = true
                    pendingSpeechText?.let { queued ->
                        pendingSpeechText = null
                        speak(queued)
                    }
                } else {
                    Log.w("TendonVoice", "TTS Initialization failed with status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e("TendonVoice", "Error initializing TTS", e)
        }
    }

    fun setVoiceReplyEnabled(enabled: Boolean) {
        _voiceVoiceEnabled.value = enabled
        if (!enabled) {
            stopSpeaking()
        }
    }

    fun toggleVoiceReply(): Boolean {
        val next = !_voiceVoiceEnabled.value
        setVoiceReplyEnabled(next)
        return next
    }

    /**
     * Converts markdown-formatted AI message into clean, natural speech for Tendon.
     */
    fun cleanTextForSpeech(rawText: String): String {
        return rawText
            // Remove markdown bold/italic/code symbols
            .replace("**", "")
            .replace("*", "")
            .replace("`", "")
            .replace("#", "")
            // Remove common emojis & bullet characters so TTS doesn't read emoji names awkwardly
            .replace(Regex("[✅📅⏰📖📌📊🏆⚠️ℹ️🗓️🍱🔬📚📝⚡🎉🤖🎙️🔊•▪✓]"), " ")
            // Expand common abbreviations for smoother speech
            .replace(Regex("\\bDr\\.", RegexOption.IGNORE_CASE), "Doctor")
            .replace(Regex("\\bProf\\.", RegexOption.IGNORE_CASE), "Professor")
            .replace(Regex("\\bDept\\.", RegexOption.IGNORE_CASE), "Department")
            .replace("->", " to ")
            .replace("→", " to ")
            // Replace multiple newlines with period pause
            .replace(Regex("\\n+"), ". ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\.\\s*\\.+"), ".")
            .trim()
    }

    /**
     * Speaks the given response aloud as Tendon.
     */
    fun speak(rawMessage: String) {
        if (!_voiceVoiceEnabled.value || rawMessage.isBlank()) return
        val speechText = cleanTextForSpeech(rawMessage)
        if (speechText.isBlank()) return

        if (!isTtsReady || tts == null) {
            pendingSpeechText = speechText
            return
        }

        mainHandler.post {
            try {
                val utteranceId = UUID.randomUUID().toString()
                _isSpeaking.value = true
                _statusText.value = "Tendon is speaking..."
                tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } catch (e: Exception) {
                Log.e("TendonVoice", "Error speaking TTS", e)
                _isSpeaking.value = false
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            try {
                if (tts?.isSpeaking == true) {
                    tts?.stop()
                }
            } catch (e: Exception) {
                Log.e("TendonVoice", "Error stopping TTS", e)
            } finally {
                _isSpeaking.value = false
                if (!_isListening.value) {
                    _statusText.value = "Tap Mic to Talk to Tendon"
                }
            }
        }
    }

    /**
     * Starts live speech recognition using Android's SpeechRecognizer.
     * Calls [onResult] when the student finishes speaking their sentence,
     * or [onFallbackRequired] if the device requires intent-based recognition.
     */
    fun startListening(
        onResult: (String) -> Unit,
        onFallbackRequired: () -> Unit
    ) {
        stopSpeaking()

        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onFallbackRequired()
                return@post
            }

            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _isListening.value = true
                            _partialTranscript.value = ""
                            _statusText.value = "Tendon is listening... Speak now"
                        }

                        override fun onBeginningOfSpeech() {
                            _isListening.value = true
                            _statusText.value = "Listening to you..."
                        }

                        override fun onRmsChanged(rmsdB: Float) {}

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _isListening.value = false
                            _statusText.value = "Tendon is processing..."
                        }

                        override fun onError(error: Int) {
                            _isListening.value = false
                            val partial = _partialTranscript.value.trim()
                            if (partial.isNotBlank()) {
                                _partialTranscript.value = ""
                                onResult(partial)
                                return
                            }
                            Log.w("TendonVoice", "SpeechRecognizer error code: $error")
                            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                                error == SpeechRecognizer.ERROR_CLIENT
                            ) {
                                _statusText.value = "Tap Mic to Talk to Tendon"
                            } else {
                                _statusText.value = "Didn't catch that. Tap Mic to try again."
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            _isListening.value = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val spoken = matches?.firstOrNull()?.trim().orEmpty()
                            _partialTranscript.value = ""
                            if (spoken.isNotBlank()) {
                                _statusText.value = "Tendon is thinking..."
                                onResult(spoken)
                            } else {
                                _statusText.value = "Tap Mic to Talk to Tendon"
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            if (partial.isNotBlank()) {
                                _partialTranscript.value = partial
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("TendonVoice", "Failed to start SpeechRecognizer, falling back to Intent", e)
                _isListening.value = false
                onFallbackRequired()
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e("TendonVoice", "Error stopping listener", e)
            } finally {
                _isListening.value = false
                _statusText.value = "Tap Mic to Talk to Tendon"
            }
        }
    }

    fun shutdown() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                tts?.stop()
                tts?.shutdown()
                tts = null
            } catch (e: Exception) {
                Log.e("TendonVoice", "Error shutting down TendonVoiceManager", e)
            }
        }
    }
}
