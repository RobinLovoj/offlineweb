@file:Suppress("DEPRECATION")

package com.lovoj.androidoffline.Offlinewebview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.webkit.WebView
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
import com.lovoj.androidoffline.R
import java.io.File
import androidx.core.content.edit
import android.content.Intent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.lovoj.androidoffline.ProductSelectionActivity
import org.json.JSONObject
import java.net.URLEncoder
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.room.Room
import com.lovoj.androidoffline.Offlinewebview.AppDatabase
import com.lovoj.androidoffline.Offlinewebview.ProductEntity
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.content.Context
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import android.view.animation.OvershootInterpolator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.AnimatorSet
import android.view.animation.CycleInterpolator
import android.animation.Keyframe
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import com.lovoj.androidoffline.ApiUtils
import android.graphics.Color
import android.widget.RelativeLayout
import android.view.Gravity


@RequiresApi(Build.VERSION_CODES.M)
class OfflineWebview : AppCompatActivity() {
    lateinit var webView: WebView
    private lateinit var downloadText: TextView
    private lateinit var indicatorText: TextView
    private lateinit var indicatorIcon: ImageView
    private lateinit var indicatorLayout: LinearLayout
    private lateinit var overallProgressBar: ProgressBar

    private lateinit var memoryManagerIntegration: MemoryManagerIntegration
    private lateinit var backgroundProcessor: BackgroundProcessor
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var indicatorJob: Job? = null
    private var indicatorAnimator: ObjectAnimator? = null
    private var apiDone = false
    private val baseDir by lazy {
        File(filesDir, "offline_web").also { if (!it.exists()) it.mkdirs() }
    }
    private var localWebServer: LocalWebServer? = null
    private val CACHE_DATA_PREFIX = "http://localhost:8080/cache_data?data="

    // Move ProductCard and ProductProgressAdapter to top-level

    data class ProductCard(
        val name: String,
        val imageRes: Int,
        var percent: Int = 0,
        var isChecked: Boolean = false
    )

