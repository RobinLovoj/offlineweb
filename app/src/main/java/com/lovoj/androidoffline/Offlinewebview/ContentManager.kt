package com.lovoj.androidoffline.Offlinewebview

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream



class ContentManager(private val baseDir: File) {
    private val zipUrl = "https://d12hs8wunnl6k1.cloudfront.net/3dorder/dist.zip"


    @RequiresApi(Build.VERSION_CODES.N)
    fun extractAndLoadContent(onSuccess: () -> Unit, onError: (String) -> Unit, onProgress: (Int) -> Unit) {
        val distIndexFile = File(baseDir, "dist/index.html")
        if (distIndexFile.exists()) {
            Log.d("ContentManager", "dist/index.html already exists, skipping extraction.")
            onSuccess()
            return
        }
        Thread {
            try {
                if (baseDir.exists()) {
                    Log.d("ContentManager", "Cleaning existing directory: ${baseDir.absolutePath}")
                    baseDir.deleteRecursively()
                }
                baseDir.mkdirs()
                val zipFile = File(baseDir, "temp.zip")
                Log.d("ContentManager", "Downloading zip to: ${zipFile.absolutePath}")

                downloadZipFileWithProgress(zipUrl, zipFile) { percent ->
                    onProgress(percent);
                 }
                Log.d("ContentManager", "Download complete — zip size: ${zipFile.length()} bytes")

                ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zipStream ->
                    val buffer = ByteArray(8192)
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        if (entryName.startsWith("__MACOSX") || entryName.contains("/._")) {
                            Log.d("ContentManager", "Skipping macOS junk file: $entryName")
                            zipStream.closeEntry().also { entry = zipStream.nextEntry }
                            continue
                        }
                        val outputFile = File(baseDir, entryName)
                        if (entry.isDirectory) {
                            outputFile.mkdirs()
                            Log.d("ContentManager", "Created directory: ${outputFile.absolutePath}")
                        } else {
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { output ->
                                var len: Int
                                while (zipStream.read(buffer).also { len = it } > 0) {
                                    output.write(buffer, 0, len)
                                }
                            }
                             Log.d("ContentManager", "Extracted file: ${outputFile.absolutePath}")
                            if (entryName.endsWith(".glb")) {
                                Log.d("ContentManager", "GLB Extracted: ${outputFile.absolutePath}")
                            }
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                }
                zipFile.delete()
                Log.d("ContentManager", "Deleted temp zip file")
                val distIndex = File(baseDir, "dist/index.html")
                if (!distIndex.exists()) {
                    Log.e("ContentManager", "index.html not found after extraction!")
                    throw Exception("index.html not found after extraction!")
                }
                patchAssetPaths(distIndex)
                Log.d("ContentManager", "Patched asset paths in dist/index.html")
                Handler(Looper.getMainLooper()).post { onSuccess() }
            } catch (e: Exception) {
                Log.e("ContentManager", "Extraction error: ${e.message}", e)
                Handler(Looper.getMainLooper()).post { onError(e.message ?: "Unknown error") }
            }
        }.start()
    }

    private fun downloadZipFile(url: String, outputFile: File) {
        URL(url).openStream().use { input ->
            FileOutputStream(outputFile).use { output -> input.copyTo(output) }
        }
    }
    @RequiresApi(Build.VERSION_CODES.N)
    private fun downloadZipFileWithProgress(
        urlString: String,
        outputFile: File,
        onProgress: (percent: Int) -> Unit
    ) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connect()

        val totalSize = connection.contentLengthLong
        Log.d("Download", "Total file size: $totalSize bytes")

        val input = BufferedInputStream(connection.inputStream)
        val output = FileOutputStream(outputFile)

        val buffer = ByteArray(8 * 1024)
        var downloadedBytes: Long = 0
        var bytesRead: Int
        var lastProgress = 0

        try {
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead.toLong()

                if (totalSize > 0) {
                    val percent = ((downloadedBytes * 100) / totalSize).toInt()
                    if (percent != lastProgress) {
                        onProgress(percent)
                        lastProgress = percent
                    }
                } else {
                    onProgress(-1) // unknown size
                }
            }

            if (totalSize > 0) onProgress(100)

        } finally {
            input.close()
            output.close()
            connection.disconnect()
        }
    }




    private fun patchAssetPaths(distIndex: File) {
        try {
            var html = distIndex.readText()
            html = html.replace("src=\"/vite.svg", "src=\"vite.svg")
            distIndex.writeText(html)
        } catch (e: Exception) {
            Log.e("ContentManager", "Error patching asset paths: ${e.message}", e)
        }
    }

    private fun fixFolderNamesForWebView(baseDir: File) {
        try {
            val womenBodyDir = File(baseDir, "3dmodel/experience/assets/body/women")
            val oldOnePiece = File(womenBodyDir, "one_piece_dress")
            val newOnePiece = File(womenBodyDir, "one_piece_dress")
            if (oldOnePiece.exists() && !newOnePiece.exists()) {
                oldOnePiece.renameTo(newOnePiece)
            }
        } catch (e: Exception) {
            Log.e("ContentManager", "Error fixing folder names: ${e.message}", e)
        }
    }
} 