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
        keepFrontBitmap: Boolean = false
    ): Boolean = withContext(Dispatchers.Default) {
        val finalWidth = backBitmap.width
        val finalHeight = backBitmap.height

        val finalBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)

        // Creare Paint cu Filtru de Culoare și Luminozitate
        val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            val cm = ColorMatrix()
            // Luminozitate
            if (brightness != 0f) {
                val bScale = (100f + brightness) / 100f
                cm.setScale(bScale, bScale, bScale, 1f)
            }
            // Filtru
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

        // 1.5. Desenează Rama Pozei Mari (Big Frame)
        when (bigFrameStyle) {
            "postcard-full" -> {
                val pad = (finalWidth.coerceAtMost(finalHeight) * 0.03f)
                val borderPaint = Paint().apply {
                    color = Color.parseColor("#A08D6E63")
                    style = Paint.Style.STROKE
                    strokeWidth = (finalWidth * 0.005f).coerceAtLeast(4f)
                }
                canvas.drawRect(pad, pad, finalWidth - pad, finalHeight - pad, borderPaint)

                // Ștampilă poștală albastră în colțul din dreapta-sus
                val postmarkPaint = Paint().apply {
                    color = Color.parseColor("#B30288D1")
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                canvas.drawCircle(finalWidth - pad - 120f, pad + 100f, 60f, postmarkPaint)
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
                val borderPaint = Paint().apply { color = Color.WHITE }
                canvas.drawRect(0f, 0f, finalWidth.toFloat(), borderW, borderPaint)
                canvas.drawRect(0f, 0f, borderW, finalHeight.toFloat(), borderPaint)
                canvas.drawRect(finalWidth - borderW, 0f, finalWidth.toFloat(), finalHeight.toFloat(), borderPaint)
                canvas.drawRect(0f, finalHeight - borderW * 2.5f, finalWidth.toFloat(), finalHeight.toFloat(), borderPaint)
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

        // 2. Calculează dimensiunile ramei
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
            // Medalion auriu
            val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 179, 0)
                style = Paint.Style.STROKE
                strokeWidth = polaroidWidth * 0.05f
            }
            canvas.drawCircle(centerX, centerY, polaroidWidth * 0.48f, goldPaint)

            // Selfie rotund decupat
            val photoDestRect = RectF(
                centerX - polaroidWidth * 0.44f,
                centerY - polaroidWidth * 0.44f,
                centerX + polaroidWidth * 0.44f,
                centerY + polaroidWidth * 0.44f
            )
            val photoSrcRect = Rect(0, 0, frontBitmap.width, frontBitmap.height)
            canvas.drawBitmap(frontBitmap, photoSrcRect, photoDestRect, imagePaint)
        } else {
            // Desenează corp ramă dreptunghiulară cu fundal tematic
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
                // Perforații zimțate de timbru poștal pe margini
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
                        textSize = polaroidWidth * 0.045f
                        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("★ UNITED STATES POSTAGE ★", polaroidRect.centerX(), polaroidRect.top + polaroidHeight * 0.09f, archTextPaint)
                }
            }

            // Calculează poziția pozei din ramă
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

            // Text data și inscripție stilizată
            val dateFormat = SimpleDateFormat("MMM. dd, yyyy", Locale.US)
            val dateText = dateFormat.format(Date()).uppercase(Locale.US)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (frameStyle == "film35") Color.WHITE else Color.BLACK
                textSize = polaroidHeight * 0.052f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            val textX = polaroidRect.centerX()
            val textY = photoBottom + ((polaroidRect.bottom - photoBottom) / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(dateText, textX, textY, textPaint)
        }

        // Încheie rotirea Canvas
        canvas.restore()

        // Eliberare resurse memorie
        backBitmap.recycle()
        if (!keepFrontBitmap) {
            frontBitmap.recycle()
        }

        // Salvare în Galerie prin MediaStore
        return@withContext saveToMediaStore(context, finalBitmap)
    }

    private suspend fun saveToMediaStore(context: Context, bitmap: Bitmap): Boolean =
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
                ?: return@withContext false

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
            return@withContext isSuccess
        }
}
