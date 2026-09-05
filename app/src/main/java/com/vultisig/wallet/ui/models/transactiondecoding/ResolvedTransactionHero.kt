package com.vultisig.wallet.ui.models.transactiondecoding

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.data.models.transaction_decoding.SignedTransactionContent
import com.vultisig.wallet.data.models.transaction_decoding.SignedTransactionDecoder
import com.vultisig.wallet.ui.components.hero.HeroCoinAmount
import com.vultisig.wallet.ui.components.hero.HeroContent
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves an execution-set amount from chain state. */
internal interface PositionReading {

    /** Whether this reader answers for the decoded transaction on this coin. */
    fun handles(decoded: DecodedTransaction, coin: Coin): Boolean

    /** The resolved amount, or null when the read failed or returned nothing. */
    suspend fun amount(decoded: DecodedTransaction, coin: Coin): HeroCoinAmount?
}

/**
 * Resolves optional chain-state amounts behind decoder-layer readers, keeping chain knowledge out
 * of the keysign view models.
 *
 * Mirrors the iOS `ResolvedTransactionHero`. With no reader matching — or with a read that fails or
 * times out — callers fall back to the signed amount the decoder already read.
 */
@Singleton
internal class ResolvedTransactionHero
@Inject
constructor(
    private val decoder: SignedTransactionDecoder,
    private val projectionCoordinator: ProjectionCoordinator,
    solanaDelegatedAmount: SolanaDelegatedAmountReader,
    solanaStakeAccountAmount: SolanaStakeAccountAmountReader,
) {

    /**
     * Chain readers, in the order they are asked. Each declares the operations it answers for, so
     * the first match wins and no reader sees a transaction it did not claim.
     */
    private val readers: List<PositionReading> =
        listOf(solanaDelegatedAmount, solanaStakeAccountAmount)

    /**
     * Resolves through the first reader that answers for [coin] — the vault's own coin for this
     * transaction, which the caller already holds.
     *
     * The coin is passed rather than searched for: a reader claims an operation and a chain, not a
     * particular token, so scanning the vault for the first coin a reader accepts could hand a
     * Solana stake reading an SPL token and price the amount at that token's decimals and rate.
     */
    suspend fun resolve(
        content: SignedTransactionContent,
        coin: Coin,
        title: String,
        readers: List<PositionReading> = this.readers,
    ): HeroContent? {
        val decoded = decoder.decode(content)

        for (reader in readers) {
            if (!reader.handles(decoded, coin)) continue
            val amount = ProjectionCoordinator.estimate { reader.amount(decoded, coin) }
            return projectionCoordinator.hero(decoded, title, amount)
        }
        return null
    }
}
