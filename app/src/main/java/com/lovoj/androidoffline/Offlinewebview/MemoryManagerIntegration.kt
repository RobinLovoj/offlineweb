package com.lovoj.androidoffline.Offlinewebview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import java.io.File


class MemoryManagerIntegration(
    private val context: Context,
    private val baseDir: File
) {
    companion object {
        private const val TAG = "MemoryManagerIntegration"
    }

    private val memoryManager = MemoryManager(context, baseDir)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        setupMemoryManagerCallbacks()
        Log.d("TAG", "MemoryManagerIntegration initialized")
    }

    private fun setupMemoryManagerCallbacks() {
        memoryManager.setMemoryPressureCallback { isHighPressure ->
            if (isHighPressure) {
                Log.w("TAG", "High memory pressure detected - performing cleanup")
                handleMemoryPressure()
            }
        }

        memoryManager.setErrorCallback { errorType, errorMessage ->
            Log.e("TAG", "Memory manager error [$errorType]: $errorMessage")
            handleMemoryError(errorType, errorMessage)
        }
    }

    /**
     * Handle memory pressure events
     */
    private fun handleMemoryPressure() {
        mainHandler.post {
            try {
                // Show user notification about memory cleanup
                showMemoryCleanupNotification()
                
                // Perform additional cleanup if needed
                performAdditionalCleanup()
                
            } catch (e: Exception) {
                Log.e("TAG", "Error handling memory pressure: ${e.message}")
            }
        }
    }

    /**
     * Handle memory manager errors
     */
    private fun handleMemoryError(errorType: String, errorMessage: String) {
        mainHandler.post {
            try {
                when (errorType) {
                    "BACKGROUND_CACHE_ERROR" -> {
                        Log.w("TAG", "Background cache error: $errorMessage")
                        // Handle background cache errors specifically
                        handleBackgroundCacheError(errorMessage)
                    }
                    "MEMORY_CLEANUP_ERROR" -> {
                        Log.e("TAG", "Memory cleanup error: $errorMessage")
                        // Try alternative cleanup methods
                        performAlternativeCleanup()
                    }
                    "MEMORY_CHECK_ERROR" -> {
                        Log.e("TAG", "Memory check error: $errorMessage")
                        // Reset monitoring if needed
                        resetMemoryMonitoring()
                    }
                    else -> {
                        Log.e("TAG", "Unknown memory error [$errorType]: $errorMessage")
                    }
                }
            } catch (e: Exception) {
                Log.e("TAG", "Error handling memory error: ${e.message}")
            }
        }
    }

    /**
     * Handle background cache errors
     */
    private fun handleBackgroundCacheError(errorMessage: String) {
        try {
            // Clear specific caches that might be causing issues
            clearProblematicCaches()
            
            // Notify background processor if available
            notifyBackgroundProcessorOfError(errorMessage)
            
        } catch (e: Exception) {
            Log.e("TAG", "Error handling background cache error: ${e.message}")
        }
    }

    /**
     * Clear problematic caches
     */
    private fun clearProblematicCaches() {
        try {
            // Clear WebView database
            context.deleteDatabase("webview.db")
            context.deleteDatabase("webviewCache.db")
            
            // Clear WebView cache directory
            val webViewCacheDir = File(context.cacheDir, "org.chromium.android_webview")
            if (webViewCacheDir.exists()) {
                webViewCacheDir.deleteRecursively()
            }
            
            Log.d("TAG", "Cleared problematic WebView caches")
            
        } catch (e: Exception) {
            Log.e("TAG", "Error clearing problematic caches: ${e.message}")
        }
    }

    /**
     * Notify background processor of errors
     */
    private fun notifyBackgroundProcessorOfError(errorMessage: String) {
        // This would be called when we have access to BackgroundProcessor
        // For now, we log the error
        Log.w("TAG", "Background processor should be notified of error: $errorMessage")
    }

    /**
     * Perform additional cleanup beyond standard memory cleanup
     */
    private fun performAdditionalCleanup() {
        try {
            // Clear any remaining temporary files
            clearRemainingTempFiles()
            
            // Clear any large files that might be cached
            clearLargeCachedFiles()
            
            // Force garbage collection
            System.gc()
            
            Log.d("TAG", "Additional cleanup completed")
            
        } catch (e: Exception) {
            Log.e("TAG", "Error performing additional cleanup: ${e.message}")
        }
    }

    /**
     * Clear remaining temporary files
     */
    private fun clearRemainingTempFiles() {
        try {
            val tempDir = File(context.cacheDir, "offline_web_temp")
            if (tempDir.exists()) {
                tempDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 1024 * 1024) { // Files larger than 1MB
                        file.delete()
                        Log.d("TAG", "Deleted large temp file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TAG", "Error clearing remaining temp files: ${e.message}")
        }
    }

    /**
     * Clear large cached files
     */
    private fun clearLargeCachedFiles() {
        try {
            val cacheDir = File(context.cacheDir, "offline_web_cache")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 5 * 1024 * 1024) { // Files larger than 5MB
                        file.delete()
                        Log.d("TAG", "Deleted large cache file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TAG", "Error clearing large cached files: ${e.message}")
        }
    }

    /**
     * Perform alternative cleanup methods
     */
    private fun performAlternativeCleanup() {
        try {
            // Try clearing application cache
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
            
            // Clear external cache
            context.externalCacheDir?.let { externalCache ->
                if (externalCache.exists()) {
                    externalCache.deleteRecursively()
                    externalCache.mkdirs()
                }
            }
            
            Log.d("TAG", "Alternative cleanup completed")
            
        } catch (e: Exception) {
            Log.e("TAG", "Error performing alternative cleanup: ${e.message}")
        }
    }

    /**
     * Reset memory monitoring
     */
    private fun resetMemoryMonitoring() {
        try {
            // Restart memory monitoring
            memoryManager.forceCleanup()
            Log.d("TAG", "Memory monitoring reset")
            
        } catch (e: Exception) {
            Log.e("TAG", "Error resetting memory monitoring: ${e.message}")
        }
    }

    /**
     * Show memory cleanup notification to user
     */
    private fun showMemoryCleanupNotification() {
        try {
            // Show a toast message to inform user about memory cleanup
            android.widget.Toast.makeText(
                context,
                "Optimizing memory usage...",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
        } catch (e: Exception) {
            Log.e("TAG", "Error showing memory cleanup notification: ${e.message}")
        }
    }

    /**
     * Register a WebView with memory management
     */
    fun registerWebView(id: String, webView: WebView) {
        memoryManager.registerWebView(id, webView)
    }

    /**
     * Unregister a WebView from memory management
     */
    fun unregisterWebView(id: String) {
        memoryManager.unregisterWebView(id)
    }

    /**
     * Handle background processor cache data errors
     */
    fun handleBackgroundCacheError(error: String, url: String? = null) {
        memoryManager.handleBackgroundCacheError(error, url)
    }

    /**
     * Force immediate memory cleanup
     */
    fun forceMemoryCleanup() {
        memoryManager.forceCleanup()
    }

    /**
     * Get memory statistics
     */
    fun getMemoryStats(): Map<String, Any> {
        return memoryManager.getMemoryStats()
    }

    /**
     * Get recent log entries
     */
    fun getLogEntries(limit: Int = 50): List<String> {
        return memoryManager.getLogEntries(limit)
    }

    /**
     * Check if memory pressure is high
     */
    fun isMemoryPressureHigh(): Boolean {
        return memoryManager.getMemoryUsagePercent() > 80
    }

    /**
     * Get current memory usage percen"TAG"e
     */
    fun getMemoryUsagePercent(): Int {
        return memoryManager.getMemoryUsagePercent()
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        memoryManager.cleanup()
    }

    fun setMemoryPressureCallback(callback: (Boolean) -> Unit) {
        memoryManager.setMemoryPressureCallback(callback)
    }

    fun setErrorCallback(callback: (String, String) -> Unit) {
        memoryManager.setErrorCallback(callback)
    }
} 