package pe.logyx.feature.reputation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val reputationModule = module {
    viewModel { (organizationId: String) -> OrganizationProfileViewModel(organizationId, get()) }
    viewModel { (shipmentId: String) -> ReviewViewModel(shipmentId, get(), get()) }
}
