package com.odom.applimit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.odom.applimit.ui.AppNavGraph
import com.odom.applimit.ui.theme.AppLimitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var interstitialAd: InterstitialAd? = null
    private var pendingShowAd = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        loadInterstitialAd()
        handleBlockerIntent(intent)
        setContent {
            AppLimitTheme {
                AppNavGraph()
            }
        }
    }

    // Called when activity is already running and a new intent arrives (launchMode=singleTop)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleBlockerIntent(intent)
    }

    private fun handleBlockerIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_FROM_BLOCKER, false) == true) {
            tryShowAd()
        }
    }

    private fun loadInterstitialAd() {
        InterstitialAd.load(
            this,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    if (pendingShowAd) {
                        pendingShowAd = false
                        showAd(ad)
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    pendingShowAd = false
                }
            }
        )
    }

    private fun tryShowAd() {
        val ad = interstitialAd
        if (ad != null) {
            interstitialAd = null
            showAd(ad)
        } else {
            // Ad not ready yet — show it as soon as it loads
            pendingShowAd = true
        }
    }

    private fun showAd(ad: InterstitialAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitialAd() // pre-load for next blocker trigger
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadInterstitialAd()
            }
        }
        ad.show(this)
    }

    companion object {
        const val EXTRA_FROM_BLOCKER = "from_blocker"

        // Replace with your real AdMob interstitial ad unit ID before publishing.
        // This is Google's test ID — it always returns a test ad.
        private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }
}
