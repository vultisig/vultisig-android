package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.vultisig.wallet.data.sources.AppDataStore
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Covers the global, TTL-based dismissal model (#5064): a dismissal persists across reads, is keyed
 * per banner (so banners are independent), and lapses once the banner's TTL elapses.
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
        now += PromoBanner.BuyVultSwap.ttl.inWholeMilliseconds - 1
        repo.isDismissed(PromoBanner.BuyVultSwap).first() shouldBe true
    }

    @Test
    fun `a dismissal lapses once the TTL elapses`() = runTest {
        repo.dismiss(PromoBanner.FollowXVultisig)

        now +=
            PromoBanner.FollowXVultisig.ttl.inWholeMilliseconds + 1.milliseconds.inWholeMilliseconds

        repo.isDismissed(PromoBanner.FollowXVultisig).first() shouldBe false
    }

    @Test
    fun `dismissals are independent per banner`() = runTest {
        repo.dismiss(PromoBanner.BuyVultSwap)

        repo.isDismissed(PromoBanner.BuyVultSwap).first() shouldBe true
        repo.isDismissed(PromoBanner.FollowXVultisig).first() shouldBe false
        repo.isDismissed(PromoBanner.UpgradeVaultDkls).first() shouldBe false
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
