package com.example.scannerpdfocr.util

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdManager {

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null

    // -------------------------
    // INTERSTITIEL
    // -------------------------
    fun loadInterstitial(context: Context) {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
                context,
                "ca-app-pub-3940256099942544/1033173712", // Test ID
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitial = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitial = null
                    }
                }
        )
    }

    fun showInterstitial(activity: Activity) {
        val ad = interstitial ?: return

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {}

        ad.show(activity)

        interstitial = null
        loadInterstitial(activity)
    }

    // -------------------------
    // REWARDED
    // -------------------------
    fun loadRewarded(context: Context) {
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
                context,
                "ca-app-pub-3940256099942544/5224354917", // Test ID
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewarded = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewarded = null
                    }
                }
        )
    }

    fun showRewarded(activity: Activity, onEarned: () -> Unit) {
        val ad = rewarded ?: return

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {}

        ad.show(activity) {
            onEarned()
        }

        rewarded = null
        loadRewarded(activity)
    }

    // -------------------------
    // MÉTHODE MANQUANTE (OBLIGATOIRE)
    // -------------------------
    fun hasRewarded(): Boolean = rewarded != null
}
