# Memory Manager for Android Offline Web Application

## Overview

The Memory Manager is a comprehensive solution for handling memory pressure, cache management, and background process error handling in Android applications. It automatically monitors system memory usage and releases unused resources when the Android system detects high memory consumption.

## Features

### 🧠 Memory Management
- **Automatic Memory Monitoring**: Continuously monitors system memory usage
- **Memory Pressure Detection**: Automatically detects when memory usage exceeds 80%
- **Intelligent Cleanup**: Releases unused resources when memory pressure is detected
- **WebView Memory Management**: Manages WebView caches and memory efficiently

### 🗂️ Cache Management
- **Automatic Cache Cleanup**: Removes old and large cache files
- **Temporary File Management**: Cleans up temporary files automatically
- **Cache Size Limits**: Enforces maximum cache size (100MB by default)
- **URL-Specific Cache Clearing**: Clears cache for specific URLs when errors occur

### 🔄 Background Process Error Handling
- **Cache Data Error Recovery**: Handles errors from background processes
- **Automatic Error Recovery**: Attempts to recover from cache-related errors
- **Error Logging**: Comprehensive logging of all memory and cache errors
- **Fallback Mechanisms**: Alternative cleanup methods when primary methods fail

### 📊 Monitoring & Statistics
- **Memory Usage Statistics**: Real-time memory usage monitoring
- **Cleanup Logs**: Detailed logs of all cleanup operations
- **Performance Metrics**: Track memory released and error counts
- **Memory Pressure Alerts**: Notifications when memory pressure is detected

## Classes Overview

### 1. MemoryManager
The core memory management class that handles:
- Memory pressure detection and response
- WebView cache and memory cleanup
- Temporary file management
- System cache clearing
- Error handling and recovery

### 2. MemoryManagerIntegration
Provides seamless integration between MemoryManager and existing components:
- BackgroundProcessor integration
- WebView registration and management
- Error callback handling
- Memory pressure notifications

### 3. MemoryManagerExample
Example implementation showing best practices:
- Application-level memory management setup
- Emergency cleanup procedures
- User notification handling
- Extension functions for easy integration

## Usage Examples

### Basic Setup

```kotlin
// In your Activity or Application class
class MainActivity : FragmentActivity() {
    private lateinit var memoryManager: MemoryManagerExample
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize memory manager
        val baseDir = File(filesDir, "offline_web")
        memoryManager = setupMemoryManager(baseDir)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        memoryManager.cleanup()
    }
}
```

### WebView Integration

```kotlin
// Register WebView with memory manager
val webView = WebView(this)
webView.registerWithMemoryManager(memoryManager, "main_webview")

// Unregister when done
webView.unregisterFromMemoryManager(memoryManager, "main_webview")
```

### Background Process Integration

```kotlin
// In your BackgroundProcessor
class BackgroundProcessor {
    private var memoryManagerIntegration: MemoryManagerIntegration? = null
    
    fun processCacheDataUrlInBackground(url: String) {
        // Initialize memory manager if needed
        if (memoryManagerIntegration == null) {
            memoryManagerIntegration = MemoryManagerIntegration(context, baseDir)
        }
        
        // Process URL...
    }
    
    // Handle cache errors
    fun handleCacheDataError(error: String, url: String? = null) {
        memoryManagerIntegration?.handleBackgroundCacheError(error, url)
    }
}
```

### Manual Memory Cleanup

```kotlin
// Force immediate cleanup
memoryManager.forceMemoryCleanup()

// Check memory pressure
if (memoryManager.isMemoryPressureHigh()) {
    // Handle high memory pressure
    performAdditionalCleanup()
}

// Get memory statistics
val stats = memoryManager.getMemoryStats()
println("Memory usage: ${stats["memoryUsagePercent"]}%")
```

## Configuration

### Memory Thresholds
```kotlin
// In MemoryManager.kt
private const val MEMORY_THRESHOLD_PERCENT = 80 // Trigger cleanup when memory usage > 80%
private const val MAX_CACHE_SIZE_MB = 100L // 100MB max cache size
```

### Cleanup Intervals
```kotlin
private const val CACHE_CLEANUP_INTERVAL = 300000L // 5 minutes
private const val MEMORY_CHECK_INTERVAL = 60000L // 1 minute
```

## Error Handling

### Background Cache Errors
The MemoryManager automatically handles background cache errors:

1. **Error Detection**: Monitors for cache-related errors in background processes
2. **Automatic Recovery**: Attempts to clear problematic caches
3. **Fallback Methods**: Uses alternative cleanup methods if primary methods fail
4. **User Notification**: Informs users when cache errors are resolved

### Memory Cleanup Errors
When memory cleanup fails:

1. **Alternative Cleanup**: Tries different cleanup methods
2. **System Cache Clearing**: Clears application and external caches
3. **Garbage Collection**: Forces garbage collection if needed
4. **Error Logging**: Logs all errors for debugging

## Monitoring and Debugging

### Memory Statistics
```kotlin
val stats = memoryManager.getMemoryStats()
// Returns map with:
// - memoryUsagePercent: Current memory usage percentage
// - availableMemory: Available memory in human-readable format
// - totalMemory: Total memory in human-readable format
// - totalMemoryReleased: Total memory released by cleanup operations
// - cacheSize: Current cache size
// - errorCount: Number of errors encountered
// - lastCleanupTime: Timestamp of last cleanup
// - isMonitoring: Whether monitoring is active
```

### Log Entries
```kotlin
val logs = memoryManager.getLogEntries(limit = 50)
// Returns recent log entries with timestamps
```

## Best Practices

### 1. Initialize Early
Initialize the MemoryManager as early as possible in your application lifecycle.

### 2. Register WebViews
Always register WebViews with the memory manager to ensure proper cleanup.

### 3. Handle Errors Gracefully
Set up error callbacks to handle memory and cache errors appropriately.

### 4. Monitor Memory Usage
Regularly check memory statistics to ensure optimal performance.

### 5. Cleanup Resources
Always call cleanup() when destroying activities or the application.

## Integration with Existing Code

The MemoryManager is designed to work seamlessly with your existing code:

1. **BackgroundProcessor**: Already integrated with error handling
2. **WebView Components**: Easy registration and management
3. **Activity Lifecycle**: Automatic cleanup on activity destruction
4. **Error Handling**: Comprehensive error recovery mechanisms

## Performance Impact

The MemoryManager is designed to have minimal performance impact:

- **Lightweight Monitoring**: Uses efficient system APIs for memory monitoring
- **Background Processing**: All cleanup operations run in background threads
- **Smart Scheduling**: Cleanup operations are scheduled to avoid UI blocking
- **Memory Efficient**: Uses weak references to avoid memory leaks

## Troubleshooting

### Common Issues

1. **High Memory Usage**: Check if WebViews are properly registered and unregistered
2. **Cache Errors**: Verify cache directories have proper permissions
3. **Cleanup Failures**: Check if files are locked by other processes

### Debug Information

Enable detailed logging by checking the logcat output with tag "MemoryManager" or "MemoryManagerIntegration".

## License

This MemoryManager implementation is part of the offline web application and follows the same licensing terms as the main project. 