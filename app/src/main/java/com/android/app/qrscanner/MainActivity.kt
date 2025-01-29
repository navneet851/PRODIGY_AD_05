package com.android.app.qrscanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.android.app.qrscanner.ui.theme.CameraRim
import com.android.app.qrscanner.ui.theme.QRScannerTheme
import com.android.app.qrscanner.ui.theme.Rim1
import com.android.app.qrscanner.ui.theme.Rim2
import com.android.app.qrscanner.ui.theme.Rim3
import com.android.app.qrscanner.ui.theme.Rim4
import com.android.app.qrscanner.ui.theme.Rim5
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                setContent {
                    QRScannerTheme {
                        var showDialog by remember {
                            mutableStateOf("")
                        }
                        Scaffold(modifier = Modifier.fillMaxSize()) { it ->
                            CameraPreview(cameraExecutor, it){ result ->
                                showDialog = result
                            }
                            if (showDialog != "") {
                                Dialog(
                                    onDismissRequest = {
                                        showDialog = ""

                                    }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "Result",
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                        Text(
                                            text = showDialog,
                                            color = Color.White,
                                            modifier = Modifier.padding(10.dp)
                                        )

                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(showDialog))
                                                startActivity(intent)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Rim1,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text("Browse")
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Permission Required", Toast.LENGTH_SHORT).show()
            }
        }
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun CameraPreview(cameraExecutor: ExecutorService, paddingValues: PaddingValues, onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                val imageAnalyzer = ImageAnalysis.Builder().build().also { it ->
                    it.setAnalyzer(cameraExecutor, { imageProxy ->
                        processImageProxy(imageProxy) { result ->
                            onScanned(result)
//                            Toast.makeText(ctx, result, Toast.LENGTH_SHORT).show()
                        }
                    })
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalyzer)
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Failed to bind camera use cases", Toast.LENGTH_SHORT).show()
                }

                previewView
            },
            modifier = Modifier
                .fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().border(15.dp, Rim5, RoundedCornerShape(25.dp))) {}
        Box(modifier = Modifier.fillMaxSize().padding(5.dp).border(6.dp, Color.White, RoundedCornerShape(31.dp))) {}
        Box(modifier = Modifier.fillMaxSize().padding(10.dp).border(6.dp, Rim3, RoundedCornerShape(25.dp))) {}
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(imageProxy: ImageProxy, onSuccess: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    // Handle the scanned barcode
                    val rawValue = barcode.rawValue
                    if (rawValue != null)
                        onSuccess(rawValue)
                    Log.d("BarcodeScanner", "Scanned barcode: $rawValue")
                    // Do something with the QR code value
                }
            }
            .addOnFailureListener {
                // Handle the error
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}