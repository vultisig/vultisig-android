package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
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
import com.vultisig.wallet.ui.models.deposit.bondLpUnitsCeiling
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import java.util.UUID

/** Builds a Bond [DepositTransaction] for THORChain or MayaChain. */
internal class BondStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val stateProvider: () -> DepositFormUiModel,
    private val selectedTokenProvider: () -> Coin?,
    private val selectedAccountProvider: () -> Account?,
    private val addressProvider: () -> Address?,
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
    private val requireTokenAmount: (Coin, Account, Address, TokenValue) -> BigInteger,
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

        // Enforced when a surplus was loaded, not required the way Unbond requires its ceiling:
        // Bond still accepts a typed asset when the pool list comes back empty, and that path has
        // no figure to measure against.
        val bondableUnits = state.bondLpUnitsCeiling()
        if (
            bondableUnits != null &&
                (lpUnits.toBigIntegerOrNull() ?: BigInteger.ZERO) > bondableUnits
        ) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_lpunits_exceeds_available)
            )
        }

        val operatorFeeText = operatorFeeFieldState.text.toString()
        val operatorFeeValue: Int? =
            if (operatorFeeText.isNotBlank()) {
                operatorFeeText.toIntOrNull()?.takeIf { it in 0..10000 }
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_invalid_operator_fee)
                    )
            } else {
                null
            }

        val selectedToken =
            selectedTokenProvider()
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )

        val srcAddress = selectedToken.address

        val gasFee = calculateGasFee(chain, selectedToken, srcAddress)

        val tokenAmountInt =
            if (depositChain == Chain.ThorChain) {
                val selectedAccount =
                    selectedAccountProvider()
                        ?: throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_no_address)
                        )
                val address =
                    addressProvider()
                        ?: throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_no_address)
                        )
                requireTokenAmount(selectedToken, selectedAccount, address, gasFee)
            } else {
                tokenAmountFieldState.text
                    .toString()
                    .toBigDecimalOrNull()
                    ?.movePointRight(selectedToken.decimal)
                    ?.toBigInteger() ?: BigInteger.ONE
            }

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
