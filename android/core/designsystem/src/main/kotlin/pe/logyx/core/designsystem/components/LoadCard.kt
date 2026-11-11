package pe.logyx.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.res.stringResource
import pe.logyx.core.designsystem.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.label
import pe.logyx.core.model.AuctionStatus
import pe.logyx.core.model.AuctionView
import pe.logyx.core.model.RequestView

/** Cuenta regresiva de subasta: neutra, ámbar si faltan menos de 5 min, y "Cerrada" al vencer. */
@Composable
fun AuctionCountdown(auction: AuctionView?, modifier: Modifier = Modifier) {
    if (auction == null) return
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(1_000); value = System.currentTimeMillis() }
    }
    val instant = java.time.Instant.ofEpochMilli(now)
    val remaining = Fmt.remaining(auction.closesAt, instant)
    if (auction.status != AuctionStatus.OPEN || remaining == null) {
        StatusPill(stringResource(R.string.ds_auction_closed), PillTone.Neutral, modifier)
        return
    }
    val urgent = (Fmt.remainingSeconds(auction.closesAt, instant) ?: Long.MAX_VALUE) < 300
    StatusPill(stringResource(R.string.ds_closes_in, remaining), if (urgent) PillTone.Amber else PillTone.Petrol, modifier)
}

/**
 * Tarjeta del marketplace (móvil): precio protagonista, ruta, chips y CTA.
 * @param compatible si la flota del transportista puede llevar la carga.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoadCard(
    request: RequestView,
    compatible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LogyxCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            PriceTag(request.suggestedPrice, label = stringResource(R.string.ds_reference_price))
            AuctionCountdown(request.auction)
        }
        Spacer(Modifier.height(14.dp))
        RouteLine(
            origin = cityLabel(request.originCity),
            destination = cityLabel(request.destinationCity),
            distance = request.distanceKm?.let { Fmt.km(it) },
            originSub = stringResource(R.string.ds_pickup_on, Fmt.date(request.pickupDate)),
            destinationSub = null,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoChip(request.cargoType.label)
            InfoChip(Fmt.tons(request.weightKg))
            request.minVehicleType?.let { InfoChip(stringResource(R.string.ds_min_vehicle, it.label)) }
            if (request.requesterRucVerified) VerifiedBadge()
            TrustScoreBadge(request.requesterTrustScore)
            if (compatible) InfoChip(stringResource(R.string.ds_compatible), emphasized = true, icon = Icons.Rounded.CheckCircle)
            request.auction?.let { InfoChip(stringResource(R.string.ds_bids_count, it.bidCount, it.maxBids), icon = Icons.Rounded.Timer) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(request.requesterName ?: stringResource(R.string.ds_company), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(request.cargoDescription ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.size(8.dp))
            LogyxButton(
                text = stringResource(if (request.myBid != null) R.string.ds_view_my_bid else R.string.ds_view_load_and_bid),
                onClick = onClick,
                variant = ButtonVariant.Money,
                size = ButtonSize.Medium,
                fullWidth = false,
            )
        }
    }
}
