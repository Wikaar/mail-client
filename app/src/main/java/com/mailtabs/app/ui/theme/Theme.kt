package com.mailtabs.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E5AA8),
    secondary = Color(0xFF4F6070),
    tertiary = Color(0xFF7A5590),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    secondary = Color(0xFFB7C8DA),
    tertiary = Color(0xFFE3BBF5),
)

/**
 * Тема берётся из uiMode активити: выбор «Светлая/Тёмная/Как в системе» применяется через
 * AppCompatDelegate.setDefaultNightMode, поэтому isSystemInDarkTheme() отражает настройку приложения.
 */
@Composable
fun MailTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
