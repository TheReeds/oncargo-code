package pe.logyx.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import pe.logyx.app.navigation.LogyxApp
import pe.logyx.core.data.SessionState
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.model.BookingStatus
import pe.logyx.core.model.CargoType
import kotlin.test.assertEquals

/** PYME: pestaña Viajes -> reservar espacio (validación inline, confirmación) -> aparece en "Mis reservas". */
@RunWith(AndroidJUnit4::class)
class PymeBookingFlowTest {
    private val trips = FakeTrips()
    private val auth = FakeAuth(SessionState.LoggedIn(Data.pyme))
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FakeKoinRule(auth)).around(FakeTripsKoinRule(trips)).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun book_space_on_a_published_trip() {
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Mis solicitudes")
        compose.onNodeWithText("Viajes").performClick()
        waitFor("Transportes Sur Andino SRL")
        compose.onNodeWithText("Transportes Sur Andino SRL").assertIsDisplayed()

        compose.onAllNodesWithText("Reservar espacio").onFirst().performClick()
        waitFor("Peso de tu carga")
        // Sin datos: validación inline y no se envía nada
        compose.onAllNodesWithText("Reservar espacio").onLast().performClick()
        waitFor("Ingresa el peso de tu carga, en kg.")
        waitFor("Describe qué vas a enviar.")
        assertEquals(0, trips.booked.size)

        compose.onAllNodes(hasSetTextAction())[0].performTextInput("2500")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Mercadería de bazar en cajas")
        compose.onNode(hasContentDescription("Tipo de carga", substring = true)).performClick()
        compose.onNodeWithText("Carga general").performClick()
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("Av. Argentina 1234, Callao")
        compose.onAllNodes(hasSetTextAction())[3].performTextInput("Jr. San Román 456, Juliaca")
        waitFor("S/ 1,200.00") // 0.48 por kg x 2,500 kg
        // Con el teclado abierto el botón puede quedar tapado: se dispara la acción semántica, no un toque.
        compose.onAllNodesWithText("Reservar espacio").onLast().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)

        waitFor("¿Reservar 2,500 kg por S/ 1,200.00?")
        compose.onNodeWithText("Enviar reserva").performClick()
        compose.waitUntil(10_000) { trips.booked.size == 1 }
        assertEquals(2500.0, trips.booked.single().second.weightKg)
        assertEquals(CargoType.GENERAL, trips.booked.single().second.cargoType)
        // Salta a "Mis reservas" y muestra la reserva por confirmar
        waitFor("Por confirmar")
        waitFor("Reserva enviada")
    }
}

/** Transportista: Home -> Mis viajes -> detalle -> confirmar una reserva (con diálogo de confirmación). */
@RunWith(AndroidJUnit4::class)
class CarrierTripBookingsFlowTest {
    private val trips = FakeTrips(bookings = listOf(TripData.booking()))
    private val auth = FakeAuth(SessionState.LoggedIn(Data.carrier))
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FakeKoinRule(auth)).around(FakeTripsKoinRule(trips)).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun confirm_a_pending_booking() {
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Tu actividad")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Ver mis viajes"))
        compose.onNodeWithText("Ver mis viajes").performClick()
        waitFor("Mis viajes")
        waitFor("ASA-202")
        compose.onNodeWithText("ASA-202").performClick()

        waitFor("Comercial Altiplano SAC")
        compose.onAllNodesWithText("Confirmar reserva").onFirst().performClick()
        waitFor("¿Confirmar la reserva de 2,500 kg?")
        assertEquals(0, trips.confirmed.size)
        compose.onAllNodesWithText("Confirmar reserva").onLast().performClick()
        compose.waitUntil(10_000) { trips.confirmed.size == 1 }
        waitFor("Reserva confirmada")
        assertEquals(BookingStatus.CONFIRMED, trips.bookings.single().status)
    }
}

