package com.example.tvoffline.Offlinewebview

import fi.iki.elonen.NanoHTTPD
import java.io.File

class LocalWebServer(
    private val rootDir: File,
    port: Int = 8080
) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        return try {
            var uri = session.uri
            if (uri == "/") uri = "/index.html"

            // If the request is for /3dmodel or /assets, serve from /dist/
            val file: File = when {
                uri.startsWith("/3dmodel/") || uri.startsWith("/assets/") -> {
                    File(rootDir, "dist$uri")
                }
                else -> {
                    File(rootDir, uri.removePrefix("/"))
                }
            }

            var targetFile = file
            if (targetFile.isDirectory) {
                targetFile = File(targetFile, "index.html")
            }

            if (!targetFile.exists()) {
                if (!uri.contains(".") || uri.endsWith(".html")) {
                    targetFile = File(rootDir, "dist/index.html")
                } else {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found")
                }
            }

            val mime = getMimeTypeForFile(targetFile.name)
            val fis = targetFile.inputStream()
            newFixedLengthResponse(Response.Status.OK, mime, fis, targetFile.length())
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.toString())
        }
    }
}