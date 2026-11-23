package app.aroma.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.data.attempt
import app.aroma.data.repository.AuthRepository
import app.aroma.data.toAromaError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

    fun onServerChange(text: String) = mutableState.update { it.copy(serverInput = text, error = null) }

    fun onUsernameChange(text: String) = mutableState.update { it.copy(username = text, error = null) }

    fun onPasswordChange(text: String) = mutableState.update { it.copy(password = text, error = null) }

    fun onTokenChange(text: String) = mutableState.update { it.copy(token = text, error = null) }

    fun toggleTokenMode() = mutableState.update { it.copy(useToken = !it.useToken, error = null) }

    fun changeServer() = mutableState.update { it.copy(step = LoginStep.Server, server = null, error = null) }

    fun connect() =
        work {
            val server = auth.probe(state.value.serverInput)
            mutableState.update { it.copy(server = server, step = LoginStep.Credentials, useToken = !server.allowPasswordLogin) }
        }

    /**
     * On success the session changes and the app leaves the login screen by itself. This view
     * model lives as long as the activity, so the password and token go right away: after a
     * sign-out the login screen must not offer them again.
     */
    fun signIn() =
        work {
            val current = state.value
            val server = current.server ?: return@work
            if (current.useToken) auth.signInWithToken(server, current.token) else auth.signIn(server, current.username, current.password)
            mutableState.update { LoginUiState(serverInput = it.serverInput) }
        }

    private fun work(block: suspend () -> Unit) {
        if (state.value.isWorking) return
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, error = null) }
            attempt { block() }.onFailure { error -> mutableState.update { it.copy(error = error.toAromaError()) } }
            mutableState.update { it.copy(isWorking = false) }
        }
    }
}
