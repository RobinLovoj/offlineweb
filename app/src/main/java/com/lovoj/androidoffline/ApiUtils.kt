package com.lovoj.androidoffline

import android.content.Context
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object ApiUtils {
    fun fetchMakingProductList(context: Context, token: String, onSuccess: (List<String>) -> Unit, onError: (String) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://stagingb2b.lovoj.com/api/v1/auth/ownStoreDetalis")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    try {
                        val json = JSONObject(body)
                        val store = json.getJSONObject("store")
                        val makingProductList = store.getJSONArray("makingProductList")
                        val list = mutableListOf<String>()
                        for (i in 0 until makingProductList.length()) {
                            list.add(makingProductList.getString(i).trim())
                        }
                        onSuccess(list)
                    } catch (e: Exception) {
                        onError("Parsing error: ${e.message}")
                    }
                } else {
                    onError("API error: ${response.code}")
                }
            }
        })
    }
} 