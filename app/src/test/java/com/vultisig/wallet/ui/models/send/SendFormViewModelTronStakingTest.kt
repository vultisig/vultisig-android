@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.blockchain.tron.GetTronFrozenBalancesUseCase
import com.vultisig.wallet.data.blockchain.tron.TronFrozenBalances
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SendFormViewModelTronStakingTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val vaultRepository: VaultRepository = mockk()
    private val getTronFrozenBalances: GetTronFrozenBalancesUseCase = mockk()
    private val savedStateHandle: SavedStateHandle = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, type = "UNFREEZE_TRX")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    @Test
    fun `loadTronFrozenBalances populates state with the loaded resource values`() = runTest {
        val vault = vaultWithTrxCoin()
        coEvery { vaultRepository.get(VAULT_ID) } returns vault
        coEvery { getTronFrozenBalances(TRX_ADDRESS) } returns
            TronFrozenBalances(bandwidthTrx = BigDecimal("5"), energyTrx = BigDecimal("3"))

        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(TronResourceType.BANDWIDTH, state.tronResourceType)
        assertEquals("5", state.tronBalanceAvailableOverride)
        assertFalse(state.isTronFrozenBalancesLoading)
        assertFalse(state.hasTronFrozenBalancesError)
    }

    @Test
    fun `loadTronFrozenBalances flips to error when vault has no TRX coin`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns vaultWithoutTrxCoin()

        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.hasTronFrozenBalancesError)
        assertFalse(state.isTronFrozenBalancesLoading)
        assertNull(state.tronBalanceAvailableOverride)
    }

    @Test
    fun `loadTronFrozenBalances flips to error when the use case throws`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns vaultWithTrxCoin()
        coEvery { getTronFrozenBalances(TRX_ADDRESS) } throws IllegalStateException("api down")

        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.hasTronFrozenBalancesError)
    }

    @Test
    fun `setTronResourceType switches the override to the selected resource balance`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns vaultWithTrxCoin()
        coEvery { getTronFrozenBalances(TRX_ADDRESS) } returns
            TronFrozenBalances(bandwidthTrx = BigDecimal("5"), energyTrx = BigDecimal("3"))
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setTronResourceType(TronResourceType.ENERGY)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(TronResourceType.ENERGY, state.tronResourceType)
        assertEquals("3", state.tronBalanceAvailableOverride)
    }

    @Test
    fun `setTronResourceType is a no-op while a send is in flight`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns vaultWithTrxCoin()
        coEvery { getTronFrozenBalances(TRX_ADDRESS) } returns
            TronFrozenBalances(bandwidthTrx = BigDecimal("5"), energyTrx = BigDecimal("3"))
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.uiState.value = vm.uiState.value.copy(isLoading = true)

        vm.setTronResourceType(TronResourceType.ENERGY)
        advanceUntilIdle()

        assertEquals(TronResourceType.BANDWIDTH, vm.uiState.value.tronResourceType)
        assertEquals("5", vm.uiState.value.tronBalanceAvailableOverride)
    }

    private fun buildViewModel(): SendFormViewModel {
        val tokenBalanceMapper = mockk<AccountToTokenBalanceUiModelMapper>()
        coEvery { tokenBalanceMapper.invoke(any()) } returns
            TokenBalanceUiModel(
                model = mockk(relaxed = true),
                title = "",
                balance = "0",
                fiatValue = "0",
                isNativeToken = true,
                isLayer2 = false,
                tokenStandard = null,
                tokenLogo = "",
                chainLogo = 0,
            )
        return SendFormViewModel(
            savedStateHandle = savedStateHandle,
            navigator = mockk(relaxed = true),
            accountToTokenBalanceUiModelMapper = tokenBalanceMapper,
            mapTokenValueToString = mockk(relaxed = true),
            requestQrScan = mockk(relaxed = true),
            accountsRepository = mockk(relaxed = true),
            appCurrencyRepository = mockk(relaxed = true),
            chainAccountAddressRepository = mockk(relaxed = true),
            tokenPriceRepository = mockk(relaxed = true),
            transactionRepository = mockk(relaxed = true),
            blockChainSpecificRepository = mockk(relaxed = true),
            requestResultRepository = mockk(relaxed = true),
            addressParserRepository = mockk(relaxed = true),
            getAvailableTokenBalance = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
            advanceGasUiRepository = mockk(relaxed = true),
            vaultRepository = vaultRepository,
            tokenRepository = mockk(relaxed = true),
            depositTransactionRepository = mockk(relaxed = true),
            stakingDetailsRepository = mockk(relaxed = true),
            feeServiceComposite = mockk(relaxed = true),
            chainValidationService = mockk(relaxed = true),
            requestAddressBookEntry = mockk(relaxed = true),
            getTronFrozenBalances = getTronFrozenBalances,
        )
    }

    private fun vaultWithTrxCoin(): Vault =
        mockk<Vault>(relaxed = true).also {
            every { it.id } returns VAULT_ID
            every { it.coins } returns
                listOf(
                    mockk<Coin>(relaxed = true).also { c ->
                        every { c.chain } returns Chain.Tron
                        every { c.isNativeToken } returns true
                        every { c.address } returns TRX_ADDRESS
                    }
                )
        }

    private fun vaultWithoutTrxCoin(): Vault =
        mockk<Vault>(relaxed = true).also {
            every { it.id } returns VAULT_ID
            every { it.coins } returns emptyList()
        }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val TRX_ADDRESS = "TXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    }
}
