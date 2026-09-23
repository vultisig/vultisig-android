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
 * The parts of an `eth_signTypedData_v4` payload the verify screen reads, reduced to what is
 * actually signed. The hashers only encode the fields `types` declares, so an undeclared key in
 * `domain` or `message` is dropped here rather than shown as if it were part of the signature.
 */
internal data class Eip712TypedData(
    val domainName: String?,
    val domainChainId: BigInteger?,
    val verifyingContract: String?,
    val primaryType: String,
    val message: JsonObject,
) {
    companion object {
        /**
         * Reads [raw] as EIP-712 typed data, or null when it is not a JSON object shaped like one.
         * A payload over [MAX_TYPED_DATA_LENGTH] is not read at all and is left to the raw view.
         */
        fun parse(json: Json, raw: String): Eip712TypedData? {
            if (raw.length > MAX_TYPED_DATA_LENGTH) return null
            val root =
                runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
                    ?: return null
            val primaryType = root["primaryType"]?.stringOrNull() ?: return null
            val types = root["types"] as? JsonObject ?: return null
            val message =
                (root["message"] as? JsonObject)?.declaredOnly(primaryType, types, depth = 0)
                    ?: return null
            // ethers derives the domain type from the domain's own keys when it is not declared,
            // so the domain is only narrowed when `EIP712Domain` is.
            val domain =
                (root["domain"] as? JsonObject)?.let { domain ->
                    if (DOMAIN_TYPE in types) domain.declaredOnly(DOMAIN_TYPE, types, depth = 0)
                    else domain
                }
            return Eip712TypedData(
                domainName = domain?.get("name")?.stringOrNull(),
                domainChainId = domain?.get("chainId")?.bigIntegerOrNull(),
                verifyingContract = domain?.get("verifyingContract")?.addressOrNull(),
                primaryType = primaryType,
                message = message,
            )
        }
    }
}

/**
 * [this] struct value with only the fields [type] declares, in declared order, and each nested
 * struct narrowed the same way. Null when [type] is not a declared struct or nesting runs past
 * [MAX_TYPE_DEPTH], which self-referencing types would otherwise allow.
 */
private fun JsonObject.declaredOnly(type: String, types: JsonObject, depth: Int): JsonObject? {
    if (depth > MAX_TYPE_DEPTH) return null
    val fields = types[type] as? JsonArray ?: return null
    val narrowed = LinkedHashMap<String, JsonElement>()
    for (field in fields) {
        val name = (field as? JsonObject)?.get("name")?.stringOrNull() ?: return null
        val fieldType = field["type"]?.stringOrNull() ?: return null
        val value = this[name] ?: continue
        narrowed[name] = value.declaredOnlyAs(fieldType, types, depth + 1) ?: return null
    }
    return JsonObject(narrowed)
}

/** [this] value narrowed per [type]: structs and arrays of them recurse, anything else is kept. */
private fun JsonElement.declaredOnlyAs(type: String, types: JsonObject, depth: Int): JsonElement? {
    if (type.endsWith("]")) {
        val elementType = type.substringBeforeLast("[")
        val array = this as? JsonArray ?: return this
        return JsonArray(array.map { it.declaredOnlyAs(elementType, types, depth) ?: return null })
    }
    if (type !in types) return this
    return (this as? JsonObject)?.declaredOnly(type, types, depth)
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

    /** Permit2 `PermitTransferFrom` shapes sign a one-time transfer rather than an allowance. */
    val isTransfer: Boolean
        get() = primaryType == PERMIT_TRANSFER_FROM || primaryType == PERMIT_BATCH_TRANSFER_FROM

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

/**
 * The most tokens a batch permit is read into rows for. Each one can cost a metadata lookup, so a
 * larger batch falls back to the generic typed-data rows instead.
 */
private const val MAX_PERMIT_TOKENS = 16

/** Real typed data is a few KB; anything far past that is not worth parsing for display. */
private const val MAX_TYPED_DATA_LENGTH = 64 * 1024

/** Uniswap's Permit2, deployed at the same address on every chain it supports. */
private const val PERMIT2_ADDRESS = "0x000000000022D473030F116dDEE9F6B43aC78BA3"

private const val DOMAIN_TYPE = "EIP712Domain"

/** Deeper than any real typed data nests; bounds the recursion in [declaredOnly]. */
private const val MAX_TYPE_DEPTH = 16

private val EVM_ADDRESS = Regex("^0x[0-9a-fA-F]{40}$")

/**
 * The permit this typed data carries, or null when its primary type is not a known permit. The
 * Permit2 shapes are only read as such when signed for the Permit2 contract itself; any other
 * contract reusing those type names gets the generic rows rather than Permit2's semantics.
 */
internal fun Eip712TypedData.permitOrNull(): Eip712Permit? {
    val spender = message["spender"]?.addressOrNull() ?: return null
    val isPermit2 = verifyingContract.equals(PERMIT2_ADDRESS, ignoreCase = true)
    val parsed =
        when (primaryType) {
            Eip712Permit.PERMIT -> eip2612Permit()
            Eip712Permit.PERMIT_SINGLE -> if (isPermit2) permit2Single() else null
            Eip712Permit.PERMIT_BATCH -> if (isPermit2) permit2Batch() else null
            Eip712Permit.PERMIT_TRANSFER_FROM -> if (isPermit2) permit2TransferFrom() else null
            Eip712Permit.PERMIT_BATCH_TRANSFER_FROM ->
                if (isPermit2) permit2BatchTransferFrom() else null
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
    if (details.size > MAX_PERMIT_TOKENS) return null
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
    if (permitted.size > MAX_PERMIT_TOKENS) return null
    val tokens = permitted.map { (it as? JsonObject)?.transferToken() ?: return null }
    return tokens to message["deadline"]?.bigIntegerOrNull()
}

/** Permit2 `PermitDetails`: token, amount and the allowance's own expiration. */
private fun JsonObject.allowanceToken(): PermitToken? =
    transferToken()?.copy(expiration = this["expiration"]?.bigIntegerOrNull())

/** Permit2 `TokenPermissions`: token and amount only. */
private fun JsonObject.transferToken(): PermitToken? {
    val address = this["token"]?.addressOrNull() ?: return null
    val amount = this["amount"]?.bigIntegerOrNull() ?: return null
    return PermitToken(address, amount, expiration = null)
}

private fun JsonElement.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

/**
 * A 20-byte hex address, or null. Typed data can declare these fields as `string`, so anything
 * else is refused rather than shown and copied as an address.
 */
private fun JsonElement.addressOrNull(): String? = stringOrNull()?.takeIf { EVM_ADDRESS.matches(it) }

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
