@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.db.models.AccountOrderEntity
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.AccountOrderRepository
import com.vultisig.wallet.data.repositories.AccountsRepository
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

internal class SortAccountsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val vaultId = "test-vault-id"

    private lateinit var navigator: Navigator<Destination>
    private lateinit var accountsRepository: AccountsRepository
    private lateinit var accountOrderRepository: AccountOrderRepository
    private lateinit var savedStateHandle: SavedStateHandle

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        navigator = mockk(relaxed = true)
        accountsRepository = mockk(relaxed = true)
        accountOrderRepository = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle()

        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.SortAccounts>() } returns
            Route.SortAccounts(vaultId = vaultId)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    private fun createAddresses(vararg chains: Chain): List<Address> =
        chains.map { chain ->
            Address(chain = chain, address = "${chain.raw}-address", accounts = emptyList())
        }

    private fun createOrders(
        vararg entries: Triple<String, Float, Boolean>
    ): List<AccountOrderEntity> =
        entries.map { (chain, order, isPinned) ->
            AccountOrderEntity(vaultId = vaultId, chain = chain, order = order, isPinned = isPinned)
        }

    private fun createViewModel(): SortAccountsViewModel {
        return SortAccountsViewModel(
            savedStateHandle = savedStateHandle,
            navigator = navigator,
            accountsRepository = accountsRepository,
            accountOrderRepository = accountOrderRepository,
        )
    }

    @Test
    fun `initial state is empty`() {
        coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(emptyList())
        coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(emptyList())

        val vm = createViewModel()

        assertTrue(vm.uiState.value.pinnedAccounts.isEmpty())
        assertTrue(vm.uiState.value.unpinnedAccounts.isEmpty())
    }

    @Test
    fun `loads addresses without saved orders into unpinned`() {
        val addresses = createAddresses(Chain.Bitcoin, Chain.Ethereum, Chain.Solana)
        coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(addresses)
        coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(emptyList())

        val vm = createViewModel()

        assertTrue(vm.uiState.value.pinnedAccounts.isEmpty())
        assertEquals(3, vm.uiState.value.unpinnedAccounts.size)
    }

    @Test
    fun `loads addresses with saved orders into correct sections`() {
        val addresses = createAddresses(Chain.Bitcoin, Chain.Ethereum, Chain.Solana)
        val orders =
            createOrders(
                Triple(Chain.Ethereum.raw, 0f, true),
                Triple(Chain.Bitcoin.raw, 1f, false),
                Triple(Chain.Solana.raw, 2f, false),
            )
        coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(addresses)
        coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(orders)

        val vm = createViewModel()

        assertEquals(1, vm.uiState.value.pinnedAccounts.size)
        assertEquals(Chain.Ethereum.raw, vm.uiState.value.pinnedAccounts[0].chainId)
        assertEquals(2, vm.uiState.value.unpinnedAccounts.size)
        assertEquals(Chain.Bitcoin.raw, vm.uiState.value.unpinnedAccounts[0].chainId)
        assertEquals(Chain.Solana.raw, vm.uiState.value.unpinnedAccounts[1].chainId)
    }

    @Test
    fun `pinned accounts are sorted by saved order`() {
        val addresses = createAddresses(Chain.Bitcoin, Chain.Ethereum, Chain.Solana)
        val orders =
            createOrders(
                Triple(Chain.Solana.raw, 0f, true),
                Triple(Chain.Bitcoin.raw, 1f, true),
                Triple(Chain.Ethereum.raw, 2f, true),
            )
        coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(addresses)
        coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(orders)

        val vm = createViewModel()

        assertEquals(3, vm.uiState.value.pinnedAccounts.size)
        assertEquals(Chain.Solana.raw, vm.uiState.value.pinnedAccounts[0].chainId)
        assertEquals(Chain.Bitcoin.raw, vm.uiState.value.pinnedAccounts[1].chainId)
        assertEquals(Chain.Ethereum.raw, vm.uiState.value.pinnedAccounts[2].chainId)
    }

    @Test
    fun `togglePin moves unpinned item to pinned`() {
        val addresses = createAddresses(Chain.Bitcoin, Chain.Ethereum)
        coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(addresses)
        coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(emptyList())

        val vm = createViewModel()
        val item = vm.uiState.value.unpinnedAccounts.first()

        vm.togglePin(item)

        assertEquals(1, vm.uiState.value.pinnedAccounts.size)
        assertEquals(item.chainId, vm.uiState.value.pinnedAccounts[0].chainId)
        assertTrue(vm.uiState.value.pinnedAccounts[0].isPinned)
        assertEquals(1, vm.uiState.value.unpinnedAccounts.size)
    }

    @Test
    fun `togglePin moves pinned item to unpinned`() {
        val addresses = createAddresses(Chain.Bitcoin)
        val orders = createOrders(Triple(Chain.Bitcoin.raw, 0f, true))
        coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(addresses)
        coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(orders)

        val vm = createViewModel()
        val item = vm.uiState.value.pinnedAccounts.first()

        vm.togglePin(item)

        assertTrue(vm.uiState.value.pinnedAccounts.isEmpty())
        assertEquals(1, vm.uiState.value.unpinnedAccounts.size)
        assertFalse(vm.uiState.value.unpinnedAccounts[0].isPinned)
    }

    @Test
    fun `movePinned reorders pinned list`() {
        val addresses = createAddresses(Chain.Bitcoin, Chain.Ethereum, Chain.Solana)
        val orders =
            createOrders(
                Triple(Chain.Bitcoin.raw, 0f, true),
                Triple(Chain.Ethereum.raw, 1f, true),
                Triple(Chain.Solana.raw, 2f, true),
            )
        coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(addresses)
        coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(orders)

        val vm = createViewModel()

        vm.movePinned(0, 2)

        assertEquals(Chain.Ethereum.raw, vm.uiState.value.pinnedAccounts[0].chainId)
        assertEquals(Chain.Solana.raw, vm.uiState.value.pinnedAccounts[1].chainId)
        assertEquals(Chain.Bitcoin.raw, vm.uiState.value.pinnedAccounts[2].chainId)
    }

    @Test
    fun `moveUnpinned reorders unpinned list`() {
        val addresses = createAddresses(Chain.Bitcoin, Chain.Ethereum, Chain.Solana)
        coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(addresses)
        coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(emptyList())

        val vm = createViewModel()

        vm.moveUnpinned(2, 0)

        assertEquals(Chain.Solana.raw, vm.uiState.value.unpinnedAccounts[0].chainId)
        assertEquals(Chain.Bitcoin.raw, vm.uiState.value.unpinnedAccounts[1].chainId)
        assertEquals(Chain.Ethereum.raw, vm.uiState.value.unpinnedAccounts[2].chainId)
    }

    @Test
    fun `movePinned with out-of-bounds indices is ignored`() {
        val addresses = createAddresses(Chain.Bitcoin, Chain.Ethereum)
        val orders =
            createOrders(Triple(Chain.Bitcoin.raw, 0f, true), Triple(Chain.Ethereum.raw, 1f, true))
        coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(addresses)
        coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(orders)

        val vm = createViewModel()

        vm.movePinned(0, 5)

        assertEquals(Chain.Bitcoin.raw, vm.uiState.value.pinnedAccounts[0].chainId)
        assertEquals(Chain.Ethereum.raw, vm.uiState.value.pinnedAccounts[1].chainId)
    }

    @Test
    fun `moveUnpinned with out-of-bounds indices is ignored`() {
        val addresses = createAddresses(Chain.Bitcoin)
        coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(addresses)
        coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(emptyList())

        val vm = createViewModel()

        vm.moveUnpinned(-1, 0)

        assertEquals(1, vm.uiState.value.unpinnedAccounts.size)
    }

    @Test
    fun `save persists correct order and navigates back`() =
        runTest(testDispatcher) {
            val addresses = createAddresses(Chain.Bitcoin, Chain.Ethereum, Chain.Solana)
            val orders =
                createOrders(
                    Triple(Chain.Ethereum.raw, 0f, true),
                    Triple(Chain.Bitcoin.raw, 1f, false),
                    Triple(Chain.Solana.raw, 2f, false),
                )
            coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(addresses)
            coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(orders)
            coEvery { accountOrderRepository.saveOrders(any(), any()) } returns Unit

            val vm = createViewModel()

            vm.save()

            coVerify {
                accountOrderRepository.saveOrders(
                    vaultId,
                    withArg { savedOrders ->
                        assertEquals(3, savedOrders.size)

                        val pinned = savedOrders.filter { it.isPinned }
                        assertEquals(1, pinned.size)
                        assertEquals(Chain.Ethereum.raw, pinned[0].chain)
                        assertEquals(0f, pinned[0].order)

                        val unpinned = savedOrders.filter { !it.isPinned }
                        assertEquals(2, unpinned.size)
                        assertEquals(Chain.Bitcoin.raw, unpinned[0].chain)
                        assertEquals(Chain.Solana.raw, unpinned[1].chain)
                    },
                )
            }
            coVerify { navigator.navigate(Destination.Back) }
        }

    @Test
    fun `back navigates back`() =
        runTest(testDispatcher) {
            coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(emptyList())
            coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(emptyList())

            val vm = createViewModel()

            vm.back()

            coVerify { navigator.navigate(Destination.Back) }
        }

    @Test
    fun `save does not navigate back if repository throws`() =
        runTest(testDispatcher) {
            coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(emptyList())
            coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(emptyList())
            coEvery { accountOrderRepository.saveOrders(any(), any()) } throws
                RuntimeException("DB error")

            val vm = createViewModel()

            vm.save()

            coVerify(exactly = 0) { navigator.navigate(Destination.Back) }
        }

    @Test
    fun `togglePin then save persists updated pin state`() =
        runTest(testDispatcher) {
            val addresses = createAddresses(Chain.Bitcoin, Chain.Ethereum)
            coEvery { accountsRepository.loadAddresses(vaultId, false) } returns flowOf(addresses)
            coEvery { accountOrderRepository.loadOrders(vaultId) } returns flowOf(emptyList())
            coEvery { accountOrderRepository.saveOrders(any(), any()) } returns Unit

            val vm = createViewModel()

            val itemToPin = vm.uiState.value.unpinnedAccounts.first()
            vm.togglePin(itemToPin)
            vm.save()

            coVerify {
                accountOrderRepository.saveOrders(
                    vaultId,
                    withArg { savedOrders ->
                        val pinned = savedOrders.filter { it.isPinned }
                        assertEquals(1, pinned.size)
                        assertEquals(itemToPin.chainId, pinned[0].chain)
                    },
                )
            }
        }
}
