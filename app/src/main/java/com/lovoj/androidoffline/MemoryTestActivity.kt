//package com.lovoj.androidoffline
//
//import android.os.Bundle
//import android.util.Log
//import android.widget.Button
//import android.widget.TextView
//import androidx.appcompat.app.AppCompatActivity
//import com.lovoj.androidoffline.Offlinewebview.MemoryManagerIntegration
//import java.io.File
//
///**
// * Test Activity to demonstrate MemoryManager functionality
// * This activity shows memory statistics and allows manual testing
// */
//class MemoryTestActivity : AppCompatActivity() {
//
//    private lateinit var memoryManagerIntegration: MemoryManagerIntegration
//    private lateinit var statsTextView: TextView
//    private lateinit var logsTextView: TextView
//    private lateinit var forceCleanupButton: Button
//    private lateinit var getStatsButton: Button
//    private lateinit var getLogsButton: Button
//
//    private val baseDir by lazy {
//        File(filesDir, "offline_web").also { if (!it.exists()) it.mkdirs() }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_memory_test)
//
//        // Initialize UI components
//        statsTextView = findViewById(R.id.stats_text_view)
//        logsTextView = findViewById(R.id.logs_text_view)
//        forceCleanupButton = findViewById(R.id.force_cleanup_button)
//        getStatsButton = findViewById(R.id.get_stats_button)
//        getLogsButton = findViewById(R.id.get_logs_button)
//
//        // Initialize MemoryManager
//        memoryManagerIntegration = MemoryManagerIntegration(this, baseDir)
//
//        // Setup callbacks
//        setupMemoryCallbacks()
//
//        // Setup button click listeners
//        setupButtonListeners()
//
//        // Initial stats display
//        updateStatsDisplay()
//    }
//
//    private fun setupMemoryCallbacks() {
//        memoryManagerIntegration.setMemoryPressureCallback { isHighPressure ->
//            runOnUiThread {
//                if (isHighPressure) {
//                    Log.w("MemoryTest", "High memory pressure detected!")
//                    updateStatsDisplay()
//                }
//            }
//        }
//
//        memoryManagerIntegration.setErrorCallback { errorType, errorMessage ->
//            runOnUiThread {
//                Log.e("MemoryTest", "Memory error [$errorType]: $errorMessage")
//                updateLogsDisplay()
//            }
//        }
//    }
//
//    private fun setupButtonListeners() {
//        forceCleanupButton.setOnClickListener {
//            Log.d("MemoryTest", "Force cleanup triggered")
//            memoryManagerIntegration.forceMemoryCleanup()
//            updateStatsDisplay()
//            updateLogsDisplay()
//        }
//
//        getStatsButton.setOnClickListener {
//            updateStatsDisplay()
//        }
//
//        getLogsButton.setOnClickListener {
//            updateLogsDisplay()
//        }
//    }
//
//    private fun updateStatsDisplay() {
//        val stats = memoryManagerIntegration.getMemoryStats()
//        if (stats != null) {
//            val statsText = buildString {
//                appendLine("=== Memory Statistics ===")
//                appendLine("Memory Usage: ${stats["memoryUsagePercent"]}%")
//                appendLine("Available Memory: ${stats["availableMemory"]}")
//                appendLine("Total Memory: ${stats["totalMemory"]}")
//                appendLine("Memory Released: ${stats["totalMemoryReleased"]}")
//                appendLine("Cache Size: ${stats["cacheSize"]}")
//                appendLine("Error Count: ${stats["errorCount"]}")
//                appendLine("Monitoring Active: ${stats["isMonitoring"]}")
//                appendLine("Current WebView URL: ${stats["currentWebViewUrl"]}")
//                appendLine("Content Preserved: ${stats["webViewContentPreserved"]}")
//                appendLine("Last Cleanup: ${stats["lastCleanupTime"]}")
//            }
//            statsTextView.text = statsText
//        } else {
//            statsTextView.text = "No statistics available"
//        }
//    }
//
//    private fun updateLogsDisplay() {
//        val logs = memoryManagerIntegration.getLogEntries(20)
//        if (logs != null && logs.isNotEmpty()) {
//            val logsText = buildString {
//                appendLine("=== Recent Logs ===")
//                logs.forEach { log ->
//                    appendLine(log)
//                }
//            }
//            logsTextView.text = logsText
//        } else {
//            logsTextView.text = "No logs available"
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        memoryManagerIntegration.cleanup()
//    }
//}