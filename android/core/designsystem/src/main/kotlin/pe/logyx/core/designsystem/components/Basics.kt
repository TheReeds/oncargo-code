package pe.logyx.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.res.stringResource
import pe.logyx.core.designsystem.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.model.VehicleType

enum class PillTone { Petrol, Emerald, Amber, Red, Neutral }

/** Estado compacto: Publicada / Adjudicada / En tránsito / Entregado... */
@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val c = LogyxTheme.colors
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        PillTone.Petrol -> scheme.primaryContainer to scheme.onPrimaryContainer
        PillTone.Emerald -> c.moneyContainer to c.onMoneyContainer
        PillTone.Amber -> c.warningContainer to c.onWarningContainer
        PillTone.Red -> c.dangerContainer to c.dangerText
        PillTone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Row(
        modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(6.dp).background(fg, CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/** Chip informativo (no interactivo): `Carga general`, `3.2 t`, `Compatible`. */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false, icon: ImageVector? = null) {
    val c = LogyxTheme.colors
    val bg = if (emphasized) c.moneyContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (emphasized) c.onMoneyContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = fg)
            Spacer(Modifier.size(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/** Precio en Geist Mono, verde esmeralda (dinero/oportunidad) con etiqueta opcional. */
@Composable
fun PriceTag(
    amount: Double?,
    modifier: Modifier = Modifier,
    label: String? = null,
    large: Boolean = true,
    color: Color = LogyxTheme.colors.moneyText,
) {
    Column(modifier) {
        Text(
            Fmt.money(amount),
            style = if (large) LogyxText.moneyLarge else LogyxText.moneyMedium,
            color = color,
        )
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Número grande + etiqueta + contexto. */
@Composable
fun KpiTile(value: String, label: String, modifier: Modifier = Modifier, context: String? = null, accent: Color = MaterialTheme.colorScheme.primary) {
    Card(
        modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = LogyxText.kpi, color = accent)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            if (context != null) {
                Text(context, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String = stringResource(R.string.ds_loading)) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Estado vacío con camioncito ilustrado y acción siguiente. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    vehicle: VehicleType = VehicleType.TRUCK_MEDIUM,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TruckIllustration(vehicle, Modifier.size(width = 160.dp, height = 90.dp), muted = true)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            LogyxButton(actionLabel, onAction, variant = ButtonVariant.Secondary, size = ButtonSize.Medium, fullWidth = false)
        }
    }
}

/** Error de carga: camión averiado con aviso rojo, qué pasó ([message]) y botón para reintentar. */
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.ds_error_title),
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            TruckIllustration(VehicleType.TRUCK_MEDIUM, Modifier.size(width = 160.dp, height = 90.dp), muted = true)
            Box(
                Modifier.align(Alignment.TopEnd).size(34.dp).background(LogyxTheme.colors.dangerContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(22.dp), tint = LogyxTheme.colors.dangerText) }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            LogyxButton(stringResource(R.string.ds_retry), onRetry, size = ButtonSize.Medium, fullWidth = false)
        }
    }
}

/** Contenedor con pull-to-refresh para listas. */
@Composable
fun Refreshable(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier) { content() }
}

/** Tarjeta base de LOGYX: radio 10dp, borde fino steel-200. */
@Composable
fun LogyxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Card(onClick, modifier, shape = MaterialTheme.shapes.medium, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding)) { content() }
        }
    } else {
        Card(modifier, shape = MaterialTheme.shapes.medium, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding)) { content() }
        }
    }
}

/** Sobrecarga para los mapeos `status.pill()` de Labels.kt. */
@Composable
fun StatusPill(pill: Pair<String, PillTone>, modifier: Modifier = Modifier) = StatusPill(pill.first, pill.second, modifier)
