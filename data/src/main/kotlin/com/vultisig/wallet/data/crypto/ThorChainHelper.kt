package com.vultisig.wallet.data.crypto

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.chains.helpers.PublicKeyHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.CosmoSignature
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.getNotNativeTicker
import com.vultisig.wallet.data.models.isSecuredAsset
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.ticker
import com.vultisig.wallet.data.models.transactionHash
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.wallet.Swaps
import kotlinx.serialization.json.Json
import tss.KeysignResponse
import vultisig.keysign.v1.TransactionType
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Cosmos
import wallet.core.jni.proto.Cosmos.Amount
import java.math.BigInteger


@OptIn(ExperimentalStdlibApi::class)
class ThorChainHelper(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
    private val ticker: String,
    private val hrp: String?,
    private val networkId: String,
    private val gasUnit: Long,
) {

    companion object {
        const val THOR_CHAIN_GAS_UNIT: Long = 20000000
        var THORCHAIN_NETWORK_ID: String = "thorchain-1"

        const val MAYA_CHAIN_GAS_UNIT: Long = 2000000000
        val SECURE_ASSETS_TICKERS = listOf(
            "BTC",
            "ETH",
            "BCH",
            "LTC",
            "DOGE",
            "AVAX",
            "BNB"
        )

        fun maya(
            vaultHexPublicKey: String,
            vaultHexChainCode: String,
        ) = ThorChainHelper(
            vaultHexPublicKey = vaultHexPublicKey,
            vaultHexChainCode = vaultHexChainCode,
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
            ticker = "RUNE",
            hrp = null,
            networkId = THORCHAIN_NETWORK_ID,
            gasUnit = THOR_CHAIN_GAS_UNIT,
        )


        private val coinType: CoinType = CoinType.THORCHAIN
    }

    private fun getTicker(coin: Coin): String {
        return if (coin.isNativeToken) {
            ticker
        } else {
            coin.getNotNativeTicker()
        }
    }

    private fun getPreSignInputData(keysignPayload: KeysignPayload): ByteArray {
        val tokenAddress = keysignPayload.coin.address
        val fromAddress = if (!hrp.isNullOrEmpty()) {
            AnyAddress(tokenAddress, coinType, hrp)
        } else {
            AnyAddress(tokenAddress, coinType)
        }.data()

        val isDeposit: Boolean
        val accountNumber: BigInteger
        val sequence: BigInteger
        val transactionType: TransactionType

        when (val specific = keysignPayload.blockChainSpecific) {
            is BlockChainSpecific.THORChain -> {
                isDeposit = specific.isDeposit
                accountNumber = specific.accountNumber
                sequence = specific.sequence
                transactionType = specific.transactionType
            }

            is BlockChainSpecific.MayaChain -> {
                isDeposit = specific.isDeposit
                accountNumber = specific.accountNumber
                sequence = specific.sequence
                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED
            }

            else -> error("Invalid blockChainSpecific $specific for ThorChainHelper")
        }

        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)

        val memo = keysignPayload.memo

        val message = if (isDeposit) {
            if (transactionType.genericWasmMessage()) {
                val message = Cosmos.Message.newBuilder().apply {
                    wasmExecuteContractGeneric =
                        buildThorchainWasmGenericMessage(keysignPayload, transactionType)
                }.build()

                val fee = Cosmos.Fee.newBuilder().apply {
                    gas = 20_000_000L
                }.build()

                val input = Cosmos.SigningInput.newBuilder().apply {
                    this.signingMode = Cosmos.SigningMode.Protobuf
                    this.accountNumber = accountNumber.toLong()
                    this.chainId = networkId
                    keysignPayload.memo?.let { this.memo = it }
                    this.sequence = sequence.toLong()
                    this.addMessages(message)
                    this.fee = fee
                    this.publicKey = ByteString.copyFrom(publicKey.data())
                    this.mode = Cosmos.BroadcastMode.SYNC
                }.build()

                return input.toByteArray()
            }
            buildThorchainDepositMessage(keysignPayload, fromAddress, memo)
        } else {
            if (transactionType == TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT) {
                val message = Cosmos.Message.newBuilder().apply {
                    wasmExecuteContractGeneric =
                        buildThorchainWasmGenericMessage(keysignPayload, transactionType)
                }.build()

                val fee = Cosmos.Fee.newBuilder().apply {
                    gas = 20_000_000L
                }.build()

                val input = Cosmos.SigningInput.newBuilder().apply {
                    this.signingMode = Cosmos.SigningMode.Protobuf
                    this.accountNumber = accountNumber.toLong()
                    this.chainId = networkId
                    keysignPayload.memo?.let { this.memo = it }
                    this.sequence = sequence.toLong()
                    this.addMessages(message)
                    this.fee = fee
                    this.publicKey = ByteString.copyFrom(publicKey.data())
                    this.mode = Cosmos.BroadcastMode.SYNC
                }.build()

                return input.toByteArray()
            } else {
                buildThorchainSendMessage(keysignPayload, fromAddress)
            }
        }

        val input = Cosmos.SigningInput.newBuilder().apply {
            this.publicKey = ByteString.copyFrom(publicKey.data())
            this.signingMode = Cosmos.SigningMode.Protobuf
            this.chainId = networkId
            this.accountNumber = accountNumber.toLong()
            this.sequence = sequence.toLong()
            this.mode = Cosmos.BroadcastMode.SYNC
            keysignPayload.memo?.let { this.memo = it }
            this.addAllMessages(listOf(message))
            this.fee = Cosmos.Fee.newBuilder().apply {
                this.gas = gasUnit
            }.build()
        }.build()
        return input.toByteArray()
    }

    private fun buildThorchainWasmGenericMessage(
        keysignPayload: KeysignPayload,
        transactionType: TransactionType,
    ): Cosmos.Message.WasmExecuteContractGeneric? {
        val fromAddr = try {
            AnyAddress(keysignPayload.coin.address, CoinType.THORCHAIN)
        } catch (e: Exception) {
            throw Exception("${keysignPayload.coin.address} is invalid")
        }

        val wasmGenericMessage =
            Cosmos.Message.WasmExecuteContractGeneric.newBuilder().apply {
                senderAddress = fromAddr.description()
                contractAddress = keysignPayload.toAddress
                when (transactionType) {
                    TransactionType.TRANSACTION_TYPE_THOR_MERGE -> {
                        val memo = keysignPayload.memo?.lowercase()
                            ?: throw IllegalArgumentException("Missing memo for ${transactionType.name}")

                        executeMsg = """{ "deposit": {} }"""
                        addCoins(
                            Cosmos.Amount.newBuilder().apply {
                                denom = memo.removePrefix("merge:")
                                amount = keysignPayload.toAmount.toString()
                            }.build()
                        )
                    }

                    TransactionType.TRANSACTION_TYPE_THOR_UNMERGE -> {
                        val memo = keysignPayload.memo?.lowercase()
                            ?: throw IllegalArgumentException("Missing memo for ${transactionType.name}")

                        val sharesAmount = memo
                            .takeIf { it.startsWith("unmerge:") }
                            ?.split(":")
                            ?.getOrNull(2)
                            ?: error("Invalid unmerge memo format ${keysignPayload.memo}")
                        executeMsg = """{ "withdraw": { "share_amount": "$sharesAmount" } }"""
                    }

                    TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT -> {
                        requireNotNull(keysignPayload.wasmExecuteContractPayload) {
                            "Invalid empty WasmExecuteContractPayload"
                        }
                        val contractPayload = keysignPayload.wasmExecuteContractPayload

                        val coins = contractPayload.coins.filterNotNull().map { coin ->
                            Amount.newBuilder().apply {
                                denom = coin.denom.lowercase()
                                amount = coin.amount
                            }.build()
                        }

                        executeMsg = contractPayload.executeMsg
                        senderAddress = contractPayload.senderAddress
                        contractAddress = contractPayload.contractAddress
                        addAllCoins(coins)
                    }

                    else -> error("Unsupported type ${transactionType.name}")
                }
            }.build()
        return wasmGenericMessage
    }

    private fun buildThorchainDepositMessage(
        keysignPayload: KeysignPayload,
        fromAddress: ByteArray?,
        memo: String?
    ): Cosmos.Message? {
        val symbol = getTicker(keysignPayload.coin)
        val assetTicker = getTicker(keysignPayload.coin)
        val isSecured = keysignPayload.coin.isSecuredAsset()
        val chainName = if (isSecured) {
            keysignPayload.coin.ticker
        } else
            keysignPayload.coin.getChainName()

        val coin = Cosmos.THORChainCoin.newBuilder().setDecimals(0)
            .setAsset(
                Cosmos.THORChainAsset.newBuilder().setSecured(true).setChain(chainName).setTicker(assetTicker).setSymbol(symbol).setSynth(false)
                    .build()
            )
            .let {
                if (keysignPayload.toAmount > BigInteger.ZERO) {
                    it.setAmount(keysignPayload.toAmount.toString())
                        .setDecimals(keysignPayload.coin.decimal.toLong())
                } else it
            }

        return Cosmos.Message.newBuilder().apply {
            thorchainDepositMessage = Cosmos.Message.THORChainDeposit.newBuilder().apply {
                this.signer = ByteString.copyFrom(fromAddress)
                this.memo = memo
                this.addCoins(coin)
            }.build()
        }.build()
    }

    private fun buildThorchainSendMessage(
        keysignPayload: KeysignPayload,
        fromAddress: ByteArray?
    ): Cosmos.Message {
        val toAddress = if (!hrp.isNullOrEmpty()) {
            AnyAddress(keysignPayload.toAddress, coinType, hrp)
        } else {
            AnyAddress(keysignPayload.toAddress, coinType)
        }.data()

        val sendAmount = Cosmos.Amount.newBuilder().apply {
            this.denom = if (keysignPayload.coin.isNativeToken)
                keysignPayload.coin.ticker.lowercase()
            else keysignPayload.coin.contractAddress
            this.amount = keysignPayload.toAmount.toString()
        }.build()

        return Cosmos.Message.newBuilder().apply {
            thorchainSendMessage = Cosmos.Message.THORChainSend.newBuilder().apply {
                this.fromAddress = ByteString.copyFrom(fromAddress)
                this.toAddress = ByteString.copyFrom(toAddress)
                this.addAllAmounts(listOf(sendAmount))
            }.build()
        }.build()
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
            .checkError()
        val cosmosSig = Json.decodeFromString<CosmoSignature>(output.serialized)
        return SignedTransactionResult(
            output.serialized,
            cosmosSig.transactionHash(),
        )
    }

}

fun Coin.getChainName(): String {
    return when (this.chain) {
        Chain.ThorChain -> {
            "THOR"
        }
        Chain.MayaChain -> {
            "MAYA"
        }
        Chain.BscChain -> {
            "BSC"
        }
        else -> {
            this.chain.ticker().uppercase()
        }
    }
}

private fun TransactionType.genericWasmMessage(): Boolean =
    this.mergeOrUnMerge() || this == TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT

private fun TransactionType.mergeOrUnMerge(): Boolean =
    this == TransactionType.TRANSACTION_TYPE_THOR_MERGE ||
            this == TransactionType.TRANSACTION_TYPE_THOR_UNMERGE

