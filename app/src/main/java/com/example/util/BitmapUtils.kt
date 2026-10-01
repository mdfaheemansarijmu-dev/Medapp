package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object BitmapUtils {

    /**
     * Rotates and enhances a Bitmap from image bytes.
     * Automatically downsamples large images (e.g. 12MP-48MP camera photos) to max 1400px
     * using inSampleSize to prevent OutOfMemory, speed up ML Kit OCR by 5x, and reduce payload size.
     */
    fun rotateAndEnhanceImage(imageBytes: ByteArray, maxDimension: Int = 1400): Bitmap {
        // 1. Read bounds only to calculate optimal inSampleSize
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight

        var sampleSize = 1
        val maxOriginal = maxOf(origW, origH)
        while (maxOriginal / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
            ?: return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // 2. Rotate bitmap based on EXIF orientation
        try {
            val inputStream = ByteArrayInputStream(imageBytes)
            val exifInterface = android.media.ExifInterface(inputStream)
            val orientation = exifInterface.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            val rotationDegrees = when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = rotatedBitmap
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Further scale if still larger than maxDimension
        val currentMax = maxOf(bitmap.width, bitmap.height)
        if (currentMax > maxDimension) {
            val scale = maxDimension.toFloat() / currentMax
            val scaledW = (bitmap.width * scale).toInt()
            val scaledH = (bitmap.height * scale).toInt()
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
            if (scaledBitmap != bitmap) {
                bitmap.recycle()
                bitmap = scaledBitmap
            }
        }

        // 4. Enhance contrast (1.25x) and slight brightness adjustment for clean OCR legibility
        try {
            val config = bitmap.config ?: Bitmap.Config.ARGB_8888
            val enhancedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, config)
            val canvas = Canvas(enhancedBitmap)
            val paint = Paint()
            
            val contrast = 1.25f
            val brightness = -10f
            val cm = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            bitmap.recycle()
            bitmap = enhancedBitmap
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return bitmap
    }

    /**
     * Converts a Bitmap to ByteArray (JPEG)
     */
    fun bitmapToByteArray(bitmap: Bitmap, quality: Int = 82): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}
