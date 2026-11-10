package pe.logyx.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R

/** Chip de filtro (interactivo, seleccionable). Área táctil >= 48dp. */
@Composable
fun LogyxFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).padding(vertical = 4.dp),
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = when {
            selected -> ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) })
            leadingIcon != null -> ({ Icon(leadingIcon, null, Modifier.size(FilterChipDefaults.IconSize)) })
            else -> null
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surface,
            labelColor = scheme.onSurface,
            iconColor = scheme.onSurfaceVariant,
            selectedContainerColor = scheme.primary,
            selectedLabelColor = scheme.onPrimary,
            selectedLeadingIconColor = scheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = LogyxTheme.colors.fieldBorder.copy(alpha = 0.55f),
            selectedBorderColor = scheme.primary,
        ),
    )
}

/** Fila de chips que salta de línea. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/**
 * Control segmentado: 2 a 4 opciones mutuamente excluyentes (p. ej. "Por recoger | En ruta | Entregados").
 * Para muchas opciones usa [LogyxFilterChip].
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .background(scheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(if (isSelected) scheme.surface else scheme.surfaceVariant, label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .semantics { this.selected = isSelected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    optionLabel(option),
                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}


/** Insignia pequeña informativa con icono: "Compatible", "Nuevo", "Mejor precio". */
@Composable
fun LogyxBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.Neutral,
    icon: ImageVector? = null,
) {
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
        modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(13.dp), tint = fg)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

/** Empresa con RUC verificado. */
@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, text: String = stringResource(R.string.ds_verified_company)) {
    LogyxBadge(text, modifier, PillTone.Emerald, Icons.Rounded.Verified)
}

/** Contador numérico (ofertas 3/5, notificaciones). */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, description: String? = null) {
    Box(
        modifier
            .heightIn(min = 20.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .semantics { description?.let { contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Preview(showBackground = true)
@Composable
private fun ChipsPreview() {
    LogyxTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipRow {
                LogyxFilterChip("Cerca de mí", true, {}, leadingIcon = Icons.Rounded.NearMe)
                LogyxFilterChip("Mi vehículo", false, {})
            }
            SegmentedControl(listOf("Activas", "Cerradas", "Todas"), "Activas", {}, { it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogyxBadge("Compatible", tone = PillTone.Emerald)
                VerifiedBadge()
                CountBadge(3)
            }
        }
    }
}
