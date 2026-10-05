package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.blockchain.near.NearFees
import com.vultisig.wallet.data.common.hexToByteArrayOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import wallet.core.jni.Base58

/** What the node reports for an account; `locked` only ever relaxes the storage requirement. */
data class NearAccount(val amount: BigInteger, val locked: BigInteger, val storageUsage: BigInteger)

data class NearAccessKey(val nonce: BigInteger, val isFullAccess: Boolean)

/** The final block a transaction is anchored to, and the gas price its reservation is priced at. */
data class NearFinalBlock(val hash: ByteArray, val gasPrice: BigInteger)

/** What the node knows about a transaction before finality is interpreted. */
data class NearTransactionOutcome(
    val returnedHash: String?,
    val finalExecutionStatus: String?,
    val status: JsonElement?,
)

/** A plain unsigned decimal: no sign, no whitespace, no exponent. */
internal val NEAR_UNSIGNED_DECIMAL = Regex("^[0-9]+$")

/** A JSON-RPC error the node returned; [name] distinguishes a missing record from a rejection. */
class NearRpcException(val method: String, val name: String, message: String) :
    Exception("NEAR $method failed ($name): $message")

/**
 * NEAR mainnet JSON-RPC. Everything the native transfer freezes at preparation time comes from
 * here: the access-key nonce, the final block's hash and gas price, and the runtime config the gas
 * reservation is priced from.
 */
interface NearApi {
    /** `null` when the node answers UNKNOWN_ACCOUNT (an unfunded account); a failed read throws. */
    suspend fun getAccount(accountId: String): NearAccount?

    /** `null` when the account does not hold this key; a failed read throws. */
    suspend fun getAccessKey(accountId: String, hexPublicKey: String): NearAccessKey?

    suspend fun getFinalBlock(): NearFinalBlock

    suspend fun getFeeConfig(): NearFees.FeeConfig

    /** The hash the node reports for the submitted bytes, when it reports one. */
    suspend fun sendTransaction(signedTransactionBase64: String): String?

    suspend fun getTransactionOutcome(hash: String, senderAccountId: String): NearTransactionOutcome
}

/**
 * Balance [account] must keep behind to back its own storage. The fee config is read only when
 * NEP-448 does not already exempt the account; a failed read throws.
 */
suspend fun NearApi.storageReserve(account: NearAccount): BigInteger =
    if (account.storageUsage <= NearFees.ZERO_BALANCE_STORAGE_LIMIT) {
        BigInteger.ZERO
    } else {
        NearFees.storageReserve(
            storageUsage = account.storageUsage,
            locked = account.locked,
            storageAmountPerByte = getFeeConfig().storageAmountPerByte,
        )
    }

/** [storageReserve] of [accountId] read now; an unfunded account reserves nothing. */
suspend fun NearApi.storageReserve(accountId: String): BigInteger =
    getAccount(accountId)?.let { storageReserve(it) } ?: BigInteger.ZERO

