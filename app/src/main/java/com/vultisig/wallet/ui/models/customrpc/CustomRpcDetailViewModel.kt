package com.vultisig.wallet.ui.models.customrpc

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.CustomRpcDefaultEndpoint
import com.vultisig.wallet.data.api.RpcHealthProbe
import com.vultisig.wallet.data.api.RpcHealthResult
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.CustomRpcRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class CustomRpcDetailUiState(
    val chainName: String = "",
    val defaultEndpoint: String? = null,
    val errorMessage: UiText? = null,
    val isSaving: Boolean = false,
    val hasExistingOverride: Boolean = false,
    val canSave: Boolean = false,
)

@HiltViewModel
internal class CustomRpcDetailViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val customRpcRepository: CustomRpcRepository,
    private val rpcHealthProbe: RpcHealthProbe,
    private val snackbarFlow: SnackbarFlow,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.CustomRpcDetail>()
    private val chain = Chain.fromRaw(args.chainId)

    val urlFieldState = TextFieldState()

    private val _state =
        MutableStateFlow(
            CustomRpcDetailUiState(
                chainName = chain.raw,
                defaultEndpoint = CustomRpcDefaultEndpoint.string(chain),
            )
        )
    val state: StateFlow<CustomRpcDetailUiState> = _state.asStateFlow()

    init {
        // Read the persisted override off the main thread via the reactive API. The synchronous
        // urlFor() would block on first-use disk hydration, and init runs on the main thread.
        viewModelScope.launch {
            val existing = customRpcRepository.overrides.first()[chain]
            if (existing != null) {
                urlFieldState.setTextAndPlaceCursorAtEnd(existing)
                _state.update { it.copy(hasExistingOverride = true) }
            }
        }

        // Re-validate and clear any stale error whenever the field changes.
        viewModelScope.launch {
            snapshotFlow { urlFieldState.text.toString() }
                .collectLatest { text ->
                    val trimmed = text.trim()
                    _state.update {
                        it.copy(
                            canSave = trimmed.isNotEmpty() && isValidRpcUrl(trimmed),
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    fun onPaste(value: String) {
        urlFieldState.setTextAndPlaceCursorAtEnd(value.trim())
    }

    /**
     * Validates the URL, then probes the endpoint (chain-aware) before persisting — there is no
     * separate Test button (#4997). A reachable endpoint is saved and the user returns to the
     * picker with a success banner; an unreachable / wrong-chain / malformed endpoint surfaces an
     * inline error and is not persisted.
     */
    fun onSaveClick() {
        val url = urlFieldState.text.toString().trim()
        if (!isValidRpcUrl(url)) {
            _state.update {
                it.copy(errorMessage = UiText.StringResource(R.string.custom_rpc_invalid_url))
            }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.safeLaunch(
            onError = {
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = UiText.StringResource(R.string.custom_rpc_unreachable),
                    )
                }
            }
        ) {
            when (rpcHealthProbe.probe(chain, url)) {
                is RpcHealthResult.Reachable -> {
                    customRpcRepository.setOverride(chain, url)
                    snackbarFlow.showMessage(
                        UiText.StringResource(R.string.custom_rpc_save_success)
                    )
                    navigator.back()
                }
                RpcHealthResult.WrongChain ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = UiText.StringResource(R.string.custom_rpc_wrong_chain),
                        )
                    }
                RpcHealthResult.InvalidResponse ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            errorMessage =
                                UiText.StringResource(R.string.custom_rpc_invalid_response),
                        )
                    }
                RpcHealthResult.Unreachable ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = UiText.StringResource(R.string.custom_rpc_unreachable),
                        )
                    }
            }
        }
    }

    fun onResetClick() {
        viewModelScope.safeLaunch {
            customRpcRepository.clearOverride(chain)
            navigator.back()
        }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    private fun isValidRpcUrl(url: String): Boolean =
        runCatching {
                val uri = URI(url)
                uri.scheme?.lowercase() == "https" && !uri.host.isNullOrBlank()
            }
            .getOrDefault(false)
}
