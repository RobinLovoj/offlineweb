package com.lovoj.androidoffline

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import com.lovoj.androidoffline.Offlinewebview.MemoryManagerIntegration
import java.io.File

/**
 * Example implementation showing how to integrate MemoryManager with your application
 * This class demonstrates best practices for memory management in Android apps
 */
class MemoryManagerExample {
    companion object {
        private const val TAG = "MemoryManagerExample"
    }

    private var memoryManagerIntegration: MemoryManagerIntegration? = null
    private var applicationContext: Context? = null

    /**
     * Initialize memory manager for the application
     */
    fun initializeMemoryManager(context: Context, baseDir: File) {
        applicationContext = context.applicationContext
        memoryManagerIntegration = MemoryManagerIntegration(context, baseDir)
        
        Log.d(TAG, "MemoryManager initialized successfully")
        
        // Set up memory pressure monitoring
        setupMemoryPressureMonitoring()
    }

    /**
     * Setup memory pressure monitoring and automatic cleanup
     */
    private fun setupMemoryPressureMonitoring() {
        memoryManagerIntegration?.setMemoryPressureCallback { isHighPressure ->
            if (isHighPressure) {
                Log.w(TAG, "High memory pressure detected - performing automatic cleanup")
                handleHighMemoryPressure()
            }
        }

        memoryManagerIntegration?.setErrorCallback { errorType, errorMessage ->
            Log.e(TAG, "Memory manager error [$errorType]: $errorMessage")
            handleMemoryError(errorType, errorMessage)
        }
    }

    /**
     * Handle high memory pressure situations
     */
    private fun handleHighMemoryPressure() {
        // Show user notification
        showMemoryOptimizationNotification()
        
        // Perform additional cleanup
        performEmergencyCleanup()
        
        // Log the event
        Log.w(TAG, "Emergency memory cleanup performed due to high pressure")
    }

    /**
     * Handle memory manager errors
     */
    private fun handleMemoryError(errorType: String, errorMessage: String) {
        when (errorType) {
            "BACKGROUND_CACHE_ERROR" -> {
                Log.w(TAG, "Background cache error handled: $errorMessage")
                // Handle background cache errors
                handleBackgroundCacheError(errorMessage)
            }
            "MEMORY_CLEANUP_ERROR" -> {
                Log.e(TAG, "Memory cleanup error: $errorMessage")
                // Try alternative cleanup methods
                performAlternativeCleanup()
            }
            else -> {
                Log.e(TAG, "Unknown memory error [$errorType]: $errorMessage")
            }
        }
    }

    /**
     * Handle background cache errors
     */
    private fun handleBackgroundCacheError(errorMessage: String) {
        // Clear problematic caches
        clearProblematicCaches()
        
        // Notify user if needed
        showCacheErrorNotification(errorMessage)
    }

    /**
     * Perform emergency cleanup when memory pressure is high
     */
    private fun performEmergencyCleanup() {
        try {
            // Force immediate cleanup
            memoryManagerIntegration?.forceMemoryCleanup()
            
            // Clear additional system caches
            clearSystemCaches()
            
            // Force garbage collection
            System.gc()
            
            Log.d(TAG, "Emergency cleanup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during emergency cleanup: ${e.message}")
        }
    }

    /**
     * Perform alternative cleanup methods
     */
    private fun performAlternativeCleanup() {
        try {
            // Clear application cache
            applicationContext?.cacheDir?.deleteRecursively()
            applicationContext?.cacheDir?.mkdirs()
            
            // Clear external cache
            applicationContext?.externalCacheDir?.let { externalCache ->
                if (externalCache.exists()) {
                    externalCache.deleteRecursively()
                    externalCache.mkdirs()
                }
            }
            
            Log.d(TAG, "Alternative cleanup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during alternative cleanup: ${e.message}")
        }
    }

