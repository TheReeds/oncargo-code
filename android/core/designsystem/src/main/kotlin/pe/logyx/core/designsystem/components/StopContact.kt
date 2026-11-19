package pe.logyx.core.designsystem.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopType
import pe.logyx.core.model.StopView

/** Abre el marcador con el número (`ACTION_DIAL`: no llama solo ni pide permiso). false si no hay app de teléfono. */
fun Context.dialPhone(phone: String): Boolean = launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })))

/**
 * Abre una app de mapas en la dirección (`geo:0,0?q=…`); si no hay ninguna, Google Maps en el navegador.
 * false si tampoco hay navegador.
 */
fun Context.openDirections(query: String): Boolean {
    val q = Uri.encode(query)
    return launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q"))) ||
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$q")))
}

private fun Context.launch(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** `987654321` → `987 654 321` (se lee mejor y TalkBack lo dicta por grupos). */
fun formatPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return if (digits.length == 9) "${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6)}" else phone
}

/** `HH:mm` aunque el servidor mande `HH:mm:ss`. */
private fun hhmm(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.take(5)

/** «Recibe de 08:00 a 12:00» (entrega) o «Atiende de … a …» (recojo); null sin ventana. */
@Composable
fun stopWindowText(stop: StopView): String? {
    val from = hhmm(stop.windowFrom)
    val to = hhmm(stop.windowTo)
    val pickup = stop.type == StopType.PICKUP
    return when {
        from != null && to != null -> stringResource(if (pickup) R.string.ds_contact_window_pickup else R.string.ds_contact_window_delivery, from, to)
        from != null -> stringResource(if (pickup) R.string.ds_contact_window_pickup_from else R.string.ds_contact_window_delivery_from, from)
        to != null -> stringResource(if (pickup) R.string.ds_contact_window_pickup_to else R.string.ds_contact_window_delivery_to, to)
        else -> null
    }
}

/** Texto para la app de mapas: dirección y ciudad («Av. Argentina 1234, Lima»). */
fun StopView.directionsQuery(): String = listOfNotNull(address?.takeIf { it.isNotBlank() }, cityLabel(city).takeIf { it.isNotBlank() }).joinToString(", ")

/**
 * Contacto de una parada (roadmap v2 1.3): ventana horaria, nombre, «Llamar» (`tel:`), «Cómo llegar» (`geo:`) e
 * instrucciones. Solo pinta lo que viene (las partes lo reciben tras adjudicar; el resto ve null). Botones de 48 dp
 * (`large` = 56 dp, modo conductor) con descripción para TalkBack. Si no hay app para llamar o abrir el mapa lo dice
 * debajo, sin romper.
 * ```kotlin
 * StopContactBlock(stop, large = true)                 // conductor: siguiente parada
 * StopContactBlock(stop, showDirections = false)       // seguimiento PYME/transportista
 * ```
 */
@Composable
fun StopContactBlock(
    stop: StopView,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    showDirections: Boolean = true,
) {
    val context = LocalContext.current
    var unavailable by remember(stop.id) { mutableStateOf<Int?>(null) }
    val window = stopWindowText(stop)
    val name = stop.contactName?.takeIf { it.isNotBlank() }
    val phone = stop.contactPhone?.takeIf { it.isNotBlank() }
    val instructions = stop.instructions?.takeIf { it.isNotBlank() }
    val query = stop.directionsQuery()
    val canDirect = showDirections && query.isNotBlank()
    if (window == null && name == null && phone == null && instructions == null && !canDirect) return
    val bodyStyle = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        window?.let { ContactLine(Icons.Rounded.Schedule, it, bodyStyle) }
        if (name != null || phone != null) {
            ContactLine(Icons.Rounded.Person, listOfNotNull(name, phone?.let(::formatPhone)).joinToString(" · "), bodyStyle)
        }
        if (phone != null || canDirect) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val size = if (large) ButtonSize.Large else ButtonSize.Medium
                if (phone != null) {
                    val callA11y = stringResource(R.string.ds_contact_call_a11y, name ?: formatPhone(phone))
                    LogyxButton(
                        stringResource(R.string.ds_contact_call),
                        { if (!context.dialPhone(phone)) unavailable = R.string.ds_contact_no_dialer },
                        Modifier.weight(1f).semantics { contentDescription = callA11y },
                        variant = ButtonVariant.Secondary, size = size, icon = Icons.Rounded.Call,
                    )
                }
                if (canDirect) {
                    val dirA11y = stringResource(R.string.ds_contact_directions_a11y, query)
                    LogyxButton(
                        stringResource(R.string.ds_contact_directions),
                        { if (!context.openDirections(query)) unavailable = R.string.ds_contact_no_maps },
                        Modifier.weight(1f).semantics { contentDescription = dirA11y },
                        variant = ButtonVariant.Secondary, size = size, icon = Icons.Rounded.Directions,
                    )
                }
            }
        }
        unavailable?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = LogyxTheme.colors.dangerText)
        }
        instructions?.let { ContactLine(Icons.Rounded.Info, stringResource(R.string.ds_contact_instructions, it), bodyStyle) }
    }
}

@Composable
private fun ContactLine(icon: ImageVector, text: String, style: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp, end = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = style, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Preview(showBackground = true)
@Composable
private fun StopContactBlockPreview() {
    LogyxTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            StopContactBlock(
                StopView(
                    "d", 2, StopType.DELIVERY, "JULIACA", "Jr. Lima 456", StopStatus.PENDING,
                    contactName = "Ana Torres", contactPhone = "987654321", windowFrom = "08:00", windowTo = "12:00",
                    instructions = "Tocar el timbre del almacén 3. Descarga por la puerta lateral.",
                ),
                large = true,
            )
            StopContactBlock(StopView("p", 1, StopType.PICKUP, "LIMA", "Av. Argentina 123", StopStatus.PENDING, windowFrom = "07:00"), showDirections = false)
        }
    }
}
