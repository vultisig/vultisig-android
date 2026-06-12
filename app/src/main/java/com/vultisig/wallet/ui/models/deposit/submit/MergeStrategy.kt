package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import java.util.UUID
import vultisig.keysign.v1.TransactionType

/** Builds a Merge [DepositTransaction] for THORChain. */
internal class MergeStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val stateProvider: () -> DepositFormUiModel,
    private val addressProvider: () -> Address?,
    private val requireTokenAmount: (Coin, Account, Address, TokenValue) -> BigInteger,
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

        val address =
            addressProvider()
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )

        val mergeToken = stateProvider().selectedCoin

        val selectedAccount =
            address.accounts.find { it.token.ticker.equals(mergeToken.ticker, ignoreCase = true) }
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.merge_account_doesnt_exist)
                )

        val selectedToken = selectedAccount.token

        val srcAddress = selectedToken.address

        val gasFee = calculateGasFee(chain, selectedToken, srcAddress)

        val dstAddr = mergeToken.contract

        val memo = "merge:${mergeToken.denom}"

        val tokenAmount = requireTokenAmount(selectedToken, selectedAccount, address, gasFee)

        val specific =
            blockChainSpecificRepository.getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_THOR_MERGE,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddr,
            memo = memo,
            srcTokenValue = TokenValue(value = tokenAmount, token = selectedToken),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }
}
