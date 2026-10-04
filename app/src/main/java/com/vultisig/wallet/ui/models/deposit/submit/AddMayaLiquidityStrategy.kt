@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.OPERATION_MINT
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CheckMayaLpPairingUseCase
import com.vultisig.wallet.data.usecases.MayaChainLpPreflightUseCase
import com.vultisig.wallet.data.usecases.MayaLpPairing
import com.vultisig.wallet.ui.models.defi.MAYA_NATIVE_LP_POOLS
import com.vultisig.wallet.ui.models.deposit.toError
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Builds the asset side of a symmetric MayaChain LP add: the chain's native coin sent to the live
 * Maya inbound vault with `+:POOL:<CACAO address>`, which MayaChain matches against the vault's
 * CACAO side to credit one position.
 *
 * The pool is fixed by the chain — see [MAYA_NATIVE_LP_POOLS]. The inbound address is fetched on
 * every build (vaults churn), and a halted chain, an LP pause, or a deposit below the inbound's
 * dust threshold is refused before signing, since MayaChain would refund or ignore it.
 */
internal class AddMayaLiquidityStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val tokenAmountFieldState: TextFieldState,
    private val accountsRepository: AccountsRepository,
    private val vaultRepository: VaultRepository,
    private val mayaChainApi: MayaChainApi,
    private val mayaChainLpPreflight: MayaChainLpPreflightUseCase,
    private val checkMayaLpPairing: CheckMayaLpPairingUseCase,
    private val feeServiceComposite: FeeServiceComposite,
    private val tokenRepository: TokenRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val gasFeeToEstimate: suspend (GasFeeParams) -> EstimatedGasFee,
    private val getBitcoinTransactionPlan: BitcoinTransactionPlanBuilder,
) : DepositSubmitStrategy {

    override suspend fun build(): DepositTransaction {
        val vaultId =
            requireNotNull(vaultIdProvider()) {
                "vaultId must be initialized before creating transaction"
            }
        val chain =
            chainProvider()
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )
        val pool =
            MAYA_NATIVE_LP_POOLS[chain]
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )

        val vault =
            withContext(Dispatchers.IO) { vaultRepository.get(vaultId) } ?: error("Vault not found")
        // The memo credits the position to the vault's CACAO account, so the vault must hold one.
        val cacaoAddress =
            vault.coins
                .firstOrNull { it.chain == Chain.MayaChain && it.isNativeToken }
                ?.address
                ?.takeIf { it.isNotBlank() }
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.deposit_error_mayachain_not_enabled_for_lp)
                )

        val token =
            accountsRepository
                .loadAddress(vaultId, chain)
                .first()
                .accounts
                .firstOrNull { it.token.isNativeToken }
                ?.token
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )

        val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()
        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }
        val tokenAmountInt = tokenAmount.movePointRight(token.decimal).toBigInteger()

        mayaChainLpPreflight(pool, isPairedAdd = true)?.let { block -> throw block.toError() }
        requirePairable(pool, cacaoAddress, token.address)

        val inbound = requireInbound(pool, chain)
        requireAboveInboundDust(tokenAmountInt, token.decimal, token.ticker, inbound, chain)

        val memo = DepositMemo.AddLiquidity(pool, cacaoAddress).toString()

        val transfer =
            planInboundMemoTransfer(
                vaultId = vaultId,
                vault = vault,
                token = token,
                amount = tokenAmountInt,
                dstAddress = inbound.address,
                memo = memo,
                feeServiceComposite = feeServiceComposite,
                tokenRepository = tokenRepository,
                blockChainSpecificRepository = blockChainSpecificRepository,
                gasFeeToEstimate = gasFeeToEstimate,
                getBitcoinTransactionPlan = getBitcoinTransactionPlan,
            )

        return DepositTransaction(
            id = Uuid.random().toString(),
            vaultId = vaultId,
            srcToken = token,
            srcAddress = token.address,
            dstAddress = inbound.address,
            memo = memo,
            srcTokenValue = TokenValue(value = tokenAmountInt, token = token),
            estimatedFees = transfer.gasFee,
            estimateFeesFiat = transfer.estimatedGasFee.formattedFiatValue,
            blockChainSpecific = transfer.specific.blockChainSpecific,
            utxos = transfer.specific.utxos,
            operation = OPERATION_MINT,
            pool = pool,
            pairedAddress = cacaoAddress,
        )
    }

    /**
     * Refuses an asset deposit MayaChain would refund for its addresses: into a live CACAO-only
     * position, which never takes an asset address, or into a record keyed to other addresses. An
     * unreadable record refuses too — this is the half that leaves the vault's own chain.
     */
    private suspend fun requirePairable(pool: String, cacaoAddress: String, assetAddress: String) {
        val pairing =
            try {
                checkMayaLpPairing(pool, cacaoAddress, assetAddress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to read the MayaChain LP record for %s", pool)
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.deposit_error_maya_lp_unverified)
                )
            }
        val reason =
            when (pairing) {
                MayaLpPairing.Pairable -> return
                MayaLpPairing.SingleSidedPosition ->
                    R.string.deposit_error_maya_lp_single_sided_position
                MayaLpPairing.AddressMismatch -> R.string.deposit_error_maya_lp_address_mismatch
            }
        throw InvalidTransactionDataException(UiText.FormattedText(reason, listOf(pool)))
    }

    /**
     * The live Maya inbound for [pool]'s chain. Fails closed: an unreachable mayanode, a chain
     * missing from the inbound set, or one reporting a halt or paused LP actions all refuse the
     * deposit rather than sending funds to an address MayaChain may not credit.
     */
    private suspend fun requireInbound(pool: String, chain: Chain): THORChainInboundAddress {
        val chainPrefix = pool.substringBefore('.')
        val inbound =
            try {
                withContext(Dispatchers.IO) { mayaChainApi.getInboundAddresses() }
                    .firstOrNull { it.chain.equals(chainPrefix, ignoreCase = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch MayaChain inbound for %s", chainPrefix)
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.deposit_error_maya_inbound_unavailable)
                )
            }
                ?: throw InvalidTransactionDataException(
                    UiText.FormattedText(R.string.deposit_error_pool_not_available, listOf(pool))
                )

        if (
            inbound.halted ||
                inbound.chainLPActionsPaused ||
                inbound.chainTradingPaused ||
                inbound.globalTradingPaused
        ) {
            throw InvalidTransactionDataException(
                UiText.FormattedText(R.string.deposit_error_maya_chain_halted, listOf(chain.raw))
            )
        }
        if (inbound.address.isBlank()) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }
        return inbound
    }

    /**
     * Bifrost ignores an inbound below the chain's `dust_threshold`, published in MayaChain's 1e8
     * fixed point whatever the coin's own decimals — such a deposit confirms on the source chain and
     * is never credited.
     */
    private fun requireAboveInboundDust(
        amount: BigInteger,
        decimals: Int,
        ticker: String,
        inbound: THORChainInboundAddress,
        chain: Chain,
    ) {
        val dust = inbound.dustThreshold?.toBigIntegerOrNull() ?: return
        if (dust <= BigInteger.ZERO) return
        val amountFixed8 = BigDecimal(amount).movePointLeft(decimals - MAYA_FIXED_POINT_DECIMALS)
        if (amountFixed8 < BigDecimal(dust)) {
            throw InvalidTransactionDataException(
                UiText.FormattedText(
                    R.string.send_form_minimum_send_amount_is_requires_this,
                    listOf(
                        BigDecimal(dust)
                            .movePointLeft(MAYA_FIXED_POINT_DECIMALS)
                            .stripTrailingZeros()
                            .toPlainString(),
                        ticker,
                        chain.raw,
                    ),
                )
            )
        }
    }

    private companion object {
        const val MAYA_FIXED_POINT_DECIMALS = 8
    }
}
