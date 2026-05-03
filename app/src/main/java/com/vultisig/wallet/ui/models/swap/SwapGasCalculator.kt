package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Swap
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.getDustThreshold
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

internal data class GasCalculationResult(val gasFee: TokenValue, val estimated: EstimatedGasFee)

internal class SwapGasCalculator
@Inject
constructor(
    private val feeServiceComposite: FeeServiceComposite,
    private val vaultRepository: VaultRepository,
    private val tokenRepository: TokenRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
) {

    suspend fun calculateGasFee(sendSrc: SendSrc, vaultId: String): GasCalculationResult? {
        val chain = sendSrc.address.chain
        val selectedToken = sendSrc.account.token
        val vault = vaultRepository.get(vaultId) ?: return null

        val blockchainTransaction =
            Swap(
                coin = selectedToken,
                vault =
                    VaultData(
                        vaultHexPublicKey = vault.getPubKeyByChain(chain),
                        vaultHexChainCode = vault.hexChainCode,
                    ),
                amount = BigInteger.ZERO,
                to = sendSrc.address.address,
                callData = "",
                approvalData = null,
                isMax = false,
            )

        val fee =
            withContext(Dispatchers.IO) { feeServiceComposite.calculateFees(blockchainTransaction) }

        val nativeCoin = withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }

        val gasFee = TokenValue(value = fee.amount, token = nativeCoin)
        var estimatedTotalFee = gasFee

        if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
            val specific =
                blockChainSpecificRepository.getSpecific(
                    chain = selectedToken.chain,
                    address = selectedToken.address,
                    token = selectedToken,
                    gasFee = gasFee,
                    isSwap = true,
                    isMaxAmountEnabled = false,
                    isDeposit = false,
                )
            val plan =
                getBitcoinTransactionPlan(
                    vaultId,
                    selectedToken,
                    selectedToken.address,
                    chain.getDustThreshold.add(BigInteger.ONE),
                    BlockChainSpecificAndUtxo(
                        blockChainSpecific = BlockChainSpecific.UTXO(byteFee = gasFee.value, true),
                        utxos = specific.utxos,
                    ),
                    memo = null,
                )
            estimatedTotalFee = gasFee.copy(value = (plan ?: return null).fee.toBigInteger())
        }

        val estimated =
            gasFeeToEstimatedFee(
                GasFeeParams(
                    gasLimit = BigInteger.valueOf(1),
                    gasFee = estimatedTotalFee,
                    selectedToken = selectedToken,
                    perUnit = true,
                )
            )

        return GasCalculationResult(gasFee = gasFee, estimated = estimated)
    }

    private suspend fun getBitcoinTransactionPlan(
        vaultId: String,
        selectedToken: Coin,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        specific: BlockChainSpecificAndUtxo,
        memo: String?,
    ): Bitcoin.TransactionPlan? {
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
        if (plan.error != SigningError.OK) {
            Timber.e("UTXO plan error: %s", plan.error.name)
            return null
        }
        return plan
    }

    suspend fun getSpecificAndUtxo(srcToken: Coin, srcAddress: String, gasFee: TokenValue) =
        try {
            blockChainSpecificRepository.getSpecific(
                chain = srcToken.chain,
                address = srcAddress,
                token = srcToken,
                gasFee = gasFee,
                isSwap = true,
                isMaxAmountEnabled = false,
                isDeposit = srcToken.chain == Chain.MayaChain,
                gasLimit = getGasLimit(srcToken),
            )
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            Timber.d(e)
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.swap_screen_invalid_specific_and_utxo)
            )
        }

    internal suspend fun computeUtxoPlanFeeResult(
        vaultId: String,
        srcToken: Coin,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        specificAndUtxo: BlockChainSpecificAndUtxo,
        memo: String?,
    ): GasCalculationResult? {
        if (srcToken.chain.standard != TokenStandard.UTXO || srcToken.chain == Chain.Cardano) {
            return null
        }
        val plan =
            getBitcoinTransactionPlan(
                vaultId = vaultId,
                selectedToken = srcToken,
                dstAddress = dstAddress,
                tokenAmountInt = tokenAmountInt,
                specific = specificAndUtxo,
                memo = memo,
            ) ?: return null
        val nativeCoin =
            withContext(Dispatchers.IO) { tokenRepository.getNativeToken(srcToken.chain.id) }
        val planFee = TokenValue(value = plan.fee.toBigInteger(), token = nativeCoin)
        val estimated =
            gasFeeToEstimatedFee(
                GasFeeParams(
                    gasLimit = BigInteger.valueOf(1),
                    gasFee = planFee,
                    selectedToken = srcToken,
                    perUnit = true,
                )
            )
        return GasCalculationResult(gasFee = planFee, estimated = estimated)
    }

    private fun getGasLimit(token: Coin): BigInteger? {
        val isEVMSwap = token.isNativeToken && token.chain in listOf(Chain.Ethereum, Chain.Arbitrum)
        return if (isEVMSwap)
            BigInteger.valueOf(if (token.chain == Chain.Ethereum) ETH_GAS_LIMIT else ARB_GAS_LIMIT)
        else null
    }

    companion object {
        const val ETH_GAS_LIMIT: Long = 40_000
        const val ARB_GAS_LIMIT: Long = 400_000
    }
}
