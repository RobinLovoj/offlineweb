package com.lovoj.androidoffline

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
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

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private lateinit var loadingIndicator: View
    private lateinit var splashLogo: ImageView
    private lateinit var appName: TextView

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        splashLogo = findViewById(R.id.splashLogo)
        appName = findViewById(R.id.appName)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        startAnimations()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun startAnimations() {
        val logoFadeIn = ObjectAnimator.ofFloat(splashLogo, View.ALPHA, 0f, 1f)
        val logoScaleX = ObjectAnimator.ofFloat(splashLogo, View.SCALE_X, 0.5f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(splashLogo, View.SCALE_Y, 0.5f, 1f)

        val nameFadeIn = ObjectAnimator.ofFloat(appName, View.ALPHA, 0f, 1f)
        val nameSlideUp = ObjectAnimator.ofFloat(appName, View.TRANSLATION_Y, 50f, 0f)

        AnimatorSet().apply {
            playTogether(logoFadeIn, logoScaleX, logoScaleY)
            interpolator = AccelerateDecelerateInterpolator()
            duration = 1000
            startDelay = 500
            start()
        }

        AnimatorSet().apply {
            playTogether(nameFadeIn, nameSlideUp)
            interpolator = AccelerateDecelerateInterpolator()
            duration = 800
            startDelay = 1000
            start()
        }

        CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            checkInternetSpeedAndNavigate()
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
            // Convert bytes/ms to Mbps
            val bits = bytes.size * 8
            val seconds = timeMillis / 1000.0
            (bits / 1_000_000.0) / seconds
        } catch (e: Exception) {
            0.0
        }
    }
}
