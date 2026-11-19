package pe.logyx.feature.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.RequestsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.designsystem.components.moneyToInput
import pe.logyx.core.designsystem.components.parseMoney
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.CreateRequestBody
import pe.logyx.core.model.PlaceKind
import pe.logyx.core.model.PlaceView
import pe.logyx.core.model.QuoteView
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.nextWeekPickup
import pe.logyx.core.network.ApiException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Límites de los `@Size` del backend (`CreateRequest`). */
const val MAX_ADDRESS = 200
const val MAX_CARGO_DESCRIPTION = 300
const val MAX_REFERENCE = 40
const val MAX_CONTACT_NAME = 120
const val MAX_CONTACT_PHONE = 20
const val MAX_INSTRUCTIONS = 300
const val MAX_WEIGHT_KG = 40_000.0

private val LIMA: ZoneId = ZoneId.of("America/Lima")
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Un extremo de la ruta (recojo o entrega): dirección, contacto privado, ventana e instrucciones. */
data class StopForm(
    val address: String = "",
    val placeId: String? = null,
    val contactName: String = "",
    val contactPhone: String = "",
    val windowFrom: LocalTime? = null,
    val windowTo: LocalTime? = null,
    val instructions: String = "",
)

data class NewRequestForm(
    val origin: CityItem? = null,
    val destination: CityItem? = null,
    val pickup: StopForm = StopForm(),
    val delivery: StopForm = StopForm(),
    val cargoType: CargoType = CargoType.GENERAL,
    val description: String = "",
    val weightText: String = "",
    val volumeText: String = "",
    val date: LocalDate? = null,
    val reference: String = "",
    val declaredText: String = "",
    val invitePreferred: Boolean = false,
) {
    val weightKg: Double? get() = weightText.replace(",", "").toDoubleOrNull()
    val volumeM3: Double? get() = volumeText.replace(",", "").toDoubleOrNull()

    /** Hay datos para cotizar: ruta distinta y peso válido. */
    val quotable: Boolean get() = origin != null && destination != null && origin.code != destination.code && (weightKg ?: 0.0) > 0
}

/** Campos del formulario con error; la clave es el `field` del contrato (el mismo que devuelve la API). */
typealias RequestFormErrors = Map<String, UiText>

/** Datos de contacto de 9 dígitos (con +51 opcional), igual que `PeruPhone` del backend. */
fun normalizePhone(text: String): String? {
    val digits = text.filter { it.isDigit() }
    val national = if (digits.length == 11 && digits.startsWith("51")) digits.drop(2) else digits
    return national.takeIf { it.length == 9 }
}

/**
 * Validación local del formulario (la API revalida todo: RN07 en direcciones/descripción, ventanas, valor declarado).
 * Las claves son los `field` del contrato para que un error de la API caiga en el mismo campo.
 */
fun validateNewRequest(f: NewRequestForm, today: LocalDate): RequestFormErrors {
    val e = linkedMapOf<String, UiText>()
    if (f.origin == null) e["originCity"] = UiText.of(R.string.requests_new_err_origin)
    if (f.destination == null) e["destinationCity"] = UiText.of(R.string.requests_new_err_destination)
    else if (f.origin != null && f.origin.code == f.destination.code) e["destinationCity"] = UiText.of(R.string.requests_new_err_same_city)
    if (f.pickup.address.trim().length < 3) e["originAddress"] = UiText.of(R.string.requests_new_err_pickup_address)
    if (f.delivery.address.trim().length < 3) e["destinationAddress"] = UiText.of(R.string.requests_new_err_delivery_address)
    if (f.description.trim().length < 3) e["cargoDescription"] = UiText.of(R.string.requests_new_err_description)
    val kg = f.weightKg
    if (kg == null || kg <= 0) e["weightKg"] = UiText.of(R.string.requests_new_err_weight)
    else if (kg > MAX_WEIGHT_KG) e["weightKg"] = UiText.of(R.string.requests_new_err_weight_max)
    if (f.volumeText.isNotBlank() && (f.volumeM3 ?: 0.0) <= 0) e["volumeM3"] = UiText.of(R.string.requests_new_err_volume)
    val date = f.date
    if (date == null) e["pickupDate"] = UiText.of(R.string.requests_new_err_date)
    else if (date.isBefore(today)) e["pickupDate"] = UiText.of(R.string.requests_new_err_date_past)
    if (f.declaredText.isNotBlank() && (parseMoney(f.declaredText) ?: 0.0) <= 0) e["declaredValue"] = UiText.of(R.string.requests_new_err_declared)
    for ((prefix, s) in listOf("pickup" to f.pickup, "delivery" to f.delivery)) {
        if ((s.windowFrom == null) != (s.windowTo == null)) e["${prefix}WindowTo"] = UiText.of(R.string.requests_new_err_window_incomplete)
        else if (s.windowFrom != null && s.windowTo != null && !s.windowFrom.isBefore(s.windowTo)) e["${prefix}WindowTo"] = UiText.of(R.string.requests_new_err_window_order)
        if (s.contactPhone.isNotBlank() && normalizePhone(s.contactPhone) == null) e["${prefix}ContactPhone"] = UiText.of(R.string.requests_new_err_phone)
    }
    return e
}

