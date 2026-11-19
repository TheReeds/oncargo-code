package pe.logyx.feature.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxCombobox
import pe.logyx.core.designsystem.components.LogyxDateField
import pe.logyx.core.designsystem.components.LogyxDropdown
import pe.logyx.core.designsystem.components.LogyxFilterChip
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.LogyxTextField
import pe.logyx.core.designsystem.components.LogyxTimeField
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.designsystem.components.MoneyField
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.asString
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.PlaceView

/** Actions del formulario «Nueva solicitud»; agrupadas para no pasar 25 lambdas. */
class NewRequestActions(
    val onRetry: () -> Unit = {},
    val onOrigin: (pe.logyx.core.model.CityItem?) -> Unit = {},
    val onDestination: (pe.logyx.core.model.CityItem?) -> Unit = {},
    val onAddress: (pickup: Boolean, String) -> Unit = { _, _ -> },
    val onPlace: (pickup: Boolean, PlaceView?) -> Unit = { _, _ -> },
    val onCargoType: (CargoType) -> Unit = {},
    val onWeight: (String) -> Unit = {},
    val onVolume: (String) -> Unit = {},
    val onDate: (java.time.LocalDate) -> Unit = {},
    val onDescription: (String) -> Unit = {},
    val onReference: (String) -> Unit = {},
    val onDeclared: (String) -> Unit = {},
    val onInvitePreferred: (Boolean) -> Unit = {},
    val onToggleContacts: () -> Unit = {},
    val onContact: (pickup: Boolean, name: String?, phone: String?, instructions: String?, from: java.time.LocalTime?, to: java.time.LocalTime?, clearWindow: Boolean) -> Unit = { _, _, _, _, _, _, _ -> },
    val onSubmit: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val placesFor: (pickup: Boolean) -> List<PlaceView> = { emptyList() },
)

@Composable
fun NewRequestRoute(
    fromRequestId: String?,
    onBack: () -> Unit,
    onPublished: (requestId: String) -> Unit,
    viewModel: NewRequestViewModel = koinViewModel { parametersOf(fromRequestId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdId) { state.createdId?.let { viewModel.navigationHandled(); onPublished(it) } }
    NewRequestScreen(
        state, onBack,
        NewRequestActions(
            viewModel::load, viewModel::onOrigin, viewModel::onDestination, viewModel::onAddress, viewModel::onPlace, viewModel::onCargoType,
            viewModel::onWeight, viewModel::onVolume, viewModel::onDate, viewModel::onDescription, viewModel::onReference, viewModel::onDeclared,
            viewModel::onInvitePreferred, viewModel::toggleContacts,
            { pickup, name, phone, instr, from, to, clear -> viewModel.onContact(pickup, name, phone, instr, from, to, clear) },
            viewModel::submit, viewModel::onMessageShown, viewModel::placesFor,
        ),
    )
}

@Composable
fun NewRequestScreen(state: NewRequestUiState, onBack: () -> Unit, actions: NewRequestActions = NewRequestActions()) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, actions.onMessageShown)
    LogyxScreen(stringResource(R.string.requests_new_title), onBack = onBack, snackbar = snackbar) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.fillMaxSize(), count = 3)
                state.error != null -> ErrorState(state.error, actions.onRetry)
                else -> Form(state, actions)
            }
        }
    }
}

