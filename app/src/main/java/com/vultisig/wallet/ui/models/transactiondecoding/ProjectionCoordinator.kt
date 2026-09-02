package com.vultisig.wallet.ui.models.transactiondecoding

import android.content.Context
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.ui.components.hero.HeroCoinAmount
import com.vultisig.wallet.ui.components.hero.HeroContent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Turns a decoded reading into a hero that says what is certain immediately, and what is estimated
 * when it arrives.
 *
 * Mirrors the iOS `ProjectionCoordinator`. Only quantities settled at execution get a committed
 * scope; an absolute signed amount has nothing to project and is rendered by
 * [DecodedTransactionPresentation] instead.
 */
@Singleton
internal class ProjectionCoordinator
@Inject
constructor(@ApplicationContext private val context: Context) {

    /** Returns a committed scope only for quantities settled at execution. */
    fun scope(decoded: DecodedTransaction): String? =
        when (val amount = decoded.amount) {
            is DecodedAmount.Fraction ->
                // The signed share remains exact even when the projected amount is not.
                context.getString(
                    R.string.withdrawing_share_of_staked_position,
                    DecodedTransactionPresentation.percentage(amount.basisPoints),
                )

            DecodedAmount.Unstated ->
                when (decoded.operation) {
                    DecodedOperation.Unstake -> context.getString(R.string.scope_your_whole_stake)
                    DecodedOperation.ClaimRewards ->
                        context.getString(R.string.scope_rewards_accrued_so_far)
                    else -> null
                }

            is DecodedAmount.Units -> null
        }

    /** Builds the immediate verb-and-scope hero; an estimate is optional. */
    fun hero(
        decoded: DecodedTransaction,
        title: String,
        estimate: HeroCoinAmount? = null,
    ): HeroContent? {
        val scope = scope(decoded) ?: return null
        return HeroContent.Projected(title = title, estimate = estimate, scope = scope)
    }

    companion object {
        /** Short enough that optional chain state cannot stall the done screen. */
        val TIMEOUT = 5.seconds

        /**
         * Applies the same deadline to every optional position read. An ordinary reader failure — a
         * network error, a malformed response, an empty answer — degrades to the existing scope
         * rather than fabricating a number.
         *
         * Cancellation is rethrown rather than swallowed. The deadline itself cancels through
         * [withTimeoutOrNull], and a caller going away (the view model being cleared) must not be
         * turned into a silent null that leaves the rest of the coroutine running.
         */
        suspend fun estimate(read: suspend () -> HeroCoinAmount?): HeroCoinAmount? =
            withTimeoutOrNull(TIMEOUT) {
                try {
                    read()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
            }
    }
}
