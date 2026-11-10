package pe.logyx.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
 * Confirmación de una acción importante. Úsala SIEMPRE antes de: aceptar una oferta,
 * cancelar una solicitud, avanzar de parada o cerrar sesión.
 * Título = pregunta corta; mensaje = consecuencia; botón de confirmar = verbo + objeto.
 */
@Composable
fun LogyxConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.ds_cancel),
    destructive: Boolean = false,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val extra = LogyxTheme.colors
    val tint = if (destructive) extra.dangerText else MaterialTheme.colorScheme.primary
    val tintBg = if (destructive) extra.dangerContainer else MaterialTheme.colorScheme.primaryContainer
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        icon = {
            Box(Modifier.size(52.dp).background(tintBg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon ?: if (destructive) Icons.Rounded.WarningAmber else Icons.Rounded.HelpOutline, null, tint = tint, modifier = Modifier.size(28.dp))
            }
        },
        title = { Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            LogyxButton(
                confirmLabel, onConfirm,
                variant = if (destructive) ButtonVariant.Destructive else ButtonVariant.Primary,
                size = ButtonSize.Medium, loading = loading, fullWidth = false,
            )
        },
        dismissButton = {
            LogyxButton(dismissLabel, onDismiss, variant = ButtonVariant.Ghost, size = ButtonSize.Medium, enabled = !loading, fullWidth = false)
        },
    )
}

/**
 * Hoja inferior estándar: asa, título y botón cerrar, contenido desplazable con insets.
 * Úsala para ofertar, elegir conductor, listas con búsqueda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogyxBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            if (title != null) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.ds_close)) }
                }
                Spacer(Modifier.size(8.dp))
            }
            content()
            Spacer(Modifier.size(24.dp))
        }
    }
}

