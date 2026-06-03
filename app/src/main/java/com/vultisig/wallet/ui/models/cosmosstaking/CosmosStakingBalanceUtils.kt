package com.vultisig.wallet.ui.models.cosmosstaking

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.BalanceRepository
import java.math.BigDecimal

/**
 * Cached spendable native-coin balance for [coin] in human-decimal units, read from the same
 * [BalanceRepository] path the send-form uses. Falls back to zero on a cache miss — for the staking
 * fee preflights this conservatively trips the insufficient-fee warning until the next refresh,
 * preferring a false-positive block over a false-negative MPC burn.
 *
 * Single source for the spendable-balance read shared by the delegate / undelegate / redelegate /
 * withdraw-rewards view-models (each injects [BalanceRepository]). Call inside the IO dispatcher.
 */
internal suspend fun BalanceRepository.cachedSpendableBalance(coin: Coin): BigDecimal =
    runCatching {
            val pair = getCachedTokenBalanceAndPrice(coin.address, coin)
            val tokenValue = pair.tokenBalance.tokenValue ?: return@runCatching BigDecimal.ZERO
            BigDecimal(tokenValue.value).movePointLeft(coin.decimal)
        }
        .getOrDefault(BigDecimal.ZERO)