fun NewRequestForm.toBody(): CreateRequestBody {
    fun String.orNull() = trim().takeIf { it.isNotEmpty() }
    return CreateRequestBody(
        originCity = origin!!.code, originAddress = pickup.address.trim(),
        destinationCity = destination!!.code, destinationAddress = delivery.address.trim(),
        cargoType = cargoType, cargoDescription = description.trim(), weightKg = weightKg!!, volumeM3 = volumeM3?.takeIf { it > 0 },
        pickupDate = date!!.toString(), customerReference = reference.orNull(), declaredValue = parseMoney(declaredText)?.takeIf { it > 0 },
        pickupContactName = pickup.contactName.orNull(), pickupContactPhone = pickup.contactPhone.orNull()?.let { normalizePhone(it) ?: it },
        pickupWindowFrom = pickup.windowFrom?.format(HHMM), pickupWindowTo = pickup.windowTo?.format(HHMM), pickupInstructions = pickup.instructions.orNull(),
        deliveryContactName = delivery.contactName.orNull(), deliveryContactPhone = delivery.contactPhone.orNull()?.let { normalizePhone(it) ?: it },
        deliveryWindowFrom = delivery.windowFrom?.format(HHMM), deliveryWindowTo = delivery.windowTo?.format(HHMM), deliveryInstructions = delivery.instructions.orNull(),
        originPlaceId = pickup.placeId, destinationPlaceId = delivery.placeId, invitePreferred = invitePreferred.takeIf { it },
    )
}

/** Formulario precargado desde una solicitud (Duplicar): todo salvo la fecha, que sugiere el mismo día de la semana siguiente. */
fun formFrom(r: RequestView, cities: List<CityItem>, today: LocalDate): NewRequestForm = NewRequestForm(
    origin = cities.firstOrNull { it.code == r.originCity }, destination = cities.firstOrNull { it.code == r.destinationCity },
    pickup = StopForm(r.originAddress.orEmpty(), null, r.pickupContactName.orEmpty(), r.pickupContactPhone.orEmpty(), r.pickupWindowFrom.toTime(), r.pickupWindowTo.toTime(), r.pickupInstructions.orEmpty()),
    delivery = StopForm(r.destinationAddress.orEmpty(), null, r.deliveryContactName.orEmpty(), r.deliveryContactPhone.orEmpty(), r.deliveryWindowFrom.toTime(), r.deliveryWindowTo.toTime(), r.deliveryInstructions.orEmpty()),
    cargoType = r.cargoType, description = r.cargoDescription.orEmpty(), weightText = moneyToInput(r.weightKg), volumeText = moneyToInput(r.volumeM3),
    date = LocalDate.parse(r.nextWeekPickup(today)), reference = r.customerReference.orEmpty(), declaredText = moneyToInput(r.declaredValue),
)

private fun String?.toTime(): LocalTime? = this?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

data class NewRequestUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val cities: List<CityItem> = emptyList(),
    val places: List<PlaceView> = emptyList(),
    val form: NewRequestForm = NewRequestForm(),
    val errors: RequestFormErrors = emptyMap(),
    val quote: QuoteView? = null,
    val quoting: Boolean = false,
    val quoteError: String? = null,
    val submitting: Boolean = false,
    /** Código de la solicitud de la que se copió el formulario (Duplicar); nulo si es una solicitud nueva. */
    val duplicatedFrom: String? = null,
    val contactsOpen: Boolean = false,
    val createdId: String? = null,
    val message: UiMessage? = null,
)

/**
 * «Nueva solicitud» de la PYME (roadmap 2.16): ruta, carga, fecha, cotización en vivo, libreta de direcciones y contactos
 * privados. [fromRequestId] precarga una solicitud existente (Duplicar). La `Idempotency-Key` es una por pantalla: un toque
 * doble o un reintento tras fallar la red no crean dos solicitudes.
 */
