package com.ratig.app.feature.identification

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoCameraBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/** Barcode symbologies the RATIG scanner understands (configurable, documented). */
enum class ScannerFormat(val mlkitFormat: Int) {
    QR_CODE(Barcode.FORMAT_QR_CODE),
    CODE_128(Barcode.FORMAT_CODE_128),
}

private enum class CameraPermission { GRANTED, DENIED }

/**
 * Full-screen CameraX + ML Kit barcode scanner.
 *
 * Behaviour (CONTRACT-2 §Identification):
 *  - the CAMERA runtime permission is requested ONLY when this screen is open,
 *    never at app start;
 *  - denied/unavailable camera shows a clear Indonesian message and manual NIK
 *    input on the identification screen keeps working;
 *  - duplicate detections of the same value are ignored for a cooldown window;
 *  - a start/stop button freezes and resumes the camera without leaving.
 *
 * The scanner NEVER interprets a scan as authentication; identification logic
 * (QR prefix check, NIK validation, lookup) lives in IdentificationViewModel.
 */
@Composable
fun ScannerScreen(
    formats: List<ScannerFormat>,
    onDetected: (value: String, isQr: Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var permission by remember {
        mutableStateOf(
            if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                CameraPermission.GRANTED
            } else {
                CameraPermission.DENIED
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permission = if (granted) CameraPermission.GRANTED else CameraPermission.DENIED
    }
    LaunchedEffect(Unit) {
        if (permission == CameraPermission.DENIED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color.Black) {
        when (permission) {
            CameraPermission.DENIED -> PermissionDeniedContent(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onClose = onClose,
            )

            CameraPermission.GRANTED -> CameraContent(
                formats = formats,
                onDetected = onDetected,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onRequest: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.NoPhotography,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Izin kamera ditolak",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Aktifkan izin kamera untuk memindai kode. " +
                "Anda tetap dapat memasukkan NIK secara manual.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Beri Izin Kamera") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onClose) { Text("Tutup") }
    }
}

@Composable
private fun CameraContent(
    formats: List<ScannerFormat>,
    onDetected: (value: String, isQr: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var cameraRunning by remember { mutableStateOf(true) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // Duplicate-detection guard: same value within the cooldown is ignored.
    var lastValue by remember { mutableStateOf<String?>(null) }
    var lastAtMs by remember { mutableLongStateOf(0L) }

    val acceptedFormats = remember(formats) { formats.map { it.mlkitFormat } }
    val scanner = remember(formats) {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                acceptedFormats.first(),
                *acceptedFormats.drop(1).toIntArray(),
            )
            .build()
        BarcodeScanning.getClient(options)
    }
    DisposableEffect(formats) {
        onDispose { scanner.close() }
    }

    val handleBarcodes: (List<Barcode>) -> Unit = { barcodes ->
        for (barcode in barcodes) {
            val value = barcode.rawValue ?: continue
            if (barcode.format !in acceptedFormats) continue
            val now = SystemClock.elapsedRealtime()
            if (value == lastValue && now - lastAtMs < DUPLICATE_COOLDOWN_MS) continue
            lastValue = value
            lastAtMs = now
            onDetected(value, barcode.format == Barcode.FORMAT_QR_CODE)
            break
        }
    }

    DisposableEffect(cameraRunning) {
        if (!cameraRunning) return@DisposableEffect onDispose { }
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var provider: ProcessCameraProvider? = null
        future.addListener(
            {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor, BarcodeAnalyzer(scanner, handleBarcodes))
                        }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (e: Exception) {
                    cameraError = "Kamera tidak tersedia atau sedang dipakai aplikasi lain."
                }
            },
            executor,
        )
        onDispose {
            runCatching { provider?.unbindAll() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (cameraRunning && cameraError == null) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            // Aim window: color never the only cue (border + label below).
            Box(
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 300.dp)
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1.6f)
                    .border(3.dp, Color.White, MaterialTheme.shapes.medium),
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.PhotoCameraBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    cameraError ?: "Kamera dihentikan.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Anda tetap dapat memasukkan NIK secara manual.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
                if (cameraError != null) {
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onClose) { Text("Kembali") }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Tutup pemindai",
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (formats.singleOrNull() == ScannerFormat.QR_CODE) {
                    "Pindai QR pekerja"
                } else {
                    "Pindai barcode (Code 128)"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { cameraRunning = !cameraRunning },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Icon(
                    Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (cameraRunning) "Hentikan Kamera" else "Mulai Kamera")
            }
        }

        Text(
            if (formats.singleOrNull() == ScannerFormat.QR_CODE) {
                "Arahkan kamera ke kode QR pada kartu pekerja"
            } else {
                "Arahkan kamera ke barcode Code 128 pada kartu pekerja"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        )
    }
}

/** Delivers frames to ML Kit; always closes the [ImageProxy]. */
private class BarcodeAnalyzer(
    private val scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    private val onBarcodes: (List<Barcode>) -> Unit,
) : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { onBarcodes(it) }
            .addOnFailureListener { /* frame skipped; next frame retries */ }
            .addOnCompleteListener { imageProxy.close() }
    }
}

private const val DUPLICATE_COOLDOWN_MS = 2_000L
