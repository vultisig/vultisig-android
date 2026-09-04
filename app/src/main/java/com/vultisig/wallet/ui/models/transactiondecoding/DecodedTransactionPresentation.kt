package com.vultisig.wallet.ui.models.transactiondecoding

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.isLayer2
import com.vultisig.wallet.data.models.monoToneLogo
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.components.hero.HeroCoinAmount
import com.vultisig.wallet.ui.components.hero.HeroContent
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The vault's own coin for [coin], which carries the trusted decimals and logo. Falls back to
 * [coin] itself when the vault holds no match — on a joining co-signer that coin is peer-supplied,
 * so a local match is preferred wherever one exists.
 */
internal fun List<Coin>.trustedMatchFor(coin: Coin): Coin =
    firstOrNull {
        it.chain == coin.chain &&
            it.ticker.equals(coin.ticker, ignoreCase = true) &&
            it.contractAddress.equals(coin.contractAddress, ignoreCase = true)
    } ?: coin

/**
 * Converts provenance-aware readings into display content. This is the single boundary where
 * unsigned ticker, logo, and decimal metadata may enter.
 *
 * Mirrors the iOS `DecodedTransactionPresentation`. Amount scaling and asset resolution are shared
 * between Verify and Done; only the surface-owned title differs, which is why [hero] takes the
 * title rather than deriving it.
 */
