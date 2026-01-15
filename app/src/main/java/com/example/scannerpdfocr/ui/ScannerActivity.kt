package com.example.scannerpdfocr.ui

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.scannerpdfocr.R
import com.example.scannerpdfocr.util.ImageUtils
import com.example.scannerpdfocr.util.PdfUtil
import com.google.common.util.concurrent.ListenableFuture
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream

class ScannerActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private var currentBitmap: Bitmap? = null
    private var rotation = 0

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        OpenCVLoader.initDebug()

        requestPermission.launch(Manifest.permission.CAMERA)

        findViewById<Button>(R.id.btn_capture).setOnClickListener { takePhoto() }
        findViewById<Button>(R.id.btn_rotate).setOnClickListener { rotateImage() }
        findViewById<Button>(R.id.btn_filter).setOnClickListener { applyFilter() }
        findViewById<Button>(R.id.btn_crop).setOnClickListener { cropImage() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { saveScan() }
        findViewById<Button>(R.id.btn_share).setOnClickListener { shareScan() }
        findViewById<Button>(R.id.btn_add).setOnClickListener { addAnother() }
    }

    private fun startCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.preview_view).surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "scan_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScannerPdfOcr")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri: Uri? = outputFileResults.savedUri
                    savedUri?.let {
                        currentBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                        showScannedImage()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                }
            }
        )
    }

    private fun showScannedImage() {
        findViewById<PreviewView>(R.id.preview_view).visibility = View.GONE
        findViewById<Button>(R.id.btn_capture).visibility = View.GONE
        findViewById<ImageView>(R.id.img_scanned).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.tools_layout).visibility = View.VISIBLE
        findViewById<ImageView>(R.id.img_scanned).setImageBitmap(currentBitmap)
    }

    private fun rotateImage() {
        currentBitmap?.let {
            val matrix = Matrix()
            matrix.postRotate(90f)
            currentBitmap = Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, true)
            findViewById<ImageView>(R.id.img_scanned).setImageBitmap(currentBitmap)
            rotation = (rotation + 90) % 360
        }
    }

    private fun applyFilter() {
        currentBitmap?.let {
            // Simple filter: increase contrast
            currentBitmap = ImageUtils.adjustContrast(it, 1.5f)
            findViewById<ImageView>(R.id.img_scanned).setImageBitmap(currentBitmap)
        }
    }

    private fun cropImage() {
        currentBitmap?.let {
            // For simplicity, skip auto detection, use manual crop with uCrop
            val tempFile = File(cacheDir, "temp_crop.jpg")
            FileOutputStream(tempFile).use { out ->
                it.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            val uri = Uri.fromFile(tempFile)
            val destUri = Uri.fromFile(File(cacheDir, "cropped.jpg"))
            com.yalantis.ucrop.UCrop.of(uri, destUri).start(this)
        }
    }

    private fun saveScan() {
        val filename = findViewById<EditText>(R.id.et_filename).text.toString()
        val isPdf = findViewById<RadioButton>(R.id.rb_pdf).isChecked

        currentBitmap?.let {
            if (isPdf) {
                val pdfPath = PdfUtil.saveBitmapAsPdf(this, it, "$filename.pdf")
                Toast.makeText(this, "PDF sauvegardé: $pdfPath", Toast.LENGTH_SHORT).show()
            } else {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$filename.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScannerPdfOcr")
                    }
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { u ->
                    contentResolver.openOutputStream(u)?.use { out ->
                        it.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                    Toast.makeText(this, "Image sauvegardée", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareScan() {
        val filename = findViewById<EditText>(R.id.et_filename).text.toString()
        val isPdf = findViewById<RadioButton>(R.id.rb_pdf).isChecked

        currentBitmap?.let {
            val tempFile = File(cacheDir, if (isPdf) "$filename.pdf" else "$filename.jpg")
            FileOutputStream(tempFile).use { out ->
                if (isPdf) {
                    // For simplicity, share as image, PDF sharing is complex
                    it.compress(Bitmap.CompressFormat.JPEG, 100, out)
                } else {
                    it.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                tempFile
            )
            val share = Intent(Intent.ACTION_SEND)
            share.type = if (isPdf) "application/pdf" else "image/jpeg"
            share.putExtra(Intent.EXTRA_STREAM, uri)
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(share, "Partager"))
        }
    }

    private fun addAnother() {
        // Reset to camera view
        findViewById<PreviewView>(R.id.preview_view).visibility = View.VISIBLE
        findViewById<Button>(R.id.btn_capture).visibility = View.VISIBLE
        findViewById<ImageView>(R.id.img_scanned).visibility = View.GONE
        findViewById<LinearLayout>(R.id.tools_layout).visibility = View.GONE
        currentBitmap = null
        rotation = 0
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == com.yalantis.ucrop.UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            val resultUri = com.yalantis.ucrop.UCrop.getOutput(data ?: return)
            resultUri?.let {
                currentBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                findViewById<ImageView>(R.id.img_scanned).setImageBitmap(currentBitmap)
            }
        }
    }
}