@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.screens.select

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.chaintokens.GetChainTokensUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class SelectAssetViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var accountRepository: AccountsRepository
    private lateinit var requestResultRepository: RequestResultRepository
    private lateinit var getChainTokens: GetChainTokensUseCase
    private lateinit var vaultRepository: VaultRepository
    private lateinit var enableTokenUseCase: EnableTokenUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.SelectAsset>() } returns
            Route.SelectAsset(
                vaultId = VAULT_ID,
                preselectedNetworkId = Chain.ThorChain.id,
                networkFilters = Route.SelectNetwork.Filters.SwapAvailable,
                requestId = REQUEST_ID,
            )

        navigator = mockk(relaxed = true)
        accountRepository = mockk(relaxed = true)
        requestResultRepository = mockk(relaxed = true)
        getChainTokens = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        enableTokenUseCase = mockk(relaxed = true)

        // Stub the dependencies touched by init's collectAssets()/loadAllAvailableNetworks() so
        // test ordering does not race the ViewModel's eager initialization.
        coEvery { vaultRepository.get(VAULT_ID) } returns null
        every { vaultRepository.getEnabledChains(VAULT_ID) } returns flowOf(emptySet())
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `a second selectAsset call before the first completes is ignored`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val asset =
                AssetUiModel(
                    token = usdcCoin(),
                    logo = "",
                    title = "USDC",
                    subtitle = "Ethereum",
                    amount = "0",
                    value = "0",
                    isDisabled = true,
                )

            // Same population this PR makes co-visible: two rows sharing a ticker, tapped in
            // quick succession before the first enable+respond round trip completes.
            vm.selectAsset(asset)
            vm.selectAsset(asset)

            coVerify(exactly = 1) { enableTokenUseCase.invoke(VAULT_ID, asset.token) }
            coVerify(exactly = 1) { requestResultRepository.respond(REQUEST_ID, any()) }
        }

    @Test
    fun `selecting an already-enabled asset does not call enableTokenUseCase`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val asset =
                AssetUiModel(
                    token = usdcCoin(),
                    logo = "",
                    title = "USDC",
                    subtitle = "Ethereum",
                    amount = "0",
                    value = "0",
                    isDisabled = false,
                )

            vm.selectAsset(asset)

            coVerify(exactly = 0) { enableTokenUseCase.invoke(any(), any()) }
            coVerify(exactly = 1) { requestResultRepository.respond(REQUEST_ID, any()) }
        }

    private fun createViewModel() =
        SelectAssetViewModel(
            savedStateHandle = mockk(relaxed = true),
            navigator = navigator,
            mapTokenValueToDecimalUiString = mockk(relaxed = true),
            fiatValueToString = mockk(relaxed = true),
            accountRepository = accountRepository,
            requestResultRepository = requestResultRepository,
            getChainTokens = getChainTokens,
            vaultRepository = vaultRepository,
            enableTokenUseCase = enableTokenUseCase,
        )

    private fun usdcCoin() =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            isNativeToken = false,
        )

    companion object {
        private const val VAULT_ID = "vault-1"
        private const val REQUEST_ID = "request-1"
    }
}
