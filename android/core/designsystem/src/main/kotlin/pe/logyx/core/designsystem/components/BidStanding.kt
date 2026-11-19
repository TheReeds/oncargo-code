package pe.logyx.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R
import pe.logyx.core.designsystem.label
import pe.logyx.core.model.BidPosition
import pe.logyx.core.model.BidStandingBand
import pe.logyx.core.model.MyBidStanding
import pe.logyx.core.model.visiblePosition

/**
 * Mi posición en la subasta (V15, F15/F51): "Tu oferta es la mejor", "A menos de 5 % de la mejor", "A menos de 15 % de la mejor"
 * o "Más de 15 % sobre la mejor", más "Vas 2.º de 5". Las ofertas son selladas (RN04): **nunca** muestra montos ajenos.
 * Con `vsBest` nulo (adjudicada o cancelada) solo dice la posición final, sin insinuar el precio ganador.
 */
@Composable
fun BidStandingRow(standing: MyBidStanding, modifier: Modifier = Modifier) {
    val band = standing.vsBest
    val tone = when (band ?: if (standing.isBest) BidStandingBand.BEST else null) {
        BidStandingBand.BEST -> Triple(LogyxTheme.colors.moneyContainer, LogyxTheme.colors.onMoneyContainer, Icons.Rounded.EmojiEvents)
        BidStandingBand.WITHIN_5_PCT -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Icons.Rounded.TrendingUp)
        BidStandingBand.WITHIN_15_PCT -> Triple(LogyxTheme.colors.warningContainer, LogyxTheme.colors.onWarningContainer, Icons.Rounded.TrendingUp)
        BidStandingBand.ABOVE_15_PCT -> Triple(LogyxTheme.colors.dangerContainer, LogyxTheme.colors.dangerText, Icons.Rounded.TrendingUp)
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.Leaderboard)
    }
    val headline = when {
        band != null && band != BidStandingBand.UNKNOWN -> band.label
        standing.isBest && !standing.finalStanding -> BidStandingBand.BEST.label
        else -> null
    }
    val rankText = if (standing.finalStanding) stringResource(R.string.ds_standing_final_rank, standing.rank, standing.bidCount)
    else stringResource(R.string.ds_standing_rank, standing.rank, standing.bidCount)
    // Posición aproximada (V22, 2.8): no se repite cuando ya eres la mejor (la banda lo dice).
    val positionText = standing.visiblePosition?.label
    val describe = stringResource(R.string.ds_standing_description, listOfNotNull(headline, rankText, positionText).joinToString(". "))
    Row(
        modifier.fillMaxWidth().background(tone.first, RoundedCornerShape(12.dp)).padding(12.dp).semantics(mergeDescendants = true) { contentDescription = describe },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(tone.third, null, Modifier.size(24.dp), tint = tone.second)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (headline != null) Text(headline, style = MaterialTheme.typography.titleSmall, color = tone.second)
            Text(rankText, style = MaterialTheme.typography.bodyMedium, color = tone.second)
            if (positionText != null) Text(positionText, style = MaterialTheme.typography.bodyMedium, color = tone.second)
            if (!standing.finalStanding) {
                Text(stringResource(R.string.ds_standing_can_change), style = MaterialTheme.typography.labelMedium, color = tone.second)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BidStandingPreview() {
    LogyxTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BidStandingRow(MyBidStanding(1, 4, true, BidStandingBand.BEST))
            BidStandingRow(MyBidStanding(2, 4, false, BidStandingBand.WITHIN_5_PCT))
            BidStandingRow(MyBidStanding(3, 4, false, BidStandingBand.WITHIN_15_PCT))
            BidStandingRow(MyBidStanding(4, 4, false, BidStandingBand.ABOVE_15_PCT, finalStanding = true))
            BidStandingRow(MyBidStanding(2, 3, false, null, finalStanding = true))
            BidStandingRow(MyBidStanding(3, 5, false, BidStandingBand.WITHIN_15_PCT, position = BidPosition.UPPER_HALF))
        }
    }
}
