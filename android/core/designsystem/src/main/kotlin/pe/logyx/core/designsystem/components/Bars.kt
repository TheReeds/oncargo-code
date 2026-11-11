package pe.logyx.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R

/**
 * Barra superior de LOGYX: título grande alineado a la izquierda, atrás en círculo tonal,
 * subtítulo opcional (p. ej. ruta o estado) y acciones.
 */
@Composable
fun LogyxTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, Modifier.size(48.dp)) {
                Box(
                    Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.ds_back), Modifier.size(20.dp))
                }
            }
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(
                title,
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        LiveIndicator(LocalRealtimeStatus.current)
        actions()
    }
}

data class LogyxNavItem(
    val label: String,
    val icon: ImageVector,
    /** Si no es null y > 0, muestra un contador sobre el icono. */
    val badgeCount: Int? = null,
)

/** Barra de navegación inferior: fondo blanco con filete, indicador petróleo, etiqueta siempre visible. */
@Composable
fun LogyxBottomBar(
    items: List<LogyxNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        HorizontalDivider(color = scheme.outlineVariant)
        NavigationBar(containerColor = scheme.surface, tonalElevation = 0.dp) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    icon = {
                        if ((item.badgeCount ?: 0) > 0) {
                            BadgedBox(badge = { Badge { Text(item.badgeCount.toString()) } }) { Icon(item.icon, null) }
                        } else {
                            Icon(item.icon, null)
                        }
                    },
                    label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = scheme.primary,
                        selectedTextColor = scheme.primary,
                        indicatorColor = scheme.primaryContainer,
                        unselectedIconColor = scheme.onSurfaceVariant,
                        unselectedTextColor = scheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BarsPreview() {
    LogyxTheme {
        Column {
            LogyxTopBar("Detalle de carga", subtitle = "Lima → Juliaca", onBack = {})
            LogyxBottomBar(
                listOf(
                    LogyxNavItem("Inicio", Icons.Rounded.Home),
                    LogyxNavItem("Cargas", Icons.Rounded.Search),
                    LogyxNavItem("Ofertas", Icons.Rounded.Gavel, badgeCount = 2),
                    LogyxNavItem("Envíos", Icons.Rounded.LocalShipping),
                ),
                selectedIndex = 1, onSelect = {},
            )
        }
    }
}
