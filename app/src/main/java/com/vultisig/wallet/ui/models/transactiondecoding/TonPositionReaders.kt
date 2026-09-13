package com.vultisig.wallet.ui.models.transactiondecoding

import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.transaction_decoding.DecodedCounterparty
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.ui.components.hero.HeroCoinAmount
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import wallet.core.jni.TONAddressConverter

/**
 * Resolves a TON nominator-pool withdrawal to the whole position it releases.
 *
 * The signed transfer carries only the 0.2 TON withdraw signal fee — never the stake — so the
 * figure lives in chain state, keyed by the pool the signed comment is addressed to. A just-placed
 * deposit still awaiting its cycle is part of what the pool returns, so it counts alongside the
 * active stake.
 *
 * Mirrors the iOS `TonStakedPositionReader`. Like [TcyStakedPositionReader], this serves the
 * initiator as well as a joining co-signer: both verify surfaces resolve the same reading.
 */
@Singleton
internal class TonStakedPositionReader
internal constructor(
    private val tonStakingApi: TonStakingApi,
    private val presentation: DecodedTransactionPresentation,
    /**
     * The user-friendly bounceable spelling of a TON address, or null for one that will not
     * convert. A parameter, as on the other TON surfaces, because wallet-core's converter is a
     * native call with no host-JVM binary.
     */
    private val toBounceable: (String) -> String?,
) : PositionReading {

    @Inject
    constructor(
        tonStakingApi: TonStakingApi,
        presentation: DecodedTransactionPresentation,
    ) : this(
        tonStakingApi,
        presentation,
        { TONAddressConverter.toUserFriendly(it, /* bounceable= */ true, /* testnet= */ false) },
    )

    override fun handles(decoded: DecodedTransaction, coin: Coin): Boolean =
        coin.chain == Chain.Ton &&
            decoded.operation == DecodedOperation.Unstake &&
            decoded.counterparty is DecodedCounterparty.Pool

    override suspend fun amount(decoded: DecodedTransaction, coin: Coin): HeroCoinAmount? {
        val signedPool = (decoded.counterparty as? DecodedCounterparty.Pool)?.value ?: return null

        // Read against the vault's own address, and matched on the exact pool the signed comment
        // is sent to — not the largest position the owner holds: a wallet can stake in several
        // pools, and describing the wrong one is worse than describing none.
        val position =
            tonStakingApi.getNominatorPools(coin.address).firstOrNull {
                sameAddress(it.pool, signedPool)
            } ?: return null

        val raw = BigInteger.valueOf(position.amount) + BigInteger.valueOf(position.pendingDeposit)
        if (raw.signum() <= 0) return null

        return presentation.heroAmount(coin, raw)
    }

    /**
     * tonapi reports pools in raw `0:hex` form while the signed destination is user-friendly and
     * bounceable, so both are normalised to the same form before comparing. An address that will
     * not convert is compared as written.
     */
    private fun sameAddress(lhs: String, rhs: String): Boolean = normalise(lhs) == normalise(rhs)

    private fun normalise(address: String): String =
        runCatching { toBounceable(address) }.getOrNull() ?: address
}
