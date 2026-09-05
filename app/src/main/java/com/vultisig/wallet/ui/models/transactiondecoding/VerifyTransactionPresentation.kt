package com.vultisig.wallet.ui.models.transactiondecoding

import android.content.Context
import androidx.compose.runtime.Immutable
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.transaction_decoding.SignedTransactionContent
import com.vultisig.wallet.data.models.transaction_decoding.SignedTransactionDecoder
import com.vultisig.wallet.ui.components.hero.HeroContent
import com.vultisig.wallet.ui.components.hero.retitled
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One verify-surface reading of a transaction, carrying the pieces the surface needs to place it
 * against whatever hero it already resolved.
 *
 * Mirrors the ordering the iOS `TransactionHeroResolver` registers: a chain-state projection
 * outranks a simulation because it states a scope no figure can ("your whole stake"); a simulation
 * outranks the plain decoder because it prices a balance change the signed bytes never state, and
 * only borrows the decoded verb; the signed-amount reading is the last claimant.
 */
@Immutable
internal data class VerifyHero(
    /** The verb alone, for a surface that already holds richer figures. */
    val verb: String,
    /** A chain-state projection, when a reader resolved one. Null until chain readers land. */
    val projected: HeroContent?,
    /** The hero built from the signed amount alone. */
    val decoded: HeroContent,
) {
    /** Places this reading over the hero [existing] the surface resolved on its own. */
    fun applyTo(existing: HeroContent?): HeroContent =
        projected ?: existing?.retitled(verb) ?: decoded
}

/**
 * Adapts the shared signed-transaction reading to Verify's present-progressive vocabulary. It owns
 * no chain parsing and no amount formatting.
 *
 * Mirrors the iOS `DecodedTransactionPresentation` verify path. The iOS surface/provider registry
 * is not ported: Android already expresses hero precedence as branch order on each verify surface —
 * a Blockaid or TON simulation, a limit-order title, an XRPL trust line — so [VerifyHero.applyTo]
 * carries the one ordering decision the registry existed to encode.
 *
 * The initiator and a joining co-signer both read through [SignedTransactionContent], so the two
 * devices show the same verb for the same transaction.
 */
@Singleton
internal class VerifyTransactionPresentation
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val decoder: SignedTransactionDecoder,
    private val presentation: DecodedTransactionPresentation,
    private val resolvedTransactionHero: ResolvedTransactionHero,
) {

    /**
     * Reads [content], or returns null for a deliberately silent operation — a plain transfer, a
     * swap, an approval, a vote, an unknown call — which leaves the surface's own presentation
     * exactly as it is.
     *
     * A projection that fails or times out degrades to the signed amount; it never blocks the
     * verify screen and never fabricates a number.
     */
    suspend fun resolve(
        content: SignedTransactionContent,
        coin: Coin,
        trustedCoins: List<Coin>,
    ): VerifyHero? {
        val decoded = decoder.decode(content)
        val title =
            DecodedTransactionPresentation.verifyTitleRes(decoded.operation)
                ?.let(context::getString) ?: return null

        // A projection reads chain state keyed off the coin, so it runs only on the vault's own
        // coin; the decoded hero falls back to the peer-supplied one for its display metadata.
        val trusted = trustedCoins.trustedCoinFor(coin)

        return VerifyHero(
            verb = title,
            projected = trusted?.let { resolvedTransactionHero.resolve(content, it, title) },
            decoded = presentation.hero(decoded, trusted ?: coin, title),
        )
    }
}