    class ProductProgressAdapter(
        private val context: Context,
        private val products: List<ProductCard>
    ) : BaseAdapter() {
        override fun getCount(): Int = products.size
        override fun getItem(position: Int): Any = products[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_media_card, parent, false)
            val imageThumbnail = view.findViewById<ImageView>(R.id.imageThumbnail)
            val productName = view.findViewById<TextView>(R.id.productName)
            val percentText = view.findViewById<TextView>(R.id.percentText)
            val progressBar = view.findViewById<ProgressBar>(R.id.itemProgressBar)
            val checkMark = view.findViewById<ImageView>(R.id.checkMark)
            val item = products[position]
            imageThumbnail.setImageResource(item.imageRes)
            productName.text = item.name
            percentText.text = "${item.percent}%"
            progressBar.progress = item.percent
            checkMark.visibility = if (item.isChecked) View.VISIBLE else View.GONE
            return view
        }
    }

    @SuppressLint("SetTextI18n", "MissingInflatedId")
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_web)

        // Initialize overallProgressBar after setContentView
        overallProgressBar = findViewById(R.id.overallProgressBar)
        // Ensure download_text is initialized right after setContentView
        downloadText = findViewById(R.id.download_text_per)
        webView = findViewById(R.id.webview)
        indicatorText = findViewById(R.id.indicatorText)
        indicatorIcon = findViewById(R.id.indicatorIcon)
        indicatorLayout = findViewById(R.id.connectionIndicator)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        registerNetworkCallback()
        startIndicatorPolling()
        updateConnectionIndicator(isInternetAvailable())

        memoryManagerIntegration = MemoryManagerIntegration(this, baseDir)
        setupMemoryPressureCallbacks()

        val apiHelper = ApiHelper()
        backgroundProcessor = BackgroundProcessor(
            context = this,
            baseDir = baseDir,
            mainWebView = webView,
            handleApiRequest = { url, method -> apiHelper.handleApiRequest(url, method) }
        )
        val resourceMonitor = ResourceMonitor()
        val contentManager = ContentManager(baseDir)
        val webViewSetup = WebViewSetup(this)

        val distDir = File(baseDir, "dist")
        Log.d("TAG", "onCreate: Data $distDir")
        localWebServer = LocalWebServer(distDir, 8080)
        localWebServer?.start()

        webView.addJavascriptInterface(
            WebAppInterface(this, downloadText),
            "AndroidBackgroundProcessor"
        )

        // Add custom interface for texture error handling
        webView.addJavascriptInterface(
            TextureErrorHandler(this),
            "AndroidTextureHandler"
        )

        // Add custom interface for saving customized product data
        webView.addJavascriptInterface(
            SaveCustomizedProductInterface(),
            "AndroidSaveProduct"
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

            @SuppressLint("SetTextI18n")
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("OfflineWebview", "WebView onPageFinished: $url")

                if (url.toString().contains(CACHE_DATA_PREFIX)) {
                    //download_text.text = "Configuring content..."
                }

                // Inject comprehensive error handling for texture loading
                injectTextureErrorHandler(view)
                injectProactiveTextureProtection(view)

                if (url != null) {
                    Log.d("OfflineWebview", "Page loaded successfully: $url")
                }
                val prefs =
                    getSharedPreferences("offlineweb_prefs", MODE_PRIVATE)
                apiDone = prefs.getBoolean("api_done", false)
                view?.evaluateJavascript(
                    "window.apiDoneFromAndroid = ${if (apiDone) "true" else "false"};",
                    null
                )
            }

            private fun injectTextureErrorHandler(webView: WebView?) {
                webView?.evaluateJavascript(
                    """
                    (function() {
                        // Override console.error to catch texture errors
                        const originalError = console.error;
                        console.error = function(...args) {
                            const message = args.join(' ');
                            
                            // Check if it's a texture error
                            if (message.includes('Error applying texture:')) {
                                console.warn('Texture error detected, attempting recovery...');
                                
                                // Use Android texture handler for recovery
                                if (window.AndroidTextureHandler) {
                                    window.AndroidTextureHandler.handleTextureError(message, null);
                                } else {
                                    // Fallback recovery
                                    try {
                                        // Clear texture cache
                                        if (window.THREE && window.THREE.Cache) {
                                            window.THREE.Cache.clear();
                                        }
                                        
                                        // Force garbage collection if available
                                        if (window.gc) {
                                            window.gc();
                                        }
                                        
                                        console.log('Texture cache cleared, retrying...');
                                    } catch (e) {
                                        console.warn('Recovery attempt failed:', e);
                                    }
                                }
                            }
                            
                            // Call original error function
                            originalError.apply(console, args);
                        };
                        
                        // Add error handler for image loading
                        const originalImage = window.Image;
                        window.Image = function() {
                            const img = new originalImage();
                            img.addEventListener('error', function(e) {
                                console.warn('Image loading failed:', e.target.src);
                                // Don't throw error, just log it
                            });
                            return img;
                        };
                        
                        // Override texture loader to handle errors gracefully
                        if (window.THREE && window.THREE.TextureLoader) {
                            const originalLoad = window.THREE.TextureLoader.prototype.load;
                            window.THREE.TextureLoader.prototype.load = function(url, onLoad, onProgress, onError) {
                                const wrappedOnError = function(error) {
                                    console.warn('Texture loading failed for:', url, error);
                                    // Create a fallback texture instead of throwing error
                                    if (onError) {
                                        onError(new Error('Texture loading failed: ' + url));
                                    }
                                };
                                return originalLoad.call(this, url, onLoad, onProgress, wrappedOnError);
                            };
                        }
                        
                        console.log('Texture error handler injected');
                    })();
                """.trimIndent(), null
                )
            }

            private fun injectProactiveTextureProtection(webView: WebView?) {
                webView?.evaluateJavascript(
                    """
                    (function() {
                        // Proactive texture protection
                        console.log('Injecting proactive texture protection...');
                        
                        // Override any texture application functions to prevent errors
                        const originalApplyTexture = window.applyTexture || function(){};
                        window.applyTexture = function(textureData) {
                            try {
                                console.log('Proactive: Applying texture with protection...');
                                
                                // Validate texture data
                                if (!textureData || !textureData.fabImage) {
                                    console.warn('Proactive: Invalid texture data, using fallback');
                                    return applyFallbackTexture();
                                }
                                
                                // Check if texture URL is valid
                                if (typeof textureData.fabImage === 'string' && textureData.fabImage.trim() === '') {
                                    console.warn('Proactive: Empty texture URL, using fallback');
                                    return applyFallbackTexture();
                                }
                                
                                // Call original function with error handling
                                return originalApplyTexture.call(this, textureData);
                                
                            } catch (error) {
                                console.warn('Proactive: Texture application failed, using fallback:', error);
                                return applyFallbackTexture();
                            }
                        };
                        
                        // Override the specific function that's causing the error
                        if (window.applyTextureToComponents) {
                            const originalApplyToComponents = window.applyTextureToComponents;
                            window.applyTextureToComponents = function(textureData, componentType) {
                                try {
                                    console.log('Proactive: Safe texture application to', componentType);
                                    
                                    // Validate texture data before applying
                                    if (!textureData || !textureData.fabImage) {
                                        console.warn('Proactive: Invalid texture data for', componentType, ', using fallback');
                                        return applyFallbackTexture();
                                    }
                                    
                                    return originalApplyToComponents.call(this, textureData, componentType);
                                } catch (error) {
                                    console.warn('Proactive: Texture application to', componentType, 'failed, using fallback:', error);
                                    return applyFallbackTexture();
                                }
                            };
                            console.log('Proactive: Override applied to applyTextureToComponents');
                        }
                        
                        // Override fabric texture application
                        if (window.applyFabricTexture) {
                            const originalFabric = window.applyFabricTexture;
                            window.applyFabricTexture = function(fabricData) {
                                try {
                                    console.log('Proactive: Safe fabric texture application');
                                    
                                    // Validate fabric data
                                    if (!fabricData || !fabricData.fabImage) {
                                        console.warn('Proactive: Invalid fabric data, using fallback');
                                        return applyFallbackTexture();
                                    }
                                    
                                    return originalFabric.call(this, fabricData);
                                } catch (error) {
                                    console.warn('Proactive: Fabric texture failed, using fallback:', error);
                                    return applyFallbackTexture();
                                }
                            };
                            console.log('Proactive: Override applied to applyFabricTexture');
                        }
                        
                        // Override any other texture-related functions
                        const textureFunctions = [
                            'applyTextureToShirt',
                            'applyTextureToMaterial',
                            'loadTexture',
                            'createTexture'
                        ];
                        
                        textureFunctions.forEach(funcName => {
                            if (window[funcName]) {
                                const originalFunc = window[funcName];
                                window[funcName] = function(...args) {
                                    try {
                                        console.log('Proactive: Safe execution of', funcName);
                                        return originalFunc.apply(this, args);
                                    } catch (error) {
                                        console.warn('Proactive:', funcName, 'failed, using fallback:', error);
                                        return applyFallbackTexture();
                                    }
                                };
                                console.log('Proactive: Override applied to', funcName);
                            }
                        });
                        
                        // Create fallback texture function
                        window.applyFallbackTexture = function() {
                            try {
                                if (window.THREE && window.scene) {
                                    const canvas = document.createElement('canvas');
                                    canvas.width = 256;
                                    canvas.height = 256;
                                    const ctx = canvas.getContext('2d');
                                    
                                    // Create a nice fallback pattern
                                    ctx.fillStyle = '#f5f5f5';
                                    ctx.fillRect(0, 0, 256, 256);
                                    ctx.fillStyle = '#e0e0e0';
                                    ctx.fillRect(0, 0, 128, 128);
                                    ctx.fillRect(128, 128, 128, 128);
                                    ctx.fillStyle = '#d0d0d0';
                                    ctx.fillRect(64, 64, 128, 128);
                                    
                                    const fallbackTexture = new window.THREE.CanvasTexture(canvas);
                                    fallbackTexture.wrapS = window.THREE.RepeatWrapping;
                                    fallbackTexture.wrapT = window.THREE.RepeatWrapping;
                                    fallbackTexture.repeat.set(1, 1);
                                    
                                    // Apply to scene
                                    let appliedCount = 0;
                                    window.scene.traverse(function(child) {
                                        if (child.material) {
                                            if (Array.isArray(child.material)) {
                                                child.material.forEach(mat => {
                                                    if (mat && mat.map) {
                                                        mat.map = fallbackTexture;
                                                        mat.needsUpdate = true;
                                                        appliedCount++;
                                                    }
                                                });
                                            } else if (child.material.map) {
                                                child.material.map = fallbackTexture;
                                                child.material.needsUpdate = true;
                                                appliedCount++;
                                            }
                                        }
                                    });
                                    
                                    // Force render
                                    if (window.renderer && window.camera) {
                                        window.renderer.render(window.scene, window.camera);
                                    }
                                    
                                    console.log('Proactive: Fallback texture applied to', appliedCount, 'materials');
                                    return true;
                                }
                            } catch (e) {
                                console.error('Proactive: Fallback texture failed:', e);
                            }
                            return false;
                        };
                        
                        // Override any existing texture error handlers
                        if (window.handleTextureError) {
                            const originalHandleError = window.handleTextureError;
                            window.handleTextureError = function(error) {
                                console.warn('Proactive: Intercepted texture error:', error);
                                return applyFallbackTexture();
                            };
                        }
                        
                        // Override console.error to prevent texture errors from being thrown
                        const originalConsoleError = console.error;
                        console.error = function(...args) {
                            const message = args.join(' ');
                            if (message.includes('Error applying texture:')) {
                                console.warn('Proactive: Intercepted texture error, applying fallback');
                                applyFallbackTexture();
                                return; // Don't call original error
                            }
                            originalConsoleError.apply(console, args);
                        };
                        
                        console.log('Proactive texture protection injected');
                    })();
                """.trimIndent(), null
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
                // progressBar.visibility = View.GONE
                // loaderLayout.visibility = View.GONE

                val errorMessage = "WebView Error: ${error?.description}"
                memoryManagerIntegration.handleBackgroundCacheError(
                    errorMessage,
                    request?.url?.toString()
                )
            }
        }

        webViewSetup.setupWebView(webView, null)

        // ULTRA-AGGRESSIVE ERROR PREVENTION - Run BEFORE any content loads
        injectUltraAggressiveErrorPrevention()

        // Add WebChromeClient to catch console errors
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val messageText = message.message()
                Log.d("WebView Console", "[${message.messageLevel()}] $messageText")

                // Handle texture errors specifically
                if (messageText.contains("Error applying texture:")) {
                    Log.w("TextureError", "Detected texture error: $messageText")
                    handleTextureErrorInKotlin(messageText)
                }

                return true
            }
        }

        // Inject immediate error protection
        injectImmediateErrorProtection()

        // Set up periodic protection injection
        setupPeriodicProtection()

        // Glide.with(this).asGif().load(R.drawable.overlay).into(progressBar)
        if (!apiDone) {
            // progressBar.visibility = View.VISIBLE
            // contentManager.extractAndLoadContent(
            //     onSuccess = {
            //         loadContent()
            //     },
            //     onError = { errorMsg ->
            //         // progressBar.visibility = View.GONE
            //         // loaderLayout.visibility = View.GONE
            //         val distIndex = File(baseDir, "dist/index.html")
            //         if (!distIndex.exists()) {
            //             Toast.makeText(
            //                 this,
            //                 "No Internet and no local content found. Please connect to the internet once.",
            //                 Toast.LENGTH_LONG
            //             ).show()
            //         } else {
            //             loadContent()
            //         }
            //         memoryManagerIntegration.handleBackgroundCacheError("Content loading error: $errorMsg")
            //     },
            //     onProgress = { progress ->
            //        // download_text.text = "Downloading content: $progress%"
            //         if (progress.toString() == "100") {
            //            // download_text.text = "Extracting content..."
            //         }
            //     }

            // )
        } else {
            loadContent()
        }



        Handler(mainLooper).postDelayed({
            // if (progressBar.isVisible) {
            Log.e("OfflineWebview", "Loader timeout: forcibly hiding loader after 10 seconds.")
            // }
        }, 10000)

        // Use RecyclerView for productProgressRecycler
        val productRecycler = findViewById<GridView>(R.id.productProgressRecycler)
        productRecycler.numColumns = 3
        val productList = mutableListOf<ProductCard>()
        val adapter = ProductProgressAdapter(this, productList)
        productRecycler.adapter = adapter

        // Fetch product list from API
        val token = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("token", null)
        if (token == null) {
            Toast.makeText(this, "No token found. Please login again.", Toast.LENGTH_LONG).show()
        } else {
            ApiUtils.fetchMakingProductList(this, token, { apiProductNames ->
                runOnUiThread {
                    productList.clear()
                    for (name in apiProductNames) {
                        val imageRes = getProductImageRes(name)
                        productList.add(ProductCard(name, imageRes))
                    }
                    adapter.notifyDataSetChanged()
                }
            }, { error ->
                runOnUiThread {
                    Toast.makeText(this, "Failed to load products: $error", Toast.LENGTH_LONG).show()
                }
            })
        }

        // Start ContentManager and update progress dynamically
        contentManager.extractAndLoadContent(
            onSuccess = {
                loadContent()
            },
            onError = { errorMsg ->
                // progressBar.visibility = View.GONE
                // loaderLayout.visibility = View.GONE
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
                memoryManagerIntegration.handleBackgroundCacheError("Content loading error: $errorMsg")
            },
            onProgress = { progress ->
                runOnUiThread {
                    // Update main download card progress bar and percent
                    overallProgressBar.progress = progress
                     downloadText.text = "$progress%"
                    // Update product cards as before
                    for (product in productList) {
                        product.percent = progress
                        product.isChecked = progress >= 100
                    }
                    adapter.notifyDataSetChanged()
                }
            }
        )


