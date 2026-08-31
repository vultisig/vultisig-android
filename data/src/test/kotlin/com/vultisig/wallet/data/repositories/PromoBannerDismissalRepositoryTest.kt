package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.vultisig.wallet.data.sources.AppDataStore
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Covers the global dismissal model (#5064, #5669): a dismissal persists across reads, is keyed per
 * banner (so banners are independent), and either lapses once the banner's TTL elapses, lasts only
 * as long as the process, or — under a permanent policy — never lapses.
 */
internal class PromoBannerDismissalRepositoryTest {

    /** In-memory [AppDataStore] so reads observe prior writes without Android DataStore. */
    private val store = FakeAppDataStore()

    private val repo = PromoBannerDismissalRepositoryImpl(store)

    private var now = 1_000_000L

    init {
        repo.clock = PromoBannerDismissalRepositoryImpl.Clock { now }
    }

    @Test
    fun `a banner is not dismissed before it is ever closed`() = runTest {
        repo.isDismissed(PromoBanner.BuyVultSwap).first() shouldBe false
    }

    @Test
    fun `dismissing a banner hides it while within the TTL`() = runTest {
        repo.dismiss(PromoBanner.BuyVultSwap)

        repo.isDismissed(PromoBanner.BuyVultSwap).first() shouldBe true

        // One millisecond before the TTL elapses it is still hidden.
        now += ttlOf(PromoBanner.BuyVultSwap) - 1
        repo.isDismissed(PromoBanner.BuyVultSwap).first() shouldBe true
    }

    @Test
    fun `a dismissal lapses once the TTL elapses`() = runTest {
        repo.dismiss(PromoBanner.FollowXVultisig)

        now += ttlOf(PromoBanner.FollowXVultisig) + 1.milliseconds.inWholeMilliseconds

        repo.isDismissed(PromoBanner.FollowXVultisig).first() shouldBe false
    }

    @Test
    fun `dismissals are independent per banner`() = runTest {
        repo.dismiss(PromoBanner.BuyVultSwap)

        repo.isDismissed(PromoBanner.BuyVultSwap).first() shouldBe true
        repo.isDismissed(PromoBanner.FollowXVultisig).first() shouldBe false
        repo.isDismissed(PromoBanner.UpgradeVaultDkls).first() shouldBe false
    }

    @Test
    fun `a permanent dismissal outlasts any TTL`() = runTest {
        repo.dismiss(PromoBanner.BuyVultSwap)

        now += 3650.days.inWholeMilliseconds

        repo.isDismissed(PromoBanner.BuyVultSwap).first() shouldBe false
        repo.isDismissed(PromoBanner.BuyVultSwap, DismissPolicy.Permanent).first() shouldBe true
    }

    @Test
    fun `a permanent policy still shows a banner that was never dismissed`() = runTest {
        repo.isDismissed(PromoBanner.BuyVultSwap, DismissPolicy.Permanent).first() shouldBe false
    }

    @Test
    fun `a permanent policy on one banner leaves the others on their TTL`() = runTest {
        repo.dismiss(PromoBanner.BuyVultSwap)
        repo.dismiss(PromoBanner.FollowXVultisig)

        now += ttlOf(PromoBanner.FollowXVultisig) + 1.milliseconds.inWholeMilliseconds

        repo.isDismissed(PromoBanner.BuyVultSwap, DismissPolicy.Permanent).first() shouldBe true
        repo.isDismissed(PromoBanner.FollowXVultisig).first() shouldBe false
    }

    @Test
    fun `closing the QBTC claim banner keeps it closed for good`() = runTest {
        repo.dismiss(PromoBanner.ClaimQbtc)

        now += 3650.days.inWholeMilliseconds

        repo.isDismissed(PromoBanner.ClaimQbtc).first() shouldBe true
    }

    @Test
    fun `a session dismissal hides the banner for the rest of the process`() = runTest {
        repo.dismiss(PromoBanner.BackupVaultShare)

        repo.isDismissed(PromoBanner.BackupVaultShare).first() shouldBe true

        // Unlike a TTL, no amount of elapsed time brings it back inside this process.
        now += 3650.days.inWholeMilliseconds
        repo.isDismissed(PromoBanner.BackupVaultShare).first() shouldBe true
    }

    @Test
    fun `a session dismissal does not survive a new process`() = runTest {
        repo.dismiss(PromoBanner.BackupVaultShare)

        // A fresh instance over the same storage stands in for a cold launch: the timestamp is
        // still on disk, and a session policy is required to ignore it.
        val relaunched = PromoBannerDismissalRepositoryImpl(store)

        relaunched.isDismissed(PromoBanner.BackupVaultShare).first() shouldBe false
    }

    @Test
    fun `a session dismissal is independent of the other banners`() = runTest {
        repo.dismiss(PromoBanner.BackupVaultShare)

        repo.isDismissed(PromoBanner.BuyVultSwap).first() shouldBe false
        repo.isDismissed(PromoBanner.FollowXVultisig).first() shouldBe false
    }

    private fun ttlOf(banner: PromoBanner): Long {
        val policy = banner.dismissPolicy
        check(policy is DismissPolicy.Ttl) { "Expected a TTL policy for $banner" }
        return policy.duration.inWholeMilliseconds
    }

    private class FakeAppDataStore : AppDataStore {
        private val prefs = MutableStateFlow<Preferences>(emptyPreferences())

        override suspend fun editData(
            transform: suspend (MutablePreferences) -> Unit
        ): Preferences {
            val mutable = prefs.value.toMutablePreferences()
            transform(mutable)
            val updated = mutable.toPreferences()
            prefs.value = updated
            return updated
        }

        override fun <T> readData(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
            prefs.map { it[key] ?: defaultValue }

        override suspend fun <T> set(key: Preferences.Key<T>, value: T) {
            editData { it[key] = value }
        }

        override fun <T> readData(key: Preferences.Key<T>): Flow<T?> = prefs.map { it[key] }
    }
}
