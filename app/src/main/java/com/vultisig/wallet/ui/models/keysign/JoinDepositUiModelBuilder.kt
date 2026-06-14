package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.ThorchainMemoParser
import com.vultisig.wallet.ui.models.deposit.VerifyDepositUiModel
import com.vultisig.wallet.ui.models.mappers.DepositTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.DepositTransactionToUiModelMapper
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the [VerifyUiModel.Deposit] model for the join-keysign verify screen. Extracted verbatim
 * from `JoinKeysignViewModel.loadTransaction`'s deposit branch — behavior is unchanged.
 */
internal class JoinDepositUiModelBuilder
@Inject
constructor(
    private val tokenRepository: TokenRepository,
    private val vaultRepository: VaultRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val mapDepositTransactionToUiModel: DepositTransactionToUiModelMapper,
    private val mapDepositTransactionHistoryData: DepositTransactionHistoryDataMapper,
    private val thorchainMemoParser: ThorchainMemoParser,
    private val feeResolver: JoinKeysignFeeResolver,
) {

    /**
     * Builds the deposit [JoinKeysignVerifyResult] from [payload] for the vault identified by
     * [vaultId], resolving fees and parsing the THOR memo.
     */
    suspend fun build(payload: KeysignPayload, vaultId: String): JoinKeysignVerifyResult {
        when (payload.blockChainSpecific) {
            is BlockChainSpecific.MayaChain,
            is BlockChainSpecific.THORChain,
            is BlockChainSpecific.Ethereum,
            is BlockChainSpecific.UTXO -> Unit

            else -> error("BlockChainSpecific ${payload.blockChainSpecific} is not supported")
        }

        val payloadToken = payload.coin
        val chain = payloadToken.chain

        val tokenValue =
            TokenValue(
                value = payload.toAmount,
                unit = payloadToken.ticker,
                decimals = payloadToken.decimal,
            )

        val vault =
            withContext(Dispatchers.IO) { vaultRepository.get(vaultId) } ?: error("Vault not found")

        val blockchainTransaction =
            Transfer(
                coin = payloadToken,
                vault =
                    VaultData(
                        vaultHexChainCode = vault.hexChainCode,
                        vaultHexPublicKey = vault.getPubKeyByChain(chain),
                    ),
                amount = tokenValue.value,
                to = payload.toAddress,
                memo = payload.memo,
                isMax = false,
            )

        val nativeCoin = withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }
        val estimatedTokenFees =
            feeResolver.resolveJoinKeysignNetworkFee(
                payload = payload,
                chain = chain,
                nativeCoin = nativeCoin,
                blockchainTransaction = blockchainTransaction,
            )

        val totalGasAndFee =
            gasFeeToEstimatedFee(
                GasFeeParams(
                    gasLimit = BigInteger.valueOf(1),
                    gasFee = estimatedTokenFees,
                    selectedToken = payload.coin,
                )
            )

        val parsedThorMemo = thorchainMemoParser.parse(payload.memo ?: "")

        val depositTransaction =
            DepositTransaction(
                id = UUID.randomUUID().toString(),
                vaultId = vaultId,
                srcToken = payload.coin,
                srcAddress = payload.coin.address,
                dstAddress = payload.toAddress,
                memo = payload.memo ?: "",
                srcTokenValue = tokenValue,
                estimatedFees = estimatedTokenFees,
                estimateFeesFiat = totalGasAndFee.formattedFiatValue,
                blockChainSpecific = payload.blockChainSpecific,
                operation = parsedThorMemo?.operation.orEmpty(),
                nodeAddress = parsedThorMemo?.nodeAddress.orEmpty(),
                pairedAddress = parsedThorMemo?.pairedAddress.orEmpty(),
                thorAddress = parsedThorMemo?.thorAddress.orEmpty(),
                pool = parsedThorMemo?.pool.orEmpty(),
            )
        val depositTransactionUiModel = mapDepositTransactionToUiModel(depositTransaction)
        return JoinKeysignVerifyResult(
            verifyUiModel = VerifyUiModel.Deposit(VerifyDepositUiModel(depositTransactionUiModel)),
            transactionTypeUiModel = TransactionTypeUiModel.Deposit(depositTransactionUiModel),
            transactionHistoryData = mapDepositTransactionHistoryData(depositTransactionUiModel),
        )
    }
}
