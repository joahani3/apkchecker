package com.releasewatch.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory as buildViewModelFactory
import com.releasewatch.app.AppContainer
import com.releasewatch.app.ReleaseWatchApp

@Composable
inline fun <reified VM : ViewModel> viewModelFactory(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as ReleaseWatchApp).container
    val factory = buildViewModelFactory {
        initializer { create(container) }
    }
    return viewModel(factory = factory)
}
