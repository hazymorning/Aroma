package app.aroma.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Keeps the view models made within [content] for the one sign-in [signIn] stands for, and clears them
 * once [content] leaves for good. Navigation keeps its screens' view models with the activity, so without
 * this the screens of an account signed out of would live on unseen, loading again for the next one.
 * A rotation or a change of language keeps them, as the activity does.
 */
@Composable
internal fun SignInScope(signIn: Any, scopes: SignInScopes = viewModel { SignInScopes() }, content: @Composable () -> Unit) {
    val owner = remember(scopes, signIn) { scopes.owner(signIn) }
    val activity = LocalActivity.current
    DisposableEffect(scopes, signIn) {
        onDispose { if (activity?.isChangingConfigurations != true) scopes.end(signIn) }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

/** The view models of each sign-in shown, kept with the activity through configuration changes. */
internal class SignInScopes : ViewModel() {
    private val stores = mutableMapOf<Any, ViewModelStore>()

    fun owner(signIn: Any): ViewModelStoreOwner {
        val store = stores.getOrPut(signIn) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
    }

    fun end(signIn: Any) {
        stores.remove(signIn)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach(ViewModelStore::clear)
        stores.clear()
    }
}
