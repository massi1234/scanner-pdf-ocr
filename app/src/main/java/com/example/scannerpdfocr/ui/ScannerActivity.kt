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
import android.os.Environment
import android.provider.MediaStore
import android.view.View
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
import com.yalantis.ucrop.UCrop
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
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

        OpenCVLoader.initDebug()

        requestPermission.launch(Manifest.permission.CAMERA)

        findViewById<Button>(R.id.btn_capture).setOnClickListener { takePhoto() }
        findViewById<Button>(R.id.btn_rotate).setOnClickListener { rotateImage() }
        findViewById<Button>(R.id.btn_filter).setOnClickListener { applyFilter() }
        findViewById<Button>(R.id.btn_crop).setOnClickListener { cropImage() }
        findViewById<Button>(R.id.btn_auto_crop).setOnClickListener { autoCropImage() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { saveScan() }
        findViewById<Button>(R.id.btn_share).setOnClickListener { shareScan() }
        findViewById<Button>(R.id.btn_add).setOnClickListener { addAnother() }
    }

    private fun showError(message: String) {
        findViewById<TextView>(R.id.tv_error).text = message
        findViewById<TextView>(R.id.tv_error).visibility = View.VISIBLE
        findViewById<Button>(R.id.btn_back).visibility = View.VISIBLE
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
            } catch (e: Exception) {
                showError("Erreur caméra: ${e.message}")
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
                        outputFileResults.savedUri?.let { uri ->
                            val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                            bitmap?.let {
                                var processedBitmap = it
                                // Apply auto crop on capture
                                val cropped = detectDocumentEdges(it)
                                if (cropped != null) {
                                    processedBitmap = cropped
                                }
                                scannedBitmaps.add(processedBitmap)
                                currentIndex = scannedBitmaps.lastIndex
                                showScannedImage()
                                Toast.makeText(this@ScannerActivity, "Image capturée", Toast.LENGTH_SHORT).show()
                            } ?: Toast.makeText(this@ScannerActivity, "Erreur décodage bitmap", Toast.LENGTH_SHORT).show()
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
    }

    private fun updateImageView() {
        findViewById<ImageView>(R.id.img_scanned)
                .setImageBitmap(scannedBitmaps[currentIndex])
    }

    private fun updateScanCount() {
        findViewById<TextView>(R.id.tv_scan_count).text =
                "Scans: ${scannedBitmaps.size}"
    }

    private fun rotateImage() {
        val matrix = Matrix().apply { postRotate(90f) }
        scannedBitmaps[currentIndex] =
                Bitmap.createBitmap(
                        scannedBitmaps[currentIndex],
                        0, 0,
                        scannedBitmaps[currentIndex].width,
                        scannedBitmaps[currentIndex].height,
                        matrix,
                        true
                )
        updateImageView()
    }

    private fun applyFilter() {
        scannedBitmaps[currentIndex] =
                ImageUtils.adjustContrast(scannedBitmaps[currentIndex], 1.5f)
        updateImageView()
    }

    private fun cropImage() {
        val tempFile = File(cacheDir, "temp_crop.jpg")
        FileOutputStream(tempFile).use {
            scannedBitmaps[currentIndex].compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
        UCrop.of(
                Uri.fromFile(tempFile),
                Uri.fromFile(File(cacheDir, "cropped.jpg"))
        ).start(this)
    }

    private fun autoCropImage() {
        val bitmap = scannedBitmaps[currentIndex]
        val croppedBitmap = detectDocumentEdges(bitmap)
        if (croppedBitmap != null) {
            scannedBitmaps[currentIndex] = croppedBitmap
            updateImageView()
            Toast.makeText(this, "Auto crop appliqué", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Impossible de détecter les bords", Toast.LENGTH_SHORT).show()
        }
    }

    private fun detectDocumentEdges(bitmap: Bitmap): Bitmap? {
        try {
            val mat = Mat()
            val bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            org.opencv.android.Utils.bitmapToMat(bmp32, mat)

            // Convert to grayscale
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

            // Apply Gaussian blur
            val blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            // Edge detection
            val edges = Mat()
            Imgproc.Canny(blurred, edges, 75.0, 200.0)

            // Find contours
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            // Find the largest contour (assuming it's the document)
            var maxArea = 0.0
            var bestContour: MatOfPoint? = null
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area > maxArea) {
                    maxArea = area
                    bestContour = contour
                }
            }

            if (bestContour != null) {
                // Approximate the contour to a quadrilateral
                val approx = MatOfPoint2f()
                val contour2f = MatOfPoint2f(*bestContour.toArray())
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * Imgproc.arcLength(contour2f, true), true)

                if (approx.toArray().size == 4) {
                    // Perspective transform to straighten the document
                    val points = approx.toArray()
                    val sortedPoints = sortPoints(points)

                    val src = MatOfPoint2f(*sortedPoints)
                    val dst = MatOfPoint2f(
                            Point(0.0, 0.0),
                            Point(mat.width().toDouble(), 0.0),
                            Point(mat.width().toDouble(), mat.height().toDouble()),
                            Point(0.0, mat.height().toDouble())
                    )

                    val transform = Imgproc.getPerspectiveTransform(src, dst)
                    val warped = Mat()
                    Imgproc.warpPerspective(mat, warped, transform, Size(mat.width().toDouble(), mat.height().toDouble()))

                    val resultBitmap = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888)
                    org.opencv.android.Utils.matToBitmap(warped, resultBitmap)
                    return resultBitmap
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun sortPoints(points: Array<Point>): Array<Point> {
        // Sort points in order: top-left, top-right, bottom-right, bottom-left
        val sorted = points.sortedBy { it.y + it.x }
        return arrayOf(sorted[0], sorted[1], sorted[3], sorted[2])
    }

    private fun saveScan() {
        val filename = findViewById<EditText>(R.id.et_filename).text.toString()
        val isPdf = findViewById<RadioButton>(R.id.rb_pdf).isChecked

        if (isPdf) {
            val pdfFile = File(
                    getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "$filename.pdf"
            )
            PdfUtil.saveBitmapsAsPdf(this, scannedBitmaps, pdfFile.absolutePath)
            Toast.makeText(this, "PDF sauvegardé", Toast.LENGTH_SHORT).show()
        } else {
            scannedBitmaps.forEachIndexed { index, bitmap ->
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "${filename}_$index.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                }
                contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                )?.let { uri ->
                    contentResolver.openOutputStream(uri)?.use {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                    }
                }
            }
            Toast.makeText(this, "Images sauvegardées", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareScan() {
        val filename = findViewById<EditText>(R.id.et_filename).text.toString()
        val tempFile = File(cacheDir, "$filename.jpg")
        FileOutputStream(tempFile).use {
            scannedBitmaps.first().compress(Bitmap.CompressFormat.JPEG, 100, it)
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                tempFile
        )

        startActivity(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
        )
    }

    private fun addAnother() {
        findViewById<PreviewView>(R.id.preview_view).visibility = View.VISIBLE
        findViewById<Button>(R.id.btn_capture).visibility = View.VISIBLE
        findViewById<ImageView>(R.id.img_scanned).visibility = View.GONE
        findViewById<TextView>(R.id.tv_scan_count).visibility = View.GONE
        findViewById<LinearLayout>(R.id.tools_layout).visibility = View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            UCrop.getOutput(data!!)?.let {
                scannedBitmaps[currentIndex] =
                        BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                updateImageView()
            }
        }
    }
}
