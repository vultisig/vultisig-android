@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.OPERATION_MINT
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.usecases.CheckMayaLpPairingUseCase
import com.vultisig.wallet.data.usecases.MayaChainLpPreflightUseCase
import com.vultisig.wallet.data.usecases.MayaLpPairing
import com.vultisig.wallet.data.usecases.ThorChainLpPreflightUseCase
import com.vultisig.wallet.ui.models.defi.mayaLpPairedChain
import com.vultisig.wallet.ui.models.defi.parseThorChainPool
import com.vultisig.wallet.ui.models.deposit.toError
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import timber.log.Timber

/** Builds an Add-Liquidity [DepositTransaction] for the RUNE/CACAO side of a pool. */
internal class AddLiquidityStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val lpPoolIdProvider: () -> String?,
    private val tokenAmountFieldState: TextFieldState,
    private val accountsRepository: AccountsRepository,
    private val thorChainLpPreflight: ThorChainLpPreflightUseCase,
    private val mayaChainLpPreflight: MayaChainLpPreflightUseCase,
    private val checkMayaLpPairing: CheckMayaLpPairingUseCase,
    private val resolvePairedAddress: suspend (Chain, String, String) -> String?,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val calculateGasFee: suspend (Chain, Coin, String) -> TokenValue,
    private val getFeesFiatValue:
        suspend (BlockChainSpecificAndUtxo, TokenValue, Coin) -> EstimatedGasFee,
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

        val assetChain = parseThorChainPool(poolId).chain
        val isSymmetricPool = assetChain != null && assetChain != Chain.ThorChain

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
        val acceptsSingleSided =
            mayaPairing == MayaLpPairing.SingleSidedPosition ||
                mayaPairing == MayaLpPairing.PairedWithOtherAddress
        if (mayaPairing == MayaLpPairing.AddressMismatch && pairedAddress != null) {
            throw InvalidTransactionDataException(
                UiText.FormattedText(R.string.deposit_error_maya_lp_address_mismatch, listOf(poolId))
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
        val isThorChainLpAdd = chain == Chain.ThorChain || (isSymmetricPool && chain == assetChain)
        if (isThorChainLpAdd) {
            thorChainLpPreflight(poolId, isPairedAdd = pairedAddress != null)?.let { block ->
                throw block.toError()
            }
        }
        if (isMayaChainLpAdd) {
            mayaChainLpPreflight(poolId, isPairedAdd = memoPairedAddress != null)?.let { block ->
                throw block.toError()
            }
        }

        val srcAddress = selectedToken.address
        val gasFee = calculateGasFee(chain, selectedToken, srcAddress)
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

}
