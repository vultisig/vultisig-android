package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.cosmosNativeDenom
import com.vultisig.wallet.data.usecases.CosmosMessage
import java.math.BigInteger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import vultisig.keysign.v1.SignAmino

/** A coin as the signed data names it: a bank denom, or a THORChain / Maya deposit asset. */
internal data class CosmosSignedCoin(val denom: String, val amount: BigInteger)

/** One amount leaving the vault, as the signed data states it. */
internal data class CosmosSignedTransferLeg(
    val recipient: String?,
    val coin: CosmosSignedCoin,
    val memo: String? = null,
)

/**
 * What a Cosmos `signDirect` / `signAmino` request moves out of the vault, read from the signed
 * messages rather than the payload's unsigned `toAddress` / `toAmount`. [hasUnsummarizedMessages]
 * is set when a message can't be read, or its effect isn't fully described by [transfers].
 */
internal data class CosmosSignDataReview(
    val transfers: List<CosmosSignedTransferLeg>,
    val hasUnsummarizedMessages: Boolean,
) {
    /** Every distinct recipient, in the order it first appears. */
    val recipients: List<String>
        get() = transfers.mapNotNull { it.recipient }.distinct()

    /** One total per denom, in the order each denom first appears. */
    val coinTotals: List<CosmosSignedCoin>
        get() =
            transfers
                .groupBy { it.coin.denom }
                .map { (denom, legs) ->
                    CosmosSignedCoin(
                        denom,
                        legs.fold(BigInteger.ZERO) { sum, leg -> sum + leg.coin.amount },
                    )
                }

    /** Every distinct transfer memo, in the order it first appears. */
    val memos: List<String>
        get() = transfers.mapNotNull { it.memo }.distinct()
}

/** Reads every message of a decoded `signDirect` body. An empty body is unsummarized. */
internal fun CosmosMessage.toSignDataReview(chain: Chain): CosmosSignDataReview =
    messages.map { readMessage(DIRECT_READERS, it.typeUrl, it.value, chain) }.toReview()

/**
 * Reads every message of a `signAmino` request. A `value` that isn't a JSON object is unsummarized.
 */
internal fun SignAmino.toSignDataReview(chain: Chain, json: Json): CosmosSignDataReview =
    msgs
        .map { msg ->
            if (msg == null) return@map UNSUMMARIZED
            val value =
                try {
                    json.parseToJsonElement(msg.value) as? JsonObject
                } catch (_: SerializationException) {
                    null
                } ?: return@map UNSUMMARIZED
            readMessage(AMINO_READERS, msg.type, value, chain)
        }
        .toReview()

private fun List<MessageReading>.toReview() =
    CosmosSignDataReview(
        transfers = flatMap { it.transfers },
        hasUnsummarizedMessages = isEmpty() || any { !it.isSummarized },
    )

private class MessageReading(
    val transfers: List<CosmosSignedTransferLeg>,
    val isSummarized: Boolean,
)

private val UNSUMMARIZED = MessageReading(emptyList(), isSummarized = false)
private val NO_TRANSFERS = MessageReading(emptyList(), isSummarized = true)

private typealias MessageReader = (value: JsonElement, chain: Chain) -> MessageReading

/** Thrown by the field readers for a value the signed data doesn't state the way it should. */
private class UnreadableMessage : Exception()

private fun readMessage(
    readers: Map<String, MessageReader>,
    type: String,
    value: JsonElement,
    chain: Chain,
): MessageReading {
    val read = readers[type] ?: return UNSUMMARIZED
    return try {
        read(value, chain)
    } catch (_: UnreadableMessage) {
        UNSUMMARIZED
    }
}

private fun JsonElement.obj(): JsonObject = this as? JsonObject ?: throw UnreadableMessage()

private fun JsonElement?.string(): String {
    val primitive = this as? JsonPrimitive ?: throw UnreadableMessage()
    if (!primitive.isString || primitive.content.isEmpty()) throw UnreadableMessage()
    return primitive.content
}

private fun JsonElement?.optionalString(): String? {
    if (this == null || this is kotlinx.serialization.json.JsonNull) return null
    val primitive = this as? JsonPrimitive ?: throw UnreadableMessage()
    if (!primitive.isString) throw UnreadableMessage()
    return primitive.content.takeIf { it.isNotEmpty() }
}

private fun JsonElement?.amount(): BigInteger {
    val primitive = this as? JsonPrimitive ?: throw UnreadableMessage()
    val content = primitive.content
    if (!primitive.isString || content.isEmpty() || !content.all(Char::isDigit)) {
        throw UnreadableMessage()
    }
    return content.toBigInteger()
}

private fun JsonElement?.coin(): CosmosSignedCoin {
    val coin = this as? JsonObject ?: throw UnreadableMessage()
    return CosmosSignedCoin(denom = coin["denom"].string(), amount = coin["amount"].amount())
}

private fun JsonElement?.coins(): List<CosmosSignedCoin> =
    (this as? JsonArray ?: throw UnreadableMessage()).map { it.coin() }

private fun transfers(recipient: String?, coins: List<CosmosSignedCoin>, memo: String? = null) =
    coins.map {
        CosmosSignedTransferLeg(recipient = recipient, coin = it, memo = memo)
    }

/** The asset a THORChain / Maya deposit names for the chain's own coin. */
private fun nativeDepositAsset(chain: Chain): String? =
    when (chain) {
        Chain.ThorChain -> "THOR.RUNE"
        Chain.MayaChain -> "MAYA.CACAO"
        else -> null
    }

