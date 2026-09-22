package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.api.chains.ton.tonUserFriendlyAddress
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/**
 * A vault's Tonstakers position: its tsTON balance and the jetton wallet that holds it, which is
 * where an unstake's burn is sent. [jettonWalletAddress] is null while the vault has never held
 * tsTON (no wallet is deployed), and the balance is then zero.
 */
data class TonLiquidPosition(val tsTonBalance: BigInteger, val jettonWalletAddress: String?) {
    val hasPosition: Boolean
        get() = tsTonBalance.signum() > 0
}

/**
 * What the pool says about itself. [totalBalance] over [supply] is the tsTON→TON rate; [apy] and
 * [minStake] come from tonapi's pool listing and are display/validation metadata, so either may be
 * missing on a partial read without blocking the position.
 */
data class TonLiquidPoolState(
    val totalBalance: BigInteger,
    val supply: BigInteger,
    val apy: Double?,
    val minStake: BigInteger?,
    val isDepositOpen: Boolean,
    val isOptimistic: Boolean,
) {
    /** TON (nanotons) the pool would pay for [tsTon] base units at the current rate, floored. */
    fun tonValueOf(tsTon: BigInteger): BigInteger =
        if (supply.signum() > 0) tsTon * totalBalance / supply else BigInteger.ZERO

    /** tsTON base units the pool mints for a net [depositAmount] (nanotons), floored. */
    fun tsTonFor(depositAmount: BigInteger): BigInteger =
        if (totalBalance.signum() > 0) depositAmount * supply / totalBalance else BigInteger.ZERO
}

/**
 * Reads the two halves of a Tonstakers position. The tsTON balance and jetton wallet come from the
 * indexer's jetton-wallet lookup — the same call a jetton transfer resolves its wallet with — and
 * the pool state from tonapi. Nothing here is cached: the screens that need it read it on open and
 * on refresh, as the nominator screens do.
 */
class TonLiquidStakingService
internal constructor(
    private val tonApi: TonApi,
    private val tonStakingApi: TonStakingApi,
    /**
     * The user-friendly bounceable spelling of a TON address, or null for one that will not
     * convert. A parameter because WalletCore's converter is a native call with no host-JVM binary.
     */
    private val toBounceable: (String) -> String?,
) {

    @Inject
    constructor(
        tonApi: TonApi,
        tonStakingApi: TonStakingApi,
    ) : this(tonApi, tonStakingApi, ::tonUserFriendlyAddress)

    suspend fun getPosition(ownerAddress: String): TonLiquidPosition {
        val wallet =
            tonApi
                .getJettonWallet(ownerAddress, Tonstakers.TSTON_MASTER_ADDRESS)
                .matchingWallet(Tonstakers.TSTON_MASTER_ADDRESS, toBounceable)
                ?: return TonLiquidPosition(BigInteger.ZERO, null)
        // The burn has to reach the wallet bounceable, whichever spelling the indexer used.
        val jettonWallet = toBounceable(wallet.address) ?: wallet.address
        return TonLiquidPosition(
            tsTonBalance = wallet.balance.toBigIntegerOrNull() ?: BigInteger.ZERO,
            jettonWalletAddress = jettonWallet,
        )
    }

    /**
     * The pool's rate and gates, or null when the get-method read failed — without the rate a
     * position has no value and a stake no preview, so callers fail closed. The listing metadata
     * (APY, minimum) is best-effort on top.
     */
    suspend fun getPoolState(): TonLiquidPoolState? = coroutineScope {
        val fullData = async { tonStakingApi.getLiquidPoolData(Tonstakers.POOL_ADDRESS) }
        val listing = async {
            try {
                tonStakingApi.getStakingPool(Tonstakers.POOL_ADDRESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Tonstakers pool listing unavailable")
                null
            }
        }
        val data = fullData.await() ?: return@coroutineScope null
        val info = listing.await()
        TonLiquidPoolState(
            totalBalance = BigInteger.valueOf(data.totalBalance),
            supply = BigInteger.valueOf(data.supply),
            apy = info?.apy,
            minStake = info?.minStake?.takeIf { it > 0 }?.let(BigInteger::valueOf),
            isDepositOpen = data.isDepositOpen,
            isOptimistic = data.isOptimistic,
        )
    }
}
