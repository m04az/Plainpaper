package app.plainpaper.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.plainpaper.core.domain.coroutines.DispatcherProvider
import app.plainpaper.core.domain.usecase.capture.DiscardSessionUseCase
import app.plainpaper.core.domain.usecase.capture.RecoverSessionUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val recoverSession: RecoverSessionUseCase,
    private val discardSession: DiscardSessionUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = effectChannel.receiveAsFlow()

    init {
        checkRecoverable()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.ScanClicked -> {
                effectChannel.trySend(HomeEffect.NavigateToScan)
            }

            HomeAction.ImportClicked -> {
                effectChannel.trySend(HomeEffect.NavigateToImport)
            }

            HomeAction.ScreenStarted -> {
                checkRecoverable()
            }

            HomeAction.ContinueRecovery -> {
                uiState.value.recoverable?.let { effectChannel.trySend(HomeEffect.NavigateToHandoff(it.id)) }
            }

            HomeAction.DiscardRecovery -> {
                discardRecoverable()
            }
        }
    }

    private fun checkRecoverable() {
        viewModelScope.launch(dispatchers.io) {
            val session = recoverSession()
            mutableUiState.update { it.copy(recoverable = session?.let { s -> RecoverableSession(s.id, s.pageCount) }) }
        }
    }

    private fun discardRecoverable() {
        val session = uiState.value.recoverable ?: return
        mutableUiState.update { it.copy(recoverable = null) }
        viewModelScope.launch(dispatchers.io) { discardSession(session.id) }
    }
}
