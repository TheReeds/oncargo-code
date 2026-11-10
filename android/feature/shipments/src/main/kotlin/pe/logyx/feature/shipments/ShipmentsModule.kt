package pe.logyx.feature.shipments

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val shipmentsModule = module {
    viewModelOf(::ShipmentsViewModel)
    viewModel { (shipmentId: String) -> ShipmentDetailViewModel(shipmentId, get(), get(), get()) }
}
