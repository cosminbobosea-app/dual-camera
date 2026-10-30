package com.example.dualpolaroidcamera.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.atan2

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

    // Text Personalizat, Font și Ștampilă Poștală Dinamică (Dată + Oraș GPS)
    val currentDateText = remember {
        SimpleDateFormat("MMM. dd, yyyy", Locale.US).format(Date()).uppercase(Locale.US)
    }
    var customCaption by remember { mutableStateOf("") }
    var captionFont by remember { mutableStateOf("handwriting") }
    var cityName by remember { mutableStateOf("BUCUREȘTI") }

    // Giroscop (Nivelă Electronică / Orizont Virtual + Efect 3D pe Timbru/Ramă)
    var gyroEnabled by remember { mutableStateOf(true) }
    var gyroRoll by remember { mutableFloatStateOf(0f) }
    var gyroPitch by remember { mutableFloatStateOf(0f) }

    // Shortcut Galerie & Previzualizare Ultima Poză
    var lastCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var capturedCount by remember { mutableIntStateOf(0) }
    var showGalleryDialog by remember { mutableStateOf(false) }

    // Temporizator Selfie (0s, 3s, 10s) și poză selfie înghețată în rama mică
    var selfieTimerSeconds by remember { mutableIntStateOf(3) }
    var selfieCountdown by remember { mutableStateOf<Int?>(null) }
    var frozenSelfieBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Control Focus & Camere
    var primaryCameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var secondaryCameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    // Funcție preluare Oraș din GPS (Geocoder)
    val fetchCityFromGps: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    val loc = locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        ?: locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (loc != null) {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                        val foundCity = addresses?.firstOrNull()?.locality
                            ?: addresses?.firstOrNull()?.subAdminArea
                            ?: addresses?.firstOrNull()?.adminArea
                        if (!foundCity.isNullOrBlank()) {
                            withContext(Dispatchers.Main) {
                                cityName = foundCity.uppercase(Locale.getDefault())
                                Toast.makeText(context, "Ștampilă actualizată: $cityName", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Orașul nu a putut fi detectat automat", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Activează GPS-ul sau scrie orașul manual", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Eroare la citirea locației GPS", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchCityFromGps()
        } else {
            Toast.makeText(context, "Poți scrie numele orașului și manual în câmpul Ștampilă", Toast.LENGTH_SHORT).show()
        }
    }

    // Senzor Giroscop / Gravitație pentru Nivela Electronică și Înclinarea 3D a Timbrului
    DisposableEffect(gyroEnabled) {
        if (!gyroEnabled) {
            gyroRoll = 0f
            gyroPitch = 0f
            return@DisposableEffect onDispose {}
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                // Calculăm unghiul de înclinare orizontală (Roll) în grade
                val rollDeg = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()
                // Calculăm înclinarea față-spate (Pitch)
                val pitchDeg = (gz * 3.5f).coerceIn(-25f, 25f)
                // Netezire (low-pass filter) pentru mișcare fluidă
                gyroRoll = gyroRoll * 0.8f + rollDeg.coerceIn(-45f, 45f) * 0.2f
                gyroPitch = gyroPitch * 0.8f + pitchDeg * 0.2f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (gravitySensor != null) {
            sensorManager.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

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

    val selectedFontFamily = when (captionFont) {
        "handwriting" -> FontFamily.Cursive
        "retro" -> FontFamily.Serif
        "typewriter" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }

    val fullStampText = if (cityName.isNotBlank()) "${cityName.uppercase()} • $currentDateText" else currentDateText

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

                // Stratul 1.2: Nivelă Electronică Giroscopică (Orizont Virtual în centrul ecranului)
                if (gyroEnabled) {
                    val isLevel = abs(gyroRoll) <= 1.5f
                    val levelColor = if (isLevel) Color(0xFF10B981) else Color(0xCCFFB300)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    rotationZ = -gyroRoll
                                    translationY = (gyroPitch * 1.5f).coerceIn(-30f, 30f)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(modifier = Modifier.width(65.dp).height(2.dp).background(levelColor))
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(24.dp)
                                    .border(2.dp, levelColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(5.dp).background(levelColor, CircleShape))
                            }
                            Box(modifier = Modifier.width(65.dp).height(2.dp).background(levelColor))
                        }

                        if (!isUIHidden) {
                            Text(
                                text = if (isLevel) "✓ ORIZONT DREPT 0°" else "ÎNCLINARE ${gyroRoll.toInt()}°",
                                color = if (isLevel) Color(0xFF6EE7B7) else Color(0xFFFCD34D),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(top = 54.dp)
                                    .background(Color(0xAA000000), RoundedCornerShape(10.dp))
                                    .border(1.dp, levelColor, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Stratul 1.5: Rama Pozei Mari (Big Frame: poate fi "none" sau orice temă, cu ștampilă dinamică și text editabil)
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
                                text = "📍 CARTE POȘTALĂ • $fullStampText",
                                color = Color(0xFFFDE68A),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                                    .clickable {
                                        isUIHidden = false
                                        activeTab = "caption"
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )

                            // Ștampilă poștală rotundă în colțul dreapta-sus
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 8.dp)
                                    .size(58.dp)
                                    .border(1.5.dp, Color(0xFF38BDF8), CircleShape)
                                    .background(Color(0x440C4A6E), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = cityName.ifBlank { "POST" }.take(9),
                                        color = Color(0xFF7DD3FC),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(text = "✉ AIR MAIL", color = Color.White, fontSize = 6.sp)
                                    Text(text = currentDateText.take(6), color = Color(0xFFBAE6FD), fontSize = 6.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(
                                text = "✍️ „${customCaption.ifBlank { "Greetings from our journey!" }}”",
                                color = Color(0xFFFFF8E1),
                                fontSize = 14.sp,
                                fontFamily = selectedFontFamily,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(bottom = 112.dp, start = 8.dp)
                                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0x55FFB300), RoundedCornerShape(8.dp))
                                    .clickable {
                                        isUIHidden = false
                                        activeTab = "caption"
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
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
                                Text(text = "▸ KODAK TX 400 • $cityName", color = Color(0xFFFFB300), fontSize = 10.sp)
                                Text(text = "FRAME 24A", color = Color(0xFFFFB300), fontSize = 10.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xEE121214)).padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "ISO 400", color = Color.LightGray, fontSize = 10.sp)
                                Text(text = fullStampText, color = Color.LightGray, fontSize = 10.sp)
                            }
                        }
                    }
                    "stamp-border" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .border(4.dp, Color(0xFFFDFBF7), RoundedCornerShape(2.dp))
                        ) {
                            Text(
                                text = "★ $fullStampText ★",
                                color = Color(0xFF1E293B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 6.dp)
                                    .background(Color(0xDDFDFBF7), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    "polaroid-macro" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 50.dp, top = 8.dp, start = 8.dp, end = 8.dp)
                                .border(12.dp, Color.White, RoundedCornerShape(2.dp))
                        ) {
                            Text(
                                text = "✍️ ${customCaption.ifBlank { fullStampText }}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = selectedFontFamily,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 64.dp)
                                    .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                                    .clickable {
                                        isUIHidden = false
                                        activeTab = "caption"
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    "halloween" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(6.dp, Color(0xFFEA580C), RoundedCornerShape(2.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "🎃 HALLOWEEN • $fullStampText",
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
                                text = "✨ MERRY CHRISTMAS • $fullStampText ❄️",
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
                                text = "💖 LOVE ALWAYS • $fullStampText",
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
                                text = "🌺 ALOHA • $fullStampText 🌴",
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
                                text = "★ TOP SECRET // OMERTÀ • $cityName ★",
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
                                text = "⚡ WITNESS ME! • $fullStampText 💀",
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
                                text = "MAX PAIN: THE FALL 🎯 • $cityName",
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

                // Stratul 2: Fereastra dinamică cu Rame Multiple, Înclinare 3D Giroscopică, Drag & Drop, Pinch-to-Scale și Bara de Setări Atașată
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
                    captionText = customCaption,
                    captionFont = captionFont,
                    cityName = cityName,
                    gyroPitch = gyroPitch,
                    gyroRoll = gyroRoll,
                    gyroEnabled = gyroEnabled,
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
                    onEditCaption = {
                        isUIHidden = false
                        activeTab = "caption"
                    },
                    onClearFrozenSelfie = { frozenSelfieBitmap = null }
                )

                // Stratul 3: Bara de Sus Compactă și Responsive (fără text vertical)
                if (!isUIHidden) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 34.dp, start = 10.dp, end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Rândul 1: Brand + Acțiuni Rapide (Scroll orizontal sigur)
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
                                // Buton Giroscop / Nivelă
                                Button(
                                    onClick = { gyroEnabled = !gyroEnabled },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (gyroEnabled) {
                                            if (abs(gyroRoll) <= 1.5f) Color(0x3310B981) else Color(0x33F59E0B)
                                        } else {
                                            Color(0xFF27272A)
                                        },
                                        contentColor = if (gyroEnabled) {
                                            if (abs(gyroRoll) <= 1.5f) Color(0xFF6EE7B7) else Color(0xFFFCD34D)
                                        } else {
                                            Color.LightGray
                                        }
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = if (gyroEnabled) "🧭 ${gyroRoll.toInt()}°" else "🧭 Giro Off",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // Shortcut Galerie în bara de sus
                                Button(
                                    onClick = { showGalleryDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF27272A),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = if (capturedCount > 0) "🖼️ Galerie ($capturedCount)" else "🖼️ Galerie",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

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

                        // Rândul 2: Tab-uri dedicate: Ramă Mică | Ramă Mare | Text & Ștampilă | Timer | Filtre | Lumină | Focus
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
                                Triple("caption", "✍️ Text & Ștampilă", Color(0xFFFDE047)),
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

                        // Panoul 2.5: Text Personalizat, Font și Ștampilă Oraș GPS
                        if (activeTab == "caption") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xF2000000), RoundedCornerShape(16.dp))
                                    .border(1.dp, Color(0x80FDE047), RoundedCornerShape(16.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customCaption,
                                    onValueChange = { if (it.length <= 40) customCaption = it },
                                    label = { Text("Mesaj pe Polaroid / Carte Poștală", fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFFFFB300),
                                        unfocusedBorderColor = Color(0x55FFFFFF)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Selector Font (Scris de mână, Retro, Mașină, Marker)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Font:", color = Color.LightGray, fontSize = 11.sp)
                                    listOf(
                                        "handwriting" to "✍️ Mână",
                                        "retro" to "📜 Retro",
                                        "typewriter" to "⌨️ Mașină",
                                        "marker" to "🖊️ Marker"
                                    ).forEach { (id, lbl) ->
                                        val isFontSel = captionFont == id
                                        Button(
                                            onClick = { captionFont = id },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isFontSel) Color(0xFFFFB300) else Color(0xFF27272A),
                                                contentColor = if (isFontSel) Color.Black else Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(text = lbl, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Ștampilă Poștală Dinamică (Oraș + Buton GPS)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = cityName,
                                        onValueChange = { if (it.length <= 20) cityName = it.uppercase() },
                                        label = { Text("Oraș Ștampilă (ex: BUCUREȘTI)", fontSize = 10.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color(0xFF7DD3FC),
                                            unfocusedTextColor = Color(0xFF7DD3FC),
                                            focusedBorderColor = Color(0xFF38BDF8),
                                            unfocusedBorderColor = Color(0x5538BDF8)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                                fetchCityFromGps()
                                            } else {
                                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF38BDF8),
                                            contentColor = Color.Black
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("📍 GPS Oraș", fontSize = 11.sp, fontWeight = FontWeight.Black)
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

                // Stratul 4: Selector Zoom (1x / 1.5x / 2x / 3x) + Buton Galerie, Setări, Declanșator și Comutare
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
                        lastPhotoBitmap = lastCapturedBitmap,
                        photoCount = capturedCount,
                        onOpenGallery = { showGalleryDialog = true },
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

                                        val (savedUri, previewBmp) = ImageProcessingPipeline.createAndSavePolaroid(
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
                                            captionText = customCaption,
                                            captionFont = captionFont,
                                            cityName = cityName,
                                            keepFrontBitmap = frozenSelfieBitmap != null
                                        )

                                        if (savedUri != null) {
                                            lastCapturedUri = savedUri
                                            lastCapturedBitmap = previewBmp
                                            capturedCount += 1
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

                // Stratul 5: Fereastră Modală Galerie & Previzualizare Fotografie Salvată
                if (showGalleryDialog) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xEE000000))
                            .clickable { showGalleryDialog = false }
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF18181B), RoundedCornerShape(24.dp))
                                .border(1.dp, Color(0x44FFB300), RoundedCornerShape(24.dp))
                                .clickable(enabled = false) {}
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🖼️ Galeria Polaroid ($capturedCount poze)",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "✕",
                                    color = Color.LightGray,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { showGalleryDialog = false }
                                )
                            }

                            val bmp = lastCapturedBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Ultima poză",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(360.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.Black)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Color(0xFF27272A), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Nu ai făcut încă nicio poză în această sesiune.\nPozele tale sunt salvate în Pictures/DualPolaroid.",
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        try {
                                            val uri = lastCapturedUri ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "image/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Pozele se află în aplicația Galerie -> DualPolaroid", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFFB300),
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("📂 Galerie Telefon", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }

                                if (lastCapturedUri != null) {
                                    Button(
                                        onClick = {
                                            try {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "image/jpeg"
                                                    putExtra(Intent.EXTRA_STREAM, lastCapturedUri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Trimite fotografia Polaroid"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Nu s-a putut partaja fotografia", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF27272A),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("📤 Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
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
