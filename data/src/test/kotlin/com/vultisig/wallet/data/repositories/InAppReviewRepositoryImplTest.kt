package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.vultisig.wallet.data.repositories.InAppReviewRepositoryImpl.Companion.PROMPT_COOLDOWN
import com.vultisig.wallet.data.sources.AppDataStore
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Covers the throttle: either qualifying moment owes the user a card, an ask is spent on request,
 * and a spent ask holds the next one off for [PROMPT_COOLDOWN].
 */
internal class InAppReviewRepositoryImplTest {

    private val store = FakeAppDataStore()

    private val repo = InAppReviewRepositoryImpl(store)

    /** Far enough from zero that a test only opts into the cooldown by moving [now] back to it. */
    private var now = 365.days.inWholeMilliseconds

    init {
        repo.clock = InAppReviewRepositoryImpl.Clock { now }
    }

    @Test
    fun `nothing is pending until a moment is reached`() = runTest {
        repo.isPromptPending.first() shouldBe false
    }

    @Test
    fun `a created vault owes the user a card`() = runTest {
        repo.onVaultCreated()

        repo.isPromptPending.first() shouldBe true
    }

    @Test
    fun `a successful transaction owes the user a card`() = runTest {
        repo.onTransactionSucceeded()

        repo.isPromptPending.first() shouldBe true
    }

    @Test
    fun `requesting the prompt spends it`() = runTest {
        repo.onVaultCreated()

        repo.onPromptRequested()

        repo.isPromptPending.first() shouldBe false
    }

    @Test
    fun `a second moment inside the cooldown is not asked again`() = runTest {
        promptOnce()

        now += PROMPT_COOLDOWN.inWholeMilliseconds - 1
        repo.onTransactionSucceeded()

        repo.isPromptPending.first() shouldBe false
    }

    @Test
    fun `a moment asks again once the cooldown elapses`() = runTest {
        promptOnce()

        now += PROMPT_COOLDOWN.inWholeMilliseconds
        repo.onTransactionSucceeded()

        repo.isPromptPending.first() shouldBe true
    }

    @Test
    fun `a second moment before the card is shown leaves it pending`() = runTest {
        repo.onVaultCreated()
        repo.onTransactionSucceeded()

        repo.isPromptPending.first() shouldBe true

        // Still a single ask: spending it clears the pair, rather than leaving one queued behind.
        repo.onPromptRequested()
        repo.isPromptPending.first() shouldBe false
    }

    @Test
    fun `a prompt that was never requested survives a restart`() = runTest {
        repo.onVaultCreated()

        InAppReviewRepositoryImpl(store).isPromptPending.first() shouldBe true
    }

    /** Reaches a moment and spends the ask it earns, starting the cooldown at [now]. */
    private suspend fun promptOnce() {
        repo.onVaultCreated()
        repo.isPromptPending.first() shouldBe true
        repo.onPromptRequested()
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
