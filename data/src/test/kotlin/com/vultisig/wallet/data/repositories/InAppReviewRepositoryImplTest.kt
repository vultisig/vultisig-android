package com.vultisig.wallet.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.vultisig.wallet.data.repositories.InAppReviewRepositoryImpl.Companion.MIN_INSTALL_AGE
import com.vultisig.wallet.data.repositories.InAppReviewRepositoryImpl.Companion.MIN_QUALIFYING_EVENTS
import com.vultisig.wallet.data.repositories.InAppReviewRepositoryImpl.Companion.MIN_TIME_BETWEEN_PROMPTS
import com.vultisig.wallet.data.sources.AppDataStore
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Covers the gates from #5700: the prompt waits for enough distinct positive events, for the
 * install to be old enough, for a version that has not been asked yet, and for the floor between
 * two asks to elapse.
 */
internal class InAppReviewRepositoryImplTest {

    private val store = FakeAppDataStore()

    private val repo = InAppReviewRepositoryImpl(mockk<Context>(), store)

    /** Well past both windows, so a test only opts into a gate by moving [now] back toward it. */
    private var installedAt = 0L
    private var now = 365.days.inWholeMilliseconds
    private var version: String? = "1.0.0"

    init {
        repo.clock = InAppReviewRepositoryImpl.Clock { now }
        repo.installTime = InAppReviewRepositoryImpl.InstallTime { installedAt }
        repo.appVersion = InAppReviewRepositoryImpl.AppVersion { version }
    }

    @Test
    fun `no prompt before the event threshold is reached`() = runTest {
        recordEvents(MIN_QUALIFYING_EVENTS - 1)

        repo.claimReviewPrompt() shouldBe false
    }

    @Test
    fun `prompts once the event threshold is reached`() = runTest {
        recordEvents(MIN_QUALIFYING_EVENTS)

        repo.claimReviewPrompt() shouldBe true
    }

    @Test
    fun `distinct kinds of positive moment all count`() = runTest {
        repo.record(AppReviewEvent.VaultBackupCompleted("vault-1")) shouldBe true
        repo.record(AppReviewEvent.DevicePairingCompleted("session-1")) shouldBe true

        repo.claimReviewPrompt() shouldBe true
    }

    @Test
    fun `the same event never counts twice`() = runTest {
        repo.record(AppReviewEvent.ConfirmedOutboundTransaction("0xabc")) shouldBe true
        repo.record(AppReviewEvent.ConfirmedOutboundTransaction("0xabc")) shouldBe false

        repo.claimReviewPrompt() shouldBe false
    }

    @Test
    fun `an event with a blank identity is ignored`() = runTest {
        repo.record(AppReviewEvent.ConfirmedOutboundTransaction("   ")) shouldBe false
        repo.record(AppReviewEvent.VaultBackupCompleted("")) shouldBe false

        repo.claimReviewPrompt() shouldBe false
    }

    @Test
    fun `no prompt while the install is younger than the minimum age`() = runTest {
        installedAt = now - MIN_INSTALL_AGE.inWholeMilliseconds + 1
        recordEvents(MIN_QUALIFYING_EVENTS + 2)

        repo.claimReviewPrompt() shouldBe false
    }

    @Test
    fun `an install that just crossed the minimum age is eligible`() = runTest {
        installedAt = now - MIN_INSTALL_AGE.inWholeMilliseconds
        recordEvents(MIN_QUALIFYING_EVENTS)

        repo.claimReviewPrompt() shouldBe true
    }

    @Test
    fun `the event count survives ineligible evaluations`() = runTest {
        installedAt = now
        recordEvents(MIN_QUALIFYING_EVENTS)
        repo.claimReviewPrompt() shouldBe false

        now += MIN_INSTALL_AGE.inWholeMilliseconds

        repo.claimReviewPrompt() shouldBe true
    }

    @Test
    fun `the same version is never asked twice`() = runTest {
        promptOnce()

        now += MIN_TIME_BETWEEN_PROMPTS.inWholeMilliseconds * 10

        repo.claimReviewPrompt() shouldBe false
    }

    @Test
    fun `a new version still waits out the floor between two asks`() = runTest {
        promptOnce()
        version = "1.1.0"

        now += MIN_TIME_BETWEEN_PROMPTS.inWholeMilliseconds - 1

        repo.claimReviewPrompt() shouldBe false
    }

    @Test
    fun `a new version may ask once the floor elapses`() = runTest {
        promptOnce()
        version = "1.1.0"

        now += MIN_TIME_BETWEEN_PROMPTS.inWholeMilliseconds

        repo.claimReviewPrompt() shouldBe true
    }

    @Test
    fun `an unreadable version never spends an ask`() = runTest {
        version = null
        recordEvents(MIN_QUALIFYING_EVENTS)

        repo.claimReviewPrompt() shouldBe false
    }

    // Upgraders from #5427 arrive with a transaction counter and no broadened counter at all.
    @Test
    fun `the legacy transaction count still counts towards eligibility`() = runTest {
        store.editData { it[LEGACY_SUCCESSFUL_TRANSACTIONS_KEY] = MIN_QUALIFYING_EVENTS }

        repo.claimReviewPrompt() shouldBe true
    }

    @Test
    fun `the first sighting of a balance is never an arrival`() = runTest {
        observeBalance(BigInteger("1000000000000000000")) shouldBe false
    }

    @Test
    fun `a meaningful increase counts as an arrival`() = runTest {
        observeBalance(BigInteger.ZERO)

        observeBalance(BigInteger("1000000000000000000")) shouldBe true
    }

    @Test
    fun `dust below the fiat floor is not an arrival`() = runTest {
        observeBalance(BigInteger.ZERO)

        // 0.0001 ETH at $2 is well under the one-unit floor.
        observeBalance(BigInteger("100000000000000")) shouldBe false
    }

    @Test
    fun `an unpriced non-native token is never an arrival`() = runTest {
        observeBalance(BigInteger.ZERO, price = null, isNativeToken = false)

        observeBalance(
            BigInteger("1000000000000000000"),
            price = null,
            isNativeToken = false,
        ) shouldBe false
    }

    @Test
    fun `a shrinking balance is not an arrival`() = runTest {
        observeBalance(BigInteger("2000000000000000000"))

        observeBalance(BigInteger("1000000000000000000")) shouldBe false
    }

    private suspend fun observeBalance(
        balance: BigInteger,
        price: BigDecimal? = BigDecimal("2"),
        isNativeToken: Boolean = true,
    ): Boolean =
        repo.onConfirmedBalanceObserved(
            coinId = "ETH-Ethereum",
            balance = balance,
            decimals = 18,
            fiatPricePerUnit = price,
            isNativeToken = isNativeToken,
        )

    private suspend fun recordEvents(count: Int) {
        repeat(count) { repo.record(AppReviewEvent.ConfirmedOutboundTransaction("0xtx$it")) }
    }

    /** Drives the repository to its first claim, consuming this version's ask. */
    private suspend fun promptOnce() {
        recordEvents(MIN_QUALIFYING_EVENTS)
        repo.claimReviewPrompt() shouldBe true
    }

    private companion object {
        val LEGACY_SUCCESSFUL_TRANSACTIONS_KEY =
            intPreferencesKey("in_app_review/successful_transactions")
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
