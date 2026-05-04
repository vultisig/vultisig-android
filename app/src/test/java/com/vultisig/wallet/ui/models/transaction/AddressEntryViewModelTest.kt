@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.db.models.AddressBookOrderEntity
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.order.OrderRepository
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
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

/** Unit tests for [AddressEntryViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AddressEntryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var requestQrScan: RequestQrScanUseCase
    private lateinit var addressBookRepository: AddressBookRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var orderRepository: OrderRepository<AddressBookOrderEntity>
    private lateinit var requestResultRepository: RequestResultRepository

    /** Sets up mocks and test dispatcher before each test. */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.AddressEntry>() } returns
            Route.AddressEntry(chainId = null, address = null, vaultId = VAULT_ID)
        navigator = mockk(relaxed = true)
        requestQrScan = mockk(relaxed = true)
        addressBookRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
        orderRepository = mockk(relaxed = true)
        requestResultRepository = mockk(relaxed = true)
    }

    /** Cleans up mocks and resets test dispatcher after each test. */
    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        AddressEntryViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            requestQrScan = requestQrScan,
            addressBookRepository = addressBookRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            orderRepository = orderRepository,
            requestResultRepository = requestResultRepository,
        )

    /** Verifies saveAddress with blank title sets titleError. */
    @Test
    fun `saveAddress with blank title sets titleError`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.saveAddress()
            vm.state.value.titleError.shouldNotBeNull()
        }

    /** Verifies saveAddress with title length over the production max sets titleError. */
    @Test
    fun `saveAddress with title over max length sets titleError`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            // OVER_MAX_LABEL exceeds the production constant LABEL_MAX_LENGTH
            // (private in AddressEntryViewModel, currently 100). If that constant
            // changes, update OVER_MAX_LABEL to keep this test meaningful.
            vm.titleTextFieldState.edit { replace(0, length, "A".repeat(OVER_MAX_LABEL)) }
            vm.saveAddress()
            vm.state.value.titleError.shouldNotBeNull()
        }

    /** Verifies saveAddress with invalid address sets addressError. */
    @Test
    fun `saveAddress with invalid address sets addressError`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.titleTextFieldState.edit { replace(0, length, "Alice") }
            vm.addressTextFieldState.edit { replace(0, length, "invalid-address") }
            every { chainAccountAddressRepository.isValid(any(), any()) } returns false
            vm.saveAddress()
            vm.state.value.addressError.shouldNotBeNull()
        }

    /** Verifies setOutputAddress sets the address field text. */
    @Test
    fun `setOutputAddress sets the address field text`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.setOutputAddress("0xdeadbeef")
            vm.addressTextFieldState.text.toString() shouldBe "0xdeadbeef"
        }

    /** Verifies setOutputAddress trims surrounding whitespace. */
    @Test
    fun `setOutputAddress trims surrounding whitespace`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.setOutputAddress("   $ETH_ADDRESS   ")
            vm.addressTextFieldState.text.toString() shouldBe ETH_ADDRESS
        }

    /**
     * Verifies saveAddress with valid inputs persists an [AddressBookEntry] carrying the exact
     * title/address typed, and navigates back on completion.
     */
    @Test
    fun `saveAddress with valid inputs persists entry and navigates back`() =
        runTest(testDispatcher) {
            every { chainAccountAddressRepository.isValid(any(), any()) } returns true
            val capturedEntry = slot<AddressBookEntry>()
            coEvery { addressBookRepository.add(capture(capturedEntry)) } returns Unit
            val vm = createViewModel()
            vm.titleTextFieldState.edit { replace(0, length, "Alice") }
            vm.addressTextFieldState.edit { replace(0, length, ETH_ADDRESS) }
            vm.saveAddress()
            advanceUntilIdle()
            coVerify { addressBookRepository.add(any()) }
            capturedEntry.captured.title shouldBe "Alice"
            capturedEntry.captured.address shouldBe ETH_ADDRESS
            coVerify { navigator.navigate(Destination.Back) }
        }

    /** Verifies saveAddress passes the pre-selected Bitcoin chain to address validation. */
    @Test
    fun `saveAddress validates address against the pre-selected Bitcoin chain`() =
        runTest(testDispatcher) {
            every { any<SavedStateHandle>().toRoute<Route.AddressEntry>() } returns
                Route.AddressEntry(chainId = "Bitcoin", address = "bc1q0000", vaultId = VAULT_ID)
            every { chainAccountAddressRepository.isValid(Chain.Bitcoin, any()) } returns true
            val vm = createViewModel()
            vm.titleTextFieldState.edit { replace(0, length, "Alice") }

            vm.saveAddress()
            advanceUntilIdle()

            verify { chainAccountAddressRepository.isValid(Chain.Bitcoin, "bc1q0000") }
            vm.state.value.addressError.shouldBeNull()
        }

    /** Verifies saveAddress passes the pre-selected Ethereum chain to address validation. */
    @Test
    fun `saveAddress validates address against the pre-selected Ethereum chain`() =
        runTest(testDispatcher) {
            every { any<SavedStateHandle>().toRoute<Route.AddressEntry>() } returns
                Route.AddressEntry(chainId = "Ethereum", address = ETH_ADDRESS, vaultId = VAULT_ID)
            every { chainAccountAddressRepository.isValid(Chain.Ethereum, any()) } returns true
            val vm = createViewModel()
            vm.titleTextFieldState.edit { replace(0, length, "Alice") }

            vm.saveAddress()
            advanceUntilIdle()

            verify { chainAccountAddressRepository.isValid(Chain.Ethereum, ETH_ADDRESS) }
            vm.state.value.addressError.shouldBeNull()
        }

    /** Verifies saveAddress passes the pre-selected Solana chain to address validation. */
    @Test
    fun `saveAddress validates address against the pre-selected Solana chain`() =
        runTest(testDispatcher) {
            every { any<SavedStateHandle>().toRoute<Route.AddressEntry>() } returns
                Route.AddressEntry(chainId = "Solana", address = SOL_ADDRESS, vaultId = VAULT_ID)
            every { chainAccountAddressRepository.isValid(Chain.Solana, any()) } returns true
            val vm = createViewModel()
            vm.titleTextFieldState.edit { replace(0, length, "Alice") }

            vm.saveAddress()
            advanceUntilIdle()

            verify { chainAccountAddressRepository.isValid(Chain.Solana, SOL_ADDRESS) }
            vm.state.value.addressError.shouldBeNull()
        }

    /**
     * Verifies scanAddress wires through [RequestQrScanUseCase] and writes the scanned QR payload
     * into the address text field via setOutputAddress.
     */
    @Test
    fun `scanAddress populates address field from QR scan result`() =
        runTest(testDispatcher) {
            coEvery { requestQrScan() } returns ETH_ADDRESS
            val vm = createViewModel()

            vm.scanAddress()
            advanceUntilIdle()

            coVerify { requestQrScan() }
            vm.addressTextFieldState.text.toString() shouldBe ETH_ADDRESS
        }

    /**
     * Verifies scanAddress does NOT update the address field when the QR scan returns null (e.g.,
     * user cancelled the scan).
     */
    @Test
    fun `scanAddress leaves address empty when QR result is null`() =
        runTest(testDispatcher) {
            coEvery { requestQrScan() } returns null
            val vm = createViewModel()

            vm.scanAddress()
            advanceUntilIdle()

            coVerify { requestQrScan() }
            vm.addressTextFieldState.text.toString() shouldBe ""
        }

    /**
     * Verifies the address-book lookup path: when the route carries an existing (chainId, address)
     * pair, the VM queries the repository and pre-populates the form via the edit branch.
     */
    @Test
    fun `init with existing entry loads it from address book and prefills form`() =
        runTest(testDispatcher) {
            coEvery { addressBookRepository.entryExists("Ethereum", ETH_ADDRESS) } returns true
            coEvery { addressBookRepository.getEntry("Ethereum", ETH_ADDRESS) } returns
                AddressBookEntry(chain = Chain.Ethereum, address = ETH_ADDRESS, title = "Bob")
            every { any<SavedStateHandle>().toRoute<Route.AddressEntry>() } returns
                Route.AddressEntry(chainId = "Ethereum", address = ETH_ADDRESS, vaultId = VAULT_ID)

            val vm = createViewModel()
            advanceUntilIdle()

            coVerify { addressBookRepository.entryExists("Ethereum", ETH_ADDRESS) }
            coVerify { addressBookRepository.getEntry("Ethereum", ETH_ADDRESS) }
            vm.titleTextFieldState.text.toString() shouldBe "Bob"
            vm.addressTextFieldState.text.toString() shouldBe ETH_ADDRESS
        }

    /**
     * Verifies the address-book lookup path: when the route carries an (chainId, address) pair for
     * which no existing entry is found, the VM takes the create branch and only seeds the address
     * text field (title remains empty for the user to fill in).
     */
    @Test
    fun `init with non-existing entry takes create branch and seeds only the address`() =
        runTest(testDispatcher) {
            coEvery { addressBookRepository.entryExists("Ethereum", ETH_ADDRESS) } returns false
            every { any<SavedStateHandle>().toRoute<Route.AddressEntry>() } returns
                Route.AddressEntry(chainId = "Ethereum", address = ETH_ADDRESS, vaultId = VAULT_ID)

            val vm = createViewModel()
            advanceUntilIdle()

            coVerify { addressBookRepository.entryExists("Ethereum", ETH_ADDRESS) }
            coVerify(exactly = 0) { addressBookRepository.getEntry(any(), any()) }
            vm.titleTextFieldState.text.toString() shouldBe ""
            vm.addressTextFieldState.text.toString() shouldBe ETH_ADDRESS
        }

    /**
     * Verifies that an unsupported chainId in the route does not crash the VM and that calling
     * [AddressEntryViewModel.saveAddress] sets an address error without invoking
     * [ChainAccountAddressRepository.isValid] (address field is empty so the blank-address guard
     * fires first).
     */
    @Test
    fun `saveAddress with unsupported chainId does not invoke isValid and sets addressError`() =
        runTest(testDispatcher) {
            every { any<SavedStateHandle>().toRoute<Route.AddressEntry>() } returns
                Route.AddressEntry(chainId = "UnknownChain", address = null, vaultId = VAULT_ID)
            val vm = createViewModel()
            vm.titleTextFieldState.edit { replace(0, length, "Alice") }

            vm.saveAddress()
            advanceUntilIdle()

            verify(exactly = 0) { chainAccountAddressRepository.isValid(any(), any()) }
            vm.state.value.addressError.shouldNotBeNull()
        }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val ETH_ADDRESS = "0x1234567890123456789012345678901234567890"
        const val SOL_ADDRESS = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"

        // Length that exceeds the private LABEL_MAX_LENGTH constant in AddressEntryViewModel.
        // If the production constant changes, update this and the matching test name.
        const val OVER_MAX_LABEL = 101
    }
}
