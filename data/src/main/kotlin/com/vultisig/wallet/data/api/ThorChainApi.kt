package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.DenomMetadata
import com.vultisig.wallet.data.api.models.GraphQLResponse
import com.vultisig.wallet.data.api.models.MetadataResponse
import com.vultisig.wallet.data.api.models.MetadatasResponse
import com.vultisig.wallet.data.api.models.TcyStakerResponse
import com.vultisig.wallet.data.api.models.ThorTcyBalancesResponseJson
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalanceResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosEnvelopedTxResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosTransactionBroadcastResponse
import com.vultisig.wallet.data.api.models.cosmos.NativeTxFeeRune
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountResultJson
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountValue
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteError
import com.vultisig.wallet.data.api.models.thorchain.BlockNumber
import com.vultisig.wallet.data.api.models.thorchain.BondedNodesResponse
import com.vultisig.wallet.data.api.models.thorchain.ChurnEntry
import com.vultisig.wallet.data.api.models.thorchain.MergeAccount
import com.vultisig.wallet.data.api.models.thorchain.MidgardHealth
import com.vultisig.wallet.data.api.models.thorchain.MidgardNetworkData
import com.vultisig.wallet.data.api.models.thorchain.NodeDetailsResponse
import com.vultisig.wallet.data.api.models.thorchain.RootData
import com.vultisig.wallet.data.api.models.thorchain.RujiStakeBalances
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.api.models.thorchain.TcyDistribution
import com.vultisig.wallet.data.api.models.thorchain.TcyModuleBalanceResponse
import com.vultisig.wallet.data.api.models.thorchain.TcyStakeResponse
import com.vultisig.wallet.data.api.models.thorchain.TcyStakersResponse
import com.vultisig.wallet.data.api.models.thorchain.TcyUserDistributionsResponse
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolJson
import com.vultisig.wallet.data.api.models.thorchain.ThorChainTransactionJson
import com.vultisig.wallet.data.api.models.thorchain.ThorNameResponseJson
import com.vultisig.wallet.data.api.models.thorchain.ThorOwnerData
import com.vultisig.wallet.data.api.models.thorchain.ThorchainConstantsResponse
import com.vultisig.wallet.data.api.models.thorchain.VaultRedemptionResponseJson
import com.vultisig.wallet.data.chains.helpers.ThorChainAffiliateHelper
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.utils.ThorChainSwapQuoteResponseJsonSerializer
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.math.BigInteger
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber

interface ThorChainApi {

    suspend fun getBalance(address: String): List<CosmosBalance>

    suspend fun getAccountNumber(address: String): THORChainAccountValue

    suspend fun getSwapQuotes(
        address: String,
        fromAsset: String,
        toAsset: String,
        amount: String,
        interval: String,
        referralCode: String,
        bpsDiscount: Int,
    ): THORChainSwapQuoteDeserialized

    suspend fun broadcastTransaction(tx: String): String?

    suspend fun getTHORChainNativeTransactionFee(): BigInteger

    suspend fun getTHORChainReferralFees(): NativeTxFeeRune

    suspend fun getNetworkChainId(): String

    suspend fun resolveName(name: String, chain: String): String?

    suspend fun getTransactionDetail(tx: String): ThorChainTransactionJson

    suspend fun getTHORChainInboundAddresses(): List<THORChainInboundAddress>

    suspend fun getDenomMetaFromLCD(denom: String): DenomMetadata?

    suspend fun getPools(): List<ThorChainPoolJson>

    suspend fun getConstants(): ThorchainConstantsResponse

    // Referral feature
    suspend fun existsReferralCode(code: String): Boolean

    suspend fun getReferralCodeInfo(code: String): ThorOwnerData

    suspend fun getReferralCodesByAddress(address: String): List<String>

    suspend fun getLastBlock(): Long

    suspend fun getThorchainTokenPriceByContract(contract: String): VaultRedemptionResponseJson

    // Ruji Merge & Stake
    suspend fun getRujiMergeBalances(address: String): List<MergeAccount>

    suspend fun getRujiStakeBalance(address: String): RujiStakeBalances

    // Bonded nodes API
    suspend fun getBondedNodes(address: String): BondedNodesResponse

    suspend fun getNodeDetails(nodeAddress: String): NodeDetailsResponse

    suspend fun getChurns(): List<ChurnEntry>

    suspend fun getChurnInterval(): Long

    suspend fun getMidgardNetworkData(): MidgardNetworkData

    suspend fun getMidgardHealth(): MidgardHealth

    // TCY Staking API
    suspend fun getUnstakableTcyAmount(address: String): String?

