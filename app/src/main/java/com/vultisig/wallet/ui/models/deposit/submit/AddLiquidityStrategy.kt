@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.OPERATION_MINT
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CheckMayaLpPairingUseCase
import com.vultisig.wallet.data.usecases.CheckThorChainLpPairingUseCase
import com.vultisig.wallet.data.usecases.MayaChainLpPreflightUseCase
import com.vultisig.wallet.data.usecases.MayaLpPairing
import com.vultisig.wallet.data.usecases.ThorChainLpPairing
import com.vultisig.wallet.data.usecases.ThorChainLpPreflightUseCase
import com.vultisig.wallet.ui.models.defi.mayaLpPairedChain
import com.vultisig.wallet.ui.models.defi.parseThorChainPool
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
 * Builds an Add-Liquidity [DepositTransaction]: the RUNE/CACAO side of a pool as a `MsgDeposit`, or
 * the asset side that completes a pending THORChain half-deposit as a memo transfer to the asset
 * chain's live inbound vault.
 */
internal class AddLiquidityStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val lpPoolIdProvider: () -> String?,
    private val tokenAmountFieldState: TextFieldState,
    private val accountsRepository: AccountsRepository,
    private val thorChainLpPreflight: ThorChainLpPreflightUseCase,
    private val mayaChainLpPreflight: MayaChainLpPreflightUseCase,
    private val checkMayaLpPairing: CheckMayaLpPairingUseCase,
    private val checkThorChainLpPairing: CheckThorChainLpPairingUseCase,
    private val resolvePairedAddress: suspend (Chain, String, String) -> String?,
    private val addressProvider: () -> Address?,
    private val requireTokenAmount: (Coin, Account, Address, TokenValue) -> BigInteger,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val calculateGasFee: suspend (Chain, Coin, String) -> TokenValue,
    private val getFeesFiatValue:
        suspend (BlockChainSpecificAndUtxo, TokenValue, Coin) -> EstimatedGasFee,
    private val thorChainApi: ThorChainApi,
    private val vaultRepository: VaultRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val tokenRepository: TokenRepository,
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

        val poolId =
            lpPoolIdProvider()
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )

        val address = accountsRepository.loadAddress(vaultId, chain).first()
        val selectedToken =
            address.accounts.firstOrNull { it.token.isNativeToken }?.token
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )

        val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }
        val tokenAmountInt = tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

        val parsedPool = parseThorChainPool(poolId)
        val assetChain = parsedPool.chain
        val isSymmetricPool = assetChain != null && assetChain != Chain.ThorChain
        val isThorChainAssetSide = isSymmetricPool && chain == assetChain
        // A token pool's asset half is an ERC-20/TRC-20 deposit through the router after an
        // approval, which the Deposit flow cannot build; the chain's native coin under that pool's
        // memo would be credited to nothing.
        if (isThorChainAssetSide && parsedPool.contractAddress.isNotEmpty()) {
            throw InvalidTransactionDataException(
                UiText.FormattedText(
                    R.string.deposit_error_lp_token_pool_asset_side,
                    listOf(poolId),
                )
            )
        }

        val pairedAddress = resolvePairedAddress(chain, vaultId, poolId)
        val isMayaChainLpAdd = chain == Chain.MayaChain
        // mayanode only records an asset address on a zero-unit record, so a live position refunds
        // any add naming a different one — but checks it only when the add names one. There the
        // CACAO side stays the single-sided add it always was; a pending half has fixed both
        // addresses and refunds either way. See [CheckMayaLpPairingUseCase]. The record is read
        // even without an asset address: a live position takes a `+:POOL` top-up as it is.
        val mayaPairing =
            if (
                isMayaChainLpAdd &&
                    (pairedAddress != null || mayaLpPairedChain(chain, poolId) != null)
            ) {
                checkMayaPairing(poolId, selectedToken.address, pairedAddress)
            } else {
                null
            }
        // thornode refunds a RUNE add naming an asset address other than the one a live or
        // pending record holds, and an empty recorded address never matches a named one. It
        // compares only when the add names one, so a live position takes `+:POOL` as it is. See
        // [CheckThorChainLpPairingUseCase].
        val thorPairing =
            if (chain == Chain.ThorChain && isSymmetricPool) {
                checkThorChainPairing(poolId, selectedToken.address, pairedAddress)
            } else {
                null
            }
        val acceptsSingleSided =
            mayaPairing == MayaLpPairing.SingleSidedPosition ||
                mayaPairing == MayaLpPairing.PairedWithOtherAddress ||
                thorPairing == ThorChainLpPairing.SingleSidedPosition ||
                thorPairing == ThorChainLpPairing.PairedWithOtherAddress
        if (mayaPairing == MayaLpPairing.AddressMismatch && pairedAddress != null) {
            throw InvalidTransactionDataException(
                UiText.FormattedText(
                    R.string.deposit_error_maya_lp_address_mismatch,
                    listOf(poolId),
                )
            )
        }
        if (thorPairing == ThorChainLpPairing.AddressMismatch) {
            throw InvalidTransactionDataException(
                UiText.FormattedText(
                    R.string.deposit_error_thorchain_lp_address_mismatch,
                    listOf(poolId),
                )
            )
        }
        val memoPairedAddress = if (acceptsSingleSided) null else pairedAddress

        // Preflight against THORChain network state — pool status and the relevant mimir pause
        // keys. Refuses to build the keysign payload when the network would refund the inbound,
        // sparing the user the inbound gas spend. It asks about the pool, not about the inbound's
        // source chain, so it must also cover the asset-side add that completes a pending
        // half-deposit — that inbound is refundable on exactly the same terms, and it arrives on
        // the pool's own asset chain. It is told whether the memo names the other side because a
        // Staged pool accepts exactly the paired adds and refunds the rest. Maya reuses this
        // strategy with chain == MayaChain and its own pool ids, which thornode knows nothing
        // about: a THORChain-wide PAUSELP, or a same-named THOR pool sitting Staged, would reject a
        // perfectly valid CACAO add — so a CACAO add asks mayanode instead.
        val isThorChainLpAdd = chain == Chain.ThorChain || isThorChainAssetSide
        if (isThorChainLpAdd) {
            thorChainLpPreflight(poolId, isPairedAdd = memoPairedAddress != null)?.let { block ->
                throw block.toError()
            }
        }
        if (isMayaChainLpAdd) {
            mayaChainLpPreflight(poolId, isPairedAdd = memoPairedAddress != null)?.let { block ->
                throw block.toError()
            }
        }

        val srcAddress = selectedToken.address
        // Either half of a symmetric THORChain add MUST name the other half's address, or
        // THORChain opens a separate asymmetric position rather than crediting the pair: a
        // RUNE-side add carries the asset address, and the asset-side add that completes a pending
        // half-deposit carries the RUNE address. Gated on the preflight's THORChain-only condition
        // as well as the pool shape: isSymmetricPool alone would reject every Maya pool add, while
        // isThorChainLpAdd alone would reject a THOR.* pool that has no paired side at all. A CACAO
        // add pairs exactly where mayaLpPairedChain says the app can deposit the asset half; an
        // unresolved address there must not quietly turn a fresh add single-sided — a live position
        // already is one. The address comes from a coin the vault holds, so a missing one means
        // that chain is not enabled.
        val pairedChain =
            when {
                isMayaChainLpAdd -> mayaLpPairedChain(chain, poolId)
                isSymmetricPool && isThorChainLpAdd ->
                    if (chain == Chain.ThorChain) assetChain else Chain.ThorChain
                else -> null
            }
        if (pairedChain != null && pairedAddress == null && !acceptsSingleSided) {
            throw InvalidTransactionDataException(
                UiText.FormattedText(
                    R.string.deposit_error_lp_paired_chain_not_enabled,
                    listOf(pairedChain.raw, poolId),
                )
            )
        }
        val memo = DepositMemo.AddLiquidity(poolId, memoPairedAddress)

        if (isThorChainAssetSide) {
            return buildThorChainAssetSide(
                vaultId = vaultId,
                poolId = poolId,
                token = selectedToken,
                amount = tokenAmountInt,
                memo = memo.toString(),
                runeAddress = memoPairedAddress.orEmpty(),
            )
        }

        val gasFee = calculateGasFee(chain, selectedToken, srcAddress)
        requireCoveredByFormBalance(addressProvider(), selectedToken, gasFee, requireTokenAmount)

        val specific =
            blockChainSpecificRepository.getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = Uuid.random().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = "",
            memo = memo.toString(),
            srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            operation = OPERATION_MINT,
            pool = poolId,
            pairedAddress = memoPairedAddress.orEmpty(),
        )
    }

    /**
     * The asset half of a symmetric THORChain add: [amount] of the pool's native asset sent to the
     * chain's live inbound vault with `+:POOL:<RUNE address>`, which THORChain matches against the
     * pending RUNE half. Planned like every other inbound memo transfer, so a UTXO chain spends the
     * selected inputs and puts the memo in OP_RETURN.
     */
    private suspend fun buildThorChainAssetSide(
        vaultId: String,
        poolId: String,
        token: Coin,
        amount: BigInteger,
        memo: String,
        runeAddress: String,
    ): DepositTransaction {
        val inbound = requireThorChainInbound(poolId, token.chain)
        requireAboveInboundDust(amount, token.decimal, token.ticker, inbound, token.chain)

        val vault =
            withContext(Dispatchers.IO) { vaultRepository.get(vaultId) } ?: error("Vault not found")
        val transfer =
            planInboundMemoTransfer(
                vaultId = vaultId,
                vault = vault,
                token = token,
                amount = amount,
                dstAddress = inbound.address,
                memo = memo,
                feeServiceComposite = feeServiceComposite,
                tokenRepository = tokenRepository,
                blockChainSpecificRepository = blockChainSpecificRepository,
                gasFeeToEstimate = gasFeeToEstimate,
                getBitcoinTransactionPlan = getBitcoinTransactionPlan,
            )

        // A UTXO plan already refuses an amount its inputs cannot cover, with its own error.
        if (!token.chain.isPlannedUtxoChain) {
            requireCoveredByFormBalance(
                addressProvider(),
                token,
                transfer.gasFee,
                requireTokenAmount,
            )
        }

        return DepositTransaction(
            id = Uuid.random().toString(),
            vaultId = vaultId,
            srcToken = token,
            srcAddress = token.address,
            dstAddress = inbound.address,
            memo = memo,
            srcTokenValue = TokenValue(value = amount, token = token),
            estimatedFees = transfer.gasFee,
            estimateFeesFiat = transfer.estimatedGasFee.formattedFiatValue,
            blockChainSpecific = transfer.specific.blockChainSpecific,
            utxos = transfer.specific.utxos,
            operation = OPERATION_MINT,
            pool = poolId,
            pairedAddress = runeAddress,
        )
    }

    /**
     * THORChain's live inbound for [poolId]'s chain, read on every build because vaults churn.
     * Fails closed on an unreachable thornode, a chain missing from the inbound set, or one that is
     * halted, trading-paused or LP-paused — the same gate iOS applies to an LP add.
     */
    private suspend fun requireThorChainInbound(
        poolId: String,
        chain: Chain,
    ): THORChainInboundAddress {
        val chainPrefix = poolId.substringBefore('.')
        val inbound =
            try {
                withContext(Dispatchers.IO) { thorChainApi.getTHORChainInboundAddresses() }
                    .firstOrNull { it.chain.equals(chainPrefix, ignoreCase = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch THORChain inbound for %s", chainPrefix)
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.deposit_error_thorchain_inbound_unavailable)
                )
            }
                ?: throw InvalidTransactionDataException(
                    UiText.FormattedText(R.string.deposit_error_pool_not_available, listOf(poolId))
                )

        if (
            inbound.halted ||
                inbound.chainLPActionsPaused ||
                inbound.chainTradingPaused ||
                inbound.globalTradingPaused
        ) {
            throw InvalidTransactionDataException(
                UiText.FormattedText(
                    R.string.deposit_error_thorchain_chain_halted,
                    listOf(chain.raw),
                )
            )
        }
        if (inbound.address.isBlank()) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }
        return inbound
    }

    /** Reads the vault's record; an unreadable one refuses rather than risk a refunded inbound. */
    private suspend fun checkMayaPairing(
        pool: String,
        cacaoAddress: String,
        assetAddress: String?,
    ): MayaLpPairing =
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

    /** Reads the vault's record; an unreadable one refuses rather than risk a refunded inbound. */
    private suspend fun checkThorChainPairing(
        pool: String,
        runeAddress: String,
        assetAddress: String?,
    ): ThorChainLpPairing =
        try {
            checkThorChainLpPairing(pool, runeAddress, assetAddress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to read the THORChain LP record for %s", pool)
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_thorchain_lp_unverified)
            )
        }
}
