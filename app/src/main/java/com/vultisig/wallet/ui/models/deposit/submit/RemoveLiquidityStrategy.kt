package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.OPERATION_WITHDRAW
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import java.util.UUID
import kotlinx.coroutines.flow.first

/** Builds a Remove-Liquidity [DepositTransaction] withdrawing the slider-selected pool fraction. */
internal class RemoveLiquidityStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val lpPoolIdProvider: () -> String?,
    private val stateProvider: () -> DepositFormUiModel,
    private val accountsRepository: AccountsRepository,
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

        val srcAddress = selectedToken.address
        val gasFee = calculateGasFee(chain, selectedToken, srcAddress)

        val s = stateProvider()
        // Reuse the exact basis points (0..10000) the slider used to compute the displayed redeem
        // amount so the on-chain memo withdraws the same fraction the user saw.
        val basisPoints = s.removeLpBasisPoints

        if (basisPoints <= 0 || basisPoints > 10_000) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_from_invalid_amount)
            )
        }

        val memo = DepositMemo.RemoveLiquidity(poolId, basisPoints)

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
            operation = OPERATION_WITHDRAW,
            pool = poolId,
        )
    }
}
