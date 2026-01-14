package com.example.scannerpdfocr.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

object ImageUtils {
    fun adjustContrast(src: Bitmap, contrast: Float): Bitmap {
        // contrast: 1.0 = original, >1 increase, <1 decrease
        val cm = ColorMatrix()
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        cm.set(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        val ret = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        val canvas = Canvas(ret)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return ret
    }

    fun toGrayscale(src: Bitmap): Bitmap {
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val ret = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        val canvas = Canvas(ret)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return ret
    }

    fun saveBitmapToFile(bitmap: Bitmap, file: File, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 90) {
        FileOutputStream(file).use { fos ->
            bitmap.compress(format, quality, fos)
        }
    }
}
