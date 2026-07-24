package com.homecontrol.feature.dashboard.di

import com.homecontrol.feature.dashboard.DashboardViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dashboardModule = module {
    viewModel { DashboardViewModel(get()) }
}
