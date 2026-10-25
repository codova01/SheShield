package com.example.sheshield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// SheShield palette
val SheCream = Color(0xFFFFF5F0)
val SheBrown = Color(0xFF3B2016)
val SheBrownSoft = Color(0xFF7A4F3F)
val SheAccent = Color(0xFFB5591F)
val SheRed = Color(0xFFC62828)
val SheRedHalo = Color(0xFFF2C6C6)
val ShePeach = Color(0xFFFFE3D3)
val SheMint = Color(0xFFDCEEDF)
val SheGreenDark = Color(0xFF1F3D2B)
val SheGreenSoft = Color(0xFF4F7A60)
val SheBorder = Color(0xFFF0DCD2)
val SheAmber = Color(0xFFD9822B)
val SheAmberHalo = Color(0xFFF6D9B8)
val SheGreen = Color(0xFF2E7D4F)
val SheGreenHalo = Color(0xFFCFE6D6)

private val SheColorScheme = lightColorScheme(
    primary = SheAccent,
    onPrimary = Color.White,
    primaryContainer = ShePeach,
    onPrimaryContainer = SheBrown,
    secondary = SheGreen,
    onSecondary = Color.White,
    secondaryContainer = SheMint,
    onSecondaryContainer = SheGreenDark,
    tertiary = SheBrownSoft,
    background = SheCream,
    onBackground = SheBrown,
    surface = SheCream,
    onSurface = SheBrown,
    surfaceVariant = Color.White,
    onSurfaceVariant = SheBrownSoft,
    outline = Color(0xFFC9A99A),
    outlineVariant = SheBorder,
    error = SheRed,
    onError = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White
)

private val SheTypography = androidx.compose.material3.Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    )
)

@Composable
fun SheShieldTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // The design is light only, so both parameters are ignored on purpose.
    MaterialTheme(
        colorScheme = SheColorScheme,
        typography = SheTypography,
        content = content
    )
}