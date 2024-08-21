package com.vultisig.wallet.chains

import com.google.gson.Gson
import com.google.protobuf.ByteString
import com.vultisig.wallet.common.Numeric
import com.vultisig.wallet.data.wallet.Swaps
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.CosmoSignature
import com.vultisig.wallet.models.SignedTransactionResult
import com.vultisig.wallet.models.transactionHash
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import com.vultisig.wallet.tss.getSignatureWithRecoveryID
import tss.KeysignResponse
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Cosmos
import java.math.BigInteger

@OptIn(ExperimentalStdlibApi::class)
internal class THORCHainHelper(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    companion object{
        private val coinType: CoinType = CoinType.THORCHAIN
        const val THOR_CHAIN_GAS_UNIT: Long = 20000000
    }


    fun getCoin(): Coin? {
        val derivedPublicKey = PublicKeyHelper.getDerivedPublicKey(
            vaultHexPublicKey,
            vaultHexChainCode,
            coinType.derivationPath()
        )
        val publicKey = PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val address = coinType.deriveAddressFromPublicKey(publicKey)
        return Coins.getCoin("RUNE", address, derivedPublicKey, coinType)
    }

    fun getSwapPreSignedInputData(
        keysignPayload: KeysignPayload,
        input: Cosmos.SigningInput.Builder,
    ): ByteArray {
        val thorchainData = keysignPayload.blockChainSpecific as? BlockChainSpecific.THORChain
            ?: throw Exception("Invalid blockChainSpecific")
        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val inputData = input.apply {
            this.publicKey = ByteString.copyFrom(publicKey.data())
            this.accountNumber = thorchainData.accountNumber.toLong()
            this.sequence = thorchainData.sequence.toLong()
            this.mode = Cosmos.BroadcastMode.SYNC
            this.fee = Cosmos.Fee.newBuilder().apply {
                this.gas = THOR_CHAIN_GAS_UNIT
            }.build()
        }.build()
        return inputData.toByteArray()
    }

    private fun getPreSignInputData(keysignPayload: KeysignPayload): ByteArray {
        if (keysignPayload.coin.ticker != "RUNE") {
            throw Exception("Coin is not RUNE")
        }
        val fromAddress = AnyAddress(keysignPayload.coin.address, coinType).data()
        val thorchainData = keysignPayload.blockChainSpecific as? BlockChainSpecific.THORChain
            ?: throw Exception("Invalid blockChainSpecific")
        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)


        val memo = keysignPayload.memo
        val isDeposit = !memo.isNullOrEmpty() && MayaChainHelper.DEPOSIT_PREFIXES.any { memo.startsWith(it) }

        val msgSend = if (isDeposit) {
            val coin = Cosmos.THORChainCoin.newBuilder()
                .setAsset(
                    Cosmos.THORChainAsset.newBuilder()
                        .setChain("THOR")
                        .setSymbol("RUNE")
                        .setTicker("RUNE")
                        .setSynth(false)
                        .build()
                )
                .let {
                    if (keysignPayload.toAmount > BigInteger.ZERO) {
                        it.setAmount(keysignPayload.toAmount.toString())
                            .setDecimals(keysignPayload.coin.decimal.toLong())
                    } else it
                }

            Cosmos.Message.newBuilder().apply {
                thorchainDepositMessage = Cosmos.Message.THORChainDeposit.newBuilder().apply {
                    this.signer = ByteString.copyFrom(fromAddress)
                    this.memo = memo
                    this.addCoins(coin)
                }.build()
            }.build()
        } else {
            val toAddress = AnyAddress(keysignPayload.toAddress, coinType).data()

            val sendAmount = Cosmos.Amount.newBuilder().apply {
                this.denom = keysignPayload.coin.ticker.lowercase()
                this.amount = keysignPayload.toAmount.toString()
            }.build()

            Cosmos.Message.newBuilder().apply {
                thorchainSendMessage = Cosmos.Message.THORChainSend.newBuilder().apply {
                    this.fromAddress = ByteString.copyFrom(fromAddress)
                    this.toAddress = ByteString.copyFrom(toAddress)
                    this.addAllAmounts(listOf(sendAmount))
                }.build()
            }.build()
        }

        val input = Cosmos.SigningInput.newBuilder().apply {
            this.publicKey = ByteString.copyFrom(publicKey.data())
            this.signingMode = Cosmos.SigningMode.Protobuf
            this.chainId = coinType.chainId()
            this.accountNumber = thorchainData.accountNumber.toLong()
            this.sequence = thorchainData.sequence.toLong()
            this.mode = Cosmos.BroadcastMode.SYNC
            keysignPayload.memo?.let {
                this.memo = it
            }

            this.addAllMessages(listOf(msgSend))

            this.fee = Cosmos.Fee.newBuilder().apply {
                this.gas = THOR_CHAIN_GAS_UNIT
            }.build()
        }.build()
        return input.toByteArray()
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val inputData = getPreSignInputData(keysignPayload)
        return Swaps.getPreSignedImageHash(inputData, coinType, keysignPayload.coin.chain)
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val inputData = getPreSignInputData(keysignPayload)
        return getSignedTransaction(inputData, signatures)
    }

    fun getSignedTransaction(
        inputData: ByteArray,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val thorchainPublicKey = PublicKeyHelper.getDerivedPublicKey(
            hexPublicKey = vaultHexPublicKey,
            hexChainCode = vaultHexChainCode,
            coinType.derivationPath()
        )
        val publicKey = PublicKey(thorchainPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
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
        allPublicKeys.add(publicKey.data())
        val compileWithSignature = TransactionCompiler.compileWithSignatures(
            coinType,
            inputData,
            allSignatures,
            allPublicKeys
        )
        val output = Cosmos.SigningOutput.parseFrom(compileWithSignature)
        val cosmosSig = Gson().fromJson(output.serialized, CosmoSignature::class.java)
        return SignedTransactionResult(
            output.serialized,
            cosmosSig.transactionHash(),
        )
    }
}