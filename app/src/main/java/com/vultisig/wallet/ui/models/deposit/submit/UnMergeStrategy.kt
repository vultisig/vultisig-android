package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.models.thorchain.MergeAccount
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.utils.toUnit
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import java.util.UUID
import vultisig.keysign.v1.TransactionType
import wallet.core.jni.CoinType

/**
 * Builds an UnMerge [DepositTransaction] for THORChain, converting the entered amount to shares.
 */
internal class UnMergeStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val stateProvider: () -> DepositFormUiModel,
    private val addressProvider: () -> Address?,
    private val rujiMergeBalancesProvider: () -> List<MergeAccount>?,
    private val tokenAmountFieldState: TextFieldState,
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
        val unmergeToken = stateProvider().selectedUnMergeCoin
        val unMergeAccountBalance =
            rujiMergeBalancesProvider()?.firstOrNull {
                it.pool?.mergeAsset?.metadata?.symbol.equals(unmergeToken.ticker, true)
            }
        val maxShares = unMergeAccountBalance?.shares?.toBigInteger() ?: BigInteger.ZERO

        // transform amount back to share units
        val tokenShares =
            tokenAmountFieldState.text.toString().toBigDecimalOrNull()?.let {
                CoinType.THORCHAIN.toUnit(it)
            }

        if (tokenShares == null || tokenShares <= BigInteger.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        if (tokenShares > maxShares) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_max_shares)
            )
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

        val account =
            address.accounts.find { it.token.ticker.equals(unmergeToken.ticker, ignoreCase = true) }
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.merge_account_doesnt_exist)
                )

        val srcAddress = account.token.address
        val dstAddr = unmergeToken.contract
        val memo = "unmerge:${unmergeToken.denom}:${tokenShares}"
        val gasFee = calculateGasFee(chain, account.token, srcAddress)

        val specific =
            blockChainSpecificRepository.getSpecific(
                chain,
                srcAddress,
                account.token,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_THOR_UNMERGE,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, account.token)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = account.token,
            srcAddress = srcAddress,
            dstAddress = dstAddr,
            memo = memo,
            srcTokenValue = TokenValue(value = tokenShares, token = account.token),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }
}
