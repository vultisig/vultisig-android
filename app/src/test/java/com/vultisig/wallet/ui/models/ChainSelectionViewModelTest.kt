@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DiscoverTokenUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChainSelectionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var vaultRepository: VaultRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var discoverTokenUseCase: DiscoverTokenUseCase
    private lateinit var navigator: Navigator<Destination>
    private lateinit var requestResultRepository: RequestResultRepository
    private lateinit var snackbarFlow: SnackbarFlow

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.AddChainAccount>() } returns
            Route.AddChainAccount(vaultId = VAULT_ID)
        vaultRepository = mockk(relaxed = true)
        tokenRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
        discoverTokenUseCase = mockk()
        every { discoverTokenUseCase(any(), any()) } just Runs
        navigator = mockk(relaxed = true)
        requestResultRepository = mockk(relaxed = true)
        snackbarFlow = mockk(relaxed = true)

        // Stub the dependencies touched by init's loadChains() and by onCommitChanges
        // so test ordering does not race the ViewModel's eager initialization.
        coEvery { vaultRepository.get(VAULT_ID) } returns vault()
        every { tokenRepository.nativeTokens } returns emptyFlow()
        every { vaultRepository.getEnabledChains(VAULT_ID) } returns emptyFlow()
        coEvery { chainAccountAddressRepository.getAddress(any<Coin>(), any<Vault>()) } returns
            Pair("address", "derivedPubKey")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `triggers token discovery for each successfully enabled chain`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.uiState.update {
                it.copy(
                    chains =
                        listOf(
                            ChainUiModel(isEnabled = true, coin = nativeCoin(Chain.Ethereum)),
                            ChainUiModel(isEnabled = true, coin = nativeCoin(Chain.Bitcoin)),
                        )
                )
            }

            vm.onCommitChanges()
            advanceUntilIdle()

            verify(exactly = 1) { discoverTokenUseCase(VAULT_ID, Chain.Ethereum.raw) }
            verify(exactly = 1) { discoverTokenUseCase(VAULT_ID, Chain.Bitcoin.raw) }
        }

    @Test
    fun `skips token discovery for chains whose enable step fails`() =
        runTest(testDispatcher) {
            coEvery { chainAccountAddressRepository.getAddress(any<Coin>(), any<Vault>()) } answers
                {
                    when (firstArg<Coin>().chain) {
                        Chain.Bitcoin -> throw IllegalArgumentException("derivation failed")
                        else -> Pair("address", "pubKey")
                    }
                }
            val vm = createViewModel()
            vm.uiState.update {
                it.copy(
                    chains =
                        listOf(
                            ChainUiModel(isEnabled = true, coin = nativeCoin(Chain.Ethereum)),
                            ChainUiModel(isEnabled = true, coin = nativeCoin(Chain.Bitcoin)),
                        )
                )
            }

            vm.onCommitChanges()
            advanceUntilIdle()

            verify(exactly = 1) { discoverTokenUseCase(VAULT_ID, Chain.Ethereum.raw) }
            verify(exactly = 0) { discoverTokenUseCase(VAULT_ID, Chain.Bitcoin.raw) }
        }

    @Test
    fun `triggers discovery only after the native token is persisted`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.uiState.update {
                it.copy(
                    chains =
                        listOf(ChainUiModel(isEnabled = true, coin = nativeCoin(Chain.Ethereum)))
                )
            }

            vm.onCommitChanges()
            advanceUntilIdle()

            coVerifyOrder {
                vaultRepository.addTokenToVault(VAULT_ID, any())
                discoverTokenUseCase(VAULT_ID, Chain.Ethereum.raw)
            }
        }

    @Test
    fun `does not trigger discovery for chains being disabled`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.uiState.update {
                it.copy(
                    chains =
                        listOf(ChainUiModel(isEnabled = false, coin = nativeCoin(Chain.Ethereum)))
                )
            }

            vm.onCommitChanges()
            advanceUntilIdle()

            verify(exactly = 0) { discoverTokenUseCase(any(), any()) }
        }

    @Test
    fun `logs and continues when token discovery scheduling fails`() =
        runTest(testDispatcher) {
            every { discoverTokenUseCase(VAULT_ID, Chain.Ethereum.raw) } throws
                IllegalStateException("WorkManager not initialized")
            val vm = createViewModel()
            vm.uiState.update {
                it.copy(
                    chains =
                        listOf(
                            ChainUiModel(isEnabled = true, coin = nativeCoin(Chain.Ethereum)),
                            ChainUiModel(isEnabled = true, coin = nativeCoin(Chain.Bitcoin)),
                        )
                )
            }

            vm.onCommitChanges()
            advanceUntilIdle()

            // Bitcoin's enable + discovery still runs after Ethereum's discovery threw.
            coVerify { vaultRepository.addTokenToVault(VAULT_ID, any()) }
            verify { discoverTokenUseCase(VAULT_ID, Chain.Bitcoin.raw) }
        }

    private fun createViewModel() =
        ChainSelectionViewModel(
            savedStateHandle = mockk(relaxed = true),
            vaultRepository = vaultRepository,
            tokenRepository = tokenRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            discoverTokenUseCase = discoverTokenUseCase,
            navigator = navigator,
            requestResultRepository = requestResultRepository,
            snackbarFlow = snackbarFlow,
        )

    private fun vault() = Vault(id = VAULT_ID, name = "Test Vault")

    private fun nativeCoin(chain: Chain) =
        Coin(
            chain = chain,
            ticker = chain.raw,
            logo = "",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private companion object {
        private const val VAULT_ID = "test-vault-id"
    }
}
