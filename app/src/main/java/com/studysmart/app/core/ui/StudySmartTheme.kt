package com.studysmart.app.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val StudySmartLightColors = lightColorScheme(
    primary = Color(0xFF5B52F2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFEDFF),
    onPrimaryContainer = Color(0xFF312B68),
    background = Color(0xFFFAFAFE),
    onBackground = Color(0xFF17182F),
    surface = Color.White,
    onSurface = Color(0xFF17182F),
    surfaceVariant = Color(0xFFF5F6FA),
    onSurfaceVariant = Color(0xFF6B6D7E),
    outline = Color(0xFFE8E9F0),
    outlineVariant = Color(0xFFE8E9F0),
    error = Color(0xFFB52A3A),
    errorContainer = Color(0xFFFDECEF),
    onErrorContainer = Color(0xFF711522),
)

private val StudySmartDarkColors = darkColorScheme(
    primary = Color(0xFFAFA8FF),
    onPrimary = Color(0xFF201E50),
    primaryContainer = Color(0xFF302D63),
    onPrimaryContainer = Color(0xFFE3E1FF),
    background = Color(0xFF11121B),
    onBackground = Color(0xFFF1F0F8),
    surface = Color(0xFF1A1B27),
    onSurface = Color(0xFFF1F0F8),
    surfaceVariant = Color(0xFF222332),
    onSurfaceVariant = Color(0xFFB9B8C8),
    outline = Color(0xFF36374A),
    outlineVariant = Color(0xFF36374A),
    error = Color(0xFFFF9AA6),
    errorContainer = Color(0xFF4C2029),
    onErrorContainer = Color(0xFFFFDADF),
)

data class StudySmartSemanticColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val track: Color,
    val surfaceSelected: Color,
    val textTertiary: Color,
)

private val StudySmartLightSemanticColors = StudySmartSemanticColors(
    success = Color(0xFF0B6B38),
    successContainer = Color(0xFFE8F6EE),
    warning = Color(0xFF8A5A00),
    warningContainer = Color(0xFFFFF5DE),
    info = Color(0xFF2C57A4),
    infoContainer = Color(0xFFEAF1FF),
    track = Color(0xFFEFEDFF),
    surfaceSelected = Color(0xFFE9E7FF),
    textTertiary = Color(0xFF9A9CAE),
)

private val StudySmartDarkSemanticColors = StudySmartSemanticColors(
    success = Color(0xFF6DDB9A),
    successContainer = Color(0xFF153C2A),
    warning = Color(0xFFF7C96D),
    warningContainer = Color(0xFF4A3512),
    info = Color(0xFF9AB9FF),
    infoContainer = Color(0xFF1D3155),
    track = Color(0xFF343548),
    surfaceSelected = Color(0xFF2A2940),
    textTertiary = Color(0xFF85869A),
)

val LocalStudySmartSemanticColors = staticCompositionLocalOf {
    StudySmartLightSemanticColors
}

private val StudySmartTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 19.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp,
    ),
)

@Composable
fun StudySmartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) StudySmartDarkColors else StudySmartLightColors,
        typography = StudySmartTypography,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalStudySmartSemanticColors provides if (darkTheme) {
                StudySmartDarkSemanticColors
            } else {
                StudySmartLightSemanticColors
            },
            content = content,
        )
    }
}