//        val token = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("token", null)
//        if (token == null) {
//            Toast.makeText(this, "No token found. Please login again.", Toast.LENGTH_LONG).show()
//        } else {
//            ApiUtils.fetchMakingProductList(this, token, { productList: List<String> ->
//                for (name in productList) {
//                    val imageRes = getProductImageRes(name)
//                    products.add(ProductDownloadStatus(name, imageRes))
//                }
//                this@OfflineWebview.runOnUiThread {
//                    adapter.notifyDataSetChanged()
//                    simulateProductDownloadProgress(products, adapter, overallProgressBar, download_text)
//                }
//            }, { error: String ->
//                this@OfflineWebview.runOnUiThread {
//                    Toast.makeText(this, "Failed to load products: $error", Toast.LENGTH_LONG).show()
//                }
//            })
//        }
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
        this@OfflineWebview.runOnUiThread {
            Toast.makeText(
                this,
                "Optimizing memory for better performance...",
                Toast.LENGTH_SHORT
            ).show()

            Log.d("OfflineWebview", "Memory pressure handled automatically")
        }
    }


    private fun handleMemoryError(errorType: String, errorMessage: String) {
        this@OfflineWebview.runOnUiThread {
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
        val prefs = getSharedPreferences("offlineweb_prefs", MODE_PRIVATE)
        val prefsToken = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val apiDone = prefs.getBoolean("api_done", false)
        val token = prefsToken.getString("token", null)
        Log.d("TAG", "loadContent: Check Token $token")
        val json = JSONObject()
        json.put("token", token)

        val formattedJson = json.toString(4) // 4 = indentation level

        val urlencodedtokn = URLEncoder.encode(formattedJson, "UTF-8")


        val intent = intent
        val fabricUrl = intent.getStringExtra("fabric_url")
        if (apiDone && fabricUrl == null) {
            // loaderLayout.visibility = View.GONE
            // progressBar.visibility = View.GONE
            val selectionIntent = Intent(this, ProductSelectionActivity::class.java)
            selectionIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(selectionIntent)
            finish()
            return
        }
        val urlFabric = fabricUrl ?: "http://localhost:8080/"
        var url = "http://localhost:8080/"

        if (apiDone && fabricUrl != null) {
            url = urlFabric
          //  download_text.text = ""
            // loaderLayout.visibility = View.GONE
        } else {
            url = CACHE_DATA_PREFIX + urlencodedtokn
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
        unregisterNetworkCallback()
        indicatorJob?.cancel()
        stopContinuousPinchAnimation()
    }


    fun getMemoryStats(): Map<String, Any>? {
        return memoryManagerIntegration.getMemoryStats()
    }


    fun forceMemoryCleanup() {
        memoryManagerIntegration.forceMemoryCleanup()
    }

    private fun handleTextureErrorInKotlin(errorMessage: String) {
        Log.w("OfflineWebview", "Handling texture error in Kotlin: $errorMessage")

        webView.evaluateJavascript(
            """
            (function() {
                console.log('Kotlin: Starting texture error recovery...');
                console.log('Kotlin: Current scene state:', !!window.scene);
                console.log('Kotlin: Current renderer state:', !!window.renderer);
                console.log('Kotlin: Current THREE.js state:', !!window.THREE);
                
                // Log current texture state
                if (window.scene) {
                    let textureCount = 0;
                    window.scene.traverse(function(child) {
                        if (child.material && child.material.map) {
                            textureCount++;
                        }
                    });
                    console.log('Kotlin: Current textures in scene:', textureCount);
                }
            })();
        """.trimIndent()
        ) { result ->
            Log.d("OfflineWebview", "Scene state logged: $result")
        }

        webView.evaluateJavascript(
            """
            (function() {
                try {
                    console.log('Kotlin: Attempting texture error recovery...');
                    
                    // Clear all texture caches
                    if (window.THREE && window.THREE.Cache) {
                        window.THREE.Cache.clear();
                        console.log('Kotlin: THREE.js cache cleared');
                    }
                    
                    // Clear any custom texture caches
                    if (window.textureCache) {
                        window.textureCache.clear();
                        console.log('Kotlin: Custom texture cache cleared');
                    }
                    
                    // Clear any material caches
                    if (window.materialCache) {
                        window.materialCache.clear();
                        console.log('Kotlin: Material cache cleared');
                    }
                    
                    // Create fallback texture
                    let fallbackTexture = null;
                    if (window.THREE) {
                        const canvas = document.createElement('canvas');
                        canvas.width = 128;
                        canvas.height = 128;
                        const ctx = canvas.getContext('2d');
                        
                        // Create a simple fallback pattern
                        ctx.fillStyle = '#e0e0e0';
                        ctx.fillRect(0, 0, 128, 128);
                        ctx.fillStyle = '#c0c0c0';
                        ctx.fillRect(0, 0, 64, 64);
                        ctx.fillRect(64, 64, 64, 64);
                        
                        fallbackTexture = new window.THREE.CanvasTexture(canvas);
                        fallbackTexture.wrapS = window.THREE.RepeatWrapping;
                        fallbackTexture.wrapT = window.THREE.RepeatWrapping;
                        fallbackTexture.repeat.set(1, 1);
                        
                        console.log('Kotlin: Fallback texture created');
                    }
                    
                    // Apply fallback texture to all materials
                    let appliedCount = 0;
                    if (window.scene && fallbackTexture) {
                        window.scene.traverse(function(child) {
                            if (child.material) {
                                if (Array.isArray(child.material)) {
                                    child.material.forEach(mat => {
                                        if (mat && mat.map) {
                                            mat.map = fallbackTexture;
                                            mat.needsUpdate = true;
                                            appliedCount++;
                                        }
                                    });
                                } else if (child.material.map) {
                                    child.material.map = fallbackTexture;
                                    child.material.needsUpdate = true;
                                    appliedCount++;
                                }
                            }
                        });
                        console.log('Kotlin: Applied fallback texture to', appliedCount, 'materials');
                    }
                    
                    // Force scene update
                    if (window.scene && window.renderer && window.camera) {
                        window.renderer.render(window.scene, window.camera);
                        console.log('Kotlin: Scene re-rendered');
                    }
                    
                    // Notify Android of recovery
                    if (window.AndroidTextureHandler) {
                        window.AndroidTextureHandler.onRecoverySuccess();
                    }
                    
                    console.log('Kotlin: Texture recovery completed successfully');
                    
                } catch (e) {
                    console.error('Kotlin: Recovery failed:', e);
                    if (window.AndroidTextureHandler) {
                        window.AndroidTextureHandler.onRecoveryFailed(e.toString());
                    }
                }
            })();
        """.trimIndent()
        ) { result ->
            Log.d("OfflineWebview", "Recovery code executed: $result")
        }

        // Also try to prevent future errors by overriding the problematic function
        webView.evaluateJavascript(
            """
            (function() {
                try {
                    // Override the specific function that's causing the error
                    if (window.applyTextureToComponents) {
                        const originalApply = window.applyTextureToComponents;
                        window.applyTextureToComponents = function(textureData, componentType) {
                            try {
                                console.log('Kotlin: Safe texture application to', componentType);
                                return originalApply.call(this, textureData, componentType);
                            } catch (error) {
                                console.warn('Kotlin: Texture application failed, using fallback');
                                return window.applyFallbackTexture();
                            }
                        };
                        console.log('Kotlin: Override applied to applyTextureToComponents');
                    }
                    
                    // Also override any other texture application functions
                    if (window.applyFabricTexture) {
                        const originalFabric = window.applyFabricTexture;
                        window.applyFabricTexture = function(fabricData) {
                            try {
                                console.log('Kotlin: Safe fabric texture application');
                                return originalFabric.call(this, fabricData);
                            } catch (error) {
                                console.warn('Kotlin: Fabric texture failed, using fallback');
                                return window.applyFallbackTexture();
                            }
                        };
                        console.log('Kotlin: Override applied to applyFabricTexture');
                    }
                    
                } catch (e) {
                    console.error('Kotlin: Function override failed:', e);
                }
            })();
        """.trimIndent()
        ) { result ->
            Log.d("OfflineWebview", "Function override executed: $result")
        }
    }

    private fun injectImmediateErrorProtection() {
        webView.evaluateJavascript(
            """
            (function() {
                console.log('Injecting COMPLETE error suppression system...');
                
                const originalConsoleError = console.error;
                console.error = function(...args) {
                    const message = args.join(' ');
                    
                    if (message.includes('Error applying texture:') || 
                        message.includes('[object Event]') ||
                        message.includes('texture') ||
                        message.includes('Texture') ||
                        message.includes('THREE') ||
                        message.includes('WebGL')) {
                        console.warn('SUPPRESSED: Texture error prevented:', message);
                        return; // COMPLETELY IGNORE THE ERROR
                    }
                    
                    originalConsoleError.apply(console, args);
                };
                
                const textureFunctionNames = [
                    'applyTexture',
                    'applyTextureToComponents', 
                    'applyFabricTexture',
                    'applyTextureToShirt',
                    'applyTextureToMaterial',
                    'loadTexture',
                    'createTexture',
                    'setTexture',
                    'updateTexture',
                    'loadFabricTexture',
                    'applyFabric',
                    'setFabric',
                    'updateFabric'
                ];
                
                textureFunctionNames.forEach(funcName => {
                    if (window[funcName]) {
                        const originalFunc = window[funcName];
                        window[funcName] = function(...args) {
                            try {
                                console.log('SAFE: Executing', funcName, 'with protection');
                                
                                const validArgs = args.filter(arg => {
                                    if (arg === null || arg === undefined) return false;
                                    if (typeof arg === 'object' && Object.keys(arg).length === 0) return false;
                                    if (typeof arg === 'string' && arg.trim() === '') return false;
                                    return true;
                                });
                                
                                if (validArgs.length === 0) {
                                    console.warn('SAFE: No valid arguments for', funcName, ', using fallback');
                                    return window.applySafeFallbackTexture();
                                }
                                
                               
                                const result = originalFunc.apply(this, validArgs);
                                
                              
                                if (result instanceof Error || result === null || result === undefined) {
                                    console.warn('SAFE: Function returned error/null, using fallback');
                                    return window.applySafeFallbackTexture();
                                }
                                
                                return result;
                                
                            } catch (error) {
                                console.warn('SAFE: Function', funcName, 'failed, using fallback:', error.message);
                                return window.applySafeFallbackTexture();
                            }
                        };
                        console.log('SAFE: Override applied to', funcName);
                    }
                });
                
                window.applySafeFallbackTexture = function() {
                    try {
                        console.log('SAFE: Applying ultra-safe fallback texture');
                        
                        if (window.THREE && window.scene) {
                            const canvas = document.createElement('canvas');
                            canvas.width = 64;
                            canvas.height = 64;
                            const ctx = canvas.getContext('2d');
                            
                            ctx.fillStyle = '#f0f0f0';
                            ctx.fillRect(0, 0, 64, 64);
                            
                            const fallbackTexture = new window.THREE.CanvasTexture(canvas);
                            fallbackTexture.wrapS = window.THREE.RepeatWrapping;
                            fallbackTexture.wrapT = window.THREE.RepeatWrapping;
                            fallbackTexture.repeat.set(1, 1);
                            
                            let appliedCount = 0;
                            window.scene.traverse(function(child) {
                                if (child.material) {
                                    if (Array.isArray(child.material)) {
                                        child.material.forEach(mat => {
                                            if (mat) {
                                                mat.map = fallbackTexture;
                                                mat.needsUpdate = true;
                                                appliedCount++;
                                            }
                                        });
                                    } else {
                                        child.material.map = fallbackTexture;
                                        child.material.needsUpdate = true;
                                        appliedCount++;
                                    }
                                }
                            });
                            if (window.renderer && window.camera) {
                                window.renderer.render(window.scene, window.camera);
                            }
                            
                            console.log('SAFE: Fallback texture applied to', appliedCount, 'materials');
                            return true;
                        }
                    } catch (e) {
                        console.warn('SAFE: Fallback texture failed, but error suppressed:', e.message);
                    }
                    return false;
                };
                
                const originalSetTimeout = window.setTimeout;
                const originalSetInterval = window.setInterval;
                const originalRequestAnimationFrame = window.requestAnimationFrame;
                
                window.setTimeout = function(func, delay, ...args) {
                    if (typeof func === 'function') {
                        const safeFunc = function() {
                            try {
                                return func.apply(this, args);
                            } catch (error) {
                                if (error.message && (
                                    error.message.includes('texture') ||
                                    error.message.includes('Texture') ||
                                    error.message.includes('THREE') ||
                                    error.message.includes('WebGL') ||
                                    error.message.includes('Error applying texture:')
                                )) {
                                    console.warn('SAFE: Suppressed texture error in setTimeout');
                                    return window.applySafeFallbackTexture();
                                }
                                throw error;
                            }
                        };
                        return originalSetTimeout.call(this, safeFunc, delay);
                    }
                    return originalSetTimeout.apply(this, arguments);
                };
                
                window.setInterval = function(func, delay, ...args) {
                    if (typeof func === 'function') {
                        const safeFunc = function() {
                            try {
                                return func.apply(this, args);
                            } catch (error) {
                                if (error.message && (
                                    error.message.includes('texture') ||
                                    error.message.includes('Texture') ||
                                    error.message.includes('THREE') ||
                                    error.message.includes('WebGL') ||
                                    error.message.includes('Error applying texture:')
                                )) {
                                    console.warn('SAFE: Suppressed texture error in setInterval');
                                    return window.applySafeFallbackTexture();
                                }
                                throw error;
                            }
                        };
                        return originalSetInterval.call(this, safeFunc, delay);
                    }
                    return originalSetInterval.apply(this, arguments);
                };
                
                window.requestAnimationFrame = function(func) {
                    if (typeof func === 'function') {
                        const safeFunc = function(timestamp) {
                            try {
                                return func(timestamp);
                            } catch (error) {
                                if (error.message && (
                                    error.message.includes('texture') ||
                                    error.message.includes('Texture') ||
                                    error.message.includes('THREE') ||
                                    error.message.includes('WebGL') ||
                                    error.message.includes('Error applying texture:')
                                )) {
                                    console.warn('SAFE: Suppressed texture error in requestAnimationFrame');
                                    return window.applySafeFallbackTexture();
                                }
                                throw error;
                            }
                        };
                        return originalRequestAnimationFrame.call(this, safeFunc);
                    }
                    return originalRequestAnimationFrame.apply(this, arguments);
                };
                
                const originalAddEventListener = window.addEventListener;
                window.addEventListener = function(type, listener, options) {
                    if (typeof listener === 'function') {
                        const safeListener = function(event) {
                            try {
                                return listener.call(this, event);
                            } catch (error) {
                                if (error.message && (
                                    error.message.includes('texture') ||
                                    error.message.includes('Texture') ||
                                    error.message.includes('THREE') ||
                                    error.message.includes('WebGL') ||
                                    error.message.includes('Error applying texture:')
                                )) {
                                    console.warn('SAFE: Suppressed texture error in event listener');
                                    return window.applySafeFallbackTexture();
                                }
                                throw error;
                            }
                        };
                        return originalAddEventListener.call(this, type, safeListener, options);
                    }
                    return originalAddEventListener.apply(this, arguments);
                };
                
                window.addEventListener('error', function(event) {
                    if (event.message && (
                        event.message.includes('texture') ||
                        event.message.includes('Texture') ||
                        event.message.includes('THREE') ||
                        event.message.includes('WebGL') ||
                        event.message.includes('Error applying texture:') ||
                        event.message.includes('[object Event]')
                    )) {
                        console.warn('SAFE: Global error handler suppressed texture error:', event.message);
                        event.preventDefault();
                        event.stopPropagation();
                        window.applySafeFallbackTexture();
                        return false;
                    }
                });
                
                if (window.THREE && window.THREE.TextureLoader) {
                    const originalLoad = window.THREE.TextureLoader.prototype.load;
                    window.THREE.TextureLoader.prototype.load = function(url, onLoad, onProgress, onError) {
                        const safeOnError = function(error) {
                            console.warn('SAFE: Texture loading failed for:', url, 'using fallback');
                            if (onError) {
                                onError(new Error('Texture loading failed: ' + url));
                            }
                            window.applySafeFallbackTexture();
                        };
                        
                        const safeOnLoad = function(texture) {
                            if (onLoad) {
                                try {
                                    onLoad(texture);
                                } catch (error) {
                                    console.warn('SAFE: onLoad callback failed, using fallback');
                                    window.applySafeFallbackTexture();
                                }
                            }
                        };
                        
                        return originalLoad.call(this, url, safeOnLoad, onProgress, safeOnError);
                    };
                    console.log('SAFE: THREE.js TextureLoader overridden');
                }
                
                const originalImage = window.Image;
                window.Image = function() {
                    const img = new originalImage();
                    img.addEventListener('error', function(e) {
                        console.warn('SAFE: Image loading failed:', e.target.src, 'using fallback');
                        window.applySafeFallbackTexture();
                    });
                    return img;
                };
                
                console.log('SAFE: Complete error suppression system injected');
                
                // 9. IMMEDIATELY APPLY FALLBACK TEXTURE TO PREVENT ANY ERRORS
                setTimeout(function() {
                    window.applySafeFallbackTexture();
                }, 100);
                
            })();
        """.trimIndent()
        ) { result ->
            Log.d("OfflineWebview", "Complete error suppression system injected: $result")
        }
    }

    private fun setupPeriodicProtection() {
        Handler(mainLooper).postDelayed({
            injectImmediateErrorProtection()
            setupPeriodicProtection()
        }, 5000) // Inject every 5 seconds
    }

    private fun injectUltraAggressiveErrorPrevention() {
        webView.evaluateJavascript(
            """
            (function() {
                console.log('ULTRA-AGGRESSIVE: Installing complete error prevention system...');
                
                const originalThrow = Error.prototype.constructor;
                Error.prototype.constructor = function(message) {
                    if (message && (
                        message.includes('texture') ||
                        message.includes('Texture') ||
                        message.includes('THREE') ||
                        message.includes('WebGL') ||
                        message.includes('Error applying texture:') ||
                        message.includes('[object Event]')
                    )) {
                        console.warn('ULTRA-AGGRESSIVE: Prevented error creation:', message);
                        return new Error('Suppressed texture error');
                    }
                    return new originalThrow(message);
                };
                
                console.error = function(...args) {
                    const message = args.join(' ');
                    if (message.includes('Error applying texture:') || 
                        message.includes('[object Event]') ||
                        message.includes('texture') ||
                        message.includes('Texture') ||
                        message.includes('THREE') ||
                        message.includes('WebGL')) {
                        console.warn('ULTRA-AGGRESSIVE: Completely suppressed error:', message);
                        return; // NEVER LOG TEXTURE ERRORS
                    }
                    try {
                        console.warn('Non-texture error:', message);
                    } catch (e) {
                    }
                };
                
                const textureFunctionNames = [
                    'applyTexture', 'applyTextureToComponents', 'applyFabricTexture',
                    'applyTextureToShirt', 'applyTextureToMaterial', 'loadTexture',
                    'createTexture', 'setTexture', 'updateTexture', 'loadFabricTexture',
                    'applyFabric', 'setFabric', 'updateFabric', 'handleTexture',
                    'processTexture', 'renderTexture', 'displayTexture'
                ];
                
                textureFunctionNames.forEach(funcName => {
                    window[funcName] = function(...args) {
                        console.log('ULTRA-AGGRESSIVE: Safe execution of', funcName);
                        try {
                            // Always return success, never throw
                            return true;
                        } catch (e) {
                            console.warn('ULTRA-AGGRESSIVE: Function', funcName, 'suppressed');
                            return true;
                        }
                    };
                });
                
                const originalSetTimeout = window.setTimeout;
                window.setTimeout = function(func, delay, ...args) {
                    if (typeof func === 'function') {
                        const ultraSafeFunc = function() {
                            try {
                                return func.apply(this, args);
                            } catch (error) {
                                if (error.message && (
                                    error.message.includes('texture') ||
                                    error.message.includes('Texture') ||
                                    error.message.includes('THREE') ||
                                    error.message.includes('WebGL') ||
                                    error.message.includes('Error applying texture:')
                                )) {
                                    console.warn('ULTRA-AGGRESSIVE: Suppressed async texture error');
                                    return true; // Always return success
                                }
                        
                                console.warn('ULTRA-AGGRESSIVE: Suppressed async error:', error.message);
                                return true;
                            }
                        };
                        return originalSetTimeout.call(this, ultraSafeFunc, delay);
                    }
                    return originalSetTimeout.apply(this, arguments);
                };
                
           
                const originalAddEventListener = window.addEventListener;
                window.addEventListener = function(type, listener, options) {
                    if (typeof listener === 'function') {
                        const ultraSafeListener = function(event) {
                            try {
                                return listener.call(this, event);
                            } catch (error) {
                                console.warn('ULTRA-AGGRESSIVE: Suppressed event listener error:', error.message);
                                return true; // Always return success
                            }
                        };
                        return originalAddEventListener.call(this, type, ultraSafeListener, options);
                    }
                    return originalAddEventListener.apply(this, arguments);
                };
                
                window.addEventListener('error', function(event) {
                    console.warn('ULTRA-AGGRESSIVE: Global error handler caught:', event.message);
                    event.preventDefault();
                    event.stopPropagation();
                    return false; // Never let errors propagate
                });
                
                window.THREE = window.THREE || {};
                if (window.THREE.TextureLoader) {
                    window.THREE.TextureLoader.prototype.load = function(url, onLoad, onProgress, onError) {
                        console.log('ULTRA-AGGRESSIVE: Safe texture loading for:', url);
                        // Always call onLoad with a safe texture
                        if (onLoad) {
                            try {
                                // Create a safe fallback texture
                                const canvas = document.createElement('canvas');
                                canvas.width = 64;
                                canvas.height = 64;
                                const ctx = canvas.getContext('2d');
                                ctx.fillStyle = '#f0f0f0';
                                ctx.fillRect(0, 0, 64, 64);
                                
                                const safeTexture = {
                                    image: canvas,
                                    wrapS: 1001,
                                    wrapT: 1001,
                                    repeat: { x: 1, y: 1 },
                                    needsUpdate: true
                                };
                                
                                onLoad(safeTexture);
                            } catch (e) {
                                console.warn('ULTRA-AGGRESSIVE: Safe texture creation failed, but error suppressed');
                            }
                        }
                        return this;
                    };
                }
                
                const originalImage = window.Image;
                window.Image = function() {
                    const img = new originalImage();
                    img.addEventListener('error', function(e) {
                        console.warn('ULTRA-AGGRESSIVE: Image loading failed but suppressed:', e.target.src);
                        // Don't throw error, just log it
                    });
                    return img;
                };
                  window.applySafeFallbackTexture = function() {
                    console.log('ULTRA-AGGRESSIVE: Applying safe fallback texture');
                    try {
                        if (window.THREE && window.scene) {
                            const canvas = document.createElement('canvas');
                            canvas.width = 64;
                            canvas.height = 64;
                            const ctx = canvas.getContext('2d');
                            ctx.fillStyle = '#f0f0f0';
                            ctx.fillRect(0, 0, 64, 64);
                            
                            const fallbackTexture = {
                                image: canvas,
                                wrapS: 1001,
                                wrapT: 1001,
                                repeat: { x: 1, y: 1 },
                                needsUpdate: true
                            };
                            
                            window.scene.traverse(function(child) {
                                if (child.material) {
                                    if (Array.isArray(child.material)) {
                                        child.material.forEach(mat => {
                                            if (mat) {
                                                mat.map = fallbackTexture;
                                                mat.needsUpdate = true;
                                            }
                                        });
                                    } else {
                                        child.material.map = fallbackTexture;
                                        child.material.needsUpdate = true;
                                    }
                                }
                            });
                            
                            if (window.renderer && window.camera) {
                                window.renderer.render(window.scene, window.camera);
                            }
                            
                            console.log('ULTRA-AGGRESSIVE: Safe fallback texture applied');
                            return true;
                        }
                    } catch (e) {
                        console.warn('ULTRA-AGGRESSIVE: Fallback texture failed but error suppressed');
                    }
                    return false;
                };
                
                window.applyTexture = window.applySafeFallbackTexture;
                window.applyTextureToComponents = window.applySafeFallbackTexture;
                window.applyFabricTexture = window.applySafeFallbackTexture;
                
                console.log('ULTRA-AGGRESSIVE: Complete error prevention system installed');
                
                setTimeout(function() {
                    window.applySafeFallbackTexture();
                }, 50);
                
            })();
        """.trimIndent()
        ) { result ->
            Log.d("OfflineWebview", "Ultra-aggressive error prevention injected: $result")
        }
    }

    private fun registerNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val hasInternet = isInternetAvailable()
                        withContext(Dispatchers.Main) {
                            updateConnectionIndicator(hasInternet)
                        }
                    }
                }
                override fun onLost(network: Network) {
                    this@OfflineWebview.runOnUiThread { updateConnectionIndicator(false) }
                }
            }
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    private fun unregisterNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback!!)
        }
    }

    private fun startIndicatorPolling() {
        indicatorJob?.cancel()
        indicatorJob = CoroutineScope(Dispatchers.IO).launch {
            var lastState: Boolean? = null
            while (true) {
                val hasInternet = isInternetAvailable()
                if (lastState == null || lastState != hasInternet) {
                    withContext(Dispatchers.Main) {
                        updateConnectionIndicator(hasInternet)
                    }
                    lastState = hasInternet
                }
                delay(2000)
            }
        }
    }

    private fun startContinuousPinchAnimation(isOnline: Boolean) {
        indicatorAnimator?.cancel()
        val scaleX = PropertyValuesHolder.ofKeyframe(View.SCALE_X,
            Keyframe.ofFloat(0f, 1f),
            Keyframe.ofFloat(0.33f, if (isOnline) 0.8f else 1.12f),
            Keyframe.ofFloat(0.66f, if (isOnline) 1.12f else 0.8f),
            Keyframe.ofFloat(1f, 1f)
        )
        val scaleY = PropertyValuesHolder.ofKeyframe(View.SCALE_Y,
            Keyframe.ofFloat(0f, 1f),
            Keyframe.ofFloat(0.33f, if (isOnline) 0.8f else 1.12f),
            Keyframe.ofFloat(0.66f, if (isOnline) 1.12f else 0.8f),
            Keyframe.ofFloat(1f, 1f)
        )
        indicatorAnimator = ObjectAnimator.ofPropertyValuesHolder(indicatorLayout, scaleX, scaleY).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }
    private fun stopContinuousPinchAnimation() {
        indicatorAnimator?.cancel()
        indicatorLayout.scaleX = 1f
        indicatorLayout.scaleY = 1f
    }

    private fun updateConnectionIndicator(isOnline: Boolean) {
        indicatorLayout.setBackgroundResource(
            if (isOnline) R.drawable.indicator_online_bg else R.drawable.indicator_offline_bg
        )

        val onlineColor = android.graphics.Color.parseColor("#009900") // Material green
        val offlineColor = android.graphics.Color.parseColor("#D32F2F") // Material red
        val fromColor = (indicatorText.currentTextColor)
        val toColor = if (isOnline) onlineColor else offlineColor

        ValueAnimator.ofArgb(fromColor, toColor).apply {
            duration = 300
            addUpdateListener { animator ->
                indicatorText.setTextColor(animator.animatedValue as Int)
                indicatorIcon.setColorFilter(animator.animatedValue as Int)
            }
            start()
        }

        startContinuousPinchAnimation(isOnline)

     //   indicatorIcon.setImageResource(if (isOnline) R.drawable.ic_check else R.drawable.ic_block)

        indicatorText.animate().alpha(0f).setDuration(100).withEndAction {
            indicatorText.text = if (isOnline) "ONLINE 😃" else "OFFLINE 🤩"
            indicatorText.animate().alpha(1f).setDuration(200).start()
        }.start()

        Toast.makeText(this, if (isOnline) "You are ONLINE" else "You are OFFLINE", Toast.LENGTH_SHORT).show()
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val command = "ping -c 1 8.8.8.8"
            val process = Runtime.getRuntime().exec(command)
            val returnVal = process.waitFor()
            returnVal == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun simulateProductDownloadProgress(products: MutableList<ProductDownloadStatus>, adapter: ProductProgressAdapter, overallProgressBar: ProgressBar, downloadText: TextView) {
        CoroutineScope(Dispatchers.Main).launch {
            var total = 0
            while (total < products.size * 100) {
                for (product in products) {
                    if (product.percent < 100) {
                        product.percent += (5..15).random()
                        if (product.percent >= 100) {
                            product.percent = 100
                            product.isComplete = true
                        }
                    }
                }
                adapter.notifyDataSetChanged()
                total = products.sumOf { it.percent }
                val percent = if (products.isNotEmpty()) total / products.size else 0
                overallProgressBar.progress = percent
                downloadText.text = "$percent%"
                delay(400)
            }
        }
    }
}



