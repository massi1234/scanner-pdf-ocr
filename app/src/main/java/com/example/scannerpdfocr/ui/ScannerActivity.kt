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
import android.view.View
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
import com.yalantis.ucrop.UCrop
//import org.opencv.core.*
//import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream

class ScannerActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private val scannedBitmaps = mutableListOf<Bitmap>()
    private var currentIndex = 0

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "Permission caméra accordée", Toast.LENGTH_SHORT).show()
            startCamera()
        } else {
            showError("Permission caméra refusée. Accordez-la dans les paramètres.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

//        OpenCVLoader.initDebug()

        requestPermission.launch(Manifest.permission.CAMERA)

        findViewById<Button>(R.id.btn_capture).setOnClickListener { takePhoto() }
        findViewById<Button>(R.id.btn_rotate).setOnClickListener { rotateImage() }
        findViewById<Button>(R.id.btn_filter).setOnClickListener { applyFilter() }
        findViewById<Button>(R.id.btn_crop).setOnClickListener { cropImage() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { saveScan() }
        findViewById<Button>(R.id.btn_share).setOnClickListener { shareScan() }
        findViewById<Button>(R.id.btn_add).setOnClickListener { addAnother() }
    }
    private fun showError(message: String) {
        findViewById<TextView>(R.id.tv_error).text = message
        findViewById<TextView>(R.id.tv_error).visibility = View.VISIBLE
        findViewById<Button>(R.id.btn_back).visibility = View.VISIBLE
        // Hide other elements
        findViewById<PreviewView>(R.id.preview_view).visibility = View.GONE
        findViewById<Button>(R.id.btn_capture).visibility = View.GONE
        findViewById<ImageView>(R.id.img_scanned).visibility = View.GONE
        findViewById<TextView>(R.id.tv_scan_count).visibility = View.GONE
        findViewById<LinearLayout>(R.id.tools_layout).visibility = View.GONE
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
                Toast.makeText(this, "Caméra démarrée", Toast.LENGTH_SHORT).show()
            } catch (exc: Exception) {
                showError("Erreur caméra: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        Toast.makeText(this, "Capture en cours...", Toast.LENGTH_SHORT).show()
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
                        try {
                            val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                            if (bitmap != null) {
                                scannedBitmaps.add(bitmap)
                                currentIndex = scannedBitmaps.size - 1
                                Toast.makeText(this@ScannerActivity, "Image capturée", Toast.LENGTH_SHORT).show()
                                showScannedImage()
                            } else {
                                showError("Erreur chargement image")
                            }
                        } catch (e: Exception) {
                            showError("Erreur: ${e.message}")
                        }
                    } ?: Toast.makeText(this@ScannerActivity, "URI sauvegarde null", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    showError("Erreur capture: ${exception.message}")
                }
            }
        )
    }

    private fun showScannedImage() {
        findViewById<PreviewView>(R.id.preview_view).visibility = View.GONE
        findViewById<Button>(R.id.btn_capture).visibility = View.GONE
        findViewById<ImageView>(R.id.img_scanned).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_scan_count).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.tools_layout).visibility = View.VISIBLE
        updateImageView()
        updateScanCount()
        Toast.makeText(this, "Mode édition activé", Toast.LENGTH_SHORT).show()
    }

    private fun updateImageView() {
        if (scannedBitmaps.isNotEmpty()) {
            findViewById<ImageView>(R.id.img_scanned).setImageBitmap(scannedBitmaps[currentIndex])
        }
    }

    private fun updateScanCount() {
        findViewById<TextView>(R.id.tv_scan_count).text = "Scans: ${scannedBitmaps.size}"
    }

    private fun rotateImage() {
        if (scannedBitmaps.isNotEmpty()) {
            val matrix = Matrix()
            matrix.postRotate(90f)
            scannedBitmaps[currentIndex] = Bitmap.createBitmap(scannedBitmaps[currentIndex], 0, 0, scannedBitmaps[currentIndex].width, scannedBitmaps[currentIndex].height, matrix, true)
            updateImageView()
        }
    }

    private fun applyFilter() {
        if (scannedBitmaps.isNotEmpty()) {
            scannedBitmaps[currentIndex] = ImageUtils.adjustContrast(scannedBitmaps[currentIndex], 1.5f)
            updateImageView()
        }
    }

    private fun cropImage() {
        if (scannedBitmaps.isNotEmpty()) {
            val tempFile = File(cacheDir, "temp_crop.jpg")
            FileOutputStream(tempFile).use { out ->
                scannedBitmaps[currentIndex].compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            val uri = Uri.fromFile(tempFile)
            val destUri = Uri.fromFile(File(cacheDir, "cropped.jpg"))
            UCrop.of(uri, destUri).start(this)
        }
    }

    private fun saveScan() {
        if (scannedBitmaps.isEmpty()) return
        val filename = findViewById<EditText>(R.id.et_filename).text.toString()
        val isPdf = findViewById<RadioButton>(R.id.rb_pdf).isChecked

        if (isPdf) {
            val pdfFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "$filename.pdf")
            val pdfPath = PdfUtil.saveBitmapsAsPdf(this, scannedBitmaps, pdfFile.absolutePath)
            if (pdfPath != null) {
                showSaveNotification(pdfPath)
                Toast.makeText(this, "PDF sauvegardé", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Erreur sauvegarde PDF", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Save as images
            scannedBitmaps.forEachIndexed { index, bitmap ->
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "${filename}_$index.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScannerPdfOcr")
                    }
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { u ->
                    contentResolver.openOutputStream(u)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                }
            }
            Toast.makeText(this, "Images sauvegardées", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSaveNotification(path: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "scan_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Scans", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Scan sauvegardé")
            .setContentText("Fichier: $path")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(1, notification)
    }

    private fun shareScan() {
        if (scannedBitmaps.isEmpty()) return
        val filename = findViewById<EditText>(R.id.et_filename).text.toString()
        val isPdf = findViewById<RadioButton>(R.id.rb_pdf).isChecked

        if (isPdf) {
            val tempFile = File(cacheDir, "$filename.pdf")
            PdfUtil.saveBitmapsAsPdf(this, scannedBitmaps, tempFile.absolutePath)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                tempFile
            )
            val share = Intent(Intent.ACTION_SEND)
            share.type = "application/pdf"
            share.putExtra(Intent.EXTRA_STREAM, uri)
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(share, "Partager PDF"))
        } else {
            // Share first image for simplicity
            val tempFile = File(cacheDir, "$filename.jpg")
            FileOutputStream(tempFile).use { out ->
                scannedBitmaps.first().compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                tempFile
            )
            val share = Intent(Intent.ACTION_SEND)
            share.type = "image/jpeg"
            share.putExtra(Intent.EXTRA_STREAM, uri)
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(share, "Partager Image"))
        }
    }

    private fun addAnother() {
        // Go back to camera to add another scan
        findViewById<PreviewView>(R.id.preview_view).visibility = View.VISIBLE
        findViewById<Button>(R.id.btn_capture).visibility = View.VISIBLE
        findViewById<ImageView>(R.id.img_scanned).visibility = View.GONE
        findViewById<TextView>(R.id.tv_scan_count).visibility = View.GONE
        findViewById<LinearLayout>(R.id.tools_layout).visibility = View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(data ?: return)
            resultUri?.let { uri ->
                scannedBitmaps[currentIndex] = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                updateImageView()
            }
        }
    }
}
