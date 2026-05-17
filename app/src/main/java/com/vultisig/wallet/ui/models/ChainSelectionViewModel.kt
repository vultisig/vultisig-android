package com.vultisig.wallet.ui.models

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.hasPreGeneratedKey
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DiscoverTokenUseCase
import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import com.vultisig.wallet.ui.models.VaultAccountsViewModel.Companion.REFRESH_CHAIN_DATA
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class ChainSelectionUiModel(
    val chains: List<ChainUiModel> = emptyList(),
    val isKeyImportVault: Boolean = false,
)

internal data class ChainUiModel(val isEnabled: Boolean, val coin: Coin)

@HiltViewModel
internal class ChainSelectionViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val tokenRepository: TokenRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val discoverTokenUseCase: DiscoverTokenUseCase,
    private val navigator: Navigator<Destination>,
    private val requestResultRepository: RequestResultRepository,
    private val snackbarFlow: SnackbarFlow,
) : ViewModel() {

    val args = savedStateHandle.toRoute<Route.AddChainAccount>()
    private val vaultId: String = args.vaultId
    private val routeFromInitVault: Boolean = args.routeFromInitVault
    val uiState = MutableStateFlow(ChainSelectionUiModel())

    val searchTextFieldState = TextFieldState()

    init {
        loadChains()
    }

    fun enableAccountTemp(nativeToken: Coin) {
        val chains =
            uiState.value.chains.map {
                if (it.coin.id == nativeToken.id) {
                    it.copy(isEnabled = true)
                } else {
                    it
                }
            }
        uiState.update { it.copy(chains = chains) }
    }

    fun disableAccountTemp(nativeToken: Coin) {
        val chains =
            uiState.value.chains.map {
                if (it.coin.id == nativeToken.id) {
                    it.copy(isEnabled = false)
                } else {
                    it
                }
            }
        uiState.update { it.copy(chains = chains) }
    }

    fun onCommitChanges() {
        val toEnableAccounts = uiState.value.chains.filter { it.isEnabled }
        val toDisableAccounts = uiState.value.chains - toEnableAccounts.toSet()

        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: error("No vault with $vaultId")

            val failedChains = mutableListOf<String>()
            toEnableAccounts.forEach { chain ->
                try {
                    enableAccount(chain.coin, vault)
                } catch (e: CancellationException) {
                    // Rethrow to preserve coroutine cancellation: `CancellationException`
                    // extends `IllegalStateException`, so the broader catch below would
                    // otherwise swallow it.
                    throw e
                } catch (e: Exception) {
                    // Covers `require(...)`/`error(...)`, `hexToByteArray` parse errors, Trust
                    // Wallet Core's `PublicKey` JNI constructor `InvalidParameterException`, and
                    // the gomobile `Tss.getDerivedPubKey` checked `Exception` reachable from the
                    // ECDSA branch via `PublicKeyHelper.getDerivedPublicKey` for vaults with a
                    // malformed `pubKeyECDSA` or `hexChainCode`.
                    Timber.w(e, "Failed to enable chain %s", chain.coin.chain.raw)
                    failedChains += chain.coin.chain.raw
                    return@forEach
                }
                runCatching { discoverTokenUseCase(vaultId, chain.coin.chain.raw) }
                    .onFailure {
                        Timber.w(
                            it,
                            "Token discovery scheduling failed for chain %s",
                            chain.coin.chain.raw,
                        )
                    }
            }
            toDisableAccounts.forEach { disableAccount(it.coin) }

            if (failedChains.isNotEmpty()) {
                val shown = failedChains.take(MAX_FAILED_CHAINS_SHOWN).joinToString(", ")
                val overflow = failedChains.size - MAX_FAILED_CHAINS_SHOWN
                val summary = if (overflow > 0) "$shown +$overflow" else shown
                snackbarFlow.showMessage(
                    R.string.chain_selection_enable_failed.asUiText(summary),
                    SnackbarType.Error,
                )
            }

            if (routeFromInitVault) {
                navigator.route(
                    route = Route.Home(),
                    opts = NavigationOptions(clearBackStack = true),
                )
            } else {
                requestResultRepository.respond(REFRESH_CHAIN_DATA, Unit)
                navigator.back()
            }
        }
    }

    fun onBackClick() {
        viewModelScope.launch { navigator.back() }
    }

    fun setSearchText(searchText: String) {
        searchTextFieldState.setTextAndPlaceCursorAtEnd(text = searchText)
    }

    private suspend fun enableAccount(nativeToken: Coin, vault: Vault) {
        val (address, derivedPublicKey) =
            chainAccountAddressRepository.getAddress(nativeToken, vault)
        val updatedCoin = nativeToken.copy(address = address, hexPublicKey = derivedPublicKey)

        vaultRepository.addTokenToVault(vaultId, updatedCoin)
    }

    private suspend fun disableAccount(coin: Coin) {
        vaultRepository.deleteChainFromVault(vaultId, coin.chain)
    }

    private fun loadChains() {
        viewModelScope.launch {
            val vault =
                vaultRepository.get(vaultId)
                    ?: run {
                        navigator.back()
                        return@launch
                    }
            val isKeyImport = vault.libType == SigningLibType.KeyImport

            uiState.update { it.copy(isKeyImportVault = isKeyImport) }

            combine(
                    tokenRepository.nativeTokens,
                    vaultRepository.getEnabledChains(vaultId),
                    searchTextFieldState.textAsFlow(),
                ) { tokens, enabledChains, query ->
                    tokens
                        .filter { token -> !isKeyImport || vault.hasPreGeneratedKey(token.chain) }
                        .filter { token ->
                            token.chain.TssKeysignType != TssKeyType.MLDSA ||
                                vault.pubKeyMLDSA.isNotBlank()
                        }
                        .filter {
                            query.isBlank() ||
                                it.ticker.contains(query, ignoreCase = true) ||
                                it.chain.raw.contains(query, ignoreCase = true)
                        }
                        .map { token ->
                            ChainUiModel(isEnabled = token.chain in enabledChains, coin = token)
                        }
                        .sortedWith(compareBy({ it.coin.ticker }, { it.coin.chain.raw }))
                }
                .collect { chains -> uiState.update { it.copy(chains = chains) } }
        }
    }

    private companion object {
        private const val MAX_FAILED_CHAINS_SHOWN = 5
    }
}
