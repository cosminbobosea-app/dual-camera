package com.example.dualpolaroidcamera.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ShutterButton(
    isEnabled: Boolean,
    isLoading: Boolean,
    isBackPrimary: Boolean = true,
    isUIHidden: Boolean = false,
    onToggleSettings: () -> Unit = {},
    onClick: () -> Unit,
    onFlipCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Buton Setări (Afișează / Ascunde panourile de setări)
        Column(
            modifier = Modifier
                .size(52.dp)
                .background(
                    if (!isUIHidden) Color(0x33F59E0B) else Color(0xCC18181B),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    BorderStroke(1.5.dp, if (!isUIHidden) Color(0xFFFFB300) else Color(0x33FFFFFF)),
                    RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onToggleSettings),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "⚙️", fontSize = 16.sp)
            Text(
                text = "Setări",
                color = if (!isUIHidden) Color(0xFFFCD34D) else Color.LightGray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Declanșator Foto Polaroid Principal
        Box(
            modifier = Modifier
                .size(76.dp)
                .border(BorderStroke(4.dp, Color.White), CircleShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(50.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) Color.White else Color.Gray)
                        .clickable(
                            enabled = isEnabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
                )
            }
        }

        // Buton Comutare Cameră Spate / Față
        Column(
            modifier = Modifier
                .size(52.dp)
                .background(Color(0xCC18181B), RoundedCornerShape(16.dp))
                .border(BorderStroke(1.5.dp, Color(0xFFFFB300)), RoundedCornerShape(16.dp))
                .clickable(enabled = isEnabled, onClick = onFlipCamera),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "⇄", color = Color(0xFFFFB300), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (isBackPrimary) "Spate" else "Față",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