@Singleton
internal class DecodedTransactionPresentation
@Inject
constructor(
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val fiatValueToString: FiatValueToStringMapper,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val projectionCoordinator: ProjectionCoordinator,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
) {

    /**
     * Builds a hero, using [coin] only for presentation metadata. Every readable amount either
     * renders as a figure or degrades to the bare verb, so this always produces content.
     */
    suspend fun hero(decoded: DecodedTransaction, coin: Coin, title: String): HeroContent {
        // Execution-set quantities state their signed scope immediately. A later chain read may
        // add an estimate, but never owns the verb/scope.
        projectionCoordinator.hero(decoded, title)?.let {
            return it
        }

        val amount = decoded.amount
        val titleOnly = HeroContent.Title(title)

        return when (amount) {
            is DecodedAmount.Units ->
                when (val asset = amount.asset) {
                    DecodedAsset.TransactionCoin -> sendHero(title, coin, amount.value) ?: titleOnly

                    // The chain-native unit is committed by the operation, so resolve its display
                    // metadata from the chain rather than from payload sidecars.
                    DecodedAsset.ChainNative ->
                        nativeAsset(coin)?.let { sendHero(title, it, amount.value) } ?: titleOnly

                    is DecodedAsset.Denom ->
                        denomAsset(coin, asset.value)?.let { sendHero(title, it, amount.value) }
                            ?: titleOnly
                }

            // The rent reserve has to be read live before this can be shown as stake, so the
            // funding figure is deliberately not rendered: it is larger than what gets staked.
            is DecodedAmount.AccountFunding -> titleOnly

            // Projection and localized scope arrive with fractional readers.
            is DecodedAmount.Fraction -> titleOnly
            DecodedAmount.Unstated -> titleOnly
        }
    }

    /**
     * A verb over a zero is the bug in better clothes: an unrepresentable, non-positive, or
     * rounds-to-nothing quantity degrades to the bare verb rather than presenting a carrier amount
     * as the operation amount.
     */
    private suspend fun sendHero(title: String, coin: Coin, raw: BigInteger): HeroContent? =
        heroAmount(coin, raw)?.let { HeroContent.Send(title = title, coin = it) }

    /**
     * A display amount for [raw] base units of [coin], or null when there is nothing truthful to
     * show. Shared with the position readers, so a projected estimate is formatted and priced by
     * exactly the same rules as a signed amount.
     */
    suspend fun heroAmount(coin: Coin, raw: BigInteger): HeroCoinAmount? {
        // Values wider than the precision the display formatter carries are refused rather than
        // rounded: an amount that cannot be represented exactly is not shown at all.
        if (raw.abs().toString().length > MAX_SIGNIFICANT_DIGITS) return null

        val tokenValue = TokenValue(value = raw, unit = coin.ticker, decimals = coin.decimal)
        if (tokenValue.decimal <= BigDecimal.ZERO) return null

        // The app's own amount formatter, so a decoder-driven card reads exactly like the one it
        // replaces — same grouping, same precision, same large-value suffixes.
        val amount = mapTokenValueToDecimalUiString(tokenValue)
        // A dust quantity that rounds away at display precision states nothing truthful either.
        // Only a bare "0" parses; a grouped or suffixed amount throws and is kept.
        if (runCatching { BigDecimal(amount) }.getOrNull()?.signum() == 0) return null

        return heroAmount(coin, tokenValue, amount)
    }

    /**
     * Prices the amount off the trusted local rate for [coin]; an unpriceable amount shows none.
     */
    private suspend fun heroAmount(
        coin: Coin,
        tokenValue: TokenValue,
        amount: String,
    ): HeroCoinAmount {
        val fiat =
            runCatching {
                    val currency = appCurrencyRepository.currency.first()
                    val value = convertTokenValueToFiat(coin, tokenValue, currency)
                    if (value.value > BigDecimal.ZERO) fiatValueToString(value) else null
                }
                .getOrNull()

        return HeroCoinAmount(
            amount = amount,
            ticker = coin.ticker,
            logo = coin.logo,
            fiatValue = fiat,
            // The badge follows the resolved asset, under the same rule the card applies to every
            // other token: a USDC amount keeps its Base or Arbitrum context, a native asset needs
            // no badge, and an L2 native keeps one because its ticker alone is ambiguous.
            chainLogo =
                coin.chain.monoToneLogo.takeIf { !coin.isNativeToken || coin.chain.isLayer2 },
        )
    }

    /**
     * The chain's own native asset, resolved from the curated catalogue rather than from the
     * payload's coin — the signed unit is committed by the operation, not by peer-supplied
     * metadata.
     */
    private fun nativeAsset(coin: Coin): Coin? =
        Coins.coins[coin.chain]?.firstOrNull { it.isNativeToken }

    /**
     * ⚠️ **The chain's own denom, resolved from the chain rather than from the payload.** A Cosmos
     * SignDoc names `uatom`, and the curated table keys ATOM at an empty contract address — so a
     * contract lookup finds nothing and a delegate that states its amount perfectly well would
     * render as a bare verb. Matching the chain's fee unit is what closes that, and it is exact:
     * denoms are case-sensitive.
     *
     * Every other denom is matched case-sensitively for the same reason, which is why
     * [Coins.findCuratedByContract] — deliberately case-insensitive for pool strings — is not used
     * here.
     */
    private fun denomAsset(coin: Coin, denom: String): Coin? {
        if (denom == coin.chain.feeUnit) return nativeAsset(coin)
        return Coins.coins[coin.chain]?.firstOrNull { it.contractAddress == denom }
    }

    companion object {
        /**
         * Matches the precision iOS refuses beyond — `Decimal` carries 38 significant digits, and
         * an amount wider than that is a wire artefact rather than a quantity anyone holds.
         */
        private const val MAX_SIGNIFICANT_DIGITS = 38

        /**
         * Done uses completed-action copy while Verify keeps the present-progressive wording. Every
         * operation has an explicit decision; swaps remain owned by their dedicated two-sided Done
         * screen and return null so the caller keeps that layout.
         */
        fun doneTitleRes(operation: DecodedOperation): Int? =
            when (operation) {
                DecodedOperation.Transfer,
                DecodedOperation.ContractCall,
                DecodedOperation.Unknown -> R.string.done_verb_sent
                DecodedOperation.Swap -> null
                DecodedOperation.Approve -> R.string.done_verb_approved
                DecodedOperation.Stake -> R.string.done_verb_staked
                DecodedOperation.Unstake -> R.string.done_verb_unstaked
                DecodedOperation.Bond -> R.string.done_verb_bonded
                DecodedOperation.Unbond -> R.string.done_verb_unbonded
                DecodedOperation.Rebond -> R.string.done_verb_rebonded
                DecodedOperation.Leave -> R.string.done_verb_left
                DecodedOperation.Delegate -> R.string.done_verb_delegated
                DecodedOperation.Undelegate -> R.string.done_verb_undelegated
                DecodedOperation.Redelegate -> R.string.done_verb_redelegated
                DecodedOperation.ClaimRewards -> R.string.done_verb_claimed_rewards
                DecodedOperation.Mint -> R.string.done_verb_minted
                DecodedOperation.Redeem -> R.string.done_verb_redeemed
                DecodedOperation.SecuredAssetWithdraw,
                DecodedOperation.WithdrawStake -> R.string.done_verb_withdrew
                DecodedOperation.AddLiquidity -> R.string.done_verb_added_liquidity
                DecodedOperation.RemoveLiquidity -> R.string.done_verb_removed_liquidity
                DecodedOperation.Merge -> R.string.done_verb_merged
                DecodedOperation.Unmerge -> R.string.done_verb_unmerged
                DecodedOperation.IbcTransfer -> R.string.done_verb_bridged
                DecodedOperation.Vote -> R.string.done_verb_voted
                DecodedOperation.SecuredAssetDeposit -> R.string.done_verb_deposited
                DecodedOperation.SwitchChain -> R.string.done_verb_switched
                DecodedOperation.LimitOrderPlacement -> R.string.done_verb_placed_limit_order
                DecodedOperation.LimitOrderCancel -> R.string.limit_swap_cancel_done_sent
            }

        /**
         * Verify uses present-progressive copy: the transaction has not been signed yet, so the
         * verb names what the user is about to do. Every operation carries an explicit decision,
         * and the null branch is the deliberately-silent set — readings whose surface already
         * describes the transaction better than a verb could.
         *
         * A transfer is what the send screens are; a swap is rendered two-sided by its own verify
         * screen; an approval names its spender and allowance; a vote moves nothing, so the verb
         * would be the whole hero; a contract call of unknown shape and an unreadable transaction
         * have nothing to add. `RemoveLiquidity` is silent for a different reason: naming it would
         * displace the carrier amount the transaction actually charges.
         */
        fun verifyTitleRes(operation: DecodedOperation): Int? =
            when (operation) {
                DecodedOperation.Transfer,
                DecodedOperation.Swap,
                DecodedOperation.Approve,
                DecodedOperation.Vote,
                DecodedOperation.ContractCall,
                DecodedOperation.RemoveLiquidity,
                DecodedOperation.Unknown -> null

                // The Cosmos staking verify screen already ships this exact wording in all ten
                // locales; reusing it keeps a decoded delegate reading identical to the screen a
                // user reaches the same operation through.
                DecodedOperation.Stake -> R.string.cosmos_staking_youre_staking
                DecodedOperation.Unstake -> R.string.cosmos_staking_youre_unstaking
                DecodedOperation.ClaimRewards -> R.string.cosmos_staking_youre_claiming

                DecodedOperation.Bond -> R.string.verify_verb_bonding
                DecodedOperation.Unbond -> R.string.verify_verb_unbonding
                DecodedOperation.Rebond -> R.string.verify_verb_rebonding
                DecodedOperation.Leave -> R.string.verify_verb_leaving
                DecodedOperation.SecuredAssetDeposit -> R.string.verify_verb_depositing
                DecodedOperation.SecuredAssetWithdraw,
                DecodedOperation.WithdrawStake -> R.string.verify_verb_withdrawing
                DecodedOperation.SwitchChain -> R.string.verify_verb_switching
                DecodedOperation.Delegate -> R.string.verify_verb_delegating
                DecodedOperation.Undelegate -> R.string.verify_verb_undelegating
                DecodedOperation.Redelegate -> R.string.verify_verb_redelegating
                DecodedOperation.AddLiquidity -> R.string.verify_verb_adding_liquidity
                DecodedOperation.Redeem -> R.string.verify_verb_redeeming
                DecodedOperation.Mint -> R.string.verify_verb_minting
                DecodedOperation.Merge -> R.string.verify_verb_merging
                DecodedOperation.Unmerge -> R.string.verify_verb_unmerging
                DecodedOperation.IbcTransfer -> R.string.verify_verb_bridging

                // The limit-order screens own this wording already, on both routes.
                DecodedOperation.LimitOrderPlacement -> R.string.verify_limit_order_title
                DecodedOperation.LimitOrderCancel -> R.string.verify_limit_order_cancel_title
            }

        /**
         * Basis points as a percentage, without trailing noise: 10000 reads "100%", 5006 reads
         * "50.06%".
         *
         * ⚠️ Formatted through [NumberFormat.getPercentInstance] rather than by appending an ASCII
         * `%`. Where the symbol sits, whether a space precedes it, and which symbol is used at all
         * are locale rules.
         */
        fun percentage(basisPoints: Int): String {
            val fraction =
                BigDecimal(basisPoints).divide(BigDecimal(10_000)).setScale(4, RoundingMode.DOWN)
            return NumberFormat.getPercentInstance(Locale.getDefault())
                .apply {
                    minimumFractionDigits = 0
                    maximumFractionDigits = 2
                }
                .format(fraction)
        }
    }
}
