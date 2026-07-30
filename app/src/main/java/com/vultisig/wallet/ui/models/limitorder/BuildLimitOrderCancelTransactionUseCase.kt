package com.vultisig.wallet.ui.models.limitorder

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.swap.limit.LimitOrderCancelDustError
import com.vultisig.wallet.data.swap.limit.LimitOrderCancelEligibility
import com.vultisig.wallet.data.swap.limit.limitOrderCancelDustAmount
import com.vultisig.wallet.data.swap.limit.limitOrderCancelDustCeiling
import com.vultisig.wallet.data.swap.limit.limitOrderCancelEligibility
import com.vultisig.wallet.data.swap.limit.limitOrderCancelLocalDustFloor
import com.vultisig.wallet.data.swap.limit.thorchainMemoAssetChainPrefix
import com.vultisig.wallet.ui.models.deposit.DepositGasFeeHelper
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

/** Why a cancel could not be prepared. Each case maps to its own user-facing message. */
internal enum class LimitOrderCancelFailure {
    /** [limitOrderCancelEligibility] refused the order. */
    NotCancellable,

    /** The vault holds no coin on the chain the cancel has to be sent from. */
    MissingSigningCoin,

    /** THORChain published no live, unhalted inbound vault for the source chain. */
    NoInboundAddress,

    /**
     * The dust an L1 cancel must attach could not be resolved safely. Fatal rather than defaulted:
     * an under-funded cancel is ignored by Bifrost, which looks exactly like success.
     */
    DustUnavailable,

    /** The vault cannot pay for the cancel — the dust plus the chain fee exceed its balance. */
    InsufficientBalance,
}

