package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.OPERATION_MINT
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.isSecuredAssetEligible
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wallet.core.jni.proto.Bitcoin

/**
 * Builds a Bitcoin transaction plan (UTXO selection + fee) for a secured-asset deposit.
 *
 * @param vaultId the vault initiating the deposit.
 * @param selectedToken the UTXO token being deposited.
 * @param dstAddress the THORChain inbound vault address.
 * @param tokenAmountInt the deposit amount in base units.
 * @param specific the chain-specific data and UTXOs for the source address.
 * @param memo the deposit memo to embed in the plan.
 */
internal typealias BitcoinTransactionPlanBuilder =
    suspend (
        vaultId: String,
        selectedToken: Coin,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        specific: BlockChainSpecificAndUtxo,
        memo: String?,
    ) -> Bitcoin.TransactionPlan

/** Builds a Secured-Asset [DepositTransaction] minting a secured asset on THORChain. */
internal class SecuredAssetStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val thorAddressFieldState: TextFieldState,
    private val tokenAmountFieldState: TextFieldState,
    private val selectedAccountProvider: () -> Account?,
    private val resolveInboundAddress: suspend (Coin) -> String,
    private val vaultRepository: VaultRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val tokenRepository: TokenRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val gasFeeToEstimate: suspend (GasFeeParams) -> EstimatedGasFee,
    private val planBtcProvider: () -> Bitcoin.TransactionPlan?,
    private val setPlanBtc: (Bitcoin.TransactionPlan?) -> Unit,
    private val getBitcoinTransactionPlan: BitcoinTransactionPlanBuilder,
    private val selectUtxosIfNeeded:
        (Chain, BlockChainSpecificAndUtxo) -> BlockChainSpecificAndUtxo,
    private val validateBtcLikeAmount: (BigInteger, Chain) -> Unit,
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

        // Invalidate any cached UTXO plan so a re-submitted deposit recomputes its Bitcoin
        // transaction plan (UTXO selection + fee) for the current amount/destination/token rather
        // than reusing a stale plan from a previous submit.
        setPlanBtc(null)

        val selectedAccount =
            selectedAccountProvider()
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )

        val selectedToken = selectedAccount.token

        if (!selectedAccount.token.isSecuredAssetEligible()) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_not_secured_asset)
            )
        }

        val srcAddress = selectedToken.address

        val dstAddr = resolveInboundAddress(selectedToken)
        if (dstAddr.isBlank()) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }
        val memo = "SECURE+:$thorAddress"

        val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val tokenAmountInt = tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

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
                to = dstAddr,
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
            blockChainSpecificRepository
                .getSpecific(
                    chain,
                    srcAddress,
                    selectedToken,
                    gasFee,
                    memo = memo,
                    isSwap = false,
                    dstAddress = dstAddr,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                    tokenAmountValue = tokenAmountInt,
                )
                .let { specific ->
                    if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
                        planBtcProvider()
                            ?: getBitcoinTransactionPlan(
                                    vaultId,
                                    selectedToken,
                                    dstAddr,
                                    tokenAmountInt,
                                    specific,
                                    memo,
                                )
                                .also { plan -> setPlanBtc(plan) }

                        selectUtxosIfNeeded(chain, specific)
                    } else {
                        specific
                    }
                }
        if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
            validateBtcLikeAmount(tokenAmountInt, chain)
        }
        val estimatedGasFee = gasFeeToEstimate(fromGas)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddr,
            memo = memo,
            srcTokenValue = TokenValue(value = tokenAmountInt, token = selectedToken),
            estimatedFees = gasFee,
            estimateFeesFiat = estimatedGasFee.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            utxos = specific.utxos,
            thorAddress = thorAddress,
            operation = OPERATION_MINT,
        )
    }
}
