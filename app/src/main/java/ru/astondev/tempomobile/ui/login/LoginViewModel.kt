package ru.astondev.tempomobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.astondev.tempomobile.data.repo.AuthRepository
import javax.inject.Inject

data class LoginUiState(
    val baseUrl: String = "https://jira.astondev.ru",
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(
            baseUrl = authRepository.currentCredentials()?.baseUrl ?: "https://jira.astondev.ru",
            username = authRepository.currentCredentials()?.authUsername.orEmpty(),
        )
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onBaseUrlChange(value: String) = _state.update { it.copy(baseUrl = value, error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun login() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = authRepository.login(s.baseUrl, s.username.trim(), s.password)
            _state.update {
                if (result.isSuccess) {
                    it.copy(loading = false, loggedIn = true)
                } else {
                    it.copy(loading = false, error = result.exceptionOrNull()?.message)
                }
            }
        }
    }
}
