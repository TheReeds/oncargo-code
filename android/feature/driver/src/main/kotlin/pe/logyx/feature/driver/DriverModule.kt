package pe.logyx.feature.driver

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val driverModule = module {
    viewModelOf(::DriverTripViewModel)
}
