package pe.logyx.core.designsystem.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R

enum class MessageKind { Success, Error, Info, Warning }

/**
 * Mensaje transitorio para el usuario. Los ViewModels lo exponen en su estado
 * (`message: UiMessage?`) y la pantalla lo muestra con [MessageEffect].
 * Convención de copy: éxito = "Oferta enviada"; error = qué pasó + qué hacer.
 */
@Immutable
data class UiMessage(
    val text: String = "",
    val kind: MessageKind = MessageKind.Info,
    val actionLabel: String? = null,
    @StringRes val textRes: Int? = null,
    val args: List<Any> = emptyList(),
) {
    /** Texto final: el recurso (con argumentos) si lo hay; si no, el texto literal (p. ej. mensajes de la API). */
    fun resolve(context: Context): String = textRes?.let { context.getString(it, *args.toTypedArray()) } ?: text

    companion object {
        fun success(text: String) = UiMessage(text, MessageKind.Success)
        fun success(@StringRes res: Int, vararg args: Any) = UiMessage(kind = MessageKind.Success, textRes = res, args = args.toList())
        fun error(text: String, actionLabel: String? = null) = UiMessage(text, MessageKind.Error, actionLabel)
        fun error(@StringRes res: Int, vararg args: Any) = UiMessage(kind = MessageKind.Error, textRes = res, args = args.toList())
        fun info(text: String) = UiMessage(text, MessageKind.Info)
        fun info(@StringRes res: Int, vararg args: Any) = UiMessage(kind = MessageKind.Info, textRes = res, args = args.toList())
    }
}

/**
 * Texto de UI que un ViewModel puede exponer sin tener `Context`: literal (mensajes de la API) o recurso con argumentos.
 * Se resuelve en la pantalla con `asString()`.
 */
@Immutable
sealed interface UiText {
    data class Raw(val value: String) : UiText
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> stringResource(id, *args.toTypedArray())
}

private class LogyxVisuals(
    override val message: String,
    val kind: MessageKind,
    override val actionLabel: String?,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = true
    override val duration: SnackbarDuration =
        if (kind == MessageKind.Error) SnackbarDuration.Long else SnackbarDuration.Short
}

/** Estado del host de mensajes. Créalo con [rememberLogyxSnackbarState]. */
@Stable
class LogyxSnackbarState {
    internal val host = SnackbarHostState()

    /** Muestra el mensaje y suspende hasta que desaparece. Devuelve true si se pulsó la acción. */
    suspend fun show(message: UiMessage): Boolean =
        host.showSnackbar(LogyxVisuals(message.text, message.kind, message.actionLabel)) == SnackbarResult.ActionPerformed
}

@Composable
fun rememberLogyxSnackbarState(): LogyxSnackbarState = remember { LogyxSnackbarState() }

/**
 * Muestra [message] cuando no es null y luego llama a [onShown] para que el ViewModel lo limpie.
 * ```
 * MessageEffect(state.message, snackbar, onMessageShown)
 * ```
 */
@Composable
fun MessageEffect(message: UiMessage?, snackbar: LogyxSnackbarState, onShown: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.show(message.copy(text = message.resolve(context), textRes = null))
            onShown()
        }
    }
}

private data class KindStyle(val bg: Color, val fg: Color, val accent: Color, val icon: ImageVector)

@Composable
private fun styleFor(kind: MessageKind): KindStyle {
    val c = LogyxTheme.colors
    val s = MaterialTheme.colorScheme
    return when (kind) {
        MessageKind.Success -> KindStyle(c.moneyContainer, c.onMoneyContainer, c.moneyStrong, Icons.Rounded.CheckCircle)
        MessageKind.Error -> KindStyle(c.dangerContainer, c.dangerText, c.dangerText, Icons.Rounded.ErrorOutline)
        MessageKind.Warning -> KindStyle(c.warningContainer, c.onWarningContainer, c.onWarningContainer, Icons.Rounded.WarningAmber)
        MessageKind.Info -> KindStyle(c.infoContainer, c.onInfoContainer, s.primary, Icons.Rounded.Info)
    }
}

/** Reemplaza al `SnackbarHost` por defecto: tarjeta con icono, color por variante y descartar. */
@Composable
fun LogyxSnackbarHost(state: LogyxSnackbarState, modifier: Modifier = Modifier) {
    SnackbarHost(state.host, modifier) { data ->
        val kind = (data.visuals as? LogyxVisuals)?.kind ?: MessageKind.Info
        val st = styleFor(kind)
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .fillMaxWidth()
                .background(st.bg, RoundedCornerShape(14.dp))
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(st.icon, null, Modifier.size(22.dp), tint = st.accent)
            Spacer(Modifier.width(10.dp))
            Text(data.visuals.message, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = st.fg)
            data.visuals.actionLabel?.let {
                TextButton(onClick = { data.performAction() }) { Text(it, color = st.fg, style = MaterialTheme.typography.labelLarge) }
            }
            IconButton(onClick = { data.dismiss() }) {
                Icon(Icons.Rounded.Close, stringResource(R.string.ds_banner_dismiss), tint = st.fg)
            }
        }
    }
}

/** Aviso persistente dentro de la pantalla (no desaparece solo): errores de formulario, estado de subasta... */
@Composable
fun AlertBanner(
    text: String,
    modifier: Modifier = Modifier,
    kind: MessageKind = MessageKind.Info,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val st = styleFor(kind)
    Row(
        modifier
            .fillMaxWidth()
            .background(st.bg, RoundedCornerShape(14.dp))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(st.icon, null, Modifier.size(22.dp), tint = st.accent)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = st.fg)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = st.fg)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(actionLabel, color = st.fg, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.ds_banner_dismiss), tint = st.fg, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BannersPreview() {
    LogyxTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AlertBanner("Oferta enviada. Te avisaremos si la PYME la elige.", kind = MessageKind.Success)
            AlertBanner("No pudimos enviar tu oferta. Revisa tu conexión e inténtalo de nuevo.", kind = MessageKind.Error, actionLabel = "Reintentar", onAction = {})
            AlertBanner("La subasta cierra en 4 min.", kind = MessageKind.Warning, title = "Date prisa")
            AlertBanner("Los precios incluyen peajes.", kind = MessageKind.Info)
        }
    }
}
