package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.RpcResponse
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import timber.log.Timber

/**
 * Outcome of probing a custom RPC endpoint for liveness (and, where possible, network identity).
 */
sealed interface RpcHealthResult {
    /**
     * The endpoint responded successfully. [networkVerified] is true only when the probe could
     * confirm the endpoint serves the expected chain (EVM `eth_chainId` match); for chains without
     * a cheap identity check it is a liveness-only result.
     */
    data class Reachable(val latencyMs: Long, val networkVerified: Boolean) : RpcHealthResult

    /** The endpoint did not respond, timed out, or returned a transport/HTTP error. */
    data object Unreachable : RpcHealthResult

    /** The endpoint is alive but serves a different chain than expected (EVM chainId mismatch). */
    data object WrongChain : RpcHealthResult

    /** The endpoint responded but the payload could not be understood as the expected RPC shape. */
    data object InvalidResponse : RpcHealthResult
}

/** Probes a candidate custom RPC URL so the detail screen can show a "Test" result (#4787). */
interface RpcHealthProbe {
    suspend fun probe(chain: Chain, url: String): RpcHealthResult
}

internal class RpcHealthProbeImpl @Inject constructor(private val httpClient: HttpClient) :
    RpcHealthProbe {

    override suspend fun probe(chain: Chain, url: String): RpcHealthResult {
        val endpoint = url.trim()
        if (endpoint.isBlank()) return RpcHealthResult.Unreachable
        return try {
            withTimeout(PROBE_TIMEOUT_MS) {
                when (chain.standard) {
                    TokenStandard.EVM -> probeEvm(chain, endpoint)
                    TokenStandard.COSMOS -> probeCosmos(endpoint)
                    else -> probeReachability(endpoint)
                }
            }
        } catch (_: TimeoutCancellationException) {
            // A probe timeout means the endpoint is too slow to be usable; surface it as a result
            // rather than cancelling the caller (it's a CancellationException subtype).
            RpcHealthResult.Unreachable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Custom RPC probe failed for %s", chain.id)
            RpcHealthResult.Unreachable
        }
    }

    private suspend fun probeEvm(chain: Chain, url: String): RpcHealthResult {
        val started = System.currentTimeMillis()
        val response =
            httpClient
                .post(url) {
                    setBody(RpcPayload(method = "eth_chainId", params = buildJsonArray {}))
                }
                .bodyOrThrow<RpcResponse>()
        val latency = System.currentTimeMillis() - started

        val reportedChainId =
            response.result?.removePrefix("0x")?.toLongOrNull(16)
                ?: return RpcHealthResult.InvalidResponse

        val expected = evmChainId(chain)
        return when {
            expected == null -> RpcHealthResult.Reachable(latency, networkVerified = false)
            reportedChainId == expected ->
                RpcHealthResult.Reachable(latency, networkVerified = true)
            else -> RpcHealthResult.WrongChain
        }
    }

    private suspend fun probeCosmos(url: String): RpcHealthResult {
        val started = System.currentTimeMillis()
        val response = httpClient.get("${url.trimEnd('/')}/$COSMOS_NODE_INFO_PATH")
        val latency = System.currentTimeMillis() - started
        // The LCD node_info endpoint confirms liveness. We don't verify the reported network id
        // against the chain here, so this stays a liveness-only result.
        return if (response.status.isSuccess()) {
            RpcHealthResult.Reachable(latency, networkVerified = false)
        } else {
            RpcHealthResult.Unreachable
        }
    }

    private suspend fun probeReachability(url: String): RpcHealthResult {
        val started = System.currentTimeMillis()
        val response = httpClient.get(url)
        val latency = System.currentTimeMillis() - started
        return if (response.status.isSuccess()) {
            RpcHealthResult.Reachable(latency, networkVerified = false)
        } else {
            RpcHealthResult.Unreachable
        }
    }

    private fun evmChainId(chain: Chain): Long? =
        when (chain) {
            Chain.Ethereum -> 1
            Chain.BscChain -> 56
            Chain.Avalanche -> 43114
            Chain.Polygon -> 137
            Chain.Optimism -> 10
            Chain.CronosChain -> 25
            Chain.Blast -> 81457
            Chain.Base -> 8453
            Chain.Arbitrum -> 42161
            Chain.ZkSync -> 324
            Chain.Mantle -> 5000
            Chain.Sei -> 1329
            Chain.Robinhood -> 4663
            Chain.Hyperliquid -> 999
            else -> null
        }

    private companion object {
        const val PROBE_TIMEOUT_MS = 8_000L
        const val COSMOS_NODE_INFO_PATH = "cosmos/base/tendermint/v1beta1/node_info"
    }
}
