package com.studysmart.app.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AppViewModelFactory(
    private val container: AppContainer,
    private val creator: (AppContainer) -> ViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel = creator(container)
        require(modelClass.isInstance(viewModel)) {
            "Factory created ${viewModel::class.java.name}, expected ${modelClass.name}"
        }
        return viewModel as T
    }
}
