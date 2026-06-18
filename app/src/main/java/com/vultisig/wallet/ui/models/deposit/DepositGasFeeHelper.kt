package com.vultisig.wallet.ui.models.deposit

import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.proto.Bitcoin

/**
 * Reusable gas/fee/amount helpers extracted from `DepositFormViewModel` so the fee estimation,
 * Bitcoin transaction-plan building and fiat/token amount conversion are unit-testable in isolation
 * and shareable across deposit strategies. Stateless: all per-form context (vault, chain, currency)
 * is passed in by the caller.
 */
internal class DepositGasFeeHelper
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val tokenRepository: TokenRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val tokenPriceRepository: TokenPriceRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
) {

    /**
     * Fetches the native-token deposit gas fee for [address] on [chain] and reports the formatted
     * total-gas / estimated-fee strings via [onResult] for display. Runs on [scope]; no-op when the
     * address holds no native-token account.
     *
     * @param vaultId the vault whose keys back the transaction.
     */
    fun loadGasFeeForDisplay(
        scope: CoroutineScope,
        vaultId: String,
        chain: Chain,
        address: Address,
        onResult: (totalGas: UiText, estimatedFee: UiText) -> Unit,
    ) {
        scope.safeLaunch {
            val token = address.accounts.find { it.token.isNativeToken }?.token ?: return@safeLaunch
            val srcAddress = token.address
            val gasFee = calculateGasFee(vaultId, chain, token, srcAddress)
            val specific =
                blockChainSpecificRepository.getSpecific(
                    chain,
                    srcAddress,
                    token,
                    gasFee,
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                )
            val estimatedGasFee = getFeesFiatValue(chain, specific, gasFee, token)
            onResult(
                UiText.DynamicString(estimatedGasFee.formattedTokenValue),
                UiText.DynamicString(estimatedGasFee.formattedFiatValue),
            )
        }
    }

    /**
     * Calculates the native-token gas fee for a deposit on [chain] originating from [srcAddress].
     *
     * @param vaultId the vault whose keys back the transaction.
     * @return the estimated fee expressed in the chain's native token.
     */
    suspend fun calculateGasFee(
        vaultId: String,
        chain: Chain,
        token: Coin,
        srcAddress: String,
    ): TokenValue {
        val vault =
            withContext(Dispatchers.IO) { vaultRepository.get(vaultId) } ?: error("Vault not found")
        val blockchainTransaction =
            Transfer(
                coin = token,
                vault =
                    VaultData(
                        vaultHexChainCode = vault.hexChainCode,
                        vaultHexPublicKey = vault.getPubKeyByChain(chain),
                    ),
                amount = BigInteger.ZERO,
                to = srcAddress,
                isMax = false,
            )
        val fees =
            withContext(Dispatchers.IO) { feeServiceComposite.calculateFees(blockchainTransaction) }
        val nativeCoin = withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }
        return TokenValue(value = fees.amount, token = nativeCoin)
    }

    /**
     * Converts a raw [gasFee] into a displayable [EstimatedGasFee] (token + fiat), applying the EVM
     * gas-limit from [specific] when [chain] is EVM and a unit limit otherwise.
     */
    suspend fun getFeesFiatValue(
        chain: Chain?,
        specific: BlockChainSpecificAndUtxo,
        gasFee: TokenValue,
        selectedToken: Coin,
    ): EstimatedGasFee {
        return gasFeeToEstimatedFee(
            GasFeeParams(
                gasLimit =
                    if (chain?.standard == TokenStandard.EVM) {
                        (specific.blockChainSpecific as BlockChainSpecific.Ethereum).gasLimit
                    } else {
                        BigInteger.valueOf(1)
                    },
                gasFee = gasFee,
                selectedToken = selectedToken,
            )
        )
    }

    /**
     * Builds the Bitcoin (UTXO) transaction plan used to size fees and select inputs for a deposit.
     *
     * @param vaultId the vault whose keys back the transaction.
     * @param memo the deposit memo to embed in the plan.
     */
    suspend fun getBitcoinTransactionPlan(
        vaultId: String,
        selectedToken: Coin,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        specific: BlockChainSpecificAndUtxo,
        memo: String?,
    ): Bitcoin.TransactionPlan {
        val vault = vaultRepository.get(vaultId) ?: error("Can't calculate plan fees")

        val keysignPayload =
            KeysignPayload(
                coin = selectedToken,
                toAddress = dstAddress,
                toAmount = tokenAmountInt,
                blockChainSpecific = specific.blockChainSpecific,
                memo = memo,
                vaultPublicKeyECDSA = vault.pubKeyECDSA,
                vaultLocalPartyID = vault.localPartyID,
                utxos = specific.utxos,
                libType = vault.libType,
                wasmExecuteContractPayload = null,
            )

        val utxo = UtxoHelper.getHelper(vault, keysignPayload.coin.coinType)

        val plan = utxo.getBitcoinTransactionPlan(keysignPayload)
        return plan
    }

    /**
     * Converts a token/fiat amount [value] using the live price of [token] in [currency], applying
     * [transform] (token→fiat or fiat→token). Returns an empty string for unparseable input and
     * `null` when the price is unavailable or zero so the caller can skip the conversion.
     */
    suspend fun convertAmountValue(
        value: String,
        token: Coin,
        currency: AppCurrency,
        transform: (value: BigDecimal, price: BigDecimal) -> BigDecimal,
    ): String? {
        val decimalValue = value.toBigDecimalOrNull() ?: return ""
        return try {
            val price = tokenPriceRepository.getPrice(token, currency).first()
            if (price == BigDecimal.ZERO) {
                Timber.w(
                    "convertAmountValue: price is ZERO for token %s, skipping conversion",
                    token.ticker,
                )
                return null
            }
            transform(decimalValue, price).toPlainString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Failed to get price for token %s", token.ticker)
            null
        }
    }
}
