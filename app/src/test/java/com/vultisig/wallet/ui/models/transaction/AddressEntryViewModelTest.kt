@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.db.models.AddressBookOrderEntity
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.order.OrderRepository
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class AddressEntryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var requestQrScan: RequestQrScanUseCase
    private lateinit var addressBookRepository: AddressBookRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var orderRepository: OrderRepository<AddressBookOrderEntity>
    private lateinit var requestResultRepository: RequestResultRepository

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

    @Test
    fun `saveAddress with blank title sets titleError`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.saveAddress()
            assertNotNull(vm.state.value.titleError)
        }

    @Test
    fun `saveAddress with title over 100 chars sets titleError`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.titleTextFieldState.edit { replace(0, length, "A".repeat(101)) }
            vm.saveAddress()
            assertNotNull(vm.state.value.titleError)
        }

    @Test
    fun `saveAddress with invalid address sets addressError`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.titleTextFieldState.edit { replace(0, length, "Alice") }
            vm.addressTextFieldState.edit { replace(0, length, "invalid-address") }
            every { chainAccountAddressRepository.isValid(any(), any()) } returns false
            vm.saveAddress()
            assertNotNull(vm.state.value.addressError)
        }

    @Test
    fun `setOutputAddress sets the address field text`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.setOutputAddress("0xdeadbeef")
            assertEquals("0xdeadbeef", vm.addressTextFieldState.text.toString())
        }

    @Test
    fun `saveAddress with valid inputs calls addressBookRepository add`() =
        runTest(testDispatcher) {
            every { chainAccountAddressRepository.isValid(any(), any()) } returns true
            val vm = createViewModel()
            vm.titleTextFieldState.edit { replace(0, length, "Alice") }
            vm.addressTextFieldState.edit {
                replace(0, length, "0x1234567890123456789012345678901234567890")
            }
            vm.saveAddress()
            advanceUntilIdle()
            coVerify { addressBookRepository.add(any()) }
        }

    @Test
    fun `titleError is null initially`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            assertNull(vm.state.value.titleError)
        }

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
