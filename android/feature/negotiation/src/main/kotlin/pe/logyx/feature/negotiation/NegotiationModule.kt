package pe.logyx.feature.negotiation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val negotiationModule = module {
    viewModel { (requestId: String, carrierOrgId: String) -> ChatViewModel(requestId, carrierOrgId, get(), get()) }
    viewModel { (requestId: String) -> ThreadsViewModel(requestId, get()) }
}
