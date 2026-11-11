package pe.logyx.feature.notifications

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val notificationsModule = module {
    single { UnreadCounter(get()) }
    viewModel { NotificationsViewModel(get(), get()) }
}
