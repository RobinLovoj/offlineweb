package com.example.tvoffline

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvoffline.network.LoginRequest
import com.example.tvoffline.network.LoginResponse
import com.example.tvoffline.network.RetrofitClient
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    private val _email = MutableLiveData<String>()
    val email: MutableLiveData<String> = _email

    private val _password = MutableLiveData<String>()
    val password: MutableLiveData<String> = _password

    private val _emailError = MutableLiveData<String>()
    val emailError: LiveData<String> = _emailError

    private val _passwordError = MutableLiveData<String>()
    val passwordError: LiveData<String> = _passwordError

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    init {
        _email.value = ""
        _password.value = ""
        _isLoading.value = false
    }

    private fun validateEmail(email: String) {
        when {
            email.isEmpty() -> _emailError.value = "Email is required"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> 
                _emailError.value = "Invalid email format"
            else -> _emailError.value = null
        }
    }

    private fun validatePassword(password: String) {
        when {
            password.isEmpty() -> _passwordError.value = "Password is required"
            password.length < 6 -> _passwordError.value = "Password must be at least 6 characters"
            else -> _passwordError.value = null
        }
    }

    fun onLoginClicked() {
        if (!validateInputs()) return

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val loginRequest = LoginRequest(
                    email = _email.value ?: "",
                    password = _password.value ?: "",
                    storeType = "Designer",
                    role = "admin"
                )

                val response = RetrofitClient.apiService.login(loginRequest)
                
                if (response.isSuccessful && response.body()?.success == true) {
                    _loginResult.value = LoginResult.Success(
                        token = response.body()?.token ?: "",
                        customerId = "65d07f45f74958cdcb1b24e0" // Using default customerId since it's not in the response
                    )
                } else {
                    _loginResult.value = LoginResult.Error(response.body()?.message ?: "Login failed")
                }
            } catch (e: Exception) {
                _loginResult.value = LoginResult.Error(e.message ?: "Network error occurred")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun validateInputs(): Boolean {
        validateEmail(_email.value ?: "")
        validatePassword(_password.value ?: "")
        return _emailError.value == null && _passwordError.value == null
    }

    sealed class LoginResult {
        data class Success(val token: String, val customerId: String) : LoginResult()
        data class Error(val message: String) : LoginResult()
    }
} 