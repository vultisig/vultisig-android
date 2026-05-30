@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.ui.geometry.Offset
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.select.AssetSelected
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns token & network selection and the chain-switching navigation around it.
 *
 * Navigates to the selection screens, awaits the result via [RequestResultRepository], lazily adds
 * a missing native token to the vault, and pins the new token selection. Extracted from
 * `SendFormViewModel` to keep the coordinator focused; it operates on the same shared
 * [selectedToken] state the ViewModel exposes, so selection stays in sync across collaborators.
 *
 * @param scope the owning ViewModel's `viewModelScope`.
 * @param navigator app navigator used to route to the selection screens.
 * @param requestResultRepository channel used to await a selection result by request id.
 * @param tokenRepository source of native tokens when one must be added to the vault.
 * @param vaultRepository vault store used to persist a newly added native token.
 * @param chainAccountAddressRepository derives the address/public key for an added token.
 * @param tokenPreselectionService preselects a token after a forced chain switch.
 * @param accountsLoader reloads accounts after a native token is added.
 * @param amountFractionManager preempted on token switch to guard the in-flight percentage race.
 * @param amountManager whose user-input cache is reset on token switch.
 * @param vaultIdProvider supplies the current vault id.
 * @param accounts the current list of vault accounts.
 * @param selectedToken shared selection state read and written by the delegate.
 * @param expandSection callback used to expand a form section after a selection.
 */
