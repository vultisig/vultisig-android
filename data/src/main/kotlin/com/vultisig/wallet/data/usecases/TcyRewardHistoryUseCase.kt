package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.TcyUserDistributionsResponse.TcyUserDistribution
import java.math.BigInteger
import java.util.Date
import javax.inject.Inject

/** What one daily TCY distribution paid a staker, in RUNE base units. */
data class TcyRewardPayout(val date: Date, val amount: BigInteger)

/** Every RUNE payout the TCY stake of an address has received. */
interface TcyRewardHistoryUseCase {

    /**
     * The payouts to [address], newest first; empty when it was never paid. Rows Midgard reports
     * with an unparseable or non-positive amount or date are left out.
     */
    suspend operator fun invoke(address: String): List<TcyRewardPayout>
}

internal class TcyRewardHistoryUseCaseImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : TcyRewardHistoryUseCase {

    override suspend fun invoke(address: String): List<TcyRewardPayout> =
        thorChainApi
            .fetchTcyUserDistributions(address)
            .distributions
            .orEmpty()
            .mapNotNull { it.parse() }
            .sortedByDescending { it.date }
}

// Midgard reports a TCY distribution's date in seconds, unlike a churn's nanoseconds.
private fun TcyUserDistribution.parse(): TcyRewardPayout? {
    val amount = amount.toBigIntegerOrNull()?.takeIf { it.signum() > 0 } ?: return null
    val seconds =
        date.toLongOrNull()?.takeIf { it in 1..MAX_DATE_SECONDS } ?: return null
    return TcyRewardPayout(date = Date(seconds * MILLIS_PER_SECOND), amount = amount)
}

private const val MILLIS_PER_SECOND = 1_000L

// The largest second count whose millisecond value still fits in a Long.
private const val MAX_DATE_SECONDS = Long.MAX_VALUE / MILLIS_PER_SECOND