private fun depositDenom(chain: Chain, asset: String): String =
    if (asset.equals(nativeDepositAsset(chain), ignoreCase = true)) {
        chain.cosmosNativeDenom ?: asset
    } else asset

/** `BTC.BTC`, `ETH/ETH` (synth), `ETH~ETH` (trade) or `ETH-ETH` (secured). */
private fun JsonElement?.thorchainAsset(): String {
    val asset = this as? JsonObject ?: throw UnreadableMessage()
    val separator =
        when {
            asset["synth"].isTrue() -> "/"
            asset["trade"].isTrue() -> "~"
            asset["secured"].isTrue() -> "-"
            else -> "."
        }
    return "${asset["chain"].string()}$separator${asset["symbol"].string()}"
}

private fun JsonElement?.isTrue() = (this as? JsonPrimitive)?.content == "true"

private fun readDeposit(
    value: JsonObject,
    chain: Chain,
    asset: (JsonElement?) -> String,
): MessageReading {
    if (nativeDepositAsset(chain) == null) return UNSUMMARIZED
    val coins = value["coins"] as? JsonArray ?: throw UnreadableMessage()
    val memo = value["memo"].optionalString()
    return MessageReading(
        transfers =
            coins.map { element ->
                val coin = element.obj()
                CosmosSignedTransferLeg(
                    recipient = null,
                    coin =
                        CosmosSignedCoin(
                            denom = depositDenom(chain, asset(coin["asset"])),
                            amount = coin["amount"].amount(),
                        ),
                    memo = memo,
                )
            },
        // A deposit's memo only shows on its transfers, so a memo without coins isn't summarized.
        isSummarized = coins.isNotEmpty() || memo == null,
    )
}

private val readSend: MessageReader = { value, _ ->
    val body = value.obj()
    MessageReading(transfers(body["toAddress"].string(), body["amount"].coins()), true)
}

private val readAminoSend: MessageReader = { value, _ ->
    val body = value.obj()
    MessageReading(transfers(body["to_address"].string(), body["amount"].coins()), true)
}

private val noTransfers: MessageReader = { _, _ -> NO_TRANSFERS }

/**
 * Readers for the message bodies [com.vultisig.wallet.data.usecases.ParseCosmosMessageUseCase]
 * decodes, keyed by type url. A type it leaves as base64, or one not listed, is unsummarized.
 */
private val DIRECT_READERS: Map<String, MessageReader> =
    mapOf(
        "/cosmos.bank.v1beta1.MsgSend" to readSend,
        "/types.MsgSend" to readSend,
        "/types.MsgDeposit" to
            { value, chain ->
                readDeposit(value.obj(), chain) { it.thorchainAsset() }
            },
        "/ibc.applications.transfer.v1.MsgTransfer" to
            { value, _ ->
                val body = value.obj()
                MessageReading(
                    transfers(
                        recipient = body["receiver"].string(),
                        coins = listOf(body["token"].coin()),
                        memo = body["memo"].optionalString(),
                    ),
                    isSummarized = true,
                )
            },
        "/cosmwasm.wasm.v1.MsgExecuteContract" to
            { value, _ ->
                val body = value.obj()
                MessageReading(
                    transfers(body["contract"].string(), body["funds"].coins()),
                    isSummarized = false,
                )
            },
        "/cosmos.staking.v1beta1.MsgDelegate" to
            { value, _ ->
                val body = value.obj()
                MessageReading(
                    transfers(body["validatorAddress"].string(), listOf(body["amount"].coin())),
                    isSummarized = true,
                )
            },
        "/cosmos.staking.v1beta1.MsgUndelegate" to noTransfers,
        "/cosmos.staking.v1beta1.MsgBeginRedelegate" to noTransfers,
        "/cosmos.distribution.v1beta1.MsgWithdrawDelegatorReward" to noTransfers,
        "/cosmos.gov.v1beta1.MsgVote" to noTransfers,
    )

private val AMINO_READERS: Map<String, MessageReader> =
    mapOf(
        "cosmos-sdk/MsgSend" to readAminoSend,
        "thorchain/MsgSend" to readAminoSend,
        "bank/MsgSend" to readAminoSend,
        "thorchain/MsgDeposit" to
            { value, chain ->
                readDeposit(value.obj(), chain) { it.string() }
            },
        "cosmos-sdk/MsgTransfer" to
            { value, _ ->
                val body = value.obj()
                MessageReading(
                    transfers(
                        recipient = body["receiver"].string(),
                        coins = listOf(body["token"].coin()),
                        memo = body["memo"].optionalString(),
                    ),
                    isSummarized = true,
                )
            },
        "wasm/MsgExecuteContract" to
            { value, _ ->
                val body = value.obj()
                MessageReading(
                    transfers(
                        recipient = body["contract"].string(),
                        coins = body["funds"]?.coins().orEmpty(),
                    ),
                    isSummarized = false,
                )
            },
        "cosmos-sdk/MsgDelegate" to
            { value, _ ->
                val body = value.obj()
                MessageReading(
                    transfers(body["validator_address"].string(), listOf(body["amount"].coin())),
                    isSummarized = true,
                )
            },
        "cosmos-sdk/MsgUndelegate" to noTransfers,
        "cosmos-sdk/MsgBeginRedelegate" to noTransfers,
        "cosmos-sdk/MsgWithdrawDelegationReward" to noTransfers,
        "cosmos-sdk/MsgVote" to noTransfers,
        "cosmos-sdk/v1/MsgVote" to noTransfers,
        "cosmos-sdk/MsgVoteWeighted" to noTransfers,
        "cosmos-sdk/v1/MsgVoteWeighted" to noTransfers,
    )
