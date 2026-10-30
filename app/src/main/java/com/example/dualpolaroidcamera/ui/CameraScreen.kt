package com.example.dualpolaroidcamera.ui

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    // Setări Interfață și Efecte
    var isBackPrimary by remember { mutableStateOf(true) }
    var selectedFrameStyle by remember { mutableStateOf("stamp-vintage") }
    var selectedBigFrameStyle by remember { mutableStateOf("postcard-full") }
    var frameCategory by remember { mutableStateOf("small") }
    var selectedFilter by remember { mutableStateOf("vintage-warm") }
    var brightnessLevel by remember { mutableFloatStateOf(0f) }
    var polaroidRotation by remember { mutableFloatStateOf(0f) }
    var polaroidScale by remember { mutableFloatStateOf(1.0f) }
    var activeTab by remember { mutableStateOf<String?>("frames") }
    var isUIHidden by remember { mutableStateOf(false) }

    // Control Focus
    var primaryCameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var secondaryCameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    // Viewfinder Views
    val backPreviewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    // Textura selfie în mod COMPATIBLE pentru a respecta marginile, rotirea și padding-ul Card-ului!
    val frontPreviewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var backImageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var frontImageCapture: ImageCapture? by remember { mutableStateOf(null) }

    // Poziție Drag & Drop a ramei
    var polaroidOffsetX by remember { mutableFloatStateOf(32f) }
    var polaroidOffsetY by remember { mutableFloatStateOf(160f) }

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
        Toast.makeText(context, "Focus: $target", Toast.LENGTH_SHORT).show()
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
        val polaroidWidthPx = with(density) { 145.dp.toPx() }
        val polaroidHeightPx = with(density) { 190.dp.toPx() }

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
                // Stratul 1: Camera Principală (Fundal)
                AndroidView(
                    factory = { backPreviewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Stratul 1.5: Rama Pozei Mari (Big Frame: Vedere Poștală, Film 35mm, Timbru, Polaroid)
                when (selectedBigFrameStyle) {
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
                                    .padding(8.dp)
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

                // Stratul 2: Fereastra dinamică cu Rame Multiple, Drag & Drop și Rotire
                PolaroidFrame(
                    frontPreviewView = frontPreviewView,
                    offsetX = polaroidOffsetX,
                    offsetY = polaroidOffsetY,
                    rotationDegrees = polaroidRotation,
                    scale = polaroidScale,
                    frameStyle = selectedFrameStyle,
                    isUIHidden = isUIHidden,
                    onDrag = { dx, dy ->
                        val newX = polaroidOffsetX + dx
                        val newY = polaroidOffsetY + dy
                        polaroidOffsetX = newX.coerceIn(0f, screenWidthPx - polaroidWidthPx)
                        polaroidOffsetY = newY.coerceIn(120f, screenHeightPx - polaroidHeightPx - 160f)
                    },
                    onRotate = { delta ->
                        polaroidRotation = (polaroidRotation + delta) % 360f
                    },
                    onNudgeRotate = { step ->
                        polaroidRotation = (polaroidRotation + step) % 360f
                    },
                    onScaleChange = { newScale ->
                        polaroidScale = newScale
                    },
                    onTriggerFocus = { triggerFocus("front") }
                )

                // Stratul 3: Bara de Sus cu Comenzi (Rame, Filtre, Lumină, Comutare Cameră)
                if (!isUIHidden) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 36.dp, start = 12.dp, end = 12.dp)
                    ) {
                        // Meniu principal
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xCC000000), RoundedCornerShape(24.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { activeTab = if (activeTab == "frames") null else "frames" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeTab == "frames") Color.White else Color(0x33FFFFFF),
                                        contentColor = if (activeTab == "frames") Color.Black else Color.White
                                    ),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = "Rame", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { activeTab = if (activeTab == "filters") null else "filters" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeTab == "filters") Color.White else Color(0x33FFFFFF),
                                        contentColor = if (activeTab == "filters") Color.Black else Color.White
                                    ),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = "Filtre", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { activeTab = if (activeTab == "adjust") null else "adjust" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeTab == "adjust") Color.White else Color(0x33FFFFFF),
                                        contentColor = if (activeTab == "adjust") Color.Black else Color.White
                                    ),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = if (brightnessLevel != 0f) "Lumină (${brightnessLevel.toInt()}%)" else "Lumină",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Buton Focus
                                Button(
                                    onClick = { activeTab = if (activeTab == "focus") null else "focus" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeTab == "focus") Color(0xFF00E676) else Color(0x3300E676),
                                        contentColor = if (activeTab == "focus") Color.Black else Color(0xFF00E676)
                                    ),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = "🎯 Focus", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // Buton Ascunde Setări (Ecran Curat)
                                Button(
                                    onClick = { isUIHidden = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0x33FFFFFF),
                                        contentColor = Color(0xFFFFB300)
                                    ),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = "👁️ Ascunde", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Buton Comutare Cameră Spate ⇄ Față
                            Button(
                                onClick = {
                                    isBackPrimary = !isBackPrimary
                                    Toast.makeText(
                                        context,
                                        if (isBackPrimary) "Camera principală: Spate" else "Camera principală: Față (Selfie)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFB300),
                                    contentColor = Color.Black
                                ),
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = if (isBackPrimary) "⇄ Spate" else "⇄ Față",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Meniu Focus
                        if (activeTab == "focus") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .background(Color(0xCC000000), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { triggerFocus("back") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF), contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(text = "Focalizează Spate", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { triggerFocus("front") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF), contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(text = "Focalizează Față", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { triggerFocus("all") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(text = "Ambele", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Selector de Rame cu sub-selecție Rama Mică vs Rama Mare
                        if (activeTab == "frames") {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xDD000000), RoundedCornerShape(12.dp))
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Button(
                                        onClick = { frameCategory = "small" },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (frameCategory == "small") Color(0xFFFFB300) else Color.Transparent,
                                            contentColor = if (frameCategory == "small") Color.Black else Color.LightGray
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(text = "Rama Mică (CAM 2)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { frameCategory = "big" },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (frameCategory == "big") Color(0xFF03A9F4) else Color.Transparent,
                                            contentColor = if (frameCategory == "big") Color.Black else Color.LightGray
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(text = "Rama Mare (CAM 1)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (frameCategory == "small") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            "stamp-vintage" to "Timbru Gravat",
                                            "stamp-modern" to "Timbru Modern",
                                            "polaroid" to "Polaroid",
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
                                            Button(
                                                onClick = { selectedFrameStyle = id },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (selectedFrameStyle == id) Color(0xFFFFB300) else Color(0xBB000000),
                                                    contentColor = if (selectedFrameStyle == id) Color.Black else Color.White
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(text = name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }

                                if (frameCategory == "big") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            "none" to "Fără Ramă",
                                            "postcard-full" to "Vedere Poștală",
                                            "halloween" to "Halloween Spooky",
                                            "christmas" to "Crăciun Holiday",
                                            "love" to "Love & Romance",
                                            "hawaii" to "Hawaii Aloha",
                                            "mafia" to "Mafia Secret File",
                                            "madmax" to "Mad Max Wasteland",
                                            "maxpayne" to "Max Pain Comic",
                                            "film-sprockets" to "Film 35mm Cinema",
                                            "stamp-border" to "Timbru Uriaș",
                                            "polaroid-macro" to "Polaroid Macro"
                                        ).forEach { (id, name) ->
                                            Button(
                                                onClick = { selectedBigFrameStyle = id },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (selectedBigFrameStyle == id) Color(0xFF03A9F4) else Color(0xBB000000),
                                                    contentColor = if (selectedBigFrameStyle == id) Color.Black else Color.White
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(text = name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    // Selector de Filtre
                    if (activeTab == "filters") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
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
                                Button(
                                    onClick = { selectedFilter = id },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedFilter == id) Color(0xFFFFB300) else Color(0xBB000000),
                                        contentColor = if (selectedFilter == id) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(text = name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Slider Luminozitate (-50% la +50%)
                    if (activeTab == "adjust") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .background(Color(0xCC000000), RoundedCornerShape(16.dp))
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
                                modifier = Modifier.width(44.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            } else {
                // Buton plutitor când setările sunt ascunse (Mod Ecran Curat)
                Button(
                    onClick = { isUIHidden = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xDD000000),
                        contentColor = Color(0xFFFFB300)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 36.dp, end = 16.dp)
                ) {
                    Text(text = "👁️ Arată Setări", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Stratul 4: Butonul de declanșare foto și comutare
            ShutterButton(
                isEnabled = !isProcessing,
                isLoading = isProcessing,
                onClick = {
                    val backCap = backImageCapture
                    val frontCap = frontImageCapture
                    if (backCap != null && frontCap != null && !isProcessing) {
                        isProcessing = true
                        coroutineScope.launch {
                            try {
                                val primaryDeferred = async(Dispatchers.IO) { takePictureToBitmap(context, backCap, !isBackPrimary) }
                                val secondaryDeferred = async(Dispatchers.IO) { takePictureToBitmap(context, frontCap, isBackPrimary) }

                                val primaryBitmap = primaryDeferred.await()
                                val secondaryBitmap = secondaryDeferred.await()

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
                                    rotationDegrees = polaroidRotation,
                                    filter = selectedFilter,
                                    brightness = brightnessLevel
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
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                )
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
