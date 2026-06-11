package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import java.util.UUID
import kotlinx.coroutines.flow.first

/** Builds a Remove-Cacao-Pool [DepositTransaction] withdrawing CACAO from the Maya pool. */
internal class RemoveCacaoPoolStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val tokenAmountFieldState: TextFieldState,
    private val accountsRepository: AccountsRepository,
    private val validateMayaTransactionHeight: suspend (String) -> Boolean,
    private val validateBasisPoints: (Int?) -> UiText?,
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

        val address = accountsRepository.loadAddress(vaultId, chain).first()

        val selectedToken =
            address.accounts.firstOrNull { it.token.isNativeToken }?.token
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )

        val srcAddress = selectedToken.address

        validateMayaTransactionHeight(srcAddress) ||
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_has_not_reached_maturity)
            )

        val gasFee = calculateGasFee(chain, selectedToken, srcAddress)

        val basisPoints = tokenAmountFieldState.text.toString().toIntOrNull()

        validateBasisPoints(basisPoints)?.let { throw InvalidTransactionDataException(it) }

        val memo =
            DepositMemo.WithdrawPool(
                basisPoints = basisPoints!! * 100 // 10000 BP = 100%; basisPoints in 0..100
            )

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
            srcTokenValue = TokenValue(value = BigInteger.ZERO, token = selectedToken),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
        )
    }
}