    suspend fun getTcyAutoCompoundAmount(address: String): String?

    suspend fun fetchTcyStakedAmount(address: String): TcyStakeResponse

    suspend fun fetchTcyDistributions(limit: Int): List<TcyDistribution>

    suspend fun fetchTcyUserDistributions(address: String): TcyUserDistributionsResponse

    suspend fun fetchTcyModuleBalance(): TcyModuleBalanceResponse

    suspend fun fetchTcyStakers(): TcyStakersResponse
}

internal class ThorChainApiImpl
@Inject
constructor(
    private val httpClient: HttpClient,
    private val thorChainSwapQuoteResponseJsonSerializer: ThorChainSwapQuoteResponseJsonSerializer,
    private val json: Json,
) : ThorChainApi {

    override suspend fun getUnstakableTcyAmount(address: String): String? {
        return try {
            val response =
                httpClient.get("$THORNODE_BASE/thorchain/tcy_staker/$address") {
                    header(xClientID, xClientIDValue)
                }
            if (!response.status.isSuccess()) {
                null
            } else {
                response.bodyOrThrow<TcyStakerResponse>().unstakable
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching TCY staker data for %s", address)
            null
        }
    }

    override suspend fun getTcyAutoCompoundAmount(address: String): String? {
        val url = "$THORNODE_BASE/cosmos/bank/v1beta1/balances/$address"
        val response = httpClient.get(url)
        return if (!response.status.isSuccess()) {
            null
        } else {
            val stakingTcyAmount =
                response
                    .bodyOrThrow<ThorTcyBalancesResponseJson>()
                    .balances
                    .find { it.denom == "x/staking-tcy" }
                    ?.amount
            stakingTcyAmount
        }
    }

    private val xClientID = "X-Client-ID"
    private val xClientIDValue = "vultisig"

    override suspend fun getBalance(address: String): List<CosmosBalance> {
        val response =
            httpClient.get("$THORNODE_BASE/cosmos/bank/v1beta1/balances/$address") {
                header(xClientID, xClientIDValue)
            }
        val resp = response.bodyOrThrow<CosmosBalanceResponse>()
        return resp.balances ?: emptyList()
    }

    override suspend fun getSwapQuotes(
        address: String,
        fromAsset: String,
        toAsset: String,
        amount: String,
        interval: String,
        referralCode: String,
        bpsDiscount: Int,
    ): THORChainSwapQuoteDeserialized {
        val affiliateParams =
            ThorChainAffiliateHelper.buildAffiliateParams(
                referralCode = referralCode,
                discountBps = bpsDiscount,
            )

        val response =
            httpClient.get("$THORNODE_BASE/thorchain/quote/swap") {
                parameter("from_asset", fromAsset)
                parameter("to_asset", toAsset)
                parameter("amount", amount)
                parameter("destination", address)
                parameter("streaming_interval", interval)
                if (affiliateParams.isNotEmpty()) {
                    affiliateParams.forEach { (key, value) ->
                        when (key) {
                            "affiliate" -> parameter("affiliate", value)
                            "affiliate_bps" -> parameter("affiliate_bps", value)
                        }
                    }
                }
            }
        return try {
            json.decodeFromString(
                thorChainSwapQuoteResponseJsonSerializer,
                response.bodyOrThrow<String>(),
            )
        } catch (e: Exception) {
            Timber.e(e, "Error deserializing THORChain swap quote")
            THORChainSwapQuoteDeserialized.Error(
                THORChainSwapQuoteError(HttpStatusCode.fromValue(response.status.value).description)
            )
        }
    }

    override suspend fun getAccountNumber(address: String): THORChainAccountValue {
        val response =
            httpClient.get("$THORNODE_BASE/auth/accounts/$address") {
                header(xClientID, xClientIDValue)
            }
        return response.bodyOrThrow<THORChainAccountResultJson>().result?.value
            ?: error("Field value is not found in the response")
    }

    override suspend fun getTHORChainNativeTransactionFee(): BigInteger {
        val response =
            httpClient.get("$THORNODE_BASE/thorchain/network") { header(xClientID, xClientIDValue) }
        val content = response.bodyOrThrow<NativeTxFeeRune>()
        return content.value?.let { BigInteger(it) } ?: 0.toBigInteger()
    }

    override suspend fun getTHORChainReferralFees(): NativeTxFeeRune {
        return httpClient
            .get("$THORNODE_BASE/thorchain/network") { header(xClientID, xClientIDValue) }
            .bodyOrThrow<NativeTxFeeRune>()
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        try {
            val response =
                httpClient.post(Endpoints.THORCHAIN_BROADCAST_TX) {
                    contentType(ContentType.Application.Json)
                    header(xClientID, xClientIDValue)
                    setBody(tx)
                }
            val responseRawString = response.bodyAsText()
            val result =
                json.decodeFromString<CosmosTransactionBroadcastResponse>(responseRawString)

            val txResponse =
                result.txResponse ?: error("Error broadcasting transaction: $responseRawString")
            if (txResponse.code == 0 || txResponse.code == ERR_TX_IN_MEMPOOL_CACHE) {
                return txResponse.txHash
            }
            error("Error broadcasting transaction: $responseRawString")
        } catch (e: Exception) {
            Timber.tag("THORChainService").e(e, "Error broadcasting transaction")
            throw e
        }
    }

    override suspend fun getNetworkChainId(): String =
        httpClient
            .get("$THORCHAIN_RPC_URL/status")
            .bodyOrThrow<JsonObject>()["result"]
            ?.jsonObject
            ?.get("node_info")
            ?.jsonObject
            ?.get("network")
            ?.jsonPrimitive
            ?.content ?: error("Could't find network field in response for THORChain chain id")

    override suspend fun resolveName(name: String, chain: String): String? =
        httpClient
            .get("$MIDGARD_URL/thorname/lookup/$name")
            .bodyOrThrow<ThorNameResponseJson>()
            .entries
            .find { it.chain == chain }
            ?.address

    override suspend fun getTransactionDetail(tx: String): ThorChainTransactionJson {
        val response = httpClient.get("$THORNODE_BASE/cosmos/tx/v1beta1/txs/$tx")
        if (!response.status.isSuccess()) {
            // The URL initially returns a 'not found' response but eventually
            // provides a successful response after some time
            if (response.status == HttpStatusCode.NotFound)
                return ThorChainTransactionJson(
                    code = null,
                    codeSpace = null,
                    rawLog = response.bodyAsText(),
                )
            error(
                "getTransactionDetail failed: status=${response.status}, body=${response.bodyAsText()}"
            )
        }
        val envelope = response.bodyOrThrow<CosmosEnvelopedTxResponse>()
        return ThorChainTransactionJson(
            code = envelope.txResponse.code,
            codeSpace = envelope.txResponse.codeSpace,
            rawLog = envelope.txResponse.rawLog ?: "",
        )
    }

    override suspend fun getTHORChainInboundAddresses(): List<THORChainInboundAddress> =
        httpClient
            .get("$THORNODE_BASE/thorchain/inbound_addresses") { header(xClientID, xClientIDValue) }
            .bodyOrThrow()

    override suspend fun getPools(): List<ThorChainPoolJson> =
        httpClient
            .get("$THORNODE_BASE/thorchain/pools") { header(xClientID, xClientIDValue) }
            .bodyOrThrow()

    override suspend fun getConstants(): ThorchainConstantsResponse {
        val response =
            httpClient.get("$THORNODE_BASE/thorchain/constants") {
                header(xClientID, xClientIDValue)
            }
        return response.bodyOrThrow<ThorchainConstantsResponse>()
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun getRujiMergeBalances(address: String): List<MergeAccount> {
        val accountBase64 = Base64.encode("Account:$address".toByteArray())
        val query = RUJI_MERGE_QUERY.format(accountBase64)

        val response =
            httpClient
                .post(RUJI_GRAPHQL_URL) {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("query", query) })
                }
                .bodyOrThrow<GraphQLResponse<RootData>>()

        if (!response.errors.isNullOrEmpty()) {
            throw Exception("Could not fetch balances: ${response.errors}")
        }

        return response.data?.node?.merge?.accounts ?: emptyList()
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun getRujiStakeBalance(address: String): RujiStakeBalances {
        val accountBase64 = Base64.encode("Account:$address".toByteArray())
        val query = RUJI_STAKE_QUERY.format(accountBase64)

        val httpResponse =
            httpClient.post(RUJI_GRAPHQL_URL) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("query", query) })
            }
        if (!httpResponse.status.isSuccess()) {
            throw Exception("Could not fetch balances: status ${httpResponse.status.value}")
        }

        val response = httpResponse.bodyOrThrow<GraphQLResponse<RootData>>()
        if (!response.errors.isNullOrEmpty()) {
            throw Exception("Could not fetch balances: ${response.errors}")
        }

        val stake = response.data?.node?.stakingV2?.firstOrNull() ?: return RujiStakeBalances()

        val stakeAmount = stake.bonded.amount.toBigIntegerOrNull() ?: BigInteger.ZERO
        val stakeTicker = stake.bonded.asset.metadata?.symbol ?: ""
        val rewardsAmount = stake.pendingRevenue?.amount?.toBigIntegerOrNull() ?: BigInteger.ZERO
        val rewardsTicker = stake.pendingRevenue?.asset?.metadata?.symbol ?: "USDC"
        val apr =
            runCatching { (stake.pool?.summary?.apr?.value ?: "0.0").toDouble() }.getOrDefault(0.0)

        return RujiStakeBalances(
            stakeAmount = stakeAmount,
            stakeTicker = stakeTicker,
            rewardsAmount = rewardsAmount,
            rewardsTicker = rewardsTicker,
            apr = apr,
        )
    }

    override suspend fun existsReferralCode(code: String): Boolean {
        val response =
            httpClient.get("$THORNODE_BASE/thorchain/thorname/$code") {
                header(xClientID, xClientIDValue)
            }

        if (response.status == HttpStatusCode.NotFound) {
            return false
        }

        if (
            response.status == HttpStatusCode.InternalServerError &&
                response.bodyAsText().contains("fail to fetch THORName")
        ) {
            return false
        }

        val thorName = response.bodyOrThrow<ThorOwnerData>()

        return thorName.aliases.any { alias ->
            alias.chain.equals("THOR", ignoreCase = true) && alias.address.isNotBlank()
        }
    }

    override suspend fun getReferralCodeInfo(code: String): ThorOwnerData {
        val response =
            httpClient.get("$THORNODE_BASE/thorchain/thorname/$code") {
                header(xClientID, xClientIDValue)
            }
        return response.bodyOrThrow<ThorOwnerData>()
    }

    override suspend fun getReferralCodesByAddress(address: String): List<String> {
        val response =
            httpClient.get("$MIDGARD_URL/thorname/rlookup/$address") {
                header(xClientID, xClientIDValue)
            }
        if (response.status.isSuccess()) {
            return response.bodyOrThrow<List<String>>()
        }
        return emptyList()
    }

    override suspend fun getLastBlock(): Long {
        val response =
            httpClient.get("$THORNODE_BASE/thorchain/lastblock") {
                header(xClientID, xClientIDValue)
            }
        return response.bodyOrThrow<List<BlockNumber>>().firstOrNull()?.thorchain ?: 0L
    }

    override suspend fun getThorchainTokenPriceByContract(
        contract: String
    ): VaultRedemptionResponseJson {
        val url = "$IBS_TEAM_URL/api/cosmwasm/wasm/v1/contract/$contract/smart/eyJzdGF0dXMiOiB7fX0="
        return httpClient
            .get(url) { header(xClientID, xClientIDValue) }
            .bodyOrThrow<VaultRedemptionResponseJson>()
    }

    private suspend fun getThorchainDenomMetadata(denom: String): DenomMetadata? {
        return try {
            val encodedDenom = java.net.URLEncoder.encode(denom, Charsets.UTF_8.name())
            val response =
                httpClient
                    .get("$THORNODE_BASE/cosmos/bank/v1beta1/denoms_metadata/$encodedDenom")
                    .bodyOrThrow<MetadataResponse>()
            response.metadata
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch denom metadata for %s", denom)
            null
        }
    }

    private suspend fun getFetchThorchainAllDenomMetadata(): List<DenomMetadata>? {
        return try {
            val response =
                httpClient
                    .get("$THORNODE_BASE/cosmos/bank/v1beta1/denoms_metadata?pagination.limit=1000")
                    .bodyOrThrow<MetadatasResponse>()
            response.metadatas
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch denom metadata list")
            emptyList()
        }
    }

    override suspend fun getDenomMetaFromLCD(denom: String): DenomMetadata? =
        getThorchainDenomMetadata(denom)
            ?: getFetchThorchainAllDenomMetadata()?.find { it.base == denom }

    override suspend fun getBondedNodes(address: String): BondedNodesResponse {
        val url = "$MIDGARD_URL/bonds/$address"

        return httpClient
            .get(url) { header(xClientID, xClientIDValue) }
            .bodyOrThrow<BondedNodesResponse>()
    }

    override suspend fun getNodeDetails(nodeAddress: String): NodeDetailsResponse {
        val url = "$THORNODE_BASE/thorchain/node/$nodeAddress"

        return httpClient
            .get(url) { header(xClientID, xClientIDValue) }
            .bodyOrThrow<NodeDetailsResponse>()
    }

    override suspend fun getChurns(): List<ChurnEntry> {
        val url = "$MIDGARD_URL/churns"
        return httpClient
            .get(url) { header(xClientID, xClientIDValue) }
            .bodyOrThrow<List<ChurnEntry>>()
    }

    override suspend fun getChurnInterval(): Long {
        val url = "$THORNODE_BASE/thorchain/mimir/key/CHURNINTERVAL"
        return httpClient.get(url) { header(xClientID, xClientIDValue) }.bodyOrThrow<Long>()
    }

    override suspend fun getMidgardNetworkData(): MidgardNetworkData {
        val url = "$MIDGARD_URL/network"

        return httpClient
            .get(url) { header(xClientID, xClientIDValue) }
            .bodyOrThrow<MidgardNetworkData>()
    }

    override suspend fun getMidgardHealth(): MidgardHealth {
        val url = "$MIDGARD_URL/health"

        return httpClient
            .get(url) { header(xClientID, xClientIDValue) }
            .bodyOrThrow<MidgardHealth>()
    }

    override suspend fun fetchTcyStakedAmount(address: String): TcyStakeResponse {
        val httpResponse =
            httpClient.get("$THORNODE_BASE/thorchain/tcy_staker/$address") {
                header(xClientID, xClientIDValue)
            }
        return if (
            httpResponse.status.value == 400 &&
                httpResponse.bodyAsText().contains("TCYStaker doesn't exist", ignoreCase = true)
        ) {
            TcyStakeResponse(address = address, amount = "0")
        } else if (!httpResponse.status.isSuccess()) {
            Timber.e("FetchTcyStakedAmount %s", httpResponse.status)
            error("Error Fetching Tcy Staked: status=${httpResponse.status}")
        } else {
            httpResponse.bodyOrThrow<TcyStakeResponse>()
        }
    }

    override suspend fun fetchTcyDistributions(limit: Int): List<TcyDistribution> {
        return httpClient
            .get("$THORNODE_BASE/thorchain/tcy_distributions") {
                parameter("limit", limit)
                header(xClientID, xClientIDValue)
            }
            .bodyOrThrow<List<TcyDistribution>>()
    }

    override suspend fun fetchTcyUserDistributions(address: String): TcyUserDistributionsResponse {
        return httpClient
            .get("$MIDGARD_URL/tcy/distribution/$address") { header(xClientID, xClientIDValue) }
            .bodyOrThrow<TcyUserDistributionsResponse>()
    }

    override suspend fun fetchTcyModuleBalance(): TcyModuleBalanceResponse {
        return httpClient
            .get("$THORNODE_BASE/thorchain/balance/module/tcy_stake") {
                header(xClientID, xClientIDValue)
            }
            .bodyOrThrow<TcyModuleBalanceResponse>()
    }

    override suspend fun fetchTcyStakers(): TcyStakersResponse {
        return httpClient
            .get("$THORNODE_BASE/thorchain/tcy_stakers") { header(xClientID, xClientIDValue) }
            .bodyOrThrow<TcyStakersResponse>()
    }

    companion object {
        private const val THORNODE_BASE = "https://thornode.thorchain.network"
        private const val MIDGARD_URL = "https://midgard.thorchain.network/v2"
        private const val THORCHAIN_RPC_URL = "https://rpc.thorchain.network"
        private const val IBS_TEAM_URL = "https://thorchain.ibs.team"
        private const val RUJI_GRAPHQL_URL = "https://api.vultisig.com/ruji/api/graphql"

        private const val RUJI_MERGE_QUERY =
            """
        {
          node(id:"%s") {
            ... on Account {
              merge {
                accounts {
                  pool {
                    mergeAsset {
                      metadata {
                        symbol
                      }
                    }
                  }
                  size {
                    amount
                  }
                  shares
                }
              }
            }
          }
        }
        """

        private const val RUJI_STAKE_QUERY =
            """
        {
          node(id:"%s") {
            ... on Account {
              stakingV2 {
                account
                bonded {
                  amount
                  asset {
                    metadata {
                      symbol
                    }
                  }
                }
                pendingRevenue {
                  amount
                  asset {
                    metadata {
                      symbol
                    }
                  }
                }
                pool {
                  summary {
                    apr {
                      value
                    }
                  }
                }
              }
            }
          }
        }
        """
        // Cosmos SDK ErrTxInMempoolCache (code 19): tx already accepted into mempool, treat as
        // success.
        // https://github.com/cosmos/cosmos-sdk/blob/v0.50.0/types/errors/errors.go#L79
        private const val ERR_TX_IN_MEMPOOL_CACHE = 19
    }
}
