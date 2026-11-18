package pe.logyx.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R

/** Estado de envío de un mensaje propio (envío optimista). */
enum class BubbleState { Sent, Sending, Failed }

/**
 * Burbuja de chat. [mine] alinea a la derecha (petróleo); la otra parte, a la izquierda (superficie).
 * [system] = aviso centrado (contra-ofertas), con [price] opcional en mono.
 */
@Composable
fun ChatBubble(
    text: String,
    mine: Boolean,
    time: String?,
    modifier: Modifier = Modifier,
    sender: String? = null,
    system: Boolean = false,
    price: Double? = null,
    state: BubbleState = BubbleState.Sent,
    onRetry: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    if (system) {
        Column(modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.widthIn(max = 320.dp).background(LogyxTheme.colors.infoContainer, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                if (price != null) Text(Fmt.money(price), style = LogyxText.moneySmall, color = LogyxTheme.colors.moneyText)
                time?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
            }
        }
        return
    }
    val bg = if (mine) scheme.primary else scheme.surface
    val fg = if (mine) scheme.onPrimary else scheme.onSurface
    val shape = RoundedCornerShape(16.dp, 16.dp, if (mine) 4.dp else 16.dp, if (mine) 16.dp else 4.dp)
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Column(
                Modifier.widthIn(max = 300.dp).background(bg, shape).padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (!mine && sender != null) Text(sender, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                Text(text, style = MaterialTheme.typography.bodyLarge, color = fg)
            }
            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state == BubbleState.Failed) {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.padding(end = 4.dp), tint = LogyxTheme.colors.dangerText)
                    Text(
                        stringResource(R.string.ds_chat_not_sent) + if (onRetry != null) " · " else "",
                        style = MaterialTheme.typography.labelSmall, color = LogyxTheme.colors.dangerText,
                    )
                    if (onRetry != null) {
                        Text(
                            stringResource(R.string.ds_chat_retry), style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary, modifier = Modifier.clickable(role = Role.Button, onClick = onRetry).padding(vertical = 8.dp),
                        )
                    }
                } else {
                    Text(
                        if (state == BubbleState.Sending) stringResource(R.string.ds_chat_sending) else time.orEmpty(),
                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Campo de redacción con botón de enviar; [error] se explica debajo del campo (RN07). */
@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    sending: Boolean = false,
    enabled: Boolean = true,
    placeholder: String = stringResource(R.string.ds_chat_placeholder),
    maxLength: Int = 1000,
) {
    // Rol Lector: no puede escribir (POST 403 AUTH-007); la pantalla muestra el aviso "Modo lectura".
    val enabled = enabled && !LocalReadOnly.current
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().imePadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                LogyxTextField(
                    value = value,
                    onValueChange = { if (it.length <= maxLength) onValueChange(it) },
                    label = stringResource(R.string.ds_chat_label),
                    placeholder = placeholder,
                    error = error,
                    enabled = enabled,
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )
            }
            IconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank() && !sending,
                modifier = Modifier.padding(start = 8.dp, top = 22.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.ds_chat_send))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatPreview() {
    LogyxTheme {
        Column(Modifier.padding(12.dp)) {
            ChatBubble("¿La carga requiere refrigeración?", mine = false, time = "10:02", sender = "Transportes Sur Andino")
            ChatBubble("Sí, entre 2 y 4 °C.", mine = true, time = "10:03")
            ChatBubble("Te escribo ahora", mine = true, time = null, state = BubbleState.Sending)
            ChatBubble("Mensaje con datos", mine = true, time = null, state = BubbleState.Failed, onRetry = {})
            ChatBubble("Contraoferta enviada", mine = false, time = "10:10", system = true, price = 1700.0)
            ChatComposer("999", {}, {}, error = "El mensaje contiene datos de contacto. Coordina todo por el chat de OnCargo.")
        }
    }
}
