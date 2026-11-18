package pe.logyx.feature.requests

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val requestsModule = module {
    viewModel { RequestsViewModel(get(), get(), get()) }
    viewModel { (requestId: String) -> RequestDetailViewModel(requestId, get(), get(), get(), get(), get()) }
}