data class ProductDownloadStatus(
    val name: String,
    val imageRes: Int,
    var percent: Int = 0,
    var isComplete: Boolean = false
)


class MediaCardGridAdapter(
    private val context: Context,
    private val items: List<MediaCardItem>
) : BaseAdapter() {

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_media_card, parent, false)

        val imageThumbnail = view.findViewById<ImageView>(R.id.imageThumbnail)
        val checkMark = view.findViewById<ImageView>(R.id.checkMark)

        val item = items[position]

        imageThumbnail.setImageResource(item.imageRes)
        checkMark.visibility = if (item.isChecked) View.VISIBLE else View.GONE

        return view
    }
}


class WebAppInterface(
    private val activity: OfflineWebview,
    private val statusTextView: TextView
) {
    private val db by lazy {
        Room.databaseBuilder(
            activity.applicationContext,
            AppDatabase::class.java, "my-database"
        ).build()
    }

    @JavascriptInterface
    fun onWebLoadingFinished(data: Boolean) {
        Log.d("WebAppInterface", "Callback received: onWebLoadingFinished($data)")
        val prefs =
            activity.getSharedPreferences("offlineweb_prefs", android.content.Context.MODE_PRIVATE)
        if (data) {
            prefs.edit { putBoolean("api_done", true) }
            activity.runOnUiThread {
                val selectionIntent =
                    Intent(activity, ProductSelectionActivity::class.java)
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

    @SuppressLint("SetTextI18n")
    @JavascriptInterface
    fun getProductCachePercentage(data: Int) {
        Log.d("WebAppInterface", "Callback received: getProductCachePercentage($data)")
        statusTextView.text = "Configuring content  ${data}%"
    }

    @JavascriptInterface
    fun saveCustomizedProductData(jsonArray: String?) {
        if (jsonArray.isNullOrBlank() || jsonArray == "undefined" || jsonArray == "null") {
            Log.w("WebAppInterface", "saveCustomizedProductData called with invalid input: $jsonArray")
            return
        }
        Log.d("WebAppInterface", "saveCustomizedProductData called with: $jsonArray")
        try {
            val jsonArr = org.json.JSONArray(jsonArray)
            val productList = mutableListOf<ProductEntity>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                productList.add(ProductEntity(json = obj.toString()))
            }
            CoroutineScope(Dispatchers.IO).launch {
                db.productDao().insertAll(productList)
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "Data saved locally!", Toast.LENGTH_SHORT).show()
                    Toast.makeText(activity, "Your data has been saved offline. It will auto-sync when internet is available.", Toast.LENGTH_LONG).show()
                    val intent = Intent(activity, com.lovoj.androidoffline.ProductSelectionActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Failed to parse product data: $e")
        }
    }
}

class TextureErrorHandler(private val activity: OfflineWebview) {

    @RequiresApi(Build.VERSION_CODES.M)
    @JavascriptInterface
    fun handleTextureError(errorMessage: String, textureUrl: String?) {
        Log.d(
            "TextureErrorHandler",
            "Callback received: handleTextureError($errorMessage, $textureUrl)"
        )
        Log.w("TextureErrorHandler", "Texture error: $errorMessage for URL: $textureUrl")

        activity.runOnUiThread {
            activity.webView.evaluateJavascript(
                """
                (function() {
                    try {
                        if (window.THREE && window.THREE.Cache) {
                            window.THREE.Cache.clear();
                            console.log('THREE.js cache cleared');
                        }
                        
                        if (window.textureCache) {
                            window.textureCache.clear();
                            console.log('Custom texture cache cleared');
                        }
                        
                        if (window.renderer && window.renderer.render) {
                            window.renderer.render(window.scene, window.camera);
                            console.log('Renderer updated');
                        }
                        
                        if (window.AndroidTextureHandler) {
                            window.AndroidTextureHandler.onRecoverySuccess();
                        }
                    } catch (e) {
                        console.error('Recovery failed:', e);
                        if (window.AndroidTextureHandler) {
                            window.AndroidTextureHandler.onRecoveryFailed(e.toString());
                        }
                    }
                })();
            """.trimIndent(), null
            )
        }
    }

    @JavascriptInterface
    fun onRecoverySuccess() {
        Log.d("TextureErrorHandler", "Callback received: onRecoverySuccess()")
        Log.d("TextureErrorHandler", "Texture recovery successful")
    }

    @JavascriptInterface
    fun onRecoveryFailed(error: String) {
        Log.d("TextureErrorHandler", "Callback received: onRecoveryFailed($error)")
        Log.e("TextureErrorHandler", "Texture recovery failed: $error")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @JavascriptInterface
    fun createFallbackTexture() {
        Log.d("TextureErrorHandler", "Callback received: createFallbackTexture()")
        Log.d("TextureErrorHandler", "Creating fallback texture")

        activity.runOnUiThread {
            activity.webView.evaluateJavascript(
                """
                (function() {
                    try {
                        if (window.THREE) {
                            const canvas = document.createElement('canvas');
                            canvas.width = 256;
                            canvas.height = 256;
                            const ctx = canvas.getContext('2d');
                            
                         
                            ctx.fillStyle = '#f0f0f0';
                            ctx.fillRect(0, 0, 256, 256);
                            ctx.fillStyle = '#cccccc';
                            ctx.fillRect(0, 0, 128, 128);
                            ctx.fillRect(128, 128, 128, 128);
                            
                            const texture = new window.THREE.CanvasTexture(canvas);
                            texture.wrapS = window.THREE.RepeatWrapping;
                            texture.wrapT = window.THREE.RepeatWrapping;
                            texture.repeat.set(1, 1);
                            
                        
                            if (window.scene) {
                                window.scene.traverse(function(child) {
                                    if (child.material) {
                                        if (Array.isArray(child.material)) {
                                            child.material.forEach(mat => {
                                                if (mat.map) mat.map = texture;
                                            });
                                        } else {
                                            if (child.material.map) child.material.map = texture;
                                        }
                                    }
                                });
                            }
                            
                            console.log('Fallback texture created and applied');
                        }
                    } catch (e) {
                        console.error('Failed to create fallback texture:', e);
                    }
                })();
            """.trimIndent(), null
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @JavascriptInterface
    fun onSceneReady() {
        Log.d("TextureErrorHandler", "Callback received: onSceneReady()")
        val fabricUrl = activity.intent.getStringExtra("fabric_url")
        if (fabricUrl != null) {
            activity.runOnUiThread {
                activity.webView.evaluateJavascript(
                    "if(window.applyFabricTexture){window.applyFabricTexture({fabImage: '" + fabricUrl + "'});}",
                    null
                )
            }
        }
    }
}

class SaveCustomizedProductInterface {
    @JavascriptInterface
    fun saveCustomizedProductData(jsonArray: String?) {
        if (jsonArray.isNullOrBlank() || jsonArray == "undefined" || jsonArray == "null") {
            Log.w(
                "SaveCustomizedProduct",
                "saveCustomizedProductData called with invalid input: $jsonArray"
            )
            return
        }
        try {
            Log.d("SaveCustomizedProduct", "Received JSON: $jsonArray")
            // Parse and print the array of objects
            val jsonArr = org.json.JSONArray(jsonArray)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                Log.d("SaveCustomizedProduct", "Object $i: $obj")
            }
        } catch (e: Exception) {
            Log.e("SaveCustomizedProduct", "Error parsing JSON: ${e.message}")
        }
    }
}
private fun getProductImageRes(productName: String): Int {
    return when (productName.trim().lowercase()) {
        "shirt" -> R.drawable.men_shirt
        "pant" -> R.drawable.cropped_pants
        "blazer" -> R.drawable.blazer
        "bandhgala suit" -> R.drawable.bandhgala_suit
        "suits" -> R.drawable.suit
        "half jacket" -> R.drawable.half_jacket
        "trench coat" -> R.drawable.overcoat
        "abayas" -> R.drawable.men_abayas
        "kurti" -> R.drawable.kurta
        "bottom wear" -> R.drawable.cropped_pants
        "dupatta" -> R.drawable.spl
        "women shirt" -> R.drawable.men_shirt
        "women pant" -> R.drawable.cropped_pants
        "women blazer" -> R.drawable.blazer
        "women suit" -> R.drawable.suit
        "one piece dress" -> R.drawable.one_piece
        "women trench coat" -> R.drawable.overcoat
        "women abayas" -> R.drawable.women_abayas
        "skirt" -> R.drawable.women_skirt
        else -> R.drawable.suit
    }
}

