package com.pallu.tryon.ui.kiosk

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.ui.common.BigButton
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.PhotoCamera
import com.pallu.tryon.ui.common.PhotoCameraView
import com.pallu.tryon.ui.common.readImageAsJpeg
import com.pallu.tryon.ui.common.rememberCameraPermission
import com.pallu.tryon.ui.theme.Mirror
import com.pallu.tryon.ui.theme.ZariLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun CaptureScreen(vm: KioskViewModel, onBack: () -> Unit, onUploaded: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val gift = state.mode == TryOnMode.GIFT

    Box(Modifier.fillMaxSize().background(Mirror)) {
        when {
            state.photoPreview != null -> Review(vm, state, onRetake = { vm.setPhoto(null) }, onUse = { vm.upload(onUploaded) })
            gift -> PickPhoto(vm, onBack)
            else -> LiveCamera(vm, onBack)
        }
    }
}

@Composable
private fun LiveCamera(vm: KioskViewModel, onBack: () -> Unit) {
    val hasCamera = rememberCameraPermission()
    val camera = remember { PhotoCamera() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    val scope = rememberCoroutineScope()
    var countdown by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            if (countdown == 1) {
                countdown = 0
                scope.launch {
                    runCatching { camera.takeJpeg(executor) }
                        .onSuccess { vm.setPhoto(it) }
                        .onFailure { error = "The camera didn't take the photo. Please try again." }
                }
            } else {
                countdown -= 1
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (hasCamera) {
            // PreviewView already mirrors the front camera like a real mirror; the saved photo is not mirrored.
            PhotoCameraView(Modifier.fillMaxSize(), camera, useFrontCamera = true)
            BodyGuide(Modifier.fillMaxSize())
        } else {
            Text(
                "Allow camera access so the mirror can take your photo.",
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                textAlign = TextAlign.Center,
            )
        }
        if (countdown > 0) {
            Text(
                "$countdown",
                fontSize = 180.sp,
                color = Color.White,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Mirror.copy(alpha = 0.72f)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Step back until your whole body fits the outline", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                "Stand straight, arms slightly away from your body, facing the mirror.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Mirror.copy(alpha = 0.72f)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ErrorNote(error, Modifier.widthIn(max = 560.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back", style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.padding(12.dp))
                BigButton(
                    text = if (countdown > 0) "Hold still…" else "Take my photo in 3 seconds",
                    enabled = hasCamera && countdown == 0,
                    onClick = { error = null; countdown = 3 },
                    modifier = Modifier.widthIn(min = 300.dp),
                )
            }
        }
    }
}

/** Dashed head-and-body outline so customers frame themselves full length. */
@Composable
private fun BodyGuide(modifier: Modifier) {
    Canvas(modifier) {
        val h = size.height * 0.70f
        val w = h * 0.36f
        val top = size.height * 0.17f
        val cx = size.width / 2
        val stroke = Stroke(width = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 18f)))
        val head = h * 0.13f
        drawOval(ZariLight, topLeft = Offset(cx - head * 0.4f, top), size = Size(head * 0.8f, head), style = stroke)
        drawRoundRect(
            ZariLight,
            topLeft = Offset(cx - w / 2, top + head * 1.08f),
            size = Size(w, h - head * 1.08f),
            cornerRadius = CornerRadius(w * 0.3f, w * 0.3f),
            style = stroke,
        )
    }
}

@Composable
private fun PickPhoto(vm: KioskViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { readImageAsJpeg(context, uri) }
                .onSuccess { vm.setPhoto(it) }
                .onFailure { error = it.message ?: "Couldn't open that photo." }
        }
    }
    LaunchedEffect(Unit) {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Text("Choose a photo of the person", style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
        Text(
            "A clear, well-lit photo where they're standing works best. A half-length photo also works.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 620.dp),
        )
        ErrorNote(error, Modifier.widthIn(max = 560.dp))
        BigButton("Choose photo", {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, Modifier.widthIn(min = 280.dp))
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun Review(vm: KioskViewModel, state: KioskState, onRetake: () -> Unit, onUse: () -> Unit) {
    val preview = state.photoPreview ?: return
    Box(Modifier.fillMaxSize()) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = "Your photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(bottom = 140.dp),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Mirror).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ErrorNote(state.error, Modifier.widthIn(max = 560.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onRetake, enabled = !state.uploading) {
                    Text(if (state.mode == TryOnMode.GIFT) "Choose another" else "Retake", style = MaterialTheme.typography.labelLarge)
                }
                BigButton(
                    text = if (state.uploading) "Sending…" else "Use this photo",
                    onClick = onUse,
                    enabled = !state.uploading,
                    modifier = Modifier.widthIn(min = 280.dp),
                )
                if (state.uploading) CircularProgressIndicator(color = ZariLight)
            }
        }
    }
}
