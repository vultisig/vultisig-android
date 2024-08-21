package com.vultisig.wallet.chains

import com.google.protobuf.ByteString
import com.vultisig.wallet.common.Numeric
import com.vultisig.wallet.common.toByteString
import com.vultisig.wallet.common.toKeccak256
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.SignedTransactionResult
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import com.vultisig.wallet.tss.getSignatureWithRecoveryID
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Ethereum
import java.math.BigInteger

@OptIn(ExperimentalStdlibApi::class)
internal class EvmHelper(
    private val coinType: CoinType,
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    companion object{
        const val DefaultEthSwapGasUnit: Long = 600000L
    }

    fun getCoin(): Coin? {
        val ticker = when (coinType) {
            CoinType.ETHEREUM -> "ETH"
            CoinType.CRONOSCHAIN -> "CRO"
            CoinType.POLYGON -> "MATIC"
            CoinType.AVALANCHECCHAIN -> "AVAX"
            CoinType.SMARTCHAIN -> "BNB"
            else -> throw Exception("Unsupported coin ${coinType.name}")
        }
        val derivedPublicKey = PublicKeyHelper.getDerivedPublicKey(
            vaultHexPublicKey,
            vaultHexChainCode,
            coinType.derivationPath()
        )
        val publicKey = PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val address = coinType.deriveAddressFromPublicKey(publicKey)
        return Coins.getCoin(ticker, address, derivedPublicKey, coinType)
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
                        data = it.toByteString()
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
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            throw Exception(preSigningOutput.errorMessage)
        }
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
        return SignedTransactionResult(
            rawTransaction = Numeric.toHexStringNoPrefix(output.encoded.toByteArray()),
            transactionHash = output.encoded.toByteArray().toKeccak256()
        )
    }

}