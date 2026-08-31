package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.getIncReserve
import com.vultisig.wallet.data.api.matches
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.RIPPLE_SEED_OWNER_RESERVE_DROPS
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.rippleTokenIdentity
import java.math.BigInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/** Adding the token to a vault makes its balance readable; receiving any needs a `TrustSet`. */
interface RippleTrustLines {

    suspend fun tokensNeedingTrustLine(address: String, coins: List<Coin>): Set<TokenId>

    /** XRP a trust line adds to the account's reserve, from `reserve_inc`. */
    suspend fun fetchOwnerReserve(): BigInteger
}

internal class RippleTrustLinesImpl @Inject constructor(private val rippleApi: RippleApi) :
    RippleTrustLines {

    override suspend fun tokensNeedingTrustLine(address: String, coins: List<Coin>): Set<TokenId> {
        val tokens = coins.mapNotNull { coin -> coin.rippleTokenIdentity()?.let { coin.id to it } }
        if (tokens.isEmpty()) return emptySet()

        val lines = accountLinesOrNull(address) ?: return emptySet()

        // A line with a zero or negative balance still exists; only its absence needs activating.
        return tokens
            .filterNot { (_, identity) -> lines.any { it.matches(identity) } }
            .map { (tokenId, _) -> tokenId }
            .toSet()
    }

    override suspend fun fetchOwnerReserve(): BigInteger =
        try {
            rippleApi.fetchServerState().getIncReserve()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "XRPL server_state failed while reading the owner reserve")
            RIPPLE_SEED_OWNER_RESERVE_DROPS
        }

    // Null rather than empty: without evidence a line is missing, do not offer to open one.
    private suspend fun accountLinesOrNull(address: String) =
        try {
            rippleApi.fetchAccountLines(address)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "XRPL account_lines failed while checking trust lines")
            null
        }
}
