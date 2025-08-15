package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.GraphQLResponse
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteError
import com.vultisig.wallet.data.api.models.TcyStakerResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalanceResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosTransactionBroadcastResponse
import com.vultisig.wallet.data.api.models.cosmos.NativeTxFeeRune
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountResultJson
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountValue
import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.utils.ThorChainSwapQuoteResponseJsonSerializer
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface ThorChainApi {

    suspend fun getBalance(
        address: String,
    ): List<CosmosBalance>

    suspend fun getAccountNumber(
        address: String,
    ): THORChainAccountValue

    suspend fun getSwapQuotes(
        address: String,
        fromAsset: String,
        toAsset: String,
        amount: String,
        interval: String,
        isAffiliate: Boolean,
    ): THORChainSwapQuoteDeserialized

    suspend fun broadcastTransaction(tx: String): String?
    suspend fun getTHORChainNativeTransactionFee(): BigInteger
    suspend fun getTHORChainReferralFees(): NativeTxFeeRune

    suspend fun getNetworkChainId(): String

    suspend fun resolveName(
        name: String,
        chain: String
    ): String?

    suspend fun getTransactionDetail(tx: String): ThorChainTransactionJson
    suspend fun getTHORChainInboundAddresses(): List<THORChainInboundAddress>

    suspend fun getUnstakableTcyAmount(address: String): String?

    suspend fun getPools(): List<ThorChainPoolJson>

    suspend fun getRujiMergeBalances(address: String): List<MergeAccount>
    suspend fun getRujiStakeBalance(address: String): RujiStakeBalances
    suspend fun existsReferralCode(code: String): Boolean
}

