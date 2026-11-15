package com.rilelchaz.player3.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.rilelchaz.player3.data.AppSettings

@Composable
fun Player3Theme(s: AppSettings, content: @Composable () -> Unit) {
    val dark = when (s.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    var scheme: ColorScheme =
        if (s.dynamicColor && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            seedScheme(s.seedHue, dark)
        }
    if (dark && s.amoled) scheme = scheme.toAmoled()

    val k = s.corner
    val shapes = Shapes(
        extraSmall = RoundedCornerShape((4 * k).dp),
        small = RoundedCornerShape((8 * k).dp),
        medium = RoundedCornerShape((12 * k).dp),
        large = RoundedCornerShape((16 * k).dp),
        extraLarge = RoundedCornerShape((28 * k).dp),
    )

    val family = when (s.fontFamily) {
        1 -> FontFamily.Serif
        2 -> FontFamily.Monospace
        3 -> FontFamily.Cursive
        else -> FontFamily.Default
    }
    val typography = remember(family) { typographyWith(family) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * s.fontScale)) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography, content = content)
    }
}

private fun typographyWith(f: FontFamily): Typography {
    val d = Typography()
    return d.copy(
        displayLarge = d.displayLarge.copy(fontFamily = f),
        displayMedium = d.displayMedium.copy(fontFamily = f),
        displaySmall = d.displaySmall.copy(fontFamily = f),
        headlineLarge = d.headlineLarge.copy(fontFamily = f),
        headlineMedium = d.headlineMedium.copy(fontFamily = f),
        headlineSmall = d.headlineSmall.copy(fontFamily = f),
        titleLarge = d.titleLarge.copy(fontFamily = f),
        titleMedium = d.titleMedium.copy(fontFamily = f),
        titleSmall = d.titleSmall.copy(fontFamily = f),
        bodyLarge = d.bodyLarge.copy(fontFamily = f),
        bodyMedium = d.bodyMedium.copy(fontFamily = f),
        bodySmall = d.bodySmall.copy(fontFamily = f),
        labelLarge = d.labelLarge.copy(fontFamily = f),
        labelMedium = d.labelMedium.copy(fontFamily = f),
        labelSmall = d.labelSmall.copy(fontFamily = f),
    )
}

private fun c(h: Float, s: Float, l: Float): Color =
    Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
)

/** Палитра из одного оттенка (для телефонов без Material You или когда динамика выключена). */
fun seedScheme(h: Float, dark: Boolean): ColorScheme =
    if (!dark) lightColorScheme(
        primary = c(h, .55f, .40f),
        onPrimary = Color.White,
        primaryContainer = c(h, .90f, .90f),
        onPrimaryContainer = c(h, .80f, .14f),
        inversePrimary = c(h, .90f, .80f),
        secondary = c(h + 10, .22f, .40f),
        onSecondary = Color.White,
        secondaryContainer = c(h + 10, .45f, .89f),
        onSecondaryContainer = c(h + 10, .30f, .13f),
        tertiary = c(h + 65, .35f, .42f),
        onTertiary = Color.White,
        tertiaryContainer = c(h + 65, .65f, .90f),
        onTertiaryContainer = c(h + 65, .60f, .14f),
        background = c(h, .30f, .98f),
        onBackground = c(h, .12f, .11f),
        surface = c(h, .30f, .98f),
        onSurface = c(h, .12f, .11f),
        surfaceVariant = c(h, .25f, .90f),
        onSurfaceVariant = c(h, .12f, .30f),
        inverseSurface = c(h, .10f, .20f),
        inverseOnSurface = c(h, .20f, .95f),
        outline = c(h, .08f, .46f),
        outlineVariant = c(h, .18f, .80f),
        surfaceDim = c(h, .20f, .87f),
        surfaceBright = c(h, .30f, .98f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = c(h, .30f, .96f),
        surfaceContainer = c(h, .28f, .94f),
        surfaceContainerHigh = c(h, .25f, .92f),
        surfaceContainerHighest = c(h, .22f, .90f),
    ) else darkColorScheme(
        primary = c(h, .90f, .80f),
        onPrimary = c(h, .70f, .20f),
        primaryContainer = c(h, .45f, .30f),
        onPrimaryContainer = c(h, .90f, .90f),
        inversePrimary = c(h, .55f, .40f),
        secondary = c(h + 10, .40f, .80f),
        onSecondary = c(h + 10, .30f, .20f),
        secondaryContainer = c(h + 10, .22f, .30f),
        onSecondaryContainer = c(h + 10, .45f, .90f),
        tertiary = c(h + 65, .60f, .80f),
        onTertiary = c(h + 65, .50f, .20f),
        tertiaryContainer = c(h + 65, .30f, .30f),
        onTertiaryContainer = c(h + 65, .65f, .90f),
        background = c(h, .12f, .08f),
        onBackground = c(h, .12f, .90f),
        surface = c(h, .12f, .08f),
        onSurface = c(h, .12f, .90f),
        surfaceVariant = c(h, .10f, .28f),
        onSurfaceVariant = c(h, .15f, .80f),
        inverseSurface = c(h, .12f, .90f),
        inverseOnSurface = c(h, .10f, .20f),
        outline = c(h, .08f, .60f),
        outlineVariant = c(h, .10f, .28f),
        surfaceDim = c(h, .12f, .06f),
        surfaceBright = c(h, .10f, .24f),
        surfaceContainerLowest = c(h, .12f, .05f),
        surfaceContainerLow = c(h, .12f, .10f),
        surfaceContainer = c(h, .12f, .12f),
        surfaceContainerHigh = c(h, .11f, .16f),
        surfaceContainerHighest = c(h, .10f, .20f),
    )
