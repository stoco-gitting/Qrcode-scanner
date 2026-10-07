package com.stoco.qrscanner.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.stoco.qrscanner.data.ParseResult
import com.stoco.qrscanner.data.QrParser
import com.stoco.qrscanner.data.Sensor
import com.stoco.qrscanner.data.SensorDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private enum class Kind { NEW, DUPLICATE, INVALID }
private data class Feedback(val kind: Kind, val title: String, val detail: String, val id: Long = System.nanoTime())

@Composable
fun ScannerScreen(dao: SensorDao, total: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera permission is required to scan.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Grant permission") }
        }
        return
    }

    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var sessionCount by remember { mutableIntStateOf(0) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    // Debounce: ignore the same raw value for a few seconds, and any value while a result is being handled.
    val lastSeen = remember { mutableMapOf<String, Long>() }
    var busy by remember { mutableStateOf(false) }

    val onCode: (String) -> Unit = handler@{ raw ->
        val now = System.currentTimeMillis()
        if (busy) return@handler
        if ((lastSeen[raw] ?: 0L) > now - 3000) return@handler
        lastSeen[raw] = now
        busy = true
        scope.launch {
            val fb = when (val r = QrParser.parse(raw)) {
                is ParseResult.Invalid -> {
                    tone.startTone(ToneGenerator.TONE_PROP_NACK, 200)
                    Feedback(Kind.INVALID, "Invalid QR: ${r.reason}", raw.take(80))
                }
                is ParseResult.Ok -> {
                    val id = dao.insert(Sensor(pn = r.pn, devEui = r.devEui, appEui = r.appEui, appKey = r.appKey))
                    if (id == -1L) {
                        tone.startTone(ToneGenerator.TONE_PROP_NACK, 150)
                        vibrate(context, 300)
                        Feedback(Kind.DUPLICATE, "Already scanned", "${r.pn}  •  ${r.devEui}")
                    } else {
                        sessionCount++
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                        vibrate(context, 80)
                        Feedback(Kind.NEW, "Added ${r.pn}", "DEVEUI ${r.devEui}")
                    }
                }
            }
            feedback = fb
            delay(700)
            busy = false
        }
    }

    LaunchedEffect(feedback?.id) {
        if (feedback != null) { delay(2500); feedback = null }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(onCode = onCode, onCamera = { camera = it })

        // Frame
        val frameColor = when (feedback?.kind) {
            Kind.NEW -> Color(0xFF2E7D32); Kind.DUPLICATE -> Color(0xFFF9A825)
            Kind.INVALID -> Color(0xFFC62828); null -> Color.White.copy(alpha = 0.7f)
        }
        Box(Modifier.align(Alignment.Center).size(260.dp).border(4.dp, frameColor, RoundedCornerShape(16.dp)))

        // Counters
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.6f)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Total: $total", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("This session: $sessionCount", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
            FilledIconButton(onClick = {
                torch = !torch
                camera?.cameraControl?.enableTorch(torch)
            }) { Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff, "Torch") }
        }

        feedback?.let { fb ->
            val bg = when (fb.kind) {
                Kind.NEW -> Color(0xFF2E7D32); Kind.DUPLICATE -> Color(0xFFF9A825); Kind.INVALID -> Color(0xFFC62828)
            }
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(12.dp), color = bg) {
                Column(Modifier.padding(16.dp)) {
                    Text(fb.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(fb.detail, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit, onCamera: (Camera) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    DisposableEffect(Unit) {
        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        val view = PreviewView(ctx)
        val providerFuture = ProcessCameraProvider.getInstance(ctx)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val main = ContextCompat.getMainExecutor(ctx)
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) { proxy.close(); return@setAnalyzer }
                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { codes ->
                        codes.firstNotNullOfOrNull { it.rawValue }?.let { raw -> main.execute { currentOnCode(raw) } }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            provider.unbindAll()
            val cam = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            onCamera(cam)
        }, ContextCompat.getMainExecutor(ctx))
        view
    })
}

private fun vibrate(context: Context, ms: Long) {
    val v: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        v.vibrate(ms)
    }
}
