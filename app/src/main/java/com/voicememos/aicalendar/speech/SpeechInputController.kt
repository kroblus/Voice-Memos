package com.voicememos.aicalendar.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechInputController(
    context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var recognitionActive: Boolean = false
    private var recognitionGeneration: Long = 0

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start() {
        if (!isAvailable) {
            onError("当前设备没有可用的语音识别服务，可以直接输入文字。")
            return
        }

        val generation = ++recognitionGeneration
        val speechRecognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext)
            .also { recognizer = it }

        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (generation != recognitionGeneration) return
                    recognitionActive = true
                    onListeningChanged(true)
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    reportTranscribing(generation)
                }

                override fun onError(error: Int) {
                    if (generation != recognitionGeneration) return
                    reportTranscribing(generation)
                    onError(error.toReadableMessage())
                }

                override fun onResults(results: Bundle?) {
                    if (generation != recognitionGeneration) return
                    reportTranscribing(generation)
                    results?.bestText()?.let(onFinalResult)
                        ?: onError("没有识别到语音，可以直接输入文字。")
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (generation != recognitionGeneration) return
                    partialResults?.bestText()?.let(onPartialResult)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )

        speechRecognizer.startListening(recognizerIntent())
    }

    fun stop() {
        recognizer?.stopListening()
        reportTranscribing(recognitionGeneration)
    }

    fun cancel() {
        recognitionGeneration += 1
        recognizer?.cancel()
        recognitionActive = false
    }

    fun destroy() {
        val wasActive = recognitionActive
        recognitionGeneration += 1
        recognizer?.destroy()
        recognizer = null
        recognitionActive = false
        if (wasActive) {
            onListeningChanged(false)
            onError("语音识别已中断，已保留当前文字，可以重试。")
        }
    }

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, SILENCE_TIMEOUT_MILLIS)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出一个或多个日程")
        }

    private fun Bundle.bestText(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun reportTranscribing(generation: Long) {
        if (generation != recognitionGeneration) return
        recognitionActive = false
        onListeningChanged(false)
    }

    private fun Int.toReadableMessage(): String =
        when (this) {
            SpeechRecognizer.ERROR_AUDIO -> "录音失败，请检查麦克风。"
            SpeechRecognizer.ERROR_CLIENT -> "语音识别被中断，可以再试一次。"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限才能语音输入。"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            -> "语音识别网络不可用，可以直接输入文字。"
            SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到明确内容，可以再说一次或直接输入。"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别正在忙，请稍后再试。"
            SpeechRecognizer.ERROR_SERVER -> "语音识别服务暂时不可用。"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到语音，可以直接输入文字。"
            else -> "语音识别失败，可以直接输入文字。"
        }

    private companion object {
        const val SILENCE_TIMEOUT_MILLIS = 3_000L
    }
}
