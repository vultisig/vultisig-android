package com.vultisig.wallet.presenter.keysign

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.common.toJson
import java.lang.reflect.Type
import java.math.BigInteger

internal sealed class BlockChainSpecific {
    data class UTXO(
        @SerializedName("byteFee")
        val byteFee: BigInteger,
        @SerializedName("sendMaxAmount")
        val sendMaxAmount: Boolean
    ) : BlockChainSpecific()

    data class Ethereum(
        @SerializedName("maxFeePerGasWei")
        val maxFeePerGasWei: BigInteger,
        @SerializedName("priorityFeeWei")
        val priorityFeeWei: BigInteger,
        @SerializedName("nonce")
        val nonce: BigInteger,
        @SerializedName("gasLimit")
        val gasLimit: BigInteger,
    ) : BlockChainSpecific()

    data class THORChain(
        @SerializedName("accountNumber")
        val accountNumber: BigInteger,
        @SerializedName("sequence")
        val sequence: BigInteger,
        @SerializedName("fee")
        val fee: BigInteger,
    ) : BlockChainSpecific()

    data class Cosmos(
        @SerializedName("accountNumber")
        val accountNumber: BigInteger,
        @SerializedName("sequence")
        val sequence: BigInteger,
        @SerializedName("gas")
        val gas: BigInteger,
    ) : BlockChainSpecific()

    data class Solana(
        @SerializedName("recentBlockHash")
        val recentBlockHash: String,
        @SerializedName("priorityFee")
        val priorityFee: BigInteger
    ) : BlockChainSpecific()

    data class Sui(
        @SerializedName("referenceGasPrice")
        val referenceGasPrice: BigInteger,
        @SerializedName("coins")
        val coins: List<Map<String, String>>
    ) : BlockChainSpecific()

    data class Polkadot(
        @SerializedName("recentBlockHash")
        val recentBlockHash: String,
        @SerializedName("nonce")
        val nonce: BigInteger,
        @SerializedName("currentBlockNumber")
        val currentBlockNumber: BigInteger,
        @SerializedName("specVersion")
        val specVersion: UInt,
        @SerializedName("transactionVersion")
        val transactionVersion: UInt,
        @SerializedName("genesisHash")
        val genesisHash: String,
    ) : BlockChainSpecific()
}

internal class BlockChainSpecificSerializer : JsonSerializer<BlockChainSpecific> {
    override fun serialize(
        src: BlockChainSpecific,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement {
        val jsonObject = JsonObject()
        when (src) {
            is BlockChainSpecific.UTXO -> {
                val utxoJSON = JsonObject()
                utxoJSON.add("byteFee", src.byteFee.toJson())
                utxoJSON.addProperty("sendMaxAmount", src.sendMaxAmount)
                jsonObject.add("UTXO", utxoJSON)
            }

            is BlockChainSpecific.Ethereum -> {
                val ethereumJSON = JsonObject()
                ethereumJSON.add("maxFeePerGasWei", src.maxFeePerGasWei.toJson())
                ethereumJSON.add("priorityFeeWei", src.priorityFeeWei.toJson())
                ethereumJSON.addProperty("nonce", src.nonce)
                ethereumJSON.add("gasLimit", src.gasLimit.toJson())
                jsonObject.add("Ethereum", ethereumJSON)
            }

            is BlockChainSpecific.THORChain -> {
                val thorChainJSON = JsonObject()
                thorChainJSON.addProperty("accountNumber", src.accountNumber)
                thorChainJSON.addProperty("sequence", src.sequence)
                thorChainJSON.addProperty("fee", src.fee)
                jsonObject.add("THORChain", thorChainJSON)
            }

            is BlockChainSpecific.Cosmos -> {
                val cosmosJSON = JsonObject()
                cosmosJSON.addProperty("accountNumber", src.accountNumber)
                cosmosJSON.addProperty("sequence", src.sequence)
                cosmosJSON.addProperty("gas", src.gas)
                jsonObject.add("Cosmos", cosmosJSON)
            }

            is BlockChainSpecific.Solana -> {
                val solanaJSON = JsonObject()
                solanaJSON.addProperty("recentBlockHash", src.recentBlockHash)
                solanaJSON.add("priorityFee", src.priorityFee.toJson())
                jsonObject.add("Solana", solanaJSON)
            }

            is BlockChainSpecific.Sui -> {
                val suiJSON = JsonObject()
                suiJSON.add("referenceGasPrice", src.referenceGasPrice.toJson())
                suiJSON.add("coins", context?.serialize(src.coins))
                jsonObject.add("Sui", suiJSON)
            }

            is BlockChainSpecific.Polkadot -> {
                val polkadotJSON = JsonObject()
                polkadotJSON.addProperty("recentBlockHash", src.recentBlockHash)
                polkadotJSON.addProperty("nonce", src.nonce)
                polkadotJSON.add("currentBlockNumber", src.currentBlockNumber.toJson())
                polkadotJSON.addProperty("specVersion", src.specVersion.toLong())
                polkadotJSON.addProperty("transactionVersion", src.transactionVersion.toLong())
                polkadotJSON.addProperty("genesisHash", src.genesisHash)
                jsonObject.add("Polkadot", polkadotJSON)
            }
        }
        return jsonObject
    }

}

internal class BlockChainSpecificDeserializer : JsonDeserializer<BlockChainSpecific> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): BlockChainSpecific {
        val jsonObject = json.asJsonObject

