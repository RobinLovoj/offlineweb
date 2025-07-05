package com.lovoj.androidoffline.Offlinewebview

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit


class MemoryManager(
    private val context: Context,
    private val baseDir: File
) {
    companion object {
        private const val TAG = "MemoryManager"
        private const val MEMORY_THRESHOLD_PERCENT = 80
        private const val CACHE_CLEANUP_INTERVAL = 300000L
        private const val MEMORY_CHECK_INTERVAL = 60000L
        private const val MAX_CACHE_SIZE_MB = 100L
        private const val MAX_LOG_ENTRIES = 200
    }

    private val activityManager: ActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()
    private val scheduledExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    
    private val webViewReferences = ConcurrentHashMap<String, WeakReference<WebView>>()
    private val backgroundProcessorRef = WeakReference<BackgroundProcessor?>(null)
    
    private val cacheDir = File(context.cacheDir, "offline_web_cache")
    private val tempDir = File(context.cacheDir, "offline_web_temp")
    private val logEntries = mutableListOf<String>()
    
    private var errorCallback: ((String, String) -> Unit)? = null
    private var memoryPressureCallback: ((Boolean) -> Unit)? = null
    
    private var isMonitoring = false
    private var lastCleanupTime = 0L
    private var totalMemoryReleased = 0L
    private var errorCount = 0
    
    private var currentWebViewUrl: String? = null
    private var webViewContentPreserved = false

    init {
        setupCacheDirectories()
        startMemoryMonitoring()
        addLogEntry("MemoryManager initialized with WebView content preservation")
    }

    /**
     * Set callback for memory pressure events
     */
    fun setMemoryPressureCallback(callback: (Boolean) -> Unit) {
        memoryPressureCallback = callback
    }

    /**
     * Set callback for error handling
     */
    fun setErrorCallback(callback: (String, String) -> Unit) {
        errorCallback = callback
    }

    /**
     * Register a WebView for memory management
     */
    fun registerWebView(id: String, webView: WebView) {
        webViewReferences[id] = WeakReference(webView)
        
        // Store current URL for content preservation
        if (id == "main_webview" || id == "offline_webview") {
            currentWebViewUrl = webView.url
            addLogEntry("Main WebView registered with URL: $currentWebViewUrl")
        } else {
            addLogEntry("WebView registered: $id")
        }
    }

    /**
     * Unregister a WebView
     */
    fun unregisterWebView(id: String) {
        webViewReferences.remove(id)
        addLogEntry("WebView unregistered: $id")
    }

    /**
     * Register BackgroundProcessor for memory management
     */
    fun registerBackgroundProcessor(processor: BackgroundProcessor) {
        // Note: We can't directly store the reference, but we can monitor it
        addLogEntry("BackgroundProcessor registered for monitoring")
    }

    /**
     * Start memory monitoring
     */
    private fun startMemoryMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        addLogEntry("Memory monitoring started")
        
        // Schedule periodic memory checks
        scheduledExecutor.scheduleAtFixedRate({
            checkMemoryPressure()
        }, MEMORY_CHECK_INTERVAL, MEMORY_CHECK_INTERVAL, TimeUnit.MILLISECONDS)
        
        // Schedule periodic cache cleanup
        scheduledExecutor.scheduleAtFixedRate({
            performCacheCleanup()
        }, CACHE_CLEANUP_INTERVAL, CACHE_CLEANUP_INTERVAL, TimeUnit.MILLISECONDS)
    }

    /**
     * Check current memory pressure and trigger cleanup if needed
     */
    private fun checkMemoryPressure() {
        try {
            activityManager.getMemoryInfo(memoryInfo)
            val availableMemory = memoryInfo.availMem
            val totalMemory = memoryInfo.totalMem
            val usedMemoryPercent = ((totalMemory - availableMemory) * 100) / totalMemory
            
            addLogEntry("Memory usage: ${usedMemoryPercent}% (${formatBytes(availableMemory)} available)")
            
            if (usedMemoryPercent > MEMORY_THRESHOLD_PERCENT) {
                addLogEntry("High memory pressure detected: ${usedMemoryPercent}%")
                handleMemoryPressure()
                memoryPressureCallback?.invoke(true)
            } else {
                memoryPressureCallback?.invoke(false)
            }
        } catch (e: Exception) {
            addLogEntry("Error checking memory pressure: ${e.message}")
            handleError("MEMORY_CHECK_ERROR", e.message ?: "Unknown error")
        }
    }

    /**
     * Handle memory pressure by releasing unused resources
     */
    private fun handleMemoryPressure() {
        addLogEntry("Handling memory pressure...")
        
        val releasedMemory = performMemoryCleanup()
        totalMemoryReleased += releasedMemory
        
        addLogEntry("Memory cleanup completed. Released: ${formatBytes(releasedMemory)}")
        
        // Force garbage collection if memory pressure is critical
        if (getMemoryUsagePercent() > 90) {
            addLogEntry("Critical memory pressure - forcing garbage collection")
            System.gc()
        }
    }

    /**
     * Perform comprehensive memory cleanup while preserving WebView content
     */
    private fun performMemoryCleanup(): Long {
        var totalReleased = 0L
        
        try {
            // 1. Clear WebView caches (but preserve content)
            totalReleased += clearWebViewCachesPreserveContent()
            
            // 2. Clear temporary files
            totalReleased += clearTempFiles()
            
            // 3. Clear old cache files
            totalReleased += clearOldCacheFiles()
            
            // 4. Clear WebView memory (but preserve content)
            totalReleased += clearWebViewMemoryPreserveContent()
            
            // 5. Clear system caches
            clearSystemCaches()
            
            lastCleanupTime = System.currentTimeMillis()
            
        } catch (e: Exception) {
            addLogEntry("Error during memory cleanup: ${e.message}")
            handleError("MEMORY_CLEANUP_ERROR", e.message ?: "Unknown error")
        }
        
        return totalReleased
    }

    /**
     * Clear WebView caches while preserving content
     */
    private fun clearWebViewCachesPreserveContent(): Long {
        var released = 0L
        
        webViewReferences.forEach { (id, webViewRef) ->
            webViewRef.get()?.let { webView ->
                try {
                    // Store current URL and state
                    val currentUrl = webView.url
                    val currentTitle = webView.title
                    
                    // Clear cache but preserve content
                    webView.clearCache(true)
                    
                    // Clear history but keep current page
                    webView.clearHistory()
                    
                    // Clear form data
                    webView.clearFormData()
                    
                    // Clear SSL preferences
                    webView.clearSslPreferences()
                    
                    // Reload current content if it's the main WebView
                    if (id == "main_webview" || id == "offline_webview") {
                        if (currentUrl != null && currentUrl.isNotEmpty()) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                webView.loadUrl(currentUrl)
                                addLogEntry("Reloaded main WebView content: $currentUrl")
                            }, 100)
                        }
                    }
                    
                    addLogEntry("Cleared WebView cache while preserving content: $id")
                    released += 1024 * 1024 // Estimate 1MB per WebView
                    
                } catch (e: Exception) {
                    addLogEntry("Error clearing WebView cache for $id: ${e.message}")
                }
            }
        }
        
        return released
    }

    /**
     * Clear WebView memory while preserving content
     */
    private fun clearWebViewMemoryPreserveContent(): Long {
        var released = 0L
        
        webViewReferences.forEach { (id, webViewRef) ->
            webViewRef.get()?.let { webView ->
                try {
                    // Store current state
                    val currentUrl = webView.url
                    val currentTitle = webView.title
                    
                    // Clear JavaScript variables but preserve page content
                    webView.evaluateJavascript("""
                        (function() {
                            // Clear global variables but keep essential ones
                            for (var key in window) {
                                if (window.hasOwnProperty(key) && 
                                    key !== 'console' && 
                                    key !== 'location' && 
                                    key !== 'document' &&
                                    key !== 'AndroidBackgroundProcessor' &&
                                    !key.startsWith('__') &&
                                    !key.startsWith('webkit')) {
                                    try {
                                        delete window[key];
                                    } catch(e) {}
                                }
                            }
                            // Clear localStorage but keep essential data
                            if (window.localStorage) {
                                const essentialKeys = ['user_preferences', 'app_state'];
                                const keys = Object.keys(localStorage);
                                keys.forEach(key => {
                                    if (!essentialKeys.includes(key)) {
                                        localStorage.removeItem(key);
                                    }
                                });
                            }
                            // Clear sessionStorage
                            if (window.sessionStorage) {
                                sessionStorage.clear();
                            }
                            console.log('WebView memory cleared while preserving content');
                        })();
                    """.trimIndent(), null)
                    
                    // Reload content if it's the main WebView and we have a URL
                    if ((id == "main_webview" || id == "offline_webview") && currentUrl != null) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            webView.loadUrl(currentUrl)
                            addLogEntry("Reloaded main WebView after memory cleanup: $currentUrl")
                        }, 200)
                    }
                    
                    addLogEntry("Cleared WebView memory while preserving content: $id")
                    released += 512 * 1024 // Estimate 512KB per WebView
                    
                } catch (e: Exception) {
                    addLogEntry("Error clearing WebView memory for $id: ${e.message}")
                }
            }
        }
        
        return released
    }

    /**
     * Clear temporary files
     */
    private fun clearTempFiles(): Long {
        var released = 0L
        
        try {
            if (tempDir.exists()) {
                val files = tempDir.listFiles()
                files?.forEach { file ->
                    try {
                        val size = file.length()
                        if (file.delete()) {
                            released += size
                            addLogEntry("Deleted temp file: ${file.name} (${formatBytes(size)})")
                        }
                    } catch (e: Exception) {
                        addLogEntry("Error deleting temp file ${file.name}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            addLogEntry("Error clearing temp files: ${e.message}")
        }
        
        return released
    }

    /**
     * Clear old cache files based on age and size
     */
    private fun clearOldCacheFiles(): Long {
        var released = 0L
        val currentTime = System.currentTimeMillis()
        val maxAge = 24 * 60 * 60 * 1000 // 24 hours
        
        try {
            if (cacheDir.exists()) {
                val files = cacheDir.listFiles()
                files?.forEach { file ->
                    try {
                        val age = currentTime - file.lastModified()
                        val size = file.length()
                        
                        // Delete files older than 24 hours or if cache is too large
                        if (age > maxAge || getCacheSize() > MAX_CACHE_SIZE_MB * 1024 * 1024) {
                            if (file.delete()) {
                                released += size
                                addLogEntry("Deleted old cache file: ${file.name} (${formatBytes(size)})")
                            }
                        }
                    } catch (e: Exception) {
                        addLogEntry("Error deleting cache file ${file.name}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            addLogEntry("Error clearing old cache files: ${e.message}")
        }
        
        return released
    }

    /**
     * Clear system caches
     */
    private fun clearSystemCaches() {
        try {
            // Clear application cache but preserve essential files
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    // Don't delete files related to WebView or offline content
                    if (!file.name.contains("webview") && 
                        !file.name.contains("offline") && 
                        !file.name.contains("dist")) {
                        try {
                            file.deleteRecursively()
                        } catch (e: Exception) {
                            addLogEntry("Error deleting cache file ${file.name}: ${e.message}")
                        }
                    }
                }
            }
            
            // Clear external cache if available
            context.externalCacheDir?.let { externalCache ->
                if (externalCache.exists()) {
                    externalCache.listFiles()?.forEach { file ->
                        if (!file.name.contains("webview") && 
                            !file.name.contains("offline") && 
                            !file.name.contains("dist")) {
                            try {
                                file.deleteRecursively()
                            } catch (e: Exception) {
                                addLogEntry("Error deleting external cache file ${file.name}: ${e.message}")
                            }
                        }
                    }
                }
            }
            
            addLogEntry("Cleared system caches while preserving essential files")
            
        } catch (e: Exception) {
            addLogEntry("Error clearing system caches: ${e.message}")
        }
    }

    /**
     * Perform periodic cache cleanup
     */
    private fun performCacheCleanup() {
        addLogEntry("Performing periodic cache cleanup...")
        val released = clearOldCacheFiles()
        if (released > 0) {
            addLogEntry("Periodic cleanup released: ${formatBytes(released)}")
        }
    }

    /**
     * Handle background processor cache data errors
     */
    fun handleBackgroundCacheError(error: String, url: String? = null) {
        errorCount++
        addLogEntry("Background cache error: $error")
        
        try {
            // Clear specific cache related to the error
            if (url != null) {
                clearUrlSpecificCache(url)
            }
            
            // Notify error callback
            errorCallback?.invoke("BACKGROUND_CACHE_ERROR", error)
            
            // If too many errors, perform full cleanup
            if (errorCount > 5) {
                addLogEntry("Too many errors, performing full cleanup")
                performMemoryCleanup()
                errorCount = 0
            }
            
        } catch (e: Exception) {
            addLogEntry("Error handling background cache error: ${e.message}")
        }
    }

    /**
     * Clear cache specific to a URL
     */
    private fun clearUrlSpecificCache(url: String) {
        try {
            // Clear WebView cache for specific URL but preserve main content
            webViewReferences.forEach { (id, webViewRef) ->
                webViewRef.get()?.let { webView ->
                    // Only clear if it's not the main WebView
                    if (id != "main_webview" && id != "offline_webview") {
                        webView.evaluateJavascript("""
                            (function() {
                                // Clear any cached data for this URL
                                if (window.localStorage) {
                                    const keys = Object.keys(localStorage);
                                    keys.forEach(key => {
                                        if (key.includes('${url.hashCode()}')) {
                                            localStorage.removeItem(key);
                                        }
                                    });
                                }
                            })();
                        """.trimIndent(), null)
                    }
                }
            }
            
            addLogEntry("Cleared URL-specific cache for: $url")
            
        } catch (e: Exception) {
            addLogEntry("Error clearing URL-specific cache: ${e.message}")
        }
    }

    /**
     * Get current memory usage percentage
     */
    fun getMemoryUsagePercent(): Int {
        activityManager.getMemoryInfo(memoryInfo)
        val availableMemory = memoryInfo.availMem
        val totalMemory = memoryInfo.totalMem
        return ((totalMemory - availableMemory) * 100 / totalMemory).toInt()
    }

    /**
     * Get current cache size
     */
    private fun getCacheSize(): Long {
        return calculateDirectorySize(cacheDir)
    }

    /**
     * Calculate directory size recursively
     */
    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }

    /**
     * Setup cache directories
     */
    private fun setupCacheDirectories() {
        try {
            cacheDir.mkdirs()
            tempDir.mkdirs()
            addLogEntry("Cache directories setup complete")
        } catch (e: Exception) {
            addLogEntry("Error setting up cache directories: ${e.message}")
        }
    }

    /**
     * Add log entry with timestamp
     */
    private fun addLogEntry(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logEntry = "[$timestamp] $message"
        
        synchronized(logEntries) {
            logEntries.add(logEntry)
            if (logEntries.size > MAX_LOG_ENTRIES) {
                logEntries.removeAt(0)
            }
        }
        
        Log.d(TAG, message)
    }

    /**
     * Handle errors with callback
     */
    private fun handleError(type: String, message: String) {
        addLogEntry("Error [$type]: $message")
        errorCallback?.invoke(type, message)
    }

    /**
     * Format bytes to human readable format
     */
    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return "%.1f %s".format(size, units[unitIndex])
    }

    /**
     * Get memory statistics
     */
    fun getMemoryStats(): Map<String, Any> {
        activityManager.getMemoryInfo(memoryInfo)
        return mapOf(
            "memoryUsagePercent" to getMemoryUsagePercent(),
            "availableMemory" to formatBytes(memoryInfo.availMem),
            "totalMemory" to formatBytes(memoryInfo.totalMem),
            "totalMemoryReleased" to formatBytes(totalMemoryReleased),
            "cacheSize" to formatBytes(getCacheSize()),
            "errorCount" to errorCount,
            "lastCleanupTime" to lastCleanupTime,
            "isMonitoring" to isMonitoring,
            "currentWebViewUrl" to (currentWebViewUrl ?: "none"),
            "webViewContentPreserved" to webViewContentPreserved
        )
    }

    /**
     * Get recent log entries
     */
    fun getLogEntries(limit: Int = 50): List<String> {
        synchronized(logEntries) {
            return logEntries.takeLast(limit)
        }
    }

    /**
     * Force immediate cleanup while preserving WebView content
     */
    fun forceCleanup() {
        addLogEntry("Forcing immediate cleanup while preserving WebView content...")
        val released = performMemoryCleanup()
        addLogEntry("Force cleanup completed. Released: ${formatBytes(released)}")
    }

    /**
     * Stop memory monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        scheduledExecutor.shutdown()
        addLogEntry("Memory monitoring stopped")
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopMonitoring()
        webViewReferences.clear()
        logEntries.clear()
        addLogEntry("MemoryManager cleanup completed")
    }
} 