internal class ThorChainApiImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val thorChainSwapQuoteResponseJsonSerializer: ThorChainSwapQuoteResponseJsonSerializer,
    private val json: Json,
) : ThorChainApi {

    override suspend fun getUnstakableTcyAmount(address: String): String? {
        return try {
            val response =
                httpClient.get("https://thornode.ninerealms.com/thorchain/tcy_staker/$address") {
                    header(xClientID, xClientIDValue)
                }
            if (!response.status.isSuccess()) {
                null
            } else {
                response.body<TcyStakerResponse>().unstakable
            }
        } catch (e: Exception) {
            // Exception occurred while fetching or parsing TCY staker data
            null
        }
    }

    private val xClientID = "X-Client-ID"
    private val xClientIDValue = "vultisig"

    override suspend fun getBalance(address: String): List<CosmosBalance> {
        val response = httpClient
            .get("https://thornode.ninerealms.com/cosmos/bank/v1beta1/balances/$address") {
                header(xClientID, xClientIDValue)
            }
        val resp = response.body<CosmosBalanceResponse>()
        return resp.balances ?: emptyList()
    }

    override suspend fun getSwapQuotes(
        address: String,
        fromAsset: String,
        toAsset: String,
        amount: String,
        interval: String,
        isAffiliate: Boolean,
    ): THORChainSwapQuoteDeserialized {
        val response = httpClient
            .get("https://thornode.ninerealms.com/thorchain/quote/swap") {
                parameter("from_asset", fromAsset)
                parameter("to_asset", toAsset)
                parameter("amount", amount)
                parameter("destination", address)
                parameter("streaming_interval", interval)
                parameter("affiliate", THORChainSwaps.AFFILIATE_FEE_ADDRESS)
                parameter(
                    "affiliate_bps",
                    if (isAffiliate) THORChainSwaps.AFFILIATE_FEE_RATE else "0"
                )
            }
        return try {
            json.decodeFromString(
                thorChainSwapQuoteResponseJsonSerializer,
                response.body<String>()
            )
        } catch (e: Exception) {
            Timber.tag("THORChainService")
                .e("Error deserializing THORChain swap quote: ${e.message}")
            THORChainSwapQuoteDeserialized.Error(
                THORChainSwapQuoteError(
                    HttpStatusCode.fromValue(response.status.value).description
                )
            )
        }
    }

    override suspend fun getAccountNumber(address: String): THORChainAccountValue {
        val response = httpClient
            .get("https://thornode.ninerealms.com/auth/accounts/$address") {
                header(xClientID, xClientIDValue)
            }
        return response.body<THORChainAccountResultJson>().result?.value
            ?: error("Field value is not found in the response")
    }

    override suspend fun getTHORChainNativeTransactionFee(): BigInteger {
        val response = httpClient.get("https://thornode.ninerealms.com/thorchain/network") {
            header(xClientID, xClientIDValue)
        }
        val content = response.body<NativeTxFeeRune>()
        return content.value?.let { BigInteger(it) } ?: 0.toBigInteger()
    }

    override suspend fun getTHORChainReferralFees(): NativeTxFeeRune {
        return httpClient.get("https://thornode.ninerealms.com/thorchain/network") {
            header(xClientID, xClientIDValue)
        }.bodyOrThrow<NativeTxFeeRune>()
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        try {
            val response = httpClient.post(Endpoints.THORCHAIN_BROADCAST_TX) {
                contentType(ContentType.Application.Json)
                header(xClientID, xClientIDValue)
                setBody(tx)
            }
            val responseRawString = response.bodyAsText()
            val result = response.body<CosmosTransactionBroadcastResponse>()

            val txResponse = result.txResponse
            if (txResponse?.code == 0 || txResponse?.code == 19) {
                return txResponse.txHash
            }
            throw Exception("Error broadcasting transaction: $responseRawString")
        } catch (e: Exception) {
            Timber.tag("THORChainService").e("Error broadcasting transaction: ${e.message}")
            throw e
        }
    }

    override suspend fun getNetworkChainId(): String =
        httpClient.get("https://rpc.ninerealms.com/status")
            .body<JsonObject>()["result"]
            ?.jsonObject
            ?.get("node_info")
            ?.jsonObject
            ?.get("network")
            ?.jsonPrimitive
            ?.content
            ?: error("Could't find network field in response for THORChain chain id")

    override suspend fun resolveName(
        name: String,
        chain: String,
    ): String? = httpClient
        .get("https://midgard.ninerealms.com/v2/thorname/lookup/$name")
        .body<ThorNameResponseJson>()
        .entries
        .find { it.chain == chain }
        ?.address

    override suspend fun getTransactionDetail(tx: String): ThorChainTransactionJson {
        val response = httpClient
            .get("https://thornode.ninerealms.com/cosmos/tx/v1beta1/txs/$tx")
        if (!response.status.isSuccess()) {
            //The  URL initially returns a 'not found' response but eventually
            // provides a successful response after some time
            if (response.status.equals(HttpStatusCode.NotFound))
                return ThorChainTransactionJson(
                    code = null,
                    codeSpace = null,
                    rawLog = response.bodyAsText()
                )
        } else
            return ThorChainTransactionJson(
                code = response.status.value,
                codeSpace = HttpStatusCode.fromValue(response.status.value).description,
                rawLog = response.bodyAsText()
            )
        return response.body()
    }

    override suspend fun getTHORChainInboundAddresses(): List<THORChainInboundAddress> {
        val response = httpClient
            .get("https://thornode.ninerealms.com/thorchain/inbound_addresses") {
                header(xClientID, xClientIDValue)
            }
        if (!response.status.isSuccess()) {
            // Error getting THORChain inbound addresses
            throw Exception("Error getting THORChain inbound addresses")
        }
        return response.body()
    }

    override suspend fun getPools(): List<ThorChainPoolJson> =
        httpClient
            .get("$NNRLM_URL/pools") {
                header(xClientID, xClientIDValue)
            }.throwIfUnsuccessful()
            .body()

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun getRujiMergeBalances(address: String): List<MergeAccount> {
        val accountBase64 = Base64.encode("Account:$address".toByteArray())

        val query = """
        {
          node(id:"$accountBase64") {
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
        """.trimIndent()

        val response = httpClient.post("https://api.rujira.network/api/graphql") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("query", query)
            })
        }.body<GraphQLResponse<RootData>>()

        if (!response.errors.isNullOrEmpty()) {
            throw Exception("Could not fetch balances: ${response.errors}")
        }

        return response.data?.node?.merge?.accounts ?: emptyList()
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun getRujiStakeBalance(address: String): RujiStakeBalances {
        val accountBase64 = Base64.encode("Account:$address".toByteArray())

        val query = """
        {
          node(id:"$accountBase64") {
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
              }
            }
          }
        }
        """.trimIndent()

        val response = httpClient.post("https://api.rujira.network/api/graphql") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("query", query)
            })
        }.body<GraphQLResponse<RootData>>()

        if (!response.errors.isNullOrEmpty()) {
            throw Exception("Could not fetch balances: ${response.errors}")
        }

        val stake =
            response.data?.node?.stakingV2?.firstOrNull() ?: return RujiStakeBalances()

        val stakeAmount = stake.bonded.amount.toBigIntegerOrNull() ?: BigInteger.ZERO
        val stakeTicker = stake.bonded.asset.metadata?.symbol ?: ""
        val rewardsAmount = stake.pendingRevenue?.amount?.toBigIntegerOrNull() ?: BigInteger.ZERO
        val rewardsTicker = stake.pendingRevenue?.asset?.metadata?.symbol ?: ""

        return RujiStakeBalances(
            stakeAmount = stakeAmount,
            stakeTicker = stakeTicker,
            rewardsAmount = rewardsAmount,
            rewardsTicker = rewardsTicker,
        )
    }

    override suspend fun existsReferralCode(code: String): Boolean {
        try {
            val response = httpClient
                .get("$NNRLM_URL/thorname/$code") {
                    header(xClientID, xClientIDValue)
                }
            return response.status.isSuccess()
        } catch (e: Exception) {
            Timber.tag("THORChainService").e("Error checking referral code: ${e.message}")
            return false
        }
    }

    companion object {
        private const val NNRLM_URL = "https://thornode.ninerealms.com/thorchain"
    }
}

