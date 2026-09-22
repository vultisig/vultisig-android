package com.vultisig.wallet.ui.models.sign

import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * The parts of an `eth_signTypedData_v4` payload the verify screen reads. The `types` block is
 * left alone: it only matters to the hasher, which takes the raw JSON anyway.
 */
internal data class Eip712TypedData(
    val domainName: String?,
    val domainChainId: BigInteger?,
    val verifyingContract: String?,
    val primaryType: String,
    val message: JsonObject,
) {
    companion object {
        /** Reads [raw] as EIP-712 typed data, or null when it is not a JSON object shaped like one. */
        fun parse(json: Json, raw: String): Eip712TypedData? {
            val root =
                runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
                    ?: return null
            val primaryType = root["primaryType"]?.stringOrNull() ?: return null
            val message = root["message"] as? JsonObject ?: return null
            val domain = root["domain"] as? JsonObject
            return Eip712TypedData(
                domainName = domain?.get("name")?.stringOrNull(),
                domainChainId = domain?.get("chainId")?.bigIntegerOrNull(),
                verifyingContract = domain?.get("verifyingContract")?.stringOrNull(),
                primaryType = primaryType,
                message = message,
            )
        }
    }
}

/** One token a permit grants an allowance on. [expiration] is only carried by Permit2. */
internal data class PermitToken(
    val address: String,
    val amount: BigInteger,
    val expiration: BigInteger?,
)

/**
 * A token approval signed off-chain: EIP-2612 `Permit`, or one of the Permit2 shapes. Mirrors the
 * extension's `Eip712PermitDisplay` parsers so both sides of a keysign describe the same request.
 */
internal data class Eip712Permit(
    val primaryType: String,
    val spender: String,
    val tokens: List<PermitToken>,
    val deadline: BigInteger?,
) {
    /**
     * Whether [amount] is the "approve everything" sentinel for this permit shape. Permit2 packs
     * the amount into a uint160, so its max is a second sentinel there — and only there: in an
     * EIP-2612 `Permit` the amount is a uint256, where 2^160-1 is just a (huge) number.
     * `PermitTransferFrom` amounts are transfers, not allowances, so nothing is unlimited.
     */
    fun isUnlimited(amount: BigInteger): Boolean =
        when (primaryType) {
            PERMIT -> amount == MAX_UINT256
            PERMIT_SINGLE,
            PERMIT_BATCH -> amount == MAX_UINT256 || amount == MAX_UINT160
            else -> false
        }

    companion object {
        const val PERMIT = "Permit"
        const val PERMIT_SINGLE = "PermitSingle"
        const val PERMIT_BATCH = "PermitBatch"
        const val PERMIT_TRANSFER_FROM = "PermitTransferFrom"
        const val PERMIT_BATCH_TRANSFER_FROM = "PermitBatchTransferFrom"
    }
}

private val MAX_UINT256: BigInteger = BigInteger.ONE.shiftLeft(256) - BigInteger.ONE
private val MAX_UINT160: BigInteger = BigInteger.ONE.shiftLeft(160) - BigInteger.ONE

/** The permit this typed data carries, or null when its primary type is not a known permit. */
internal fun Eip712TypedData.permitOrNull(): Eip712Permit? {
    val spender = message["spender"]?.stringOrNull() ?: return null
    val parsed =
        when (primaryType) {
            Eip712Permit.PERMIT -> eip2612Permit()
            Eip712Permit.PERMIT_SINGLE -> permit2Single()
            Eip712Permit.PERMIT_BATCH -> permit2Batch()
            Eip712Permit.PERMIT_TRANSFER_FROM -> permit2TransferFrom()
            Eip712Permit.PERMIT_BATCH_TRANSFER_FROM -> permit2BatchTransferFrom()
            else -> null
        } ?: return null
    return Eip712Permit(
        primaryType = primaryType,
        spender = spender,
        tokens = parsed.first,
        deadline = parsed.second,
    )
}

private typealias ParsedPermit = Pair<List<PermitToken>, BigInteger?>

/**
 * EIP-2612: the token is the verifying contract and the amount is `value`. The DAI variant carries
 * a boolean `allowed` instead, which is read as all-or-nothing so it lands on the same rows.
 */
private fun Eip712TypedData.eip2612Permit(): ParsedPermit? {
    val token = verifyingContract ?: return null
    val allowed = (message["allowed"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
    val amount =
        when (allowed) {
            true -> MAX_UINT256
            false -> BigInteger.ZERO
            null -> message["value"]?.bigIntegerOrNull() ?: return null
        }
    val deadline = message["deadline"]?.bigIntegerOrNull() ?: message["expiry"]?.bigIntegerOrNull()
    return listOf(PermitToken(token, amount, expiration = null)) to deadline
}

private fun Eip712TypedData.permit2Single(): ParsedPermit? {
    val details = message["details"] as? JsonObject ?: return null
    val token = details.allowanceToken() ?: return null
    return listOf(token) to message["sigDeadline"]?.bigIntegerOrNull()
}

private fun Eip712TypedData.permit2Batch(): ParsedPermit? {
    val details = message["details"] as? JsonArray ?: return null
    val tokens = details.map { (it as? JsonObject)?.allowanceToken() ?: return null }
    return tokens to message["sigDeadline"]?.bigIntegerOrNull()
}

private fun Eip712TypedData.permit2TransferFrom(): ParsedPermit? {
    val permitted = message["permitted"] as? JsonObject ?: return null
    val token = permitted.transferToken() ?: return null
    return listOf(token) to message["deadline"]?.bigIntegerOrNull()
}

private fun Eip712TypedData.permit2BatchTransferFrom(): ParsedPermit? {
    val permitted = message["permitted"] as? JsonArray ?: return null
    val tokens = permitted.map { (it as? JsonObject)?.transferToken() ?: return null }
    return tokens to message["deadline"]?.bigIntegerOrNull()
}

/** Permit2 `PermitDetails`: token, amount and the allowance's own expiration. */
private fun JsonObject.allowanceToken(): PermitToken? =
    transferToken()?.copy(expiration = this["expiration"]?.bigIntegerOrNull())

/** Permit2 `TokenPermissions`: token and amount only. */
private fun JsonObject.transferToken(): PermitToken? {
    val address = this["token"]?.stringOrNull() ?: return null
    val amount = this["amount"]?.bigIntegerOrNull() ?: return null
    return PermitToken(address, amount, expiration = null)
}

private fun JsonElement.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

/**
 * Reads a JSON number, a decimal string, or a `0x` hex string — all three show up in typed data
 * produced by different dApp libraries.
 */
private fun JsonElement.bigIntegerOrNull(): BigInteger? {
    if (this is JsonNull || this !is JsonPrimitive) return null
    val text = content.trim()
    if (text.isEmpty()) return null
    return runCatching {
            if (text.startsWith("0x", ignoreCase = true)) BigInteger(text.substring(2), 16)
            else BigInteger(text)
        }
        .getOrNull()
}
