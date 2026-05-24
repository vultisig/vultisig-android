package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositMemo.Bond
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.OPERATION_BOND
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.usecases.DepositMemoAssetsValidatorUseCase
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID

/** Builds a Bond [DepositTransaction] for THORChain or MayaChain. */
internal class BondStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val stateProvider: () -> DepositFormUiModel,
    private val selectedTokenProvider: () -> Coin?,
    private val nodeAddressFieldState: TextFieldState,
    private val tokenAmountFieldState: TextFieldState,
    private val providerFieldState: TextFieldState,
    private val assetsFieldState: TextFieldState,
    private val lpUnitsFieldState: TextFieldState,
    private val operatorFeeFieldState: TextFieldState,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val isAssetCharsValid: DepositMemoAssetsValidatorUseCase,
    private val isLpUnitCharsValid: (String) -> Boolean,
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

        val state = stateProvider()

        if (state.isWhitelistFailed) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.bond_not_whitelisted_error)
            )
        }

        val depositChain = state.depositChain

        val nodeAddress = nodeAddressFieldState.text.toString()

        if (nodeAddress.isBlank() || !chainAccountAddressRepository.isValid(chain, nodeAddress)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }

        val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

        if (
            depositChain == Chain.ThorChain &&
                (tokenAmount == null || tokenAmount <= BigDecimal.ZERO)
        ) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val assets = assetsFieldState.text.toString()

        if (depositChain == Chain.MayaChain && !isAssetCharsValid(assets)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_assets)
            )
        }

        val lpUnits = lpUnitsFieldState.text.toString()

        if (depositChain == Chain.MayaChain && !isLpUnitCharsValid(lpUnits)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_lpunits)
            )
        }

        val operatorFeeAmount = operatorFeeFieldState.text.toString().toBigDecimalOrNull()

        val selectedToken =
            selectedTokenProvider()
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )

        val tokenAmountInt =
            tokenAmount?.movePointRight(selectedToken.decimal)?.toBigInteger() ?: BigInteger.ONE

        val operatorFeeValue =
            operatorFeeAmount
                ?.movePointRight(if (depositChain == Chain.ThorChain) 2 else 0)
                ?.toInt()

        val srcAddress = selectedToken.address

        val gasFee = calculateGasFee(chain, selectedToken, srcAddress)

        val providerText = providerFieldState.text.toString()
        val provider = providerText.ifBlank { null }

        val memo =
            when (depositChain) {
                Chain.MayaChain ->
                    Bond.Maya(
                        nodeAddress = nodeAddress,
                        providerAddress = provider,
                        lpUnits = lpUnits.toLongOrNull(),
                        assets = assets,
                    )

                Chain.ThorChain ->
                    Bond.Thor(
                        nodeAddress = nodeAddress,
                        providerAddress = provider,
                        operatorFee = operatorFeeValue,
                    )

                else -> error("chain is invalid")
            }

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
            srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            operation = OPERATION_BOND,
            nodeAddress = nodeAddress,
        )
    }
}