internal class LimitOrderCancelException(
    val failure: LimitOrderCancelFailure,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Assembles the transaction that cancels a resting THORChain limit order.
 *
 * The cancel reaches THORChain by one of two routes, decided by the chain that FUNDED the order:
 * - **THORChain-sourced** → a `MsgDeposit` carrying the `m=<` memo and no coins at all. Its whole
 *   cost is the deposit gas.
 * - **Any other routable chain** → a dust transfer to the Asgard inbound vault carrying the same
 *   memo, observed by Bifrost and dispatched to the same modify handler. The dust is irreversibly
 *   donated to the pool — there is no refund path for anything attached to an `m=<` — so it is the
 *   transaction's amount and the Verify screen shows it before the user signs.
 *
 * The result is an ordinary [DepositTransaction], so it flows through the existing verify → keysign
 * pipeline unchanged; the memo is the only thing about it that is limit-order specific.
 *
 * The `EnableAdvSwapQueue` mimir gate deliberately does NOT apply here. Placement is gated and must
 * be — a `=<` signed while the queue is disabled can execute as an unprotected market swap. A
 * cancel has the opposite risk profile: if the mimir flips off while an order rests, the order
 * still exists and the user still needs a way out.
 */
internal class BuildLimitOrderCancelTransactionUseCase
@Inject
constructor(
    private val thorChainApi: ThorChainApi,
    private val accountsRepository: AccountsRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val depositGasFeeHelper: DepositGasFeeHelper,
) {

    suspend fun build(vaultId: String, order: PendingLimitOrderEntity): DepositTransaction {
        // Re-checked here rather than trusted from the tapped card: the list snapshot can be
        // minutes old, and in that window the order can fill, expire, or have a cancel recorded
        // against it. Signing a cancel for an order that already closed spends a fee (and on L1
        // donates dust) for a memo that can no longer match anything.
        val eligibility = limitOrderCancelEligibility(order)
        if (eligibility !is LimitOrderCancelEligibility.Cancellable) {
            val blocker = (eligibility as LimitOrderCancelEligibility.Blocked).blocker
            throw LimitOrderCancelException(
                LimitOrderCancelFailure.NotCancellable,
                "limit order ${order.inboundTxHash} cannot be cancelled: $blocker",
            )
        }
        val memo = eligibility.memo
        val sourceChain =
            Chain.entries.firstOrNull { it.raw == order.sourceChain }
                ?: throw LimitOrderCancelException(
                    LimitOrderCancelFailure.NotCancellable,
                    "limit order ${order.inboundTxHash} has no recorded source chain",
                )

        // The coin that SIGNS the cancel, never the order's own asset — a cancel moves no tokens,
        // so
        // what it needs is the gas asset of the chain it is sent from.
        val signingCoin =
            accountsRepository
                .loadAddress(vaultId, sourceChain)
                .first()
                .accounts
                .firstOrNull { it.token.isNativeToken }
                ?.token
                ?: throw LimitOrderCancelException(
                    LimitOrderCancelFailure.MissingSigningCoin,
                    "vault holds no native coin on $sourceChain to sign the cancel with",
                )

        val gasFee =
            depositGasFeeHelper.calculateGasFee(
                vaultId = vaultId,
                chain = sourceChain,
                token = signingCoin,
                srcAddress = signingCoin.address,
            )

        val isThorchainSourced = sourceChain == Chain.ThorChain
        val destination = if (isThorchainSourced) null else resolveInboundVault(sourceChain)
        val amount =
            if (destination == null) BigInteger.ZERO
            else resolveDust(sourceChain, signingCoin.decimal, destination.dustThreshold)

        val specific =
            blockChainSpecificRepository.getSpecific(
                chain = sourceChain,
                address = signingCoin.address,
                token = signingCoin,
                gasFee = gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = isThorchainSourced,
                dstAddress = destination?.address,
                tokenAmountValue = amount,
                memo = memo,
            )

        // Priced AFTER the specific, because on a UTXO chain `gasFee` is a per-byte RATE, not a
        // total — comparing a balance against it would wave through a cancel the vault cannot pay
        // for. The plan gives the real fee for the exact inputs and OP_RETURN this cancel will
        // carry.
        assertAffordable(
            vaultId = vaultId,
            chain = sourceChain,
            signingCoin = signingCoin,
            amount = amount,
            gasFee = gasFee,
            destination = destination,
            specific = specific,
            memo = memo,
        )

        val estimatedFee =
            depositGasFeeHelper.getFeesFiatValue(sourceChain, specific, gasFee, signingCoin)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = signingCoin,
            srcAddress = signingCoin.address,
            dstAddress = destination?.address.orEmpty(),
            memo = memo,
            srcTokenValue = TokenValue(value = amount, token = signingCoin),
            estimatedFees = gasFee,
            estimateFeesFiat = estimatedFee.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            utxos = specific.utxos,
        )
    }

    /**
     * The live, non-halted THORChain inbound vault for [chain].
     *
     * Never cached: the address selected here is signed against, and a stale inbound can lag a
     * vault rotation — signing to a rotated-out vault sends funds nowhere recoverable. Fails closed
     * rather than falling back to anything.
     */
    private suspend fun resolveInboundVault(chain: Chain): InboundVault {
        val prefix =
            thorchainMemoAssetChainPrefix[chain]
                ?: throw LimitOrderCancelException(
                    LimitOrderCancelFailure.NoInboundAddress,
                    "$chain is not routable through THORChain",
                )
        val inbound =
            thorChainApi.getTHORChainInboundAddresses().firstOrNull {
                it.chain.trim().uppercase() == prefix &&
                    !it.halted &&
                    !it.globalTradingPaused &&
                    !it.chainTradingPaused
            }
                ?: throw LimitOrderCancelException(
                    LimitOrderCancelFailure.NoInboundAddress,
                    "no live THORChain inbound for $chain",
                )
        return InboundVault(inbound.address, inbound.dustThreshold)
    }

    private fun resolveDust(
        chain: Chain,
        decimals: Int,
        inboundDustThreshold: String?,
    ): BigInteger =
        try {
            limitOrderCancelDustAmount(
                localDustFloor = limitOrderCancelLocalDustFloor(chain),
                inboundDustThreshold = inboundDustThreshold,
                decimals = decimals,
                ceiling =
                    limitOrderCancelDustCeiling(chain).movePointRight(decimals).toBigIntegerExact(),
                chainSymbol = thorchainMemoAssetChainPrefix[chain] ?: chain.raw,
            )
        } catch (e: LimitOrderCancelDustError) {
            throw LimitOrderCancelException(
                LimitOrderCancelFailure.DustUnavailable,
                "could not resolve the dust an L1 cancel must attach",
                e,
            )
        }

    /**
     * Refuse before signing when the vault cannot pay. The dust is not the whole cost — the chain
     * fee rides on top — and the deposit verify screen performs no balance check of its own, so a
     * cancel built here would otherwise fail at broadcast after the whole signing ceremony.
     *
     * A balance that cannot be read is NOT treated as insufficient: that would block a cancel the
     * user can afford on the strength of a failed lookup.
     */
    private suspend fun assertAffordable(
        vaultId: String,
        chain: Chain,
        signingCoin: Coin,
        amount: BigInteger,
        gasFee: TokenValue,
        destination: InboundVault?,
        specific: BlockChainSpecificAndUtxo,
        memo: String,
    ) {
        val balance =
            accountsRepository
                .loadAddress(vaultId, chain)
                .first()
                .accounts
                .firstOrNull { it.token.isNativeToken }
                ?.tokenValue
                ?.value ?: return
        val totalFee =
            totalFee(vaultId, chain, signingCoin, amount, gasFee, destination, specific, memo)
        if (balance < amount + totalFee) {
            throw LimitOrderCancelException(
                LimitOrderCancelFailure.InsufficientBalance,
                "vault cannot cover the cancel on $chain",
            )
        }
    }

    /**
     * The whole fee this cancel will pay, in the signing coin's smallest units.
     *
     * On a UTXO chain [gasFee] is a sat/vByte rate, so the total has to come from a transaction
     * plan built over the same inputs, amount and OP_RETURN the cancel will carry. Everywhere else
     * the fee service already reports a total. A plan that cannot be built falls back to the rate,
     * which under-states the fee — deliberately, because refusing a cancel on a failed local
     * estimate is worse than letting the node have the last word.
     */
    private suspend fun totalFee(
        vaultId: String,
        chain: Chain,
        signingCoin: Coin,
        amount: BigInteger,
        gasFee: TokenValue,
        destination: InboundVault?,
        specific: BlockChainSpecificAndUtxo,
        memo: String,
    ): BigInteger {
        if (chain.standard != TokenStandard.UTXO || destination == null) return gasFee.value
        return try {
            depositGasFeeHelper
                .getBitcoinTransactionPlan(
                    vaultId = vaultId,
                    selectedToken = signingCoin,
                    dstAddress = destination.address,
                    tokenAmountInt = amount,
                    specific = specific,
                    memo = memo,
                )
                .fee
                .toBigInteger()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not plan the cancel transaction on %s; pricing off the rate", chain)
            gasFee.value
        }
    }

    private data class InboundVault(val address: String, val dustThreshold: String?)
}
