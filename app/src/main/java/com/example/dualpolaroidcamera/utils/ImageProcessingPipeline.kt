package com.example.dualpolaroidcamera.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageProcessingPipeline {

    fun imageProxyToBitmap(image: ImageProxy, isFrontCamera: Boolean): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val matrix = Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    suspend fun createAndSavePolaroid(
        context: Context,
        backBitmap: Bitmap,
        frontBitmap: Bitmap,
        relativePolaroidX: Float,
        relativePolaroidY: Float,
        polaroidWidthRatio: Float = 0.36f,
        frameStyle: String = "stamp-vintage",
        bigFrameStyle: String = "none",
        rotationDegrees: Float = 0f,
        filter: String = "standard",
        brightness: Float = 0f,
        captionText: String = "",
        captionFont: String = "handwriting",
        cityName: String = "BUCUREȘTI",
        keepFrontBitmap: Boolean = false
    ): Pair<Uri?, Bitmap?> = withContext(Dispatchers.Default) {
        val finalWidth = backBitmap.width
        val finalHeight = backBitmap.height

        val finalBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)

        val dateFormat = SimpleDateFormat("MMM. dd, yyyy", Locale.US)
        val dateText = dateFormat.format(Date()).uppercase(Locale.US)
        val fullStampHeader = if (cityName.isNotBlank()) "${cityName.uppercase()} • $dateText" else dateText

        val selectedTypeface = when (captionFont) {
            "handwriting" -> Typeface.create("cursive", Typeface.ITALIC)
            "retro" -> Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            "typewriter" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        // Creare Paint cu Filtru de Culoare și Luminozitate
        val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            val cm = ColorMatrix()
            if (brightness != 0f) {
                val bScale = (100f + brightness) / 100f
                cm.setScale(bScale, bScale, bScale, 1f)
            }
            when (filter) {
                "vintage-warm" -> {
                    val warm = ColorMatrix(floatArrayOf(
                        1.2f, 0f, 0f, 0f, 20f,
                        0f, 1.05f, 0f, 0f, 10f,
                        0f, 0f, 0.85f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    cm.postConcat(warm)
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
                    val cyan = ColorMatrix(floatArrayOf(
                        0.9f, 0f, 0f, 0f, 0f,
                        0f, 1.15f, 0f, 0f, 10f,
                        0f, 0f, 1.25f, 0f, 25f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    cm.postConcat(cyan)
                }
                "sepia-classic" -> {
                    val sepia = ColorMatrix(floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    cm.postConcat(sepia)
                }
            }
            colorFilter = ColorMatrixColorFilter(cm)
        }

        // 1. Desenează imaginea de fundal pe tot ecranul cu filtru aplicat
        canvas.drawBitmap(backBitmap, 0f, 0f, imagePaint)

        // 1.5. Desenează Rama Pozei Mari (Big Frame) + Ștampilă Dinamică + Mesaj Personalizat
        when (bigFrameStyle) {
            "postcard-full" -> {
                val pad = (finalWidth.coerceAtMost(finalHeight) * 0.03f)
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#A08D6E63")
                    style = Paint.Style.STROKE
                    strokeWidth = (finalWidth * 0.005f).coerceAtLeast(4f)
                }
                canvas.drawRect(pad, pad, finalWidth - pad, finalHeight - pad, borderPaint)

                // Ștampilă poștală albastră în colțul din dreapta-sus cu Orașul și Data
                val postX = finalWidth - pad - 120f
                val postY = pad + 100f
                val postmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#D938BDF8")
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
                canvas.drawCircle(postX, postY, 64f, postmarkPaint)

                val stampTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#E0F2FE")
                    textSize = 20f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(cityName.ifBlank { "POST" }.take(10), postX, postY - 12f, stampTextPaint)
                canvas.drawText(dateText.take(8), postX, postY + 18f, stampTextPaint)

                // Mesaj scris de mână în partea de jos a vederii poștale
                val msgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FFFBEB")
                    textSize = (finalWidth * 0.026f).coerceAtLeast(28f)
                    typeface = selectedTypeface
                    setShadowLayer(6f, 0f, 2f, Color.BLACK)
                }
                val postcardMsg = "„${captionText.ifBlank { "Greetings from our journey! Wish you were here!" }}”"
                canvas.drawText(postcardMsg, pad + 32f, finalHeight - pad - 36f, msgPaint)
            }
            "film-sprockets" -> {
                val trackH = finalHeight * 0.07f
                val trackPaint = Paint().apply { color = Color.parseColor("#EE121214") }
                canvas.drawRect(0f, 0f, finalWidth.toFloat(), trackH, trackPaint)
                canvas.drawRect(0f, finalHeight - trackH, finalWidth.toFloat(), finalHeight.toFloat(), trackPaint)
            }
            "stamp-border" -> {
                val borderPaint = Paint().apply {
                    color = Color.parseColor("#FFFDFBF7")
                    style = Paint.Style.STROKE
                    strokeWidth = 14f
                }
                canvas.drawRect(10f, 10f, finalWidth - 10f, finalHeight - 10f, borderPaint)
            }
            "polaroid-macro" -> {
                val borderW = finalWidth * 0.04f
                val bottomH = borderW * 2.8f
                val borderPaint = Paint().apply { color = Color.WHITE }
                canvas.drawRect(0f, 0f, finalWidth.toFloat(), borderW, borderPaint)
                canvas.drawRect(0f, 0f, borderW, finalHeight.toFloat(), borderPaint)
                canvas.drawRect(finalWidth - borderW, 0f, finalWidth.toFloat(), finalHeight.toFloat(), borderPaint)
                canvas.drawRect(0f, finalHeight - bottomH, finalWidth.toFloat(), finalHeight.toFloat(), borderPaint)

                val macroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = (finalWidth * 0.028f).coerceAtLeast(28f)
                    typeface = selectedTypeface
                    textAlign = Paint.Align.CENTER
                }
                val bottomLabel = if (captionText.isNotBlank()) "$captionText • $fullStampHeader" else fullStampHeader
                canvas.drawText(bottomLabel, finalWidth / 2f, finalHeight - bottomH / 2f + 10f, macroPaint)
            }
            "halloween" -> {
                val p = Paint().apply { color = Color.parseColor("#EA580C"); style = Paint.Style.STROKE; strokeWidth = 24f }
                canvas.drawRect(12f, 12f, finalWidth - 12f, finalHeight - 12f, p)
            }
            "christmas" -> {
                val p = Paint().apply { color = Color.parseColor("#15803D"); style = Paint.Style.STROKE; strokeWidth = 24f }
                canvas.drawRect(12f, 12f, finalWidth - 12f, finalHeight - 12f, p)
            }
            "love" -> {
                val p = Paint().apply { color = Color.parseColor("#F43F5E"); style = Paint.Style.STROKE; strokeWidth = 20f }
                canvas.drawRect(10f, 10f, finalWidth - 10f, finalHeight - 10f, p)
            }
            "hawaii" -> {
                val p = Paint().apply { color = Color.parseColor("#D97706"); style = Paint.Style.STROKE; strokeWidth = 24f }
                canvas.drawRect(12f, 12f, finalWidth - 12f, finalHeight - 12f, p)
            }
            "mafia" -> {
                val p = Paint().apply { color = Color.parseColor("#27272A"); style = Paint.Style.STROKE; strokeWidth = 24f }
                canvas.drawRect(12f, 12f, finalWidth - 12f, finalHeight - 12f, p)
            }
            "madmax" -> {
                val p = Paint().apply { color = Color.parseColor("#EAB308"); style = Paint.Style.STROKE; strokeWidth = 24f }
                canvas.drawRect(12f, 12f, finalWidth - 12f, finalHeight - 12f, p)
            }
            "maxpayne" -> {
                val p = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 28f }
                canvas.drawRect(14f, 14f, finalWidth - 14f, finalHeight - 14f, p)
            }
        }

        // 2. Calculează dimensiunile ramei mici
        val polaroidWidth = finalWidth * polaroidWidthRatio
        val polaroidHeight = if (frameStyle == "circle") polaroidWidth else polaroidWidth * (180f / 140f)

        val maxX = finalWidth - polaroidWidth
        val maxY = finalHeight - polaroidHeight
        val polaroidLeft = (relativePolaroidX * finalWidth).coerceIn(0f, maxX)
        val polaroidTop = (relativePolaroidY * finalHeight).coerceIn(0f, maxY)
        val polaroidRect = RectF(
            polaroidLeft,
            polaroidTop,
            polaroidLeft + polaroidWidth,
            polaroidTop + polaroidHeight
        )

        // 3. Transformare Canvas pentru Rotire liberă la unghiul ales
        canvas.save()
        val centerX = polaroidRect.centerX()
        val centerY = polaroidRect.centerY()
        canvas.rotate(rotationDegrees, centerX, centerY)

        // Umbră realistă
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(
                polaroidWidth * 0.05f,
                0f,
                polaroidWidth * 0.025f,
                Color.argb(140, 0, 0, 0)
            )
        }

        if (frameStyle == "circle") {
            canvas.drawCircle(centerX, centerY, polaroidWidth / 2f, shadowPaint)
            val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 179, 0)
                style = Paint.Style.STROKE
                strokeWidth = polaroidWidth * 0.05f
            }
            canvas.drawCircle(centerX, centerY, polaroidWidth * 0.48f, goldPaint)

            val photoDestRect = RectF(
                centerX - polaroidWidth * 0.44f,
                centerY - polaroidWidth * 0.44f,
                centerX + polaroidWidth * 0.44f,
                centerY + polaroidWidth * 0.44f
            )
            val photoSrcRect = Rect(0, 0, frontBitmap.width, frontBitmap.height)
            canvas.drawBitmap(frontBitmap, photoSrcRect, photoDestRect, imagePaint)
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (frameStyle) {
                    "stamp-vintage", "stamp" -> Color.parseColor("#1A365D")
                    "halloween" -> Color.parseColor("#180D2B")
                    "christmas" -> Color.parseColor("#7F1D1D")
                    "love" -> Color.parseColor("#831843")
                    "hawaii" -> Color.parseColor("#064E3B")
                    "mafia" -> Color.parseColor("#27272A")
                    "madmax" -> Color.parseColor("#1C1917")
                    "maxpayne" -> Color.BLACK
                    else -> Color.WHITE
                }
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(polaroidRect, polaroidWidth * 0.02f, polaroidWidth * 0.02f, bgPaint)

            if (frameStyle == "stamp" || frameStyle == "stamp-vintage" || frameStyle == "stamp-modern") {
                val toothRadius = polaroidWidth * 0.03f
                val toothStep = toothRadius * 2.8f
                val punchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                }
                var x = polaroidRect.left + toothStep / 2f
                while (x < polaroidRect.right) {
                    canvas.drawCircle(x, polaroidRect.top, toothRadius, punchPaint)
                    canvas.drawCircle(x, polaroidRect.bottom, toothRadius, punchPaint)
                    x += toothStep
                }
                var y = polaroidRect.top + toothStep / 2f
                while (y < polaroidRect.bottom) {
                    canvas.drawCircle(polaroidRect.left, y, toothRadius, punchPaint)
                    canvas.drawCircle(polaroidRect.right, y, toothRadius, punchPaint)
                    y += toothStep
                }

                if (frameStyle == "stamp-vintage" || frameStyle == "stamp") {
                    val archTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = polaroidWidth * 0.044f
                        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    val headerTitle = "★ ${cityName.ifBlank { "UNITED STATES" }.uppercase()} POSTAGE ★"
                    canvas.drawText(headerTitle.take(26), polaroidRect.centerX(), polaroidRect.top + polaroidHeight * 0.09f, archTextPaint)
                }
            }

            val sidePadding = polaroidWidth * (10f / 140f)
            val topPadding = polaroidHeight * (12f / 180f)
            val bottomSpace = polaroidHeight * (34f / 180f)

            val photoLeft = polaroidRect.left + sidePadding
            val photoTop = polaroidRect.top + topPadding
            val photoRight = polaroidRect.right - sidePadding
            val photoBottom = polaroidRect.bottom - bottomSpace

            val photoDestRect = RectF(photoLeft, photoTop, photoRight, photoBottom)
            val photoSrcRect = Rect(0, 0, frontBitmap.width, frontBitmap.height)
            canvas.drawBitmap(frontBitmap, photoSrcRect, photoDestRect, imagePaint)

            val textColor = when (frameStyle) {
                "film35", "stamp-vintage", "stamp", "halloween", "christmas", "love", "hawaii", "mafia", "madmax", "maxpayne" -> Color.WHITE
                else -> Color.BLACK
            }

            val textX = polaroidRect.centerX()
            if (captionText.isNotBlank()) {
                val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = polaroidHeight * 0.052f
                    typeface = selectedTypeface
                    textAlign = Paint.Align.CENTER
                }
                val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = polaroidHeight * 0.038f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(captionText.take(24), textX, photoBottom + (polaroidRect.bottom - photoBottom) * 0.44f, captionPaint)
                canvas.drawText(fullStampHeader.take(24), textX, photoBottom + (polaroidRect.bottom - photoBottom) * 0.84f, datePaint)
            } else {
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = polaroidHeight * 0.046f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val textY = photoBottom + ((polaroidRect.bottom - photoBottom) / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
                canvas.drawText(fullStampHeader.take(24), textX, textY, textPaint)
            }
        }

        // Încheie rotirea Canvas
        canvas.restore()

        // Creăm o miniatură (thumbnail) pentru butonul de Galerie înainte de salvarea în MediaStore
        val previewThumb = Bitmap.createScaledBitmap(
            finalBitmap,
            (finalWidth * 0.25f).toInt().coerceAtLeast(200),
            (finalHeight * 0.25f).toInt().coerceAtLeast(200),
            true
        )

        // Eliberare resurse memorie
        backBitmap.recycle()
        if (!keepFrontBitmap) {
            frontBitmap.recycle()
        }

        // Salvare în Galerie prin MediaStore
        val savedUri = saveToMediaStore(context, finalBitmap)
        return@withContext Pair(savedUri, previewThumb)
    }

    private suspend fun saveToMediaStore(context: Context, bitmap: Bitmap): Uri? =
        withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val fileName = "IMG_DUAL_POLAROID_$timestamp.jpg"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/DualPolaroid"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null

            var isSuccess = false
            try {
                resolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                    if (bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                        outputStream.flush()
                        isSuccess = true
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                isSuccess = false
            } finally {
                bitmap.recycle()
            }
            return@withContext if (isSuccess) uri else null
        }
}
