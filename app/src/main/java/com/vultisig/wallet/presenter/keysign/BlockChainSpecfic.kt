package com.vultisig.wallet.presenter.keysign

import android.os.Parcelable
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.parcelize.Parcelize
import java.lang.reflect.Type
import java.math.BigInteger

@Parcelize
sealed class BlockChainSpecific : Parcelable {
    data class UTXO(val byteFee: BigInteger) : BlockChainSpecific()
    data class Ethereum(
        val maxFeePerGasWei: BigInteger,
        val priorityFeeWei: BigInteger,
        val nonce: Long,
        val gasLimit: BigInteger,
    ) : BlockChainSpecific()

    data class THORChain(val accountNumber: BigInteger, val sequence: BigInteger) :
        BlockChainSpecific()

    data class Cosmos(
        val accountNumber: BigInteger,
        val sequence: BigInteger,
        val gas: BigInteger,
    ) :
        BlockChainSpecific()

    data class Solana(val recentBlockHash: String, val priorityFee: BigInteger) :
        BlockChainSpecific()

    data class Sui(val referenceGasPrice: BigInteger, val coins: List<Map<String, String>>) :
        BlockChainSpecific()

    data class Polkadot(
        val recentBlockHash: String,
        val nonce: BigInteger,
        val currentBlockNumber: BigInteger,
        val specVersion: UInt,
        val transactionVersion: UInt,
        val genesisHash: String,
    ) : BlockChainSpecific()
}

class BlockChainSpecificSerializer : JsonSerializer<BlockChainSpecific> {
    override fun serialize(
        src: BlockChainSpecific,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement {
        val jsonObject = JsonObject()
        when (src) {
            is BlockChainSpecific.UTXO -> {
                val utxoJSON = JsonObject()
                utxoJSON.addProperty("byteFee", src.byteFee)
                jsonObject.add("UTXO", utxoJSON)
            }

            is BlockChainSpecific.Ethereum -> {
                val ethereumJSON = JsonObject()
                ethereumJSON.addProperty("maxFeePerGasWei", src.maxFeePerGasWei)
                ethereumJSON.addProperty("priorityFeeWei", src.priorityFeeWei)
                ethereumJSON.addProperty("nonce", src.nonce)
                ethereumJSON.addProperty("gasLimit", src.gasLimit)
                jsonObject.add("Ethereum", ethereumJSON)
            }

            is BlockChainSpecific.THORChain -> {
                val thorChainJSON = JsonObject()
                thorChainJSON.addProperty("accountNumber", src.accountNumber)
                thorChainJSON.addProperty("sequence", src.sequence)
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
                solanaJSON.addProperty("priorityFee", src.priorityFee)
                jsonObject.add("Solana", solanaJSON)
            }

            is BlockChainSpecific.Sui -> {
                val suiJSON = JsonObject()
                suiJSON.addProperty("referenceGasPrice", src.referenceGasPrice)
                suiJSON.add("coins", context?.serialize(src.coins))
                jsonObject.add("Sui", suiJSON)
            }

            is BlockChainSpecific.Polkadot -> {
                val polkadotJSON = JsonObject()
                polkadotJSON.addProperty("recentBlockHash", src.recentBlockHash)
                polkadotJSON.addProperty("nonce", src.nonce)
                polkadotJSON.addProperty("currentBlockNumber", src.currentBlockNumber)
                polkadotJSON.addProperty("specVersion", src.specVersion.toLong())
                polkadotJSON.addProperty("transactionVersion", src.transactionVersion.toLong())
                polkadotJSON.addProperty("genesisHash", src.genesisHash)
                jsonObject.add("Polkadot", polkadotJSON)
            }
        }
        return jsonObject
    }

}

class BlockChainSpecificDeserializer : JsonDeserializer<BlockChainSpecific> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): BlockChainSpecific {
        val jsonObject = json.asJsonObject

        return when {
            jsonObject.has("UTXO") -> context.deserialize<BlockChainSpecific.UTXO>(
                jsonObject.get("UTXO"),
                BlockChainSpecific.UTXO::class.java
            )

            jsonObject.has("Ethereum") -> context.deserialize<BlockChainSpecific.Ethereum>(
                jsonObject.get("Ethereum"),
                BlockChainSpecific.Ethereum::class.java
            )

            jsonObject.has("THORChain") -> context.deserialize<BlockChainSpecific.THORChain>(
                jsonObject.get("THORChain"),
                BlockChainSpecific.THORChain::class.java
            )

            jsonObject.has("Cosmos") -> context.deserialize<BlockChainSpecific.Cosmos>(
                jsonObject.get("Cosmos"),
                BlockChainSpecific.Cosmos::class.java
            )

            jsonObject.has("Solana") -> context.deserialize<BlockChainSpecific.Solana>(
                jsonObject.get("Solana"),
                BlockChainSpecific.Solana::class.java
            )

            jsonObject.has("Sui") -> context.deserialize<BlockChainSpecific.Sui>(
                jsonObject.get("Sui"),
                BlockChainSpecific.Sui::class.java
            )

            jsonObject.has("Polkadot") -> context.deserialize<BlockChainSpecific.Polkadot>(
                jsonObject.get("Polkadot"),
                BlockChainSpecific.Polkadot::class.java
            )

            else -> throw JsonParseException("Not a valid BlockChainSpecific type")
        }
    }
}