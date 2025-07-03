package com.example.tvoffline.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(
    val email: String,
    val password: String,
    val storeType: String,
    val role: String
)

data class User(
    val id: String,
    val email: String,
    val storeType: String,
    val role: String
)

data class LoginResponse(
    val token: String?,
    val message: String?,
    val success: Boolean?
)

interface ApiService {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
} 