    /**
     * Clear problematic caches
     */
    private fun clearProblematicCaches() {
        try {
            // Clear WebView databases
            applicationContext?.deleteDatabase("webview.db")
            applicationContext?.deleteDatabase("webviewCache.db")
            
            // Clear WebView cache directory
            val webViewCacheDir = File(applicationContext?.cacheDir, "org.chromium.android_webview")
            if (webViewCacheDir.exists()) {
                webViewCacheDir.deleteRecursively()
            }
            
            Log.d(TAG, "Problematic caches cleared")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing problematic caches: ${e.message}")
        }
    }

    /**
     * Clear system caches
     */
    private fun clearSystemCaches() {
        try {
            // Clear application cache
            applicationContext?.cacheDir?.deleteRecursively()
            applicationContext?.cacheDir?.mkdirs()
            
            // Clear external cache
            applicationContext?.externalCacheDir?.let { externalCache ->
                if (externalCache.exists()) {
                    externalCache.deleteRecursively()
                    externalCache.mkdirs()
                }
            }
            
            Log.d(TAG, "System caches cleared")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing system caches: ${e.message}")
        }
    }

    /**
     * Show memory optimization notification to user
     */
    private fun showMemoryOptimizationNotification() {
        try {
            android.widget.Toast.makeText(
                applicationContext,
                "Optimizing memory usage for better performance...",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing memory optimization notification: ${e.message}")
        }
    }

    /**
     * Show cache error notification to user
     */
    private fun showCacheErrorNotification(errorMessage: String) {
        try {
            android.widget.Toast.makeText(
                applicationContext,
                "Cache error detected and resolved",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing cache error notification: ${e.message}")
        }
    }

    /**
     * Register a WebView with memory management
     */
    fun registerWebView(id: String, webView: WebView) {
        memoryManagerIntegration?.registerWebView(id, webView)
        Log.d(TAG, "WebView registered with memory manager: $id")
    }

    /**
     * Unregister a WebView from memory management
     */
    fun unregisterWebView(id: String) {
        memoryManagerIntegration?.unregisterWebView(id)
        Log.d(TAG, "WebView unregistered from memory manager: $id")
    }

    /**
     * Handle background cache data errors
     */
    fun handleBackgroundCacheError(error: String, url: String? = null) {
        memoryManagerIntegration?.handleBackgroundCacheError(error, url)
        Log.w(TAG, "Background cache error handled: $error")
    }

    /**
     * Force immediate memory cleanup
     */
    fun forceMemoryCleanup() {
        memoryManagerIntegration?.forceMemoryCleanup()
        Log.d(TAG, "Forced memory cleanup executed")
    }

    /**
     * Get memory statistics
     */
    fun getMemoryStats(): Map<String, Any>? {
        return memoryManagerIntegration?.getMemoryStats()
    }

    /**
     * Get recent log entries
     */
    fun getLogEntries(limit: Int = 50): List<String>? {
        return memoryManagerIntegration?.getLogEntries(limit)
    }

    /**
     * Check if memory pressure is high
     */
    fun isMemoryPressureHigh(): Boolean {
        return memoryManagerIntegration?.isMemoryPressureHigh() ?: false
    }

    /**
     * Get current memory usage percentage
     */
    fun getMemoryUsagePercent(): Int {
        return memoryManagerIntegration?.getMemoryUsagePercent() ?: 0
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        memoryManagerIntegration?.cleanup()
        Log.d(TAG, "MemoryManagerExample cleanup completed")
    }
}

/**
 * Extension function to easily integrate MemoryManager with FragmentActivity
 */
fun FragmentActivity.setupMemoryManager(baseDir: File): MemoryManagerExample {
    val memoryManagerExample = MemoryManagerExample()
    memoryManagerExample.initializeMemoryManager(this, baseDir)
    return memoryManagerExample
}

/**
 * Extension function to register WebView with memory manager
 */
fun WebView.registerWithMemoryManager(memoryManager: MemoryManagerExample, id: String) {
    memoryManager.registerWebView(id, this)
}

/**
 * Extension function to unregister WebView from memory manager
 */
fun WebView.unregisterFromMemoryManager(memoryManager: MemoryManagerExample, id: String) {
    memoryManager.unregisterWebView(id)
} 