package com.example.scannerpdfocr.viewmodel

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.scannerpdfocr.util.AdManager

class MainViewModel : ViewModel() {
    val sourceImageUri = MutableLiveData<Uri?>()
    val processedBitmap = MutableLiveData<android.graphics.Bitmap?>()
    val pdfPath = MutableLiveData<String?>()
    val ocrText = MutableLiveData<String?>()
    val adManager = AdManager()
    fun setSourceImageUri(uri: Uri) { sourceImageUri.value = uri }
}
