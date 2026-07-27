package com.vultisig.wallet.ui.models.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.db.models.AddressBookOrderEntity
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.order.OrderRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
internal class AddressBookViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var addressBookRepository: AddressBookRepository
    private lateinit var requestResultRepository: RequestResultRepository
    private lateinit var orderRepository: OrderRepository<AddressBookOrderEntity>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.AddressBookScreen>() } returns
            Route.AddressBookScreen(vaultId = VAULT_ID)
        navigator = mockk(relaxed = true)
        addressBookRepository = mockk(relaxed = true)
        requestResultRepository = mockk(relaxed = true)
        orderRepository = mockk(relaxed = true)
        coEvery { addressBookRepository.getEntries() } returns emptyList()
        every { orderRepository.loadOrders(null) } returns emptyFlow()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `requestDeleteAddress stages the entry without deleting`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val entry = entry("Alice")

            vm.requestDeleteAddress(entry)

            assertSame(entry, vm.state.value.pendingDeletion)
            coVerify(exactly = 0) { addressBookRepository.delete(any(), any()) }
            coVerify(exactly = 0) { orderRepository.delete(any(), any()) }
        }

    @Test
    fun `cancelDeleteAddress clears the pending entry and never deletes`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.requestDeleteAddress(entry("Alice"))

            vm.cancelDeleteAddress()

            assertNull(vm.state.value.pendingDeletion)
            coVerify(exactly = 0) { addressBookRepository.delete(any(), any()) }
            coVerify(exactly = 0) { orderRepository.delete(any(), any()) }
        }

    @Test
    fun `confirmDeleteAddress deletes the pending entry and clears the state`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val entry = entry("Alice", address = ALICE_ADDRESS, chain = Chain.Ethereum)
            vm.requestDeleteAddress(entry)

            vm.confirmDeleteAddress()
            advanceUntilIdle()

            assertNull(vm.state.value.pendingDeletion)
            coVerify(exactly = 1) {
                addressBookRepository.delete(chainId = Chain.Ethereum.id, address = ALICE_ADDRESS)
            }
            coVerify(exactly = 1) { orderRepository.delete(null, entry.model.id) }
        }

    @Test
    fun `confirmDeleteAddress without a pending entry is a no-op`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.confirmDeleteAddress()
            advanceUntilIdle()

            assertNull(vm.state.value.pendingDeletion)
            coVerify(exactly = 0) { addressBookRepository.delete(any(), any()) }
            coVerify(exactly = 0) { orderRepository.delete(any(), any()) }
        }

    @Test
    fun `confirm after cancel does not delete the previously staged entry`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.requestDeleteAddress(entry("Alice"))
            vm.cancelDeleteAddress()

            vm.confirmDeleteAddress()
            advanceUntilIdle()

            coVerify(exactly = 0) { addressBookRepository.delete(any(), any()) }
        }

    @Test
    fun `consecutive requests overwrite pending and confirm deletes only the latest`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.requestDeleteAddress(entry("Alice", address = ALICE_ADDRESS))
            val bob = entry("Bob", address = BOB_ADDRESS)
            vm.requestDeleteAddress(bob)
            clearMocks(addressBookRepository, orderRepository, answers = false)

            vm.confirmDeleteAddress()
            advanceUntilIdle()

            coVerify(exactly = 1) {
                addressBookRepository.delete(chainId = Chain.Ethereum.id, address = BOB_ADDRESS)
            }
            coVerify(exactly = 0) {
                addressBookRepository.delete(chainId = any(), address = ALICE_ADDRESS)
            }
            coVerify(exactly = 1) { orderRepository.delete(null, bob.model.id) }
        }

    @Test
    fun `confirmDeleteAddress calls loadData to refresh the list after successful delete`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.requestDeleteAddress(entry("Alice", address = ALICE_ADDRESS))
            clearMocks(orderRepository, answers = false)

            vm.confirmDeleteAddress()
            advanceUntilIdle()

            coVerify(exactly = 1) { addressBookRepository.delete(any(), any()) }
            // loadData() subscribes to orderRepository.loadOrders to refresh the list
            coVerify(atLeast = 1) { orderRepository.loadOrders(null) }
        }

    @Test
    fun `confirmDeleteAddress clears pending state even when repository throws`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            coEvery { addressBookRepository.delete(any(), any()) } throws
                RuntimeException("DB error")
            vm.requestDeleteAddress(entry("Alice", address = ALICE_ADDRESS))

            vm.confirmDeleteAddress()
            advanceUntilIdle()

            assertNull(vm.state.value.pendingDeletion)
        }

    @Test
    fun `initial pending deletion is null`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            assertEquals(null, vm.state.value.pendingDeletion)
        }

    @Test
    fun `EVM entries show EVM as the network label`() =
        runTest(testDispatcher) {
            coEvery { addressBookRepository.getEntries() } returns
                listOf(
                    AddressBookEntry(
                        chain = Chain.Ethereum,
                        address = ALICE_ADDRESS,
                        title = "Alice",
                    )
                )
            every { orderRepository.loadOrders(null) } returns flowOf(emptyList())
            val vm = createViewModel()

            vm.loadData()
            advanceUntilIdle()

            assertEquals("EVM", vm.state.value.entries.single().network)
        }

    @Test
    fun `non-EVM entries keep their chain name as the network label`() =
        runTest(testDispatcher) {
            coEvery { addressBookRepository.getEntries() } returns
                listOf(
                    AddressBookEntry(chain = Chain.Bitcoin, address = BTC_ADDRESS, title = "Cold")
                )
            every { orderRepository.loadOrders(null) } returns flowOf(emptyList())
            val vm = createViewModel()

            vm.loadData()
            advanceUntilIdle()

            assertEquals("Bitcoin", vm.state.value.entries.single().network)
        }

    @Test
    fun `loadData filtered by one EVM chain still surfaces entries saved under another`() =
        runTest(testDispatcher) {
            every { any<SavedStateHandle>().toRoute<Route.AddressBookScreen>() } returns
                Route.AddressBookScreen(vaultId = VAULT_ID, chainId = Chain.Arbitrum.id)
            coEvery { addressBookRepository.getEntries() } returns
                listOf(
                    AddressBookEntry(
                        chain = Chain.Ethereum,
                        address = ALICE_ADDRESS,
                        title = "Alice",
                    ),
                    AddressBookEntry(chain = Chain.Bitcoin, address = BTC_ADDRESS, title = "Cold"),
                )
            every { orderRepository.loadOrders(null) } returns flowOf(emptyList())
            val vm = createViewModel()

            vm.loadData()
            advanceUntilIdle()

            assertEquals(listOf("Alice"), vm.state.value.entries.map { it.name })
        }

    private fun createViewModel(): AddressBookViewModel =
        AddressBookViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            addressBookRepository = addressBookRepository,
            requestResultRepository = requestResultRepository,
            orderRepository = orderRepository,
        )

    private fun entry(
        name: String,
        address: String = ALICE_ADDRESS,
        chain: Chain = Chain.Ethereum,
    ) =
        AddressBookEntryUiModel(
            model = AddressBookEntry(chain = chain, address = address, title = name),
            image = "",
            name = name,
            network = chain.name,
            address = address,
        )

    private companion object {
        const val VAULT_ID = "vault-1"
        const val ALICE_ADDRESS = "0xAlice"
        const val BOB_ADDRESS = "0xBob"
        const val BTC_ADDRESS = "1BoatSLRHtKNngkdXEeobR76b53LETtpyT"
    }
}
