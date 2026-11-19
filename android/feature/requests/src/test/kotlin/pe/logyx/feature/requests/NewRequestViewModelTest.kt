package pe.logyx.feature.requests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.RequestsRepository
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.CreateRequestBody
import pe.logyx.core.model.PlaceKind
import pe.logyx.core.model.PlaceView
import pe.logyx.core.model.PublishedFromTemplate
import pe.logyx.core.model.QuoteView
import pe.logyx.core.model.PublicOrganization
import pe.logyx.core.model.RequestStatus
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.TemplateScheduleView
import pe.logyx.core.model.TemplateView
import pe.logyx.core.model.nextWeekPickup
import pe.logyx.core.network.ApiException
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val LIMA = CityItem("LIMA", "Lima", -12.0, -77.0)
private val JULIACA = CityItem("JULIACA", "Juliaca", -15.5, -70.1)
private val TODAY = LocalDate.of(2026, 11, 18)          // miércoles

private class NewReqCatalog : CatalogRepository {
    override suspend fun cities() = Result.success(listOf(LIMA, JULIACA))
    override suspend fun organization(id: String) = Result.failure<PublicOrganization>(IllegalStateException())
}

private class NewReqRepo(
    var created: Result<RequestView> = Result.success(source.copy(id = "new1", code = "OC-NEW01")),
    var quote: Result<QuoteView> = Result.success(QuoteView(distanceKm = 1250.0, suggestedPrice = 2400.0, priceFloor = 1800.0)),
    var places: Result<List<PlaceView>> = Result.success(emptyList()),
    var templates: Result<List<TemplateView>> = Result.success(emptyList()),
) : RequestsRepository {
    var createdBody: CreateRequestBody? = null
    var createdKeys = mutableListOf<String?>()
    var quotes = 0
    var published: Triple<String, String, String?>? = null
    var publishResult: Result<PublishedFromTemplate> = Result.success(PublishedFromTemplate("req9", "OC-TPL01", "2026-11-25"))
    override suspend fun mine() = Result.success(emptyList<RequestView>())
    override suspend fun detail(id: String) = Result.success(source)
    override suspend fun bids(requestId: String) = Result.success(emptyList<pe.logyx.core.model.BidView>())
    override suspend fun acceptBid(bidId: String) = Result.failure<pe.logyx.core.model.AcceptResult>(UnsupportedOperationException())
    override suspend fun cancel(requestId: String, reason: String?) = Result.failure<RequestView>(UnsupportedOperationException())
    override suspend fun create(body: CreateRequestBody, idempotencyKey: String?): Result<RequestView> { createdBody = body; createdKeys += idempotencyKey; return created }
    override suspend fun quote(origin: String, destination: String, weightKg: Double, cargoType: CargoType): Result<QuoteView> { quotes++; return quote }
    override suspend fun places() = places
    override suspend fun templates() = templates
    override suspend fun publishTemplate(templateId: String, pickupDate: String, idempotencyKey: String?): Result<PublishedFromTemplate> {
        published = Triple(templateId, pickupDate, idempotencyKey); return publishResult
    }
}

private val source = RequestView(
    id = "src1", originCity = "LIMA", originAddress = "Av. Argentina 1234, Callao", destinationCity = "JULIACA", destinationAddress = "Jr. Lima 456",
    cargoType = CargoType.FRAGILE, cargoDescription = "Vidrio templado", weightKg = 2500.0, pickupDate = "2026-11-19", status = RequestStatus.MATCHED, code = "OC-SRC01",
    customerReference = "OC 77", pickupContactName = "María Quispe", pickupContactPhone = "987654321", pickupWindowFrom = "08:00", pickupWindowTo = "12:00",
)

private fun filled() = NewRequestForm(
    origin = LIMA, destination = JULIACA, pickup = StopForm(address = "Av. Argentina 1234, Callao"), delivery = StopForm(address = "Jr. Lima 456, Juliaca"),
    description = "Rollos de tela", weightText = "3200", date = TODAY.plusDays(3),
)

