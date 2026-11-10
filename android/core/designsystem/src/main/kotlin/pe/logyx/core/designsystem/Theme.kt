package pe.logyx.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Tokens de docs/design/DESIGN.md. */
object LogyxPalette {
    val Ink900 = Color(0xFF0E1726)
    val Ink700 = Color(0xFF334155)
    val Ink400 = Color(0xFF94A3B8)
    val Steel100 = Color(0xFFEEF2F6)
    val Steel200 = Color(0xFFE2E8F0)
    val Surface = Color(0xFFFFFFFF)
    val Petrol700 = Color(0xFF0B4F6C)
    val Petrol50 = Color(0xFFE6F1F5)
    val Petrol400 = Color(0xFF38A3C9)
    val Emerald500 = Color(0xFF10B981)
    val Emerald700 = Color(0xFF047857)
    val Emerald50 = Color(0xFFECFDF5)
    val Amber500 = Color(0xFFF59E0B)
    val Amber700 = Color(0xFFB45309)
    val Amber50 = Color(0xFFFFFBEB)
    val Red600 = Color(0xFFDC2626)
    val Red50 = Color(0xFFFEF2F2)
    val DarkBg = Color(0xFF0B1220)
    val DarkSurface = Color(0xFF111A2B)
}

/** Colores semánticos que Material 3 no cubre: dinero/oportunidad y advertencia. */
@Immutable
data class LogyxColors(
    val money: Color,
    /** Esmeralda con contraste AA para botones y cifras sobre fondos claros/oscuros. */
    val moneyStrong: Color,
    /** Esmeralda legible como texto (precios). */
    val moneyText: Color,
    val onMoneyContainer: Color,
    val moneyContainer: Color,
    val warning: Color,
    val onWarningContainer: Color,
    val warningContainer: Color,
    val route: Color,
    val dangerContainer: Color,
)

private val LightExtra = LogyxColors(
    money = LogyxPalette.Emerald500,
    moneyStrong = LogyxPalette.Emerald700,
    moneyText = LogyxPalette.Emerald700,
    onMoneyContainer = LogyxPalette.Emerald700,
    moneyContainer = LogyxPalette.Emerald50,
    warning = LogyxPalette.Amber500,
    onWarningContainer = LogyxPalette.Amber700,
    warningContainer = LogyxPalette.Amber50,
    route = LogyxPalette.Petrol700,
    dangerContainer = LogyxPalette.Red50,
)

private val DarkExtra = LightExtra.copy(
    moneyText = Color(0xFF34D399),
    onMoneyContainer = Color(0xFF6EE7B7),
    moneyContainer = Color(0xFF0B2A22),
    onWarningContainer = Color(0xFFFCD34D),
    warningContainer = Color(0xFF2E2108),
    route = LogyxPalette.Petrol400,
    dangerContainer = Color(0xFF3B1414),
)

val LocalLogyxColors = staticCompositionLocalOf { LightExtra }

private val LightScheme = lightColorScheme(
    primary = LogyxPalette.Petrol700,
    onPrimary = Color.White,
    primaryContainer = LogyxPalette.Petrol50,
    onPrimaryContainer = LogyxPalette.Petrol700,
    secondary = LogyxPalette.Emerald500,
    onSecondary = Color.White,
    secondaryContainer = LogyxPalette.Emerald50,
    onSecondaryContainer = LogyxPalette.Emerald700,
    tertiary = LogyxPalette.Amber500,
    onTertiary = LogyxPalette.Ink900,
    tertiaryContainer = LogyxPalette.Amber50,
    onTertiaryContainer = LogyxPalette.Amber700,
    error = LogyxPalette.Red600,
    onError = Color.White,
    errorContainer = LogyxPalette.Red50,
    onErrorContainer = LogyxPalette.Red600,
    background = LogyxPalette.Steel100,
    onBackground = LogyxPalette.Ink900,
    surface = LogyxPalette.Surface,
    onSurface = LogyxPalette.Ink900,
    surfaceVariant = LogyxPalette.Steel200,
    onSurfaceVariant = LogyxPalette.Ink700,
    outline = LogyxPalette.Ink400,
    outlineVariant = LogyxPalette.Steel200,
    surfaceContainerLowest = LogyxPalette.Surface,
    surfaceContainerLow = LogyxPalette.Surface,
    surfaceContainer = LogyxPalette.Surface,
    surfaceContainerHigh = LogyxPalette.Steel100,
    surfaceContainerHighest = LogyxPalette.Steel200,
)

private val DarkScheme = darkColorScheme(
    primary = LogyxPalette.Petrol400,
    onPrimary = LogyxPalette.DarkBg,
    primaryContainer = Color(0xFF0F3A4F),
    onPrimaryContainer = Color(0xFFBFE4F2),
    secondary = LogyxPalette.Emerald500,
    onSecondary = LogyxPalette.DarkBg,
    secondaryContainer = Color(0xFF0B2A22),
    onSecondaryContainer = Color(0xFF6EE7B7),
    tertiary = LogyxPalette.Amber500,
    onTertiary = LogyxPalette.DarkBg,
    error = Color(0xFFF87171),
    onError = LogyxPalette.DarkBg,
    errorContainer = Color(0xFF3B1414),
    onErrorContainer = Color(0xFFFCA5A5),
    background = LogyxPalette.DarkBg,
    onBackground = Color(0xFFE6EDF6),
    surface = LogyxPalette.DarkSurface,
    onSurface = Color(0xFFE6EDF6),
    surfaceVariant = Color(0xFF1B2638),
    onSurfaceVariant = Color(0xFFB4C0D0),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF243247),
    surfaceContainerLowest = LogyxPalette.DarkBg,
    surfaceContainerLow = LogyxPalette.DarkSurface,
    surfaceContainer = LogyxPalette.DarkSurface,
    surfaceContainerHigh = Color(0xFF1B2638),
    surfaceContainerHighest = Color(0xFF243247),
)

private val LogyxShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

object LogyxTheme {
    val colors: LogyxColors
        @Composable @ReadOnlyComposable get() = LocalLogyxColors.current
}

@Composable
fun LogyxTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLogyxColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = LogyxTypography,
            shapes = LogyxShapes,
            content = content,
        )
    }
}
