package pe.logyx.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R

/** Jerarquía visual del botón. */
enum class ButtonVariant {
    /** Acción principal de la pantalla (petróleo). */
    Primary,

    /** Dinero / oportunidad: Ofertar, Aceptar oferta (esmeralda AA). */
    Money,

    /** Acción alternativa (contorno). */
    Secondary,

    /** Acción terciaria (solo texto). */
    Ghost,

    /** Acción irreversible: cancelar, rechazar (rojo). */
    Destructive,
}

/** Alto del botón. `Xl` es para el conductor (guantes, sol, vibración): 72dp, texto grande. */
enum class ButtonSize(val height: Int, val textSp: Int, val iconDp: Int) {
    Medium(48, 15, 20),
    Large(56, 16, 22),
    Xl(72, 20, 28),
}

/**
 * Botón estándar de LOGYX. Microcopy: verbo + objeto ("Enviar oferta", "Aceptar oferta").
 * Con [loading] se muestra un indicador, se deshabilita y se anuncia como ocupado.
 */
@Composable
fun LogyxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Large,
    loading: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fullWidth: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = LogyxTheme.colors
    val shape = RoundedCornerShape(if (size == ButtonSize.Xl) 18.dp else 14.dp)
    val sizing = Modifier
        .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
        .heightIn(min = size.height.dp)
    val busyDescription = stringResource(R.string.ds_loading_action)
    val semantic = Modifier.semantics { if (loading) contentDescription = "$text. $busyDescription" }
    val isEnabled = enabled && !loading
    val padding = PaddingValues(horizontal = if (size == ButtonSize.Xl) 24.dp else 18.dp, vertical = 8.dp)
    val content: @Composable () -> Unit = {
        ButtonContent(text, icon, loading, size)
    }
    val all = modifier.then(sizing).then(semantic)
    when (variant) {
        ButtonVariant.Primary -> Button(
            onClick, all, isEnabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.primary, contentColor = scheme.onPrimary,
                disabledContainerColor = if (loading) scheme.primary.copy(alpha = 0.7f) else scheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = if (loading) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.5f),
            ),
        ) { content() }

        ButtonVariant.Money -> Button(
            onClick, all, isEnabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = extra.moneyStrong, contentColor = Color.White,
                disabledContainerColor = if (loading) extra.moneyStrong.copy(alpha = 0.7f) else scheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = if (loading) Color.White else scheme.onSurface.copy(alpha = 0.5f),
            ),
        ) { content() }

        ButtonVariant.Destructive -> Button(
            onClick, all, isEnabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = extra.dangerText, contentColor = Color.White,
                disabledContainerColor = if (loading) extra.dangerText.copy(alpha = 0.7f) else scheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = if (loading) Color.White else scheme.onSurface.copy(alpha = 0.5f),
            ),
        ) { content() }

        ButtonVariant.Secondary -> OutlinedButton(
            onClick, all, isEnabled, shape = shape, contentPadding = padding,
            border = BorderStroke(1.5.dp, if (isEnabled) scheme.primary else scheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.primary),
        ) { content() }

        ButtonVariant.Ghost -> TextButton(
            onClick, all, isEnabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(contentColor = scheme.primary),
        ) { content() }
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, loading: Boolean, size: ButtonSize) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            loading -> {
                CircularProgressIndicator(Modifier.size(size.iconDp.dp), strokeWidth = 2.5.dp, color = androidx.compose.material3.LocalContentColor.current)
                Spacer(Modifier.width(10.dp))
            }
            icon != null -> {
                Icon(icon, null, Modifier.size(size.iconDp.dp))
                Spacer(Modifier.width(10.dp))
            }
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = size.textSp.sp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ButtonsPreview() {
    LogyxTheme {
        androidx.compose.foundation.layout.Column(
            Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            LogyxButton("Enviar oferta", {}, icon = Icons.Rounded.LocalShipping)
            LogyxButton("Aceptar oferta", {}, variant = ButtonVariant.Money)
            LogyxButton("Ver detalle", {}, variant = ButtonVariant.Secondary)
            LogyxButton("Omitir", {}, variant = ButtonVariant.Ghost)
            LogyxButton("Cancelar solicitud", {}, variant = ButtonVariant.Destructive)
            LogyxButton("Enviando…", {}, loading = true)
            LogyxButton("Llegué al recojo", {}, size = ButtonSize.Xl)
        }
    }
}

