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
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
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
    private var loadLpJob: Job? = null

    /** Returns the bondable pool previously loaded for [asset], or `null` if not loaded. */
    fun bondPoolFor(asset: String): LpBondablePool? = lpBondPoolMap[asset]

    /** Cancels any in-flight remove-LP fetch so it can't write stale state into a new option. */
    fun cancelLoad() {
        loadLpJob?.cancel()
    }

    /** Loads the Maya bondable assets (and their LP units/depths) for the current user address. */
    fun loadMayaBondableAssets() {
        state.update {
            it.copy(
                bondableAssets = emptyList(),
                selectedBondAsset = "",
                availableLpUnits = null,
                removeLpUnitsDivisor = BigInteger.ZERO,
                removeLpPoolDepth = BigInteger.ZERO,
            )
        }
        assetsFieldState.clearText()
        scope.safeLaunch {
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
                    removeLpPoolDepth = firstPool?.poolCacaoDepth?.toBigInteger() ?: BigInteger.ZERO,
                )
            }
            if (firstAsset.isNotEmpty()) {
                assetsFieldState.setTextAndPlaceCursorAtEnd(firstAsset)
            }
        }
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
                                    errorText =
                                        UiText.StringResource(R.string.dialog_default_error_body)
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
                                    errorText =
                                        UiText.StringResource(R.string.dialog_default_error_body),
                                )
                            }
                            return@safeLaunch
                        }
                val totalPoolUnits = pool.units.toBigIntegerOrNull() ?: BigInteger.ZERO
                val cacaoDepth = pool.cacaoDepth.toBigIntegerOrNull() ?: BigInteger.ZERO
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
                                    errorText =
                                        UiText.StringResource(R.string.dialog_default_error_body)
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
                        balance = balanceText,
                    )
                }
                setRemoveLpPercent(state.value.removeLpPercent)
            }
    }

    /** Fills the LP-units field with the full available units for the loaded position. */
    fun setMaxLpUnits() {
        val units = state.value.availableLpUnits ?: return
        lpUnitsFieldState.setTextAndPlaceCursorAtEnd(units)
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
            ) ?: return
        lpUnitsFieldState.setTextAndPlaceCursorAtEnd(selectedUnits.toString())
        state.update {
            it.copy(
                removeLpPercent = percent,
                removeLpBasisPoints = basisPoints,
                removeLpCacaoDisplay = cacaoDisplay,
            )
        }
    }

    companion object {
        private const val ADDRESS_AWAIT_TIMEOUT_MS = 5_000L
    }
}
