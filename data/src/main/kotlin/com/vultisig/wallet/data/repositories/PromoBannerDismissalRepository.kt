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
 * Promo banners that support global, time-bounded dismissal.
 *
 * Dismissal is keyed by the banner's CTA intent ([id]) — not by vault — so closing a banner in one
 * vault keeps it hidden everywhere, and each banner carries its own [ttl]: once dismissed it stays
 * hidden until the TTL elapses, then becomes eligible to show again (#5064).
 *
 * TTLs are product knobs and are centralized here rather than hard-coded into view logic, so they
 * can be tuned per banner in one place.
 */
enum class PromoBanner(val id: String, val ttl: Duration) {
    UpgradeVaultDkls(id = "upgrade_vault_dkls", ttl = 14.days),
    BuyVultSwap(id = "buy_vult_swap", ttl = 7.days),
    FollowXVultisig(id = "follow_x_vultisig", ttl = 30.days),
}

interface PromoBannerDismissalRepository {
    /**
     * Emits true while [banner] is still within its dismissal TTL window (i.e. should stay hidden).
     */
    fun isDismissed(banner: PromoBanner): Flow<Boolean>

    /** Records that [banner] was dismissed now, starting its TTL window. */
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

    override fun isDismissed(banner: PromoBanner): Flow<Boolean> =
        appDataStore.readData(dismissedAtKey(banner)).map { dismissedAt ->
            // No stored timestamp → never dismissed. Otherwise hidden only until the TTL elapses;
            // an
            // expired dismissal reads as "show again". The comparison is re-evaluated whenever the
            // flow is re-collected (home re-entry / vault switch), so an expired TTL surfaces then.
            dismissedAt != null && clock.nowMillis() - dismissedAt < banner.ttl.inWholeMilliseconds
        }

    override suspend fun dismiss(banner: PromoBanner) {
        appDataStore.set(dismissedAtKey(banner), clock.nowMillis())
    }

    private companion object {
        fun dismissedAtKey(banner: PromoBanner) =
            longPreferencesKey(name = "banner_dismissed_at/${banner.id}")
    }
}
