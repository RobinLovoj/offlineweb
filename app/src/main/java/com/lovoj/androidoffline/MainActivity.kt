package com.lovoj.androidoffline

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.lovoj.tvoffline.MainFragment

/**
 * Loads [MainFragment].
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply Lovoj app theme for Android TV
        LovojAppTheme.applyTheme(this, LovojAppTheme.THEME_LOVOJ_APP_TV)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Apply Lovoj colors programmatically for TV interface
        applyLovojTVColors()
        
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_browse_fragment, MainFragment())
                .commitNow()
        }
    }
    
    private fun applyLovojTVColors() {
        // Get Lovoj TV colors
        val pinkButtonColor = LovojAppColors.getColor(this, R.color.pink_button)
        val whiteTextColor = LovojAppColors.getColor(this, R.color.white_text)
        val blackTextColor = LovojAppColors.getColor(this, R.color.black)
        val backgroundBlackColor = LovojAppColors.getColor(this, R.color.background_black)
        val tvBackgroundColor = LovojAppColors.getColor(this, R.color.tv_background)
        
        // Apply background color to main layout
        findViewById<android.view.View>(android.R.id.content)?.let { rootView ->
            rootView.setBackgroundColor(tvBackgroundColor)
        }
        
        // Apply colors to any visible UI elements
        // Note: Most TV UI elements are handled by the theme, but we can apply additional colors here
        
        // Set status bar color to pink
        window.statusBarColor = pinkButtonColor
        
        // Apply colors to any custom views if they exist
        applyColorsToCustomViews()
    }
    
    private fun applyColorsToCustomViews() {
        // Apply Lovoj colors to any custom views in the TV interface
        val pinkColor = LovojAppColors.getColor(this, R.color.pink_button)
        val whiteColor = LovojAppColors.getColor(this, R.color.white_text)
        
        // Example: If there are any custom buttons or text views
        // findViewById<Button>(R.id.custom_button)?.let { button ->
        //     button.setBackgroundColor(pinkColor)
        //     button.setTextColor(whiteColor)
        // }
    }
}