class NewRequestViewModel(
    private val fromRequestId: String?,
    private val repo: RequestsRepository,
    private val catalog: CatalogRepository,
    private val today: () -> LocalDate = { LocalDate.now(LIMA) },
    private val newKey: () -> String = { UUID.randomUUID().toString() },
    private val quoteDelayMs: Long = 400,
) : ViewModel() {
    private val _state = MutableStateFlow(NewRequestUiState())
    val state: StateFlow<NewRequestUiState> = _state.asStateFlow()
    private val idempotencyKey = newKey()
    private var quoteJob: Job? = null

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val cities = async { catalog.cities() }
            val places = async { repo.places().getOrDefault(emptyList()) }          // la libreta es opcional: si falla, se escribe a mano
            val source = async { fromRequestId?.let { repo.detail(it).getOrNull() } }
            cities.await().fold(
                onSuccess = { list ->
                    val copied = source.await()?.let { formFrom(it, list, today()) }
                    _state.update { s ->
                        s.copy(
                            loading = false, cities = list, places = places.await(),
                            form = copied?.takeIf { s.form == NewRequestForm() } ?: s.form,
                            duplicatedFrom = source.await()?.let { it.code ?: it.id },
                            contactsOpen = copied?.let { f -> listOf(f.pickup, f.delivery).any { c -> c.contactName.isNotBlank() || c.contactPhone.isNotBlank() || c.instructions.isNotBlank() || c.windowFrom != null } } ?: false,
                        )
                    }
                    requote()
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }

    private fun edit(vararg clear: String, f: (NewRequestForm) -> NewRequestForm) =
        _state.update { it.copy(form = f(it.form), errors = it.errors - clear.toSet()) }

    fun onOrigin(c: CityItem?) {
        edit("originCity", "destinationCity") { it.copy(origin = c, pickup = it.pickup.copy(placeId = null)) }
        requote()
    }
    fun onDestination(c: CityItem?) {
        edit("destinationCity") { it.copy(destination = c, delivery = it.delivery.copy(placeId = null)) }
        requote()
    }
    fun onCargoType(t: CargoType) { edit { it.copy(cargoType = t) }; requote() }
    fun onWeight(text: String) { edit("weightKg") { it.copy(weightText = text.filter { c -> c.isDigit() || c == '.' }.take(8)) }; requote() }
    fun onVolume(text: String) = edit("volumeM3") { it.copy(volumeText = text.filter { c -> c.isDigit() || c == '.' }.take(6)) }
    fun onDate(d: LocalDate?) = edit("pickupDate") { it.copy(date = d) }
    fun onDescription(t: String) = edit("cargoDescription") { it.copy(description = t.take(MAX_CARGO_DESCRIPTION)) }
    fun onReference(t: String) = edit("customerReference") { it.copy(reference = t.take(MAX_REFERENCE)) }
    fun onDeclared(t: String) = edit("declaredValue") { it.copy(declaredText = t) }
    fun onInvitePreferred(v: Boolean) = edit { it.copy(invitePreferred = v) }
    fun toggleContacts() = _state.update { it.copy(contactsOpen = !it.contactsOpen) }

    /** Dirección escrita a mano: suelta la dirección guardada elegida (ya no es esa). */
    fun onAddress(pickup: Boolean, t: String) = edit(if (pickup) "originAddress" else "destinationAddress") {
        if (pickup) it.copy(pickup = it.pickup.copy(address = t.take(MAX_ADDRESS), placeId = null)) else it.copy(delivery = it.delivery.copy(address = t.take(MAX_ADDRESS), placeId = null))
    }

    /** Elegir una dirección guardada copia dirección, contacto, ventana e instrucciones a ese extremo. */
    fun onPlace(pickup: Boolean, place: PlaceView?) {
        if (place == null) { edit { f -> if (pickup) f.copy(pickup = f.pickup.copy(placeId = null)) else f.copy(delivery = f.delivery.copy(placeId = null)) }; return }
        val stop = StopForm(
            address = place.address, placeId = place.id, contactName = place.contactName.orEmpty(), contactPhone = place.contactPhone.orEmpty(),
            windowFrom = place.windowFrom.toTime(), windowTo = place.windowTo.toTime(), instructions = place.instructions.orEmpty(),
        )
        val prefix = if (pickup) "pickup" else "delivery"
        edit(if (pickup) "originAddress" else "destinationAddress", "${prefix}WindowTo", "${prefix}ContactPhone") { f ->
            val city = _state.value.cities.firstOrNull { it.code == place.city }
            if (pickup) f.copy(pickup = stop, origin = city ?: f.origin) else f.copy(delivery = stop, destination = city ?: f.destination)
        }
        _state.update { it.copy(contactsOpen = it.contactsOpen || stop.contactName.isNotBlank() || stop.contactPhone.isNotBlank() || stop.windowFrom != null || stop.instructions.isNotBlank()) }
        requote()
    }

    /** Direcciones guardadas de la ciudad elegida que sirven para ese extremo. */
    fun placesFor(pickup: Boolean): List<PlaceView> {
        val s = _state.value
        val city = (if (pickup) s.form.origin else s.form.destination)?.code
        val wanted = if (pickup) PlaceKind.PICKUP else PlaceKind.DELIVERY
        return s.places.filter { (it.kind == PlaceKind.BOTH || it.kind == wanted) && (city == null || it.city == city) }
    }

    fun onContact(pickup: Boolean, name: String? = null, phone: String? = null, instructions: String? = null, from: LocalTime? = null, to: LocalTime? = null, clearWindow: Boolean = false) {
        val prefix = if (pickup) "pickup" else "delivery"
        edit("${prefix}ContactPhone", "${prefix}WindowTo") { f ->
            val cur = if (pickup) f.pickup else f.delivery
            val next = cur.copy(
                contactName = name?.take(MAX_CONTACT_NAME) ?: cur.contactName, contactPhone = phone?.take(MAX_CONTACT_PHONE) ?: cur.contactPhone,
                instructions = instructions?.take(MAX_INSTRUCTIONS) ?: cur.instructions,
                windowFrom = if (clearWindow) null else from ?: cur.windowFrom, windowTo = if (clearWindow) null else to ?: cur.windowTo,
                placeId = cur.placeId,
            )
            if (pickup) f.copy(pickup = next) else f.copy(delivery = next)
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /** Cotización en vivo con espera de [quoteDelayMs]: solo la última petición cuenta. */
    private fun requote() {
        quoteJob?.cancel()
        val f = _state.value.form
        if (!f.quotable) { _state.update { it.copy(quote = null, quoting = false, quoteError = null) }; return }
        _state.update { it.copy(quoting = true) }
        quoteJob = viewModelScope.launch {
            delay(quoteDelayMs)
            repo.quote(f.origin!!.code, f.destination!!.code, f.weightKg!!, f.cargoType).fold(
                onSuccess = { q -> _state.update { it.copy(quote = q, quoting = false, quoteError = null) } },
                onFailure = { e -> _state.update { it.copy(quote = null, quoting = false, quoteError = e.userMessage()) } },
            )
        }
    }

    fun submit() {
        val s = _state.value
        if (s.submitting) return
        val errors = validateNewRequest(s.form, today())
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, contactsOpen = it.contactsOpen || errors.keys.any { k -> k.startsWith("pickup") || k.startsWith("delivery") }) }
            return
        }
        _state.update { it.copy(submitting = true, errors = emptyMap()) }
        viewModelScope.launch {
            repo.create(s.form.toBody(), idempotencyKey).fold(
                onSuccess = { r -> _state.update { it.copy(submitting = false, createdId = r.id) } },
                onFailure = { e ->
                    val field = (e as? ApiException)?.field
                    _state.update {
                        if (field != null && field in KNOWN_FIELDS) {
                            it.copy(submitting = false, errors = it.errors + (field to UiText.Raw(e.userMessage())),
                                contactsOpen = it.contactsOpen || field.startsWith("pickup") || field.startsWith("delivery"))
                        } else it.copy(submitting = false, message = UiMessage.error(e.userMessage()))
                    }
                },
            )
        }
    }

    fun navigationHandled() = _state.update { it.copy(createdId = null) }

    private companion object {
        val KNOWN_FIELDS = setOf(
            "originCity", "destinationCity", "originAddress", "destinationAddress", "cargoDescription", "weightKg", "volumeM3", "pickupDate",
            "customerReference", "declaredValue", "pickupContactName", "pickupContactPhone", "pickupWindowFrom", "pickupWindowTo", "pickupInstructions",
            "deliveryContactName", "deliveryContactPhone", "deliveryWindowFrom", "deliveryWindowTo", "deliveryInstructions",
        )
    }
}
