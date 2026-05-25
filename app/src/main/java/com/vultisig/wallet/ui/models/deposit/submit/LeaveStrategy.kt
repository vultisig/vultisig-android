package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.OPERATION_LEAVE
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import java.util.UUID

/** Builds a Leave [DepositTransaction] for THORChain or MayaChain. */
internal class LeaveStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val selectedTokenProvider: () -> Coin?,
    private val nodeAddressFieldState: TextFieldState,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
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

        val nodeAddress = nodeAddressFieldState.text.toString()

        if (nodeAddress.isBlank() || !chainAccountAddressRepository.isValid(chain, nodeAddress)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }

        val selectedToken =
            selectedTokenProvider()
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )

        val srcAddress = selectedToken.address

        val gasFee = calculateGasFee(chain, selectedToken, srcAddress)

        val memo = DepositMemo.Leave(nodeAddress = nodeAddress)

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
            dstAddress = nodeAddress,
            memo = memo.toString(),
            srcTokenValue =
                TokenValue(
                    value =
                        (chain == Chain.MayaChain).let {
                            if (it) 1.toBigInteger() else BigInteger.ZERO
                        },
                    token = selectedToken,
                ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            operation = OPERATION_LEAVE,
            nodeAddress = nodeAddress,
        )
    }
}
