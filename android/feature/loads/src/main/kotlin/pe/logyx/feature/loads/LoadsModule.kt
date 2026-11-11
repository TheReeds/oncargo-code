package pe.logyx.feature.loads

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val loadsModule = module {
    viewModelOf(::CarrierHomeViewModel)
    viewModelOf(::LoadsFeedViewModel)
    viewModelOf(::MyBidsViewModel)
    viewModel { (requestId: String) -> LoadDetailViewModel(requestId, get(), get(), get()) }
}
