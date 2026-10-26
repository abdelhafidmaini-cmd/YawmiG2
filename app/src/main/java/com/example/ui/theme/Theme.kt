package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YawmiDarkColorScheme = darkColorScheme(
    primary = YawmiElectricCyan,
    onPrimary = YawmiNavyDeep,
    primaryContainer = YawmiNavyElevated,
    onPrimaryContainer = Color(0xFFF8FAFC),
    secondary = YawmiWarmGold,
    onSecondary = YawmiNavyDeep,
    tertiary = YawmiEmerald,
    background = YawmiNavyDeep,
    onBackground = Color(0xFFF8FAFC),
    surface = YawmiNavySurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = YawmiNavyMuted,
    onSurfaceVariant = Color(0xFF94A3B8),
    error = YawmiCoralDanger
)

private val YawmiLightColorScheme = lightColorScheme(
    primary = YawmiLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = YawmiLightGold,
    onSecondary = Color.White,
    tertiary = YawmiEmerald,
    background = YawmiLightBg,
    onBackground = Color(0xFF0F172A),
    surface = YawmiLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFEF4444)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) YawmiDarkColorScheme else YawmiLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
