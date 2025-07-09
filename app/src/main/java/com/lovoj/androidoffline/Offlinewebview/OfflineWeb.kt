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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.Visibility
import com.bumptech.glide.Glide
import com.lovoj.androidoffline.ProductSelectionActivity
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.log


@RequiresApi(Build.VERSION_CODES.M)
class OfflineWebview : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ImageView

    private lateinit var download_text: TextView
    private lateinit var loaderLayout: LinearLayout
    private lateinit var backgroundProcessor: BackgroundProcessor
    private lateinit var resourceMonitor: ResourceMonitor
    private lateinit var contentManager: ContentManager
    private lateinit var apiHelper: ApiHelper
    private lateinit var webViewSetup: WebViewSetup
    private lateinit var memoryManagerIntegration: MemoryManagerIntegration


    private var cache_Data = "http://localhost:8080/index.html#/cache-data?device=android&encoded="

    private var apiDone = false;
    private val baseDir by lazy {
        File(filesDir, "offline_web").also { if (!it.exists()) it.mkdirs() }
    }
    private var localWebServer: LocalWebServer? = null

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_web)

        webView = findViewById(R.id.webview)
        download_text = findViewById<TextView>(R.id.download_text_per)
        progressBar = findViewById(R.id.loading_indicator)
        loaderLayout = findViewById(R.id.loaderLayout)

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
            WebAppInterface(this, download_text),
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

                if (url.toString().contains(cache_Data)) {
                    download_text.text = "Configuring content..."
                }


//                progressBar.visibility = View.GONE
//                loaderLayout.visibility = View.GONE

                if (url != null) {
                    Log.d("OfflineWebview", "Page loaded successfully: $url")
                }
                val prefs =
                    getSharedPreferences("offlineweb_prefs", android.content.Context.MODE_PRIVATE)
                apiDone = prefs.getBoolean("api_done", false)
                view?.evaluateJavascript(
                    "window.apiDoneFromAndroid = ${if (apiDone) "true" else "false"};",
                    null
                )
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
                memoryManagerIntegration.handleBackgroundCacheError(
                    errorMessage,
                    request?.url?.toString()
                )
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
                loaderLayout.visibility = View.GONE

                val errorMessage = "WebView Error: ${error?.description}"
                memoryManagerIntegration.handleBackgroundCacheError(
                    errorMessage,
                    request?.url?.toString()
                )
            }
        }

        webViewSetup.setupWebView(webView, null)

        Glide.with(this).asGif().load(R.drawable.overlay).into(progressBar)
        if (!apiDone) {
            progressBar.visibility = View.VISIBLE
            contentManager.extractAndLoadContent(
                onSuccess = {
                    //progressBar.visibility = View.GONE
                    //  loaderLayout.visibility = View.GONE
                    loadContent()
                },
                onError = { errorMsg ->
                    progressBar.visibility = View.GONE
                    loaderLayout.visibility = View.GONE
                    val distIndex = File(baseDir, "dist/index.html")
                    if (!distIndex.exists()) {
                        Toast.makeText(
                            this,
                            "No Internet and no local content found. Please connect to the internet once.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        loadContent()
                    }
//                progressBar.visibility = View.GONE
//                loaderLayout.visibility = View.GONE
                    memoryManagerIntegration.handleBackgroundCacheError("Content loading error: $errorMsg")
                },
                onProgress = { progress ->
                    download_text.text = "Downloading content: $progress%"
                    if (progress.toString() == "100") {
                        download_text.text = "Extracting content..."
                    }
                }

            )
        } else {
            loadContent();
        }



        Handler(mainLooper).postDelayed({
            if (progressBar.isVisible) {
//                progressBar.visibility = View.GONE
//                loaderLayout.visibility = View.GONE
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
        val prefsToken = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val apiDone = prefs.getBoolean("api_done", false)
        val token = prefsToken.getString("token", null);
        Log.d("TAG", "loadContent: Check Token " + token);
        val json = JSONObject()
        json.put("token", token)

        val formattedJson = json.toString(4) // 4 = indentation level

        val urlencodedtokn = URLEncoder.encode(formattedJson, "UTF-8")


        val intent = intent
        val fabricUrl = intent.getStringExtra("fabric_url")
        if (apiDone && fabricUrl == null) {
            loaderLayout.visibility = View.GONE
            progressBar.visibility = View.GONE
            val selectionIntent = Intent(this, ProductSelectionActivity::class.java)
            selectionIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(selectionIntent)
            finish()
            return
        }
        val urlFabric = fabricUrl ?: "http://localhost:8080/"
        var url = "http://localhost:8080/";

        if (apiDone && fabricUrl != null) {
            url = urlFabric
            // Clear the download_text and hide the loader when loading a fabric URL
            download_text.text = ""
            loaderLayout.visibility = View.GONE
        } else {
            url = cache_Data + urlencodedtokn
        }

        Log.d("OfflineWebview", "Starting WebView with url: $url")

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

class WebAppInterface(
    private val activity: OfflineWebview,
    private val statusTextView: TextView
) {
    @JavascriptInterface
    fun onWebLoadingFinished(data: Boolean) {
        val prefs =
            activity.getSharedPreferences("offlineweb_prefs", android.content.Context.MODE_PRIVATE)
        if (data) {
            prefs.edit { putBoolean("api_done", true) }
            activity.runOnUiThread {
                val selectionIntent =
                    Intent(activity, com.lovoj.androidoffline.ProductSelectionActivity::class.java)
                selectionIntent.flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                activity.startActivity(selectionIntent)
                activity.finish()
            }
        } else {
            activity.runOnUiThread {
                Toast.makeText(activity, data.toString(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun getProductCachePercentage(data: Int) {
        statusTextView.text = "Configureing content ${data}%"
    }
}

