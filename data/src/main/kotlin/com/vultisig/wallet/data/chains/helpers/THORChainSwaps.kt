package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.wallet.Swaps
import tss.KeysignResponse
import wallet.core.jni.CoinType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Ethereum.SigningInput
import wallet.core.jni.proto.Ethereum.Transaction
import java.math.BigInteger

class THORChainSwaps(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    companion object {
        const val AFFILIATE_FEE_ADDRESS = "va"
        const val AFFILIATE_FEE_RATE_BP = 50 // 50 BP
        const val REFERRED_AFFILIATE_FEE_RATE_BP = 35 // 35 BP when there's a referral
        const val REFERRED_USER_FEE_RATE_BP = 10 // 10 BP for the referrer
        
        // Legacy constants for backward compatibility
        const val AFFILIATE_FEE_RATE = "50"

        const val MAYA_STREAMING_INTERVAL = "3"
    }

    private fun getPreSignedInputData(
        swapPayload: THORChainSwapPayload,
        keysignPayload: KeysignPayload,
        nonceIncrement: BigInteger,
    ): ByteArray {
        when (swapPayload.fromCoin.chain) {
             Chain.ThorChain -> {
                return ThorchainSwapHelper()
                    .getSwapPreSignedInputData(keysignPayload)
            }

            Chain.Bitcoin, Chain.Litecoin, Chain.Dogecoin, Chain.BitcoinCash -> {
                val helper =
                    UtxoHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
                val input = helper.getSwapPreSigningInputData(keysignPayload)
                return helper.getSigningInputData(
                    keysignPayload,
                    input.toBuilder()
                )
            }

            Chain.Ethereum, Chain.BscChain, Chain.Avalanche,Chain.Base, Chain.Arbitrum -> {
                val helper =
                    EvmHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
                return helper.getSwapPreSignedInputData(
                    keysignPayload = keysignPayload,
                    nonceIncrement = nonceIncrement
                )
            }

            Chain.Ripple -> {
                return RippleHelper.getPreSignedInputData(keysignPayload)
            }

            Chain.GaiaChain -> {
                val helper = CosmosHelper(
                    coinType = CoinType.COSMOS,
                    denom = CosmosHelper.ATOM_DENOM,
                )
                return helper.getSwapPreSignedInputData(
                    keysignPayload = keysignPayload
                )
            }

            Chain.Tron -> {
                val helper = TronHelper(
                    coinType = CoinType.TRON,
                    vaultHexPublicKey = vaultHexPublicKey,
                    vaultHexChainCode = vaultHexChainCode
                )

                return helper.getPreSignedInputData(
                    keysignPayload = keysignPayload,
                )
            }

            else -> {
                throw Exception("Unsupported chain")
            }
        }
    }

    fun getPreSignedImageHash(
        swapPayload: THORChainSwapPayload,
        keysignPayload: KeysignPayload,
        nonceIncrement: BigInteger,
    ): List<String> {
        val inputData = getPreSignedInputData(swapPayload, keysignPayload, nonceIncrement)

        val chain = swapPayload.fromCoin.chain
        val coinType = keysignPayload.coin.coinType

        return Swaps.getPreSignedImageHash(inputData, coinType, chain)
    }

    private fun getPreSigningOutput(coinType: CoinType, inputData: ByteArray): List<String> =
        Swaps.getPreSigningOutput(
            preImageHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        )

    private fun getPreSignedApproveInputData(
        approvePayload: ERC20ApprovePayload,
        keysignPayload: KeysignPayload,
    ): ByteArray {
        val approveInput = SigningInput.newBuilder()
            .setTransaction(
                Transaction.newBuilder()
                    .setErc20Approve(
                        Transaction.ERC20Approve.newBuilder()
                            .setSpender(approvePayload.spender)
                            .setAmount(ByteString.copyFrom(approvePayload.amount.toByteArray()))
                    )
            )
            .setToAddress(keysignPayload.coin.contractAddress)
            .build()

        return EvmHelper(
            keysignPayload.coin.coinType,
            vaultHexPublicKey,
            vaultHexChainCode
        ).getPreSignedInputData(signingInput = approveInput, keysignPayload = keysignPayload)
    }

    fun getPreSignedApproveImageHash(
        approvePayload: ERC20ApprovePayload,
        keysignPayload: KeysignPayload,
    ): List<String> = getPreSigningOutput(
        coinType = keysignPayload.coin.coinType,
        inputData = getPreSignedApproveInputData(approvePayload, keysignPayload)
    )

    fun getSignedApproveTransaction(
        approvePayload: ERC20ApprovePayload,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val inputData = getPreSignedApproveInputData(approvePayload, keysignPayload)
        return EvmHelper(
            keysignPayload.coin.coinType,
            vaultHexPublicKey,
            vaultHexChainCode
        ).getSignedTransaction(inputData, signatures)
    }

    fun getSignedTransaction(
        swapPayload: THORChainSwapPayload,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
        nonceIncrement: BigInteger,
    ): SignedTransactionResult {
        val inputData = getPreSignedInputData(swapPayload, keysignPayload, nonceIncrement)
        when (swapPayload.fromCoin.chain) {
            Chain.ThorChain -> {
                return ThorChainHelper.thor(vaultHexPublicKey, vaultHexChainCode)
                    .getSignedTransaction(
                        inputData,
                        signatures
                    )
            }

            Chain.Bitcoin, Chain.Dogecoin, Chain.BitcoinCash, Chain.Litecoin -> {
                val helper =
                    UtxoHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
                return helper.getSignedTransaction(inputData, signatures)
            }
            Chain.Ripple -> {
                return RippleHelper.getSignedTransaction(keysignPayload,signatures)
            }
            Chain.Ethereum, Chain.Avalanche, Chain.BscChain,Chain.Base,Chain.Arbitrum -> {
                val helper =
                    EvmHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
                return helper.getSignedTransaction(inputData, signatures)
            }

            Chain.GaiaChain -> {
                val helper = CosmosHelper(
                    coinType = CoinType.COSMOS,
                    denom = CosmosHelper.ATOM_DENOM,
                )
                return helper.getSignedTransaction(
                    input = inputData,
                    keysignPayload = keysignPayload,
                    signatures = signatures
                )
            }

            Chain.Tron -> {
                val helper = TronHelper(
                    coinType = CoinType.TRON,
                    vaultHexPublicKey = vaultHexPublicKey,
                    vaultHexChainCode = vaultHexChainCode
                )
                return helper.getSignedTransaction(
                    keysignPayload = keysignPayload,
                    signatures = signatures
                )
            }

            else -> {
                throw Exception("Unsupported chain")
            }

        }
    }
}