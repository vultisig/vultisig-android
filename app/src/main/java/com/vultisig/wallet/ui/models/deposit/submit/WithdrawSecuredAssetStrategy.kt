package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.OPERATION_WITHDRAW
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.getChain
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Builds a Withdraw-Secured-Asset [DepositTransaction] redeeming a secured asset back to its chain.
 */
internal class WithdrawSecuredAssetStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val stateProvider: () -> DepositFormUiModel,
    private val thorAddressFieldState: TextFieldState,
    private val tokenAmountFieldState: TextFieldState,
    private val accountsRepository: AccountsRepository,
    private val vaultRepository: VaultRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val tokenRepository: TokenRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val gasFeeToEstimate: suspend (GasFeeParams) -> EstimatedGasFee,
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

        val thorAddress = thorAddressFieldState.text.toString()
        if (thorAddress.isBlank()) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.thorchain_address_not_found_in_vault)
            )
        }

        val selectedSecureAsset = stateProvider().selectedSecuredAsset

        // getChain() throws on an unrecognised ticker rather than silently defaulting to a chain —
        // the DepositFormViewModel catch block turns that into a user-facing error instead of
        // misrouting the withdrawal to the wrong chain.
        val secureAssetChain = selectedSecureAsset.ticker.getChain()
        val dstAddr =
            accountsRepository.loadAddress(vaultId, secureAssetChain).firstOrNull()
                ?: throw InvalidTransactionDataException(
                    UiText.FormattedText(
                        R.string.deposit_error_chain_not_enabled,
                        listOf(secureAssetChain.raw, selectedSecureAsset.ticker),
                    )
                )

        if (dstAddr.address.isBlank()) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }

        val selectedToken = selectedSecureAsset.coin

        val memo = "SECURE-:${dstAddr.address}"

        val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val tokenAmountInt = tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

        if ((selectedSecureAsset.tokenValue?.value ?: BigInteger.ZERO) < tokenAmountInt) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_insufficient_balance)
            )
        }

        val vault =
            withContext(Dispatchers.IO) { vaultRepository.get(vaultId) } ?: error("Vault not found")

        val blockchainTransaction =
            Transfer(
                coin = selectedToken,
                vault =
                    VaultData(
                        vaultHexChainCode = vault.hexChainCode,
                        vaultHexPublicKey = vault.getPubKeyByChain(chain),
                    ),
                amount = tokenAmountInt,
                to = dstAddr.address,
                memo = memo,
                isMax = false,
            )

        val fees =
            withContext(Dispatchers.IO) { feeServiceComposite.calculateFees(blockchainTransaction) }
        val nativeCoin = withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }
        val fromGas =
            GasFeeParams(
                gasLimit = BigInteger.ONE,
                gasFee = TokenValue(value = fees.amount, token = nativeCoin),
                selectedToken = selectedToken,
            )
        val gasFee = TokenValue(value = fees.amount, token = nativeCoin)

        val specific =
            blockChainSpecificRepository.getSpecific(
                chain,
                thorAddress,
                selectedToken,
                gasFee,
                memo = memo,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                tokenAmountValue = tokenAmountInt,
            )

        val estimatedGasFee = gasFeeToEstimate(fromGas)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = thorAddress,
            dstAddress = "",
            memo = memo,
            srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
            estimatedFees = gasFee,
            estimateFeesFiat = estimatedGasFee.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            operation = OPERATION_WITHDRAW,
        )
    }
}
