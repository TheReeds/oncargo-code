package pe.logyx.app

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module
import pe.logyx.core.data.NegotiationRepository
import pe.logyx.core.model.ChatThreadView
import pe.logyx.core.model.CounterOfferView
import pe.logyx.core.model.CursorPage
import pe.logyx.core.model.MessageView
import pe.logyx.core.network.ApiException

/** Chat en memoria. Rechaza con RN07 (LOGYX-NEG-001, field "body") todo texto con dígitos seguidos, "@" o "http". */
class FakeNegotiation : NegotiationRepository {
    val stored = mutableListOf<MessageView>()
    val attempts = mutableListOf<String>()

    private fun violatesRn07(body: String) =
        Regex("\\d{6,}|\\d{3}[ -]\\d{3}").containsMatchIn(body) || "@" in body || "http" in body.lowercase()

    override suspend fun threads(requestId: String) = Result.success(emptyList<ChatThreadView>())
    override suspend fun messages(requestId: String, carrierOrgId: String, before: String?) =
        Result.success(CursorPage(stored.toList(), null))
    override suspend fun send(requestId: String, carrierOrgId: String, body: String): Result<MessageView> {
        attempts += body
        if (violatesRn07(body)) {
            return Result.failure(
                ApiException(422, "LOGYX-NEG-001", "No puedes compartir teléfonos, correos ni enlaces. Coordina todo por el chat de OnCargo.", field = "body"),
            )
        }
        val m = MessageView("m${stored.size + 1}", requestId, carrierOrgId, mine = true, body = body, createdAt = "2026-11-10T10:%02d:00-05:00".format(stored.size))
        stored += m
        return Result.success(m)
    }
    override suspend fun counterOffers(bidId: String) = Result.success(emptyList<CounterOfferView>())
    override suspend fun propose(bidId: String, amount: Double, message: String?) = Result.failure<CounterOfferView>(IllegalStateException())
    override suspend fun accept(counterOfferId: String) = Result.failure<CounterOfferView>(IllegalStateException())
    override suspend fun reject(counterOfferId: String) = Result.failure<CounterOfferView>(IllegalStateException())
}

/**
 * Regla complementaria de [FakeKoinRule] (que no registra NegotiationRepository): carga el fake en Koin y lo descarga al terminar.
 * Uso: `RuleChain.outerRule(FakeKoinRule(auth)).around(FakeNegotiationRule(fake)).around(compose)`.
 * Requiere que el Application ya haya iniciado Koin con `negotiationModule` (lo hace `LogyxApplication`).
 */
class FakeNegotiationRule(private val fake: FakeNegotiation) : TestWatcher() {
    private var overrides: Module? = null
    override fun starting(description: Description) {
        overrides = module { single<NegotiationRepository> { fake } }.also { loadKoinModules(it) }
    }
    override fun finished(description: Description) { overrides?.let { unloadKoinModules(it) } }
}
