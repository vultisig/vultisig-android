package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.errors.CosmosBroadcastException
import com.vultisig.wallet.data.api.errors.parseCosmosBroadcastResponse
import com.vultisig.wallet.data.api.models.DenomMetadata
import com.vultisig.wallet.data.api.models.MetadataResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalanceResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosGovProposal
import com.vultisig.wallet.data.api.models.cosmos.CosmosGovProposalsResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosGovTallyResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosGovTallyResult
import com.vultisig.wallet.data.api.models.cosmos.CosmosGovVote
import com.vultisig.wallet.data.api.models.cosmos.CosmosGovVoteResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosIbcDenomTraceDenomTraceJson
import com.vultisig.wallet.data.api.models.cosmos.CosmosIbcDenomTraceJson
import com.vultisig.wallet.data.api.models.cosmos.CosmosSimulateResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosTHORChainAccountResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosTxStatusJson
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountValue
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CustomRpcSupportedChains
import com.vultisig.wallet.data.repositories.CustomRpcRepository
import com.vultisig.wallet.data.utils.CosmosThorChainResponseSerializer
import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.encodeBase64
import java.net.URLEncoder
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber

interface CosmosApi {
    suspend fun getBalance(address: String): List<CosmosBalance>

    suspend fun getAccountNumber(address: String): THORChainAccountValue

    suspend fun broadcastTransaction(tx: String): String?

    /**
     * Simulates [txBytes] (the base64 `tx_bytes` of an unsigned transaction) via
     * `/cosmos/tx/v1beta1/simulate` and returns the reported `gas_info.gas_used`, or `null` when
     * simulation is unavailable so the caller can fall back to a static per-chain gas limit. Fails
     * closed on any error.
     */
    suspend fun simulate(txBytes: String): Long?

    suspend fun getWasmTokenBalance(address: String, contractAddress: String): CosmosBalance

    suspend fun getIbcDenomTraces(contractAddress: String): CosmosIbcDenomTraceDenomTraceJson

    /**
     * Chain-declared metadata for the given bank denom, or `null` when the chain has no entry or
     * the request fails. Used by token auto-discovery to resolve display ticker and decimals.
     */
    suspend fun getDenomMetadata(denom: String): DenomMetadata?

    suspend fun getLatestBlock(): String

    suspend fun getTxStatus(txHash: String): CosmosTxStatusJson?

    /**
     * Governance proposals filtered by `proposal_status` (2 = voting period, 3 = passed, 4 =
     * rejected). Used by the QBTC governance screen. Returns an empty list when the chain has none
     * for that status.
     */
    suspend fun getGovProposals(status: Int): List<CosmosGovProposal>

    /**
     * The voter's current vote on [proposalId], or `null` when they haven't voted (404) or the
     * lookup fails. Cosmos prunes votes once a proposal's voting period ends, so closed proposals
     * typically return `null`.
     */
    suspend fun getGovVote(proposalId: String, voter: String): CosmosGovVote?

    /**
     * Live tally for [proposalId] from the `/tally` endpoint. cosmos-sdk leaves
     * `final_tally_result` at zero until a proposal closes, so active proposals must read the
     * running counts here. Null on failure.
     */
    suspend fun getGovTally(proposalId: String): CosmosGovTallyResult?

    /**
     * Terra Classic's live proportional burn-tax rate from the `x/tax` module
     * (`/terra/tax/v1beta1/params` → `params.burn_tax_rate`, currently 0.5%). Returns the raw
     * decimal string, or `null` on any failure so the caller can fail closed to a conservative
     * fallback rather than signing an under-funded tx. Only meaningful for [Chain.TerraClassic].
     */
    suspend fun getTerraClassicBurnTaxRate(): String?
}

interface CosmosApiFactory {
    fun createCosmosApi(chain: Chain): CosmosApi
}

