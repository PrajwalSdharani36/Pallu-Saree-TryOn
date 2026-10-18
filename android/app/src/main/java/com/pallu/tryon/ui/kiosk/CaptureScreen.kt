package com.pallu.tryon.ui.kiosk

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.PhotoCamera
import com.pallu.tryon.ui.common.PhotoCameraView
import com.pallu.tryon.ui.common.readImageAsJpeg
import com.pallu.tryon.ui.common.rememberCameraPermission
import com.pallu.tryon.ui.theme.GoldBright
import com.pallu.tryon.ui.theme.Kumkum
import com.pallu.tryon.ui.theme.Mirror
import com.pallu.tryon.ui.theme.MirrorDeep
import com.pallu.tryon.ui.theme.MirrorRaised
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.ZariLight
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Seconds between tapping the button and the photo: enough to step back from a tablet on a stand. */
private const val COUNTDOWN_SECONDS = 4

@Composable
fun CaptureScreen(vm: KioskViewModel, onBack: () -> Unit, onUploaded: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val gift = state.mode == TryOnMode.GIFT

    Box(Modifier.fillMaxSize().background(MirrorDeep)) {
        when {
            state.photoPreview != null -> Review(state, onRetake = { vm.setPhoto(null) }, onUse = { vm.upload(onUploaded) })
            gift -> KioskPage { PickPhoto(vm, onBack) }
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
    var countdown by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val flash = remember { Animatable(0f) }

    // ring empties smoothly across the three seconds
    val ring by animateFloatAsState(
        targetValue = if (countdown > 0) (countdown - 1) / COUNTDOWN_SECONDS.toFloat() else 1f,
        animationSpec = tween(if (countdown > 0) 1000 else 0, easing = LinearEasing),
        label = "countdown-ring",
    )

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            if (countdown == 1) {
                countdown = 0
                scope.launch {
                    flash.snapTo(1f)
                    launch { flash.animateTo(0f, tween(450)) }
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
                color = OnMirror,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                textAlign = TextAlign.Center,
            )
        }

        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(MirrorDeep.copy(alpha = 0.78f)).padding(horizontal = 40.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Fit your whole body inside the outline",
                fontFamily = Rozha, fontSize = 38.sp, lineHeight = 44.sp, color = OnMirror, textAlign = TextAlign.Center,
            )
            Muted("Stand straight, arms a little away from your sides, facing the mirror.", scale = 0.9f, align = TextAlign.Center)
        }

        if (countdown > 0) {
            Box(Modifier.align(Alignment.Center).size(250.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    drawCircle(MirrorDeep.copy(alpha = 0.55f))
                    drawCircle(Color.White.copy(alpha = 0.18f), style = Stroke(stroke))
                    drawArc(
                        GoldBright, startAngle = -90f, sweepAngle = 360f * ring, useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                AnimatedContent(
                    targetState = countdown,
                    transitionSpec = { (scaleIn(initialScale = 1.35f) + fadeIn()) togetherWith fadeOut() },
                    label = "countdown",
                ) { n ->
                    Text("$n", fontFamily = Rozha, fontSize = 160.sp, color = Color(0xFFFFF6E6))
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MirrorDeep.copy(alpha = 0.82f))
                .padding(horizontal = 40.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietButton("Back", onBack)
            Spacer(Modifier.weight(1f))
            if (countdown > 0) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(MirrorRaised)
                        .border(1.dp, ZariLight.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 30.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Kumkum))
                    Spacer(Modifier.size(12.dp))
                    Text("Hold still", style = MaterialTheme.typography.titleLarge, color = OnMirror)
                }
            } else {
                GoldButton(
                    "Take my photo in $COUNTDOWN_SECONDS seconds",
                    onClick = { error = null; countdown = COUNTDOWN_SECONDS },
                    icon = Icons.Filled.PhotoCamera,
                    enabled = hasCamera,
                )
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(64.dp))
        }
        ErrorNote(error, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp).widthIn(max = 560.dp))

        if (flash.value > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
        }
    }
}

/** Dashed head-and-body outline so customers frame themselves full length. */
@Composable
private fun BodyGuide(modifier: Modifier) {
    Canvas(modifier) {
        val h = size.height * 0.62f
        val w = h * 0.38f
        val top = size.height * 0.19f
        val cx = size.width / 2
        val stroke = Stroke(width = 4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(20.dp.toPx(), 14.dp.toPx())))
        val head = h * 0.14f
        drawOval(ZariLight, topLeft = Offset(cx - head * 0.42f, top), size = Size(head * 0.84f, head), style = stroke)
        drawRoundRect(
            ZariLight,
            topLeft = Offset(cx - w / 2, top + head * 1.1f),
            size = Size(w, h - head * 1.1f),
            cornerRadius = CornerRadius(w * 0.32f, w * 0.32f),
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
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("Choose a photo of them", fontFamily = Rozha, fontSize = 60.sp, lineHeight = 64.sp, color = OnMirror, textAlign = TextAlign.Center)
        Muted(
            "A clear, well-lit photo where they're standing works best. A half-length photo works too.",
            align = TextAlign.Center, modifier = Modifier.widthIn(max = 620.dp),
        )
        ErrorNote(error, Modifier.widthIn(max = 560.dp))
        GoldButton(
            "Choose photo",
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            icon = Icons.Filled.PhotoLibrary,
            modifier = Modifier.widthIn(min = 280.dp),
        )
        QuietButton("Back", onBack)
    }
}

@Composable
private fun Review(state: KioskState, onRetake: () -> Unit, onUse: () -> Unit) {
    val preview = state.photoPreview ?: return
    Box(Modifier.fillMaxSize().background(Mirror)) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = "Your photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(top = 40.dp, bottom = 150.dp, start = 40.dp, end = 40.dp)
                .clip(RoundedCornerShape(24.dp)),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 40.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorNote(state.error, Modifier.widthIn(max = 560.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlineButton(if (state.mode == TryOnMode.GIFT) "Choose another" else "Retake", onRetake, enabled = !state.uploading)
                GoldButton(
                    text = if (state.uploading) "Sending…" else "Use this photo",
                    onClick = onUse,
                    icon = Icons.Filled.CloudUpload,
                    enabled = !state.uploading,
                    modifier = Modifier.widthIn(min = 280.dp),
                )
                if (state.uploading) CircularProgressIndicator(color = ZariLight)
            }
        }
    }
}
