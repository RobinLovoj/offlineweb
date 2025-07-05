package com.lovoj.androidoffline

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.lovoj.androidoffline.R


object LovojAppColors {

    const val PRIMARY_COLOR = 0xFF1C3E66
    const val SECONDARY_COLOR = 0xFF030868
    const val PINK_BUTTON = 0xFFF603D0
    const val PINK_TEXT = 0xFFF603D0
    const val PINK_AUTH_BACKGROUND = 0xFFCB42B6

    const val BACKGROUND_BLACK = 0xFF252525
    const val BACKGROUND_WHITE = 0xFFFFFFFF
    const val BACKGROUND_GRAY = 0xFFF0F0F0
    const val GREY_BACKGROUND = 0xFF808080

    const val WHITE_TEXT = 0xFFFFFFFF
    const val BLACK = 0xFF000000
    const val BLACK_GREY = 0xFF272727
    const val GREY_TEXT = 0xFF5A5A5A

    const val APP_BAR_WHITE_COLOR = 0xFFFFFFFF
    const val APP_BAR_COLOR = 0xFFFFFFFF
    const val BUTTON_BLACK_BACKGROUND = 0xFF000000

    const val PINK_BUTTON_SHADOW = 0x4DF603D0 // 30% alpha

    const val PRIMARY_50 = 0xFF8E9FB3
    const val PRIMARY_100 = 0xFF778BA3
    const val PRIMARY_200 = 0xFF607894
    const val PRIMARY_300 = 0xFF496585
    const val PRIMARY_400 = 0xFF335175
    const val PRIMARY_500 = 0xFF1C3E66
    const val PRIMARY_600 = 0xFF19385C
    const val PRIMARY_700 = 0xFF163252
    const val PRIMARY_800 = 0xFF142B47
    const val PRIMARY_900 = 0xFF11253D


    fun getColor(context: Context, colorResId: Int): Int {
        return ContextCompat.getColor(context, colorResId)
    }


    fun getColorWithAlpha(color: Int, alpha: Float): Int {
        return Color.argb(
            (alpha * 255).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
}

object LovojAppFonts {
    const val BELGRANO = "Belgrano"
    const val ROBOTO = "Roboto"
    const val POPPINS = "Poppins"
    const val MANROPE = "Manrope"
    const val JOST = "Jost"
    const val JOLLY_LODGER = "JollyLodger"
    const val KALAM = "Kalam"
    const val KNEWAVE = "Knewave"
    const val JS_MATH = "jsMath-cmti10"
    const val KREON = "Kreon"
    const val NUNITO = "Nunito"
}

object LovojAppTheme {

    const val THEME_LOVOJ_APP = "Theme.LovojApp"
    const val THEME_LOVOJ_APP_DARK = "Theme.LovojApp.Dark"
    const val THEME_LOVOJ_APP_TV = "Theme.LovojApp.TV"
    const val THEME_LOVOJ_APP_SPLASH = "Theme.LovojApp.Splash"


    const val STYLE_LOVOJ_BUTTON = "Widget.LovojApp.Button"
    const val STYLE_LOVOJ_TEXT_INPUT = "Widget.LovojApp.TextInputLayout"


    fun getThemeResourceId(context: Context, themeName: String): Int {
        return context.resources.getIdentifier(
            themeName,
            "style",
            context.packageName
        )
    }


    fun applyTheme(context: Context, themeName: String) {
        val themeId = getThemeResourceId(context, themeName)
        if (themeId != 0) {
            context.setTheme(themeId)
        }
    }
}
