package pe.logyx.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.InfoChip
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.RouteLine
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.TrustScoreBadge
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.TripPricingMode
import pe.logyx.core.model.TripView
import pe.logyx.core.model.fillRatio

/** `S/ 0.48 por kg` o `S/ 5,500.00 por viaje completo`. */
@Composable
internal fun tripRateText(trip: TripView): String = stringResource(
    if (trip.pricingMode == TripPricingMode.PER_KG) R.string.trips_rate_per_kg else R.string.trips_rate_per_trip,
    Fmt.money(trip.rate),
)

/** Barra de ocupación del viaje (confirmado / capacidad). La barra es decorativa; el texto la describe. */
@Composable
internal fun CapacityBar(trip: TripView, modifier: Modifier = Modifier) {
    val ratio = trip.fillRatio
    val description = stringResource(R.string.trips_capacity_cd, kgText(trip.confirmedWeightKg), kgText(trip.capacityKg))
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape)) {
            if (ratio > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(ratio).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.trips_capacity_line, kgText(trip.confirmedWeightKg), kgText(trip.capacityKg)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.trips_remaining, kgText(trip.remainingKg)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Tarjeta de un viaje publicado. [showCarrier] = vista de la PYME (transportista y Trust Score);
 * si no, vista del transportista (estado y reservas por confirmar). [footer] aloja la acción.
 */
@Composable
internal fun TripCard(
    trip: TripView,
    showCarrier: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    LogyxCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (showCarrier) {
                Text(
                    trip.carrierName.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                TrustScoreBadge(trip.carrierTrustScore)
            } else {
                Text(tripRateText(trip), Modifier.weight(1f).padding(end = 8.dp), style = LogyxText.moneySmall, color = LogyxTheme.colors.moneyText)
                StatusPill(trip.status.pill())
            }
        }
        Spacer(Modifier.height(14.dp))
        RouteLine(
            cityLabel(trip.originCity), cityLabel(trip.destinationCity),
            originSub = stringResource(R.string.trips_departure, Fmt.dateTime(trip.departureAt)),
            destinationSub = trip.vehiclePlate,
        )
        Spacer(Modifier.height(12.dp))
        CapacityBar(trip)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showCarrier) {
                InfoChip(tripRateText(trip), emphasized = true)
                trip.vehicleType?.let { InfoChip(it.label) }
            } else {
                val pending = trip.pendingBookings ?: 0
                if (pending > 0) InfoChip(pluralStringResource(R.plurals.trips_pending_bookings, pending, pending), emphasized = true)
                trip.vehicleType?.let { InfoChip(it.label) }
            }
        }
        if (!trip.notes.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(trip.notes.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (footer != null) {
            Spacer(Modifier.height(14.dp))
            footer()
        }
    }
}
