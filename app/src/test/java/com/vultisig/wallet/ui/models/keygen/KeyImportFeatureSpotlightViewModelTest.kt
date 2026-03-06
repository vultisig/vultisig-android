@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keygen

import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class KeyImportFeatureSpotlightViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var navigator: Navigator<Destination>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        navigator = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = KeyImportFeatureSpotlightViewModel(navigator = navigator)

    @Test
    fun `getStarted navigates to ImportSeedphrase`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            vm.getStarted()
            advanceUntilIdle()

            coVerify { navigator.route(Route.KeyImport.ImportSeedphrase) }
        }

    @Test
    fun `back navigates back`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            vm.back()
            advanceUntilIdle()

            coVerify { navigator.navigate(Destination.Back) }
        }
}
