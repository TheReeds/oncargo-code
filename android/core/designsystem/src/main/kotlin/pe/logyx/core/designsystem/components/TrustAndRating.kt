package pe.logyx.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R

/** Tono del Trust Score: >= 85 esmeralda, >= 70 petróleo, < 70 ámbar (nunca rojo: es una señal, no un error). */
fun trustTone(score: Double): PillTone = when {
    score >= 85 -> PillTone.Emerald
    score >= 70 -> PillTone.Petrol
    else -> PillTone.Amber
}

/**
 * Trust Score (0–100, RN09) del transportista o la PYME. Compartido por LoadCard, comparación de ofertas y perfil.
 * [score] null = no se muestra nada. Con [onClick] abre el perfil público.
 */
@Composable
fun TrustScoreBadge(score: Double?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (score == null) return
    val description = stringResource(R.string.ds_trust_description, Fmt.decimal1(score))
    val m = modifier.semantics { contentDescription = description }
    LogyxBadge(
        text = stringResource(R.string.ds_trust_label, Fmt.decimal1(score)),
        modifier = if (onClick != null) m.clickable(role = Role.Button, onClick = onClick) else m,
        tone = trustTone(score),
        icon = Icons.Rounded.Shield,
    )
}

/**
 * Estrellas de 1 a 5. Con [onValueChange] son interactivas (áreas táctiles de [size], ideal 44–48 dp); sin él, solo lectura.
 */
@Composable
fun StarRating(
    value: Int,
    modifier: Modifier = Modifier,
    onValueChange: ((Int) -> Unit)? = null,
    size: Dp = 44.dp,
) {
    val description = stringResource(R.string.ds_stars_description, value)
    Row(modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { i ->
            val filled = i <= value
            val tint = if (filled) LogyxTheme.colors.warning else MaterialTheme.colorScheme.outline
            val icon = if (filled) Icons.Rounded.Star else Icons.Rounded.StarBorder
            val star = stringResource(R.string.ds_star_n, i)
            if (onValueChange != null) {
                Icon(
                    icon, star,
                    Modifier.size(size).clickable(role = Role.Button) { onValueChange(i) }.padding(size / 12),
                    tint = tint,
                )
            } else {
                Icon(icon, null, Modifier.size(size), tint = tint)
            }
        }
    }
}

/** Fila "Puntualidad ★★★★☆ 4.0" para el perfil público: promedio por dimensión. */
@Composable
fun RatingRow(label: String, average: Double?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        StarRating(Math.round(average ?: 0.0).toInt(), size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(average?.let { Fmt.decimal1(it) } ?: "—", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun TrustPreview() {
    LogyxTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrustScoreBadge(92.0); TrustScoreBadge(77.8); TrustScoreBadge(61.0)
            }
            StarRating(4, onValueChange = {})
            StarRating(3)
            RatingRow("Puntualidad", 4.5)
        }
    }
}
