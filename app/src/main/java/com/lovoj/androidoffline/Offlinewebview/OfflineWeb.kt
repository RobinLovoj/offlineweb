@file:Suppress("DEPRECATION")

package com.lovoj.androidoffline.Offlinewebview

import android.app.ProgressDialog
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import androidx.core.view.isVisible
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import com.lovoj.androidoffline.Offlinewebview.ContentManager
import com.lovoj.androidoffline.Offlinewebview.WebViewSetup
import com.lovoj.androidoffline.R
import java.io.File
import androidx.core.content.edit
import android.content.Intent
import com.lovoj.androidoffline.ProductSelectionActivity


@RequiresApi(Build.VERSION_CODES.M)
class OfflineWebview : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressDialog: ProgressDialog
    private lateinit var backgroundProcessor: BackgroundProcessor
    private lateinit var resourceMonitor: ResourceMonitor
    private lateinit var contentManager: ContentManager
    private lateinit var apiHelper: ApiHelper
    private lateinit var webViewSetup: WebViewSetup
    private lateinit var memoryManagerIntegration: MemoryManagerIntegration

    private val baseDir by lazy {
        File(filesDir, "offline_web").also { if (!it.exists()) it.mkdirs() }
    }
    private var localWebServer: LocalWebServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_web)

        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.loading_indicator)

        memoryManagerIntegration = MemoryManagerIntegration(this, baseDir)

        setupMemoryPressureCallbacks()

        apiHelper = ApiHelper()
        backgroundProcessor = BackgroundProcessor(
            context = this,
            baseDir = baseDir,
            mainWebView = webView,
            handleApiRequest = { url, method -> apiHelper.handleApiRequest(url, method) }
        )
        resourceMonitor = ResourceMonitor()
        contentManager = ContentManager(baseDir)
        webViewSetup = WebViewSetup(this)

        val distDir = File(baseDir, "dist")
        Log.d("TAG", "onCreate: Data " + distDir.toString());
        localWebServer = LocalWebServer(distDir, 8080)
        localWebServer?.start()

        webView.addJavascriptInterface(
            BackgroundProcessorInterface(this),
            "AndroidBackgroundProcessor"
        )

        memoryManagerIntegration.registerWebView("offline_webview", webView)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                Log.d("OfflineWebview", "WebView onPageStarted: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("OfflineWebview", "WebView onPageFinished: $url")
                progressBar.visibility = View.GONE

                if (url != null) {
                    Log.d("OfflineWebview", "Page loaded successfully: $url")
                }
                val prefs = getSharedPreferences("offlineweb_prefs", android.content.Context.MODE_PRIVATE)
                val apiDone = prefs.getBoolean("api_done", false)
                view?.evaluateJavascript("window.apiDoneFromAndroid = ${if (apiDone) "true" else "false"};", null)
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                Log.d("OfflineWebview", "Loading Resource: $url")
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.d("OfflineWebview", "Received SSL Error : $request")

                val errorMessage = "HTTP Error: ${errorResponse?.statusCode}"
                memoryManagerIntegration.handleBackgroundCacheError(errorMessage, request?.url?.toString())
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e(
                    "OfflineWebview",
                    "WebView onReceivedError: ${request?.url}, error: ${error?.description}"
                )
                progressBar.visibility = View.GONE

                val errorMessage = "WebView Error: ${error?.description}"
                memoryManagerIntegration.handleBackgroundCacheError(errorMessage, request?.url?.toString())
            }
        }

        webViewSetup.setupWebView(webView, null)

        progressDialog = ProgressDialog(this).apply {
            setTitle("Preparing Content")
            setMessage("Downloading files...")
            setCancelable(false)
        }
        progressDialog.show()
        contentManager.extractAndLoadContent(
            onSuccess = {
                progressDialog.dismiss()
                loadContent()
            },
            onError = { errorMsg ->
                progressDialog.dismiss()
                val distIndex = File(baseDir, "dist/index.html")
                if (!distIndex.exists()) {
                    Toast.makeText(this, "No Internet and no local content found. Please connect to the internet once.", Toast.LENGTH_LONG).show()
                } else {
                    loadContent() // fallback: try to load whatever is there
                }
                progressBar.visibility = View.GONE
                memoryManagerIntegration.handleBackgroundCacheError("Content loading error: $errorMsg")
            }
        )

        Handler(mainLooper).postDelayed({
            if (progressBar.isVisible) {
                progressBar.visibility = View.GONE
                Log.e("OfflineWebview", "Loader timeout: forcibly hiding loader after 10 seconds.")
            }
        }, 10000)
    }


    private fun setupMemoryPressureCallbacks() {
        memoryManagerIntegration.setMemoryPressureCallback { isHighPressure ->
            if (isHighPressure) {
                Log.w("OfflineWebview", "High memory pressure detected")
                handleMemoryPressure()
            }
        }

        memoryManagerIntegration.setErrorCallback { errorType, errorMessage ->
            Log.e("OfflineWebview", "Memory manager error [$errorType]: $errorMessage")
            handleMemoryError(errorType, errorMessage)
        }
    }


    private fun handleMemoryPressure() {
        runOnUiThread {
            Toast.makeText(
                this,
                "Optimizing memory for better performance...",
                Toast.LENGTH_SHORT
            ).show()

            Log.d("OfflineWebview", "Memory pressure handled automatically")
        }
    }


    private fun handleMemoryError(errorType: String, errorMessage: String) {
        runOnUiThread {
            when (errorType) {
                "BACKGROUND_CACHE_ERROR" -> {
                    Log.w("OfflineWebview", "Background cache error: $errorMessage")
                }
                "MEMORY_CLEANUP_ERROR" -> {
                    Log.e("OfflineWebview", "Memory cleanup error: $errorMessage")
                    reloadContentIfNeeded()
                }
                else -> {
                    Log.e("OfflineWebview", "Unknown memory error [$errorType]: $errorMessage")
                }
            }
        }
    }


    private fun reloadContentIfNeeded() {
        val currentUrl = webView.url
        if (currentUrl.isNullOrEmpty() || currentUrl == "about:blank") {
            Log.d("OfflineWebview", "Reloading content after memory cleanup")
            loadContent()
        }
    }

    private fun loadContent() {
        val prefs = getSharedPreferences("offlineweb_prefs", android.content.Context.MODE_PRIVATE)
        val apiDone = prefs.getBoolean("api_done", false)
        val intent = intent
        val fabricUrl = intent.getStringExtra("fabric_url")
        if (apiDone && fabricUrl == null) {
            val selectionIntent = Intent(this, ProductSelectionActivity::class.java)
            selectionIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(selectionIntent)
            finish()
            return
        }
        val url = fabricUrl ?: "http://localhost:8080/"
        Log.d("OfflineWebview","Starting WebView with url: $url")
        webView.loadUrl(url)
    }

    override fun onDestroy() {
        super.onDestroy()
        localWebServer?.stop()
        backgroundProcessor.cleanupBackgroundWebView()

        memoryManagerIntegration.unregisterWebView("offline_webview")
        memoryManagerIntegration.cleanup()
    }


    fun getMemoryStats(): Map<String, Any>? {
        return memoryManagerIntegration.getMemoryStats()
    }


    fun forceMemoryCleanup() {
        memoryManagerIntegration.forceMemoryCleanup()
    }
}

class BackgroundProcessorInterface(private val activity: OfflineWebview) {
    @JavascriptInterface
    fun onWebLoadingFinished(data: Boolean) {
        val prefs = activity.getSharedPreferences("offlineweb_prefs", android.content.Context.MODE_PRIVATE)
        if (data) {
            prefs.edit { putBoolean("api_done", true) }
            activity.runOnUiThread {
                val selectionIntent = Intent(activity, com.lovoj.androidoffline.ProductSelectionActivity::class.java)
                selectionIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                activity.startActivity(selectionIntent)
                activity.finish()
            }
        } else {
            activity.runOnUiThread {
                Toast.makeText(activity, data.toString(), Toast.LENGTH_SHORT).show()
            }
        }
    }
}