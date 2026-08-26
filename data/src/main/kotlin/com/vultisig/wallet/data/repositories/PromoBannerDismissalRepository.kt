package com.vultisig.wallet.data.repositories

import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.longPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * What a dismissal means for a banner: [Ttl] lets it come back once the window elapses, [Permanent]
 * keeps it hidden for good, and [Session] lets it back on the next cold launch.
 */
sealed interface DismissPolicy {
    data class Ttl(val duration: Duration) : DismissPolicy

    data object Permanent : DismissPolicy

    /**
     * Hidden for the rest of this process only. Nothing is read back from storage, so the banner
     * returns on the next cold launch — the right shape for a reminder whose underlying condition
     * (an un-backed-up vault) is a standing risk rather than an announcement.
     */
    data object Session : DismissPolicy
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
    // A campaign, not a standing promo: the claim entry point also lives on the QBTC chain screen,
    // so a closed banner has nothing to come back for.
    ClaimQbtc(id = "claim_qbtc", dismissPolicy = DismissPolicy.Permanent),
    KaminoEarnSolana(id = "kamino_earn_solana", dismissPolicy = DismissPolicy.Ttl(15.days)),
    RujiraStakingThorchain(
        id = "rujira_staking_thorchain",
        dismissPolicy = DismissPolicy.Ttl(15.days),
    ),
    ReferralRewardsCode(id = "referral_rewards_code", dismissPolicy = DismissPolicy.Ttl(15.days)),
    BackupVaultShare(id = "backup_vault_share", dismissPolicy = DismissPolicy.Session),
}

interface PromoBannerDismissalRepository {
    /** Emits true while a dismissal of [banner] still hides it under the banner's own policy. */
    fun isDismissed(banner: PromoBanner): Flow<Boolean> = isDismissed(banner, banner.dismissPolicy)

    /**
     * Emits true while a dismissal of [banner] still hides it under [policy]: until the TTL window
     * elapses, for the rest of this process, or forever.
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

    /**
     * Banners closed since this process started. Held here rather than in a ViewModel because the
     * repository is the single reader of dismissal state, and [DismissPolicy.Session] has to answer
     * the same question for every collector — the carousel is rebuilt on each vault switch and home
     * re-entry, and a flag scoped to one of those would forget between them.
     */
    private val sessionDismissals = MutableStateFlow(emptySet<PromoBanner>())

    override fun isDismissed(banner: PromoBanner, policy: DismissPolicy): Flow<Boolean> =
        when (policy) {
            // Deliberately does not read storage: a session dismissal is not meant to outlive the
            // process, and the timestamp dismiss() wrote is only kept in case the policy is later
            // reassigned to one that does look at it.
            DismissPolicy.Session -> sessionDismissals.map { banner in it }
            else ->
                appDataStore.readData(dismissedAtKey(banner)).map { dismissedAt ->
                    // No stored timestamp → never dismissed. Otherwise a permanent policy hides the
                    // banner from then on, while a TTL policy hides it only until the window
                    // elapses; that comparison is re-evaluated whenever the flow is re-collected
                    // (home re-entry / vault switch), so an expired TTL surfaces then.
                    dismissedAt != null &&
                        when (policy) {
                            is DismissPolicy.Ttl ->
                                clock.nowMillis() - dismissedAt <
                                    policy.duration.inWholeMilliseconds
                            DismissPolicy.Permanent -> true
                            DismissPolicy.Session -> false
                        }
                }
        }

    override suspend fun dismiss(banner: PromoBanner) {
        sessionDismissals.update { it + banner }
        appDataStore.set(dismissedAtKey(banner), clock.nowMillis())
    }

    private companion object {
        fun dismissedAtKey(banner: PromoBanner) =
            longPreferencesKey(name = "banner_dismissed_at/${banner.id}")
    }
}
