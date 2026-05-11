@file:OptIn(ExperimentalEncodingApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import wallet.core.jni.Bech32

fun interface ParseCosmosMessageUseCase {
    operator fun invoke(signDirect: SignDirectProto): CosmosMessage
}

internal class ParseCosmosMessageUseCaseImpl(
    private val protoBuf: ProtoBuf,
    private val thorAddressEncoder: (ByteArray) -> String,
) : ParseCosmosMessageUseCase {

    @Inject constructor(protoBuf: ProtoBuf) : this(protoBuf, { Bech32.encode(THOR_BECH32_HRP, it) })

    override fun invoke(signDirect: SignDirectProto): CosmosMessage {
        require(signDirect.chainId.isNotBlank()) { "Chain ID cannot be blank" }
        require(signDirect.accountNumber.isNotBlank()) { "Account number cannot be blank" }
        require(signDirect.bodyBytes.isNotBlank()) { "Body bytes cannot be blank" }
        require(signDirect.authInfoBytes.isNotBlank()) { "Auth info bytes cannot be blank" }

        return try {
            val decodedTxBody = decodeTxBodySafe(signDirect.bodyBytes)
            val decodedAuthInfo = decodeAuthInfoSafe(signDirect.authInfoBytes)

            CosmosMessage(
                chainId = signDirect.chainId,
                accountNumber = signDirect.accountNumber,
                sequence = decodedAuthInfo.signerInfos.firstOrNull()?.sequence?.toString() ?: "0",
                memo = decodedTxBody.memo,
                messages = decodedTxBody.messages.map { it.toMessage() },
                authInfoFee = decodedAuthInfo.authInfoFee?.toFee() ?: Fee(amount = emptyList()),
            )
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalArgumentException("Invalid protobuf encoding: premature end of buffer", e)
        } catch (e: IllegalStateException) {
            throw IllegalArgumentException("Invalid protobuf encoding: ${e.message}", e)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse cosmos message: ${e.message}", e)
        }
    }

    internal fun decodeTxBodySafe(input: String): TxBody {
        require(input.isNotBlank()) { "TxBody input cannot be blank" }

        val decodedBytes =
            try {
                Base64.decode(input)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid base64 encoding in TxBody", e)
            }

        val txBody =
            try {
                protoBuf.decodeFromByteArray<TxBody>(decodedBytes)
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to decode TxBody: ${e.message}", e)
            }

        require(txBody.messages.isNotEmpty()) { "TxBody must contain at least one message" }

        return txBody
    }

    internal fun decodeAuthInfoSafe(input: String): AuthInfo {
        require(input.isNotBlank()) { "AuthInfo input cannot be blank" }

        val decodedBytes =
            try {
                Base64.decode(input)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid base64 encoding in AuthInfo", e)
            }

        return try {
            protoBuf.decodeFromByteArray<AuthInfo>(decodedBytes)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decode AuthInfo: ${e.message}", e)
        }
    }

    private fun ProtobufAny.toMessage() =
        Message(typeUrl = typeUrl, value = decodeValueOrFallback())

    private fun ProtobufAny.decodeValueOrFallback(): JsonElement =
        try {
            when (typeUrl) {
                "/cosmos.bank.v1beta1.MsgSend" ->
                    JSON.encodeToJsonElement(decodeChecked<MsgSendBody>(value))
                "/types.MsgSend" ->
                    JSON.encodeToJsonElement(
                        decodeChecked<ThorMsgSendBody>(value) {
                                requireThorAddressBytes(it.fromAddress)
                                requireThorAddressBytes(it.toAddress)
                            }
                            .toRendered()
                    )
                "/types.MsgDeposit" ->
                    JSON.encodeToJsonElement(
                        decodeChecked<ThorMsgDepositBody>(value) {
                                requireThorAddressBytes(it.signer)
                            }
                            .toRendered()
                    )
                "/cosmwasm.wasm.v1.MsgExecuteContract" ->
                    JSON.encodeToJsonElement(
                        decodeChecked<MsgExecuteContractBody>(value).toRendered()
                    )
                "/ibc.applications.transfer.v1.MsgTransfer" ->
                    JSON.encodeToJsonElement(decodeChecked<MsgTransferBody>(value))
                else -> JsonPrimitive(Base64.encode(value))
            }
        } catch (_: SerializationException) {
            JsonPrimitive(Base64.encode(value))
        }

    private fun ThorMsgSendBody.toRendered() =
        RenderedThorMsgSend(
            fromAddress = encodeThorAddress(fromAddress),
            toAddress = encodeThorAddress(toAddress),
            amount = amount,
        )

    private fun ThorMsgDepositBody.toRendered() =
        RenderedThorMsgDeposit(coins = coins, memo = memo, signer = encodeThorAddress(signer))

    private fun encodeThorAddress(bytes: ByteArray): String =
        if (bytes.isEmpty()) "" else thorAddressEncoder(bytes)

    private inline fun <reified T> decodeChecked(bytes: ByteArray, validate: (T) -> Unit = {}): T =
        protoBuf.decodeFromByteArray<T>(bytes).also(validate)

    private fun requireThorAddressBytes(bytes: ByteArray) {
        if (bytes.size != THOR_ADDRESS_LENGTH_BYTES) {
            throw SerializationException(
                "Invalid Thor address byte length: ${bytes.size}, expected $THOR_ADDRESS_LENGTH_BYTES"
            )
        }
    }

    private fun AuthInfoFee.toFee() =
        Fee(
            amount = amount.map { Amount(denom = it.denom, amount = it.amount) },
            gasLimit = gasLimit.toString(),
        )

    companion object {
        private const val THOR_BECH32_HRP = "thor"
        private const val THOR_ADDRESS_LENGTH_BYTES = 20

        private val JSON = Json {
            encodeDefaults = true
            explicitNulls = false
        }
    }
}

private fun MsgExecuteContractBody.toRendered() =
    RenderedMsgExecuteContract(
        sender = sender,
        contract = contract,
        msg = msg.toString(Charsets.UTF_8),
        funds = funds,
    )

@Serializable
internal data class MsgSendBody(
    @ProtoNumber(1) val fromAddress: String = "",
    @ProtoNumber(2) val toAddress: String = "",
    @ProtoNumber(3) val amount: List<Coin> = emptyList(),
)

@Serializable
internal data class ThorMsgSendBody(
    @ProtoNumber(1) val fromAddress: ByteArray = ByteArray(0),
    @ProtoNumber(2) val toAddress: ByteArray = ByteArray(0),
    @ProtoNumber(3) val amount: List<Coin> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ThorMsgSendBody) return false
        if (!fromAddress.contentEquals(other.fromAddress)) return false
        if (!toAddress.contentEquals(other.toAddress)) return false
        if (amount != other.amount) return false
        return true
    }

    override fun hashCode(): Int {
        var result = fromAddress.contentHashCode()
        result = 31 * result + toAddress.contentHashCode()
        result = 31 * result + amount.hashCode()
        return result
    }
}

