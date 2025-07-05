/*
package com.lovoj.androidoffline

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

*/
/**
 * Example Activity that demonstrates how to use LovojAppColors
 * This shows all the different ways to apply Lovoj colors in your app
 *//*

class LovojExampleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply Lovoj app theme
        LovojAppTheme.applyTheme(this, LovojAppTheme.THEME_LOVOJ_APP)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.example_lovoj_layout)
        
        // Apply colors programmatically
        applyLovojColorsProgrammatically()
        
        // Set up click listeners
        setupClickListeners()
        
        // Demonstrate color usage
        demonstrateColorUsage()
    }
    
    private fun applyLovojColorsProgrammatically() {
        // Method 1: Using LovojAppColors.getColor()
        val pinkButton = findViewById<MaterialButton>(R.id.lovojPinkButton)
        val primaryButton = findViewById<MaterialButton>(R.id.lovojPrimaryButton)
        val customButton = findViewById<LovojButton>(R.id.customLovojButton)
        val statusText = findViewById<TextView>(R.id.statusText)
        
        // Apply colors using the utility method
        pinkButton.setBackgroundColor(LovojAppColors.getColor(this, R.color.pink_button))
        primaryButton.setBackgroundColor(LovojAppColors.getColor(this, R.color.primary_color))
        
        // Apply text colors
        pinkButton.setTextColor(LovojAppColors.getColor(this, R.color.white_text))
        primaryButton.setTextColor(LovojAppColors.getColor(this, R.color.white_text))
        
        // Update status text
        statusText.setTextColor(LovojAppColors.getColor(this, R.color.grey_text))
        
        // Set custom button text
        customButton.setButtonText("Custom Lovoj Button")
    }
    
    private fun setupClickListeners() {
        val pinkButton = findViewById<MaterialButton>(R.id.lovojPinkButton)
        val primaryButton = findViewById<MaterialButton>(R.id.lovojPrimaryButton)
        val customButton = findViewById<LovojButton>(R.id.customLovojButton)
        
        pinkButton.setOnClickListener {
            showColorInfo("Pink Button", LovojAppColors.PINK_BUTTON)
        }
        
        primaryButton.setOnClickListener {
            showColorInfo("Primary Color", LovojAppColors.PRIMARY_COLOR)
        }
        
        customButton.setOnClickListener {
            showColorInfo("Custom Button", LovojAppColors.PINK_BUTTON)
        }
    }
    
    private fun demonstrateColorUsage() {
        // Method 2: Using color constants directly
        val primaryColor = LovojAppColors.PRIMARY_COLOR
        val pinkButtonColor = LovojAppColors.PINK_BUTTON
        val whiteTextColor = LovojAppColors.WHITE_TEXT
        val blackColor = LovojAppColors.BLACK
        
        // Method 3: Using color with alpha
        val pinkWithAlpha = LovojAppColors.getColorWithAlpha(pinkButtonColor, 0.5f)
        
        // Apply these colors to demonstrate
        val title = findViewById<TextView>(R.id.lovojTitle)
        title.setTextColor(blackColor)
        
        // You can also use these colors for other UI elements
        // For example, setting background colors, borders, etc.
    }
    
    private fun showColorInfo(colorName: String, colorValue: Int) {
        val hexColor = String.format("#%06X", (0xFFFFFF and colorValue))
        val message = "$colorName: $hexColor"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    */
/**
     * Example of how to create a dynamic color scheme
     *//*

    private fun createDynamicColorScheme() {
        // Get base colors
        val primaryColor = LovojAppColors.getColor(this, R.color.primary_color)
        val pinkColor = LovojAppColors.getColor(this, R.color.pink_button)
        
        // Create variations
        val lightPrimary = LovojAppColors.getColorWithAlpha(primaryColor, 0.3f)
        val darkPink = LovojAppColors.getColorWithAlpha(pinkColor, 0.7f)
        
        // Apply to different UI elements
        // findViewById<View>(R.id.someView).setBackgroundColor(lightPrimary)
        // findViewById<View>(R.id.anotherView).setBackgroundColor(darkPink)
    }
    
    */
/**
     * Example of how to switch between light and dark themes
     *//*

    private fun switchTheme(isDark: Boolean) {
        if (isDark) {
            LovojAppTheme.applyTheme(this, LovojAppTheme.THEME_LOVOJ_APP_DARK)
        } else {
            LovojAppTheme.applyTheme(this, LovojAppTheme.THEME_LOVOJ_APP)
        }
        // Recreate activity to apply theme changes
        recreate()
    }
} */
