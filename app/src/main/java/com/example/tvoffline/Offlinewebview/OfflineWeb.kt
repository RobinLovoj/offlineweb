@file:Suppress("DEPRECATION")

package com.example.tvoffline.Offlinewebview

import android.app.ProgressDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.tvoffline.R
import android.util.Log
import androidx.core.view.isVisible
import android.webkit.JavascriptInterface


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
    private val baseDir by lazy {
        java.io.File(filesDir, "offline_web").also { if (!it.exists()) it.mkdirs() }
    }
    private var localWebServer: LocalWebServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_web)

        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.loading_indicator)

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

        val distDir = java.io.File(baseDir, "dist")
        Log.d("TAG", "onCreate: Data " + distDir.toString());
        localWebServer = LocalWebServer(distDir, 8080)
        localWebServer?.start()

        webView.addJavascriptInterface(
            BackgroundProcessorInterface(this),
            "AndroidBackgroundProcessor"
        )

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                Log.d("OfflineWebview", "WebView onPageStarted: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("OfflineWebview", "WebView onPageFinished: $url")
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e(
                    "OfflineWebview",
                    "WebView onReceivedError: ${request?.url}, error: ${error?.description}"
                )
                progressBar.visibility = View.GONE
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
                Toast.makeText(this, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
            }
        )

        android.os.Handler(mainLooper).postDelayed({
            if (progressBar.isVisible) {
                progressBar.visibility = View.GONE
                Log.e("OfflineWebview", "Loader timeout: forcibly hiding loader after 10 seconds.")
            }
        }, 10000)


    }

    private fun loadContent() {
        val distIndex = java.io.File(baseDir, "dist/index.html")
        val directIndex = java.io.File(baseDir, "index.html")
        // Always load /index.html when server root is 'dist'
        val url = "https://appassets.androidplatform.net/assets/index.html"
        when {
            distIndex.exists() -> {
                Log.d("OfflineWebview", "Loading from local server: $url")
                webView.loadUrl(url)
            }
            directIndex.exists() -> {
                Log.d("OfflineWebview", "Loading from local server: $url")
                webView.loadUrl(url)
            }
            else -> {
                Log.e("OfflineWebview", "index.html not found in baseDir: ${baseDir.absolutePath}")
                Toast.makeText(this, "index.html not found", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        localWebServer?.stop()
        backgroundProcessor.cleanupBackgroundWebView()
    }
}

class BackgroundProcessorInterface(private val activity: OfflineWebview) {
    @JavascriptInterface
    fun onWebLoadingFinished(callbackId: String, type: String, data: String) {
        Log.d("BackgroundCallback", "Callback received: id=$callbackId, type=$type, data=$data")
        activity.runOnUiThread {
            Toast.makeText(
                activity,
                "Callback received: $callbackId, $type, $data",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}