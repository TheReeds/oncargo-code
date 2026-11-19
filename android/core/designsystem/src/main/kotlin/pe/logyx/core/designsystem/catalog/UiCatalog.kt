package pe.logyx.core.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.BidStandingRow
import pe.logyx.core.designsystem.components.ChatBubble
import pe.logyx.core.designsystem.components.LogyxReasonSheet
import pe.logyx.core.designsystem.components.VehicleChoice
import pe.logyx.core.designsystem.components.VehicleChoiceList
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.BidOutcome
import pe.logyx.core.model.BidStandingBand
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.MyBidStanding
import pe.logyx.core.model.VehicleFit
import pe.logyx.core.model.VehicleStatus
import pe.logyx.core.model.VehicleView
import pe.logyx.core.designsystem.components.ChatComposer
import pe.logyx.core.designsystem.components.EvidenceTile
import pe.logyx.core.designsystem.components.RatingRow
import pe.logyx.core.designsystem.components.SignaturePad
import pe.logyx.core.designsystem.components.StarRating
import pe.logyx.core.designsystem.components.TileStatus
import pe.logyx.core.designsystem.components.TrustScoreBadge
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.ProvideReadOnly
import pe.logyx.core.designsystem.components.ReadOnlyNotice
import pe.logyx.core.designsystem.components.VerificationBanner
import pe.logyx.core.model.VerificationStatus
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.ChipRow
import pe.logyx.core.designsystem.components.CountBadge
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.InfoChip
import pe.logyx.core.designsystem.components.KpiTile
import pe.logyx.core.designsystem.components.LiveIndicator
import pe.logyx.core.model.RealtimeStatus
import pe.logyx.core.designsystem.components.LogyxBadge
import pe.logyx.core.designsystem.components.LogyxBottomBar
import pe.logyx.core.designsystem.components.LogyxBottomSheet
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCombobox
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxDateField
import pe.logyx.core.designsystem.components.LogyxDateRangeField
import pe.logyx.core.designsystem.components.LogyxTimeField
import pe.logyx.core.designsystem.components.LogyxDropdown
import pe.logyx.core.designsystem.components.LogyxFilterChip
import pe.logyx.core.designsystem.components.LogyxNavItem
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.LogyxTextField
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.designsystem.components.MoneyField
import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.designsystem.components.PriceTag
import pe.logyx.core.designsystem.components.RouteLine
import pe.logyx.core.designsystem.components.SegmentedControl
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.StopTimeline
import pe.logyx.core.designsystem.components.TruckIllustration
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.VerifiedBadge
import pe.logyx.core.designsystem.components.parseMoney
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopType
import pe.logyx.core.model.StopView
import pe.logyx.core.model.VehicleType

