package com.example.dualpolaroidcamera.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
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
    offsetX: Float,
    offsetY: Float,
    rotationDegrees: Float,
    scale: Float = 1.0f,
    frameStyle: String,
    isUIHidden: Boolean = false,
    onDrag: (Float, Float) -> Unit,
    onRotate: (Float) -> Unit,
    onNudgeRotate: (Float) -> Unit,
    onScaleChange: ((Float) -> Unit)? = null,
    onTriggerFocus: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentDate = remember {
        val formatter = SimpleDateFormat("MMM. dd, yyyy", Locale.US)
        formatter.format(Date()).uppercase(Locale.US)
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer {
                this.rotationZ = rotationDegrees
                this.scaleX = scale
                this.scaleY = scale
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        if (!isUIHidden) {
            // Bară fină deasupra ramei pentru Rotire, Mărime și Mișcare
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-28).dp)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
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
                    text = if (rotationDegrees != 0f) "${rotationDegrees.toInt()}°" else "Trage",
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
                    modifier = Modifier.clickable { onScaleChange?.invoke((scale - 0.15f).coerceAtLeast(0.5f)) }
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
                    modifier = Modifier.clickable { onScaleChange?.invoke((scale + 0.15f).coerceAtMost(2.2f)) }
                )
            }

            // Buton Focus rapid pe colțul stânga-sus
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-10).dp, y = (-10).dp)
                    .size(26.dp)
                    .background(Color(0xCC000000), CircleShape)
                    .border(1.dp, Color(0xFF00E676), CircleShape)
                    .clickable { onTriggerFocus?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🎯", fontSize = 12.sp)
            }

            // Buton rotund de Rotire directă (trage de el pe colțul din dreapta-sus)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
                    .size(26.dp)
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
        }

        // Corpul Ramei în funcție de stilul ales
        when (frameStyle) {
            "stamp-vintage", "stamp" -> {
                // Timbru Poștal Vintage Gravat (Model 1: Benjamin Franklin 1¢)
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
                            text = "★ UNITED STATES POSTAGE ★",
                            color = Color.White,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
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
                            AndroidView(
                                factory = { frontPreviewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "1¢", color = Color(0xFFBEE3F8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(text = "ONE CENT", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(text = "1¢", color = Color(0xFFBEE3F8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "stamp-modern" -> {
                // Timbru Poștal Modern (Model 2: Travel Stamp)
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
                            Text(text = "ROMÂNIA", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(text = "$4.90", color = Color(0xFF2E7D32), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { frontPreviewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            text = "TRAVEL STAMP • $currentDate",
                            color = Color.DarkGray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            "film35" -> {
                // Film 35mm Negativ cu perforații
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
                            text = "▸ KODAK 400 • 24A",
                            color = Color(0xFFFFB300),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start).padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { frontPreviewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            text = "ISO 400 • $currentDate",
                            color = Color.LightGray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            "circle" -> {
                // Medalion Rotund cu Ramă Aurie
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
                        AndroidView(
                            factory = { frontPreviewView },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            "postcard" -> {
                // Vedere Retro Vintage
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
                            AndroidView(
                                factory = { frontPreviewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            text = "AIR MAIL • $currentDate",
                            color = Color(0xFFC2185B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 6.dp)
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
                            AndroidView(factory = { frontPreviewView }, modifier = Modifier.fillMaxSize())
                        }
                        Text(text = "🕷️ SPOOKY NIGHT • $currentDate", color = Color(0xFFFDBA74), fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
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
                            AndroidView(factory = { frontPreviewView }, modifier = Modifier.fillMaxSize())
                        }
                        Text(text = "🔔 $currentDate", color = Color(0xFFFED7AA), fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
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
                            AndroidView(factory = { frontPreviewView }, modifier = Modifier.fillMaxSize())
                        }
                        Text(text = "With Love • $currentDate", color = Color(0xFFFECDD3), fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
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
                            AndroidView(factory = { frontPreviewView }, modifier = Modifier.fillMaxSize())
                        }
                        Text(text = "☀️ $currentDate", color = Color(0xFFA7F3D0), fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
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
                            AndroidView(factory = { frontPreviewView }, modifier = Modifier.fillMaxSize())
                        }
                        Text(text = "CONFIDENTIAL • $currentDate", color = Color(0xFFD4D4D8), fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
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
                            AndroidView(factory = { frontPreviewView }, modifier = Modifier.fillMaxSize())
                        }
                        Text(text = "FURY ROAD • $currentDate", color = Color(0xFFEAB308), fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
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
                            AndroidView(factory = { frontPreviewView }, modifier = Modifier.fillMaxSize())
                        }
                        Text(text = "„Cold day in hell...” • $currentDate", color = Color(0xFFFEF08A), fontSize = 7.5.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            else -> {
                // Polaroid Clasic - Marginea de sus este garantat curată și vizibilă!
                Card(
                    modifier = Modifier
                        .width(142.dp)
                        .height(184.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 9.dp, end = 9.dp, top = 12.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { frontPreviewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentDate,
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
