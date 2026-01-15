package com.example.scannerpdfocr.util

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import java.io.FileOutputStream

object PdfUtil {
    fun saveBitmapAsPdf(context: Context, bitmap: Bitmap, fullPath: String): String? {
        val doc = PDDocument()
        val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
        doc.addPage(page)
        val pdImage = LosslessFactory.createFromImage(doc, bitmap)
        val content = PDPageContentStream(doc, page)
        content.drawImage(pdImage, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        content.close()

        val outFile = File(fullPath)
        FileOutputStream(outFile).use { fos ->
            doc.save(fos)
        }
        doc.close()
        return outFile.absolutePath
    }

    fun saveBitmapsAsPdf(context: Context, bitmaps: List<Bitmap>, fullPath: String): String? {
        val doc = PDDocument()
        for (bitmap in bitmaps) {
            val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
            doc.addPage(page)
            val pdImage = LosslessFactory.createFromImage(doc, bitmap)
            val content = PDPageContentStream(doc, page)
            content.drawImage(pdImage, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            content.close()
        }

        val outFile = File(fullPath)
        FileOutputStream(outFile).use { fos ->
            doc.save(fos)
        }
        doc.close()
        return outFile.absolutePath
    }
}