@Serializable
internal data class ThorChainAsset(
    @ProtoNumber(1) val chain: String = "",
    @ProtoNumber(2) val symbol: String = "",
    @ProtoNumber(3) val ticker: String = "",
    @ProtoNumber(4) val synth: Boolean = false,
    @ProtoNumber(5) val trade: Boolean = false,
    @ProtoNumber(6) val secured: Boolean = false,
)

@Serializable
internal data class ThorChainCoin(
    @ProtoNumber(1) val asset: ThorChainAsset = ThorChainAsset(),
    @ProtoNumber(2) val amount: String = "",
    @ProtoNumber(3) val decimals: Long = 0L,
)

@Serializable
internal data class ThorMsgDepositBody(
    @ProtoNumber(1) val coins: List<ThorChainCoin> = emptyList(),
    @ProtoNumber(2) val memo: String = "",
    @ProtoNumber(3) val signer: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ThorMsgDepositBody) return false
        if (coins != other.coins) return false
        if (memo != other.memo) return false
        if (!signer.contentEquals(other.signer)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = coins.hashCode()
        result = 31 * result + memo.hashCode()
        result = 31 * result + signer.contentHashCode()
        return result
    }
}

@Serializable
internal data class MsgExecuteContractBody(
    @ProtoNumber(1) val sender: String = "",
    @ProtoNumber(2) val contract: String = "",
    @ProtoNumber(3) val msg: ByteArray = ByteArray(0),
    @ProtoNumber(5) val funds: List<Coin> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MsgExecuteContractBody) return false
        if (sender != other.sender) return false
        if (contract != other.contract) return false
        if (!msg.contentEquals(other.msg)) return false
        if (funds != other.funds) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sender.hashCode()
        result = 31 * result + contract.hashCode()
        result = 31 * result + msg.contentHashCode()
        result = 31 * result + funds.hashCode()
        return result
    }
}

@Serializable
internal data class IbcHeight(
    @ProtoNumber(1) val revisionNumber: ULong = 0UL,
    @ProtoNumber(2) val revisionHeight: ULong = 0UL,
)

