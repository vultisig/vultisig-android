package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.common.toByteStringOrHex
import com.vultisig.wallet.data.common.toKeccak256
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.compatibleType
import com.vultisig.wallet.data.utils.compatibleDerivationPath
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.EthereumAbi
import wallet.core.jni.EthereumAbiFunction
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Ethereum
import java.math.BigInteger

@OptIn(ExperimentalStdlibApi::class)
class EvmHelper(
    private val coinType: CoinType,
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    companion object {
        const val DEFAULT_ETH_SWAP_GAS_UNIT: Long = 600000L
    }

    fun getSwapPreSignedInputData(
        keysignPayload: KeysignPayload,
        nonceIncrement: BigInteger = BigInteger.ZERO
    ): ByteArray {
        // Support both THORChain and MayaChain swaps
        val swapPayload = when (val payload = keysignPayload.swapPayload) {
            is SwapPayload.ThorChain -> payload.data
            is SwapPayload.MayaChain -> payload.data
            else -> throw Exception("Invalid swap payload for EVM chain")
        }
        
        if (swapPayload.vaultAddress.isEmpty()) {
            throw Exception("Vault address is required for swap")
        }
        require(!keysignPayload.memo.isNullOrEmpty()) {
            "Memo is required for swap"
        }
        val ethSpecifc = EthereumGasHelper.requireEthereumSpec(keysignPayload.blockChainSpecific)
        val input = EthereumGasHelper.setGasParameters(
            ethSpecifc.gasLimit,
            ethSpecifc.maxFeePerGasWei,
            Ethereum.SigningInput.newBuilder(),
            keysignPayload,
            nonceIncrement,
            coinType
        )

        // Native token, send directly to vault/inbound address
        if (swapPayload.fromCoin.isNativeToken) {
            input.toAddress = swapPayload.vaultAddress
            input.transaction = Ethereum.Transaction.newBuilder().apply {
                transfer = Ethereum.Transaction.Transfer.newBuilder().apply {
                    amount = ByteString.copyFrom(swapPayload.fromAmount.toByteArray())
                    data = keysignPayload.memo.toByteStringOrHex()
                }.build()
            }.build()
        } else {
            // ERC20 token - requires router interaction
            require(!swapPayload.routerAddress.isNullOrEmpty()) {
                "Router address is required for ERC20 token swap"
            }
            require(swapPayload.fromCoin.contractAddress.isNotEmpty()) {
                "Contract address is required for ERC20 token swap"
            }
            require(swapPayload.vaultAddress.isNotEmpty()) {
                "Vault address is required for ERC20 token swap"
            }
            val vaultAddress = AnyAddress(swapPayload.vaultAddress, coinType)
            val contractAddr = AnyAddress(
                swapPayload.fromCoin.contractAddress,
                coinType
            )
            input.toAddress = swapPayload.routerAddress
            val f = EthereumAbiFunction("depositWithExpiry")
            f.addParamAddress(vaultAddress.data(), false)
            f.addParamAddress(contractAddr.data(), false)
            f.addParamUInt256(swapPayload.fromAmount.toByteArray(), false)
            f.addParamString(keysignPayload.memo, false)
            f.addParamUInt256(
                BigInteger(swapPayload.expirationTime.toString()).toByteArray(),
                false
            )

            input.transaction = Ethereum.Transaction.newBuilder().apply {
                contractGeneric = Ethereum.Transaction.ContractGeneric.newBuilder().apply {
                    data = ByteString.copyFrom(
                        EthereumAbi.encode(f)
                    )
                    amount = ByteString.copyFrom(
                        BigInteger.ZERO.toByteArray()
                    )
                }.build()
            }.build()
        }
        return input.build().toByteArray()
    }

    fun getPreSignedInputData(
        signingInput: Ethereum.SigningInput,
        keysignPayload: KeysignPayload,
        nonceIncrement: BigInteger = BigInteger.ZERO,
    ): ByteArray {
        val ethSpecifc = EthereumGasHelper.requireEthereumSpec(keysignPayload.blockChainSpecific)
        return EthereumGasHelper.setGasParameters(
            gas = ethSpecifc.gasLimit,
            gasPrice = ethSpecifc.maxFeePerGasWei,
            signingInput = signingInput.toBuilder(),
            keysignPayload = keysignPayload,
            nonceIncrement = nonceIncrement,
            coinType = coinType
        ).build().toByteArray()
    }


    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val ethSpecifc = keysignPayload.blockChainSpecific as? BlockChainSpecific.Ethereum
            ?: throw Exception("Invalid blockChainSpecific")
        val input = Ethereum.SigningInput.newBuilder()
        input.apply {
            toAddress = keysignPayload.toAddress
            transaction = Ethereum.Transaction.newBuilder().apply {
                transfer = Ethereum.Transaction.Transfer.newBuilder().apply {
                    amount = ByteString.copyFrom(keysignPayload.toAmount.toByteArray())
                    keysignPayload.memo?.let {
                        data = it.toByteStringOrHex()
                    }
                }.build()
            }.build()
        }
        return EthereumGasHelper.setGasParameters(
            gas = ethSpecifc.gasLimit,
            gasPrice = ethSpecifc.maxFeePerGasWei,
            signingInput = input,
            keysignPayload = keysignPayload,
            nonceIncrement = BigInteger.ZERO,
            coinType = coinType
        ).build().toByteArray()
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)

        val hashes = TransactionCompiler.preImageHashes(
            coinType.compatibleType,
            result
        )
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val inputData = getPreSignedInputData(keysignPayload)
        return getSignedTransaction(inputData, signatures)
    }

    fun getSignedTransaction(
        inputData: ByteArray,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val ethPublicKey = PublicKeyHelper.getDerivedPublicKey(
            vaultHexPublicKey,
            vaultHexChainCode,
            coinType.compatibleDerivationPath()
        )
        val publicKey = PublicKey(ethPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val preHashes = TransactionCompiler.preImageHashes(
            coinType.compatibleType,
            inputData
        )
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(preHashes)
                .checkError()
        val allSignatures = DataVector()
        val allPublicKeys = DataVector()
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray())
        val signature = signatures[key]?.getSignatureWithRecoveryID()
            ?: throw Exception("Signature not found")
        if (!publicKey.verify(signature, preSigningOutput.dataHash.toByteArray())) {
            throw Exception("Signature verification failed")
        }
        allSignatures.add(signature)

        val compileWithSignature = TransactionCompiler.compileWithSignatures(
            coinType.compatibleType,
            inputData,
            allSignatures,
            allPublicKeys
        )
        val output = Ethereum.SigningOutput.parseFrom(compileWithSignature)
            .checkError()

        return SignedTransactionResult(
            rawTransaction = Numeric.toHexStringNoPrefix(output.encoded.toByteArray()),
            transactionHash = output.encoded.toByteArray().toKeccak256()
        )
    }
}