package com.example.scannerpdfocr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.scannerpdfocr.viewmodel.MainViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdView
import com.bumptech.glide.Glide
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    private val pickImage = registerForActivityResult(GetContent()) { uri: Uri? ->
        uri?.let { vm.setSourceImageUri(it) }
    }

    private val cameraLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val uriString = data?.getStringExtra("image_uri")
            uriString?.let { vm.setSourceImageUri(Uri.parse(it)) }
        }
    }

    private val scannerLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(data)
            val uris = scanningResult?.pages?.map { it.imageUri }
            uris?.firstOrNull()?.let { vm.setSourceImageUri(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AdMob
        MobileAds.initialize(this) {}

        // Banner Ad
        val adView = findViewById<AdView>(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        findViewById<Button>(R.id.btn_camera).setOnClickListener {
            val i = Intent(this, com.example.scannerpdfocr.ui.CameraActivity::class.java)
            cameraLauncher.launch(i)
        }

        findViewById<Button>(R.id.btn_gallery).setOnClickListener {
            pickImage.launch("image/*")
        }

        findViewById<Button>(R.id.btn_scan_document).setOnClickListener {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            val scanner = GmsDocumentScanning.getClient(options)
            scanner.getStartScanIntent(this)
                .addOnSuccessListener { intent ->
                    scannerLauncher.launch(intent)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Erreur scanner: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        findViewById<Button>(R.id.btn_edit).setOnClickListener {
            vm.sourceImageUri.value?.let {
                val i = Intent(this, com.example.scannerpdfocr.ui.ImageEditorActivity::class.java)
                i.putExtra("image_uri", it.toString())
                startActivity(i)
            } ?: Toast.makeText(this, "Importez ou prenez une image d'abord", Toast.LENGTH_SHORT).show()
        }

        vm.pdfPath.observe(this) { path ->
            path?.let {
                // show interstitial after PDF creation
                MyApp.adManager.showInterstitial(this)
            }
        }

        vm.sourceImageUri.observe(this) { uri ->
            val imgView = findViewById<ImageView>(R.id.img_preview_main)
            if (uri != null) {
                Glide.with(this).load(uri).into(imgView)
                imgView.visibility = View.VISIBLE
            } else {
                imgView.visibility = View.GONE
            }
        }

        // ensure ads are loaded (MyApp already loads on start)
        MyApp.adManager.loadInterstitial(this)
        MyApp.adManager.loadRewarded(this)
    }
}