@Serializable
internal data class MsgTransferBody(
    @ProtoNumber(1) val sourcePort: String = "",
    @ProtoNumber(2) val sourceChannel: String = "",
    @ProtoNumber(3) val token: Coin? = null,
    @ProtoNumber(4) val sender: String = "",
    @ProtoNumber(5) val receiver: String = "",
    @ProtoNumber(6) val timeoutHeight: IbcHeight? = null,
    @ProtoNumber(7) val timeoutTimestamp: ULong = 0UL,
    @ProtoNumber(8) val memo: String = "",
)

@Serializable
private data class RenderedThorMsgSend(
    val fromAddress: String,
    val toAddress: String,
    val amount: List<Coin>,
)

@Serializable
private data class RenderedThorMsgDeposit(
    val coins: List<ThorChainCoin>,
    val memo: String,
    val signer: String,
)

@Serializable
private data class RenderedMsgExecuteContract(
    val sender: String,
    val contract: String,
    val msg: String,
    val funds: List<Coin>,
)

@Serializable
internal data class TxBody(
    @ProtoNumber(1) val messages: List<ProtobufAny> = emptyList(),
    @ProtoNumber(2) val memo: String = "",
    @ProtoNumber(3) val timeoutHeight: ULong = 0UL,
    @ProtoNumber(4) val unordered: Boolean = false,
    @ProtoNumber(5) val timeoutTimestamp: Timestamp? = null,
    @ProtoNumber(1023) val extensionOptions: List<ProtobufAny> = emptyList(),
    @ProtoNumber(2047) val nonCriticalExtensionOptions: List<ProtobufAny> = emptyList(),
)

@Serializable
internal data class ProtobufAny(
    @ProtoNumber(1) val typeUrl: String = "",
    @ProtoNumber(2) val value: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtobufAny) return false
        if (typeUrl != other.typeUrl) return false
        if (!value.contentEquals(other.value)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = typeUrl.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}

@Serializable
internal data class Timestamp(
    @ProtoNumber(1) val seconds: Long = 0L,
    @ProtoNumber(2) val nanos: Int = 0,
)

@Serializable
internal data class Coin(
    @ProtoNumber(1) val denom: String = "",
    @ProtoNumber(2) val amount: String = "",
)

@Serializable
internal data class CompactBitArray(
    @ProtoNumber(1) val extraBitsStored: UInt = 0u,
    @ProtoNumber(2) val elems: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompactBitArray) return false
        if (extraBitsStored != other.extraBitsStored) return false
        if (!elems.contentEquals(other.elems)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = extraBitsStored.hashCode()
        result = 31 * result + elems.contentHashCode()
        return result
    }
}

@Serializable internal data class ModeInfoSingle(@ProtoNumber(1) val mode: Int = 0)

@Serializable
internal data class ModeInfoMulti(
    @ProtoNumber(1) val bitarray: CompactBitArray? = null,
    @ProtoNumber(2) val modeInfos: List<ModeInfo> = emptyList(),
)

@Serializable
internal data class ModeInfo(
    @ProtoNumber(1) val single: ModeInfoSingle? = null,
    @ProtoNumber(2) val multi: ModeInfoMulti? = null,
)

@Serializable
internal data class SignerInfo(
    @ProtoNumber(1) val publicKey: ProtobufAny? = null,
    @ProtoNumber(2) val modeInfo: ModeInfo? = null,
    @ProtoNumber(3) val sequence: ULong = 0UL,
)

@Serializable
internal data class AuthInfoFee(
    @ProtoNumber(1) val amount: List<Coin> = emptyList(),
    @ProtoNumber(2) val gasLimit: ULong = 0UL,
    @ProtoNumber(3) val payer: String = "",
    @ProtoNumber(4) val granter: String = "",
)

@Serializable
internal data class Tip(
    @ProtoNumber(1) val amount: List<Coin> = emptyList(),
    @ProtoNumber(2) val tipper: String = "",
)

@Serializable
internal data class AuthInfo(
    @ProtoNumber(1) val signerInfos: List<SignerInfo> = emptyList(),
    @ProtoNumber(2) val authInfoFee: AuthInfoFee? = null,
    @ProtoNumber(3) val tip: Tip? = null,
)

@Serializable
data class CosmosMessage(
    val chainId: String,
    val accountNumber: String,
    val sequence: String,
    val memo: String,
    val messages: List<Message>,
    @SerialName("fee") val authInfoFee: Fee,
)

@Serializable data class Message(val typeUrl: String, val value: JsonElement)

@Serializable data class Fee(val amount: List<Amount>, val gasLimit: String = "0")

@Serializable data class Amount(val denom: String, val amount: String)
