package com.vultisig.wallet.data.repositories

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * A positive moment that can contribute towards asking for a Play store review.
 *
 * The associated values are stable identities, not display values: persisting the namespaced
 * identity makes every producer safe to call again after a re-entry, a recomposition, or a process
 * restart.
 */
sealed interface AppReviewEvent {

    /** Namespaced identity used to deduplicate, or null when the identity is blank. */
    val storageId: String?

    data class ConfirmedOutboundTransaction(val txHash: String) : AppReviewEvent {
        override val storageId: String?
            get() = storageId("outbound", txHash)
    }

    data class ConfirmedIncomingFunds(val coinId: String, val balance: BigInteger) :
        AppReviewEvent {
        override val storageId: String?
            get() = storageId("incoming", "$coinId:$balance")
    }

    data class VaultBackupCompleted(val vaultId: String) : AppReviewEvent {
        override val storageId: String?
            get() = storageId("backup", vaultId)
    }

    data class VaultRestoreCompleted(val vaultId: String) : AppReviewEvent {
        override val storageId: String?
            get() = storageId("restore", vaultId)
    }

    data class DevicePairingCompleted(val sessionId: String) : AppReviewEvent {
        override val storageId: String?
            get() = storageId("pairing", sessionId)
    }
}

private fun storageId(namespace: String, identity: String): String? =
    identity.trim().takeIf { it.isNotEmpty() }?.let { "$namespace:$it" }

/**
 * Throttles the Play in-app review prompt so it is only requested at a genuine satisfaction peak.
 *
 * Play enforces its own opaque quota on top of this and the flow may no-op silently, so the app
 * never learns whether the dialog appeared — a claim is therefore spent when the prompt is
 * *requested*, not when the user rates.
 *
 * Recording an event and presenting the prompt are deliberately separate steps: a device pairing,
 * for instance, counts towards eligibility while navigating straight into keygen, which is not a
 * surface the store card may land on.
 */
interface InAppReviewRepository {

    /**
     * Emits once per [requestPromptEvaluation], for the app-level host to evaluate the policy
     * against whatever is currently on screen.
     */
    val promptOpportunities: Flow<Unit>

    /** Records [event] once for its stable identity. True when it counted for the first time. */
    suspend fun record(event: AppReviewEvent): Boolean

    /**
     * Records a freshly observed confirmed balance for [coinId] and reports whether the increase
     * since the previous sighting qualifies as "funds arrived".
     *
     * The first sighting of a coin never qualifies: an existing balance is not an arrival, and only
     * a delta measured against a balance this device already saw can be one.
     */
    suspend fun onConfirmedBalanceObserved(
        coinId: String,
        balance: BigInteger,
        decimals: Int,
        fiatPricePerUnit: BigDecimal?,
        isNativeToken: Boolean,
    ): Boolean

    /** Signals that a visible surface may safely present the store card. */
    suspend fun requestPromptEvaluation()

    /**
     * Evaluates the policy and atomically consumes this app version's single ask. A true result has
     * already been persisted, so the caller must launch the Play flow.
     */
    suspend fun claimReviewPrompt(): Boolean
}

/**
 * Records [event] and, only when it counted for the first time, offers the host a chance to ask.
 *
 * Spending an ask off an already-counted event is how a re-entered success screen — or a flow that
 * merely looks like one — burns a version's single opportunity without a new milestone behind it.
 */
suspend fun InAppReviewRepository.recordAndOfferPrompt(event: AppReviewEvent) {
    if (record(event)) {
        requestPromptEvaluation()
    }
}

/**
 * Records a vault backup milestone for only the first vault in a bulk backup operation. One tap is
 * one milestone: a bulk export of five vaults is a single good moment, not five.
 */
suspend fun InAppReviewRepository.recordFirstVaultBackupCompleted(vaults: List<*>) {
    vaults.firstOrNull()?.let { vault ->
        val vaultId =
            when (vault) {
                is com.vultisig.wallet.data.models.Vault -> vault.id
                else -> return@let
            }
        recordAndOfferPrompt(AppReviewEvent.VaultBackupCompleted(vaultId))
    }
}