@Serializable
private data class ThorOwnerData(
    @SerialName("name")
    val name: String,
    @SerialName("expire_block_height")
    val expireBlockHeight: Long,
    @SerialName("owner")
    val owner: String,
    @SerialName("preferred_asset")
    val preferredAsset: String,
    @SerialName("preferred_asset_swap_threshold_rune")
    val preferredAssetSwapThresholdRune: String,
    @SerialName("affiliate_collector_rune")
    val affilateCollectorRune: String,
    @SerialName("aliases")
    val aliases: List<String>?
)

@Serializable
private data class ThorNameEntryJson(
    @SerialName("chain")
    val chain: String,
    @SerialName("address")
    val address: String,
)

@Serializable
private data class ThorNameResponseJson(
    @SerialName("entries")
    val entries: List<ThorNameEntryJson>,
)

@Serializable
data class ThorChainTransactionJson(
    @SerialName("code")
    val code: Int?,
    @SerialName("codespace")
    val codeSpace: String?,
    @SerialName("raw_log")
    val rawLog: String,
)

@Serializable
data class THORChainInboundAddress(
    @SerialName("chain")
    val chain: String,
    @SerialName("address")
    val address: String,
    @SerialName("halted")
    val halted: Boolean,
    @SerialName("global_trading_paused")
    val globalTradingPaused: Boolean,
    @SerialName("chain_trading_paused")
    val chainTradingPaused: Boolean,
    @SerialName("chain_lp_actions_paused")
    val chainLPActionsPaused: Boolean,
    @SerialName("gas_rate")
    val gasRate: String,
    @SerialName("gas_rate_units")
    val gasRateUnits: String,
)

@Serializable
data class ThorChainPoolJson(
    // formatted as THOR.TCY
    @SerialName("asset")
    val asset: String,
    // asset price in usd with 8 decimals
    @Contextual
    @SerialName("asset_tor_price")
    val assetTorPrice: BigInteger,
)

@Serializable
data class RootData(
    val node: AccountNode?
)

@Serializable
data class AccountNode(
    val merge: MergeInfo?,
    val stakingV2: List<StakingV2?>?,
)

@Serializable
data class MergeInfo(
    val accounts: List<MergeAccount>,
)

@Serializable
data class MergeAccount(
    val pool: Pool?,
    val size: Size?,
    val shares: String?
)

@Serializable
data class Pool(
    val mergeAsset: MergeAsset?
)

@Serializable
data class MergeAsset(
    val metadata: Metadata?
)

@Serializable
data class Metadata(
    val symbol: String?
)

@Serializable
data class Size(
    val amount: String?
)

@Serializable
data class StakingV2(
    val account: String,
    val bonded: Bonded,
    val pendingRevenue: PendingRevenue?
)

@Serializable
data class Bonded(
    val amount: String,
    val asset: Asset,
)

@Serializable
data class PendingRevenue(
    val amount: String,
    val asset: Asset,
)

@Serializable
data class Asset(
    val metadata: Metadata? = null,
)

data class RujiStakeBalances(
    val stakeAmount: BigInteger = BigInteger.ZERO,
    val stakeTicker: String = "",
    val rewardsAmount: BigInteger = BigInteger.ZERO,
    val rewardsTicker: String = "USDC",
)