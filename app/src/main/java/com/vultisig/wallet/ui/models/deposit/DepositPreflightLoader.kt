package com.vultisig.wallet.ui.models.deposit

import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.LpBondablePool
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.usecases.GetMayaCacaoMaturityStatusUseCase
import com.vultisig.wallet.data.usecases.GetThorChainLpPositionUseCase
import com.vultisig.wallet.data.usecases.MayaCacaoMaturityStatus
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Reusable loading/preflight helpers extracted from `DepositFormViewModel` so the address, gas-fee,
 * Maya bondable-asset, remove-LP, CACAO-maturity and node-whitelist lookups are unit-testable in
 * isolation. Stateless: all per-form context (vault, chain, address) is passed in by the caller,
 * and each method returns plain data the caller maps into UI state. Coroutine orchestration (jobs,
 * `viewModelScope`, field-state mutation and `_state` updates) stays in the ViewModel.
 */
internal class DepositPreflightLoader
@Inject
constructor(
    private val accountsRepository: AccountsRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val mayachainBondRepository: MayachainBondRepository,
    private val getMayaCacaoMaturityStatus: GetMayaCacaoMaturityStatusUseCase,
    private val getThorChainLpPositionUseCase: GetThorChainLpPositionUseCase,
    private val gasFeeHelper: DepositGasFeeHelper,
) {

    /** Streams the [Address] for [vaultId] on [chain]. */
    fun loadAddress(vaultId: String, chain: Chain): Flow<Address> =
        accountsRepository.loadAddress(vaultId, chain)

    /**
     * Computes the displayable gas fee for a deposit on [chain] using the native token of
     * [address], or `null` when the address has no native-token account.
     */
    suspend fun loadGasFeeForDisplay(
        vaultId: String,
        chain: Chain,
        address: Address,
    ): EstimatedGasFee? {
        val token = address.accounts.find { it.token.isNativeToken }?.token ?: return null
        val srcAddress = token.address
        val gasFee = gasFeeHelper.calculateGasFee(vaultId, chain, token, srcAddress)
        val specific =
            blockChainSpecificRepository.getSpecific(
                chain,
                srcAddress,
                token,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )
        return gasFeeHelper.getFeesFiatValue(chain, specific, gasFee, token)
    }

    /** Fetches the Maya bondable-asset → pool map for [userAddress]. */
    suspend fun loadMayaBondableAssets(userAddress: String): Map<String, LpBondablePool> =
        withContext(Dispatchers.IO) {
            mayachainBondRepository.getLpBondableAssetsWithUnits(userAddress)
        }

    /**
     * Fetches the Maya remove-LP position for [poolId] held by [userAddress], or `null` when the
     * member has no units in the pool or the pool stats are unavailable.
     */
    suspend fun loadMayaRemoveLpData(poolId: String, userAddress: String): MayaRemoveLpResult? {
        val memberDetails =
            withContext(Dispatchers.IO) { mayachainBondRepository.getMemberDetails(userAddress) }
        val userLpUnits =
            memberDetails.pools.find { it.pool == poolId }?.liquidityUnits ?: return null
        val poolStats = withContext(Dispatchers.IO) { mayachainBondRepository.getLpPoolStats() }
        val pool = poolStats.find { it.asset == poolId } ?: return null
        val totalPoolUnits = pool.units.toBigIntegerOrNull() ?: BigInteger.ZERO
        val cacaoDepth = pool.cacaoDepth.toBigIntegerOrNull() ?: BigInteger.ZERO
        val userAvailableUnits = userLpUnits.toBigIntegerOrNull()
        val userCacaoDisplay =
            if (userAvailableUnits != null) {
                RemoveLpCalculator.computeAmountDisplay(
                    selectedUnits = userAvailableUnits,
                    poolDepth = cacaoDepth,
                    totalPoolUnits = totalPoolUnits,
                    decimals = RemoveLpCalculator.CACAO_DECIMALS,
                )
            } else null
        return MayaRemoveLpResult(
            availableLpUnits = userLpUnits,
            totalPoolUnits = totalPoolUnits,
            cacaoDepth = cacaoDepth,
            userCacaoDisplay = userCacaoDisplay,
        )
    }

    /**
     * Fetches the THORChain remove-LP position for [poolId] (RUNE side held by [runeAddress], asset
     * side by [assetAddress]), or `null` when the position is missing or empty.
     */
    suspend fun loadThorChainRemoveLpData(
        poolId: String,
        runeAddress: String,
        assetAddress: String?,
    ): ThorRemoveLpResult? {
        val position =
            withContext(Dispatchers.IO) {
                getThorChainLpPositionUseCase(
                    poolId = poolId,
                    runeAddress = runeAddress,
                    assetAddress = assetAddress,
                )
            }
        if (position == null || position.units <= BigInteger.ZERO) return null
        // Use the pre-computed redeem value as `poolDepth` and the user's own units as both the
        // selected and total units, so the calculator yields the full symmetric RUNE redeem value.
        val userUnits = position.units
        val runeRedeemBase = position.runeRedeemValue
        val userRuneDisplay =
            RemoveLpCalculator.computeAmountDisplay(
                selectedUnits = userUnits,
                poolDepth = runeRedeemBase,
                totalPoolUnits = userUnits,
                decimals = RemoveLpCalculator.RUNE_DECIMALS,
            )
        return ThorRemoveLpResult(
            userUnits = userUnits,
            runeRedeemBase = runeRedeemBase,
            userRuneDisplay = userRuneDisplay,
        )
    }

    /** Resolves the CACAO unstake maturity status for [addressValue]. */
    suspend fun loadCacaoMaturityStatus(addressValue: String): MayaCacaoMaturityStatus =
        getMayaCacaoMaturityStatus(addressValue)

    /**
     * Returns whether [userAddress] is whitelisted as a bond provider on the Maya node at
     * [nodeAddress].
     */
    suspend fun isNodeWhitelisted(nodeAddress: String, userAddress: String): Boolean {
        val nodeInfo = mayachainBondRepository.getNodeDetails(nodeAddress)
        return nodeInfo.bondProviders.providers.any { it.bondAddress == userAddress }
    }
}

/** Result of [DepositPreflightLoader.loadMayaRemoveLpData]. */
internal data class MayaRemoveLpResult(
    val availableLpUnits: String,
    val totalPoolUnits: BigInteger,
    val cacaoDepth: BigInteger,
    val userCacaoDisplay: String?,
)

/** Result of [DepositPreflightLoader.loadThorChainRemoveLpData]. */
internal data class ThorRemoveLpResult(
    val userUnits: BigInteger,
    val runeRedeemBase: BigInteger,
    val userRuneDisplay: String?,
)
