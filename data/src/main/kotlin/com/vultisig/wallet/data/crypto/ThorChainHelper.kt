package com.vultisig.wallet.data.crypto

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.chains.helpers.PublicKeyHelper
import com.vultisig.wallet.data.models.CosmoSignature
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.transactionHash
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.wallet.Swaps
import kotlinx.serialization.json.Json
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
class ThorChainHelper(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
    private val chainName: String,
    private val ticker: String,
    private val hrp: String?,
    private val networkId: String,
    private val gasUnit: Long,
) {

    companion object {
        const val THOR_CHAIN_GAS_UNIT: Long = 20000000
        var THORCHAIN_NETWORK_ID: String = "thorchain-1"

        const val MAYA_CHAIN_GAS_UNIT: Long = 2000000000

        fun maya(
            vaultHexPublicKey: String,
            vaultHexChainCode: String,
        ) = ThorChainHelper(
            vaultHexPublicKey = vaultHexPublicKey,
            vaultHexChainCode = vaultHexChainCode,
            chainName = "MAYA",
            ticker = "CACAO",
            hrp = "maya",
            networkId = "mayachain-mainnet-v1",
            gasUnit = MAYA_CHAIN_GAS_UNIT,
        )

        fun thor(
            vaultHexPublicKey: String,
            vaultHexChainCode: String,
        ) = ThorChainHelper(
            vaultHexPublicKey = vaultHexPublicKey,
            vaultHexChainCode = vaultHexChainCode,
            chainName = "THOR",
            ticker = "RUNE",
            hrp = null,
            networkId = THORCHAIN_NETWORK_ID,
            gasUnit = THOR_CHAIN_GAS_UNIT,
        )



        private val coinType: CoinType = CoinType.THORCHAIN
    }

    private fun getPreSignInputData(keysignPayload: KeysignPayload): ByteArray {
        val tokenAddress = keysignPayload.coin.address
        val fromAddress = if (hrp != null) {
            AnyAddress(tokenAddress, coinType, hrp)
        } else {
            AnyAddress(tokenAddress, coinType)
        }.data()

        val isDeposit: Boolean
        val accountNumber: BigInteger
        val sequence: BigInteger

        when (val specific = keysignPayload.blockChainSpecific) {
            is BlockChainSpecific.THORChain -> {
                isDeposit = specific.isDeposit
                accountNumber = specific.accountNumber
                sequence = specific.sequence
            }
            is BlockChainSpecific.MayaChain -> {
                isDeposit = specific.isDeposit
                accountNumber = specific.accountNumber
                sequence = specific.sequence
            }
            else -> error("Invalid blockChainSpecific $specific for ThorChainHelper")
        }

        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)

        val memo = keysignPayload.memo

        val msgSend = if (isDeposit) {
            val coin = Cosmos.THORChainCoin.newBuilder()
                .setAsset(
                    Cosmos.THORChainAsset.newBuilder()
                        .setChain(chainName)
                        .setSymbol(ticker)
                        .setTicker(ticker)
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
            val toAddress = if (hrp != null) {
                AnyAddress(keysignPayload.toAddress, coinType, hrp)
            } else {
                AnyAddress(keysignPayload.toAddress, coinType)
            }.data()

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
            this.chainId = networkId
            this.accountNumber = accountNumber.toLong()
            this.sequence = sequence.toLong()
            this.mode = Cosmos.BroadcastMode.SYNC
            keysignPayload.memo?.let {
                this.memo = it
            }

            this.addAllMessages(listOf(msgSend))

            this.fee = Cosmos.Fee.newBuilder().apply {
                this.gas = gasUnit
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
        allPublicKeys.add(publicKey.data())
        val compileWithSignature = TransactionCompiler.compileWithSignatures(
            coinType,
            inputData,
            allSignatures,
            allPublicKeys
        )
        val output = Cosmos.SigningOutput.parseFrom(compileWithSignature)
        val cosmosSig = Json.decodeFromString<CosmoSignature>(output.serialized)
        return SignedTransactionResult(
            output.serialized,
            cosmosSig.transactionHash(),
        )
    }

}