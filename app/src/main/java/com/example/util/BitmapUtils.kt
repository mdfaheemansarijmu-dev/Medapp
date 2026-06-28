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
     */
    fun rotateAndEnhanceImage(imageBytes: ByteArray): Bitmap {
        // Decode bitmap
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
        }
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
            ?: return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Rotate bitmap based on EXIF orientation
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

        // Enhance image (Contrast and Brightness) using ColorMatrix
        try {
            val config = bitmap.config ?: Bitmap.Config.ARGB_8888
            val enhancedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, config)
            val canvas = Canvas(enhancedBitmap)
            val paint = Paint()
            
            // Adjust contrast (1.35x) and slight brightness offset (-15f) for clearer text recognition
            val contrast = 1.35f
            val brightness = -15f
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
    fun bitmapToByteArray(bitmap: Bitmap, quality: Int = 85): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}
