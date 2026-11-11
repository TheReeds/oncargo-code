package pe.logyx.feature.documents

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val documentsModule = module {
    viewModel { (shipmentId: String) -> DocumentsViewModel(shipmentId, get()) }
}
