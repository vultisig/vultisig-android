package com.vultisig.wallet.data.blockchain.solana.staking

import com.vultisig.wallet.data.blockchain.cosmos.staking.KeybaseAvatarService
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Concrete [ValidatorMetadataProvider] backed by Stakewiz (`https://api.stakewiz.com`). The
 * `/validators` endpoint returns the full validator set in one response, so a single fetch enriches
 * an arbitrary batch of vote pubkeys; results are cached per vote pubkey for [ttlMillis] (~1h). A
 * failed or rate-limited fetch yields whatever is already cached (possibly nothing) — the call
 * never throws, so callers degrade to on-chain-only display. Mirrors the iOS
 * `StakewizValidatorMetadataProvider` (vultisig-ios #4660).
 *
 * The logo prefers Stakewiz's own bundled `image` URL (already in the bulk response), falling back
 * to a [KeybaseAvatarService] lookup ONLY when no image is present — which keeps the per-validator
 * keybase.io round-trip (an N+1) off the hot path for the common case. The remaining Keybase
 * fallbacks are resolved concurrently across the requested set rather than awaited one at a time.
 */
@Singleton
internal class StakewizValidatorMetadataProvider
@Inject
constructor(private val httpClient: HttpClient, private val avatarService: KeybaseAvatarService) :
    ValidatorMetadataProvider {

    /**
     * Clock + TTL are `internal var` so tests can pin them; not in the `@Inject` constructor
     * because Dagger ignores Kotlin default-valued params.
     */
    internal var clock: () -> Long = { System.currentTimeMillis() }
    internal var ttlMillis: Long = 60L * 60L * 1000L

    private data class CachedEntry(val value: ValidatorMetadata, val fetchedAt: Long)

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CachedEntry>()

    /**
     * Coalesces concurrent batch fetches into a single in-flight request — the `/validators`
     * endpoint is the same call regardless of which pubkeys are asked for, so there is at most one
     * outstanding fetch.
     */
    private var inFlight: CompletableDeferred<List<StakewizValidatorJson>>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun metadata(votePubkeys: List<String>): Map<String, ValidatorMetadata> {
        val requested = votePubkeys.filter { it.isNotEmpty() }.toSet()
        if (requested.isEmpty()) return emptyMap()

        // Serve everything from cache when every requested pubkey is fresh.
        val cachedHits =
            mutex
                .withLock {
                    requested.mapNotNull { pubkey ->
                        cache[pubkey]?.takeIf { isFresh(it.fetchedAt) }?.let { pubkey to it.value }
                    }
                }
                .toMap()
        if (cachedHits.size == requested.size) return cachedHits

        val rows = fetchValidators()
        // Outage — return whatever we already had cached for the request.
        if (rows.isEmpty()) return cachedHits

        val byVotePubkey =
            rows.filter { it.voteIdentity.isNotEmpty() }.associateBy { it.voteIdentity }
        val pending = requested.filter { it !in cachedHits }.mapNotNull { byVotePubkey[it] }

        // Resolve the missing rows CONCURRENTLY. Most map to a bundled Stakewiz `image` (no
        // network), but the Keybase fallback that some still need is a per-validator HTTP call —
        // running them in parallel keeps a cold picker open from serializing those round-trips.
        val resolved = coroutineScope {
            pending.map { row -> async { row.voteIdentity to map(row) } }.awaitAll()
        }

        val fetchedAt = clock()
        return mutex.withLock {
            resolved.forEach { (pubkey, metadata) ->
                cache[pubkey] = CachedEntry(metadata, fetchedAt)
            }
            cachedHits + resolved.toMap()
        }
    }

    private suspend fun map(row: StakewizValidatorJson): ValidatorMetadata {
        val name = row.name?.trim()?.takeIf { it.isNotEmpty() }
        return ValidatorMetadata(
            name = name,
            logoUrl = resolveLogo(row),
            apyEstimate = apyFraction(row.apyEstimate),
            score = row.wizScore?.let { Math.round(it).toInt() },
        )
    }

    /**
     * Prefer Stakewiz's bundled `image` URL — it ships in the same bulk response, so it costs
     * nothing. Only when a row has no usable image do we fall back to a Keybase identity lookup (a
     * per-validator HTTP call), which keeps the avatar N+1 off the hot path for the common case.
     */
    private suspend fun resolveLogo(row: StakewizValidatorJson): String? {
        row.image
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                return it
            }
        val identity = row.keybase?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return avatarService.avatarUrl(identity)
    }

    private suspend fun fetchValidators(): List<StakewizValidatorJson> {
        var owns = false
        val deferred =
            mutex.withLock {
                inFlight?.let {
                    return@withLock it
                }
                val newDeferred = CompletableDeferred<List<StakewizValidatorJson>>()
                inFlight = newDeferred
                owns = true
                newDeferred
            }

        if (owns) {
            val result =
                try {
                    fetch()
                } catch (e: CancellationException) {
                    withContext(NonCancellable) { mutex.withLock { inFlight = null } }
                    deferred.complete(emptyList())
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Stakewiz validators fetch failed — degrading to on-chain only")
                    emptyList()
                }
            mutex.withLock { inFlight = null }
            deferred.complete(result)
        }
        return deferred.await()
    }

    private suspend fun fetch(): List<StakewizValidatorJson> {
        val raw =
            httpClient
                .get(STAKEWIZ_VALIDATORS_URL) { accept(ContentType.Application.Json) }
                .bodyAsText()
        return runCatching { json.decodeFromString<List<StakewizValidatorJson>>(raw) }
            .getOrDefault(emptyList())
    }

    private fun isFresh(fetchedAt: Long): Boolean = clock() - fetchedAt < ttlMillis

    /**
     * Stakewiz reports `apy_estimate` as a percentage (e.g. `5.72`). Store it as a fraction to
     * match [ValidatorMetadata.apyEstimate] (e.g. `0.0572`). Non-positive / non-finite values
     * collapse to null.
     */
    private fun apyFraction(percent: Double?): BigDecimal? {
        if (percent == null || !percent.isFinite() || percent <= 0.0) return null
        return BigDecimal.valueOf(percent).divide(BigDecimal(100))
    }

    companion object {
        private const val STAKEWIZ_VALIDATORS_URL = "https://api.stakewiz.com/validators"
    }
}

/**
 * A single Stakewiz `/validators` row. Every enrichment field is optional so a missing key on the
 * wire collapses to "no enrichment" rather than throwing.
 */
@Serializable
internal data class StakewizValidatorJson(
    @SerialName("vote_identity") val voteIdentity: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("keybase") val keybase: String? = null,
    @SerialName("apy_estimate") val apyEstimate: Double? = null,
    @SerialName("commission") val commission: Int? = null,
    @SerialName("wiz_score") val wizScore: Double? = null,
    @SerialName("delinquent") val delinquent: Boolean? = null,
)