internal class CosmosApiFactoryImp
@Inject
constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val cosmosThorChainResponseSerializer: CosmosThorChainResponseSerializer,
    private val customRpcRepository: CustomRpcRepository,
) : CosmosApiFactory {
    override fun createCosmosApi(chain: Chain): CosmosApi {
        val defaultApiUrl = CustomRpcDefaultEndpoint.cosmosUrl(chain)

        // App-wide custom RPC override (#4787): only honor overrides for chains in the supported
        // set so an excluded chain (e.g. Qbtc, a Vultisig proxy with no real-node equivalent)
        // always resolves to its default. Unset chains keep their default endpoint.
        val apiUrl =
            if (CustomRpcSupportedChains.isSupported(chain)) {
                customRpcRepository.urlFor(chain) ?: defaultApiUrl
            } else {
                defaultApiUrl
            }

        return CosmosApiImp(httpClient, apiUrl, json, cosmosThorChainResponseSerializer)
    }
}

internal class CosmosApiImp(
    private val httpClient: HttpClient,
    private val rpcEndpoint: String,
    private val json: Json,
    private val cosmosThorChainResponseSerializer: CosmosThorChainResponseSerializer,
) : CosmosApi {
    override suspend fun getBalance(address: String): List<CosmosBalance> {
        val response = httpClient.get("$rpcEndpoint/cosmos/bank/v1beta1/balances/$address")
        val resp = response.bodyOrThrow<CosmosBalanceResponse>()
        return resp.balances ?: emptyList()
    }

    override suspend fun getAccountNumber(address: String): THORChainAccountValue {
        val response = httpClient.get("$rpcEndpoint/cosmos/auth/v1beta1/accounts/$address") {}
        val responseBody = response.bodyAsText()
        val decodedResponse = json.decodeFromString(cosmosThorChainResponseSerializer, responseBody)

        Timber.d("getAccountNumber: $responseBody")
        return when (decodedResponse) {
            is CosmosTHORChainAccountResponse.Error ->
                THORChainAccountValue(accountNumber = "0", sequence = "0", address = null)

            is CosmosTHORChainAccountResponse.Success ->
                decodedResponse.response.account ?: error("Error getting account")
        }
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        try {
            val response =
                httpClient.post("$rpcEndpoint/cosmos/tx/v1beta1/txs") {
                    contentType(ContentType.Application.Json)
                    setBody(tx)
                }
            return parseCosmosBroadcastResponse(
                rawBody = response.bodyAsText(),
                logTag = "CosmosApiService",
                json = json,
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (e !is CosmosBroadcastException) {
                Timber.tag("CosmosApiService").e(e, "Error broadcasting transaction")
            }
            throw e
        }
    }

    override suspend fun simulate(txBytes: String): Long? {
        return try {
            httpClient
                .post("$rpcEndpoint/cosmos/tx/v1beta1/simulate") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("tx_bytes", txBytes) })
                }
                .bodyOrThrow<CosmosSimulateResponse>()
                .gasInfo
                ?.gasUsed
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cosmos simulate failed; falling back to static gas limit")
            null
        }
    }

    override suspend fun getWasmTokenBalance(
        address: String,
        contractAddress: String,
    ): CosmosBalance {
        val payload = "{\"balance\":{\"address\":\"$address\"}}".encodeBase64()

        return CosmosBalance(
            denom = contractAddress,
            amount =
                httpClient
                    .get("$rpcEndpoint/cosmwasm/wasm/v1/contract/$contractAddress/smart/$payload")
                    .bodyOrThrow<JsonObject>()["data"]
                    ?.jsonObject
                    ?.get("balance")
                    ?.jsonPrimitive
                    ?.content ?: "0",
        )
    }

    override suspend fun getIbcDenomTraces(
        contractAddress: String
    ): CosmosIbcDenomTraceDenomTraceJson {
        val hash = contractAddress.removePrefix("ibc/")
        return httpClient
            .get("$rpcEndpoint/ibc/apps/transfer/v1/denom_traces/$hash")
            .bodyOrThrow<CosmosIbcDenomTraceJson>()
            .denomTrace!!
    }

    override suspend fun getDenomMetadata(denom: String): DenomMetadata? {
        return try {
            val encoded = URLEncoder.encode(denom, Charsets.UTF_8.name())
            httpClient
                .get("$rpcEndpoint/cosmos/bank/v1beta1/denoms_metadata/$encoded")
                .bodyOrThrow<MetadataResponse>()
                .metadata
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            if (e.httpStatusCode == HttpStatusCode.NotFound.value) {
                Timber.d("No denom metadata for %s (expected for non-standard denoms)", denom)
            } else {
                Timber.e(e, "Failed to fetch denom metadata for %s", denom)
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch denom metadata for %s", denom)
            null
        }
    }

    override suspend fun getLatestBlock(): String {
        return httpClient
            .get("$rpcEndpoint/cosmos/base/tendermint/v1beta1/blocks/latest")
            .bodyOrThrow<JsonObject>()
            .jsonObject["block"]
            ?.jsonObject
            ?.get("header")
            ?.jsonObject
            ?.get("height")
            ?.jsonPrimitive
            ?.content ?: "0"
    }

    override suspend fun getTxStatus(txHash: String): CosmosTxStatusJson? {
        val response = httpClient.get("$rpcEndpoint/cosmos/tx/v1beta1/txs/$txHash")
        if (response.status.value == 404) return null
        return response.bodyOrThrow<CosmosTxStatusJson>()
    }

    override suspend fun getGovProposals(status: Int): List<CosmosGovProposal> {
        return httpClient
            .get("$rpcEndpoint/cosmos/gov/v1/proposals") {
                parameter("proposal_status", status)
                // Newest-first single page; QBTC has very few proposals per status, so paging past
                // the first 20 is a follow-up if the chain ever grows beyond that.
                parameter("pagination.limit", 20)
                parameter("pagination.count_total", true)
                parameter("pagination.reverse", true)
            }
            .bodyOrThrow<CosmosGovProposalsResponse>()
            .proposals
    }

    override suspend fun getGovVote(proposalId: String, voter: String): CosmosGovVote? {
        val encodedProposalId = URLEncoder.encode(proposalId, Charsets.UTF_8.name())
        val encodedVoter = URLEncoder.encode(voter, Charsets.UTF_8.name())
        return try {
            httpClient
                .get("$rpcEndpoint/cosmos/gov/v1/proposals/$encodedProposalId/votes/$encodedVoter")
                .bodyOrThrow<CosmosGovVoteResponse>()
                .vote
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            // 404 is the expected "no vote cast" / pruned-after-tally response.
            if (e.httpStatusCode != HttpStatusCode.NotFound.value) {
                Timber.w(e, "Failed to fetch gov vote for proposal %s", proposalId)
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Unexpected error fetching gov vote for proposal %s", proposalId)
            null
        }
    }

    override suspend fun getGovTally(proposalId: String): CosmosGovTallyResult? {
        val encoded = URLEncoder.encode(proposalId, Charsets.UTF_8.name())
        return try {
            httpClient
                .get("$rpcEndpoint/cosmos/gov/v1/proposals/$encoded/tally")
                .bodyOrThrow<CosmosGovTallyResponse>()
                .tally
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch gov tally for proposal %s", proposalId)
            null
        }
    }

    override suspend fun getTerraClassicBurnTaxRate(): String? {
        // The proportional burn tax lives in the x/tax module, not the legacy treasury module
        // (whose tax_rate is 0). Fail closed (return null) so the caller applies its conservative
        // fallback rate instead of signing an under-funded tx.
        return try {
            httpClient
                .get("$rpcEndpoint/terra/tax/v1beta1/params")
                .bodyOrThrow<JsonObject>()["params"]
                ?.jsonObject
                ?.get("burn_tax_rate")
                ?.jsonPrimitive
                ?.content
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch Terra Classic burn_tax_rate; falling back to default")
            null
        }
    }
}