internal class NearApiImpl
@Inject
constructor(private val httpClient: HttpClient, private val json: Json) : NearApi {

    override suspend fun getAccount(accountId: String): NearAccount? {
        val result =
            queryOrNull(
                unknownName = UNKNOWN_ACCOUNT,
                params =
                    buildJsonObject {
                        put("request_type", "view_account")
                        put("finality", "final")
                        put("account_id", accountId)
                    },
            ) ?: return null

        return NearAccount(
            amount = result.exactInteger("amount"),
            locked = result.exactInteger("locked"),
            storageUsage = result.exactInteger("storage_usage"),
        )
    }

    // Read at `optimistic`: the nonce is the latest one, so a second send inside the finality
    // window does not reuse it (InvalidNonce).
    override suspend fun getAccessKey(accountId: String, hexPublicKey: String): NearAccessKey? {
        val result =
            queryOrNull(
                unknownName = UNKNOWN_ACCESS_KEY,
                params =
                    buildJsonObject {
                        put("request_type", "view_access_key")
                        put("finality", "optimistic")
                        put("account_id", accountId)
                        put("public_key", publicKeyString(hexPublicKey))
                    },
            ) ?: return null

        // A missing key can also arrive as a `result.error` string rather than a JSON-RPC error.
        if (result["error"] != null) return null
        val permission = result["permission"] ?: return null

        return NearAccessKey(
            nonce = result.exactInteger("nonce"),
            isFullAccess = (permission as? JsonPrimitive)?.content == FULL_ACCESS,
        )
    }

    override suspend fun getFinalBlock(): NearFinalBlock {
        val header =
            call("block", buildJsonObject { put("finality", "final") })["header"]?.jsonObject
                ?: throw malformed("block", "is missing its header")
        val hash =
            (header["hash"] as? JsonPrimitive)?.content
                ?: throw malformed("block", "is missing its header hash")
        val decoded = Base58.decodeNoCheck(hash)
        if (decoded == null || decoded.size != BLOCK_HASH_BYTES) {
            throw malformed("block", "hash is not $BLOCK_HASH_BYTES bytes: $hash")
        }
        return NearFinalBlock(hash = decoded, gasPrice = header.exactInteger("gas_price"))
    }

    override suspend fun getFeeConfig(): NearFees.FeeConfig {
        val method = "EXPERIMENTAL_protocol_config"
        val runtime =
            call(method, buildJsonObject { put("finality", "final") })["runtime_config"]?.jsonObject
                ?: throw malformed(method, "is missing its runtime_config")
        val costs =
            runtime["transaction_costs"]?.jsonObject
                ?: throw malformed(method, "is missing transaction_costs")
        val creation = costs["action_creation_config"]?.jsonObject

        return NearFees.FeeConfig(
            actionReceiptCreation =
                parameterCost(costs["action_receipt_creation_config"], "receipt creation"),
            transfer = parameterCost(creation?.get("transfer_cost"), "transfer"),
            createAccount = parameterCost(creation?.get("create_account_cost"), "create account"),
            addFullAccessKey =
                parameterCost(
                    creation?.get("add_key_cost")?.jsonObject?.get("full_access_cost"),
                    "add full access key",
                ),
            minGasPurchasePrice = runtime.exactInteger("min_gas_purchase_price"),
            storageAmountPerByte = runtime.exactInteger("storage_amount_per_byte"),
        )
    }

    override suspend fun sendTransaction(signedTransactionBase64: String): String? =
        outcomeHash(
            call(
                "send_tx",
                buildJsonObject {
                    put("signed_tx_base64", signedTransactionBase64)
                    put("wait_until", "INCLUDED")
                },
            )
        )

    override suspend fun getTransactionOutcome(
        hash: String,
        senderAccountId: String,
    ): NearTransactionOutcome {
        val result =
            call(
                "tx",
                buildJsonObject {
                    put("tx_hash", hash)
                    put("sender_account_id", senderAccountId)
                    put("wait_until", "FINAL")
                },
            )
        return NearTransactionOutcome(
            returnedHash = outcomeHash(result),
            finalExecutionStatus = (result["final_execution_status"] as? JsonPrimitive)?.content,
            status = result["status"],
        )
    }

    private suspend fun queryOrNull(unknownName: String, params: JsonObject): JsonObject? =
        try {
            call("query", params)
        } catch (e: NearRpcException) {
            if (e.name == unknownName) null else throw e
        }

    /** POSTs one JSON-RPC call and unwraps `result`, never a partial body. */
    private suspend fun call(method: String, params: JsonObject): JsonObject {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", method)
            put("method", method)
            put("params", params)
        }
        val text = httpClient.post(NEAR_RPC_URL) { setBody(body) }.bodyAsText()
        val envelope =
            try {
                json.parseToJsonElement(text) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: throw malformed(method, "returned a body that is not JSON")

        envelope["error"]?.let { throw rpcException(method, it) }

        return envelope["result"] as? JsonObject
            ?: throw malformed(method, "returned neither a result nor an error")
    }

    // `query` nests the handler's error name one level deeper (`cause.name`) than `tx` does.
    private fun rpcException(method: String, error: JsonElement): NearRpcException {
        val body = error as? JsonObject
        val name =
            (body?.get("cause") as? JsonObject)?.stringOrNull("name")
                ?: body?.stringOrNull("name")
                ?: "UNKNOWN_ERROR"
        val detail = body?.stringOrNull("data") ?: body?.stringOrNull("message") ?: error.toString()
        return NearRpcException(method, name, detail)
    }

    private fun outcomeHash(result: JsonObject): String? {
        val hash =
            (result["transaction"] as? JsonObject)?.stringOrNull("hash")
                ?: (result["transaction_outcome"] as? JsonObject)?.stringOrNull("id")
        return hash?.takeIf { it.isNotEmpty() }
    }

    private fun parameterCost(value: JsonElement?, label: String): NearFees.ParameterCost {
        val cost =
            value as? JsonObject
                ?: throw malformed(
                    "EXPERIMENTAL_protocol_config",
                    "is missing transaction_costs.$label",
                )
        return NearFees.ParameterCost(
            sendSir = cost.exactInteger("send_sir"),
            sendNotSir = cost.exactInteger("send_not_sir"),
            execution = cost.exactInteger("execution"),
        )
    }

    private fun publicKeyString(hexPublicKey: String): String {
        val key = hexPublicKey.hexToByteArrayOrNull()
        require(key != null && key.size == ED25519_PUBLIC_KEY_BYTES) {
            "$hexPublicKey is not a 32-byte Ed25519 public key"
        }
        return "ed25519:${Base58.encodeNoCheck(key)}"
    }

    private fun malformed(method: String, detail: String) =
        IllegalStateException("NEAR $method response $detail")

    companion object {
        const val NEAR_RPC_URL = "https://rpc.mainnet.fastnear.com"

        private const val UNKNOWN_ACCOUNT = "UNKNOWN_ACCOUNT"
        private const val UNKNOWN_ACCESS_KEY = "UNKNOWN_ACCESS_KEY"
        private const val FULL_ACCESS = "FullAccess"
        private const val BLOCK_HASH_BYTES = 32
        private const val ED25519_PUBLIC_KEY_BYTES = 32

        /**
         * Exact non-negative integer from a field that may arrive as a JSON number or a string.
         * Read from the primitive's text so an access-key nonce above 2^53 is never rounded.
         */
        internal fun JsonObject.exactInteger(key: String): BigInteger {
            val text =
                (this[key] as? JsonPrimitive)?.content
                    ?: throw IllegalStateException("NEAR response is missing $key")
            require(NEAR_UNSIGNED_DECIMAL.matches(text)) {
                "NEAR $key is not an unsigned decimal integer: $text"
            }
            return BigInteger(text)
        }

        private fun JsonObject.stringOrNull(key: String): String? =
            (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }
    }
}
