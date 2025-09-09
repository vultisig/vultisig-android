package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.DenomMetadata
import com.vultisig.wallet.data.api.models.GraphQLResponse
import com.vultisig.wallet.data.api.models.MetadataResponse
import com.vultisig.wallet.data.api.models.MetadatasResponse
import com.vultisig.wallet.data.api.models.TcyStakerResponse
import com.vultisig.wallet.data.api.models.ThorTcyBalancesResponseJson
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalanceResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosTransactionBroadcastResponse
import com.vultisig.wallet.data.api.models.cosmos.NativeTxFeeRune
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountResultJson
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountValue
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteError
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
        referralCode: String,
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

    suspend fun getTcyAutoCompoundAmount(address: String): String?

    suspend fun getPools(): List<ThorChainPoolJson>

    suspend fun getRujiMergeBalances(address: String): List<MergeAccount>
    suspend fun getRujiStakeBalance(address: String): RujiStakeBalances
    suspend fun existsReferralCode(code: String): Boolean
    suspend fun getReferralCodeInfo(code: String): ThorOwnerData
    suspend fun getReferralCodesByAddress(address: String): List<String>
    suspend fun getLastBlock(): Long
    suspend fun getDenomMetaFromLCD(denom: String): DenomMetadata?
    suspend fun getThorchainTokenPriceByContract(contract: String): VaultRedemptionResponseJson
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

    override suspend fun getTcyAutoCompoundAmount(address: String): String? {
        val url = "https://thornode.ninerealms.com/cosmos/bank/v1beta1/balances/$address"
        val response = httpClient.get(url)
        return if (!response.status.isSuccess()) {
            null
        } else {
            val stakingTcyAmount = response.body<ThorTcyBalancesResponseJson>().balances
                .find { it.denom == "x/staking-tcy" }
                ?.amount
            stakingTcyAmount
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
        referralCode: String,
    ): THORChainSwapQuoteDeserialized {
        val affiliateBPS = when {
            isAffiliate && referralCode.isNotEmpty() -> THORChainSwaps.AFFILIATE_FEE_RATE_PARTIAL
            isAffiliate -> THORChainSwaps.AFFILIATE_FEE_RATE
            else -> "0"
        }

        val response = httpClient
            .get("https://thornode.ninerealms.com/thorchain/quote/swap") {
                parameter("from_asset", fromAsset)
                parameter("to_asset", toAsset)
                parameter("amount", amount)
                parameter("destination", address)
                parameter("streaming_interval", interval)
                parameter("affiliate", THORChainSwaps.AFFILIATE_FEE_ADDRESS)
                parameter("affiliate_bps", affiliateBPS)
                if (referralCode.isNotEmpty() && isAffiliate) {
                    parameter("affiliate", referralCode)
                    parameter("affiliate_bps", THORChainSwaps.AFFILIATE_FEE_REFERRAL_RATE)
                }
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

        val response = httpClient.post("https://api.vultisig.com/ruji/api/graphql") {
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

        val response = httpClient.post("https://api.vultisig.com/ruji/api/graphql") {
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

    override suspend fun getReferralCodeInfo(code: String): ThorOwnerData {
        val response = httpClient
            .get("$NNRLM_URL/thorname/$code") {
                header(xClientID, xClientIDValue)
            }
        return response.bodyOrThrow<ThorOwnerData>()
    }

    override suspend fun getReferralCodesByAddress(address: String): List<String> {
        val response = httpClient
            .get("$MIDGARD_URL/thorname/rlookup/$address") {
                header(xClientID, xClientIDValue)
            }
        if (response.status.isSuccess()){
            return response.bodyOrThrow<List<String>>()
        }
        return emptyList()
    }

    override suspend fun getLastBlock(): Long {
        val response = httpClient
            .get("$NNRLM_URL/lastblock") {
                header(xClientID, xClientIDValue)
            }
        return response.bodyOrThrow<List<BlockNumber>>().firstOrNull()?.thorchain ?: 0L
    }

    override suspend fun getThorchainTokenPriceByContract(contract: String): VaultRedemptionResponseJson {
        val url = "https://thornode-mainnet-api.bryanlabs.net/cosmwasm/wasm/v1/contract/$contract/smart/eyJzdGF0dXMiOiB7fX0="

        return httpClient.get(url) {
            header(xClientID, xClientIDValue)
        }.bodyOrThrow<VaultRedemptionResponseJson>()
    }

    suspend fun getThorchainDenomMetadata(denom: String): DenomMetadata? {
        return try {
            val encodedDenom = java.net.URLEncoder.encode(
                denom,
                Charsets.UTF_8.name()
            )
            val response = httpClient
                .get("$THORNODE_BASE/cosmos/bank/v1beta1/denoms_metadata/$encodedDenom")
                .body<MetadataResponse>()
            response.metadata
        } catch (e: Exception) {
            Timber.e(
                e,
                "Failed to fetch denom metadata for $denom"
            )
            null
        }
    }

    suspend fun getFetchThorchainAllDenomMetadata(): List<DenomMetadata>? {
        return try {
            val response =
                httpClient.get("$THORNODE_BASE/cosmos/bank/v1beta1/denoms_metadata?pagination.limit=1000")
                    .body<MetadatasResponse>()
            response.metadatas
        } catch (e: Exception) {
            Timber.e(
                e,
                "Failed to fetch denom metadata list"
            )
            emptyList()
        }
    }

    override suspend fun getDenomMetaFromLCD(denom: String): DenomMetadata? =
        getThorchainDenomMetadata(denom)
            ?: getFetchThorchainAllDenomMetadata()?.find { it.base == denom }

    companion object {
        private const val NNRLM_URL = "https://thornode.ninerealms.com/thorchain"
        private const val THORNODE_BASE = "https://thornode.ninerealms.com"
        private const val MIDGARD_URL = "https://midgard.ninerealms.com/v2/"
    }
}

@Serializable
data class ThorOwnerData(
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
    val affiliateCollectorRune: String,
    @SerialName("aliases")
    val aliases: List<Aliases> = emptyList(),
) {
    @Serializable
    data class Aliases(val chain: String, val address: String)
}

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

@Serializable
data class BlockNumber(
    val thorchain: Long,
)

data class RujiStakeBalances(
    val stakeAmount: BigInteger = BigInteger.ZERO,
    val stakeTicker: String = "",
    val rewardsAmount: BigInteger = BigInteger.ZERO,
    val rewardsTicker: String = "USDC",
)

@Serializable
data class VaultRedemptionResponseJson(
    @SerialName("data")
    val data: VaultRedemptionDataJson
)

@Serializable
data class VaultRedemptionDataJson(
    @SerialName("redemption_rate")
    val redemptionRate: String,
    @SerialName("shares")
    val shares: String,
    @SerialName("nav")
    val nav: String,
    @SerialName("nav_per_share")
    val navPerShare: String,
)