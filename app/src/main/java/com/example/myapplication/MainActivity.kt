package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var hasPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        cameraExecutor = Executors.newSingleThreadExecutor()

        // existing permission logic (keeps UX you already had)
        hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                101
            )
        }

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // pass cameraExecutor so it's reused (and properly shutdown in Activity.onDestroy)
                    SmartDoorSystem(hasPermission = hasPermission, cameraExecutor = cameraExecutor)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
            // re-compose with permission true
            setContent {
                MyApplicationTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        SmartDoorSystem(hasPermission = true, cameraExecutor = cameraExecutor)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun SmartDoorSystem(hasPermission: Boolean, cameraExecutor: ExecutorService) {
    var doorStatus by remember { mutableStateOf("Door Closed") }
    // lensFacing is the state that toggles between front/back
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // stable PreviewView instance remembered across recompositions
    val previewView = remember { PreviewView(context) }
    // camera provider future (remembered)
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Door Opening System") },
                actions = {
                    TextButton(onClick = {
                        // toggle lensFacing -> DisposableEffect below will re-run and rebind
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
                            CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                    }) {
                        Text("Switch Camera", color = Color.White)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (!hasPermission) {
                Text("Camera permission is required", color = Color.Red)
                return@Box
            }

            // Preview (AndroidView)
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Bind / rebind camera when previewView or lensFacing changes,
            // and clean up detector on dispose.
            DisposableEffect(previewView, lensFacing) {
                var cameraProvider: ProcessCameraProvider? = null
                // Create single detector instance per binding lifecycle
                val detector = FaceDetection.getClient(
                    FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .enableTracking()
                        .build()
                )

                val bindRunnable = Runnable {
                    try {
                        cameraProvider = cameraProviderFuture.get()

                        // Preview
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        // ImageAnalysis
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().also { imageAnalysis ->
                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val image = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )
                                        detector.process(image)
                                            .addOnSuccessListener(ContextCompat.getMainExecutor(context)) { faces ->
                                                doorStatus = if (faces.isNotEmpty()) "Door Opened" else "Door Closed"
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("FaceDetect", "error", e)
                                                // keep closed on errors
                                                doorStatus = "Door Closed"
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                            }

                        // Unbind first then bind with new selector
                        cameraProvider?.unbindAll()

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        cameraProvider?.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            analysis
                        )
                    } catch (e: Exception) {
                        Log.e("CameraX", "binding failed", e)
                    }
                }

                cameraProviderFuture.addListener(bindRunnable, ContextCompat.getMainExecutor(context))

                onDispose {
                    // cleanup: unbind and close detector
                    try {
                        cameraProvider?.unbindAll()
                    } catch (_: Exception) { }
                    try {
                        detector.close()
                    } catch (_: Exception) { }
                }
            }

            // overlay status text (colored)
            Text(
                text = doorStatus,
                color = if (doorStatus == "Door Opened") Color(0xFF00FF04) else Color(0xFFFF002E),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            )
        }
    }
}
