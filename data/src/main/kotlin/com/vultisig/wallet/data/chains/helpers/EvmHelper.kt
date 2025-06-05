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
    companion object{
        const val DEFAULT_ETH_SWAP_GAS_UNIT: Long = 600000L
    }

    fun getSwapPreSignedInputData(keysignPayload: KeysignPayload,nonceIncrement: BigInteger = BigInteger.ZERO): ByteArray {
        val thorChainSwapPayload = keysignPayload.swapPayload as? SwapPayload.ThorChain
            ?: throw Exception("Invalid swap payload for EVM chain")
        if (thorChainSwapPayload.data.vaultAddress.isEmpty()){
            throw Exception("Vault address is required for THORChain swap")
        }
        if (keysignPayload.memo.isNullOrEmpty()){
            throw Exception("Memo is required for THORChain swap")
        }
        val ethSpecifc = requireEthereumSpec(keysignPayload.blockChainSpecific)
        val input = Ethereum.SigningInput.newBuilder().apply {
            chainId = ByteString.copyFrom(BigInteger(coinType.chainId()).toByteArray())
            nonce = ByteString.copyFrom((ethSpecifc.nonce + nonceIncrement).toByteArray())
            gasLimit = ByteString.copyFrom(ethSpecifc.gasLimit.toByteArray())
            maxFeePerGas = ByteString.copyFrom(ethSpecifc.maxFeePerGasWei.toByteArray())
            maxInclusionFeePerGas = ByteString.copyFrom(ethSpecifc.priorityFeeWei.toByteArray())
            txMode = Ethereum.TransactionMode.Enveloped
        }

        // Native token , send directly to asgard vault
        if(thorChainSwapPayload.data.fromCoin.isNativeToken){
            input.toAddress = thorChainSwapPayload.data.vaultAddress
            input.transaction = Ethereum.Transaction.newBuilder().apply {
                transfer = Ethereum.Transaction.Transfer.newBuilder().apply {
                    amount = ByteString.copyFrom(thorChainSwapPayload.data.fromAmount.toByteArray())
                    data = keysignPayload.memo.toByteStringOrHex()
                }.build()
            }.build()
        } else {
            // ERC20 token
            require(thorChainSwapPayload.data.routerAddress.isNullOrEmpty()) {
                "Router address is required for ERC20 token swap"
            }
            require(thorChainSwapPayload.data.fromCoin.contractAddress.isNotEmpty()) {
                "Contract address is required for ERC20 token swap"
            }
            require(thorChainSwapPayload.data.vaultAddress.isNotEmpty()) {
                "Vault address is required for ERC20 token swap"
            }
            val vaultAddress = AnyAddress(thorChainSwapPayload.data.vaultAddress, coinType)
            val contractAddr = AnyAddress(
                thorChainSwapPayload.data.fromCoin.contractAddress,
                coinType
            )
            input.toAddress = thorChainSwapPayload.data.routerAddress
            val f = EthereumAbiFunction("depositWithExpiry")
            f.addParamAddress(vaultAddress.data(),false)
            f.addParamAddress(contractAddr.data(),false)
            f.addParamUInt256(thorChainSwapPayload.data.fromAmount.toByteArray(),false)
            f.addParamString(keysignPayload.memo, false)
            f.addParamUInt256(BigInteger(thorChainSwapPayload.data.expirationTime.toString()).toByteArray(),false)

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
        val ethSpecifc = requireEthereumSpec(keysignPayload.blockChainSpecific)

        return signingInput.toBuilder().apply {
            chainId = ByteString.copyFrom(BigInteger(coinType.chainId()).toByteArray())
            nonce = ByteString.copyFrom((ethSpecifc.nonce + nonceIncrement).toByteArray())
            gasLimit = ByteString.copyFrom(ethSpecifc.gasLimit.toByteArray())
            maxFeePerGas = ByteString.copyFrom(ethSpecifc.maxFeePerGasWei.toByteArray())
            maxInclusionFeePerGas = ByteString.copyFrom(ethSpecifc.priorityFeeWei.toByteArray())
            txMode = Ethereum.TransactionMode.Enveloped
        }.build().toByteArray()
    }

    fun getPreSignedInputData(
        gas: BigInteger,
        gasPrice: BigInteger,
        signingInput: Ethereum.SigningInput.Builder,
        keysignPayload: KeysignPayload,
        nonceIncrement: BigInteger = BigInteger.ZERO,
    ): ByteArray {
        val ethSpecifc = requireEthereumSpec(keysignPayload.blockChainSpecific)

        return signingInput.apply {
            chainId = ByteString.copyFrom(BigInteger(coinType.chainId()).toByteArray())
            nonce = ByteString.copyFrom((ethSpecifc.nonce + nonceIncrement).toByteArray())

            gasLimit = ByteString.copyFrom(gas.toByteArray())
            setGasPrice(ByteString.copyFrom(gasPrice.toByteArray()))

            txMode = Ethereum.TransactionMode.Legacy
        }.build().toByteArray()
    }

    private fun requireEthereumSpec(spec: BlockChainSpecific): BlockChainSpecific.Ethereum =
        spec as? BlockChainSpecific.Ethereum
            ?: error("BlockChainSpecific is not Ethereum for EVM chain")

    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val ethSpecifc = keysignPayload.blockChainSpecific as? BlockChainSpecific.Ethereum
            ?: throw Exception("Invalid blockChainSpecific")
        val input = Ethereum.SigningInput.newBuilder()
        input.apply {
            chainId = ByteString.copyFrom(BigInteger(coinType.chainId()).toByteArray())
            nonce = ByteString.copyFrom(ethSpecifc.nonce.toByteArray())
            gasLimit = ByteString.copyFrom(ethSpecifc.gasLimit.toByteArray())
            maxFeePerGas = ByteString.copyFrom(ethSpecifc.maxFeePerGasWei.toByteArray())
            maxInclusionFeePerGas = ByteString.copyFrom(ethSpecifc.priorityFeeWei.toByteArray())
            toAddress = keysignPayload.toAddress
            txMode = Ethereum.TransactionMode.Enveloped
            transaction = Ethereum.Transaction.newBuilder().apply {
                transfer = Ethereum.Transaction.Transfer.newBuilder().apply {
                    amount = ByteString.copyFrom(keysignPayload.toAmount.toByteArray())
                    keysignPayload.memo?.let {
                        data = it.toByteStringOrHex()
                    }
                }.build()
            }.build()
        }
        return input.build().toByteArray()
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, result)
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
            coinType.derivationPath()
        )
        val publicKey = PublicKey(ethPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
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
            coinType,
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