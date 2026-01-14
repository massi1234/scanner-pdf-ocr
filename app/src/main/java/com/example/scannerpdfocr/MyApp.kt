package com.example.scannerpdfocr

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.example.scannerpdfocr.util.AdManager

class MyApp : Application() {
    companion object {
        lateinit var adManager: AdManager
    }

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        adManager = AdManager()
        adManager.loadInterstitial(this)
        adManager.loadRewarded(this)
    }
}