@OptIn(ExperimentalCoroutinesApi::class)
class NewRequestViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: NewReqRepo, from: String? = null) = NewRequestViewModel(from, repo, NewReqCatalog(), today = { TODAY }, newKey = { "key-1" }, quoteDelayMs = 0)

    @Test fun `la validacion local usa los field del contrato y detecta lo que falta`() {
        val e = validateNewRequest(NewRequestForm(), TODAY)
        assertEquals(setOf("originCity", "destinationCity", "originAddress", "destinationAddress", "cargoDescription", "weightKg", "pickupDate"), e.keys)
        assertTrue(validateNewRequest(filled(), TODAY).isEmpty())
    }

    @Test fun `valida peso maximo, fecha pasada, valor declarado, ventana y telefono`() {
        val f = filled().copy(
            weightText = "50000", date = TODAY.minusDays(1), declaredText = "0",
            pickup = StopForm(address = "Av. Argentina 1234", windowFrom = LocalTime.of(14, 0), windowTo = LocalTime.of(9, 0), contactPhone = "12345"),
            delivery = StopForm(address = "Jr. Lima 456", windowFrom = LocalTime.of(8, 0)),
        )
        val e = validateNewRequest(f, TODAY)
        assertEquals(setOf("weightKg", "pickupDate", "declaredValue", "pickupWindowTo", "pickupContactPhone", "deliveryWindowTo"), e.keys)
        assertEquals(null, validateNewRequest(filled().copy(pickup = StopForm(address = "Av. Argentina 1234", contactPhone = "+51 987 654 321")), TODAY)["pickupContactPhone"])
    }

    @Test fun `el telefono admite el prefijo +51 y se normaliza a 9 digitos`() {
        assertEquals("987654321", normalizePhone("+51 987-654-321"))
        assertEquals("987654321", normalizePhone("987 654 321"))
        assertNull(normalizePhone("98765"))
    }

    @Test fun `duplicar precarga todo salvo la fecha, que va al mismo dia de la semana siguiente`() = runTest(dispatcher) {
        val v = vm(NewReqRepo(), from = "src1")
        advanceUntilIdle()
        val s = v.state.value
        assertEquals("OC-SRC01", s.duplicatedFrom)
        assertEquals(LIMA, s.form.origin)
        assertEquals("Vidrio templado", s.form.description)
        assertEquals("2500", s.form.weightText)
        assertEquals("María Quispe", s.form.pickup.contactName)
        assertEquals(LocalTime.of(8, 0), s.form.pickup.windowFrom)
        assertEquals("OC 77", s.form.reference)
        assertEquals(LocalDate.of(2026, 11, 26), s.form.date)                // jueves 19 → jueves 26
        assertTrue(s.contactsOpen, "hay contactos: la sección se abre")
        assertNotNull(s.quote, "la cotización se calcula al abrir")
    }

    @Test fun `nextWeekPickup nunca devuelve una fecha pasada`() {
        assertEquals("2026-11-26", source.nextWeekPickup(TODAY))
        assertEquals("2026-11-18", source.copy(pickupDate = null).nextWeekPickup(TODAY))
        assertEquals("2026-11-18", source.copy(pickupDate = "2026-10-21").nextWeekPickup(TODAY))     // miércoles viejo → hoy mismo
    }

    @Test fun `la cotizacion en vivo espera y solo cuenta la ultima peticion`() = runTest(dispatcher) {
        val repo = NewReqRepo()
        val v = NewRequestViewModel(null, repo, NewReqCatalog(), today = { TODAY }, newKey = { "k" }, quoteDelayMs = 400)
        advanceUntilIdle()
        v.onOrigin(LIMA); v.onDestination(JULIACA)
        v.onWeight("1"); advanceTimeBy(100); v.onWeight("12"); advanceTimeBy(100); v.onWeight("120")
        advanceUntilIdle()
        assertEquals(1, repo.quotes, "las tres ediciones seguidas hacen una sola cotización")
        assertEquals(2400.0, v.state.value.quote?.suggestedPrice)
        v.onDestination(null)
        advanceUntilIdle()
        assertNull(v.state.value.quote, "sin destino no hay cotización")
    }

    @Test fun `publicar manda el cuerpo del contrato con una clave de idempotencia por pantalla`() = runTest(dispatcher) {
        val repo = NewReqRepo()
        val v = vm(repo)
        advanceUntilIdle()
        v.onOrigin(LIMA); v.onDestination(JULIACA); v.onAddress(true, "Av. Argentina 1234, Callao"); v.onAddress(false, "Jr. Lima 456, Juliaca")
        v.onDescription("Rollos de tela"); v.onWeight("3200"); v.onDate(TODAY.plusDays(3)); v.onReference("OC 4500123")
        v.onContact(true, name = "María Quispe", phone = "+51 987 654 321", from = LocalTime.of(8, 0), to = LocalTime.of(12, 30))
        v.submit(); advanceUntilIdle()
        val b = repo.createdBody!!
        assertEquals("LIMA", b.originCity); assertEquals("JULIACA", b.destinationCity); assertEquals(3200.0, b.weightKg)
        assertEquals(TODAY.plusDays(3).toString(), b.pickupDate)
        assertEquals("OC 4500123", b.customerReference)
        assertEquals("987654321", b.pickupContactPhone)
        assertEquals("08:00", b.pickupWindowFrom); assertEquals("12:30", b.pickupWindowTo)
        assertNull(b.deliveryContactName); assertNull(b.invitePreferred)
        assertEquals(listOf<String?>("key-1"), repo.createdKeys)
        assertEquals("new1", v.state.value.createdId)
        v.navigationHandled()
        assertNull(v.state.value.createdId)
    }

    @Test fun `no se publica con errores y se abre la seccion de contactos si el error es de ahi`() = runTest(dispatcher) {
        val repo = NewReqRepo()
        val v = vm(repo)
        advanceUntilIdle()
        v.submit(); advanceUntilIdle()
        assertNull(repo.createdBody)
        assertTrue(v.state.value.errors.containsKey("originCity"))
        v.onOrigin(LIMA); v.onDestination(JULIACA); v.onAddress(true, "Av. Argentina 1"); v.onAddress(false, "Jr. Lima 456"); v.onDescription("Tela")
        v.onWeight("100"); v.onDate(TODAY); v.onContact(true, phone = "123")
        v.submit(); advanceUntilIdle()
        assertTrue(v.state.value.errors.containsKey("pickupContactPhone"))
        assertTrue(v.state.value.contactsOpen)
        assertNull(repo.createdBody)
    }

    @Test fun `un error de la API con field cae bajo su campo, RN07 incluido, y el resto va en mensaje`() = runTest(dispatcher) {
        val repo = NewReqRepo(created = Result.failure(ApiException(400, "LOGYX-NEG-001", "No incluyas teléfonos ni correos", "cargoDescription")))
        val v = vm(repo)
        advanceUntilIdle()
        v.onOrigin(LIMA); v.onDestination(JULIACA); v.onAddress(true, "Av. Argentina 1234"); v.onAddress(false, "Jr. Lima 456"); v.onDescription("Llámame al 987654321")
        v.onWeight("100"); v.onDate(TODAY)
        v.submit(); advanceUntilIdle()
        assertEquals(pe.logyx.core.designsystem.components.UiText.Raw("No incluyas teléfonos ni correos"), v.state.value.errors["cargoDescription"])
        assertFalse(v.state.value.submitting)
        repo.created = Result.failure(ApiException(403, "LOGYX-ORG-003", "Tu empresa aún no está verificada"))
        v.submit(); advanceUntilIdle()
        assertNotNull(v.state.value.message)
    }

    @Test fun `elegir una direccion guardada copia direccion, contacto y ventana, escribir a mano la suelta`() = runTest(dispatcher) {
        val place = PlaceView("p1", "Almacén Callao", "LIMA", "Av. Industrial 450", contactName = "Rosa Huanca", contactPhone = "912345678",
            windowFrom = "08:00", windowTo = "17:00", instructions = "Portón verde", kind = PlaceKind.PICKUP)
        val other = place.copy(id = "p2", label = "Tienda", city = "JULIACA", kind = PlaceKind.DELIVERY)
        val v = vm(NewReqRepo(places = Result.success(listOf(place, other))))
        advanceUntilIdle()
        v.onOrigin(LIMA)
        assertEquals(listOf("p1"), v.placesFor(true).map { it.id })
        v.onPlace(true, place)
        var s = v.state.value
        assertEquals("p1", s.form.pickup.placeId)
        assertEquals("Av. Industrial 450", s.form.pickup.address)
        assertEquals("Rosa Huanca", s.form.pickup.contactName)
        assertEquals(LocalTime.of(17, 0), s.form.pickup.windowTo)
        assertTrue(s.contactsOpen)
        v.onAddress(true, "Av. Otra 99")
        s = v.state.value
        assertNull(s.form.pickup.placeId)
        v.submit(); advanceUntilIdle()
        assertTrue(v.state.value.errors.isNotEmpty())
    }

    @Test fun `la libreta es opcional - si falla se escribe a mano`() = runTest(dispatcher) {
        val v = vm(NewReqRepo(places = Result.failure(ApiException(500, "X", "Error"))))
        advanceUntilIdle()
        assertFalse(v.state.value.loading)
        assertNull(v.state.value.error)
        assertTrue(v.state.value.places.isEmpty())
    }

    @Test fun `el cuerpo manda la invitacion a preferidos solo si se marca`() = runTest(dispatcher) {
        val repo = NewReqRepo()
        val v = vm(repo)
        advanceUntilIdle()
        v.onOrigin(LIMA); v.onDestination(JULIACA); v.onAddress(true, "Av. Argentina 1234"); v.onAddress(false, "Jr. Lima 456"); v.onDescription("Tela")
        v.onWeight("100"); v.onDate(TODAY); v.onInvitePreferred(true)
        v.submit(); advanceUntilIdle()
        assertEquals(true, repo.createdBody?.invitePreferred)
    }

    // ───────── Plantillas ─────────
    private fun template(id: String = "t1") = TemplateView(
        id, "Quinua semanal", "JULIACA", null, "AREQUIPA", null, CargoType.GENERAL, null, 3000.0,
        schedule = TemplateScheduleView("WEEKLY", 1, 2, nextPickupDate = "2026-11-23"),
    )

    @Test fun `plantillas - lista y publicar ahora con la fecha y una clave por apertura`() = runTest(dispatcher) {
        val repo = NewReqRepo(templates = Result.success(listOf(template())))
        val t = TemplatesViewModel(repo, today = { TODAY }, newKey = { "tk" })
        advanceUntilIdle()
        assertEquals(1, t.state.value.items.size)
        t.askPublish(t.state.value.items.first())
        t.confirmPublish(); advanceUntilIdle()
        assertNotNull(t.state.value.publish?.error, "falta la fecha")
        t.onDate(TODAY.minusDays(1)); t.confirmPublish(); advanceUntilIdle()
        assertNotNull(t.state.value.publish?.error, "fecha pasada")
        t.onDate(TODAY.plusDays(5)); t.confirmPublish(); advanceUntilIdle()
        assertEquals(Triple("t1", TODAY.plusDays(5).toString(), "tk"), repo.published)
        assertEquals("req9", t.state.value.createdId)
        assertNull(t.state.value.publish)
    }

    @Test fun `plantillas - un error de la fecha queda en la hoja y el resto en mensaje`() = runTest(dispatcher) {
        val repo = NewReqRepo(templates = Result.success(listOf(template())))
        val t = TemplatesViewModel(repo, today = { TODAY }, newKey = { "tk" })
        advanceUntilIdle()
        t.askPublish(t.state.value.items.first()); t.onDate(TODAY.plusDays(2))
        repo.publishResult = Result.failure(ApiException(400, "LOGYX-REQ-003", "La fecha de recojo ya pasó", "pickupDate"))
        t.confirmPublish(); advanceUntilIdle()
        assertNotNull(t.state.value.publish?.error)
        assertFalse(t.state.value.publish!!.sending)
        repo.publishResult = Result.failure(ApiException(403, "LOGYX-ORG-004", "Tu empresa está suspendida"))
        t.confirmPublish(); advanceUntilIdle()
        assertNull(t.state.value.publish)
        assertNotNull(t.state.value.message)
    }

    @Test fun `plantillas - sin plantillas muestra la lista vacia y un error de carga se reintenta`() = runTest(dispatcher) {
        val repo = NewReqRepo(templates = Result.failure(ApiException(500, "X", "Error")))
        val t = TemplatesViewModel(repo)
        advanceUntilIdle()
        assertNotNull(t.state.value.error)
        repo.templates = Result.success(emptyList())
        t.refresh(); advanceUntilIdle()
        assertNull(t.state.value.error)
        assertTrue(t.state.value.items.isEmpty())
    }
}
