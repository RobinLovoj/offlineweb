package com.lovoj.androidoffline

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.core.content.edit
import com.lovoj.androidoffline.Offlinewebview.OfflineWebview
import com.lovoj.androidoffline.databinding.ActivityLoginBinding
import android.widget.TextView

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel
    private var loadingAnimation: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply Lovoj app theme
        LovojAppTheme.applyTheme(this, LovojAppTheme.THEME_LOVOJ_APP)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_login)
        binding.lifecycleOwner = this

        // Apply Lovoj colors programmatically
        applyLovojColors()

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]
        binding.viewModel = viewModel

        val view = this.currentFocus ?: View(this)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewModel.loginResult.observe(this) { result ->
            when (result) {
                is LoginViewModel.LoginResult.Success -> {
                    getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit {
                        putString("token", result.token)
                        putString("customerId", result.customerId)
                        apply()
                    }

                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, OfflineWebview::class.java).apply {
                        putExtra("token", result.token)
                        putExtra("customerId", result.customerId)
                    }
                    startActivity(intent)
                    finish()
                }
                is LoginViewModel.LoginResult.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            if (loading) {
                startLoadingAnimation()
            } else {
                stopLoadingAnimation()
            }
        }
    }

    private fun applyLovojColors() {
        // Get Lovoj colors for white background theme
        val pinkButtonColor = LovojAppColors.getColor(this, R.color.pink_button)
        val whiteTextColor = LovojAppColors.getColor(this, R.color.white_text)
        val blackTextColor = LovojAppColors.getColor(this, R.color.black)
        val backgroundWhiteColor = LovojAppColors.getColor(this, R.color.background_white)
        val greyTextColor = LovojAppColors.getColor(this, R.color.grey_text)
        
        // Apply colors to login button only (other colors are set in XML)
        binding.loginButton?.let { button ->
            button.setBackgroundColor(pinkButtonColor)
            button.setTextColor(whiteTextColor)
        }
        
        // Apply white background color to root
        binding.root.setBackgroundColor(backgroundWhiteColor)
        
        // Set status bar color to pink
        window.statusBarColor = pinkButtonColor
    }

    private fun startLoadingAnimation() {
        val loadingLogo = binding.loadingOverlay.findViewById<ImageView>(R.id.loadingLogo)
        
        // Create rotation animation
        val rotation = ObjectAnimator.ofFloat(loadingLogo, View.ROTATION, 0f, 360f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }

        // Create pulse animation
        val scaleX = ObjectAnimator.ofFloat(loadingLogo, View.SCALE_X, 0.8f, 1.2f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        val scaleY = ObjectAnimator.ofFloat(loadingLogo, View.SCALE_Y, 0.8f, 1.2f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Create fade animation
        val alpha = ObjectAnimator.ofFloat(loadingLogo, View.ALPHA, 0.6f, 1f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        AnimatorSet().apply {
            playTogether(rotation, scaleX, scaleY, alpha)
            start()
            loadingAnimation = this
        }
    }

    private fun stopLoadingAnimation() {
        loadingAnimation?.cancel()
        loadingAnimation = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLoadingAnimation()
    }
}
