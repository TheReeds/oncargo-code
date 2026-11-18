package pe.logyx.feature.requests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.NegotiationRepository
import pe.logyx.core.data.RequestsRepository
import pe.logyx.core.data.RoutesRepository
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun bid(id: String, amount: Double, status: BidStatus = BidStatus.PENDING) =
    BidView(id, "r1", carrierName = "Carrier $id", amount = amount, status = status)

private val request = RequestView(
    id = "r1", originCity = "LIMA", destinationCity = "JULIACA", cargoType = CargoType.GENERAL, weightKg = 3000.0,
    suggestedPrice = 2000.0, priceFloor = 1500.0, status = RequestStatus.PUBLISHED,
)

private class FakeRequests(var bids: List<BidView>, var acceptResult: Result<AcceptResult>) : RequestsRepository {
    var accepted: String? = null
    var current: RequestView = request
    var cancelResult: Result<RequestView> = Result.success(request.copy(status = RequestStatus.CANCELLED))
    var cancelled: Pair<String, String?>? = null
    var reopenResult: Result<RequestView> = Result.success(request)
    var reopened: Pair<String, String?>? = null
    var republishResult: Result<RequestView> = Result.success(request.copy(id = "r2", republishedFrom = "r1"))
    var republished: Pair<String, String?>? = null
    override suspend fun mine() = Result.success(listOf(request))
    override suspend fun detail(id: String) = Result.success(current)
    override suspend fun bids(requestId: String) = Result.success(bids)
    override suspend fun acceptBid(bidId: String): Result<AcceptResult> { accepted = bidId; return acceptResult }
    override suspend fun cancel(requestId: String, reason: String?): Result<RequestView> { cancelled = requestId to reason; return cancelResult }
    override suspend fun reopen(requestId: String, pickupDate: String?): Result<RequestView> { reopened = requestId to pickupDate; return reopenResult }
    override suspend fun republish(requestId: String, pickupDate: String?): Result<RequestView> { republished = requestId to pickupDate; return republishResult }
}

private class FakeCatalog : CatalogRepository {
    override suspend fun cities() = Result.success(listOf(CityItem("LIMA", "Lima", -12.0, -77.0), CityItem("JULIACA", "Juliaca", -15.5, -70.1)))
    override suspend fun organization(id: String) = Result.failure<PublicOrganization>(IllegalStateException())
}

private class FakeNegotiation(var proposeResult: Result<CounterOfferView> = Result.success(CounterOfferView("co1", "a", amount = 1700.0, status = CounterOfferStatus.PENDING))) : NegotiationRepository {
    var proposed: Triple<String, Double, String?>? = null
    var offers: Map<String, List<CounterOfferView>> = emptyMap()
    var accepted: String? = null
    var rejected: String? = null
    var respondFails: Throwable? = null
    override suspend fun threads(requestId: String) = Result.success(emptyList<ChatThreadView>())
    override suspend fun messages(requestId: String, carrierOrgId: String, before: String?) = Result.success(CursorPage<MessageView>(emptyList(), null))
    override suspend fun send(requestId: String, carrierOrgId: String, body: String) = Result.failure<MessageView>(IllegalStateException())
    override suspend fun counterOffers(bidId: String) =
        Result.success(offers[bidId] ?: listOf(CounterOfferView("old", bidId, amount = 1800.0, status = CounterOfferStatus.REJECTED)))
    override suspend fun propose(bidId: String, amount: Double, message: String?): Result<CounterOfferView> { proposed = Triple(bidId, amount, message); return proposeResult }
    override suspend fun accept(counterOfferId: String): Result<CounterOfferView> {
        accepted = counterOfferId
        return respondFails?.let { Result.failure(it) } ?: Result.success(CounterOfferView(counterOfferId, "a", amount = 1700.0, status = CounterOfferStatus.ACCEPTED))
    }
    override suspend fun reject(counterOfferId: String): Result<CounterOfferView> {
        rejected = counterOfferId
        return respondFails?.let { Result.failure(it) } ?: Result.success(CounterOfferView(counterOfferId, "a", amount = 1700.0, status = CounterOfferStatus.REJECTED))
    }
}

