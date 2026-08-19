package io.github.secondjb.annarboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val offBlack = Color(0xFF1E1E1E)
val offWhite = Color(0xFFFAFAFA)
val muiSecondary = Color(0xFF9C27B0) // <--- Add MUI's default secondary purple

// Helper to create themes based on the JS definitions
fun createThemeObj(
    isDark: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    bgColor: Color,
    surfaceColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color
): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            background = bgColor,
            surface = surfaceColor,
            onPrimary = textPrimaryColor, // Contrast text
            onBackground = textPrimaryColor,
            onSurface = textPrimaryColor
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            background = bgColor,
            surface = surfaceColor,
            onPrimary = Color.White,
            onBackground = textPrimaryColor,
            onSurface = textPrimaryColor
        )
    }
}

// Predefined Themes based on ThemeContext.jsx
val THEMES_MAP = mapOf(
    "dark" to createThemeObj(true, Color(0xFFFFCB05), Color(0xFF00274C), Color(0xFF121212), Color(0xFF1E1E1E), offWhite, offWhite),
    "light" to createThemeObj(false, Color(0xFF00274C), Color(0xFFFFCB05), offWhite, Color(0xFFFFFFFF), offBlack, offBlack),
    // Replace the duplicated primary colors with muiSecondary below:
    "blue" to createThemeObj(false, Color(0xFF2196F3), muiSecondary, Color(0xFFE3F2FD), offWhite, Color(0xFF0D47A1), offBlack),
    "red" to createThemeObj(false, Color(0xFFF44336), muiSecondary, Color(0xFFFFEBEE), offWhite, Color(0xFFB71C1C), offBlack),
    "dark_blue" to createThemeObj(true, Color(0xFF1E84D4), muiSecondary, Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFF7BA6E9), offWhite),
    "lime_green" to createThemeObj(false, Color(0xFF7FDB31), muiSecondary, Color(0xFFF9FBE7), offWhite, Color(0xFF73DD1A), offBlack),
    "dark_lime_green" to createThemeObj(true, Color(0xFF76FF03), muiSecondary, Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFFADFB51), offWhite),
    "pink" to createThemeObj(false, Color(0xFFF06292), muiSecondary, Color(0xFFFCE4EC), offWhite, Color(0xFFCA3778), offBlack),
    "dark_pink" to createThemeObj(true, Color(0xFFFF4081), muiSecondary, Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFFFF80AB), offWhite),
    "orange" to createThemeObj(false, Color(0xFFFF9800), muiSecondary, Color(0xFFFFF3E0), offWhite, Color(0xFFE26420), offBlack),
    "dark_orange" to createThemeObj(true, Color(0xFFFB8C00), muiSecondary, Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFFF0B153), offWhite),
    "dark_red" to createThemeObj(true, Color(0xFFD32F2F), muiSecondary, Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFFEB7373), offWhite),
    "purple" to createThemeObj(false, Color(0xFFAB47BC), muiSecondary, Color(0xFFF3E5F5), offWhite, Color(0xFFBA4DCD), offBlack),
    "dark_purple" to createThemeObj(true, Color(0xFF8E24AA), muiSecondary, Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFFB86BC5), offWhite),
    "teal" to createThemeObj(false, Color(0xFF26A69A), muiSecondary, Color(0xFFE0F2F1), offWhite, Color(0xFF158D7E), offBlack),
    "dark_teal" to createThemeObj(true, Color(0xFF26A69A), muiSecondary, Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFF80CBC4), offWhite),
    "yellow" to createThemeObj(false, Color(0xFFFBC02D), muiSecondary, offWhite, Color(0xFFFFFFFF), Color(0xFFE0CE2D), offBlack),
    "dark_yellow" to createThemeObj(true, Color(0xFFFBC02D), muiSecondary, Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFFFFF59D), offWhite),
    "dark_desert" to createThemeObj(true, Color(0xFFF87060), muiSecondary, Color(0xFF102542), Color(0xFF1E1E1E), Color(0xFFDF9E96), offWhite),
    "dark_steel" to createThemeObj(true, Color(0xFF928DAB), muiSecondary, Color(0xFF1F1C2C), Color(0xFF1E1E1E), offWhite, offWhite),
    "almond" to createThemeObj(false, Color(0xFF6B574E), muiSecondary, Color(0xFFEDCFB5), offWhite, Color(0xFF57382A), offBlack),
    "dark_almond" to createThemeObj(true, Color(0xFFEDCFB5), muiSecondary, Color(0xFF2B170E), Color(0xFF52392E), offWhite, offWhite)
)

val themeKeys = THEMES_MAP.keys.toList()

@Composable
fun AnnArBoardTheme(
    appTheme: String = "light",
	darkTheme: Boolean = isSystemInDarkTheme(),
	content: @Composable () -> Unit
) {
	val colorScheme = if (appTheme == "dynamic") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) THEMES_MAP["dark"]!! else THEMES_MAP["light"]!!
        }
    } else {
        THEMES_MAP[appTheme] ?: THEMES_MAP["light"]!!
    }

	MaterialTheme(
		colorScheme = colorScheme,
		typography = Typography,
		content = content
	)
}