internal class InAppReviewRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val appDataStore: AppDataStore,
) : InAppReviewRepository {

    /** Minimal clock seam so tests can drive the gates without sleeping. */
    fun interface Clock {
        fun nowMillis(): Long
    }

    /** Install-time seam so tests can age an install without a real package. */
    fun interface InstallTime {
        fun firstInstallMillis(): Long
    }

    /** App-version seam so tests can ship a new release without a real package. */
    fun interface AppVersion {
        fun name(): String?
    }

    @VisibleForTesting internal var clock: Clock = Clock { System.currentTimeMillis() }

    /**
     * Read from the package manager rather than a stored timestamp so users who installed before
     * this gate existed are correctly treated as long-standing rather than brand new. Falls back to
     * "installed right now", which fails closed: an unreadable install date suppresses the prompt
     * instead of showing it to a first-run user.
     */
    @VisibleForTesting
    internal var installTime: InstallTime = InstallTime {
        runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            }
            .getOrElse { error ->
                Timber.w(error, "Unable to read first install time; suppressing the review prompt")
                clock.nowMillis()
            }
    }

    /**
     * The marketing version, mirroring iOS's `CFBundleShortVersionString` gate. A build number is
     * deliberately not part of it: every internal build would otherwise mint a fresh ask. An
     * unreadable version fails closed, since a version-scoped claim cannot be recorded without one.
     */
    @VisibleForTesting
    internal var appVersion: AppVersion = AppVersion {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrElse { error ->
                Timber.w(error, "Unable to read the app version; suppressing the review prompt")
                null
            }
    }

    // CONFLATED so an opportunity raised while nothing is collecting — the host only claims on a
    // resumed, visible surface — waits for the next collector instead of being dropped, and so a
    // burst of successes queues one evaluation rather than several.
    private val opportunities =
        Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val promptOpportunities: Flow<Unit> = opportunities.receiveAsFlow()

    override suspend fun record(event: AppReviewEvent): Boolean =
        withContext(Dispatchers.IO) {
            val eventId = event.storageId ?: return@withContext false
            var counted = false
            appDataStore.editData { preferences -> counted = countEvent(preferences, eventId) }
            counted
        }

    override suspend fun onConfirmedBalanceObserved(
        coinId: String,
        balance: BigInteger,
        decimals: Int,
        fiatPricePerUnit: BigDecimal?,
        isNativeToken: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val id = coinId.trim()
            if (id.isEmpty() || balance.signum() < 0) return@withContext false

            var counted = false
            // One transaction for read-compare-write: two concurrent refreshes of the same coin
            // would otherwise both see the old balance and both count the same arrival.
            appDataStore.editData { preferences ->
                val previous = lastSeenBalances(preferences)[id]?.toBigIntegerOrNull()
                rememberBalance(preferences, id, balance)
                if (previous == null || balance <= previous) return@editData
                val arrival = balance - previous
                if (!isMeaningfulArrival(arrival, decimals, fiatPricePerUnit, isNativeToken)) {
                    return@editData
                }
                val eventId =
                    AppReviewEvent.ConfirmedIncomingFunds(id, balance).storageId ?: return@editData
                counted = countEvent(preferences, eventId)
            }
            counted
        }

    override suspend fun requestPromptEvaluation() {
        opportunities.send(Unit)
    }

    override suspend fun claimReviewPrompt(): Boolean =
        withContext(Dispatchers.IO) {
            val now = clock.nowMillis()
            val version = appVersion.name()?.trim()?.takeIf { it.isNotEmpty() }

            var claimed = false
            // The whole evaluate-then-consume sequence runs inside one DataStore transaction, so
            // two opportunities racing each other cannot both spend the same version's ask.
            appDataStore.editData { preferences ->
                val evaluations = (preferences[POLICY_EVALUATIONS_KEY] ?: 0) + 1
                preferences[POLICY_EVALUATIONS_KEY] = evaluations

                val eligible = version != null && isEligible(preferences, now, version)
                Timber.d(
                    "In-app review policy evaluated: eligible=%b, run=%d",
                    eligible,
                    evaluations,
                )
                if (!eligible || version == null) return@editData

                preferences[LAST_PROMPTED_AT_KEY] = now
                preferences[LAST_PROMPTED_VERSION_KEY] = version
                val claims = (preferences[PROMPT_CLAIMS_KEY] ?: 0) + 1
                preferences[PROMPT_CLAIMS_KEY] = claims
                claimed = true
                Timber.i("In-app review prompt claimed for %s, claim #%d", version, claims)
            }
            claimed
        }

    private fun isEligible(
        preferences: MutablePreferences,
        now: Long,
        currentVersion: String,
    ): Boolean {
        if (qualifyingEvents(preferences) < MIN_QUALIFYING_EVENTS) return false
        if (now - installTime.firstInstallMillis() < MIN_INSTALL_AGE.inWholeMilliseconds) {
            return false
        }
        // One ask per released version: Play's quota is opaque and the flow no-ops silently, so
        // throttling upstream per version is the only shape that stays honest about what was spent.
        if (preferences[LAST_PROMPTED_VERSION_KEY] == currentVersion) return false
        val lastPromptedAt = preferences[LAST_PROMPTED_AT_KEY] ?: return true
        return now - lastPromptedAt >= MIN_TIME_BETWEEN_PROMPTS.inWholeMilliseconds
    }

    /** Adds [eventId] to the dedupe ring and bumps the lifetime count. False when already seen. */
    private fun countEvent(preferences: MutablePreferences, eventId: String): Boolean {
        val counted = preferences[COUNTED_EVENT_IDS_KEY].splitEntries()
        if (eventId in counted) return false

        preferences[COUNTED_EVENT_IDS_KEY] =
            (counted + eventId).takeLast(COUNTED_EVENT_ID_LIMIT).joinToString(ENTRY_SEPARATOR)
        val total = qualifyingEvents(preferences) + 1
        preferences[QUALIFYING_EVENTS_KEY] = total
        Timber.d("In-app review qualifying events: %d", total)
        return true
    }

    /**
     * Upgraders keep the transactions they already earned under #5427: the legacy counter is read
     * whenever the broadened one has never been written.
     */
    private fun qualifyingEvents(preferences: MutablePreferences): Int =
        preferences[QUALIFYING_EVENTS_KEY] ?: preferences[LEGACY_SUCCESSFUL_TRANSACTIONS_KEY] ?: 0

    private fun lastSeenBalances(preferences: MutablePreferences): Map<String, String> =
        preferences[LAST_SEEN_BALANCES_KEY]
            .splitEntries()
            .mapNotNull { entry ->
                val separator = entry.lastIndexOf(BALANCE_SEPARATOR)
                if (separator <= 0) null
                else entry.substring(0, separator) to entry.substring(separator + 1)
            }
            .toMap()

    private fun rememberBalance(
        preferences: MutablePreferences,
        coinId: String,
        balance: BigInteger,
    ) {
        val entries =
            preferences[LAST_SEEN_BALANCES_KEY].splitEntries().filterNot {
                it.startsWith("$coinId$BALANCE_SEPARATOR")
            }
        preferences[LAST_SEEN_BALANCES_KEY] =
            (entries + "$coinId$BALANCE_SEPARATOR$balance")
                .takeLast(LAST_SEEN_BALANCE_LIMIT)
                .joinToString(ENTRY_SEPARATOR)
    }

    /**
     * Keeps airdropped dust and spam tokens from counting as a good moment. A priced asset has to
     * clear a fiat floor; an unpriced one is accepted only when it is the chain's native coin,
     * which no one can mint into someone else's wallet.
     */
    private fun isMeaningfulArrival(
        delta: BigInteger,
        decimals: Int,
        fiatPricePerUnit: BigDecimal?,
        isNativeToken: Boolean,
    ): Boolean {
        if (delta.signum() <= 0 || decimals !in 0..MAX_DECIMALS) return false
        if (fiatPricePerUnit == null || fiatPricePerUnit.signum() <= 0) return isNativeToken
        val amount = BigDecimal(delta).divide(BigDecimal.TEN.pow(decimals))
        return amount.multiply(fiatPricePerUnit) >= MIN_ARRIVAL_FIAT_VALUE
    }

    private fun String?.splitEntries(): List<String> =
        this?.split(ENTRY_SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()

    internal companion object {
        /** Two distinct positive moments are enough to have formed an opinion worth rating. */
        const val MIN_QUALIFYING_EVENTS = 2

        /** Keeps the prompt away from users still evaluating the wallet. */
        val MIN_INSTALL_AGE = 7.days

        /** Even a new release may not ask right on the heels of the previous one. */
        val MIN_TIME_BETWEEN_PROMPTS = 14.days

        /** Fiat value an arrival must clear to count when the asset is priced. */
        val MIN_ARRIVAL_FIAT_VALUE: BigDecimal = BigDecimal.ONE

        /**
         * Bounds only the dedupe identities; the lifetime count stays monotonic once older ones
         * roll off. Nothing can re-observe an event this many events ago — done screens and
         * pairings are reachable only forwards.
         */
        const val COUNTED_EVENT_ID_LIMIT = 100

        /** Bounds the per-coin balance sightings to the assets the user actually opens. */
        const val LAST_SEEN_BALANCE_LIMIT = 50

        /** Guards `10.pow(decimals)` against a malformed token definition. */
        private const val MAX_DECIMALS = 38

        private const val ENTRY_SEPARATOR = "\n"
        private const val BALANCE_SEPARATOR = '='

        private val QUALIFYING_EVENTS_KEY = intPreferencesKey("in_app_review/qualifying_events")
        private val COUNTED_EVENT_IDS_KEY = stringPreferencesKey("in_app_review/counted_event_ids")
        private val LAST_SEEN_BALANCES_KEY =
            stringPreferencesKey("in_app_review/last_seen_balances")
        private val LAST_PROMPTED_AT_KEY = longPreferencesKey("in_app_review/last_prompted_at")
        private val LAST_PROMPTED_VERSION_KEY =
            stringPreferencesKey("in_app_review/last_prompted_version")
        private val POLICY_EVALUATIONS_KEY = intPreferencesKey("in_app_review/policy_evaluations")
        private val PROMPT_CLAIMS_KEY = intPreferencesKey("in_app_review/prompt_claims")

        /** #5427's transaction counter, still read so upgraders keep what they earned. */
        private val LEGACY_SUCCESSFUL_TRANSACTIONS_KEY =
            intPreferencesKey("in_app_review/successful_transactions")
    }
}
