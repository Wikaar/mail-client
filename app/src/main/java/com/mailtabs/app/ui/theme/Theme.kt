package com.mailtabs.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LocalExtendedColors = staticCompositionLocalOf { DarkExtended }

/** Доступ к дополнительным цветам и размерам темы: `Wikar.colors.star`, `Wikar.dimens.gap`. */
object Wikar {
    val colors: ExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current
    val dimens = Dimens
}

/** Отступы и размеры (сетка 4dp). */
object Dimens {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val avatar = 42.dp
    val cardRadius = 16.dp
    val inputRadius = 14.dp
    val dialogRadius = 24.dp
    /** Максимальная ширина контента на планшетах — строки писем не растягиваются на весь экран. */
    val maxContentWidth = 720.dp
}

val CardShape = RoundedCornerShape(Dimens.cardRadius)
val InputShape = RoundedCornerShape(Dimens.inputRadius)
val PillShape = RoundedCornerShape(50)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(Dimens.dialogRadius),
)

private val AppTypography: Typography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelMedium = labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}

/**
 * Тема берётся из uiMode активити: выбор «Светлая/Тёмная/Как в системе» применяется через
 * AppCompatDelegate.setDefaultNightMode, поэтому isSystemInDarkTheme() отражает настройку приложения.
 * Dynamic color (Material You) намеренно не используется: у приложения своя палитра Midnight Violet.
 */
@Composable
fun MailTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtendedColors provides if (dark) DarkExtended else LightExtended) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
