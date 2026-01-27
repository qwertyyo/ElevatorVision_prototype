package com.example.elevatorvision


import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize

import androidx.camera.core.ImageAnalysis

import android.util.Log


import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.elevatorvision.ui.theme.ElevatorVisionTheme
import androidx.camera.core.ExperimentalGetImage
import com.example.elevatorvision.yolo.YoloDetector

@OptIn(ExperimentalGetImage::class)
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalGetImage::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ElevatorVisionTheme {
                CameraScreen()
            }
        }
    }
}

@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun CameraScreen() {
    var lastAnalyzeTime = 0L
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val yoloDetector = remember {
        YoloDetector(context)
    }
    val hasPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }




    // 🔹 앱 시작 시 YOLO 더미 추론 1회 실행
    LaunchedEffect(Unit) {
        yoloDetector.runDummyInference()
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

                imageAnalysis.setAnalyzer(
                    ContextCompat.getMainExecutor(ctx)
                ) { imageProxy ->

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastAnalyzeTime >= 500) { // 0.5초 = 2fps
                        lastAnalyzeTime = currentTime

                        val bitmap = ImageUtils.imageProxyToBitmap(imageProxy)
                        val rotated = ImageUtils.rotateAndResize(
                            bitmap,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        yoloDetector.detect(rotated)
                    }

                    imageProxy.close()
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}