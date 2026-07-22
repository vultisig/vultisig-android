@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.swap

import androidx.compose.ui.geometry.Offset
import com.vultisig.wallet.data.crypto.getChainName
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.isSecuredAsset
import com.vultisig.wallet.data.models.isSecuredAssetEligible
import com.vultisig.wallet.data.models.isSwapSupported
import com.vultisig.wallet.data.models.securedAssetChain
import com.vultisig.wallet.data.models.securedAssetSymbol
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.ui.models.firstSendSrc
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.select.AssetSelected
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal class SwapTokenSelector
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val accountsRepository: AccountsRepository,
    private val requestResultRepository: RequestResultRepository,
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,
) {

    fun loadTokens(
        vaultId: String,
        addresses: MutableStateFlow<List<Address>>,
        scope: CoroutineScope,
    ) {
        scope.launch {
            accountsRepository
                .loadAddresses(vaultId)
                .map { addrs -> addrs.filter { it.chain.isSwapSupported } }
                .catch {
                    Timber.e(it)
                    emit(emptyList())
                }
                .collect(addresses)
        }
    }

    fun collectSelectedTokens(
        addresses: MutableStateFlow<List<Address>>,
        selectedSrcId: MutableStateFlow<String?>,
        selectedDstId: MutableStateFlow<String?>,
        selectedSrc: MutableStateFlow<SendSrc?>,
        selectedDst: MutableStateFlow<SendSrc?>,
        chain: StateFlow<Chain?>,
        selectTokensJob: Job?,
        scope: CoroutineScope,
    ): Job {
        selectTokensJob?.cancel()
        return scope.launch {
            combine(addresses.filter { it.isNotEmpty() }, selectedSrcId, selectedDstId, chain) {
                    addrs,
                    srcTokenId,
                    dstTokenId,
                    currentChain ->
                    selectedSrc.updateSrc(srcTokenId, addrs, currentChain)
                    selectedDst.updateSrc(dstTokenId, addrs, currentChain)
                }
                .collect()
        }
    }

    fun collectSelectedAccounts(
        selectedSrc: MutableStateFlow<SendSrc?>,
        selectedDst: MutableStateFlow<SendSrc?>,
        uiState: MutableStateFlow<SwapFormUiModel>,
        scope: CoroutineScope,
    ) {
        scope.launch {
            combine(selectedSrc, selectedDst) { src, dst ->
                    val srcUiModel = src?.let { accountToTokenBalanceUiModelMapper(it) }
                    val dstUiModel = dst?.let { accountToTokenBalanceUiModelMapper(it) }
                    // A native source pays the gas out of the same balance being swapped, so a MAX
                    // amount is never actually spendable — hide the shortcut instead of letting it
                    // fill an amount the fee then invalidates.
                    val isSrcNative = src?.account?.token?.isNativeToken ?: false
                    uiState.update {
                        it.copy(
                            selectedSrcToken = srcUiModel,
                            selectedDstToken = dstUiModel,
                            enableMaxAmount = isSrcNative.not(),
                        )
                    }
                }
                .collect()
        }
    }

    suspend fun selectNetwork(
        vaultId: VaultId,
        selectedChain: Chain,
        addresses: List<Address>,
    ): SendSrc? {
        val requestId = Uuid.random().toString()
        navigator.route(
            Route.SelectNetwork(
                vaultId = vaultId,
                selectedNetworkId = selectedChain.id,
                requestId = requestId,
                filters = Route.SelectNetwork.Filters.SwapAvailable,
            )
        )
        val chain: Chain = requestResultRepository.request(requestId) ?: return null
        if (chain == selectedChain) return null
        return addresses.firstSendSrc(selectedTokenId = null, filterByChain = chain)
    }

    suspend fun selectNetworkPopup(
        vaultId: VaultId,
        selectedChain: Chain,
        position: Offset,
        addresses: List<Address>,
    ): SendSrc? {
        val requestId = Uuid.random().toString()
        navigator.route(
            Route.SelectNetworkPopup(
                requestId = requestId,
                pressX = position.x,
                pressY = position.y,
                vaultId = vaultId,
                selectedNetworkId = selectedChain.id,
                filters = Route.SelectNetwork.Filters.SwapAvailable,
            )
        )
        val chain: Chain = requestResultRepository.request(requestId) ?: return null
        if (chain == selectedChain) return null
        return addresses.firstSendSrc(selectedTokenId = null, filterByChain = chain)
    }

    suspend fun navigateToSelectToken(
        targetArg: String,
        vaultId: String,
        selectedSrc: SendSrc?,
        selectedDst: SendSrc?,
        selectedSrcId: MutableStateFlow<String?>,
        selectedDstId: MutableStateFlow<String?>,
        addresses: MutableStateFlow<List<Address>>,
        uiState: MutableStateFlow<SwapFormUiModel>,
        isSelectionQuotable: (Coin) -> Boolean,
    ) {
        // A fresh id per visit (matching selectNetwork/selectNetworkPopup) — targetArg is a fixed
        // constant shared by every source/destination pick, so using it directly as the requestId
        // let a response buffered by RequestResultRepository (e.g. from a stale/racing selection)
        // leak into a later, unrelated visit and silently resolve it.
        val requestId = Uuid.random().toString()
        navigator.route(
            Route.SelectAsset(
                vaultId = vaultId,
                requestId = requestId,
                preselectedNetworkId =
                    (when (targetArg) {
                            ARG_SELECTED_SRC_TOKEN_ID -> selectedSrc?.address?.chain
                            ARG_SELECTED_DST_TOKEN_ID -> selectedDst?.address?.chain
                            else -> Chain.ThorChain
                        })
                        ?.id ?: Chain.ThorChain.id,
                networkFilters = Route.SelectNetwork.Filters.SwapAvailable,
            )
        )
        checkTokenSelectionResponse(
            requestId,
            targetArg,
            vaultId,
            selectedSrc,
            selectedSrcId,
            selectedDstId,
            addresses,
            uiState,
            isSelectionQuotable,
        )
    }

    private suspend fun checkTokenSelectionResponse(
        requestId: String,
        targetArg: String,
        vaultId: String,
        selectedSrc: SendSrc?,
        selectedSrcId: MutableStateFlow<String?>,
        selectedDstId: MutableStateFlow<String?>,
        addresses: MutableStateFlow<List<Address>>,
        uiState: MutableStateFlow<SwapFormUiModel>,
        isSelectionQuotable: (Coin) -> Boolean,
    ) {
        val result = requestResultRepository.request<AssetSelected>(requestId) ?: return

        if (targetArg == ARG_SELECTED_DST_TOKEN_ID) {
            val srcToken = selectedSrc?.account?.token
            if (srcToken != null && srcToken.isUnderlyingOfSecuredAsset(result.token)) {
                // Same-underlying-asset swap: route to the SECURE+ mint deposit flow instead of a
                // wasteful 1:1 pool swap.
                navigator.route(Route.Deposit(vaultId = vaultId, chainId = srcToken.chain.id))
                return
            }
        }

        if (result.isDisabled) {
            // Only raise the shared loading flag when the pair the pick forms is actually quotable
            // —
            // a positive amount AND a routable (distinct, provider-backed) pair. Otherwise loading
            // a
            // not-yet-held token's account would flash the destination/fee skeletons true→false
            // over
            // a field that will never quote — the same blink the pipeline gate removes, and
            // matching
            // its isPairRoutable && amount>0 condition rather than a looser amount-only check
            // (#5296
            // review).
            val showLoading = isSelectionQuotable(result.token)
            if (showLoading) uiState.update { it.copy(isLoading = true) }
            try {
                val account = accountsRepository.loadAccount(vaultId, result.token)
                updateAccountInAddresses(account, addresses)
                if (showLoading) uiState.update { it.copy(isLoading = false) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to load account for token")
                if (showLoading) uiState.update { it.copy(isLoading = false) }
                return
            }
        }

        if (targetArg == ARG_SELECTED_SRC_TOKEN_ID) {
            selectedSrcId.value = result.token.id
        } else {
            selectedDstId.value = result.token.id
        }
    }

    private fun updateAccountInAddresses(
        loadedAccount: Account,
        addresses: MutableStateFlow<List<Address>>,
    ) {
        addresses.update { listOfAddresses ->
            listOfAddresses.map { address ->
                if (address.chain == loadedAccount.token.chain) {
                    val alreadyExists =
                        address.accounts.any { it.token.id == loadedAccount.token.id }
                    if (alreadyExists) address
                    else address.copy(accounts = address.accounts + loadedAccount)
                } else {
                    address
                }
            }
        }
    }

    companion object {
        const val ARG_SELECTED_SRC_TOKEN_ID = "ARG_SELECTED_SRC_TOKEN_ID"
        const val ARG_SELECTED_DST_TOKEN_ID = "ARG_SELECTED_DST_TOKEN_ID"
    }
}

/** True when [securedAsset] is the THORChain secured form of this coin's own native asset. */
internal fun Coin.isUnderlyingOfSecuredAsset(securedAsset: Coin): Boolean =
    isSecuredAssetEligible() &&
        securedAsset.isSecuredAsset() &&
        getChainName().equals(securedAsset.securedAssetChain(), ignoreCase = true) &&
        ticker.equals(securedAsset.securedAssetSymbol(), ignoreCase = true)
