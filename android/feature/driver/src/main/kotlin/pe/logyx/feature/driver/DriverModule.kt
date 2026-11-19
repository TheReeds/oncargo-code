package pe.logyx.feature.driver

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val driverModule = module {
    // Explícito: `viewModelOf` no respeta los parámetros por omisión; la caché y la cola vienen de `dataModule`.
    viewModel { DriverTripViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