internal class TokenNetworkSelectionDelegate(
    private val scope: CoroutineScope,
    private val navigator: Navigator<Destination>,
    private val requestResultRepository: RequestResultRepository,
    private val tokenRepository: TokenRepository,
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tokenPreselectionService: TokenPreselectionService,
    private val accountsLoader: AccountsLoader,
    private val amountFractionManager: AmountFractionManager,
    private val amountManager: AmountManager,
    private val vaultIdProvider: () -> String?,
    private val accounts: StateFlow<List<Account>>,
    private val selectedToken: MutableStateFlow<Coin?>,
    private val expandSection: (SendSections) -> Unit,
) {

    private val selectedTokenValue: Coin?
        get() = selectedToken.value

    /** Navigates to the network selection screen and applies the chosen chain. */
    fun selectNetwork() {
        scope.launch {
            val vaultId = vaultIdProvider() ?: return@launch
            val selectedChain = selectedTokenValue?.chain ?: return@launch

            val requestId = Uuid.random().toString()

            navigator.route(
                Route.SelectNetwork(
                    vaultId = vaultId,
                    selectedNetworkId = selectedChain.id,
                    requestId = requestId,
                    filters = Route.SelectNetwork.Filters.None,
                )
            )

            updateChain(requestId = requestId, selectedChain = selectedChain)
        }
    }

    /**
     * Opens the network selection popup anchored at [position] and applies the chosen chain.
     *
     * @param position the screen offset of the long-press that triggered the popup.
     */
    fun onNetworkLongPressStarted(position: Offset) {
        scope.launch {
            val vaultId = vaultIdProvider() ?: return@launch
            val selectedChain = selectedTokenValue?.chain ?: return@launch

            val requestId = Uuid.random().toString()

            navigator.route(
                Route.SelectNetworkPopup(
                    requestId = requestId,
                    pressX = position.x,
                    pressY = position.y,
                    vaultId = vaultId,
                    selectedNetworkId = selectedChain.id,
                    filters = Route.SelectNetwork.Filters.None,
                )
            )

            updateChain(requestId, selectedChain)
        }
    }

    private suspend fun updateChain(requestId: String, selectedChain: Chain) {
        val chain: Chain? = requestResultRepository.request(requestId)

        if (chain == null || chain == selectedChain) {
            return
        }

        val account =
            accounts.value.find { it.token.isNativeToken && it.token.chain == chain } ?: return

        selectToken(account.token)
    }

    /** Navigates to the token selection screen and applies the chosen token. */
    fun openTokenSelection() {
        val vaultId = vaultIdProvider() ?: return
        scope.launch {
            val requestId = Uuid.random().toString()

            val selectedChain = selectedToken.value?.chain ?: Chain.ThorChain
            navigator.route(
                Route.SelectAsset(
                    vaultId = vaultId,
                    preselectedNetworkId = selectedChain.id,
                    networkFilters = Route.SelectNetwork.Filters.None,
                    requestId = requestId,
                )
            )

            val newAssetSelected = requestResultRepository.request<AssetSelected?>(requestId)
            val newToken = newAssetSelected?.token

            if (newToken != null) {
                selectToken(newToken)
                expandSection(SendSections.Address)
            }
        }
    }

    /**
     * Opens the token selection popup anchored at [position] and applies the chosen token.
     *
     * @param position the screen offset of the long-press that triggered the popup.
     */
    fun openTokenSelectionPopup(position: Offset) {
        val vaultId = vaultIdProvider() ?: return
        scope.launch {
            val requestId = Uuid.random().toString()

            val selectedChain = selectedToken.value?.chain ?: Chain.ThorChain
            navigator.route(
                Route.SelectAssetPopup(
                    vaultId = vaultId,
                    preselectedNetworkId = selectedChain.id,
                    requestId = requestId,
                    pressX = position.x,
                    pressY = position.y,
                    selectedAssetId = selectedToken.value?.id.orEmpty(),
                )
            )

            val newAssetSelected = requestResultRepository.request<AssetSelected?>(requestId)
            val newToken = newAssetSelected?.token

            if (newToken != null) {
                selectToken(newToken)
                expandSection(SendSections.Address)
            }
        }
    }

    /**
     * Adds the native token of the first preselected chain to the vault when that chain is missing
     * from [accounts], then reloads accounts.
     *
     * @param preSelectedChainIds candidate chain ids; the first is used for the addition.
     * @param vaultId the vault to reload after a successful addition.
     */
    fun checkChainIdExistInAccounts(preSelectedChainIds: List<String>, vaultId: String) {
        // if chain Id is missing in accounts, add the first chain found by address manually.
        val chainIdForAddition = preSelectedChainIds.firstOrNull()
        val chainIdNotInAccounts =
            accounts.value.none { it.token.chain.id.equals(chainIdForAddition, ignoreCase = true) }
        if (!chainIdForAddition.isNullOrBlank() && chainIdNotInAccounts) {
            scope.launch {
                addNativeTokenToVault(chainIdForAddition)
                accountsLoader.load(vaultId)
            }
        }
    }

    private suspend fun addNativeTokenToVault(chainIdForAddition: ChainId) {
        val nativeToken = tokenRepository.getNativeToken(chainIdForAddition)
        val vaultId = requireNotNull(vaultIdProvider())
        val vault = requireNotNull(vaultRepository.get(vaultId))
        val (address, derivedPublicKey) =
            chainAccountAddressRepository.getAddress(coin = nativeToken, vault = vault)
        val updatedCoin = nativeToken.copy(address = address, hexPublicKey = derivedPublicKey)

        vaultRepository.addTokenToVault(vaultId, updatedCoin)
    }

    /**
     * Forces a token preselection when switching to a different, non-EVM chain.
     *
     * @param currentChain the currently selected chain.
     * @param newChain the chain being switched to.
     */
    fun checkIfTokenSelectionRequired(currentChain: Chain, newChain: Chain) {
        val newChainSelected = currentChain != newChain
        val isNotEvm = newChain.standard != TokenStandard.EVM
        if (newChainSelected && isNotEvm) {
            tokenPreselectionService.preSelect(
                preSelectedChainIds = listOf(newChain.id),
                preSelectedTokenId = null,
                forcePreselection = true,
            )
        }
    }

    /**
     * Pins [token] as the current selection, preempting in-flight amount calculations first.
     *
     * @param token the token to select.
     */
    fun selectToken(token: Coin) {
        Timber.d("selectToken(token = $token)")

        // Preempt any in-flight percentage calc — otherwise it can resume after the token
        // switch and write the old token's amount into tokenAmountFieldState (e.g. the
        // autocompound toggle hits this same race that setTronResourceType already preempts).
        amountFractionManager.cancel()
        amountManager.resetUserInputCache()
        selectedToken.value = token
    }
}
