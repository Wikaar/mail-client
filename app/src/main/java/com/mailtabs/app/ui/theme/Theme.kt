package com.mailtabs.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
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

// Тёмная тема — этап 2.
@Composable
fun MailTheme(content: @Composable () -> Unit) {
    val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(LocalContext.current) else LightColors
    MaterialTheme(colorScheme = scheme, content = content)
}
