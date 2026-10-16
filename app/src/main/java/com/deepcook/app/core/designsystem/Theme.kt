package com.deepcook.app.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepcook.app.core.model.ColorSource
import com.deepcook.app.core.model.ThemeMode
import com.deepcook.app.core.model.UserPreferences

private val LightKitchen = lightColorScheme(
    primary = Color(0xFF805600), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0A3), onPrimaryContainer = Color(0xFF2A1800),
    secondary = Color(0xFF735B35), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5DFBB), onSecondaryContainer = Color(0xFF291B07),
    tertiary = Color(0xFF995139), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCF), onTertiaryContainer = Color(0xFF3C0A00),
    background = Color(0xFFFFFBF4), onBackground = Color(0xFF241B10),
    surface = Color(0xFFFFFBF4), onSurface = Color(0xFF241B10),
    surfaceVariant = Color(0xFFF0E2CC), onSurfaceVariant = Color(0xFF665B49),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFCF5E9),
    surfaceContainer = Color(0xFFF7EEDF), surfaceContainerHigh = Color(0xFFF1E7D7),
    surfaceContainerHighest = Color(0xFFEBE1D1), surfaceDim = Color(0xFFE5DACC),
    surfaceBright = Color(0xFFFFFBF4), surfaceTint = Color(0xFF805600),
    outline = Color(0xFF817560), outlineVariant = Color(0xFFD6C9B4),
    inverseSurface = Color(0xFF372F24), inverseOnSurface = Color(0xFFFBF0DF),
    inversePrimary = Color(0xFFF4C35D),
)

private val DarkKitchen = darkColorScheme(
    primary = Color(0xFFF4C35D), onPrimary = Color(0xFF432C00),
    primaryContainer = Color(0xFF604200), onPrimaryContainer = Color(0xFFFFE0A3),
    secondary = Color(0xFFDEC39B), onSecondary = Color(0xFF402D12),
    secondaryContainer = Color(0xFF594326), onSecondaryContainer = Color(0xFFF5DFBB),
    tertiary = Color(0xFFFFB59C), onTertiary = Color(0xFF5B200D),
    tertiaryContainer = Color(0xFF793723), onTertiaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF18140D), onBackground = Color(0xFFEDE1CF),
    surface = Color(0xFF18140D), onSurface = Color(0xFFEDE1CF),
    surfaceVariant = Color(0xFF4D4435), onSurfaceVariant = Color(0xFFD0C5B3),
    surfaceContainerLowest = Color(0xFF120F09), surfaceContainerLow = Color(0xFF211C14),
    surfaceContainer = Color(0xFF261F17), surfaceContainerHigh = Color(0xFF30291F),
    surfaceContainerHighest = Color(0xFF3B3329), surfaceDim = Color(0xFF18140D),
    surfaceBright = Color(0xFF40382D), surfaceTint = Color(0xFFF4C35D),
    outline = Color(0xFF9B8E7B), outlineVariant = Color(0xFF4D4435),
    inverseSurface = Color(0xFFEDE1CF), inverseOnSurface = Color(0xFF372F24),
    inversePrimary = Color(0xFF805600),
)

@Composable
fun DeepCookTheme(preferences: UserPreferences = UserPreferences(), content: @Composable () -> Unit) {
    val dark = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (preferences.colorSource == ColorSource.DYNAMIC && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) {
        DarkKitchen
    } else {
        LightKitchen
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
            headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
