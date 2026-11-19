package pe.logyx.feature.requests

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val requestsModule = module {
    viewModel { RequestsViewModel(get(), get(), get()) }
    viewModel { (requestId: String) -> RequestDetailViewModel(requestId, get(), get(), get(), get(), get()) }
    // Ola 2, zona P (2.16): publicar desde el móvil y plantillas. `from` = solicitud que se duplica (nulo = formulario vacío).
    viewModel { (from: String?) -> NewRequestViewModel(from, get(), get()) }
    viewModel { TemplatesViewModel(get()) }
}
