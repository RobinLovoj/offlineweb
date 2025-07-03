package com.example.tvoffline.Offlinewebview

import android.util.Log
import android.webkit.WebResourceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL


class ApiHelper() {
    fun handleApiRequest(url: String, method: String): WebResourceResponse = runBlocking {
        try {
            makeApiRequest(url, method)
        } catch (e: Exception) {
            Log.e("ApiHelper", "API request failed: ${e.message}", e)
            createErrorResponse(500, e.message ?: "Internal Error")
        }
    }

    private suspend fun makeApiRequest(url: String, method: String): WebResourceResponse {
        var connection: HttpURLConnection? = null
        return withContext(Dispatchers.IO) {
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 10000
                    setRequestProperty("Content-Type", "application/json")
                    connect()
                }
                val responseCode = connection.responseCode
                val response = connection.inputStream.use { it.bufferedReader().readText() }
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    createSuccessResponse(response)
                } else {
                    createErrorResponse(responseCode, response)
                }
            } catch (e: Exception) {
                createErrorResponse(500, e.message ?: "Internal Error")
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun createSuccessResponse(response: String): WebResourceResponse {
        return WebResourceResponse(
            "application/json",
            "utf-8",
            response.byteInputStream()
        )
    }

    fun createErrorResponse(code: Int, message: String): WebResourceResponse {
        return WebResourceResponse(
            "application/json",
            "utf-8",
            code,
            "Error",
            mapOf(),
            "{\"error\":\"$message\"}".byteInputStream()
        )
    }

}