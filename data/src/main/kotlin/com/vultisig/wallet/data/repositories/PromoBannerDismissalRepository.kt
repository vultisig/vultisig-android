package com.vultisig.wallet.data.repositories

import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.longPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What a dismissal means for a banner: [Ttl] lets it come back once the window elapses, [Permanent]
 * keeps it hidden for good.
 */
sealed interface DismissPolicy {
    data class Ttl(val duration: Duration) : DismissPolicy

    data object Permanent : DismissPolicy
}

/**
 * Promo banners that support global dismissal.
 *
 * Dismissal is keyed by the banner's CTA intent ([id]) — not by vault — so closing a banner in one
 * vault keeps it hidden everywhere, and each banner carries its own [dismissPolicy]: a dismissal
 * either lapses after a TTL or never does (#5064, #5669).
 *
 * Policies are product knobs and are centralized here rather than hard-coded into view logic, so
 * they can be reassigned per banner in one place.
 */
enum class PromoBanner(val id: String, val dismissPolicy: DismissPolicy) {
    UpgradeVaultDkls(id = "upgrade_vault_dkls", dismissPolicy = DismissPolicy.Ttl(15.days)),
    BuyVultSwap(id = "buy_vult_swap", dismissPolicy = DismissPolicy.Ttl(7.days)),
    FollowXVultisig(id = "follow_x_vultisig", dismissPolicy = DismissPolicy.Ttl(15.days)),
}

interface PromoBannerDismissalRepository {
    /** Emits true while a dismissal of [banner] still hides it under the banner's own policy. */
    fun isDismissed(banner: PromoBanner): Flow<Boolean> = isDismissed(banner, banner.dismissPolicy)

    /**
     * Emits true while a dismissal of [banner] still hides it under [policy]: until the TTL window
     * elapses, or forever when the policy is permanent.
     *
     * Taking the policy as an argument is the seam for one served from elsewhere; the overload
     * above keeps the hardcoded assignment as the fallback, so call sites need no change when a
     * served policy arrives.
     */
    fun isDismissed(banner: PromoBanner, policy: DismissPolicy): Flow<Boolean>

    /**
     * Records that [banner] was dismissed now, starting its dismissal window. The timestamp is
     * stored the same way whatever the policy — how long it hides the banner is decided on read, so
     * a policy can be reassigned without rewriting what is already stored.
     */
    suspend fun dismiss(banner: PromoBanner)
}

internal class PromoBannerDismissalRepositoryImpl
@Inject
constructor(private val appDataStore: AppDataStore) : PromoBannerDismissalRepository {

    /** Minimal clock seam so tests can advance time without sleeping. */
    fun interface Clock {
        fun nowMillis(): Long
    }

    /**
     * Overridable clock so tests can drive TTL expiry deterministically; production uses
     * `System.currentTimeMillis`. Not [Inject]ed to keep Hilt wiring trivial.
     */
    @VisibleForTesting internal var clock: Clock = Clock { System.currentTimeMillis() }

    override fun isDismissed(banner: PromoBanner, policy: DismissPolicy): Flow<Boolean> =
        appDataStore.readData(dismissedAtKey(banner)).map { dismissedAt ->
            // No stored timestamp → never dismissed. Otherwise a permanent policy hides the banner
            // from then on, while a TTL policy hides it only until the window elapses; that
            // comparison is re-evaluated whenever the flow is re-collected (home re-entry / vault
            // switch), so an expired TTL surfaces then.
            dismissedAt != null &&
                when (policy) {
                    is DismissPolicy.Ttl ->
                        clock.nowMillis() - dismissedAt < policy.duration.inWholeMilliseconds
                    DismissPolicy.Permanent -> true
                }
        }

    override suspend fun dismiss(banner: PromoBanner) {
        appDataStore.set(dismissedAtKey(banner), clock.nowMillis())
    }

    private companion object {
        fun dismissedAtKey(banner: PromoBanner) =
            longPreferencesKey(name = "banner_dismissed_at/${banner.id}")
    }
}
