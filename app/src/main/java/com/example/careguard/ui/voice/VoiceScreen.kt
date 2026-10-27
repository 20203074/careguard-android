package com.example.careguard.ui.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val dangerPhrases = listOf("도와주세요", "도와 줘", "살려주세요", "살려 줘", "넘어졌어요", "낙상했어요", "위험해요")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    isSending: Boolean,
    onBack: () -> Unit,
    onConfirmDanger: (String) -> Unit
) {
    val context = LocalContext.current
    var transcript by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var dangerCandidate by remember { mutableStateOf<String?>(null) }
    val controller = remember(context) {
        VoiceRecognitionController(
            context = context,
            onPartial = { transcript = it },
            onFinal = { finalText ->
                transcript = finalText
                if (dangerPhrases.any { finalText.replace(" ", "").contains(it.replace(" ", "")) }) {
                    dangerCandidate = finalText
                }
            },
            onListeningChanged = { isListening = it },
            onError = { errorMessage = it }
        )
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start() else errorMessage = "마이크 권한이 없으면 음성 인식을 사용할 수 없습니다."
    }

    DisposableEffect(controller) { onDispose { controller.destroy() } }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("음성 도움 요청") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            }
        )
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("짧게 말하고 결과를 확인하세요.", style = MaterialTheme.typography.headlineSmall)
            Text("시스템 음성 인식 서비스는 기기 설정에 따라 음성을 원격 서버로 보낼 수 있습니다. 이 기능은 상시 감청 또는 백그라운드 인식에 사용하지 않습니다.")
            Button(
                onClick = {
                    if (isListening) controller.stop()
                    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(if (isListening) "인식 중지" else "음성 인식 시작")
            }
            Text(
                if (transcript.isBlank()) "아직 인식한 문장이 없습니다." else transcript,
                style = MaterialTheme.typography.titleMedium
            )
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("위험 문구가 최종 결과에 포함돼도 확인 버튼을 누르기 전에는 보호자에게 전송되지 않습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }

    dangerCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { dangerCandidate = null },
            title = { Text("위험 문구가 감지되었습니다") },
            text = { Text("‘$candidate’\n\n보호자에게 음성 도움 요청을 전송할까요?") },
            confirmButton = {
                Button(onClick = { dangerCandidate = null; onConfirmDanger(candidate) }, enabled = !isSending) { Text("보호자에게 전송") }
            },
            dismissButton = { OutlinedButton(onClick = { dangerCandidate = null }) { Text("전송하지 않음") } }
        )
    }
}

private class VoiceRecognitionController(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private val recognizer: SpeechRecognizer?

    init {
        recognizer = runCatching {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) return@runCatching null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }.getOrNull()
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                onListeningChanged(false)
                onError(errorText(error))
            }
            override fun onResults(results: android.os.Bundle?) {
                onListeningChanged(false)
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let(onFinal)
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
    }

    fun start() {
        onError("")
        val service = recognizer
        if (service == null) {
            onError("이 기기에서 음성 인식 서비스를 사용할 수 없습니다. SOS 버튼을 사용해 주세요.")
            return
        }
        service.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        })
        onListeningChanged(true)
    }

    fun stop() = recognizer?.stopListening()
    fun destroy() = recognizer?.destroy()

    private fun errorText(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성을 인식하지 못했습니다. 다시 시도해 주세요."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "음성 인식 네트워크 연결을 확인해 주세요."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "음성 인식기가 바쁩니다. 잠시 후 다시 시도해 주세요."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "한국어 음성 인식 모델을 사용할 수 없습니다."
        else -> "음성 인식 오류가 발생했습니다. 다시 시도해 주세요."
    }
}
