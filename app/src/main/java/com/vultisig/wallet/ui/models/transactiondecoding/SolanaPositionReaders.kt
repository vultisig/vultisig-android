package com.vultisig.wallet.ui.models.transactiondecoding

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingConfig
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedCounterparty
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.ui.components.hero.HeroCoinAmount
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the exact funding of a new stake account into the stake that will actually be active.
 *
 * A delegation funds the account with the stake plus its rent-exempt reserve, and only the chain
 * can say what that reserve currently is. Until it is read, the signed figure overstates the stake
 * — which is why the decoder classifies it as [DecodedAmount.AccountFunding] and no screen renders
 * it directly.
 *
 * Mirrors the iOS `SolanaDelegatedAmountReader`.
 */
@Singleton
internal class SolanaDelegatedAmountReader
@Inject
constructor(
    private val solanaApi: SolanaApi,
    private val presentation: DecodedTransactionPresentation,
) : PositionReading {

    override fun handles(decoded: DecodedTransaction, coin: Coin): Boolean =
        coin.chain == Chain.Solana &&
            decoded.operation == DecodedOperation.Delegate &&
            decoded.amount is DecodedAmount.AccountFunding

    override suspend fun amount(decoded: DecodedTransaction, coin: Coin): HeroCoinAmount? {
        val funding =
            (decoded.amount as? DecodedAmount.AccountFunding)
                ?.takeIf { it.asset == DecodedAsset.ChainNative }
                ?.value ?: return null

        // Read live, never from the bundled fallback constant. A stale reserve subtracted from an
        // exact funding produces a wrong headline figure; showing no figure leaves the scope
        // sentence, which is true either way.
        val reserve =
            solanaApi.getMinimumBalanceForRentExemption(SolanaStakingConfig.STAKE_ACCOUNT_SPACE)
        if (reserve <= BigInteger.ZERO || funding <= reserve) return null

        return presentation.heroAmount(coin, funding - reserve)
    }
}

/**
 * Resolves a whole-account deactivation to the lamports actually delegated in that account.
 *
 * Deactivation names no quantity — it cools the entire account — so the amount lives in chain
 * state, keyed by the stake account the signed instruction names.
 *
 * Mirrors the iOS `SolanaStakeAccountAmountReader`.
 */
@Singleton
internal class SolanaStakeAccountAmountReader
@Inject
constructor(
    private val stakingService: SolanaStakingService,
    private val presentation: DecodedTransactionPresentation,
) : PositionReading {

    override fun handles(decoded: DecodedTransaction, coin: Coin): Boolean =
        coin.chain == Chain.Solana &&
            decoded.operation == DecodedOperation.Unstake &&
            decoded.counterparty is DecodedCounterparty.StakeAccount

    override suspend fun amount(decoded: DecodedTransaction, coin: Coin): HeroCoinAmount? {
        val address =
            (decoded.counterparty as? DecodedCounterparty.StakeAccount)?.value ?: return null

        // Matched on the exact account the signed instruction names, not on the owner's first or
        // largest position: a wallet can hold several stake accounts, and describing the wrong one
        // is worse than describing none.
        val stake =
            stakingService
                .fetchStakeAccounts(coin.address)
                .firstOrNull { it.stakePubkey == address }
                ?.delegatedStake ?: return null

        return presentation.heroAmount(coin, stake)
    }
}
