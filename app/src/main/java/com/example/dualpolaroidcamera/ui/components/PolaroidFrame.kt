package com.example.dualpolaroidcamera.ui.components

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PolaroidFrame(
    frontPreviewView: PreviewView,
    frozenSelfieBitmap: Bitmap? = null,
    selfieCountdown: Int? = null,
    selfieTimerSeconds: Int = 3,
    offsetX: Float,
    offsetY: Float,
    rotationDegrees: Float,
    scale: Float = 1.0f,
    frameStyle: String,
    captionText: String = "",
    captionFont: String = "handwriting",
    cityName: String = "BUCUREȘTI",
    gyroPitch: Float = 0f,
    gyroRoll: Float = 0f,
    gyroEnabled: Boolean = true,
    isUIHidden: Boolean = false,
    onDrag: (Float, Float) -> Unit,
    onRotate: (Float) -> Unit,
    onNudgeRotate: (Float) -> Unit,
    onScaleChange: ((Float) -> Unit)? = null,
    onTriggerFocus: (() -> Unit)? = null,
    onTakeSelfie: (() -> Unit)? = null,
    onCycleTimer: (() -> Unit)? = null,
    onFlipCamera: (() -> Unit)? = null,
    onCycleSmallFrame: (() -> Unit)? = null,
    onEditCaption: (() -> Unit)? = null,
    onClearFrozenSelfie: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentDate = remember {
        val formatter = SimpleDateFormat("MMM. dd, yyyy", Locale.US)
        formatter.format(Date()).uppercase(Locale.US)
    }

    val fullStampLabel = if (cityName.isNotBlank()) "${cityName.uppercase()} • $currentDate" else currentDate
    val chosenFont = when (captionFont) {
        "handwriting" -> FontFamily.Cursive
        "retro" -> FontFamily.Serif
        "typewriter" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }

    @Composable
    fun RenderInnerFeed() {
        Box(modifier = Modifier.fillMaxSize()) {
            if (frozenSelfieBitmap != null) {
                Image(
                    bitmap = frozenSelfieBitmap.asImageBitmap(),
                    contentDescription = "Selfie",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AndroidView(
                    factory = { frontPreviewView },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (selfieCountdown != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = selfieCountdown.toString(),
                            color = Color(0xFFFFB300),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "ZÂMBEȘTE!",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer {
                this.rotationZ = rotationDegrees
                this.rotationX = if (gyroEnabled) (gyroPitch * 0.42f).coerceIn(-16f, 16f) else 0f
                this.rotationY = if (gyroEnabled) (gyroRoll * 0.42f).coerceIn(-16f, 16f) else 0f
                this.cameraDistance = 12f * density
                this.scaleX = scale
                this.scaleY = scale
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotationChange ->
                    onDrag(pan.x, pan.y)
                    if (zoom != 1f) {
                        onScaleChange?.invoke(scale * zoom)
                    }
                    if (rotationChange != 0f) {
                        onRotate(rotationChange)
                    }
                }
            }
    ) {
        if (!isUIHidden) {
            // Bară deasupra ramei pentru Rotire, Mărime și Mișcare
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-28).dp)
                    .background(Color(0xDD000000), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "↺",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNudgeRotate(-12f) }
                )
                Text(
                    text = if (rotationDegrees != 0f) "${rotationDegrees.toInt()}°" else "✥ Trage",
                    color = Color(0xFFFFB300),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "↻",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNudgeRotate(12f) }
                )
                Text(text = "|", color = Color(0x55FFFFFF), fontSize = 10.sp)
                Text(
                    text = "➖",
                    fontSize = 9.sp,
                    modifier = Modifier.clickable { onScaleChange?.invoke((scale - 0.15f).coerceAtLeast(0.45f)) }
                )
                Text(
                    text = "${(scale * 100).toInt()}%",
                    color = Color(0xFFFFB300),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onScaleChange?.invoke(1.0f) }
                )
                Text(
                    text = "➕",
                    fontSize = 9.sp,
                    modifier = Modifier.clickable { onScaleChange?.invoke((scale + 0.15f).coerceAtMost(2.3f)) }
                )
            }

            // Buton Focus rapid pe colțul stânga-sus
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-10).dp, y = (-10).dp)
                    .size(28.dp)
                    .background(Color(0xEE000000), CircleShape)
                    .border(1.dp, Color(0xFFFFB300), CircleShape)
                    .clickable { onTriggerFocus?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🎯", fontSize = 12.sp)
            }

            // Buton rotund de Rotire directă pe colțul din dreapta-sus
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
                    .size(28.dp)
                    .background(Color(0xFFFFB300), CircleShape)
                    .border(1.dp, Color.Black, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onRotate(dragAmount.x * 0.4f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "↻", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // Buton rotund de Redimensionare (Mărire / Micșorare prin tragere) pe colțul din dreapta-jos
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 10.dp)
                    .size(28.dp)
                    .background(Color(0xFFFB923C), CircleShape)
                    .border(1.dp, Color.Black, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val delta = (dragAmount.x + dragAmount.y) / 180f
                            onScaleChange?.invoke(scale + delta)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⤡", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // Bara de Setări și Controale Atașată Jos la Cadrul Mic (Selfie, Timer, Comutare, Stil Ramă, Text)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 44.dp)
                    .background(Color(0xF2000000), RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color(0x99FFB300), RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Buton Selfie cu numărătoare inversă
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFB300), RoundedCornerShape(10.dp))
                        .clickable { onTakeSelfie?.invoke() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (selfieTimerSeconds > 0) "📷 Selfie (${selfieTimerSeconds}s)" else "📷 Selfie",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Selector Timer (Off / 3s / 10s)
                Box(
                    modifier = Modifier
                        .background(
                            if (selfieTimerSeconds > 0) Color(0x4DFB923C) else Color(0xFF27272A),
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, Color(0x66FB923C), RoundedCornerShape(10.dp))
                        .clickable { onCycleTimer?.invoke() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (selfieTimerSeconds > 0) "⏱ ${selfieTimerSeconds}s" else "⏱ Off",
                        color = Color(0xFFFDBA74),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Schimbă camera
                Box(
                    modifier = Modifier
                        .background(Color(0xFF27272A), RoundedCornerShape(10.dp))
                        .clickable { onFlipCamera?.invoke() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(text = "🔄", fontSize = 10.sp)
                }

                // Schimbă rama mică
                Box(
                    modifier = Modifier
                        .background(Color(0xFF27272A), RoundedCornerShape(10.dp))
                        .clickable { onCycleSmallFrame?.invoke() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(text = "▣", color = Color(0xFFFFB300), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Editează Text / Ștampilă
                Box(
                    modifier = Modifier
                        .background(Color(0xFF27272A), RoundedCornerShape(10.dp))
                        .clickable { onEditCaption?.invoke() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(text = "✍️", fontSize = 10.sp)
                }

                if (frozenSelfieBitmap != null) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF991B1B), RoundedCornerShape(10.dp))
                            .clickable { onClearFrozenSelfie?.invoke() }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(text = "✕", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Corpul Ramei în funcție de stilul ales
        when (frameStyle) {
            "stamp-vintage", "stamp" -> {
                Card(
                    modifier = Modifier
                        .width(148.dp)
                        .height(195.dp)
                        .border(2.dp, Color(0xFF102A43), RoundedCornerShape(2.dp)),
                    shape = RoundedCornerShape(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A365D)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "★ ${cityName.ifBlank { "UNITED STATES" }} POSTAGE ★",
                            color = Color.White,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(80.dp))
                                .border(2.dp, Color(0xFFBEE3F8), RoundedCornerShape(80.dp))
                                .background(Color.Black)
                        ) {
                            RenderInnerFeed()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clickable { onEditCaption?.invoke() },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "1¢", color = Color(0xFFBEE3F8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = captionText.ifBlank { "ONE CENT" }.uppercase().take(14),
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(text = "1¢", color = Color(0xFFBEE3F8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "stamp-modern" -> {
                Card(
                    modifier = Modifier
                        .width(145.dp)
                        .height(190.dp)
                        .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(2.dp)),
                    shape = RoundedCornerShape(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBF7)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = cityName.ifBlank { "ROMÂNIA" }.take(12), color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(text = "$4.90", color = Color(0xFF2E7D32), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Black)
                        ) {
                            RenderInnerFeed()
                        }
                        Text(
                            text = "${captionText.ifBlank { "TRAVEL" }} • $currentDate",
                            color = Color.DarkGray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp).clickable { onEditCaption?.invoke() }
                        )
                    }
                }
            }

            "film35" -> {
                Card(
                    modifier = Modifier
                        .width(142.dp)
                        .height(188.dp),
                    shape = RoundedCornerShape(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121214)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "▸ KODAK 400 • $cityName",
                            color = Color(0xFFFFB300),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.Start).padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Black)
                        ) {
                            RenderInnerFeed()
                        }
                        Text(
                            text = "${captionText.ifBlank { "ISO 400" }} • $currentDate",
                            color = Color.LightGray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            "circle" -> {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(Color(0xFFFFB300), CircleShape)
                        .border(3.dp, Color(0xFFB78103), CircleShape)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.Black)
                    ) {
                        RenderInnerFeed()
                    }
                }
            }

            "postcard" -> {
                Card(
                    modifier = Modifier
                        .width(145.dp)
                        .height(185.dp)
                        .border(2.dp, Color(0xFF1976D2), RoundedCornerShape(4.dp)),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF7EE)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Black)
                        ) {
                            RenderInnerFeed()
                        }
                        Text(
                            text = "${captionText.ifBlank { "AIR MAIL" }} • $currentDate",
                            color = Color(0xFFC2185B),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = chosenFont,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 6.dp).clickable { onEditCaption?.invoke() }
                        )
                    }
                }
            }

            "halloween" -> {
                Card(
                    modifier = Modifier
                        .width(145.dp)
                        .height(185.dp)
                        .border(2.dp, Color(0xFFEA580C), RoundedCornerShape(2.dp)),
                    shape = RoundedCornerShape(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF180D2B)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🎃 HALLOWEEN", color = Color(0xFFFB923C), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(Color.Black)) {
                            RenderInnerFeed()
                        }
                        Text(text = "🕷️ $fullStampLabel", color = Color(0xFFFDBA74), fontSize = 8.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            "christmas" -> {
                Card(
                    modifier = Modifier
                        .width(145.dp)
                        .height(185.dp)
                        .border(2.dp, Color(0xFFFFD54F), RoundedCornerShape(2.dp)),
                    shape = RoundedCornerShape(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🎄 MERRY CHRISTMAS ❄️", color = Color(0xFFFEF08A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(Color.Black)) {
                            RenderInnerFeed()
                        }
                        Text(text = "🔔 $fullStampLabel", color = Color(0xFFFED7AA), fontSize = 8.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            "love" -> {
                Card(
                    modifier = Modifier
                        .width(145.dp)
                        .height(185.dp)
                        .border(2.dp, Color(0xFFFDA4AF), RoundedCornerShape(2.dp)),
                    shape = RoundedCornerShape(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF831843)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "💖 LOVE FOREVER 🌹", color = Color(0xFFFFE4E6), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(Color.Black)) {
                            RenderInnerFeed()
                        }
                        Text(text = "${captionText.ifBlank { "With Love" }} • $currentDate", color = Color(0xFFFECDD3), fontSize = 8.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            "hawaii" -> {
                Card(
                    modifier = Modifier
                        .width(145.dp)
                        .height(185.dp)
                        .border(2.dp, Color(0xFFD97706), RoundedCornerShape(2.dp)),
                    shape = RoundedCornerShape(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🌺 ALOHA HAWAII 🌴", color = Color(0xFFFEF08A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(Color.Black)) {
                            RenderInnerFeed()
                        }
                        Text(text = "☀️ $fullStampLabel", color = Color(0xFFA7F3D0), fontSize = 8.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            "mafia" -> {
                Card(
                    modifier = Modifier
                        .width(145.dp)
                        .height(185.dp)
                        .border(2.dp, Color(0xFF52525B), RoundedCornerShape(2.dp)),
                    shape = RoundedCornerShape(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF27272A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "★ OMERTÀ // DOSAR #1931 ★", color = Color(0xFFEF4444), fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(Color.Black)) {
                            RenderInnerFeed()
                        }
                        Text(text = "CONFIDENTIAL • $fullStampLabel", color = Color(0xFFD4D4D8), fontSize = 8.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            "madmax" -> {
                Card(
                    modifier = Modifier
                        .width(145.dp)
                        .height(185.dp)
                        .border(2.dp, Color(0xFFEAB308), RoundedCornerShape(2.dp)),
                    shape = RoundedCornerShape(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1917)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "⚡ WITNESS ME! 💀", color = Color(0xFFFACC15), fontSize = 9.sp, fontWeight = FontWeight.Black)
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(Color.Black)) {
                            RenderInnerFeed()
                        }
                        Text(text = "FURY ROAD • $fullStampLabel", color = Color(0xFFEAB308), fontSize = 8.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            "maxpayne" -> {
                Card(
                    modifier = Modifier
                        .width(145.dp)
                        .height(185.dp)
                        .border(3.dp, Color(0xFFFACC15), RoundedCornerShape(0.dp)),
                    shape = RoundedCornerShape(0.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "MAX PAIN 🎯", color = Color(0xFFFACC15), fontSize = 9.sp, fontWeight = FontWeight.Black)
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(0.dp)).background(Color.Black)) {
                            RenderInnerFeed()
                        }
                        Text(text = "„${captionText.ifBlank { "Cold day in hell..." }}” • $currentDate", color = Color(0xFFFEF08A), fontSize = 7.5.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            else -> {
                Card(
                    modifier = Modifier
                        .width(142.dp)
                        .height(188.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 9.dp, end = 9.dp, top = 12.dp, bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Black)
                        ) {
                            RenderInnerFeed()
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clickable { onEditCaption?.invoke() },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (captionText.isNotBlank()) {
                                Text(
                                    text = captionText,
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = chosenFont,
                                    fontStyle = FontStyle.Italic,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "✍️ + scrie mesaj",
                                    color = Color.Gray,
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Text(
                                text = fullStampLabel,
                                color = Color.DarkGray,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
