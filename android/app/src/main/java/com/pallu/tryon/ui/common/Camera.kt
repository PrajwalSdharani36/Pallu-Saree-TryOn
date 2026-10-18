package com.pallu.tryon.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "PalluCamera"

/** Asks for the camera permission once; returns whether it is granted. */
@Composable
fun rememberCameraPermission(): Boolean {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    return granted
}

enum class ScanKind { PRODUCT_BARCODES, QR }

/**
 * Live camera preview that reports barcodes / QR codes.
 * [onCode] fires for every detection; callers de-duplicate.
 */
@Composable
fun BarcodeScannerView(
    modifier: Modifier,
    kind: ScanKind,
    useFrontCamera: Boolean = false,
    torch: Boolean = false,
    onCode: (String) -> Unit,
) {
    val context = LocalContext.current
    var boundCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    LaunchedEffect(torch, boundCamera) {
        boundCamera?.let { cam -> if (cam.cameraInfo.hasFlashUnit()) cam.cameraControl.enableTorch(torch) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCode by rememberUpdatedState(onCode)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner: BarcodeScanner = remember(kind) {
        val formats = when (kind) {
            ScanKind.QR -> intArrayOf()
            ScanKind.PRODUCT_BARCODES -> intArrayOf(
                Barcode.FORMAT_CODE_39, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_ITF, Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_CODABAR, Barcode.FORMAT_CODE_93,
            )
        }
        val first = if (kind == ScanKind.QR) Barcode.FORMAT_QR_CODE else Barcode.FORMAT_CODE_128
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(first, *formats).build())
    }
    // TextureView-based preview: stays inside its (clipped, rounded) window. The default SurfaceView
    // can spill over the header and text fields around a small scanner window on some devices.
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner, useFrontCamera, kind) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val p = providerFuture.get()
            provider = p
            val selector = pickCamera(p, useFrontCamera)
            mirrorIfExternal(previewView, selector, wantMirror = useFrontCamera)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, CodeAnalyzer(scanner) { code -> latestOnCode(code) }) }
            try {
                p.unbindAll()
                boundCamera = p.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            boundCamera = null
            provider?.unbindAll()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Any camera that isn't the rear one: on a big kiosk this is usually a USB webcam. */
private val NOT_BACK: CameraSelector = CameraSelector.Builder()
    .addCameraFilter { infos -> infos.filter { runCatching { it.lensFacing }.getOrNull() != CameraSelector.LENS_FACING_BACK } }
    .build()

/** Whatever camera exists (last resort). */
private val ANY_CAMERA: CameraSelector = CameraSelector.Builder().addCameraFilter { infos -> infos.take(1) }.build()

/**
 * Tablets and phones have front/back cameras; big Android kiosks often only have a USB webcam,
 * which CameraX reports as an "external" camera. Prefer the requested side, then a USB camera,
 * then anything at all, so the mirror never shows a black screen.
 */
internal fun pickCamera(p: ProcessCameraProvider, front: Boolean): CameraSelector {
    fun has(s: CameraSelector) = runCatching { p.hasCamera(s) }.getOrDefault(false)
    val order = if (front) {
        listOf(CameraSelector.DEFAULT_FRONT_CAMERA, NOT_BACK, CameraSelector.DEFAULT_BACK_CAMERA, ANY_CAMERA)
    } else {
        listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA, ANY_CAMERA)
    }
    return order.firstOrNull { has(it) } ?: ANY_CAMERA
}

/**
 * PreviewView mirrors the built-in front camera by itself. A USB camera facing the customer
 * is not mirrored, so flip the preview (needs the TextureView-based mode to apply).
 */
private fun mirrorIfExternal(view: PreviewView, selector: CameraSelector, wantMirror: Boolean) {
    val builtIn = selector == CameraSelector.DEFAULT_FRONT_CAMERA || selector == CameraSelector.DEFAULT_BACK_CAMERA
    if (wantMirror && !builtIn) {
        view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE // scaleX needs TextureView
        view.scaleX = -1f
    } else {
        view.scaleX = 1f
    }
}

private class CodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onCode: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
            .addOnCompleteListener { imageProxy.close() }
    }
}

/** Handle the kiosk screen uses to take the customer's photo. */
class PhotoCamera {
    internal var capture: ImageCapture? = null
    internal var isFront: Boolean = true

    /** Returns an upright JPEG (not mirrored: the customer sees themselves as others do). */
    suspend fun takeJpeg(executor: ExecutorService): ByteArray {
        val c = capture ?: error("Camera not ready yet")
        val proxy = suspendCancellableCoroutine<ImageProxy> { cont ->
            c.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) = cont.resume(image)
                override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
            })
        }
        return proxy.use { img ->
            val bmp = img.toBitmap()
            val rotated = rotate(bmp, img.imageInfo.rotationDegrees)
            jpeg(downscale(rotated, 1600))
        }
    }
}

@Composable
fun PhotoCameraView(modifier: Modifier, camera: PhotoCamera, useFrontCamera: Boolean = true) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    DisposableEffect(lifecycleOwner, useFrontCamera) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val selector = pickCamera(p, useFrontCamera)
            mirrorIfExternal(previewView, selector, wantMirror = useFrontCamera)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            try {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                camera.capture = capture
                camera.isFront = selector == CameraSelector.DEFAULT_FRONT_CAMERA
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            camera.capture = null
            provider?.unbindAll()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

// ---------- bitmap helpers ----------
fun rotate(src: Bitmap, degrees: Int): Bitmap {
    if (degrees % 360 == 0) return src
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

fun downscale(src: Bitmap, maxSide: Int): Bitmap {
    val longest = maxOf(src.width, src.height)
    if (longest <= maxSide) return src
    val scale = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
}

fun jpeg(bmp: Bitmap, quality: Int = 90): ByteArray =
    ByteArrayOutputStream().use { out ->
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

/**
 * Reads a gallery photo as an upright JPEG of sensible size. Phone photos store "which way is up"
 * in EXIF; re-encoding loses it, so the rotation is applied to the pixels first.
 */
fun readImageAsJpeg(context: Context, uri: android.net.Uri): ByteArray {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Couldn't open that photo")
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) error("That file isn't a photo")
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
    val bmp = android.graphics.BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: error("That file isn't a photo")
    val degrees = runCatching {
        androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes)).rotationDegrees
    }.getOrDefault(0)
    return jpeg(downscale(rotate(bmp, degrees), 1600))
}
