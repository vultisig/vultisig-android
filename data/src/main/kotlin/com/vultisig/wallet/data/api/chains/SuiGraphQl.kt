package com.vultisig.wallet.data.api.chains

import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * Sui GraphQL RPC endpoints, tried in order.
 *
 * Sui is retiring JSON-RPC — shutdown on Foundation mainnet full nodes began the week of 2026-07-27
 * and code removal lands mid-October 2026 — so every Sui read, simulation and broadcast goes
 * through GraphQL RPC instead. GraphQL was chosen over the other supported replacement, gRPC,
 * because it is a plain JSON POST that the existing Ktor client already speaks; gRPC would pull a
 * grpc-okhttp/protoc-gen-grpc stack into a module whose protobuf plugin only generates
 * kotlinx-serialization classes today.
 *
 * Only the Foundation host is listed: it is the one public Sui GraphQL endpoint that answers. The
 * Vultisig proxy (`api.vultisig.com/sui`) is deliberately absent — it still speaks JSON-RPC, so it
 * is subject to the same decommission and cannot serve as a fallback until its backend moves too.
 * Adding a second host once one exists is a one-line change here; [SuiGraphQlTransport] already
 * walks the whole list.
 */
internal val SUI_GRAPHQL_ENDPOINTS = listOf("https://graphql.mainnet.sui.io/graphql")

/** The GraphQL request envelope: a query document plus its variables. */
@Serializable
internal data class SuiGraphQlRequest(
    @SerialName("query") val query: String,
    @SerialName("variables") val variables: JsonObject,
)

@Serializable
internal data class SuiGraphQlResponse(
    @SerialName("data") val data: JsonObject? = null,
    @SerialName("errors") val errors: List<SuiGraphQlError>? = null,
)

@Serializable
internal data class SuiGraphQlError(
    @SerialName("message") val message: String = UNKNOWN_GRAPHQL_ERROR,
    @SerialName("extensions") val extensions: SuiGraphQlErrorExtensions? = null,
)

@Serializable
internal data class SuiGraphQlErrorExtensions(@SerialName("code") val code: String? = null)

private const val UNKNOWN_GRAPHQL_ERROR = "unknown error"

/**
 * A Sui GraphQL application error — the node parsed the request and refused it (bad address,
 * undecodable transaction, indexer outage).
 *
 * Extends [IllegalStateException] because the JSON-RPC implementation this replaces reported the
 * same conditions through `error(...)`; callers that already catch [IllegalStateException] keep
 * working unchanged. [SuiStatusProvider][com.vultisig.wallet.data.api.txstatus.SuiStatusProvider]
 * catches the subtype specifically so a persistent node failure is reported immediately instead of
 * being masked as a retryable pending poll.
 */
internal class SuiRpcException(val errorMessage: String, val code: String? = null) :
    IllegalStateException(
        if (code == null) "Sui GraphQL error: $errorMessage"
        else "Sui GraphQL error ($code): $errorMessage"
    )

/**
 * Posts GraphQL documents to Sui, failing over across [endpoints].
 *
 * Failover covers transport-level faults only — an unreachable host, a TLS failure, a 5xx. A
 * populated `errors` array is the node's considered answer to a well-formed request, so it is
 * raised immediately rather than replayed against every remaining host, which would only delay the
 * same message.
 *
 * Retrying a broadcast across hosts is safe: Sui identifies a transaction by the digest of its
 * signed bytes, so re-submitting identical bytes is idempotent — the node returns the existing
 * effects rather than executing twice. That makes failover most valuable exactly where it is
 * riskiest elsewhere: a broadcast whose response was lost in transit still lands.
 */
internal class SuiGraphQlTransport(
    private val http: HttpClient,
    private val endpoints: List<String> = SUI_GRAPHQL_ENDPOINTS,
) {
    init {
        require(endpoints.isNotEmpty()) { "SuiGraphQlTransport requires at least one endpoint" }
    }

    /** Runs [document] and returns its `data` object, or throws [SuiRpcException] on refusal. */
    suspend fun query(
        document: String,
        variables: JsonObject = JsonObject(emptyMap()),
    ): JsonObject {
        var lastTransportFailure: Exception? = null

        for (endpoint in endpoints) {
            val response =
                try {
                    http
                        .post(endpoint) { setBody(SuiGraphQlRequest(document, variables)) }
                        .bodyOrThrow<SuiGraphQlResponse>()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Sui GraphQL endpoint %s failed, trying the next", endpoint)
                    lastTransportFailure = e
                    continue
                }

            val errors = response.errors
            if (!errors.isNullOrEmpty()) {
                throw SuiRpcException(
                    errorMessage = errors.joinToString("; ") { it.message },
                    code = errors.firstNotNullOfOrNull { it.extensions?.code },
                )
            }

            // GraphQL answers HTTP 200 even when it refuses the request, so an absent `data` with
            // an absent `errors` is a malformed upstream response — never an empty result.
            return response.data
                ?: throw SuiRpcException("returned neither data nor errors (malformed response)")
        }

        throw lastTransportFailure ?: SuiRpcException("no Sui GraphQL endpoint configured")
    }
}
