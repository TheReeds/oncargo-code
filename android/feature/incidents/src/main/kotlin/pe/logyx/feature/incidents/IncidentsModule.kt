package pe.logyx.feature.incidents

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val incidentsModule = module {
    viewModel { (shipmentId: String) -> ReportIncidentViewModel(shipmentId, get(), get()) }
    viewModel { (shipmentId: String) -> IncidentsListViewModel(shipmentId, get(), get()) }
    viewModel { (incidentId: String) -> IncidentDetailViewModel(incidentId, get(), get(), get()) }
}
