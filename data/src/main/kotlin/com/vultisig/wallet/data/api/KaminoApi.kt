package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Read access to Kamino's Earn (kVault) REST API.
 *
 * Public and unauthenticated — no key, no partner header. Every numeric field arrives as a decimal
 * string and is kept that way here: the wire format is exact, and parsing belongs with the code
 * that knows each field's scale rather than with the transport.
 */
interface KaminoApi {

    /** Immutable-ish vault state: display name, mints and per-vault minimums. */
    suspend fun getVaultState(vaultAddress: String): KaminoVaultStateJson

    /** Live economics: APY over several windows, share price and the token's fiat price. */
    suspend fun getVaultMetrics(vaultAddress: String): KaminoVaultMetricsJson

    /** Every kVault position [walletAddress] holds. Empty when the wallet holds none. */
    suspend fun getUserPositions(walletAddress: String): List<KaminoUserPositionJson>

    /** Realised and unrealised profit for one wallet in one vault. */
    suspend fun getPositionPnl(walletAddress: String, vaultAddress: String): KaminoPnlJson

    /**
     * Builds an unsigned deposit transaction, base64-encoded.
     *
     * [amount] is a **decimal** token amount ("0.1234"), not base units — the one place Kamino
     * wants decimals on the way in. The returned transaction embeds a recent blockhash with roughly
     * a minute of life, so it must be fetched immediately before signing rather than held.
     */
    suspend fun buildDeposit(walletAddress: String, vaultAddress: String, amount: String): String

    /** Builds an unsigned withdraw transaction. [amount] is decimal, as for [buildDeposit]. */
    suspend fun buildWithdraw(walletAddress: String, vaultAddress: String, amount: String): String
}

internal class KaminoApiImpl @Inject constructor(private val httpClient: HttpClient) : KaminoApi {

    override suspend fun getVaultState(vaultAddress: String): KaminoVaultStateJson =
        httpClient
            .get(KAMINO_URL) { url { appendPathSegments("kvaults", "vaults", vaultAddress) } }
            .bodyOrThrow()

    override suspend fun getVaultMetrics(vaultAddress: String): KaminoVaultMetricsJson =
        httpClient
            .get(KAMINO_URL) {
                url { appendPathSegments("kvaults", "vaults", vaultAddress, "metrics") }
            }
            .bodyOrThrow()

    override suspend fun getUserPositions(walletAddress: String): List<KaminoUserPositionJson> =
        httpClient
            .get(KAMINO_URL) {
                url { appendPathSegments("kvaults", "users", walletAddress, "positions") }
            }
            .bodyOrThrow()

    override suspend fun getPositionPnl(
        walletAddress: String,
        vaultAddress: String,
    ): KaminoPnlJson =
        httpClient
            .get(KAMINO_URL) {
                url {
                    appendPathSegments(
                        "kvaults",
                        "users",
                        walletAddress,
                        "vaults",
                        vaultAddress,
                        "pnl",
                    )
                }
            }
            .bodyOrThrow()

    override suspend fun buildDeposit(
        walletAddress: String,
        vaultAddress: String,
        amount: String,
    ): String = buildAction("deposit", walletAddress, vaultAddress, amount)

    override suspend fun buildWithdraw(
        walletAddress: String,
        vaultAddress: String,
        amount: String,
    ): String = buildAction("withdraw", walletAddress, vaultAddress, amount)

    private suspend fun buildAction(
        action: String,
        walletAddress: String,
        vaultAddress: String,
        amount: String,
    ): String =
        httpClient
            .post(KAMINO_URL) {
                url { appendPathSegments("ktx", "kvault", action) }
                contentType(ContentType.Application.Json)
                setBody(
                    KaminoActionRequestJson(
                        wallet = walletAddress,
                        kvault = vaultAddress,
                        amount = amount,
                    )
                )
            }
            .bodyOrThrow<KaminoActionResponseJson>()
            .transaction

    private companion object {
        const val KAMINO_URL = "https://api.kamino.finance"
    }
}

@Serializable
internal data class KaminoActionRequestJson(
    @SerialName("wallet") val wallet: String,
    @SerialName("kvault") val kvault: String,
    /** Decimal, not base units. */
    @SerialName("amount") val amount: String,
)

@Serializable
internal data class KaminoActionResponseJson(
    /** Base64-encoded unsigned transaction with a recent blockhash already baked in. */
    @SerialName("transaction") val transaction: String
)

@Serializable
data class KaminoVaultStateJson(
    @SerialName("address") val address: String,
    @SerialName("state") val state: State,
) {
    @Serializable
    data class State(
        /** Curator-set display name, e.g. "Steakhouse USDC". */
        @SerialName("name") val name: String? = null,
        @SerialName("tokenMint") val tokenMint: String,
        @SerialName("tokenMintDecimals") val tokenDecimals: Int,
        @SerialName("sharesMint") val sharesMint: String,
        @SerialName("sharesMintDecimals") val sharesDecimals: Int,
        /** In the token's base units, not decimal — 100000 is 0.1 USDC at 6 decimals. */
        @SerialName("minDepositAmount") val minDepositAmount: String? = null,
        @SerialName("minWithdrawAmount") val minWithdrawAmount: String? = null,
        @SerialName("performanceFeeBps") val performanceFeeBps: Int? = null,
        @SerialName("managementFeeBps") val managementFeeBps: Int? = null,
    )
}

@Serializable
data class KaminoVaultMetricsJson(
    /**
     * A fraction, not a percentage: "0.039967" is 3.9967%. The 30-day window is the one shown,
     * being long enough not to swing on a single day's utilisation.
     */
    @SerialName("apy30d") val apy30d: String? = null,
    /** Token amount one share converts to. Use for the position's token balance. */
    @SerialName("tokensPerShare") val tokensPerShare: String? = null,
    /**
     * Fiat value of one share, equal to [tokensPerShare] × [tokenPrice]. Use for the position's
     * fiat value directly rather than re-deriving it.
     */
    @SerialName("sharePrice") val sharePrice: String? = null,
    /** The underlying token's price in USD. */
    @SerialName("tokenPrice") val tokenPrice: String? = null,
)

@Serializable
data class KaminoUserPositionJson(
    @SerialName("vaultAddress") val vaultAddress: String,
    /** Decimal share quantity staked in the vault's farm — where a deposit puts them. */
    @SerialName("stakedShares") val stakedShares: String? = null,
    /** Decimal share quantity sitting in the wallet's own share account. */
    @SerialName("unstakedShares") val unstakedShares: String? = null,
    /** Decimal share quantity, staked and unstaked combined. */
    @SerialName("totalShares") val totalShares: String? = null,
)

@Serializable
data class KaminoPnlJson(
    @SerialName("totalPnl") val totalPnl: Amounts? = null,
    @SerialName("totalCostBasis") val totalCostBasis: Amounts? = null,
) {
    @Serializable
    data class Amounts(
        @SerialName("token") val token: String? = null,
        @SerialName("sol") val sol: String? = null,
        @SerialName("usd") val usd: String? = null,
    )
}
