package com.vultisig.wallet.data.blockchain.cosmos.qbtc

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

/**
 * Compares the network the QBTC node reports with [QbtcNetworkConfig.CHAIN_ID] before a signing
 * ceremony starts. After a chain-id change an outdated build would otherwise sign bytes the chain
 * rejects, and only find out at broadcast or proof time, after every device has signed.
 */
interface QbtcNetworkCheck {
    /**
     * True only when the node reports a network other than [QbtcNetworkConfig.CHAIN_ID]. An
     * unreachable node or an unreadable response returns false: that is not proof the build is
     * outdated, and the flow fails later on its own if the node really is down.
     */
    suspend fun isAppOutdated(): Boolean
}

/** Whether signing [this] payload depends on the QBTC chain id: a QBTC transaction or a claim. */
val KeysignPayload.isQbtcNetworkBound: Boolean
    get() = isQbtcClaim || coin.chain == Chain.Qbtc

internal class QbtcNetworkCheckImpl(
    private val httpClient: HttpClient,
    private val timeout: Duration,
) : QbtcNetworkCheck {

    @Inject constructor(httpClient: HttpClient) : this(httpClient, DEFAULT_TIMEOUT)

    override suspend fun isAppOutdated(): Boolean {
        val reported =
            try {
                withTimeout(timeout) {
                    httpClient
                        .get(
                            "${QbtcNetworkConfig.REST_URL}/cosmos/base/tendermint/v1beta1/node_info"
                        )
                        .bodyOrThrow<QbtcNodeInfoResponseJson>()
                        .defaultNodeInfo
                        .network
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "QBTC network check timed out; not blocking")
                return false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "QBTC network check failed; not blocking")
                return false
            }
        if (reported == QbtcNetworkConfig.CHAIN_ID) return false
        Timber.w(
            "QBTC node reports network %s, this build signs for %s",
            reported,
            QbtcNetworkConfig.CHAIN_ID,
        )
        return true
    }

    private companion object {
        val DEFAULT_TIMEOUT = 10.seconds
    }
}

@Serializable
private class QbtcNodeInfoResponseJson(
    @SerialName("default_node_info") val defaultNodeInfo: QbtcDefaultNodeInfoJson
)

@Serializable private class QbtcDefaultNodeInfoJson(val network: String)
