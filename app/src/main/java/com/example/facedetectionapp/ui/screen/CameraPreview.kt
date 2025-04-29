package com.example.facedetectionapp.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.facedetectionapp.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

@SuppressLint("UnsafeOptInUsageError")
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
    var faces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var imageWidth by remember { mutableStateOf(1) }
    var imageHeight by remember { mutableStateOf(1) }
    var imageRotation by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Box(modifier = modifier.fillMaxSize()) {

            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }.also { previewView ->
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = androidx.camera.core.Preview.Builder().build()
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
                                        processImageProxy(
                                            detector = faceDetector,
                                            imageProxy = imageProxy,
                                            onFaceDetected = { faceDetected = it },
                                            onFacesDetected = { faces = it },
                                            onImageSizeDetected = { w, h ->
                                                imageWidth = w
                                                imageHeight = h
                                            },
                                            onRotationDetected = { rotation ->
                                                imageRotation = rotation
                                            }
                                        )
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

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val (trueWidth, trueHeight) = if (imageRotation == 90 || imageRotation == 270) {
                    imageHeight to imageWidth
                } else {
                    imageWidth to imageHeight
                }

                faces.forEach { face ->
                    val bounds = face.boundingBox

                    val leftPercent = bounds.left.toFloat() / trueWidth
                    val topPercent = bounds.top.toFloat() / trueHeight
                    val widthPercent = bounds.width().toFloat() / trueWidth
                    val heightPercent = bounds.height().toFloat() / trueHeight

                    val left = canvasWidth * leftPercent
                    val top = canvasHeight * topPercent
                    val width = canvasWidth * widthPercent
                    val height = canvasHeight * heightPercent

                    drawRect(
                        color = Color.Green,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 4f)
                    )
                }
            }

            if (faceDetected) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp)
                        .background(
                            color = Color.Green,
                            shape = RoundedCornerShape(5.dp)
                        ),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        modifier = Modifier.padding(10.dp),
                        text = stringResource(R.string.face_detected),
                        color = Color.White,
                    )
                }
            }
        }
    }
}


@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    detector: com.google.mlkit.vision.face.FaceDetector,
    imageProxy: ImageProxy,
    onFaceDetected: (Boolean) -> Unit,
    onFacesDetected: (List<Face>) -> Unit,
    onImageSizeDetected: (Int, Int) -> Unit,
    onRotationDetected: (Int) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val rotation = imageProxy.imageInfo.rotationDegrees
        onRotationDetected(rotation)
        onImageSizeDetected(imageProxy.width, imageProxy.height)

        val image = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(image)
            .addOnSuccessListener { faces ->
                onFaceDetected(faces.isNotEmpty())
                onFacesDetected(faces)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                onFaceDetected(false)
                onFacesDetected(emptyList())
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