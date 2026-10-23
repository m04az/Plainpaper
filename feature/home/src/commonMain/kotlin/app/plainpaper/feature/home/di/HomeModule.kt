package app.plainpaper.feature.home.di

import app.plainpaper.feature.home.HomeViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeModule: Module =
    module {
        viewModelOf(::HomeViewModel)
    }
