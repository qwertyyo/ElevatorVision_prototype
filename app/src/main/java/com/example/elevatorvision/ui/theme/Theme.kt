package com.example.elevatorvision.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ElevatorVisionDarkColorScheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandOrange,
    onSecondary = Color.White,
    tertiary = BrandGreen,
    onTertiary = Color.White,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = OutlineDark,
    error = DangerRed,
    onError = Color.White
)

/**
 * ElevatorVision은 다크 테마 전용 앱입니다.
 * 승강기 통로 등 저조도 현장 환경을 고려해, 시스템 설정과 무관하게 항상 다크 테마를 사용합니다.
 */
@Composable
fun ElevatorVisionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ElevatorVisionDarkColorScheme,
        typography = Typography,
        content = content
    )
}