# Lovoj App Theme - Android Implementation

This guide explains how the Flutter Lovoj app theme has been converted to Android and how to use it in your Android TV application.

## Overview

The Flutter theme has been successfully converted to Android with the following components:

### 1. Color Resources (`app/src/main/res/values/colors.xml`)

All the Lovoj app colors have been converted to Android color resources:

```xml
<!-- Primary Colors -->
<color name="primary_color">#1C3E66</color>
<color name="secondary_color">#030868</color>
<color name="pink_button">#F603D0</color>
<color name="pink_text">#F603D0</color>
<color name="pink_auth_background">#CB42B6</color>

<!-- Background Colors -->
<color name="background_black">#252525</color>
<color name="background_white">#FFFFFF</color>
<color name="background_gray">#F0F0F0</color>
<color name="grey_background">#808080</color>

<!-- Text Colors -->
<color name="white_text">#FFFFFF</color>
<color name="black">#000000</color>
<color name="black_grey">#272727</color>
<color name="grey_text">#5A5A5A</color>
```

### 2. Theme Styles (`app/src/main/res/values/themes.xml`)

Three main themes have been created:

- **Theme.LovojApp** - Light theme for regular Android
- **Theme.LovojApp.Dark** - Dark theme variant
- **Theme.LovojApp.TV** - Android TV specific theme
- **Theme.LovojApp.Splash** - Splash screen theme

### 3. Dark Theme Support (`app/src/main/res/values-night/themes.xml`)

Automatic dark theme support that activates when the device is in dark mode.

### 4. Kotlin Utility Class (`LovojAppTheme.kt`)

A utility class that provides easy access to colors and theme management:

```kotlin
object LovojAppColors {
    const val PRIMARY_COLOR = 0xFF1C3E66
    const val PINK_BUTTON = 0xFFF603D0
    // ... more colors
}

object LovojAppTheme {
    const val THEME_LOVOJ_APP = "Theme.LovojApp"
    const val THEME_LOVOJ_APP_TV = "Theme.LovojApp.TV"
    // ... theme management methods
}
```

## Usage Instructions

### 1. Applying Themes to Activities

#### For Android TV Activities:
```kotlin
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply Lovoj app theme for Android TV
        LovojAppTheme.applyTheme(this, LovojAppTheme.THEME_LOVOJ_APP_TV)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
```

#### For Regular Android Activities:
```kotlin
class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply Lovoj app theme
        LovojAppTheme.applyTheme(this, LovojAppTheme.THEME_LOVOJ_APP)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
    }
}
```

### 2. Using Colors in XML Layouts

#### Buttons:
```xml
<Button
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:background="@color/pink_button"
    android:textColor="@color/white_text"
    android:text="Login" />
```

#### Text Views:
```xml
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textColor="@color/black"
    android:text="Welcome to Lovoj" />
```

#### Cards:
```xml
<com.google.android.material.card.MaterialCardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/background_white"
    app:cardCornerRadius="12dp"
    app:cardElevation="2dp">
    <!-- Card content -->
</com.google.android.material.card.MaterialCardView>
```

### 3. Using Colors Programmatically

```kotlin
// Get color from resource
val pinkColor = LovojAppColors.getColor(this, R.color.pink_button)
button.setBackgroundColor(pinkColor)

// Use color constants
val primaryColor = LovojAppColors.PRIMARY_COLOR
view.setBackgroundColor(primaryColor)

// Get color with alpha
val shadowColor = LovojAppColors.getColorWithAlpha(pinkColor, 0.3f)
```

### 4. Custom Widget Styles

The theme includes custom styles for buttons and text inputs:

#### Button Style:
```xml
<Button
    style="@style/Widget.LovojApp.Button"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Custom Button" />
```

#### Text Input Style:
```xml
<com.google.android.material.textfield.TextInputLayout
    style="@style/Widget.LovojApp.TextInputLayout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    
    <com.google.android.material.textfield.TextInputEditText
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Enter text" />
</com.google.android.material.textfield.TextInputLayout>
```

## Theme Features

### 1. Material Design Components
- Uses Material Design 3 components
- Custom button and input styles
- Card elevation and corner radius
- Proper color scheme implementation

### 2. Android TV Optimization
- TV-specific theme with appropriate colors
- Optimized for remote control navigation
- Proper contrast for TV displays

### 3. Dark Mode Support
- Automatic dark theme switching
- Consistent color scheme in both modes
- Proper contrast ratios

### 4. Accessibility
- High contrast colors
- Proper text color combinations
- Screen reader friendly

## Color Mapping from Flutter to Android

| Flutter Color | Android Color Resource | Hex Value |
|---------------|----------------------|-----------|
| `primaryColor` | `primary_color` | `#1C3E66` |
| `secondaryColor` | `secondary_color` | `#030868` |
| `pinkButton` | `pink_button` | `#F603D0` |
| `pinkText` | `pink_text` | `#F603D0` |
| `backgroundBlack` | `background_black` | `#252525` |
| `backgroundWhite` | `background_white` | `#FFFFFF` |
| `whiteText` | `white_text` | `#FFFFFF` |
| `black` | `black` | `#000000` |

## Migration Notes

### What's Been Updated:
1. ✅ All Flutter colors converted to Android color resources
2. ✅ Theme configuration matching Flutter's `LovojAppTheme`
3. ✅ Custom button and input styles
4. ✅ Android TV specific theme
5. ✅ Dark mode support
6. ✅ Kotlin utility class for easy theme management

### Legacy Compatibility:
- Old theme names are preserved for backward compatibility
- Existing activities will continue to work
- Gradual migration to new theme names is possible

## Best Practices

1. **Always apply themes before `super.onCreate()`** to ensure proper styling
2. **Use color resources instead of hardcoded values** for consistency
3. **Test both light and dark modes** to ensure proper contrast
4. **Use the Kotlin utility class** for programmatic color access
5. **Follow Material Design guidelines** for spacing and typography

## Troubleshooting

### Theme Not Applying:
- Ensure theme is applied before `super.onCreate()`
- Check that the theme name exists in `themes.xml`
- Verify the activity extends the correct base class

### Colors Not Showing:
- Use `@color/` prefix in XML layouts
- Use `LovojAppColors.getColor()` for programmatic access
- Check that color resources are properly defined

### Dark Mode Issues:
- Ensure `values-night/themes.xml` exists
- Test on device with dark mode enabled
- Verify color contrast ratios

This implementation provides a complete Android equivalent of the Flutter Lovoj app theme, maintaining the same visual identity and user experience across platforms. 