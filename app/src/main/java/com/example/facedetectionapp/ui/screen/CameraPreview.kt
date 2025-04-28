package com.example.facedetectionapp.ui.screen

import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.Manifest
import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.facedetectionapp.R
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    detector: FaceDetector,
    imageProxy: ImageProxy,
    onFaceDetected: (Boolean) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { faces ->
                onFaceDetected(faces.isNotEmpty())
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                onFaceDetected(false)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}


@SuppressLint("ResourceAsColor")
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    var faceDetected by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }.also { previewView ->
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = CameraPreview.Builder().build()
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            preview.setSurfaceProvider(previewView.surfaceProvider)

                            val options = FaceDetectorOptions.Builder()
                                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                                .build()

                            val faceDetector = FaceDetection.getClient(options)

                            val imageAnalyzer = ImageAnalysis.Builder()
                                .build()
                                .also {
                                    it.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                        processImageProxy(faceDetector, imageProxy) { detected ->
                                            faceDetected = detected
                                        }
                                    }
                                }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalyzer
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (faceDetected) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp)
                        .background(
                            color = Color.Black,
                            shape = RoundedCornerShape(5.dp)
                        ),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        modifier = Modifier
                            .padding(10.dp),
                        text = stringResource(R.string.face_detected),
                        color = Color.White,
                    )
                }
            }
        }
    }

}

@SuppressLint("ResourceAsColor")
@Composable
fun FaceDetectedText() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .padding(top = 5.dp)
                .background(
                    color = Color.Black,
                    shape = RoundedCornerShape(5.dp)
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                modifier = Modifier
                    .padding(10.dp),
                text = stringResource(R.string.face_detected),
                color = Color.White,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FaceDetectedTextPreview() {
    FaceDetectedText()
}