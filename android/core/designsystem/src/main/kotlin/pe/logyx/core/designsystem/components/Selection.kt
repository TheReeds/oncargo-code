package pe.logyx.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R

/**
 * Fila de selección única (radio) con tarjeta: resalta con borde petróleo y check al elegirla.
 * Úsala para elegir conductor, vehículo o cualquier opción con detalle dentro de una hoja.
 */
@Composable
fun RadioCardRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val selectedLabel = stringResource(R.string.ds_selected)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (selected) stateDescription = selectedLabel },
    ) {
        Row(
            Modifier.padding(16.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = scheme.primary)
        }
    }
}

/** Iniciales (máx. 2) de un nombre: "Carlos Rojas" -> "CR". */
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** Avatar circular con iniciales. [description] lo lee TalkBack (p. ej. "Foto de Carlos Rojas"). */
@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 72.dp, description: String? = null) {
    Box(
        modifier.size(size).background(MaterialTheme.colorScheme.primary, CircleShape)
            .semantics { description?.let { contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(name),
            style = if (size >= 64.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Pie de lista infinita mientras se carga la siguiente página. */
@Composable
fun LoadingMoreFooter(modifier: Modifier = Modifier) {
    val text = stringResource(R.string.ds_loading_more)
    Row(
        modifier.fillMaxWidth().padding(vertical = 20.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = text },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
        Text(text, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectionPreview() {
    LogyxTheme {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioCardRow(selected = true, onClick = {}) { Text("Juan Pari Mamani", Modifier.weight(1f)) }
            RadioCardRow(selected = false, onClick = {}) { Text("Rosa Quispe", Modifier.weight(1f)) }
            InitialsAvatar("Carlos Rojas")
            LoadingMoreFooter()
        }
    }
}
