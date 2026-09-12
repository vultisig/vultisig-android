package com.vultisig.wallet.ui.models.transactiondecoding

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedEvidence
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.ui.components.hero.HeroCoinAmount
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a `TCY-:<bps>` withdrawal against the signer's own staked TCY position.
 *
 * ⚠️ **A projection, not a commitment.** The memo commits to a FRACTION of whatever is staked when
 * THORChain executes it; this applies that fraction to the position the chain reports now, which is
 * the closest an absolute figure can get. The scope sentence beside it states the share, and stays
 * true whether or not this read lands — which is why a failed read leaves the hero figure-less
 * rather than falling back to the literal `0` the transaction carries.
 *
 * Mirrors the iOS `TcyStakedPositionReader`. Unlike iOS, this serves the initiator too: Android's
 * verify surfaces resolve the same reading on both devices, so there is no separate builder-quoted
 * path (iOS's `QuotedWithdrawalPresentation`) to keep in step with it.
 */
@Singleton
internal class TcyStakedPositionReader
@Inject
constructor(
    private val thorChainApi: ThorChainApi,
    private val presentation: DecodedTransactionPresentation,
) : PositionReading {

    override fun handles(decoded: DecodedTransaction, coin: Coin): Boolean =
        coin.chain == Chain.ThorChain &&
            coin.ticker.equals(TCY_TICKER, ignoreCase = true) &&
            decoded.operation == DecodedOperation.Unstake &&
            // The sidecar memo or the same memo signed inside a THORChain body.
            decoded.evidence.isNoWeaker(than = DecodedEvidence.Memo) &&
            (decoded.amount as? DecodedAmount.Fraction)?.asset == DecodedAsset.TransactionCoin

    override suspend fun amount(decoded: DecodedTransaction, coin: Coin): HeroCoinAmount? {
        val basisPoints = (decoded.amount as? DecodedAmount.Fraction)?.basisPoints ?: return null
        if (basisPoints !in 1..MAX_BASIS_POINTS) return null

        // Read against the vault's own address; a joining co-signer's payload never chooses it.
        val staked = thorChainApi.getUnstakableTcyAmount(coin.address)?.toBigIntegerOrNull()
        if (staked == null || staked.signum() <= 0) return null

        // Base units throughout, truncating exactly as the unstake form's basis points were
        // derived, so the figure can never claim more than the share the memo commits to.
        val amount =
            staked.multiply(BigInteger.valueOf(basisPoints.toLong())).divide(MAX_BASIS_POINTS_UNITS)
        if (amount.signum() <= 0) return null

        return presentation.heroAmount(coin, amount)
    }

    private companion object {
        const val TCY_TICKER = "TCY"
        const val MAX_BASIS_POINTS = 10_000
        val MAX_BASIS_POINTS_UNITS: BigInteger = BigInteger.valueOf(MAX_BASIS_POINTS.toLong())
    }
}