@Composable
private fun Form(state: NewRequestUiState, a: NewRequestActions) {
    val f = state.form
    val e = state.errors
    // `asString()` es @Composable: se resuelven los textos aquí (mapValues es inline) y los campos solo leen el mapa.
    val messages = e.mapValues { (_, v) -> v.asString() }
    fun err(k: String): String? = messages[k]
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.duplicatedFrom?.let {
            AlertBanner(stringResource(R.string.requests_new_duplicate_text, it), kind = MessageKind.Info, title = stringResource(R.string.requests_new_duplicate_title))
        }
        Section(R.string.requests_new_section_route)
        LogyxCombobox(stringResource(R.string.requests_new_origin), state.cities, f.origin, a.onOrigin, { it.name }, error = err("originCity"))
        StopFields(true, state, a, ::err)
        LogyxCombobox(
            stringResource(R.string.requests_new_destination), state.cities.filter { it.code != f.origin?.code }, f.destination, a.onDestination, { it.name },
            error = err("destinationCity"),
        )
        StopFields(false, state, a, ::err)

        Section(R.string.requests_new_section_cargo)
        val cargoTypes = CargoType.entries
        val cargoLabels = cargoTypes.associateWith { it.label }
        LogyxDropdown(stringResource(R.string.requests_new_cargo_type), cargoTypes, f.cargoType, a.onCargoType, { cargoLabels.getValue(it) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogyxTextField(
                f.weightText, a.onWeight, stringResource(R.string.requests_new_weight), Modifier.weight(1f), suffix = "kg", error = err("weightKg"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            LogyxTextField(
                f.volumeText, a.onVolume, stringResource(R.string.requests_new_volume), Modifier.weight(1f), suffix = "m³", optional = true,
                error = err("volumeM3"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        LogyxDateField(
            stringResource(R.string.requests_new_date), f.date, a.onDate, error = err("pickupDate"),
            hint = f.date?.let { stringResource(R.string.requests_new_date_hint) },
        )
        LogyxTextField(
            f.description, a.onDescription, stringResource(R.string.requests_new_description), singleLine = false, minLines = 2,
            error = err("cargoDescription"), hint = stringResource(R.string.requests_new_description_hint, MAX_CARGO_DESCRIPTION),
        )
        LogyxTextField(
            f.reference, a.onReference, stringResource(R.string.requests_new_reference), optional = true, error = err("customerReference"),
            hint = stringResource(R.string.requests_new_reference_hint),
        )
        MoneyField(f.declaredText, a.onDeclared, stringResource(R.string.requests_new_declared), optional = true, error = err("declaredValue"), hint = stringResource(R.string.requests_new_declared_hint))

        LogyxButton(
            stringResource(if (state.contactsOpen) R.string.requests_new_contacts_hide else R.string.requests_new_contacts_show), a.onToggleContacts,
            variant = ButtonVariant.Secondary, size = ButtonSize.Medium,
        )
        if (state.contactsOpen) {
            AlertBanner(stringResource(R.string.requests_new_contacts_private), kind = MessageKind.Info)
            ContactFields(true, f.pickup, a, ::err)
            ContactFields(false, f.delivery, a, ::err)
        }

        QuoteCard(state)
        LogyxFilterChip(stringResource(R.string.requests_new_invite), f.invitePreferred, { a.onInvitePreferred(!f.invitePreferred) })
        LogyxButton(
            stringResource(if (state.submitting) R.string.requests_new_publishing else R.string.requests_new_publish), a.onSubmit,
            variant = ButtonVariant.Money, loading = state.submitting, requiresOperate = true,
        )
    }
}

@Composable
private fun Section(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
}

/** Dirección de un extremo, con «Mis direcciones» (libreta) filtrada por ciudad y tipo cuando hay guardadas. */
@Composable
private fun StopFields(pickup: Boolean, state: NewRequestUiState, a: NewRequestActions, err: (String) -> String?) {
    val stop = if (pickup) state.form.pickup else state.form.delivery
    val places = a.placesFor(pickup)
    if (places.isNotEmpty()) {
        LogyxCombobox(
            stringResource(R.string.requests_new_saved_places), places, places.firstOrNull { it.id == stop.placeId }, { a.onPlace(pickup, it) }, { it.label },
            optional = true, clearable = true, optionSupporting = { "${cityLabel(it.city)} · ${it.address}" },
            hint = stringResource(R.string.requests_new_saved_places_hint),
        )
    }
    LogyxTextField(
        stop.address, { a.onAddress(pickup, it) },
        stringResource(if (pickup) R.string.requests_new_pickup_address else R.string.requests_new_delivery_address),
        error = err(if (pickup) "originAddress" else "destinationAddress"),
        placeholder = stringResource(if (pickup) R.string.requests_new_pickup_placeholder else R.string.requests_new_delivery_placeholder),
    )
}

/** Contacto, ventana horaria e instrucciones de un extremo: privados hasta que adjudiques (RN07 tras el match). */
@Composable
private fun ContactFields(pickup: Boolean, s: StopForm, a: NewRequestActions, err: (String) -> String?) {
    val p = if (pickup) "pickup" else "delivery"
    Text(stringResource(if (pickup) R.string.requests_new_contact_pickup else R.string.requests_new_contact_delivery), style = MaterialTheme.typography.titleSmall)
    LogyxTextField(s.contactName, { a.onContact(pickup, it, null, null, null, null, false) }, stringResource(R.string.requests_new_contact_name), optional = true, error = err("${p}ContactName"))
    LogyxTextField(
        s.contactPhone, { a.onContact(pickup, null, it, null, null, null, false) }, stringResource(R.string.requests_new_contact_phone), optional = true,
        error = err("${p}ContactPhone"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LogyxTimeField(stringResource(R.string.requests_new_window_from), s.windowFrom, { a.onContact(pickup, null, null, null, it, null, false) }, Modifier.weight(1f), optional = true)
        LogyxTimeField(stringResource(R.string.requests_new_window_to), s.windowTo, { a.onContact(pickup, null, null, null, null, it, false) }, Modifier.weight(1f), optional = true, error = err("${p}WindowTo"))
    }
    if (s.windowFrom != null || s.windowTo != null) {
        LogyxButton(stringResource(R.string.requests_new_window_clear), { a.onContact(pickup, null, null, null, null, null, true) }, variant = ButtonVariant.Ghost, size = ButtonSize.Medium, fullWidth = false)
    }
    LogyxTextField(
        s.instructions, { a.onContact(pickup, null, null, it, null, null, false) }, stringResource(R.string.requests_new_instructions), optional = true,
        singleLine = false, minLines = 2, error = err("${p}Instructions"),
    )
}

/** Cotización en vivo: precio sugerido y piso de la subasta (RN03) a medida que se completa la ruta y el peso. */
@Composable
private fun QuoteCard(state: NewRequestUiState) {
    LogyxCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.requests_new_quote_title), style = MaterialTheme.typography.titleSmall)
            val q = state.quote
            when {
                state.quoteError != null -> Text(state.quoteError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                !state.form.quotable -> Text(stringResource(R.string.requests_new_quote_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                q == null && state.quoting -> Text(stringResource(R.string.requests_new_quote_loading), style = MaterialTheme.typography.bodyMedium)
                q != null -> {
                    Text(
                        Fmt.money(q.suggestedPrice), style = pe.logyx.core.designsystem.LogyxText.moneyLarge,
                        color = pe.logyx.core.designsystem.LogyxTheme.colors.moneyText,
                    )
                    Text(stringResource(R.string.requests_new_quote_floor, Fmt.money(q.priceFloor), Fmt.km(q.distanceKm)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
