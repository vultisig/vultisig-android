package com.vultisig.wallet.ui.models.deposit.load

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.LpBondablePool
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.usecases.GetThorChainLpPositionUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.defi.parseThorChainPool
import com.vultisig.wallet.ui.models.deposit.BondedUnitsCeiling
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.deposit.RemoveLpCalculator
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Owns LP / liquidity-pool data loading extracted from `DepositFormViewModel` so the Maya bondable
 * assets, Maya remove-LP and THORChain remove-LP fetches plus the slider/max helpers live in one
 * cohesive, independently testable unit. Results are written back into the shared [state]; the pure
 * redeem-amount math is delegated to [RemoveLpCalculator].
 *
 * The repos / use case are Hilt-injected here; the ViewModel keeps `viewModelScope` ownership and
 * supplies it (assisted) along with the form-owned state flows / field states / accessors so this
 * loader never owns its own scope.
 */
internal class LiquidityDataLoader
@AssistedInject
constructor(
    private val mayachainBondRepository: MayachainBondRepository,
    private val getThorChainLpPositionUseCase: GetThorChainLpPositionUseCase,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val state: MutableStateFlow<DepositFormUiModel>,
    @Assisted private val address: StateFlow<Address?>,
    @Assisted("assetsField") private val assetsFieldState: TextFieldState,
    @Assisted("lpUnitsField") private val lpUnitsFieldState: TextFieldState,
    @Assisted("vaultId") private val vaultId: () -> String?,
    @Assisted("lpPoolId") private val lpPoolId: () -> String?,
    @Assisted private val resolvePairedAddress: suspend (Chain, String, String) -> String?,
) {

    /**
     * Builds a [LiquidityDataLoader] for one deposit form. The repos / use case are Hilt-injected;
     * the ViewModel supplies its [scope], the shared UI [state], the [address] flow, the LP field
     * states, and the form-owned accessors / callbacks as assisted params.
     */
    @AssistedFactory
    interface Factory {
        /** Creates a [LiquidityDataLoader] bound to the given scope, state and form accessors. */
        fun create(
            scope: CoroutineScope,
            state: MutableStateFlow<DepositFormUiModel>,
            address: StateFlow<Address?>,
            @Assisted("assetsField") assetsFieldState: TextFieldState,
            @Assisted("lpUnitsField") lpUnitsFieldState: TextFieldState,
            @Assisted("vaultId") vaultId: () -> String?,
            @Assisted("lpPoolId") lpPoolId: () -> String?,
            resolvePairedAddress: suspend (Chain, String, String) -> String?,
        ): LiquidityDataLoader
    }

    private var lpBondPoolMap: Map<String, LpBondablePool> = emptyMap()
    private var bondedUnitsByPool: Map<String, Long> = emptyMap()
    private var bondedUnitsNodeAddress: String = ""
    private var loadMayaBondableAssetsJob: Job? = null
    private var loadLpJob: Job? = null

    /** Returns the bondable pool previously loaded for [asset], or `null` if not loaded. */
    fun bondPoolFor(asset: String): LpBondablePool? = lpBondPoolMap[asset]

    /**
     * The unbond ceiling for [asset] on the node the bonded units were last loaded for, or `null`
     * when that node holds nothing in that pool for this vault.
     */
    fun bondedCeilingFor(asset: String): BondedUnitsCeiling? =
        bondedUnitsByPool[asset]?.let {
            BondedUnitsCeiling(
                nodeAddress = bondedUnitsNodeAddress,
                asset = asset,
                units = it.toString(),
            )
        }

    /** Cancels any in-flight remove-LP fetch so it can't write stale state into a new option. */
    fun cancelLoad() {
        loadMayaBondableAssetsJob?.cancel()
        loadLpJob?.cancel()
    }

    /** Loads the Maya bondable assets (and their LP units/depths) for the current user address. */
    fun loadMayaBondableAssets() {
        loadMayaBondableAssetsJob?.cancel()
        lpBondPoolMap = emptyMap()
        clearBondedUnits()
        state.update {
            it.copy(
                bondableAssets = emptyList(),
                selectedBondAsset = "",
                availableLpUnits = null,
                bondedUnitsCeiling = null,
                bondAssetsLoadFailed = false,
                removeLpUnitsDivisor = BigInteger.ZERO,
                removeLpPoolDepth = BigInteger.ZERO,
            )
        }
        assetsFieldState.clearText()
        loadMayaBondableAssetsJob =
            scope.safeLaunch(onError = ::onBondAssetsLoadFailed) {
                val userAddress =
                    withTimeoutOrNull(ADDRESS_AWAIT_TIMEOUT_MS) { address.filterNotNull().first() }
                        ?.address
                        ?: run {
                            state.update {
                                it.copy(
                                    errorText =
                                        UiText.StringResource(R.string.dialog_default_error_body)
                                )
                            }
                            return@safeLaunch
                        }
                val poolMap =
                    withContext(Dispatchers.IO) {
                        mayachainBondRepository.getLpBondableAssetsWithUnits(userAddress)
                    }
                lpBondPoolMap = poolMap
                val assets = poolMap.keys.toList()
                val firstAsset = assets.firstOrNull() ?: ""
                val firstPool = poolMap[firstAsset]
                state.update {
                    it.copy(
                        bondableAssets = assets,
                        selectedBondAsset = firstAsset,
                        availableLpUnits = firstPool?.availableUnits,
                        removeLpUnitsDivisor =
                            firstPool?.totalPoolLpUnits?.toBigInteger() ?: BigInteger.ZERO,
                        removeLpPoolDepth =
                            firstPool?.poolCacaoDepth?.toBigInteger() ?: BigInteger.ZERO,
                    )
                }
                if (firstAsset.isNotEmpty()) {
                    assetsFieldState.setTextAndPlaceCursorAtEnd(firstAsset)
                }
            }
    }

    /**
     * Loads the Maya pools [nodeAddress] holds LP units for on behalf of this vault (Unbond).
     *
     * Deliberately not the bondable-asset load: that one is address-wide and reports the surplus
     * *not* yet bonded, so it hides exactly the pools a fully-bonded user came here to unbond.
     *
     * A blank [nodeAddress] is not a failure — the field is simply not filled in yet — so it clears
     * the list and stops. Anything that did fail is recorded on the state, because an empty list on
     * its own would otherwise claim the node holds nothing when we never found out.
     */
    fun loadMayaBondedAssets(nodeAddress: String) {
        loadMayaBondableAssetsJob?.cancel()
        lpBondPoolMap = emptyMap()
        clearBondedUnits()
        state.update {
            it.copy(
                bondableAssets = emptyList(),
                selectedBondAsset = "",
                availableLpUnits = null,
                bondedUnitsCeiling = null,
                bondAssetsLoadFailed = false,
                lpUnitsError = null,
                removeLpUnitsDivisor = BigInteger.ZERO,
                removeLpPoolDepth = BigInteger.ZERO,
            )
        }
        assetsFieldState.clearText()
        if (nodeAddress.isBlank()) return
        loadMayaBondableAssetsJob =
            scope.safeLaunch(onError = ::onBondAssetsLoadFailed) {
                val bondAddress =
                    withTimeoutOrNull(ADDRESS_AWAIT_TIMEOUT_MS) { address.filterNotNull().first() }
                        ?.address
                        ?: run {
                            // Not knowing our own address is a load failure like any other: the
                            // node may well hold a position, we just cannot ask about it.
                            state.update { it.copy(bondAssetsLoadFailed = true) }
                            return@safeLaunch
                        }
                val bondedByPool =
                    withContext(Dispatchers.IO) {
                        mayachainBondRepository.getBondedLpUnitsOnNode(
                            nodeAddress = nodeAddress,
                            bondAddress = bondAddress,
                        )
                    }
                bondedUnitsByPool = bondedByPool
                bondedUnitsNodeAddress = nodeAddress
                val assets = bondedByPool.keys.toList()
                val firstAsset = assets.firstOrNull() ?: ""
                state.update {
                    it.copy(
                        bondableAssets = assets,
                        selectedBondAsset = firstAsset,
                        bondedUnitsCeiling = bondedCeilingFor(firstAsset),
                    )
                }
                if (firstAsset.isNotEmpty()) {
                    assetsFieldState.setTextAndPlaceCursorAtEnd(firstAsset)
                }
            }
    }

    /** Forgets the loaded unbond position so no ceiling can outlive the node it was read from. */
    private fun clearBondedUnits() {
        bondedUnitsByPool = emptyMap()
        bondedUnitsNodeAddress = ""
    }

    private fun onBondAssetsLoadFailed(error: Throwable) {
        Timber.e(error, "Error loading Maya bond assets")
        state.update { it.copy(bondAssetsLoadFailed = true) }
    }

    /**
     * Loads the user's Maya remove-LP position (units, CACAO depth, balance) for the active pool.
     */
    fun loadRemoveLpData() {
        val poolId =
            lpPoolId()
                ?: run {
                    state.update {
                        it.copy(
                            availableLpUnits = null,
                            removeLpUnitsDivisor = BigInteger.ZERO,
                            removeLpPoolDepth = BigInteger.ZERO,
                            // This branch returns before the reset below, so it has to clear the
                            // asset leg itself or a previous pool's second amount stays on screen.
                            removeLpAssetDisplay = "",
                            removeLpAssetSymbol = "",
                            removeLpAssetRedeemBase = BigInteger.ZERO,
                            balance = UiText.Empty,
                            errorText = UiText.StringResource(R.string.dialog_default_error_body),
                        )
                    }
                    return
                }
        state.update {
            it.copy(
                availableLpUnits = null,
                removeLpUnitsDivisor = BigInteger.ZERO,
                removeLpPoolDepth = BigInteger.ZERO,
                removeLpPercent = 0f,
                removeLpCacaoDisplay = "",
                // Clear the asset leg a previous selection left behind; the Maya pool's own asset
                // depth fills it back in below, once loaded.
                removeLpAssetDisplay = "",
                removeLpAssetSymbol = "",
                removeLpAssetRedeemBase = BigInteger.ZERO,
                balance = R.string.share_balance_loading.asUiText(),
                errorText = null,
            )
        }
        loadLpJob?.cancel()
        loadLpJob =
            scope.safeLaunch {
                val userAddress =
                    withTimeoutOrNull(ADDRESS_AWAIT_TIMEOUT_MS) { address.filterNotNull().first() }
                        ?.address
                        ?: run {
                            state.update {
                                it.copy(
                                    balance = UiText.Empty,
                                    errorText =
                                        UiText.StringResource(R.string.dialog_default_error_body),
                                )
                            }
                            return@safeLaunch
                        }
                val memberDetails =
                    withContext(Dispatchers.IO) {
                        mayachainBondRepository.getMemberDetails(userAddress)
                    }
                val userLpUnits =
                    memberDetails.pools.find { it.pool == poolId }?.liquidityUnits
                        ?: run {
                            state.update {
                                it.copy(
                                    availableLpUnits = null,
                                    removeLpUnitsDivisor = BigInteger.ZERO,
                                    removeLpPoolDepth = BigInteger.ZERO,
                                    balance = UiText.Empty,
                                    errorText =
                                        UiText.StringResource(R.string.dialog_default_error_body),
                                )
                            }
                            return@safeLaunch
                        }
                val poolStats =
                    withContext(Dispatchers.IO) { mayachainBondRepository.getLpPoolStats() }
                val pool =
                    poolStats.find { it.asset == poolId }
                        ?: run {
                            state.update {
                                it.copy(
                                    availableLpUnits = null,
                                    removeLpUnitsDivisor = BigInteger.ZERO,
                                    removeLpPoolDepth = BigInteger.ZERO,
                                    balance = UiText.Empty,
                                    errorText =
                                        UiText.StringResource(R.string.dialog_default_error_body),
                                )
                            }
                            return@safeLaunch
                        }
                val totalPoolUnits = pool.units.toBigIntegerOrNull() ?: BigInteger.ZERO
                val cacaoDepth = pool.cacaoDepth.toBigIntegerOrNull() ?: BigInteger.ZERO
                // A symmetric Maya withdrawal returns both halves, same as THORChain, so the form
                // shows the paired asset leg too. It shares removeLpUnitsDivisor with the CACAO
                // leg: selectedUnits * assetDepth / totalPoolUnits.
                val assetDepth = pool.assetDepth.toBigIntegerOrNull() ?: BigInteger.ZERO
                val assetSymbol =
                    if (assetDepth.signum() > 0) parseThorChainPool(poolId).ticker else ""
                val userAvailableUnits = userLpUnits.toBigIntegerOrNull()
                val userCacao =
                    if (userAvailableUnits != null) {
                        RemoveLpCalculator.computeAmountDisplay(
                            selectedUnits = userAvailableUnits,
                            poolDepth = cacaoDepth,
                            totalPoolUnits = totalPoolUnits,
                            decimals = RemoveLpCalculator.CACAO_DECIMALS,
                        )
                    } else null
                val balanceText =
                    if (userCacao != null) {
                        UiText.FormattedText(
                            R.string.remove_pool_amount_format,
                            listOf(userCacao, "CACAO"),
                        )
                    } else UiText.Empty
                state.update {
                    it.copy(
                        availableLpUnits = userLpUnits,
                        removeLpUnitsDivisor = totalPoolUnits,
                        removeLpPoolDepth = cacaoDepth,
                        removeLpDecimals = RemoveLpCalculator.CACAO_DECIMALS,
                        removeLpTokenSymbol = "CACAO",
                        removeLpAssetRedeemBase = assetDepth,
                        removeLpAssetSymbol = assetSymbol,
                        balance = balanceText,
                    )
                }
                setRemoveLpPercent(state.value.removeLpPercent)
            }
    }

    /** Loads the user's THORChain remove-LP position (units, RUNE redeem value, balance). */
    fun loadThorChainRemoveLpData() {
        val poolId =
            lpPoolId()
                ?: run {
                    state.update {
                        it.copy(
                            availableLpUnits = null,
                            removeLpUnitsDivisor = BigInteger.ZERO,
                            removeLpPoolDepth = BigInteger.ZERO,
                            removeLpDecimals = RemoveLpCalculator.RUNE_DECIMALS,
                            removeLpTokenSymbol = Coins.ThorChain.RUNE.ticker,
                            removeLpAssetDisplay = "",
                            removeLpAssetSymbol = "",
                            removeLpAssetRedeemBase = BigInteger.ZERO,
                            balance = UiText.Empty,
                            errorText = UiText.StringResource(R.string.dialog_default_error_body),
                        )
                    }
                    return
                }
        state.update {
            it.copy(
                availableLpUnits = null,
                removeLpUnitsDivisor = BigInteger.ZERO,
                removeLpPoolDepth = BigInteger.ZERO,
                removeLpDecimals = RemoveLpCalculator.RUNE_DECIMALS,
                removeLpTokenSymbol = Coins.ThorChain.RUNE.ticker,
                removeLpPercent = 0f,
                removeLpCacaoDisplay = "",
                removeLpAssetDisplay = "",
                removeLpAssetSymbol = "",
                removeLpAssetRedeemBase = BigInteger.ZERO,
                balance = R.string.share_balance_loading.asUiText(),
                errorText = null,
            )
        }
        loadLpJob?.cancel()
        loadLpJob =
            scope.safeLaunch {
                val userAddress =
                    withTimeoutOrNull(ADDRESS_AWAIT_TIMEOUT_MS) { address.filterNotNull().first() }
                        ?.address
                        ?: run {
                            state.update {
                                it.copy(
                                    balance = UiText.Empty,
                                    errorText =
                                        UiText.StringResource(R.string.dialog_default_error_body),
                                )
                            }
                            return@safeLaunch
                        }
                val currentVaultId = vaultId()
                val pairedAddress =
                    if (currentVaultId != null) {
                        resolvePairedAddress(Chain.ThorChain, currentVaultId, poolId)
                    } else null
                val position =
                    withContext(Dispatchers.IO) {
                        getThorChainLpPositionUseCase(
                            poolId = poolId,
                            runeAddress = userAddress,
                            assetAddress = pairedAddress,
                        )
                    }

                if (position == null || position.units <= BigInteger.ZERO) {
                    state.update {
                        it.copy(
                            availableLpUnits = null,
                            removeLpUnitsDivisor = BigInteger.ZERO,
                            removeLpPoolDepth = BigInteger.ZERO,
                            balance = UiText.Empty,
                            errorText = UiText.StringResource(R.string.dialog_default_error_body),
                        )
                    }
                    return@safeLaunch
                }

                // Use the pre-computed redeem value from the use case as `poolDepth` and the user's
                // own
                // units as `totalPoolUnits`. With selectedUnits = percent * userUnits, the
                // calculator
                // produces percent * runeRedeemValue, which is the symmetric RUNE half of
                // withdrawal.
                // Keep BigInteger end-to-end for whale positions whose units exceed Long.MAX_VALUE.
                val userUnits = position.units
                val runeRedeemBase = position.runeRedeemValue
                val symbol = Coins.ThorChain.RUNE.ticker
                val userRune =
                    RemoveLpCalculator.computeAmountDisplay(
                        selectedUnits = userUnits,
                        poolDepth = runeRedeemBase,
                        totalPoolUnits = userUnits,
                        decimals = RemoveLpCalculator.RUNE_DECIMALS,
                    )
                val balanceText =
                    if (userRune != null) {
                        UiText.FormattedText(
                            R.string.remove_pool_amount_format,
                            listOf(userRune, symbol),
                        )
                    } else UiText.Empty
                state.update {
                    it.copy(
                        availableLpUnits = userUnits.toString(),
                        removeLpUnitsDivisor = userUnits,
                        removeLpPoolDepth = runeRedeemBase,
                        removeLpDecimals = RemoveLpCalculator.RUNE_DECIMALS,
                        removeLpTokenSymbol = symbol,
                        removeLpAssetRedeemBase = position.assetRedeemValue,
                        removeLpAssetSymbol = parseThorChainPool(poolId).ticker,
                        balance = balanceText,
                    )
                }
                setRemoveLpPercent(state.value.removeLpPercent)
            }
    }

    /** Fills the LP-units field with the full available units for the loaded position. */
    fun setMaxLpUnits() {
        val current = state.value
        val units =
            when (current.depositOption) {
                DepositOption.Unbond -> current.bondedUnitsCeiling?.units
                else -> current.availableLpUnits
            } ?: return
        lpUnitsFieldState.setTextAndPlaceCursorAtEnd(units)
        state.update { it.copy(lpUnitsError = null) }
    }

    /** Applies a slider [percent] (0f..1f) to compute the selected units and redeem display. */
    fun setRemoveLpPercent(percent: Float) {
        val s = state.value
        val availableUnits = s.availableLpUnits?.toBigIntegerOrNull() ?: return
        // Keep the slider→units math fully in BigInteger so whale positions whose units exceed
        // Long.MAX_VALUE still move the slider and compute exact withdrawal amounts. `percent` is a
        // 0f..1f fraction; convert it to integer basis points (0..10000) to retain sub-percent
        // precision, then `units * bps / 10000`.
        val basisPoints = (percent * 10_000).toInt().coerceIn(0, 10_000)
        val selectedUnits =
            availableUnits.multiply(basisPoints.toBigInteger()).divide(BigInteger.valueOf(10_000L))
        val cacaoDisplay =
            RemoveLpCalculator.computeAmountDisplay(
                    selectedUnits = selectedUnits,
                    poolDepth = s.removeLpPoolDepth,
                    totalPoolUnits = s.removeLpUnitsDivisor,
                    decimals = s.removeLpDecimals,
                )
                ?.let(RemoveLpCalculator::trimTrailingZeros) ?: return
        // The asset leg is priced per unit far above RUNE on some pools, so it keeps thornode's
        // full 1e8 precision instead of the three decimals that suit RUNE and CACAO — at three it
        // would round to 0.000 and read as nothing to withdraw.
        val assetDisplay =
            if (s.removeLpAssetSymbol.isEmpty()) ""
            else
                RemoveLpCalculator.computeAmountDisplay(
                        selectedUnits = selectedUnits,
                        poolDepth = s.removeLpAssetRedeemBase,
                        totalPoolUnits = s.removeLpUnitsDivisor,
                        decimals = RemoveLpCalculator.RUNE_DECIMALS,
                        scale = RemoveLpCalculator.RUNE_DECIMALS,
                    )
                    ?.let(RemoveLpCalculator::trimTrailingZeros)
                    .orEmpty()
        lpUnitsFieldState.setTextAndPlaceCursorAtEnd(selectedUnits.toString())
        state.update {
            it.copy(
                removeLpPercent = percent,
                removeLpBasisPoints = basisPoints,
                removeLpCacaoDisplay = cacaoDisplay,
                removeLpAssetDisplay = assetDisplay,
            )
        }
    }

    companion object {
        private const val ADDRESS_AWAIT_TIMEOUT_MS = 5_000L
    }
}
