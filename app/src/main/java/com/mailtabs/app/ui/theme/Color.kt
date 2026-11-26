package com.mailtabs.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Midnight Violet — палитра WikarM@il.
 * Фиолетовый — только для акцентов (действия, выбор, индикаторы), поверхности нейтральные.
 */

// ---------- Тёмная тема ----------
private val DarkBackground = Color(0xFF101019)
private val DarkSurfaceLow = Color(0xFF15151F)
private val DarkSurface = Color(0xFF191925)
private val DarkSurfaceElevated = Color(0xFF222234)
private val DarkSurfaceVariant = Color(0xFF29283B)
private val DarkOnBackground = Color(0xFFF5F3FF)
private val DarkOnSurfaceVariant = Color(0xFF9995AA)
private val DarkOutline = Color(0xFF4A4760)
private val DarkDivider = Color(0xFF303044)

val DarkColors = darkColorScheme(
    // Светлый оттенок фиолетового — читаемый текст/иконки на тёмном фоне.
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF1E0F45),
    // Фирменный #8B5CF6 — заливки (FAB, кнопки, выбранный день).
    primaryContainer = Color(0xFF8B5CF6),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF1B1030),
    secondaryContainer = Color(0xFF2E2552),
    onSecondaryContainer = Color(0xFFEAE3FF),
    tertiary = Color(0xFFC084FC),
    onTertiary = Color(0xFF2A0A3D),
    tertiaryContainer = Color(0xFF3D2456),
    onTertiaryContainer = Color(0xFFF3E3FF),
    error = Color(0xFFF87171),
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF4A1C22),
    onErrorContainer = Color(0xFFFFD9DC),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkBackground,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = Color(0xFF8B5CF6),
    inverseSurface = Color(0xFFE9E5F5),
    inverseOnSurface = Color(0xFF1E1B2B),
    inversePrimary = Color(0xFF7541D8),
    outline = DarkOutline,
    outlineVariant = DarkDivider,
    scrim = Color(0xFF000000),
    surfaceBright = DarkSurfaceVariant,
    surfaceDim = DarkBackground,
    surfaceContainerLowest = Color(0xFF0B0B12),
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerHighest = DarkSurfaceVariant,
)

// ---------- Светлая тема ----------
val LightColors = lightColorScheme(
    primary = Color(0xFF7541D8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF7541D8),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF7656C7),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E0FF),
    onSecondaryContainer = Color(0xFF26135C),
    tertiary = Color(0xFF9445C2),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5E4FF),
    onTertiaryContainer = Color(0xFF34104D),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F6FC),
    onBackground = Color(0xFF211B2D),
    surface = Color(0xFFF7F6FC),
    onSurface = Color(0xFF211B2D),
    surfaceVariant = Color(0xFFF0EDF8),
    onSurfaceVariant = Color(0xFF6F687C),
    surfaceTint = Color(0xFF7541D8),
    inverseSurface = Color(0xFF2E2A3A),
    inverseOnSurface = Color(0xFFF4F0FB),
    inversePrimary = Color(0xFFC9B6FF),
    outline = Color(0xFFB9B2C9),
    outlineVariant = Color(0xFFE9E5F0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE5E1EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFAFE),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0EDF8),
    surfaceContainerHighest = Color(0xFFE9E5F2),
)

/** Цвета, которых нет в Material ColorScheme. */
@Immutable
data class ExtendedColors(
    val success: Color,
    val warning: Color,
    val star: Color,
    val divider: Color,
    val swipeDelete: Color,
    val onSwipeDelete: Color,
    /** Палитра аватаров отправителей (белая буква поверх). */
    val avatars: List<Color>,
    /** Цветные метки заметок календаря. */
    val noteTags: List<Color>,
)

private val AvatarPalette = listOf(
    Color(0xFF7C3AED), Color(0xFF2563EB), Color(0xFF0D9488), Color(0xFFDB2777),
    Color(0xFFEA580C), Color(0xFF4F46E5), Color(0xFF059669), Color(0xFF9333EA),
)

val DarkExtended = ExtendedColors(
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    star = Color(0xFFFBBF24),
    divider = DarkDivider,
    swipeDelete = Color(0xFFDC2626),
    onSwipeDelete = Color.White,
    avatars = AvatarPalette,
    noteTags = listOf(
        Color(0xFFA78BFA), Color(0xFF60A5FA), Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFF472B6),
    ),
)

val LightExtended = ExtendedColors(
    success = Color(0xFF16845B),
    warning = Color(0xFF9B6900),
    star = Color(0xFFD99A06),
    divider = Color(0xFFE9E5F0),
    swipeDelete = Color(0xFFC62828),
    onSwipeDelete = Color.White,
    avatars = AvatarPalette,
    noteTags = listOf(
        Color(0xFF7541D8), Color(0xFF2563EB), Color(0xFF16845B), Color(0xFFB7791F), Color(0xFFDB2777),
    ),
)
