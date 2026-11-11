package pe.logyx.feature.requests

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val requestsModule = module {
    viewModelOf(::RequestsViewModel)
    viewModel { (requestId: String) -> RequestDetailViewModel(requestId, get(), get(), get()) }
}
