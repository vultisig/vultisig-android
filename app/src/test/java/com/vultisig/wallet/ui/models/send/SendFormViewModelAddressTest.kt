@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.usecases.RequestAddressBookEntryUseCase
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SendFormViewModelAddressTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
    private val appCurrencyRepository: AppCurrencyRepository = mockk(relaxed = true)
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk(relaxed = true)
    private val addressParserRepository: AddressParserRepository = mockk(relaxed = true)
    private val requestQrScan: RequestQrScanUseCase = mockk(relaxed = true)
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase = mockk(relaxed = true)
    private val requestResultRepository: RequestResultRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { savedStateHandle.toRoute<Route.Send>() } returns Route.Send(vaultId = VAULT_ID)
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { appCurrencyRepository.defaultCurrency } returns AppCurrency.USD
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    @Test
    fun `setOutputAddress writes to addressFieldState`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setOutputAddress("0xabc")
        advanceUntilIdle()

        assertEquals("0xabc", vm.addressFieldState.text.toString())
    }

    @Test
    fun `setProviderAddress writes to providerBondFieldState only`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setProviderAddress("provider1")
        advanceUntilIdle()

        assertEquals("provider1", vm.providerBondFieldState.text.toString())
        assertEquals("", vm.addressFieldState.text.toString())
    }

    @Test
    fun `isDstAddressComplete becomes true when address has content`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setOutputAddress("0xabc")
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isDstAddressComplete)
    }

    @Test
    fun `isDstAddressComplete is false for blank address`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setOutputAddress("   ")
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isDstAddressComplete)
    }

    @Test
    fun `scanAddress invokes the QR scan use case`() = runTest {
        coEvery { requestQrScan.invoke() } returns null

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.scanAddress()
        advanceUntilIdle()

        coVerify { requestQrScan.invoke() }
    }

    @Test
    fun `scanAddress with non-blank QR populates addressFieldState`() = runTest {
        coEvery { requestQrScan.invoke() } returns "0xQR"
        every { chainAccountAddressRepository.isValid(any(), any()) } returns false

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.scanAddress()
        advanceUntilIdle()

        assertEquals("0xQR", vm.addressFieldState.text.toString())
    }

    @Test
    fun `scanAddress with blank QR leaves addressFieldState empty`() = runTest {
        coEvery { requestQrScan.invoke() } returns "  "

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.scanAddress()
        advanceUntilIdle()

        assertEquals("", vm.addressFieldState.text.toString())
    }

    @Test
    fun `scanProviderAddress with non-blank QR populates providerBondFieldState`() = runTest {
        coEvery { requestQrScan.invoke() } returns "providerQR"
        every { chainAccountAddressRepository.isValid(any(), any()) } returns false

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.scanProviderAddress()
        advanceUntilIdle()

        assertEquals("providerQR", vm.providerBondFieldState.text.toString())
        assertEquals("", vm.addressFieldState.text.toString())
    }

    @Test
    fun `setAddressFromQrCode populates the field with the QR payload`() = runTest {
        every { chainAccountAddressRepository.isValid(any(), any()) } returns false

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setAddressFromQrCode(
            qrCode = "0xqr",
            preSelectedChainId = null,
            preSelectedTokenId = null,
        )
        advanceUntilIdle()

        assertEquals("0xqr", vm.addressFieldState.text.toString())
    }

    @Test
    fun `setAddressFromQrCode is a no-op for blank input`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setAddressFromQrCode(qrCode = "", preSelectedChainId = null, preSelectedTokenId = null)
        advanceUntilIdle()

        assertEquals("", vm.addressFieldState.text.toString())
    }

    @Test
    fun `openAddressBook is a no-op when no token is selected`() = runTest {
        // The address book flow is gated on a token selection: without selectedTokenValue,
        // openAddressBook returns early before consulting the address book entry use case.
        val entry: AddressBookEntry = mockk(relaxed = true)
        every { entry.address } returns "0xfromBook"
        every { entry.chain } returns mockk(relaxed = true)
        coEvery { requestAddressBookEntry.invoke(any(), any()) } returns entry

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.openAddressBook(AddressBookType.OUTPUT)
        advanceUntilIdle()

        assertEquals("", vm.addressFieldState.text.toString())
        coVerify(exactly = 0) { requestAddressBookEntry.invoke(any(), any()) }
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
            requestQrScan = requestQrScan,
            accountsRepository = mockk(relaxed = true),
            appCurrencyRepository = appCurrencyRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            tokenPriceRepository = mockk(relaxed = true),
            transactionRepository = mockk(relaxed = true),
            blockChainSpecificRepository = mockk(relaxed = true),
            requestResultRepository = requestResultRepository,
            addressParserRepository = addressParserRepository,
            getAvailableTokenBalance = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
            advanceGasUiRepository = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            tokenRepository = mockk(relaxed = true),
            depositTransactionRepository = mockk(relaxed = true),
            stakingDetailsRepository = mockk(relaxed = true),
            feeServiceComposite = mockk(relaxed = true),
            chainValidationService = mockk(relaxed = true),
            requestAddressBookEntry = requestAddressBookEntry,
            getTronFrozenBalances = mockk(relaxed = true),
        )
    }

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
