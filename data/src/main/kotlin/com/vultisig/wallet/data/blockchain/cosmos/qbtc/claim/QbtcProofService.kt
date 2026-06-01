package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Client for the QBTC proof service. `generateProof` is long-running (up to
 * [QbtcClaimConfig.PROOF_SERVICE_TIMEOUT_MS]) — callers must surface progress UI.
 */
interface QbtcProofService {
    /** True only when the prover reports healthy AND its setup is loaded. */
    suspend fun isHealthy(): Boolean

    /** Generates the PLONK proof and, with `broadcast = true`, returns the on-chain tx hash. */
    suspend fun generateProof(request: ClaimProofRequest): ClaimProofResponse
}

internal class QbtcProofServiceImpl
@Inject
constructor(@QbtcProofHttpClient private val httpClient: HttpClient) : QbtcProofService {

    override suspend fun isHealthy(): Boolean =
        try {
            httpClient
                .get("${Endpoints.QBTC_PROOF_SERVICE_URL}/health")
                .bodyOrThrow<ProofServiceHealth>()
                .isHealthy
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A health probe is intentionally any-failure-means-unhealthy; log so a swallowed
            // failure stays diagnosable rather than silent.
            Timber.w(e, "QBTC proof service health check failed")
            false
        }

    override suspend fun generateProof(request: ClaimProofRequest): ClaimProofResponse =
        httpClient
            .post("${Endpoints.QBTC_PROOF_SERVICE_URL}/prove") {
                // This dedicated client has no DefaultRequest plugin, so set the JSON content
                // type explicitly — otherwise the request body isn't serialized as JSON.
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .bodyOrThrow()
}
