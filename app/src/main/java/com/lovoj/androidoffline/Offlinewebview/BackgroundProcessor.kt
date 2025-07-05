@file:Suppress("DEPRECATION")

package com.lovoj.androidoffline.Offlinewebview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackgroundProcessor(
    private val context: Context,
    private val baseDir: File,
    private val mainWebView: WebView,
    private val handleApiRequest: (String, String) -> WebResourceResponse
) {
    private var backgroundWebView: WebView? = null
    private var isBackgroundProcessing = false
    private val backgroundQueue = mutableListOf<String>()
    private val callbackMap = mutableMapOf<String, String>()
    private var backgroundLogs = mutableListOf<String>()
    private var backgroundStatus = "Idle"
    private var backgroundStartTime: Long = 0
    
    // Memory management integration
    private var memoryManagerIntegration: MemoryManagerIntegration? = null

    fun processCacheDataUrlInBackground(url: String) {
        Log.d("BackgroundWebView", "Processing cache-data URL in background: $url")
        addBackgroundLog("Starting background processing for: $url")

        // Initialize memory manager if not already done
        if (memoryManagerIntegration == null) {
            memoryManagerIntegration = MemoryManagerIntegration(context, baseDir)
        }

        if (isBackgroundProcessing) {
            backgroundQueue.add(url)
            addBackgroundLog("Added to queue: $url (currently processing)")
            return
        }

        isBackgroundProcessing = true
        backgroundStatus = "Processing"
        backgroundStartTime = System.currentTimeMillis()

        Handler(context.mainLooper).postDelayed({
            if (backgroundWebView == null) {
                createBackgroundWebView()
            }
            backgroundWebView?.let { bgWebView ->
                bgWebView.post {
                    bgWebView.loadUrl(url)
                    addBackgroundLog("Background WebView loading URL: $url")
                }
            }
        }, 1000)
    }

    private fun processNextInQueue() {
        if (backgroundQueue.isNotEmpty() && !isBackgroundProcessing) {
            val nextUrl = backgroundQueue.removeAt(0)
            processCacheDataUrlInBackground(nextUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createBackgroundWebView() {
        backgroundWebView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowFileAccessFromFileURLs = true
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
                loadsImagesAutomatically = false
                blockNetworkImage = true
                blockNetworkLoads = false
            }
            
            // Register with memory manager
            memoryManagerIntegration?.registerWebView("background_webview", this)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    addBackgroundLog("Intercepting request: ${request.method} $url")
                    return handleApiRequest(url, request.method)
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    addBackgroundLog("Background page started loading: $url")
                    backgroundStatus = "Loading"
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    addBackgroundLog("Background page finished loading: $url")
                    backgroundStatus = "Loaded"
                    Handler(context.mainLooper).postDelayed({
                        injectCommunicationBridge(url ?: "")
                    }, 500)
                }
                @RequiresApi(Build.VERSION_CODES.M)
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    addBackgroundLog("Error loading resource: ${request?.url} - ${error?.description}")
                    backgroundStatus = "Error"
                    
                    // Handle cache data errors with memory manager
                    val errorMessage = "Background processing error: ${error?.description}"
                    memoryManagerIntegration?.handleBackgroundCacheError(errorMessage, request?.url?.toString())
                    
                    val callbackId = callbackMap[request?.url?.toString() ?: ""]
                    if (callbackId != null) {
                        sendCallbackToWeb(callbackId, errorMessage, true)
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    addBackgroundLog("Console [${message.messageLevel()}]: ${message.message()}")
                    if (message.message().contains("BACKGROUND_RESULT:")) {
                        handleBackgroundResult(message.message())
                    }
                    return true
                }
            }
        }
        addBackgroundLog("Background WebView created successfully")
    }

    private fun injectCommunicationBridge(url: String) {
        val callbackId = callbackMap[url] ?: return
        val bridgeScript = """
            (function() {
                window.sendBackgroundResult = function(result, isError) {
                    console.log('BACKGROUND_RESULT:' + JSON.stringify({
                        callbackId: '$callbackId',
                        result: result,
                        isError: isError,
                        timestamp: Date.now()
                    }));
                };
                const originalLog = console.log;
                console.log = function(...args) {
                    originalLog.apply(console, args);
                    const message = args.join(' ');
                    if (message.includes('API_COMPLETE') || message.includes('PROCESSING_DONE')) {
                        window.sendBackgroundResult(message, false);
                    }
                };
                const originalError = console.error;
                console.error = function(...args) {
                    originalError.apply(console, args);
                    const message = args.join(' ');
                    window.sendBackgroundResult(message, true);
                };
                let checkCount = 0;
                const statusInterval = setInterval(function() {
                    checkCount++;
                    if (checkCount % 5 === 0) {
                        window.sendBackgroundResult('Progress: ' + checkCount + ' seconds', false);
                    }
                    if (checkCount > 120) {
                        clearInterval(statusInterval);
                        window.sendBackgroundResult('Background processing timeout', true);
                    }
                }, 1000);
                console.log('Background communication bridge injected for URL: $url');
            })();
        """.trimIndent()
        backgroundWebView?.evaluateJavascript(bridgeScript, null)
        addBackgroundLog("Communication bridge injected for URL: $url")
    }

    private fun handleBackgroundResult(message: String) {
        try {
            val resultData = message.substringAfter("BACKGROUND_RESULT:")
            val jsonData = org.json.JSONObject(resultData)
            val callbackId = jsonData.getString("callbackId")
            val result = jsonData.getString("result")
            val isError = jsonData.getBoolean("isError")
            sendCallbackToWeb(callbackId, result, isError)
            if (result.contains("COMPLETE") || result.contains("DONE") || result.contains("ERROR") || result.contains("timeout")) {
                callbackMap.remove(callbackId)
                isBackgroundProcessing = false
                processNextInQueue()
            }
        } catch (_: Exception) {
            isBackgroundProcessing = false
            processNextInQueue()
        }
    }

    fun cleanupBackgroundWebView() {
        backgroundWebView?.let { bgWebView ->
            addBackgroundLog("Cleaning up background WebView")
            
            // Unregister from memory manager
            memoryManagerIntegration?.unregisterWebView("background_webview")
            
            bgWebView.stopLoading()
            bgWebView.loadUrl("about:blank")
            bgWebView.destroy()
            backgroundWebView = null
            backgroundStatus = "Cleaned up"
            backgroundStartTime = 0
            isBackgroundProcessing = false
            processNextInQueue()
        }
    }

    /**
     * Force memory cleanup when system detects high memory usage
     */
    fun forceMemoryCleanup() {
        addBackgroundLog("Forcing memory cleanup due to system pressure")
        memoryManagerIntegration?.forceMemoryCleanup()
        
        // Also cleanup background WebView if it exists
        if (backgroundWebView != null) {
            cleanupBackgroundWebView()
        }
    }

    /**
     * Get memory statistics
     */
    fun getMemoryStats(): Map<String, Any>? {
        return memoryManagerIntegration?.getMemoryStats()
    }

    /**
     * Check if memory pressure is high
     */
    fun isMemoryPressureHigh(): Boolean {
        return memoryManagerIntegration?.isMemoryPressureHigh() ?: false
    }

    /**
     * Handle cache data errors from background processing
     */
    fun handleCacheDataError(error: String, url: String? = null) {
        addBackgroundLog("Handling cache data error: $error")
        memoryManagerIntegration?.handleBackgroundCacheError(error, url)
    }

    fun sendCallbackToWeb(callbackId: String, data: String, isError: Boolean) {
        mainWebView.post {
            val callbackType = if (isError) "error" else "success"
            val escapedData = data.replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
            val jsCode = """
                (function() {
                    try {
                        if (window.backgroundProcessingCallbacks && window.backgroundProcessingCallbacks['$callbackId']) {
                            window.backgroundProcessingCallbacks['$callbackId']('$callbackType', '$escapedData');
                        } else if (window.onBackgroundProcessingCallback) {
                            window.onBackgroundProcessingCallback('$callbackId', '$callbackType', '$escapedData');
                        }
                    } catch(e) {
                        console.error('Error in callback handler: ', e);
                    }
                })();
            """.trimIndent()
            mainWebView.evaluateJavascript(jsCode, null)
        }
    }

    private fun addBackgroundLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"
        backgroundLogs.add(logEntry)
        if (backgroundLogs.size > 100) {
            backgroundLogs.removeAt(0)
        }
        Log.d("BackgroundWebView", logEntry)
    }

    fun createTestInterface() {
        Thread {
            val testHtml = "<html><body><h1>Test Interface</h1></body></html>" // Replace with your actual HTML or resource
            try {
                val testFile = File(baseDir, "test_interface.html")
                testFile.writeText(testHtml)
                addBackgroundLog("Test interface created at: ${testFile.absolutePath}")
                Log.d("BackgroundWebView", "Test interface created: ${testFile.absolutePath}")

                Handler(context.mainLooper).post {
                    android.widget.Toast.makeText(context, "Test interface created successfully", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("BackgroundWebView", "Error creating test interface: ${e.message}")
                Handler(context.mainLooper).post {
                    android.widget.Toast.makeText(context, "Error creating test interface: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    fun loadTestInterface() {
        Thread {
            val testFile = File(baseDir, "test_interface.html")
            if (testFile.exists()) {
                Handler(context.mainLooper).post {
                    mainWebView.loadUrl("file://${testFile.absolutePath}")
                    addBackgroundLog("Test interface loaded in main WebView")
                    android.widget.Toast.makeText(context, "Test interface loaded", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                createTestInterface()
                Handler(context.mainLooper).postDelayed({
                    val createdFile = File(baseDir, "test_interface.html")
                    if (createdFile.exists()) {
                        Handler(context.mainLooper).post {
                            mainWebView.loadUrl("file://${createdFile.absolutePath}")
                            addBackgroundLog("Test interface created and loaded")
                            android.widget.Toast.makeText(context, "Test interface created and loaded", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }, 1000)
            }
        }.start()
    }
} 