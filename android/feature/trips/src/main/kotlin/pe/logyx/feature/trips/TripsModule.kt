package pe.logyx.feature.trips

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val tripsModule = module {
    viewModel { (fromTripId: String?) -> PublishTripViewModel(get(), get(), get(), fromTripId) }
    viewModelOf(::CarrierTripsViewModel)
    viewModel { (tripId: String, justPublished: Boolean) -> CarrierTripDetailViewModel(tripId, justPublished, get(), get()) }
    viewModel { (tripId: String) -> PymeTripDetailViewModel(tripId, get(), get()) }
    viewModelOf(::ReturnLoadsViewModel)
    viewModelOf(::SmartLoadsViewModel)
    viewModelOf(::TripSearchViewModel)
    viewModelOf(::MyBookingsViewModel)
}
