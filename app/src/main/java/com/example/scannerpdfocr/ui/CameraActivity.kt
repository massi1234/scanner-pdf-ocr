package com.example.scannerpdfocr.ui

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.scannerpdfocr.R
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri
import com.google.common.util.concurrent.ListenableFuture

class CameraActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private var mode: String? = null

    private val requestPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        mode = intent.getStringExtra("mode")

        requestPermission.launch(Manifest.permission.CAMERA)

        findViewById<Button>(R.id.btn_take_photo).setOnClickListener { takePhoto() }
        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == com.yalantis.ucrop.UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            val resultUri = com.yalantis.ucrop.UCrop.getOutput(data ?: return)
            resultUri?.let {
                val i = Intent().apply {
                    putExtra("image_uri", it.toString())
                }
                setResult(RESULT_OK, i)
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
                ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                val pv = findViewById<PreviewView>(R.id.viewFinder)
                it.setSurfaceProvider(pv.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "scan_$name")
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

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedUri: Uri? = outputFileResults.savedUri
                        savedUri?.let {
                            if (mode == "scanner") {
                                // Lancer uCrop pour recadrer
                                val destUri = Uri.fromFile(
                                        java.io.File(cacheDir, "scanned_${System.currentTimeMillis()}.jpg")
                                )
                                com.yalantis.ucrop.UCrop.of(it, destUri).start(this@CameraActivity)
                            } else {
                                val i = Intent().apply {
                                    putExtra("image_uri", it.toString())
                                }
                                setResult(RESULT_OK, i)
                                finish()
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        exception.printStackTrace()
                    }
                }
        )
    }
}
