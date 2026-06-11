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
import com.vultisig.wallet.data.usecases.ThorChainLpPreflightUseCase
import com.vultisig.wallet.ui.models.defi.parseThorChainPool
import com.vultisig.wallet.ui.models.deposit.toError
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.first

/** Builds an Add-Liquidity [DepositTransaction] for the RUNE/CACAO side of a pool. */
internal class AddLiquidityStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val lpPoolIdProvider: () -> String?,
    private val tokenAmountFieldState: TextFieldState,
    private val accountsRepository: AccountsRepository,
    private val thorChainLpPreflight: ThorChainLpPreflightUseCase,
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

        // Preflight against THORChain network state — pool status and the relevant mimir pause
        // keys. Refuses to build the keysign payload when the network would refund the inbound,
        // sparing the user the inbound gas spend.
        if (chain == Chain.ThorChain) {
            thorChainLpPreflight(poolId)?.let { block -> throw block.toError() }
        }

        val srcAddress = selectedToken.address
        val gasFee = calculateGasFee(chain, selectedToken, srcAddress)
        val pairedAddress = resolvePairedAddress(chain, vaultId, poolId)
        // For a RUNE-side add into a non-THOR pool the memo MUST carry the paired-chain address —
        // otherwise THORChain can't credit the LP when the asset half is later deposited.
        if (chain == Chain.ThorChain && pairedAddress == null) {
            val assetChain = parseThorChainPool(poolId).chain
            if (assetChain != null && assetChain != Chain.ThorChain) {
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )
            }
        }
        val memo = DepositMemo.AddLiquidity(poolId, pairedAddress)

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
            id = UUID.randomUUID().toString(),
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
            pairedAddress = pairedAddress.orEmpty(),
        )
    }
}
