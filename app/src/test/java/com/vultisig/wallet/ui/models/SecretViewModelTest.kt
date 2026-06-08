@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models

import com.vultisig.wallet.data.repositories.CustomRpcConfig
import com.vultisig.wallet.data.repositories.swap.SwapKitConfig
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [SecretViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SecretViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var swapKitConfig: SwapKitConfig
    private lateinit var customRpcConfig: CustomRpcConfig
    private lateinit var navigator: Navigator<Destination>

    /** Sets up mocks and test dispatcher before each test. */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        swapKitConfig = mockk(relaxed = true) { every { isFeatureEnabled } returns flowOf(false) }
        customRpcConfig = mockk(relaxed = true) { every { isFeatureEnabled } returns flowOf(false) }
        navigator = mockk(relaxed = true)
    }

    /** Resets the test dispatcher after each test. */
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        SecretViewModel(
            swapKitConfig = swapKitConfig,
            customRpcConfig = customRpcConfig,
            navigator = navigator,
        )

    /** Verifies toggleSwapKit persists the new feature flag value. */
    @Test
    fun `toggleSwapKit persists the new flag value`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.toggleSwapKit(true)
            coVerify { swapKitConfig.setFeatureEnabled(true) }

            vm.toggleSwapKit(false)
            coVerify { swapKitConfig.setFeatureEnabled(false) }
        }

    /** Verifies a `true` emission from isFeatureEnabled reflects into the state. */
    @Test
    fun `isFeatureEnabled emissions reflect into state`() =
        runTest(testDispatcher) {
            every { swapKitConfig.isFeatureEnabled } returns flowOf(true)
            val vm = createViewModel()

            vm.state.value.isSwapKitEnabled.shouldBeTrue()
        }
}