        when {
            jsonObject.has("UTXO") -> {
                val obj = jsonObject.get("UTXO").asJsonObject
                return BlockChainSpecific.UTXO(
                    byteFee = obj.get("byteFee").asJsonArray[1].asBigInteger,
                    sendMaxAmount = obj.get("sendMaxAmount").asBoolean
                )
            }

            jsonObject.has("Ethereum") -> {
                val obj = jsonObject.get("Ethereum").asJsonObject
                return BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = obj.get("maxFeePerGasWei").asJsonArray[1].asBigInteger,
                    priorityFeeWei = obj.get("priorityFeeWei").asJsonArray[1].asBigInteger,
                    nonce = obj.get("nonce").asBigInteger,
                    gasLimit = obj.get("gasLimit").asJsonArray[1].asBigInteger
                )
            }

            jsonObject.has("THORChain") -> {
                val obj = jsonObject.get("THORChain").asJsonObject
                return BlockChainSpecific.THORChain(
                    accountNumber = obj.get("accountNumber").asBigInteger,
                    sequence = obj.get("sequence").asBigInteger,
                    fee = obj.get("fee").asBigInteger
                )
            }

            jsonObject.has("Cosmos") -> {
                val obj = jsonObject.get("Cosmos").asJsonObject
                return BlockChainSpecific.Cosmos(
                    accountNumber = obj.get("accountNumber").asBigInteger,
                    sequence = obj.get("sequence").asBigInteger,
                    gas = obj.get("gas").asBigInteger
                )
            }

            jsonObject.has("Solana") -> {
                val obj = jsonObject.get("Solana").asJsonObject
                return BlockChainSpecific.Solana(
                    recentBlockHash = obj.get("recentBlockHash").asString,
                    priorityFee = obj.get("priorityFee").asJsonArray[1].asBigInteger
                )
            }

            jsonObject.has("Sui") -> {
                val obj = jsonObject.get("Sui").asJsonObject
                return BlockChainSpecific.Sui(
                    referenceGasPrice = obj.get("referenceGasPrice").asJsonArray[1].asBigInteger,
                    coins = context.deserialize(obj.get("coins"), List::class.java)
                )
            }

            jsonObject.has("Polkadot") -> {
                val obj = jsonObject.get("Polkadot").asJsonObject
                return BlockChainSpecific.Polkadot(
                    recentBlockHash = obj.get("recentBlockHash").asString,
                    nonce = obj.get("nonce").asBigInteger,
                    currentBlockNumber = obj.get("currentBlockNumber").asJsonArray[1].asBigInteger,
                    specVersion = obj.get("specVersion").asLong.toUInt(),
                    transactionVersion = obj.get("transactionVersion").asLong.toUInt(),
                    genesisHash = obj.get("genesisHash").asString
                )
            }

            else -> throw JsonParseException("Not a valid BlockChainSpecific type")
        }
    }
}