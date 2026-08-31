@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.ThorChainPoolCoin
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.usecases.GetThorChainPoolAssetsUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

/** The payout-asset picker behind the referral edit (issue #5684). */
internal class ReferralPayoutAssetViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var requestResultRepository: RequestResultRepository
    private lateinit var getThorChainPoolAssets: GetThorChainPoolAssetsUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        givenRoute(selectedAsset = null)

        navigator = mockk(relaxed = true)
        requestResultRepository = mockk(relaxed = true)
        getThorChainPoolAssets = mockk()
        coEvery { getThorChainPoolAssets() } returns listOf(btc(), usdc())
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `lists the available pool assets once loaded`() =
        runTest(testDispatcher) {
            val model = createViewModel()
            advanceUntilIdle()

            assertEquals(listOf("BTC", "USDC"), model.state.value.assets.map { it.ticker })
            assertEquals(false, model.state.value.isLoading)
            assertEquals(false, model.state.value.isError)
        }

    @Test
    fun `a failed read is reported as an error rather than as an empty pool list`() =
        runTest(testDispatcher) {
            coEvery { getThorChainPoolAssets() } throws IllegalStateException("offline")

            val model = createViewModel()
            advanceUntilIdle()

            assertTrue(model.state.value.assets.isEmpty())
            assertEquals(false, model.state.value.isLoading)
            assertEquals(true, model.state.value.isError)
        }

    @Test
    fun `search narrows the list by ticker`() =
        runTest(testDispatcher) {
            val model = createViewModel()
            advanceUntilIdle()

            model.searchFieldState.setTextAndPlaceCursorAtEnd("usd")
            // Nothing composes in a JVM test, so the snapshot the edit lands in has to be
            // published by hand before `snapshotFlow` sees it.
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals(listOf("USDC"), model.state.value.assets.map { it.ticker })
        }

    @Test
    fun `marks the asset the caller opened the picker on`() =
        runTest(testDispatcher) {
            givenRoute(selectedAsset = USDC_ASSET)

            val model = createViewModel()
            advanceUntilIdle()

            assertEquals(listOf(false, true), model.state.value.assets.map { it.isSelected })
        }

    @Test
    fun `a pick is returned to the caller`() =
        runTest(testDispatcher) {
            val model = createViewModel()
            advanceUntilIdle()

            model.onAssetClick(model.state.value.assets.first { it.ticker == "USDC" })
            advanceUntilIdle()

            coVerify { requestResultRepository.respond(REQUEST_ID, usdc()) }
            coVerify { navigator.navigate(Destination.Back) }
        }

    @Test
    fun `dismissing releases the caller with no pick`() =
        runTest(testDispatcher) {
            val model = createViewModel()
            advanceUntilIdle()

            model.back()
            advanceUntilIdle()

            coVerify { requestResultRepository.respond(REQUEST_ID, null) }
        }

    private fun givenRoute(selectedAsset: String?) {
        every { any<SavedStateHandle>().toRoute<Route.ReferralPayoutAsset>() } returns
            Route.ReferralPayoutAsset(requestId = REQUEST_ID, selectedAsset = selectedAsset)
    }

    private fun createViewModel() =
        ReferralPayoutAssetViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            requestResultRepository = requestResultRepository,
            getThorChainPoolAssets = getThorChainPoolAssets,
        )

    private fun btc() = ThorChainPoolCoin(asset = "BTC.BTC", coin = Coins.Bitcoin.BTC)

    private fun usdc() = ThorChainPoolCoin(asset = USDC_ASSET, coin = Coins.Ethereum.USDC)

    private companion object {
        const val REQUEST_ID = "request-id"
        const val USDC_ASSET = "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"
    }
}
