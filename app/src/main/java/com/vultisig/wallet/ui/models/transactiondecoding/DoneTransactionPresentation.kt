package com.vultisig.wallet.ui.models.transactiondecoding

import android.content.Context
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.SignedTransactionDecoder
import com.vultisig.wallet.data.models.transaction_decoding.asSignedTransactionContent
import com.vultisig.wallet.ui.components.hero.HeroContent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapts the shared signed-transaction reading to Done's completed-action vocabulary. It owns no
 * chain parsing and no amount formatting.
 *
 * Mirrors the iOS `DoneTransactionPresentation`.
 */
@Singleton
internal class DoneTransactionPresentation
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val decoder: SignedTransactionDecoder,
    private val presentation: DecodedTransactionPresentation,
    private val resolvedTransactionHero: ResolvedTransactionHero,
) {

    /**
     * The past-tense verb for a specifically-read operation, or null to keep the normal-send card's
     * existing presentation.
     */
    fun specificTitle(payload: KeysignPayload): String? {
        val operation = decoder.decode(payload.asSignedTransactionContent()).operation
        if (operation in FALLBACK_OPERATIONS) return null
        return DecodedTransactionPresentation.doneTitleRes(operation)?.let(context::getString)
    }

    /** The synchronous hero, built from the signed amount alone. */
    suspend fun hero(payload: KeysignPayload, trustedCoins: List<Coin>): HeroContent? {
        val decoded = decoder.decode(payload.asSignedTransactionContent())
        if (decoded.operation in FALLBACK_OPERATIONS) return null
        val title =
            DecodedTransactionPresentation.doneTitleRes(decoded.operation)?.let(context::getString)
                ?: return null

        return presentation.hero(decoded, trustedCoins.trustedMatchFor(payload.coin), title)
    }

    /**
     * The hero including any optional chain-state projection. A projection that fails or times out
     * degrades to [hero]; it never blocks the done screen and never fabricates a number.
     */
    suspend fun resolve(payload: KeysignPayload, trustedCoins: List<Coin>): HeroContent? {
        val content = payload.asSignedTransactionContent()
        val decoded = decoder.decode(content)
        if (decoded.operation in FALLBACK_OPERATIONS) return null
        val title =
            DecodedTransactionPresentation.doneTitleRes(decoded.operation)?.let(context::getString)
                ?: return null

        // A projection reads chain state keyed off the coin, so it runs only on the vault's own
        // coin; the fallback hero takes the peer-supplied one for its display metadata.
        val trusted = trustedCoins.trustedCoinFor(payload.coin)

        trusted?.let { coin ->
            resolvedTransactionHero.resolve(content, coin, title)?.let {
                return it
            }
        }

        return presentation.hero(decoded, trusted ?: payload.coin, title)
    }

    companion object {
        /**
         * These readings intentionally retain the normal-send card's `Sent` fallback and its
         * already-computed amount. A generic contract call or an unreadable transaction does not
         * justify replacing that richer fallback.
         */
        private val FALLBACK_OPERATIONS =
            setOf(
                DecodedOperation.Transfer,
                DecodedOperation.ContractCall,
                DecodedOperation.Unknown,
            )
    }
}
