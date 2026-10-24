package app.plainpaper.feature.home.di

import app.plainpaper.feature.home.HomeViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val homeModule: Module =
    module {
        viewModel { HomeViewModel(recoverSession = get(), discardSession = get(), dispatchers = get()) }
    }
