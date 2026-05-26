package com.vultisig.wallet.data.repositories.swap

import androidx.datastore.preferences.core.Preferences
import com.vultisig.wallet.data.sources.AppDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the SwapKit flag default to `true` (iOS parity). [SwapKitQuoteSourceTest] mocks
 * `isFeatureEnabled` directly, so a regression flipping the DataStore default back to `false`
 * wouldn't ripple to those — this is the only test that fails if the default changes.
 */
internal class SwapKitConfigTest {

    @Test
    fun `feature flag defaults to true when the DataStore key is absent`() = runTest {
        val store: AppDataStore = mockk()
        // When the key is absent, AppDataStore.readData emits the default it's given. Echo that
        // default back so the assertion reflects exactly what SwapKitConfigImpl passes.
        every { store.readData(any<Preferences.Key<Boolean>>(), any<Boolean>()) } answers
            {
                flowOf(secondArg<Boolean>())
            }

        val config: SwapKitConfig = SwapKitConfigImpl(store)

        assertTrue(config.isFeatureEnabled.first(), "SwapKit flag must default to on (iOS parity)")
    }
}
