package com.example.careguard.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    isUploading: Boolean,
    onBack: () -> Unit,
    onUpload: (File, List<String>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val controller = remember(context) { EvidenceCameraController(context.applicationContext) }
    val labels by controller.labels.collectAsStateWithLifecycle()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true
        hasMicPermission = result[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }
    LaunchedEffect(hasCameraPermission, previewView, lifecycleOwner) {
        if (hasCameraPermission && previewView != null) {
            controller.bind(lifecycleOwner, previewView!!)
        }
    }
    DisposableEffect(controller) { onDispose { controller.release() } }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("현장 영상 전송") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            }
        )
        if (!hasCameraPermission) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("카메라 권한이 있어야 현장 영상을 녹화할 수 있습니다.")
                Button(onClick = { permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) {
                    Text("카메라 권한 허용")
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { previewContext ->
                        PreviewView(previewContext).also { previewView = it }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("화면 내 감지 요약", style = MaterialTheme.typography.labelLarge)
                        Text(if (labels.isEmpty()) "아직 객체 분류 결과가 없습니다." else labels.joinToString(", "))
                        Text("분류 결과는 전송 메타데이터이며, 낙상 판정은 라즈베리 파이에서 수행합니다.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        if (isRecording) {
                            controller.stopRecording()
                        } else {
                            controller.startRecording(
                                enableAudio = hasMicPermission,
                                onStarted = { isRecording = true },
                                onFinalized = { file ->
                                    isRecording = false
                                    onUpload(file, labels)
                                },
                                onError = { message ->
                                    isRecording = false
                                    cameraError = message
                                }
                            )
                        }
                    },
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null)
                    Text(if (isRecording) " 녹화 중지 후 전송" else " 영상 녹화 시작")
                }
                if (!hasMicPermission) {
                    OutlinedButton(onClick = { permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) {
                        Text("무음 녹화 중 — 마이크 권한 허용")
                    }
                }
                if (isUploading) Text("암호화된 서버 저장소로 영상을 전송하고 있습니다.")
            }
        }
    }
}

private class EvidenceCameraController(private val context: Context) {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
    )
    private val isAnalyzing = AtomicBoolean(false)
    private val _labels = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val labels: kotlinx.coroutines.flow.StateFlow<List<String>> = _labels

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                val capture = VideoCapture.withOutput(recorder)
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val image = imageProxy.image
                    if (image == null || !isAnalyzing.compareAndSet(false, true)) {
                        imageProxy.close()
                    } else {
                        val input = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
                        objectDetector.process(input)
                            .addOnSuccessListener { objects ->
                                _labels.value = objects
                                    .flatMap { it.labels }
                                    .map { it.text }
                                    .distinct()
                                    .take(5)
                            }
                            .addOnCompleteListener {
                                isAnalyzing.set(false)
                                imageProxy.close()
                            }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, capture)
                cameraProvider = provider
                videoCapture = capture
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording(
        enableAudio: Boolean,
        onStarted: () -> Unit,
        onFinalized: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = videoCapture ?: run {
            onError("카메라가 아직 준비되지 않았습니다.")
            return
        }
        val output = File(context.cacheDir, "evidence-${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(output)
            .setFileSizeLimit(25L * 1024L * 1024L)
            .build()
        var pending = capture.output.prepareRecording(context, options)
        if (enableAudio) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> onStarted()
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    if (event.hasError()) onError("영상 녹화에 실패했습니다. 오류 코드: ${event.error}")
                    else onFinalized(output)
                }
            }
        }
    }

    fun stopRecording() {
        recording?.stop()
    }

    fun release() {
        recording?.stop()
        recording = null
        cameraProvider?.unbindAll()
        objectDetector.close()
        cameraExecutor.shutdown()
    }
}
