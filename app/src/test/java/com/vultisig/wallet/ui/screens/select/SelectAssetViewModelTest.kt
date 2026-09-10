@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.screens.select

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.chaintokens.GetChainTokensUseCase
import com.vultisig.wallet.ui.models.TokenSelectionViewModel.Companion.REQUEST_SEARCHED_TOKEN_ID
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

    @Test
    fun `canAddCustomToken follows the chain selected in the carousel`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            // ThorChain is the preselected chain and supports custom tokens.
            assertTrue(vm.state.value.canAddCustomToken)

            vm.selectChain(Chain.ZkSync)
            assertFalse(vm.state.value.canAddCustomToken)

            vm.selectChain(Chain.Ethereum)
            assertTrue(vm.state.value.canAddCustomToken)
        }

    @Test
    fun `addCustomToken opens the custom token flow for the selected chain and enables the result`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val customToken = usdcCoin()
            coEvery { requestResultRepository.request<Coin>(REQUEST_SEARCHED_TOKEN_ID) } returns
                customToken

            vm.selectChain(Chain.Ethereum)
            vm.addCustomToken()

            coVerify(exactly = 1) { navigator.route(Route.CustomToken(Chain.Ethereum.raw)) }
            coVerify(exactly = 1) { enableTokenUseCase.invoke(VAULT_ID, customToken) }
            // The query that found nothing would keep hiding the token that was just added.
            assertEquals(customToken.ticker, vm.searchFieldState.text.toString())
        }

    @Test
    fun `a dismissed custom token sheet leaves no waiter that double-enables the next token`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val customToken = usdcCoin()
            val pendingResult = CompletableDeferred<Coin?>()
            coEvery { requestResultRepository.request<Coin>(REQUEST_SEARCHED_TOKEN_ID) } coAnswers
                {
                    pendingResult.await()
                }

            // First tap is dismissed without adding anything, so its request never resolves.
            vm.addCustomToken()
            vm.addCustomToken()
            pendingResult.complete(customToken)

            coVerify(exactly = 1) { enableTokenUseCase.invoke(VAULT_ID, customToken) }
        }

    /**
     * The native asset on an L2 is the whole point of name search: the only ticker on Base is
     * `ETH`, so a user who types the asset's name used to get nothing.
     */
    @Test
    fun `typing an asset's name on an L2 surfaces the native coin the ticker hides`() =
        runTest(testDispatcher) {
            val vault = Vault(id = VAULT_ID, name = "Main")
            val eth = Coins.Base.ETH
            val usdc = Coins.Base.USDC
            coEvery { vaultRepository.get(VAULT_ID) } returns vault
            every { accountRepository.loadAddress(VAULT_ID, Chain.Base) } returns
                flowOf(
                    Address(chain = Chain.Base, address = "0xabc", accounts = accountsOf(eth, usdc))
                )
            every { getChainTokens(Chain.Base, vault) } returns flowOf(emptyList())
            val vm = createViewModel(preselectedChain = Chain.Base)

            advanceUntilIdle()
            assertEquals(listOf(eth.id, usdc.id), vm.state.value.assets.map { it.token.id })

            vm.searchFieldState.setTextAndPlaceCursorAtEnd("ethereum")
            // Nothing composes in a JVM test, so the snapshot the edit lands in has to be
            // published by hand before `snapshotFlow` sees it.
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals(listOf(eth.id), vm.state.value.assets.map { it.token.id })
        }

    @Test
    fun `an unheld token from the chain catalogue is found by name too`() =
        runTest(testDispatcher) {
            val vault = Vault(id = VAULT_ID, name = "Main")
            val eth = Coins.Base.ETH
            val usdc = Coins.Base.USDC
            coEvery { vaultRepository.get(VAULT_ID) } returns vault
            every { accountRepository.loadAddress(VAULT_ID, Chain.Base) } returns
                flowOf(Address(chain = Chain.Base, address = "0xabc", accounts = accountsOf(eth)))
            every { getChainTokens(Chain.Base, vault) } returns flowOf(listOf(usdc))
            val vm = createViewModel(preselectedChain = Chain.Base)

            vm.searchFieldState.setTextAndPlaceCursorAtEnd("usd coin")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            val found = vm.state.value.assets.single()
            assertEquals(usdc.id, found.token.id)
            assertTrue(found.isDisabled)
        }

    private fun accountsOf(vararg coins: Coin) =
        coins.map { Account(token = it, tokenValue = null, fiatValue = null, price = null) }

    private fun createViewModel(preselectedChain: Chain = Chain.ThorChain): SelectAssetViewModel {
        every { any<SavedStateHandle>().toRoute<Route.SelectAsset>() } returns
            Route.SelectAsset(
                vaultId = VAULT_ID,
                preselectedNetworkId = preselectedChain.id,
                networkFilters = Route.SelectNetwork.Filters.SwapAvailable,
                requestId = REQUEST_ID,
            )
        return SelectAssetViewModel(
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
    }

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