private fun counter(id: String, bidId: String, byCarrier: Boolean, status: CounterOfferStatus = CounterOfferStatus.PENDING, amount: Double = 1700.0) =
    CounterOfferView(id, bidId, amount = amount, status = status, proposedByCarrier = byCarrier, createdAt = "2026-11-17T10:00:00Z")

private fun detailVm(repo: FakeRequests, nego: FakeNegotiation = FakeNegotiation()) =
    RequestDetailViewModel("r1", repo, FakeCatalog(), nego, FakeRealtime())

private fun pending(vararg b: BidView) = FakeRequests(b.toList(), Result.failure(IllegalStateException()))

@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `ofertas ordenadas por monto con mejor precio y diferencia vs sugerido`() {
        val rows = buildBidRows(listOf(bid("a", 1900.0), bid("b", 1600.0), bid("c", 2100.0)), 2000.0)
        assertEquals(listOf("b", "a", "c"), rows.map { it.bid.id })
        assertEquals(listOf(true, false, false), rows.map { it.isBest })
        assertEquals(-20.0, rows[0].diffVsSuggestedPct!!, 0.001)
        assertEquals(5.0, rows[2].diffVsSuggestedPct!!, 0.001)
    }

    @Test fun `aceptar pide confirmacion y entrega el id del envio`() = runTest {
        val repo = FakeRequests(listOf(bid("a", 1900.0), bid("b", 1600.0)), Result.success(AcceptResult("ship-9")))
        val vm = RequestDetailViewModel("r1", repo, FakeCatalog(), FakeNegotiation(), FakeRealtime())
        assertEquals(2, vm.state.value.bids.size)
        assertTrue(vm.state.value.canDecide)

        vm.askAccept(bid("b", 1600.0))
        assertEquals("b", vm.state.value.confirmBid?.id)
        vm.confirmAccept()
        assertEquals("b", repo.accepted)
        assertEquals("ship-9", vm.state.value.createdShipmentId)
        vm.navigationHandled()
        assertNull(vm.state.value.createdShipmentId)
    }

    @Test fun `error al aceptar muestra el mensaje de la API`() = runTest {
        val repo = FakeRequests(listOf(bid("a", 1900.0)), Result.failure(ApiException(409, "LOGYX-MKT-009", "La solicitud ya fue adjudicada")))
        val vm = RequestDetailViewModel("r1", repo, FakeCatalog(), FakeNegotiation(), FakeRealtime())
        vm.askAccept(bid("a", 1900.0)); vm.confirmAccept()
        assertEquals("La solicitud ya fue adjudicada", vm.state.value.message?.text)
        assertNull(vm.state.value.createdShipmentId)
    }

    @Test fun `contraoferta valida piso y monto menor antes de confirmar`() = runTest {
        val nego = FakeNegotiation()
        val vm = RequestDetailViewModel("r1", FakeRequests(listOf(bid("a", 1900.0)), Result.failure(IllegalStateException())), FakeCatalog(), nego, FakeRealtime())
        vm.openCounter(bid("a", 1900.0))
        assertEquals(1, vm.state.value.counter?.previous?.size)

        vm.onCounterAmount("1400"); vm.askConfirmCounter()
        assertTrue(vm.state.value.counter?.amountError != null)
        assertTrue(vm.state.value.counter?.confirming == false)
        vm.onCounterAmount("1900"); vm.askConfirmCounter()
        assertTrue(vm.state.value.counter?.amountError != null)
        vm.onCounterAmount("1700"); vm.askConfirmCounter()
        assertTrue(vm.state.value.counter?.confirming == true)
        assertNull(nego.proposed)
    }

    @Test fun `contraoferta enviada cierra la hoja y avisa`() = runTest {
        val nego = FakeNegotiation()
        val vm = RequestDetailViewModel("r1", FakeRequests(listOf(bid("a", 1900.0)), Result.failure(IllegalStateException())), FakeCatalog(), nego, FakeRealtime())
        vm.openCounter(bid("a", 1900.0))
        vm.onCounterAmount("1700"); vm.onCounterMessage(" ¿Podemos? "); vm.askConfirmCounter(); vm.confirmCounter()
        assertEquals(Triple("a", 1700.0, "¿Podemos?"), nego.proposed)
        assertNull(vm.state.value.counter)
        assertEquals(R.string.requests_counter_sent, vm.state.value.message?.textRes)
    }

    @Test fun `error de campo de la API se muestra en el campo monto`() = runTest {
        val nego = FakeNegotiation(Result.failure(ApiException(422, "LOGYX-NEG-005", "El monto no puede ser menor al piso de S/ 1,500.00.", field = "amount")))
        val vm = RequestDetailViewModel("r1", FakeRequests(listOf(bid("a", 1900.0)), Result.failure(IllegalStateException())), FakeCatalog(), nego, FakeRealtime())
        vm.openCounter(bid("a", 1900.0))
        vm.onCounterAmount("1600"); vm.askConfirmCounter(); vm.confirmCounter()
        val c = vm.state.value.counter!!
        assertEquals(pe.logyx.core.designsystem.components.UiText.Raw("El monto no puede ser menor al piso de S/ 1,500.00."), c.amountError)
        assertTrue(!c.sending && !c.confirming)
        assertNull(vm.state.value.message)
    }

    @Test fun `tiempo real refresca la comparacion con bid placed de la solicitud y no con otra`() = runTest(UnconfinedTestDispatcher()) {
        val repo = FakeRequests(listOf(bid("a", 1900.0)), Result.success(AcceptResult("s")))
        val rt = FakeRealtime()
        val vm = RequestDetailViewModel("r1", repo, FakeCatalog(), FakeNegotiation(), rt)
        val job = launch { vm.liveLoop() }
        assertEquals(1, vm.state.value.bids.size)
        repo.bids = listOf(bid("a", 1900.0), bid("b", 1600.0))
        rt.emit(RealtimeTypes.BID_PLACED, "requestId" to "otra", "bidId" to "b")
        assertEquals(1, vm.state.value.bids.size)
        rt.emit(RealtimeTypes.BID_PLACED, "requestId" to "r1", "bidId" to "b")
        assertEquals(2, vm.state.value.bids.size)
        assertEquals("b", vm.state.value.bids.first().bid.id)
        assertFalse(vm.state.value.refreshing)
        job.cancel()
    }

    @Test fun `carga la ruta por carretera para el mapa y sin rutas queda la recta`() = runTest {
        val repo = FakeRequests(listOf(bid("a", 1900.0)), Result.success(AcceptResult("x")))
        val road = RouteGeometry(listOf(GeoPoint(-12.0, -77.0), GeoPoint(-13.7, -76.2), GeoPoint(-15.5, -70.1)), 1248.5, 1134.0, "© OpenStreetMap")
        val asked = mutableListOf<List<String>>()
        val routes = object : RoutesRepository {
            override suspend fun route(cities: List<String>): Result<RouteGeometry> { asked += cities; return Result.success(road) }
        }
        val vm = RequestDetailViewModel("r1", repo, FakeCatalog(), FakeNegotiation(), FakeRealtime(), routes)
        assertEquals(road, vm.state.value.route)
        assertEquals(listOf(listOf("LIMA", "JULIACA")), asked)
        // Sin repositorio de rutas (o sin red) no hay geometría: el mapa dibuja la recta origen → destino.
        assertNull(RequestDetailViewModel("r1", repo, FakeCatalog(), FakeNegotiation(), FakeRealtime()).state.value.route)
    }

    // ───── Negociación del lado de la PYME (A3) ─────

    @Test fun `carga la contraoferta pendiente del transportista y solo esa se puede responder`() = runTest {
        val nego = FakeNegotiation().apply {
            offers = mapOf(
                "a" to listOf(counter("c1", "a", byCarrier = true)),
                "b" to listOf(counter("c2", "b", byCarrier = false)),
                "c" to listOf(counter("c3", "c", byCarrier = true, status = CounterOfferStatus.EXPIRED)),
            )
        }
        val vm = detailVm(pending(bid("a", 1900.0), bid("b", 2000.0), bid("c", 2100.0), bid("w", 1500.0, BidStatus.WITHDRAWN)), nego)
        val s = vm.state.value
        assertEquals("c1", s.counterToRespond("a")?.id)
        // La contraoferta que propuso la propia PYME no se puede responder: queda "esperando respuesta".
        assertNull(s.counterToRespond("b"))
        assertEquals("c2", s.counterWaiting("b")?.id)
        // Las vencidas no cuentan como vigentes y las ofertas retiradas ni se consultan.
        assertNull(s.counterToRespond("c")); assertNull(s.counterWaiting("c"))
        assertNull(s.counters["w"])
    }

    @Test fun `aceptar la contraoferta del transportista pide confirmacion, llama al API y recarga`() = runTest {
        val repo = pending(bid("a", 1900.0))
        val nego = FakeNegotiation().apply { offers = mapOf("a" to listOf(counter("c1", "a", byCarrier = true, amount = 1750.0))) }
        val vm = detailVm(repo, nego)
        vm.askRespond(bid("a", 1900.0), accept = true)
        assertEquals("c1", vm.state.value.respond?.counter?.id)
        assertNull(nego.accepted)
        repo.bids = listOf(bid("a", 1750.0))
        nego.offers = emptyMap()
        vm.confirmRespond()
        assertEquals("c1", nego.accepted)
        assertNull(vm.state.value.respond)
        assertEquals(R.string.requests_counter_response_accepted, vm.state.value.message?.textRes)
        assertEquals(1750.0, vm.state.value.bids.single().bid.amount, 0.001)
        assertTrue(vm.state.value.counters.isEmpty())
    }

    @Test fun `rechazar la contraoferta del transportista`() = runTest {
        val nego = FakeNegotiation().apply { offers = mapOf("a" to listOf(counter("c1", "a", byCarrier = true))) }
        val vm = detailVm(pending(bid("a", 1900.0)), nego)
        vm.askRespond(bid("a", 1900.0), accept = false)
        vm.confirmRespond()
        assertEquals("c1", nego.rejected)
        assertNull(nego.accepted)
        assertEquals(R.string.requests_counter_response_rejected, vm.state.value.message?.textRes)
    }

    @Test fun `quien propuso la contraoferta no puede responderla`() = runTest {
        val nego = FakeNegotiation().apply { offers = mapOf("a" to listOf(counter("c1", "a", byCarrier = false))) }
        val vm = detailVm(pending(bid("a", 1900.0)), nego)
        vm.askRespond(bid("a", 1900.0), accept = true)
        assertNull(vm.state.value.respond)
        vm.confirmRespond()
        assertNull(nego.accepted)
    }

    @Test fun `error de acceso al responder se muestra con el mensaje del servidor y la hoja se cierra`() = runTest {
        val nego = FakeNegotiation().apply {
            offers = mapOf("a" to listOf(counter("c1", "a", byCarrier = true)))
            respondFails = ApiException(403, "LOGYX-AUTH-007", "Tu rol Lector solo permite consultar.")
        }
        val vm = detailVm(pending(bid("a", 1900.0)), nego)
        vm.askRespond(bid("a", 1900.0), accept = true); vm.confirmRespond()
        assertEquals("Tu rol Lector solo permite consultar.", vm.state.value.message?.text)
        assertNull(vm.state.value.respond)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `una oferta retirada nunca es la mejor y no cuenta para el precio`() {
        val rows = buildBidRows(listOf(bid("w", 1000.0, BidStatus.WITHDRAWN), bid("a", 1900.0)), 2000.0)
        assertEquals(listOf("w", "a"), rows.map { it.bid.id })
        assertEquals(listOf(false, true), rows.map { it.isBest })
    }

    @Test fun `RN07 en el mensaje de la contraoferta queda bajo el campo y respeta 500`() = runTest {
        val nego = FakeNegotiation(Result.failure(ApiException(400, "LOGYX-NEG-001", "No compartas teléfonos ni correos.", field = "message")))
        val vm = detailVm(pending(bid("a", 1900.0)), nego)
        vm.openCounter(bid("a", 1900.0))
        vm.onCounterAmount("1700"); vm.onCounterMessage("llámame al 987654321"); vm.askConfirmCounter(); vm.confirmCounter()
        val c = vm.state.value.counter!!
        assertEquals(pe.logyx.core.designsystem.components.UiText.Raw("No compartas teléfonos ni correos."), c.messageError)
        assertNull(c.amountError)
        assertNull(vm.state.value.message)
        vm.onCounterMessage("x".repeat(600))
        assertEquals(500, vm.state.value.counter!!.message.length)
        assertNull(vm.state.value.counter!!.messageError)
    }

    // ───── Cancelar con motivo (F16) ─────

    @Test fun `cancelar con motivo lo envia recortado y vacio va como null`() = runTest {
        val repo = pending(bid("a", 1900.0))
        val vm = detailVm(repo)
        vm.askCancel()
        vm.onCancelReason("  Ya no necesito el flete ")
        vm.confirmCancel()
        assertEquals("r1" to "Ya no necesito el flete", repo.cancelled)
        assertNull(vm.state.value.cancel)
        assertEquals(R.string.requests_cancelled, vm.state.value.message?.textRes)

        vm.askCancel(); vm.confirmCancel()
        assertEquals("r1" to null, repo.cancelled)
    }

    @Test fun `RN07 al cancelar muestra el error bajo el motivo y mantiene la hoja abierta`() = runTest {
        val repo = pending(bid("a", 1900.0)).apply {
            cancelResult = Result.failure(ApiException(400, "LOGYX-NEG-001", "No compartas teléfonos, correos ni enlaces.", field = "reason"))
        }
        val vm = detailVm(repo)
        vm.askCancel(); vm.onCancelReason("escríbeme a juan@correo.com"); vm.confirmCancel()
        val draft = vm.state.value.cancel!!
        assertEquals(pe.logyx.core.designsystem.components.UiText.Raw("No compartas teléfonos, correos ni enlaces."), draft.error)
        assertFalse(draft.sending)
        assertNull(vm.state.value.message)
        // Al editar el motivo el error desaparece.
        vm.onCancelReason("Ya no lo necesito")
        assertNull(vm.state.value.cancel!!.error)
    }

    @Test fun `el motivo de cancelacion se corta a 500 caracteres`() = runTest {
        val vm = detailVm(pending(bid("a", 1900.0)))
        vm.askCancel(); vm.onCancelReason("m".repeat(700))
        assertEquals(500, vm.state.value.cancel!!.reason.length)
    }

    // ───── Reabrir y republicar (A7) ─────

    private val closedByTimeout = request.copy(
        auction = AuctionView("a1", AuctionStatus.CLOSED, "2026-11-17T10:00:00Z", 5, 0, CloseReason.TIMEOUT, "2026-11-17T10:20:00Z"),
    )

    @Test fun `reabrir subasta con fecha opcional recarga y avisa`() = runTest {
        val repo = pending().apply { current = closedByTimeout }
        val vm = detailVm(repo)
        assertTrue(vm.state.value.request!!.canReopenAuction)
        vm.askReopen()
        assertEquals(RescheduleMode.REOPEN, vm.state.value.reschedule?.mode)
        vm.onRescheduleDate(java.time.LocalDate.of(2099, 12, 1))
        repo.current = request
        vm.confirmReschedule()
        assertEquals("r1" to "2099-12-01", repo.reopened)
        assertNull(vm.state.value.reschedule)
        assertEquals(R.string.requests_reopened, vm.state.value.message?.textRes)
        assertFalse(vm.state.value.request!!.canReopenAuction)

        vm.askReopen(); vm.confirmReschedule()
        assertEquals("r1" to null, repo.reopened)
    }

    @Test fun `AUC-003 al reabrir cierra la hoja y muestra el mensaje del servidor`() = runTest {
        val repo = pending().apply {
            current = closedByTimeout
            reopenResult = Result.failure(ApiException(409, "LOGYX-AUC-003", "Solo se reabre la subasta de una solicitud publicada"))
        }
        val vm = detailVm(repo)
        vm.askReopen(); vm.confirmReschedule()
        assertEquals("Solo se reabre la subasta de una solicitud publicada", vm.state.value.message?.text)
        assertNull(vm.state.value.reschedule)
    }

    @Test fun `REQ-003 por fecha pasada queda bajo el campo de fecha`() = runTest {
        val repo = pending().apply {
            current = closedByTimeout
            reopenResult = Result.failure(ApiException(400, "LOGYX-REQ-003", "La fecha de recojo ya pasó: indica una nueva fecha", field = "pickupDate"))
        }
        val vm = detailVm(repo)
        vm.askReopen(); vm.onRescheduleDate(java.time.LocalDate.of(2020, 1, 1)); vm.confirmReschedule()
        val d = vm.state.value.reschedule!!
        assertEquals(pe.logyx.core.designsystem.components.UiText.Raw("La fecha de recojo ya pasó: indica una nueva fecha"), d.error)
        assertFalse(d.sending)
        assertNull(vm.state.value.message)
        vm.onRescheduleDate(java.time.LocalDate.of(2099, 1, 1))
        assertNull(vm.state.value.reschedule!!.error)
    }

    @Test fun `republicar una cancelada abre la solicitud nueva`() = runTest {
        val repo = pending().apply { current = request.copy(status = RequestStatus.CANCELLED, cancelReason = "Cambié de plan") }
        val vm = detailVm(repo)
        assertTrue(vm.state.value.request!!.canRepublish)
        assertTrue(vm.state.value.counters.isEmpty())
        vm.askRepublish()
        assertEquals(RescheduleMode.REPUBLISH, vm.state.value.reschedule?.mode)
        vm.confirmReschedule()
        assertEquals("r1" to null, repo.republished)
        assertEquals("r2", vm.state.value.openRequestId)
        assertNull(vm.state.value.reschedule)
        assertEquals(R.string.requests_republished, vm.state.value.message?.textRes)
        vm.navigationHandled()
        assertNull(vm.state.value.openRequestId)
    }

    @Test fun `REQ-004 al republicar una solicitud que no esta cancelada`() = runTest {
        val repo = pending().apply {
            republishResult = Result.failure(ApiException(409, "LOGYX-REQ-004", "Solo se republica una solicitud cancelada"))
        }
        val vm = detailVm(repo)
        vm.askRepublish(); vm.confirmReschedule()
        assertEquals("Solo se republica una solicitud cancelada", vm.state.value.message?.text)
        assertNull(vm.state.value.openRequestId)
        assertNull(vm.state.value.reschedule)
    }

    @Test fun `cerrar la hoja de cancelar la descarta`() = runTest {
        val vm = detailVm(pending())
        vm.askCancel()
        vm.closeCancel()
        assertNull(vm.state.value.cancel)
    }
}
