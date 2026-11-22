package app.aroma.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.aroma.AppContainer
import app.aroma.AromaApplication

/** Creates a ViewModel with access to the app's dependencies. */
@Composable
inline fun <reified VM : ViewModel> aromaViewModel(key: String? = null, crossinline create: AppContainer.(SavedStateHandle) -> VM): VM {
    val container = (LocalContext.current.applicationContext as AromaApplication).container
    return viewModel(key = key) { container.create(createSavedStateHandle()) }
}
