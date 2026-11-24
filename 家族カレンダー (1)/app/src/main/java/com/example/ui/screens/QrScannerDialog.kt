package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerDialog(
  onQrDetected: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { granted -> hasCameraPermission = granted },
  )

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = Color(0xFF1A1614),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
          CameraQrPreview(
            onQrDetected = onQrDetected,
            modifier = Modifier.fillMaxSize(),
          )
          // Viewfinder frame overlay
          Canvas(modifier = Modifier.fillMaxSize()) {
            val boxSize = size.minDimension * 0.64f
            val left = (size.width - boxSize) / 2f
            val top = (size.height - boxSize) / 2f
            drawRoundRect(
              color = Color(0xFFFDF9F3),
              topLeft = Offset(left, top),
              size = Size(boxSize, boxSize),
              cornerRadius = CornerRadius(24f, 24f),
              style = Stroke(width = 6f),
            )
          }
        } else {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(
              imageVector = Icons.Filled.CameraAlt,
              contentDescription = null,
              tint = Color(0xFFFDF9F3),
              modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = "QRコードを読み取るにはカメラの権限が必要です",
              style = MaterialTheme.typography.titleMedium,
              color = Color(0xFFFDF9F3),
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "権限を許可するか、前の画面から招待コード文字列を貼り付けて参加できます。",
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFFD5C8BA),
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
              onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
              modifier = Modifier.testTag("grant_camera_button"),
            ) {
              Text("カメラ権限を許可")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = onDismiss) {
              Text("前の画面に戻る", color = Color(0xFFFDF9F3))
            }
          }
        }

        // Top-left back bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .align(Alignment.TopCenter),
          horizontalArrangement = Arrangement.Start,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.testTag("close_qr_scanner_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "ホーム画面に戻る",
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("戻る")
          }
          Spacer(modifier = Modifier.width(10.dp))
          Surface(
            color = Color(0xAA2F2A26),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f, fill = false),
          ) {
            Text(
              text = "家族の招待QRコードを枠内に映してください",
              style = MaterialTheme.typography.bodyMedium,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CameraQrPreview(
  onQrDetected: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val detectedOnce = remember { AtomicBoolean(false) }
  val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
  val barcodeScanner = remember { BarcodeScanning.getClient() }

  DisposableEffect(Unit) {
    onDispose {
      analyzerExecutor.shutdown()
      barcodeScanner.close()
    }
  }

  AndroidView(
    factory = { ctx ->
      val previewView = PreviewView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
        )
      }
      val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
      cameraProviderFuture.addListener(
        {
          try {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
              it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
              .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
              .build()
              .also { imageAnalysis ->
                imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                  val mediaImage = imageProxy.image
                  if (mediaImage != null && !detectedOnce.get()) {
                    val inputImage = InputImage.fromMediaImage(
                      mediaImage,
                      imageProxy.imageInfo.rotationDegrees,
                    )
                    barcodeScanner.process(inputImage)
                      .addOnSuccessListener { barcodes ->
                        val qr = barcodes.firstOrNull {
                          it.format == Barcode.FORMAT_QR_CODE && !it.rawValue.isNullOrBlank()
                        }
                        val value = qr?.rawValue
                        if (!value.isNullOrBlank() && detectedOnce.compareAndSet(false, true)) {
                          previewView.post { onQrDetected(value) }
                        }
                      }
                      .addOnCompleteListener {
                        imageProxy.close()
                      }
                  } else {
                    imageProxy.close()
                  }
                }
              }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
              lifecycleOwner,
              CameraSelector.DEFAULT_BACK_CAMERA,
              preview,
              analysis,
            )
          } catch (e: Exception) {
            Log.e("QrScanner", "CameraX bind failed", e)
          }
        },
        ContextCompat.getMainExecutor(ctx),
      )
      previewView
    },
    modifier = modifier,
  )
}