/** Catálogo del sistema de diseño (solo se enlaza desde builds debug). Muestra cada componente y variante. */
@Composable
fun UiCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val snackbar = rememberLogyxSnackbarState()
    val scope = rememberCoroutineScope()
    LogyxScreen("Catálogo UI", modifier, subtitle = "Sistema de diseño OnCargo", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Section("Colores y tipografía") { ColorsAndType() }
            Section("Botones") { Buttons() }
            Section("Campos de texto") { TextFields() }
            Section("Selectores") { Pickers() }
            Section("Chips, segmentado y badges") { ChipsSection() }
            Section("Mensajes") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogyxButton("Éxito", { scope.launch { snackbar.show(UiMessage.success("Oferta enviada")) } }, Modifier.weight(1f), ButtonVariant.Money, ButtonSize.Medium)
                    LogyxButton("Error", { scope.launch { snackbar.show(UiMessage.error("No pudimos enviar tu oferta. Revisa tu conexión e inténtalo de nuevo.", "Reintentar")) } }, Modifier.weight(1f), ButtonVariant.Destructive, ButtonSize.Medium)
                    LogyxButton("Info", { scope.launch { snackbar.show(UiMessage.info("Actualizamos las cargas cerca de ti")) } }, Modifier.weight(1f), ButtonVariant.Secondary, ButtonSize.Medium)
                }
                AlertBanner("Oferta enviada. Te avisaremos si la PYME la elige.", kind = MessageKind.Success)
                AlertBanner("No pudimos cargar tus envíos. Desliza hacia abajo para reintentar.", kind = MessageKind.Error, title = "Sin conexión")
                AlertBanner("La subasta cierra en 4 min.", kind = MessageKind.Warning)
                AlertBanner("Los precios incluyen peajes y combustible.", kind = MessageKind.Info, onDismiss = {})
            }
            Section("Diálogos y hojas") { Overlays() }
            Section("Estados de pantalla") {
                EmptyState("Aún no tienes ofertas", "Cuando envíes una oferta la verás aquí.", actionLabel = "Buscar cargas", onAction = {})
                ErrorState("Revisa tu conexión e inténtalo de nuevo.", onRetry = {})
                SkeletonList(Modifier.padding(0.dp), count = 1)
            }
            Section("Datos de negocio") { Domain() }
            Section("Reputación, chat y evidencia") { Phase3() }
            Section("Negociación: posición, flota y motivos") { Negotiation() }
            Section("Cuenta: verificación y rol Lector") {
                VerificationBanner(VerificationStatus.PENDING, null)
                VerificationBanner(VerificationStatus.REJECTED, "El RUC no coincide con la razón social.")
                VerificationBanner(VerificationStatus.SUSPENDED, null)
                ReadOnlyNotice()
                // Con el rol Lector el botón de escritura no se muestra (roadmap v2 1.7); `Can { }` hace lo mismo con bloques.
                ProvideReadOnly(true) { LogyxButton("Enviar oferta (oculto para el Lector)", {}, variant = ButtonVariant.Money, requiresOperate = true) }
                LogyxButton("Enviar oferta (requiere operar)", {}, variant = ButtonVariant.Money, requiresOperate = true)
            }
            Section("Indicador en vivo") {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    LiveIndicator(RealtimeStatus.LIVE)
                    LiveIndicator(RealtimeStatus.RECONNECTING)
                }
            }
            Section("Conductor sin señal (cola offline)") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pe.logyx.core.designsystem.components.SyncStatusChip(3, pe.logyx.core.designsystem.components.SyncChipState.WaitingSignal, {})
                    pe.logyx.core.designsystem.components.SyncStatusChip(1, pe.logyx.core.designsystem.components.SyncChipState.Sending, {})
                    pe.logyx.core.designsystem.components.SyncStatusChip(2, pe.logyx.core.designsystem.components.SyncChipState.Failed, {})
                    var kg by remember { mutableStateOf("2450") }
                    pe.logyx.core.designsystem.components.NumberField(kg, { kg = it }, "Peso medido", "kg", hint = "Lo que marcó la balanza.")
                    pe.logyx.core.designsystem.components.StopTimeline(
                        listOf(
                            pe.logyx.core.model.StopView(
                                "p", 1, pe.logyx.core.model.StopType.PICKUP, "LIMA", "Av. Argentina 1234", pe.logyx.core.model.StopStatus.COMPLETED,
                                completedAt = "2026-11-10T09:00:00-05:00", loadedWeightKg = 2450.0, completionOfficeReason = "Celular sin batería",
                            ),
                            pe.logyx.core.model.StopView(
                                "d", 2, pe.logyx.core.model.StopType.DELIVERY, "JULIACA", null, pe.logyx.core.model.StopStatus.COMPLETED,
                                completedAt = "2026-11-11T15:00:00-05:00", receivedByName = "Ana Torres", receivedByDoc = "45678912",
                            ),
                        ),
                    )
                }
            }
            Section("Navegación") {
                var sel by remember { mutableStateOf(1) }
                LogyxBottomBar(
                    listOf(
                        LogyxNavItem("Inicio", Icons.Rounded.Home),
                        LogyxNavItem("Cargas", Icons.Rounded.Search),
                        LogyxNavItem("Ofertas", Icons.Rounded.Gavel, badgeCount = 2),
                        LogyxNavItem("Envíos", Icons.Rounded.LocalShipping),
                    ),
                    sel, { sel = it },
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun ColorsAndType() {
    val c = LogyxTheme.colors
    val s = MaterialTheme.colorScheme
    ChipRow {
        listOf(
            "Petróleo" to s.primary, "Esmeralda AA" to c.moneyStrong, "Ámbar AA" to c.onWarningContainer,
            "Rojo AA" to c.dangerText, "Tinta" to s.onSurface,
        ).forEach { (name, color) ->
            Row(
                Modifier.padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                androidx.compose.foundation.layout.Box(Modifier.size(18.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                Text(name, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Text("Titular 24 · Geist Sans", style = MaterialTheme.typography.headlineSmall)
    Text("Título 16 semibold", style = MaterialTheme.typography.titleMedium)
    Text("Cuerpo 14 regular: Lima a Juliaca, 1,010 km.", style = MaterialTheme.typography.bodyMedium)
    Text("S/ 1,850.00", style = LogyxText.moneyLarge, color = c.moneyText)
    Text("ABC-123 · 2.5 t · 1,010 km", style = LogyxText.mono)
}

@Composable
private fun Buttons() {
    LogyxButton("Enviar oferta", {}, icon = Icons.Rounded.LocalShipping)
    LogyxButton("Aceptar oferta", {}, variant = ButtonVariant.Money)
    LogyxButton("Ver detalle", {}, variant = ButtonVariant.Secondary)
    LogyxButton("Omitir por ahora", {}, variant = ButtonVariant.Ghost)
    LogyxButton("Cancelar solicitud", {}, variant = ButtonVariant.Destructive)
    LogyxButton("Enviando oferta…", {}, loading = true)
    LogyxButton("No disponible", {}, enabled = false)
    LogyxButton("Mediano", {}, size = ButtonSize.Medium)
    LogyxButton("Llegué al recojo", {}, size = ButtonSize.Xl, icon = Icons.Rounded.NearMe)
}

@Composable
private fun TextFields() {
    var email by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var money by remember { mutableStateOf("1850.5") }
    LogyxTextField(email, { email = it }, "Correo", hint = "Usa el correo de tu empresa", placeholder = "tu@empresa.pe")
    LogyxTextField("hola", {}, "Contraseña", error = "La contraseña no es correcta. Revísala e inténtalo de nuevo.")
    LogyxTextField(note, { note = it }, "Nota para la PYME", optional = true, singleLine = false, minLines = 3)
    MoneyField(money, { money = it }, "Tu oferta", hint = "Monto mínimo ${Fmt.money(1200.0)} · Escribiste: ${Fmt.money(parseMoney(money))}")
    MoneyField("", {}, "Monto con error", error = "Ingresa un monto mayor a S/ 1,200.00.")
}

@Composable
private fun Pickers() {
    var vehicle by remember { mutableStateOf<VehicleType?>(VehicleType.TRUCK_SMALL) }
    var city by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var s by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var e by remember { mutableStateOf<java.time.LocalDate?>(null) }
    LogyxDropdown("Tipo de vehículo", VehicleType.entries, vehicle, { vehicle = it }, { it.name.lowercase().replace('_', ' ') })
    LogyxCombobox(
        "Ciudad de origen", listOf("Lima", "Arequipa", "Cusco", "Juliaca", "Puno", "Ayacucho", "Huancayo", "Tacna", "Ica", "Nasca"),
        city, { city = it }, { it }, searchPlaceholder = "Buscar ciudad", clearable = true,
    )
    LogyxDateField("Fecha de recojo", date, { date = it })
    LogyxDateRangeField("Rango de entrega", s, e, { a, b -> s = a; e = b })
    var time by remember { mutableStateOf<java.time.LocalTime?>(null) }
    LogyxTimeField("Hora de salida", time, { time = it })
}

@Composable
private fun ChipsSection() {
    var f by remember { mutableStateOf(setOf("near")) }
    var seg by remember { mutableStateOf("Activas") }
    ChipRow {
        LogyxFilterChip("Cerca de mí", "near" in f, { f = f.toggle("near") }, leadingIcon = Icons.Rounded.NearMe)
        LogyxFilterChip("Mi vehículo", "mine" in f, { f = f.toggle("mine") })
        LogyxFilterChip("Mejor pago", "pay" in f, { f = f.toggle("pay") })
    }
    SegmentedControl(listOf("Activas", "Cerradas", "Todas"), seg, { seg = it }, { it })
    ChipRow {
        StatusPill("Publicada", PillTone.Petrol)
        StatusPill("Adjudicada", PillTone.Emerald)
        StatusPill("Por recoger", PillTone.Amber)
        StatusPill("Rechazada", PillTone.Red)
        StatusPill("Cancelada", PillTone.Neutral)
    }
    ChipRow {
        LogyxBadge("Compatible", tone = PillTone.Emerald)
        LogyxBadge("Mejor precio", tone = PillTone.Petrol)
        VerifiedBadge()
        CountBadge(3)
        InfoChip("Carga general")
    }
}

private fun Set<String>.toggle(k: String) = if (k in this) this - k else this + k

@Composable
private fun Overlays() {
    var confirm by remember { mutableStateOf(false) }
    var destructive by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    LogyxButton("Confirmación", { confirm = true }, variant = ButtonVariant.Secondary, size = ButtonSize.Medium)
    LogyxButton("Confirmación destructiva", { destructive = true }, variant = ButtonVariant.Secondary, size = ButtonSize.Medium)
    LogyxButton("Hoja inferior", { sheet = true }, variant = ButtonVariant.Secondary, size = ButtonSize.Medium)
    if (confirm) LogyxConfirmDialog(
        "¿Aceptar esta oferta?", "Se rechazarán las demás ofertas y se creará el envío por S/ 1,850.00.",
        "Aceptar oferta", { confirm = false }, { confirm = false }, dismissLabel = "Volver",
    )
    if (destructive) LogyxConfirmDialog(
        "¿Cancelar la solicitud?", "Se cerrará la subasta y los transportistas dejarán de ver tu carga. No se puede deshacer.",
        "Cancelar solicitud", { destructive = false }, { destructive = false }, dismissLabel = "No, mantener", destructive = true,
    )
    if (sheet) LogyxBottomSheet({ sheet = false }, title = "Enviar oferta") {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MoneyField("1850", {}, "Tu oferta")
            LogyxButton("Enviar oferta", { sheet = false }, variant = ButtonVariant.Money)
        }
    }
}

@Composable
private fun Domain() {
    PriceTag(1850.0, label = "Precio referencial")
    RouteLine("Lima", "Juliaca", distance = "1,010 km", originSub = "Recojo 28 nov")
    ChipRow { VehicleType.entries.take(4).forEach { TruckIllustration(it) } }
    KpiTile("3", "Por recoger hoy", context = "2 en tránsito")
    StopTimeline(
        listOf(
            StopView("1", 1, StopType.PICKUP, "LIMA", "Av. Argentina 123", StopStatus.COMPLETED),
            StopView("2", 2, StopType.DELIVERY, "JULIACA", "Jr. Lima 456", StopStatus.PENDING),
        ),
    )
    pe.logyx.core.designsystem.components.StopContactBlock(
        StopView(
            "2", 2, StopType.DELIVERY, "JULIACA", "Jr. Lima 456", StopStatus.PENDING,
            contactName = "Ana Torres", contactPhone = "987654321", windowFrom = "08:00", windowTo = "12:00",
            instructions = "Descarga por la puerta lateral.",
        ),
    )
}

@Composable
private fun Negotiation() {
    var reason by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }
    var vehicle by remember { mutableStateOf<String?>("1") }
    BidStandingRow(MyBidStanding(1, 4, true, BidStandingBand.BEST))
    BidStandingRow(MyBidStanding(3, 4, false, BidStandingBand.WITHIN_15_PCT))
    BidStandingRow(MyBidStanding(4, 4, false, BidStandingBand.ABOVE_15_PCT, finalStanding = true))
    BidStandingRow(MyBidStanding(3, 5, false, BidStandingBand.WITHIN_15_PCT, position = pe.logyx.core.model.BidPosition.UPPER_HALF))
    var avisar by remember { mutableStateOf(true) }
    pe.logyx.core.designsystem.components.LogyxSwitchRow("Avisarme de cargas nuevas", avisar, { avisar = it }, supporting = "Búsqueda guardada")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BidOutcome.entries.filter { it != BidOutcome.UNKNOWN }.take(3).forEach { StatusPill(it.pill()) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BidOutcome.entries.filter { it != BidOutcome.UNKNOWN }.drop(3).forEach { StatusPill(it.pill()) }
    }
    VehicleChoiceList(
        choices = listOf(
            VehicleChoice(VehicleView("1", "ABC-101", VehicleType.REFRIGERATED, 8000.0, 30.0, VehicleStatus.AVAILABLE), VehicleFit.FITS),
            VehicleChoice(VehicleView("2", "XYZ-202", VehicleType.TRUCK_SMALL, 3000.0, null, VehicleStatus.AVAILABLE), VehicleFit.TYPE),
            VehicleChoice(VehicleView("3", "DEF-303", VehicleType.VAN, 1000.0, null, VehicleStatus.IN_ROUTE), VehicleFit.UNAVAILABLE),
        ),
        selectedId = vehicle, onSelect = { vehicle = it }, weightKg = 2500.0, volumeM3 = 20.0, cargoType = CargoType.PERISHABLE,
    )
    LogyxButton("Abrir hoja con motivo", { sheet = true }, variant = ButtonVariant.Destructive, size = ButtonSize.Medium)
    if (sheet) {
        LogyxReasonSheet(
            title = "¿Cancelar el envío?", reasonLabel = "Motivo", value = reason, onValueChange = { reason = it },
            confirmLabel = "Cancelar envío", onConfirm = { sheet = false }, onDismiss = { sheet = false },
            message = "La otra parte recibirá el motivo y la solicitud quedará cancelada.",
            error = if (reason.any { it.isDigit() }) "El texto contiene datos de contacto. Coordina todo por el chat de OnCargo." else null,
        )
    }
}

@Composable
private fun Phase3() {
    var stars by remember { mutableStateOf(4) }
    var draft by remember { mutableStateOf("") }
    val pad = pe.logyx.core.designsystem.components.rememberSignaturePadState()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrustScoreBadge(92.0); TrustScoreBadge(77.8); TrustScoreBadge(61.0)
    }
    StarRating(stars, onValueChange = { stars = it })
    RatingRow("Puntualidad", 4.5)
    ChatBubble("¿La carga requiere refrigeración?", mine = false, time = "10:02", sender = "Sur Andino")
    ChatBubble("Sí, entre 2 y 4 °C.", mine = true, time = "10:03")
    ChatBubble("Contraoferta enviada", mine = false, time = "10:10", system = true, price = 1700.0)
    ChatComposer(draft, { draft = it }, {}, error = if (draft.length > 3) "El mensaje contiene datos de contacto. Coordina todo por el chat de OnCargo." else null)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EvidenceTile("Foto 1", Modifier.weight(1f), status = TileStatus.Uploaded)
        EvidenceTile("Foto 2", Modifier.weight(1f), status = TileStatus.Uploading, progress = 0.4f)
        EvidenceTile("Foto 3", Modifier.weight(1f), status = TileStatus.Failed)
        EvidenceTile("Guía", Modifier.weight(1f), pdf = true)
    }
    SignaturePad(pad)
}

@Preview(showBackground = true, heightDp = 2400)
@Composable
private fun CatalogPreview() {
    LogyxTheme { UiCatalogScreen({}) }
}
