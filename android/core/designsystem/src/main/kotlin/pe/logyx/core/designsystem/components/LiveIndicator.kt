package pe.logyx.core.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R
import pe.logyx.core.model.RealtimeStatus

/**
 * Estado del tiempo real que `:app` publica para todas las barras superiores (los features no lo conocen).
 * `null` = no mostrar nada (sin conexión abierta todavía o inactiva: no es un fallo que el usuario deba ver).
 */
val LocalRealtimeStatus = compositionLocalOf<RealtimeStatus?> { null }

/** Provee [status] a las [LogyxTopBar] de abajo. */
@Composable
fun ProvideRealtimeStatus(status: RealtimeStatus?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRealtimeStatus provides status, content = content)
}

/**
 * Indicador discreto "En vivo" / "Reconectando…" de la barra superior (espeja `LiveIndicator` de la web).
 * Solo se pinta con [RealtimeStatus.LIVE] y [RealtimeStatus.RECONNECTING]; `CONNECTING` y `OFFLINE` no muestran nada.
 * Anuncia el cambio a TalkBack (región viva) con una descripción propia.
 */
@Composable
fun LiveIndicator(status: RealtimeStatus?, modifier: Modifier = Modifier) {
    if (status != RealtimeStatus.LIVE && status != RealtimeStatus.RECONNECTING) return
    val live = status == RealtimeStatus.LIVE
    val label = stringResource(if (live) R.string.ds_live else R.string.ds_reconnecting)
    val description = stringResource(if (live) R.string.ds_live_description else R.string.ds_reconnecting_description)
    val dot = if (live) LogyxTheme.colors.money else LogyxTheme.colors.warning
    val text = if (live) LogyxTheme.colors.moneyText else LogyxTheme.colors.onWarningContainer
    val pulse = rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse",
    )
    Row(
        modifier
            .padding(horizontal = 6.dp)
            .semantics { contentDescription = description; liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).alpha(if (live) pulse.value else 1f).background(dot, CircleShape))
            Text(label, style = MaterialTheme.typography.labelMedium, color = text, maxLines = 1)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LiveIndicatorPreview() {
    LogyxTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LiveIndicator(RealtimeStatus.LIVE)
            LiveIndicator(RealtimeStatus.RECONNECTING)
        }
    }
}
