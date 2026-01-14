package com.example.scannerpdfocr.util

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OcrUtil {

    suspend fun recognizeText(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)

        // Client ML Kit avec options explicites (obligatoire dans ta version)
        val recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        )

        val result = recognizer.process(image).await()
        return result.text
    }
}
