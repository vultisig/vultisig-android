package com.vultisig.wallet.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.vultisig.wallet.data.repositories.InAppReviewRepositoryImpl.Companion.MIN_INSTALL_AGE
import com.vultisig.wallet.data.repositories.InAppReviewRepositoryImpl.Companion.MIN_SUCCESSFUL_TRANSACTIONS
import com.vultisig.wallet.data.repositories.InAppReviewRepositoryImpl.Companion.PROMPT_COOLDOWN
import com.vultisig.wallet.data.sources.AppDataStore
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Covers the three throttle gates from #5427: the prompt waits for enough successful transactions,
 * for the install to be old enough, and for the cooldown between prompts to elapse.
 */
internal class InAppReviewRepositoryImplTest {

    private val store = FakeAppDataStore()

    private val repo = InAppReviewRepositoryImpl(mockk<Context>(), store)

    /** Well past both windows, so a test only opts into a gate by moving [now] back toward it. */
    private var installedAt = 0L
    private var now = 365.days.inWholeMilliseconds

    init {
        repo.clock = InAppReviewRepositoryImpl.Clock { now }
        repo.installTime = InAppReviewRepositoryImpl.InstallTime { installedAt }
    }

    @Test
    fun `no prompt before the transaction threshold is reached`() = runTest {
        repeat(MIN_SUCCESSFUL_TRANSACTIONS - 1) { repo.onTransactionSucceeded() shouldBe false }
    }

    @Test
    fun `prompts once the transaction threshold is reached`() = runTest {
        repeat(MIN_SUCCESSFUL_TRANSACTIONS - 1) { repo.onTransactionSucceeded() }

        repo.onTransactionSucceeded() shouldBe true
    }

    @Test
    fun `no prompt while the install is younger than the minimum age`() = runTest {
        installedAt = now - MIN_INSTALL_AGE.inWholeMilliseconds + 1

        repeat(MIN_SUCCESSFUL_TRANSACTIONS + 2) { repo.onTransactionSucceeded() shouldBe false }
    }

    @Test
    fun `an install that just crossed the minimum age is eligible`() = runTest {
        installedAt = now - MIN_INSTALL_AGE.inWholeMilliseconds

        repeat(MIN_SUCCESSFUL_TRANSACTIONS - 1) { repo.onTransactionSucceeded() }

        repo.onTransactionSucceeded() shouldBe true
    }

    @Test
    fun `the transaction count survives ineligible calls`() = runTest {
        // Transactions made while the install was too young still count toward the threshold, so
        // the prompt is not delayed by a further three transactions once the install matures.
        installedAt = now
        repeat(MIN_SUCCESSFUL_TRANSACTIONS) { repo.onTransactionSucceeded() shouldBe false }

        now += MIN_INSTALL_AGE.inWholeMilliseconds

        repo.onTransactionSucceeded() shouldBe true
    }

    @Test
    fun `no second prompt inside the cooldown`() = runTest {
        promptOnce()

        now += PROMPT_COOLDOWN.inWholeMilliseconds - 1

        repeat(MIN_SUCCESSFUL_TRANSACTIONS + 2) { repo.onTransactionSucceeded() shouldBe false }
    }

    @Test
    fun `prompts again once the cooldown elapses`() = runTest {
        promptOnce()

        now += PROMPT_COOLDOWN.inWholeMilliseconds

        repo.onTransactionSucceeded() shouldBe true
    }

    /** Drives the repository to its first `true`, consuming the cooldown. */
    private suspend fun promptOnce() {
        repeat(MIN_SUCCESSFUL_TRANSACTIONS - 1) { repo.onTransactionSucceeded() }
        repo.onTransactionSucceeded() shouldBe true
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

        override fun <T> readData(key: Preferences.Key<T>): Flow<T?> = prefs.map { it[key] }

        override suspend fun <T> set(key: Preferences.Key<T>, value: T) {
            editData { it[key] = value }
        }
    }
}
