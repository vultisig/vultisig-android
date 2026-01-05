@file:OptIn(ExperimentalEncodingApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun interface ParseCosmosMessageUseCase {
    operator fun invoke(
        signDirect: SignDirectProto
    ): CosmosMessage
}

internal class ParseCosmosMessageUseCaseImpl @Inject constructor(
    private val protoBuf: ProtoBuf,
) : ParseCosmosMessageUseCase {

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
                authInfoFee = decodedAuthInfo.authInfoFee?.toFee() ?: Fee(amount = emptyList())
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

        val decodedBytes = try {
            Base64.decode(input)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid base64 encoding in TxBody", e)
        }

        val txBody = try {
            protoBuf.decodeFromByteArray<TxBody>(decodedBytes)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decode TxBody: ${e.message}", e)
        }

        require(txBody.messages.isNotEmpty()) { "TxBody must contain at least one message" }

        return txBody
    }

    internal fun decodeAuthInfoSafe(input: String): AuthInfo {
        require(input.isNotBlank()) { "AuthInfo input cannot be blank" }

        val decodedBytes = try {
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

    private fun ProtobufAny.toMessage() = Message(
        typeUrl = typeUrl,
        value = Base64.encode(value)
    )

    private fun AuthInfoFee.toFee() = Fee(
        amount = amount.map { Amount(denom = it.denom, amount = it.amount) }
    )

}

@Serializable
internal data class TxBody(
    @ProtoNumber(1)
    val messages: List<ProtobufAny> = emptyList(),
    @ProtoNumber(2)
    val memo: String = "",
    @ProtoNumber(3)
    val timeoutHeight: ULong = 0UL,
    @ProtoNumber(4)
    val unordered: Boolean = false,
    @ProtoNumber(5)
    val timeoutTimestamp: Timestamp? = null,
    @ProtoNumber(1023)
    val extensionOptions: List<ProtobufAny> = emptyList(),
    @ProtoNumber(2047)
    val nonCriticalExtensionOptions: List<ProtobufAny> = emptyList(),
)

@Serializable
internal data class ProtobufAny(
    @ProtoNumber(1)
    val typeUrl: String = "",
    @ProtoNumber(2)
    val value: ByteArray = ByteArray(0),
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
    @ProtoNumber(1)
    val seconds: Long = 0L,
    @ProtoNumber(2)
    val nanos: Int = 0,
)

@Serializable
internal data class Coin(
    @ProtoNumber(1)
    val denom: String = "",
    @ProtoNumber(2)
    val amount: String = "",
)

@Serializable
internal data class CompactBitArray(
    @ProtoNumber(1)
    val extraBitsStored: UInt = 0u,
    @ProtoNumber(2)
    val elems: ByteArray = ByteArray(0),
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

@Serializable
internal data class ModeInfoSingle(
    @ProtoNumber(1)
    val mode: Int = 0,
)

@Serializable
internal data class ModeInfoMulti(
    @ProtoNumber(1)
    val bitarray: CompactBitArray? = null,
    @ProtoNumber(2)
    val modeInfos: List<ModeInfo> = emptyList(),
)

@Serializable
internal data class ModeInfo(
    @ProtoNumber(1)
    val single: ModeInfoSingle? = null,
    @ProtoNumber(2)
    val multi: ModeInfoMulti? = null,
)

@Serializable
internal data class SignerInfo(
    @ProtoNumber(1)
    val publicKey: ProtobufAny? = null,
    @ProtoNumber(2)
    val modeInfo: ModeInfo? = null,
    @ProtoNumber(3)
    val sequence: ULong = 0UL,
)

@Serializable
internal data class AuthInfoFee(
    @ProtoNumber(1)
    val amount: List<Coin> = emptyList(),
    @ProtoNumber(2)
    val gasLimit: ULong = 0UL,
    @ProtoNumber(3)
    val payer: String = "",
    @ProtoNumber(4)
    val granter: String = "",
)

@Serializable
internal data class Tip(
    @ProtoNumber(1)
    val amount: List<Coin> = emptyList(),
    @ProtoNumber(2)
    val tipper: String = "",
)

@Serializable
internal data class AuthInfo(
    @ProtoNumber(1)
    val signerInfos: List<SignerInfo> = emptyList(),
    @ProtoNumber(2)
    val authInfoFee: AuthInfoFee? = null,
    @ProtoNumber(3)
    val tip: Tip? = null,
)


@Serializable
data class CosmosMessage(
    val chainId: String,
    val accountNumber: String,
    val sequence: String,
    val memo: String,
    val messages: List<Message>,
    @SerialName("fee")
    val authInfoFee: Fee,
)

@Serializable
data class Message(
    val typeUrl: String,
    val value: String,
)

@Serializable
data class Fee(
    val amount: List<Amount>,
)

@Serializable
data class Amount(
    val denom: String,
    val amount: String,
)
