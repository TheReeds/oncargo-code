package pe.logyx.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Geist Sans / Geist Mono (SIL OFL), fuentes variables empaquetadas en res/font. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variableFamily(resId: Int) = FontFamily(
    listOf(
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
    ).map { w ->
        Font(resId, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    },
)

val GeistSans = variableFamily(R.font.geist_variable)
val GeistMono = variableFamily(R.font.geist_mono_variable)

private fun base() = Typography()

val LogyxTypography: Typography = base().let { t ->
    fun TextStyle.sans() = copy(fontFamily = GeistSans)
    Typography(
        displayLarge = t.displayLarge.sans(),
        displayMedium = t.displayMedium.sans(),
        displaySmall = t.displaySmall.sans(),
        headlineLarge = t.headlineLarge.sans().copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.sans().copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.sans().copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.sans().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.sans().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.sans().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.sans(),
        bodyMedium = t.bodyMedium.sans(),
        bodySmall = t.bodySmall.sans(),
        labelLarge = t.labelLarge.sans().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.sans().copy(fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.sans().copy(fontWeight = FontWeight.Medium),
    )
}

/** Cifras técnicas (precios, placas, km, toneladas): Geist Mono con números tabulares. */
object LogyxText {
    val moneyLarge = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, fontFeatureSettings = "tnum")
    val moneyMedium = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, fontFeatureSettings = "tnum")
    val moneySmall = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum")
    val mono = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")
    val kpi = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, fontFeatureSettings = "tnum")
}
