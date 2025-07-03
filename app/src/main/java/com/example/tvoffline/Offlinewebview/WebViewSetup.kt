package com.example.tvoffline.Offlinewebview

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.webkit.WebViewAssetLoader

class WebViewSetup(private val context: Context) {
    @RequiresApi(Build.VERSION_CODES.M)
    fun setupWebView(webView: WebView, assetLoader: WebViewAssetLoader?) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true

            allowUniversalAccessFromFileURLs = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            databaseEnabled = false
            setRenderPriority(WebSettings.RenderPriority.NORMAL)
            setEnableSmoothTransition(false)
            setGeolocationEnabled(false)
            setNeedInitialFocus(false)
            setLoadsImagesAutomatically(true)
            blockNetworkImage = false
            blockNetworkLoads = false
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        WebView.setWebContentsDebuggingEnabled(true)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d("WebView Console", "${message.messageLevel()}: ${message.message()} -- ${message.sourceId()}:${message.lineNumber()}")
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                Log.d("WebView", "URL loading request: $url")
                return false
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader?.shouldInterceptRequest(request as Uri)
            }
        }
    }
} 