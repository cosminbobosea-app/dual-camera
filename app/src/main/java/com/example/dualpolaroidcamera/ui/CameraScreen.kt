package com.example.dualpolaroidcamera.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Size
import android.widget.Toast
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dualpolaroidcamera.R
import com.example.dualpolaroidcamera.ui.components.PolaroidFrame
import com.example.dualpolaroidcamera.ui.components.ShutterButton
import com.example.dualpolaroidcamera.utils.ImageProcessingPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var isConcurrentSupported by remember { mutableStateOf<Boolean?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Setări Interfață și Efecte (Rama mare este implicit "none" pentru un ecran curat, dar poate fi schimbată oricând)
    var isBackPrimary by remember { mutableStateOf(true) }
    var selectedFrameStyle by remember { mutableStateOf("stamp-vintage") }
    var selectedBigFrameStyle by remember { mutableStateOf("none") }
    var selectedFilter by remember { mutableStateOf("vintage-warm") }
    var brightnessLevel by remember { mutableFloatStateOf(0f) }
    var polaroidRotation by remember { mutableFloatStateOf(0f) }
    var polaroidScale by remember { mutableFloatStateOf(1.0f) }
    var mainZoom by remember { mutableFloatStateOf(1.0f) }
    var activeTab by remember { mutableStateOf<String?>("smallFrames") }
    var isUIHidden by remember { mutableStateOf(false) }

    // Temporizator Selfie (0s, 3s, 10s) și poză selfie înghețată în rama mică
    var selfieTimerSeconds by remember { mutableIntStateOf(3) }
    var selfieCountdown by remember { mutableStateOf<Int?>(null) }
    var frozenSelfieBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Control Focus & Camere
    var primaryCameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var secondaryCameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    // Viewfinder Views (COMPATIBLE permite aplicarea filtrelor live și gesturilor multi-touch)
    val backPreviewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val frontPreviewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var backImageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var frontImageCapture: ImageCapture? by remember { mutableStateOf(null) }

    // Poziție Drag & Drop a ramei mici (plasată sub bara de sus, convertită în pixeli reali)
    val initialX = with(density) { 16.dp.toPx() }
    val initialY = with(density) { 150.dp.toPx() }
    var polaroidOffsetX by remember { mutableFloatStateOf(initialX) }
    var polaroidOffsetY by remember { mutableFloatStateOf(initialY) }

    // Aplică în timp real Filtrul și Luminozitatea pe ambele camere (Android 12+ RenderEffect)
    LaunchedEffect(selectedFilter, brightnessLevel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cm = ColorMatrix()
            if (brightnessLevel != 0f) {
                val bScale = ((100f + brightnessLevel) / 100f).coerceAtLeast(0.2f)
                cm.setScale(bScale, bScale, bScale, 1f)
            }
            when (selectedFilter) {
                "vintage-warm" -> {
                    cm.postConcat(
                        ColorMatrix(
                            floatArrayOf(
                                1.2f, 0f, 0f, 0f, 20f,
                                0f, 1.05f, 0f, 0f, 10f,
                                0f, 0f, 0.85f, 0f, -10f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                }
                "bw-600" -> {
                    val gray = ColorMatrix()
                    gray.setSaturation(0f)
                    cm.postConcat(gray)
                }
                "instant-fade" -> {
                    val fade = ColorMatrix()
                    fade.setSaturation(0.7f)
                    cm.postConcat(fade)
                }
                "cyan-chrome" -> {
                    cm.postConcat(
                        ColorMatrix(
                            floatArrayOf(
                                0.9f, 0f, 0f, 0f, 0f,
                                0f, 1.15f, 0f, 0f, 10f,
                                0f, 0f, 1.25f, 0f, 25f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                }
                "sepia-classic" -> {
                    cm.postConcat(
                        ColorMatrix(
                            floatArrayOf(
                                0.393f, 0.769f, 0.189f, 0f, 0f,
                                0.349f, 0.686f, 0.168f, 0f, 0f,
                                0.272f, 0.534f, 0.131f, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                }
            }
            val effect = if (selectedFilter == "standard" && brightnessLevel == 0f) {
                null
            } else {
                RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm))
            }
            backPreviewView.setRenderEffect(effect)
            frontPreviewView.setRenderEffect(effect)
        }
    }

    // Conectare Camere Concurente (respectă comutarea camerelor)
    LaunchedEffect(isBackPrimary) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()

        val concurrentInfos = cameraProvider.availableConcurrentCameraInfos
        val frontInfo = concurrentInfos.flatten().firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_FRONT }
        val backInfo = concurrentInfos.flatten().firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_BACK }

        if (concurrentInfos.isEmpty() || frontInfo == null || backInfo == null) {
            isConcurrentSupported = false
            return@LaunchedEffect
        }

        isConcurrentSupported = true

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()

        val primarySelector = if (isBackPrimary) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        val secondarySelector = if (isBackPrimary) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        val primaryPreview = Preview.Builder().setResolutionSelector(resolutionSelector).build().also {
            it.surfaceProvider = backPreviewView.surfaceProvider
        }
        val primaryCapture = ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val secondaryPreview = Preview.Builder().setResolutionSelector(resolutionSelector).build().also {
            it.surfaceProvider = frontPreviewView.surfaceProvider
        }
        val secondaryCapture = ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val primaryGroup = UseCaseGroup.Builder()
            .addUseCase(primaryPreview)
            .addUseCase(primaryCapture)
            .build()

        val secondaryGroup = UseCaseGroup.Builder()
            .addUseCase(secondaryPreview)
            .addUseCase(secondaryCapture)
            .build()

        val primaryConfig = ConcurrentCamera.SingleCameraConfig(
            primarySelector,
            primaryGroup,
            lifecycleOwner
        )
        val secondaryConfig = ConcurrentCamera.SingleCameraConfig(
            secondarySelector,
            secondaryGroup,
            lifecycleOwner
        )

        try {
            cameraProvider.unbindAll()
            val concurrentCamera = cameraProvider.bindToLifecycle(listOf(primaryConfig, secondaryConfig))
            primaryCameraControl = concurrentCamera.cameras.getOrNull(0)?.cameraControl
            secondaryCameraControl = concurrentCamera.cameras.getOrNull(1)?.cameraControl
            backImageCapture = primaryCapture
            frontImageCapture = secondaryCapture
            primaryCameraControl?.setZoomRatio(mainZoom)
        } catch (e: Exception) {
            isConcurrentSupported = false
        }
    }

    val triggerFocus: (String) -> Unit = { target ->
        showFocusRing = true
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(0.5f, 0.5f)
        val action = FocusMeteringAction.Builder(point).build()
        if (target == "back" || target == "all") {
            primaryCameraControl?.startFocusAndMetering(action)
        }
        if (target == "front" || target == "all") {
            secondaryCameraControl?.startFocusAndMetering(action)
        }
        coroutineScope.launch {
            delay(1100)
            showFocusRing = false
        }
    }

    val triggerSelfieCapture: () -> Unit = {
        val frontCap = frontImageCapture
        if (frontCap != null && selfieCountdown == null) {
            coroutineScope.launch {
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
                    if (selfieTimerSeconds > 0) {
                        var remaining = selfieTimerSeconds
                        while (remaining > 0) {
                            selfieCountdown = remaining
                            toneGen.startTone(
                                if (remaining == 1) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,
                                160
                            )
                            delay(1000)
                            remaining -= 1
                        }
                        selfieCountdown = null
                    }
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                    val snap = takePictureToBitmap(context, frontCap, isBackPrimary)
                    frozenSelfieBitmap = snap
                    Toast.makeText(context, "Selfie capturat în rama mică!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    selfieCountdown = null
                    Toast.makeText(context, "Eroare la capturarea selfie-ului", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val polaroidWidthPx = with(density) { 150.dp.toPx() }
        val polaroidHeightPx = with(density) { 195.dp.toPx() }

        when (isConcurrentSupported) {
            null -> {}
            false -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.hardware_error),
                        color = Color.Red,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            true -> {
                // Stratul 1: Camera Principală (Fundal) cu Pinch-to-Zoom pe tot ecranul
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoomChange, _ ->
                                if (zoomChange != 1f) {
                                    mainZoom = (mainZoom * zoomChange).coerceIn(1.0f, 5.0f)
                                    primaryCameraControl?.setZoomRatio(mainZoom)
                                }
                            }
                        }
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            triggerFocus("back")
                        }
                ) {
                    AndroidView(
                        factory = { backPreviewView },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Stratul 1.5: Rama Pozei Mari (Big Frame: poate fi "none" sau orice temă)
                when (selectedBigFrameStyle) {
                    "none" -> {
                        // Fără ramă mare - ecranul este 100% curat
                    }
                    "postcard-full" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .border(2.dp, Color(0x998D6E63), RoundedCornerShape(2.dp))
                        ) {
                            Text(
                                text = "CARTE POȘTALĂ • 2026",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Text(
                                text = "„Greetings from our journey!”",
                                color = Color(0xFFFFF8E1),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(bottom = 96.dp, start = 8.dp)
                            )
                        }
                    }
                    "film-sprockets" -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xEE121214)).padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "▸ KODAK TX 400", color = Color(0xFFFFB300), fontSize = 10.sp)
                                Text(text = "FRAME 24A", color = Color(0xFFFFB300), fontSize = 10.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xEE121214)).padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "ISO 400", color = Color.LightGray, fontSize = 10.sp)
                                Text(text = "SAFETY FILM", color = Color.LightGray, fontSize = 10.sp)
                            }
                        }
                    }
                    "stamp-border" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .border(4.dp, Color(0xFFFDFBF7), RoundedCornerShape(2.dp))
                        )
                    }
                    "polaroid-macro" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 50.dp, top = 8.dp, start = 8.dp, end = 8.dp)
                                .border(12.dp, Color.White, RoundedCornerShape(2.dp))
                        )
                    }
                    "halloween" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(6.dp, Color(0xFFEA580C), RoundedCornerShape(2.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "🎃 HALLOWEEN NIGHT",
                                color = Color(0xFFFB923C),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    "christmas" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(6.dp, Color(0xFF15803D), RoundedCornerShape(2.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "✨ MERRY CHRISTMAS ❄️",
                                color = Color(0xFFFEF08A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .background(Color(0xCC991B1B), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    "love" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(5.dp, Color(0xFFF43F5E), RoundedCornerShape(2.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "💖 LOVE ALWAYS & FOREVER",
                                color = Color(0xFFFFE4E6),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .background(Color(0xCC881337), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    "hawaii" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(6.dp, Color(0xFFD97706), RoundedCornerShape(2.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "🌺 ALOHA HAWAII 🌴",
                                color = Color(0xFFFEF08A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .background(Color(0xCC451A03), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    "mafia" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(6.dp, Color(0xFF27272A), RoundedCornerShape(2.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "★ TOP SECRET // OMERTÀ ★",
                                color = Color(0xFFDC2626),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .background(Color(0xDD000000), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color.Red, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    "madmax" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(6.dp, Color(0xFF44403C), RoundedCornerShape(2.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "⚡ WITNESS ME! • FURY ROAD 💀",
                                color = Color(0xFFFACC15),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .background(Color(0xEE1C1917), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFFEAB308), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    "maxpayne" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(8.dp, Color.Black, RoundedCornerShape(0.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "MAX PAIN: THE FALL 🎯",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .background(Color(0xFFFACC15))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Focus reticle animation
                if (showFocusRing) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .border(2.dp, Color(0xFF00E676), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF00E676), CircleShape))
                    }
                }

                // Stratul 2: Fereastra dinamică cu Rame Multiple, Drag & Drop, Pinch-to-Scale și Bara de Setări Atașată
                PolaroidFrame(
                    frontPreviewView = frontPreviewView,
                    frozenSelfieBitmap = frozenSelfieBitmap,
                    selfieCountdown = selfieCountdown,
                    selfieTimerSeconds = selfieTimerSeconds,
                    offsetX = polaroidOffsetX,
                    offsetY = polaroidOffsetY,
                    rotationDegrees = polaroidRotation,
                    scale = polaroidScale,
                    frameStyle = selectedFrameStyle,
                    isUIHidden = isUIHidden,
                    onDrag = { dx, dy ->
                        val newX = polaroidOffsetX + dx
                        val newY = polaroidOffsetY + dy
                        polaroidOffsetX = newX.coerceIn(-20f, screenWidthPx - polaroidWidthPx * 0.5f)
                        polaroidOffsetY = newY.coerceIn(80f, screenHeightPx - polaroidHeightPx - 160f)
                    },
                    onRotate = { delta ->
                        polaroidRotation = (polaroidRotation + delta) % 360f
                    },
                    onNudgeRotate = { step ->
                        polaroidRotation = (polaroidRotation + step) % 360f
                    },
                    onScaleChange = { newScale ->
                        polaroidScale = newScale.coerceIn(0.45f, 2.3f)
                    },
                    onTriggerFocus = { triggerFocus("front") },
                    onTakeSelfie = { triggerSelfieCapture() },
                    onCycleTimer = {
                        selfieTimerSeconds = when (selfieTimerSeconds) {
                            0 -> 3
                            3 -> 10
                            else -> 0
                        }
                    },
                    onFlipCamera = { isBackPrimary = !isBackPrimary },
                    onCycleSmallFrame = {
                        val styles = listOf(
                            "stamp-vintage", "stamp-modern", "polaroid", "halloween",
                            "christmas", "love", "hawaii", "mafia", "madmax", "maxpayne",
                            "film35", "postcard", "circle"
                        )
                        val idx = styles.indexOf(selectedFrameStyle)
                        selectedFrameStyle = styles[(idx + 1) % styles.size]
                    },
                    onClearFrozenSelfie = { frozenSelfieBitmap = null }
                )

                // Stratul 3: Bara de Sus Compactă și Responsive (identică cu aplicația web, fără text vertical)
                if (!isUIHidden) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 34.dp, start = 10.dp, end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Rândul 1: Brand + Acțiuni Rapide (Scroll orizontal sigur, fără înghesuire verticală)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xD9000000), RoundedCornerShape(22.dp))
                                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(22.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(end = 10.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
                                Text(
                                    text = "Dual Camera",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { isUIHidden = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0x33F59E0B),
                                        contentColor = Color(0xFFFCD34D)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = "👁️ Ascunde Setări",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Button(
                                    onClick = {
                                        isBackPrimary = !isBackPrimary
                                        Toast.makeText(
                                            context,
                                            if (isBackPrimary) "Camera principală: Spate" else "Camera principală: Față",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF27272A),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = if (isBackPrimary) "⇄ Spate" else "⇄ Față",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                if (selectedBigFrameStyle != "none") {
                                    Button(
                                        onClick = { selectedBigFrameStyle = "none" },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFE11D48),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(
                                            text = "✕ Fără Ramă Mare",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // Rândul 2: Tab-uri dedicate: Ramă Mică | Ramă Mare | Timer | Filtre | Lumină | Focus
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xCC000000), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tabs = listOf(
                                Triple("smallFrames", "▣ Ramă Mică", Color(0xFFFFB300)),
                                Triple("bigFrames", "⤢ Ramă Mare", Color(0xFF38BDF8)),
                                Triple("timer", "⏱ Timer (${selfieTimerSeconds}s)", Color(0xFFFB923C)),
                                Triple("filters", "✨ Filtre", Color.White),
                                Triple("adjust", "☀️ Lumină", Color.White),
                                Triple("focus", "🎯 Focus", Color(0xFF10B981))
                            )
                            tabs.forEach { (id, label, activeColor) ->
                                val isSel = activeTab == id
                                Button(
                                    onClick = { activeTab = if (activeTab == id) null else id },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSel) activeColor else Color.Transparent,
                                        contentColor = if (isSel) Color.Black else Color(0xFFE4E4E7)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Black else FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Panoul 1: Ramă Mică (CAM 2) + Mărime
                        if (activeTab == "smallFrames") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xE6000000), RoundedCornerShape(16.dp))
                                    .border(1.dp, Color(0x4DF59E0B), RoundedCornerShape(16.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        "stamp-vintage" to "Timbru Gravat",
                                        "stamp-modern" to "Timbru Modern",
                                        "polaroid" to "Polaroid Clasic",
                                        "halloween" to "Halloween 🎃",
                                        "christmas" to "Crăciun 🎄",
                                        "love" to "Love 💖",
                                        "hawaii" to "Hawaii 🌺",
                                        "mafia" to "Mafia 1930",
                                        "madmax" to "Mad Max ⚡",
                                        "maxpayne" to "Max Pain 🎯",
                                        "film35" to "Film 35mm",
                                        "postcard" to "Vedere",
                                        "circle" to "Cerc"
                                    ).forEach { (id, name) ->
                                        val isAct = selectedFrameStyle == id
                                        Button(
                                            onClick = { selectedFrameStyle = id },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isAct) Color(0xFFFFB300) else Color(0xFF27272A),
                                                contentColor = if (isAct) Color.Black else Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text(
                                                text = if (isAct) "$name ✓" else name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Mărime cadru mic:", color = Color.LightGray, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = { polaroidScale = (polaroidScale - 0.15f).coerceAtLeast(0.45f) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A), contentColor = Color.White),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(text = "➖ Micșorează", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            text = "${(polaroidScale * 100).toInt()}%",
                                            color = Color(0xFFFFB300),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.clickable { polaroidScale = 1.0f }
                                        )
                                        Button(
                                            onClick = { polaroidScale = (polaroidScale + 0.15f).coerceAtMost(2.3f) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A), contentColor = Color.White),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(text = "➕ Mărește", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Panoul 2: Ramă Mare (CAM 1) cu buton vizibil ❌ Fără Ramă
                        if (activeTab == "bigFrames") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xE6000000), RoundedCornerShape(16.dp))
                                    .border(1.dp, Color(0x4D38BDF8), RoundedCornerShape(16.dp))
                                    .padding(8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "none" to "❌ Fără Ramă (Curat)",
                                    "postcard-full" to "Vedere Poștală",
                                    "stamp-border" to "Timbru Uriaș",
                                    "film-sprockets" to "Film 35mm Cinema",
                                    "polaroid-macro" to "Polaroid Macro",
                                    "halloween" to "Halloween Spooky",
                                    "christmas" to "Crăciun Holiday",
                                    "love" to "Love & Romance",
                                    "hawaii" to "Hawaii Aloha",
                                    "mafia" to "Mafia Secret File",
                                    "madmax" to "Mad Max Wasteland",
                                    "maxpayne" to "Max Pain Comic"
                                ).forEach { (id, name) ->
                                    val isAct = selectedBigFrameStyle == id
                                    Button(
                                        onClick = { selectedBigFrameStyle = id },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAct) {
                                                if (id == "none") Color(0xFFE11D48) else Color(0xFF38BDF8)
                                            } else {
                                                Color(0xFF27272A)
                                            },
                                            contentColor = if (isAct && id != "none") Color.Black else Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(
                                            text = if (isAct) "$name ✓" else name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // Panoul 3: Timer Selfie (Oprit, 3s, 10s)
                        if (activeTab == "timer") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xE6000000), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "⏱ Temporizator Selfie:", color = Color(0xFFFB923C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(0 to "Oprit", 3 to "3s", 10 to "10s").forEach { (sec, lbl) ->
                                        Button(
                                            onClick = { selfieTimerSeconds = sec },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selfieTimerSeconds == sec) Color(0xFFFB923C) else Color(0xFF27272A),
                                                contentColor = if (selfieTimerSeconds == sec) Color.Black else Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(text = lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Panoul 4: Filtre
                        if (activeTab == "filters") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xE6000000), RoundedCornerShape(16.dp))
                                    .padding(8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "standard" to "Original",
                                    "vintage-warm" to "70s Warm",
                                    "bw-600" to "B&W Mono",
                                    "instant-fade" to "90s Fade",
                                    "cyan-chrome" to "Cyan Color",
                                    "sepia-classic" to "Sepia Retro"
                                ).forEach { (id, name) ->
                                    val isAct = selectedFilter == id
                                    Button(
                                        onClick = { selectedFilter = id },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAct) Color(0xFFFFB300) else Color(0xFF27272A),
                                            contentColor = if (isAct) Color.Black else Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(text = name, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }

                        // Panoul 5: Slider Luminozitate (-50% la +50%)
                        if (activeTab == "adjust") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xE6000000), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Lumină:", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                                Slider(
                                    value = brightnessLevel,
                                    onValueChange = { brightnessLevel = it },
                                    valueRange = -50f..50f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFFB300),
                                        activeTrackColor = Color(0xFFFFB300)
                                    )
                                )
                                Text(
                                    text = "${brightnessLevel.toInt()}%",
                                    color = Color(0xFFFFB300),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(44.dp).clickable { brightnessLevel = 0f },
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        // Panoul 6: Meniu Focus
                        if (activeTab == "focus") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xE6000000), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { triggerFocus("back") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A), contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(text = "Camera Spate", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { triggerFocus("front") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A), contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(text = "Camera Față", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { triggerFocus("all") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(text = "Ambele", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Buton plutitor când setările sunt ascunse (Mod Ecran Curat)
                    Button(
                        onClick = { isUIHidden = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xEE000000),
                            contentColor = Color(0xFFFFB300)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 36.dp, end = 16.dp)
                    ) {
                        Text(text = "⚙️ Deschide Setări", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Stratul 4: Selector Zoom (1x / 1.5x / 2x / 3x) + Butonul de declanșare foto, Setări și Comutare
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isUIHidden) {
                        Row(
                            modifier = Modifier
                                .background(Color(0xCC000000), RoundedCornerShape(20.dp))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(1.0f, 1.5f, 2.0f, 3.0f).forEach { z ->
                                val isSel = kotlin.math.abs(mainZoom - z) < 0.22f
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSel) Color(0xFFFFB300) else Color.Transparent,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            mainZoom = z
                                            primaryCameraControl?.setZoomRatio(z)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (isSel && kotlin.math.abs(mainZoom - z) > 0.05f) {
                                            String.format("%.1fx", mainZoom)
                                        } else {
                                            "${if (z % 1f == 0f) z.toInt() else z}x"
                                        },
                                        color = if (isSel) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    ShutterButton(
                        isEnabled = !isProcessing,
                        isLoading = isProcessing,
                        isBackPrimary = isBackPrimary,
                        isUIHidden = isUIHidden,
                        onToggleSettings = { isUIHidden = !isUIHidden },
                        onClick = {
                            val backCap = backImageCapture
                            val frontCap = frontImageCapture
                            if (backCap != null && frontCap != null && !isProcessing) {
                                isProcessing = true
                                coroutineScope.launch {
                                    try {
                                        val primaryDeferred = async(Dispatchers.IO) { takePictureToBitmap(context, backCap, !isBackPrimary) }
                                        val secondaryBitmap = frozenSelfieBitmap ?: async(Dispatchers.IO) {
                                            takePictureToBitmap(context, frontCap, isBackPrimary)
                                        }.await()
                                        val primaryBitmap = primaryDeferred.await()

                                        val relativeX = polaroidOffsetX / screenWidthPx
                                        val relativeY = polaroidOffsetY / screenHeightPx

                                        val success = ImageProcessingPipeline.createAndSavePolaroid(
                                            context = context,
                                            backBitmap = primaryBitmap,
                                            frontBitmap = secondaryBitmap,
                                            relativePolaroidX = relativeX,
                                            relativePolaroidY = relativeY,
                                            polaroidWidthRatio = 0.36f * polaroidScale,
                                            frameStyle = selectedFrameStyle,
                                            bigFrameStyle = selectedBigFrameStyle,
                                            rotationDegrees = polaroidRotation,
                                            filter = selectedFilter,
                                            brightness = brightnessLevel,
                                            keepFrontBitmap = frozenSelfieBitmap != null
                                        )

                                        if (success) {
                                            Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.save_failure), Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.save_failure), Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                        onFlipCamera = {
                            isBackPrimary = !isBackPrimary
                        }
                    )
                }
            }
        }
    }
}

private suspend fun takePictureToBitmap(
    context: Context,
    imageCapture: ImageCapture,
    isFrontCamera: Boolean
): Bitmap = suspendCancellableCoroutine { continuation ->
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = ImageProcessingPipeline.imageProxyToBitmap(image, isFrontCamera)
                    continuation.resume(bitmap)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                continuation.resumeWithException(exception)
            }
        }
    )
}
