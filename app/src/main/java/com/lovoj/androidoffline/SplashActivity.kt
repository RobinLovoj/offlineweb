package com.lovoj.androidoffline

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.lovoj.androidoffline.Offlinewebview.OfflineWebview
import kotlinx.coroutines.*
import java.net.URL
import kotlin.system.measureTimeMillis
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import java.io.File

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private lateinit var loadingIndicator: View
    private lateinit var splashLogo: ImageView
    private lateinit var appName: TextView
    private lateinit var backgroundWebView: WebView
    private var isApiDone = false

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Onboarding check
        val onboardingPrefs = getSharedPreferences("offlineweb_prefs", MODE_PRIVATE)
        val onboardingSeen = onboardingPrefs.getBoolean("onboarding_seen", false)
        if (!onboardingSeen) {
            startActivity(Intent(this, com.lovoj.androidoffline.Offlinewebview.OnboardingActivity::class.java))
            finish()
            return
        }

        val prefs = getSharedPreferences("offlineweb_prefs", MODE_PRIVATE)
        isApiDone = prefs.getBoolean("api_done", false)
        if (isApiDone) {
            startProductSelection()
            return
        }

        // Background WebView setup
        backgroundWebView = WebView(this)
        backgroundWebView.settings.javaScriptEnabled = true
        backgroundWebView.visibility = View.GONE
        backgroundWebView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onWebLoadingFinished(data: Boolean) {
                if (data) {
                    prefs.edit().putBoolean("api_done", true).apply()
                    runOnUiThread {

                        startProductSelection()
                    }
                }
            }
        }, "AndroidBackgroundProcessor")
        backgroundWebView.webViewClient = object : WebViewClient() {}
         // Apply Lovoj app theme
        LovojAppTheme.applyTheme(this, LovojAppTheme.THEME_LOVOJ_APP_SPLASH)

        splashLogo = findViewById(R.id.splashLogo)

        applyLovojColors()

        startAnimations()
    }

    private fun applyLovojColors() {
        val blackTextColor = LovojAppColors.getColor(this, R.color.black)
        val pinkColor = LovojAppColors.getColor(this, R.color.pink_button)
        val backgroundWhiteColor = LovojAppColors.getColor(this, R.color.background_white)
        window.statusBarColor = pinkColor
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun startAnimations() {
        val logoFadeIn = ObjectAnimator.ofFloat(splashLogo, View.ALPHA, 0f, 1f)
        val logoScaleX = ObjectAnimator.ofFloat(splashLogo, View.SCALE_X, 0.5f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(splashLogo, View.SCALE_Y, 0.5f, 1f)



        AnimatorSet().apply {
            playTogether(logoFadeIn, logoScaleX, logoScaleY)
            interpolator = AccelerateDecelerateInterpolator()
            duration = 1000
            startDelay = 500
            start()
        }

        AnimatorSet().apply {
            interpolator = AccelerateDecelerateInterpolator()
            duration = 800
            startDelay = 1000
            start()
        }

        CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            checkInternetSpeedAndNavigate( )
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkInternetSpeedAndNavigate() {
        CoroutineScope(Dispatchers.IO).launch {
            val speedMbps = getInternetSpeedMbps()
            withContext(Dispatchers.Main) {
                checkAuthAndNavigate(speedMbps)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkAuthAndNavigate(speedMbps: Double) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        val customerId = prefs.getString("customerId", null)

        if (token != null && customerId != null) {
            val intent = Intent(this, OfflineWebview::class.java).apply {
                putExtra("token", token)
                putExtra("customerId", customerId)
                putExtra("internetSpeedMbps", speedMbps)
            }
            startActivity(intent)
        } else {
            if (isInternetAvailable()) {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(
                    this,
                    "No internet and no login info found. Please connect to the internet.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getInternetSpeedMbps(): Double {
        return try {
            val testUrl = "https://www.google.com/favicon.ico"
            val bytes = ByteArray(1024)
            val timeMillis = measureTimeMillis {
                val connection = URL(testUrl).openConnection()
                connection.connect()
                connection.getInputStream().read(bytes)
            }
            val bits = bytes.size * 8
            val seconds = timeMillis / 1000.0
            (bits / 1_000_000.0) / seconds
        } catch (e: Exception) {
            0.0
        }
    }

    private fun startProductSelection() {
        val intent = Intent(this, ProductSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }
}