/** Transportista: Home -> Mis viajes -> Publicar viaje (ciudades, fecha y hora con selectores, tarifa) -> detalle del viaje publicado. */
@RunWith(AndroidJUnit4::class)
class PublishTripFlowTest {
    private val trips = FakeTrips()
    private val auth = FakeAuth(SessionState.LoggedIn(Data.carrier))
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FakeKoinRule(auth)).around(FakeTripsKoinRule(trips)).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun publish_a_trip_with_date_and_time_pickers() {
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Tu actividad")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Ver mis viajes"))
        compose.onNodeWithText("Ver mis viajes").performClick()
        waitFor("Mis viajes")
        compose.onAllNodesWithText("Publicar viaje").onFirst().performClick()
        waitFor("Capacidad que ofreces")

        // Sin datos: validación inline y no se envía nada
        compose.onAllNodes(hasText("Publicar viaje") and hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Elige la ciudad de origen.")
        assertEquals(0, trips.created.size)

        compose.onNode(hasContentDescription("Origen", substring = true)).performClick()
        compose.onNodeWithText("Lima").performClick()
        compose.onNode(hasContentDescription("Destino", substring = true)).performClick()
        compose.onNodeWithText("Juliaca").performClick()

        // Fecha: atajo "Mañana" del selector; hora: la propuesta (08:00)
        compose.onNode(hasContentDescription("Fecha de salida", substring = true)).performClick()
        waitFor("Mañana")
        compose.onNodeWithText("Mañana").performClick()
        compose.onNodeWithText("Aceptar").performClick()
        compose.onNode(hasContentDescription("Hora de salida", substring = true)).performClick()
        waitFor("Aceptar")
        compose.onNodeWithText("Aceptar").performClick()

        compose.onAllNodes(hasSetTextAction())[0].performTextInput("12000")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("0.48")
        compose.onAllNodes(hasText("Publicar viaje") and hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)

        compose.waitUntil(10_000) { trips.created.size == 1 }
        val sent = trips.created.single()
        assertEquals("LIMA", sent.originCity)
        assertEquals("JULIACA", sent.destinationCity)
        assertEquals(12000.0, sent.capacityKg)
        assertEquals(0.48, sent.rate)
        // Reemplaza el formulario por el detalle del viaje recién publicado
        waitFor("Viaje publicado")
        waitFor("Reservas")
    }
}

/** Transportista: rechazar una reserva pide un motivo opcional y lo envía al API. */
@RunWith(AndroidJUnit4::class)
class RejectBookingReasonFlowTest {
    private val trips = FakeTrips(bookings = listOf(TripData.booking()))
    private val auth = FakeAuth(SessionState.LoggedIn(Data.carrier))
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FakeKoinRule(auth)).around(FakeTripsKoinRule(trips)).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun reject_with_an_optional_reason() {
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Tu actividad")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Ver mis viajes"))
        compose.onNodeWithText("Ver mis viajes").performClick()
        waitFor("ASA-202")
        compose.onNodeWithText("ASA-202").performClick()
        waitFor("Comercial Altiplano SAC")
        compose.onAllNodesWithText("Rechazar reserva").onFirst().performClick()
        waitFor("¿Rechazar esta reserva?")
        assertEquals(0, trips.rejected.size)
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Ya no hay espacio")
        compose.onAllNodesWithText("Rechazar reserva").onLast().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { trips.rejected.size == 1 }
        assertEquals("b1" to "Ya no hay espacio", trips.rejected.single())
        waitFor("Reserva rechazada")
        assertEquals(BookingStatus.REJECTED, trips.bookings.single().status)
    }
}

/** Transportista: Home (aviso de sugerencias) -> Smart Load -> aceptar con confirmación. */
@RunWith(AndroidJUnit4::class)
class SmartLoadFlowTest {
    private val trips = FakeTrips()
    private val smart = FakeSmartLoads(listOf(SmartData.load()))
    private val auth = FakeAuth(SessionState.LoggedIn(Data.carrier))
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain =
        RuleChain.outerRule(FakeKoinRule(auth, loads = FakeLoads(pendingSmartLoads = 1))).around(FakeTripsKoinRule(trips, smart)).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun accept_a_smart_load_suggestion() {
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Tu actividad")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Ver sugerencias"))
        compose.onNodeWithText("Ver sugerencias").performClick()
        waitFor("Ingreso combinado")
        waitFor("ASA-202")
        compose.onAllNodesWithText("Aceptar sugerencia").onFirst().performClick()
        waitFor("¿Aceptar esta sugerencia?")
        assertEquals(0, smart.accepted.size)
        compose.onAllNodesWithText("Aceptar sugerencia").onLast().performClick()
        compose.waitUntil(10_000) { smart.accepted.size == 1 }
        assertEquals("s1", smart.accepted.single())
        waitFor("Sugerencia aceptada")
    }
}
