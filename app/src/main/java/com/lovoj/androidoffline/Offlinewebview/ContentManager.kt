package com.lovoj.androidoffline.Offlinewebview

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream


class ContentManager(private val baseDir: File) {
    private val zipUrl = "https://d12hs8wunnl6k1.cloudfront.net/3dorder/dist.zip"


    fun extractAndLoadContent(onSuccess: () -> Unit, onError: (String) -> Unit) {
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
                downloadZipFile(zipUrl, zipFile)
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