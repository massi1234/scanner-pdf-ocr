package com.example.scannerpdfocr.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.scannerpdfocr.R
import com.example.scannerpdfocr.util.PdfUtil
import com.example.scannerpdfocr.util.OcrUtil
import com.example.scannerpdfocr.util.ImageUtils
import com.example.scannerpdfocr.MyApp
import com.example.scannerpdfocr.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import android.widget.Button
import android.widget.TextView
import android.widget.ImageView
import android.content.Intent
import android.widget.Toast
import androidx.activity.viewModels

class ImageEditorActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()
    private var sourceUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_editor)

        intent.getStringExtra("image_uri")?.let {
            sourceUri = Uri.parse(it)
            loadImageAndProcess()
        }

        // --- Génération PDF ---
        findViewById<Button>(R.id.btn_generate_pdf).setOnClickListener {
            sourceUri?.let { uri ->
                Toast.makeText(this, "Génération du PDF en cours...", Toast.LENGTH_SHORT).show()
                CoroutineScope(Dispatchers.IO).launch {
                    val bmp = loadBitmap(uri)
                    val pdfPath = PdfUtil.saveBitmapAsPdf(
                            this@ImageEditorActivity,
                            bmp,
                            "scan_${System.currentTimeMillis()}.pdf"
                    )
                    runOnUiThread {
                        vm.pdfPath.value = pdfPath
                        Toast.makeText(this@ImageEditorActivity, "PDF généré", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: Toast.makeText(this, "Aucune image chargée", Toast.LENGTH_SHORT).show()
        }

        // --- Recadrage ---
        findViewById<Button>(R.id.btn_crop).setOnClickListener {
            sourceUri?.let { uri ->
                val destUri = Uri.fromFile(
                        java.io.File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                )
                com.yalantis.ucrop.UCrop.of(uri, destUri).start(this)
                Toast.makeText(this, "Outil de recadrage ouvert", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "Aucune image chargée", Toast.LENGTH_SHORT).show()
        }

        // --- Noir & Blanc ---
        findViewById<Button>(R.id.btn_bw).setOnClickListener {
            sourceUri?.let { uri ->
                Toast.makeText(this, "Conversion en noir et blanc...", Toast.LENGTH_SHORT).show()
                CoroutineScope(Dispatchers.IO).launch {
                    val bmp = loadBitmap(uri)
                    val out = ImageUtils.toGrayscale(bmp)
                    val f = java.io.File(cacheDir, "bw_${System.currentTimeMillis()}.jpg")
                    ImageUtils.saveBitmapToFile(out, f)
                    sourceUri = Uri.fromFile(f)
                    runOnUiThread { findViewById<ImageView>(R.id.img_preview).setImageBitmap(out) }
                }
            } ?: Toast.makeText(this, "Aucune image chargée", Toast.LENGTH_SHORT).show()
        }

        // --- Contraste ---
        var contrast = 1.0f

        findViewById<Button>(R.id.btn_contrast_inc).setOnClickListener {
            sourceUri?.let { uri ->
                contrast += 0.2f
                Toast.makeText(this, "Augmentation du contraste...", Toast.LENGTH_SHORT).show()
                CoroutineScope(Dispatchers.IO).launch {
                    val bmp = loadBitmap(uri)
                    val out = ImageUtils.adjustContrast(bmp, contrast)
                    val f = java.io.File(cacheDir, "contrast_${System.currentTimeMillis()}.jpg")
                    ImageUtils.saveBitmapToFile(out, f)
                    sourceUri = Uri.fromFile(f)
                    runOnUiThread { findViewById<ImageView>(R.id.img_preview).setImageBitmap(out) }
                }
            } ?: Toast.makeText(this, "Aucune image chargée", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_contrast_dec).setOnClickListener {
            sourceUri?.let { uri ->
                contrast = (contrast - 0.2f).coerceAtLeast(0.2f)
                Toast.makeText(this, "Diminution du contraste...", Toast.LENGTH_SHORT).show()
                CoroutineScope(Dispatchers.IO).launch {
                    val bmp = loadBitmap(uri)
                    val out = ImageUtils.adjustContrast(bmp, contrast)
                    val f = java.io.File(cacheDir, "contrast_${System.currentTimeMillis()}.jpg")
                    ImageUtils.saveBitmapToFile(out, f)
                    sourceUri = Uri.fromFile(f)
                    runOnUiThread { findViewById<ImageView>(R.id.img_preview).setImageBitmap(out) }
                }
            } ?: Toast.makeText(this, "Aucune image chargée", Toast.LENGTH_SHORT).show()
        }

        // --- OCR ---
        findViewById<Button>(R.id.btn_ocr).setOnClickListener {
            val prefs = getSharedPreferences("scanner_prefs", MODE_PRIVATE)
            val isPremium = prefs.getBoolean("is_premium", false)

            if (!isPremium) {
                if (MyApp.adManager.hasRewarded()) {
                    MyApp.adManager.showRewarded(this) {
                        prefs.edit().putBoolean("is_premium", true).apply()
                        Toast.makeText(this, "OCR en cours...", Toast.LENGTH_SHORT).show()
                        doOcr(sourceUri)
                    }
                } else {
                    Toast.makeText(this, "Publicité non disponible, OCR gratuit cette fois", Toast.LENGTH_SHORT).show()
                    Toast.makeText(this, "OCR en cours...", Toast.LENGTH_SHORT).show()
                    doOcr(sourceUri)
                }
            } else {
                Toast.makeText(this, "OCR en cours...", Toast.LENGTH_SHORT).show()
                doOcr(sourceUri)
            }
        }

        // --- Partage PDF ---
        findViewById<Button>(R.id.btn_share_pdf).setOnClickListener {
            vm.pdfPath.value?.let { shareFile(it) }
        }

        // --- Partage texte OCR ---
        findViewById<Button>(R.id.btn_share_text).setOnClickListener {
            vm.ocrText.value?.let { text ->
                val share = Intent(Intent.ACTION_SEND)
                share.type = "text/plain"
                share.putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(share, "Partager le texte"))
            } ?: Toast.makeText(this, "Effectuez l'OCR d'abord", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImageAndProcess() {
        sourceUri?.let { uri ->
            val bmp = loadBitmap(uri)
            findViewById<ImageView>(R.id.img_preview).setImageBitmap(bmp)
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        val input: InputStream? = contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(input)
    }

    private fun doOcr(uri: Uri?) {
        uri?.let { u ->
            CoroutineScope(Dispatchers.IO).launch {
                val bmp = loadBitmap(u)
                val text = OcrUtil.recognizeText(bmp)
                runOnUiThread {
                    findViewById<TextView>(R.id.txt_ocr_result).text = text
                    vm.ocrText.value = text
                }
            }
        }
    }

    private fun shareFile(path: String) {
        val file = java.io.File(path)
        if (!file.exists()) return

        val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
        )

        val share = Intent(Intent.ACTION_SEND)
        share.type = "application/pdf"
        share.putExtra(Intent.EXTRA_STREAM, uri)
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        startActivity(Intent.createChooser(share, "Partager PDF"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == com.yalantis.ucrop.UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            val resultUri = com.yalantis.ucrop.UCrop.getOutput(data ?: return)
            resultUri?.let {
                sourceUri = it
                val bmp = loadBitmap(it)
                findViewById<ImageView>(R.id.img_preview).setImageBitmap(bmp)
            }
        }
    }
}
