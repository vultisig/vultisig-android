package com.vultisig.wallet.ui.screens.v2.defi.circle

import android.content.Context
import com.vultisig.wallet.data.api.CircleApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.ScaCircleAccountRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class CircleDeFiPositionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var scaCircleAccountRepository: ScaCircleAccountRepository
    private lateinit var circleApi: CircleApi
    private lateinit var evmApi: EvmApiFactory
    private lateinit var vaultRepository: VaultRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var snackbarFlow: SnackbarFlow
    private lateinit var stakingDetailsRepository: StakingDetailsRepository
    private lateinit var tokenPriceRepository: TokenPriceRepository
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        navigator = mockk(relaxed = true)
        scaCircleAccountRepository = mockk(relaxed = true)
        circleApi = mockk(relaxed = true)
        evmApi = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
        snackbarFlow = mockk(relaxed = true)
        stakingDetailsRepository = mockk(relaxed = true)
        tokenPriceRepository = mockk(relaxed = true)
        appCurrencyRepository = mockk(relaxed = true)
        balanceVisibilityRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { chainAccountAddressRepository.getAddress(Chain.Ethereum, VAULT) } returns
            (OWNER_ADDRESS to "pubKey")
        coEvery { scaCircleAccountRepository.getAccount(VAULT_ID) } returns null
        // relaxed mockk returns "" for String?, which would be treated as "account found"
        // by fetchAssociatedMscaAccount — explicitly return null to simulate "no account yet".
        coEvery { circleApi.getScAccount(any()) } returns null
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onCreateAccount surfaces failures with an Error snackbar, not Success`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }
        givenNoExistingAccount()
        coEvery { circleApi.createScAccount(OWNER_ADDRESS) } throws
            NetworkException(500, "Entity needs to setup paymaster policy")

        val type = snackbarTypeShownBy { vm.onCreateAccount() }

        assertEquals(SnackbarType.Error, type)
        coVerify(exactly = 0) { scaCircleAccountRepository.saveAccount(any(), any()) }
        assertFalse(vm.state.value.circleDefi.isAccountOpen)
    }

    @Test
    fun `onCreateAccount shows a Success snackbar and saves the new address on success`() =
        runTest {
            val vm = createViewModel().also { it.setData(VAULT_ID) }
            givenNoExistingAccount()
            coEvery { scaCircleAccountRepository.saveAccount(VAULT_ID, MSCA_ADDRESS) } just Runs
            coEvery { circleApi.createScAccount(OWNER_ADDRESS) } returns MSCA_ADDRESS

            val type = snackbarTypeShownBy { vm.onCreateAccount() }

            assertEquals(SnackbarType.Success, type)
            coVerify(exactly = 1) { scaCircleAccountRepository.saveAccount(VAULT_ID, MSCA_ADDRESS) }
            assertTrue(vm.state.value.circleDefi.isAccountOpen)
        }

    @Test
    fun `onCreateAccount reports an Error when the vault cannot be resolved`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }
        givenNoExistingAccount()
        // `getEvmVaultAddress()` calls `error(...)` when the vault lookup returns null;
        // the resulting IllegalStateException must be caught by `runCatching` inside
        // `onCreateAccount` and surfaced as a user-visible error, never reach the API.
        coEvery { vaultRepository.get(VAULT_ID) } returns null

        val type = snackbarTypeShownBy { vm.onCreateAccount() }

        assertEquals(SnackbarType.Error, type)
        coVerify(exactly = 0) { circleApi.createScAccount(any()) }
        coVerify(exactly = 0) { scaCircleAccountRepository.saveAccount(any(), any()) }
    }

    /**
     * Resets recorded calls on the snackbar and account-repository mocks after `setData(...)` has
     * populated the initial state, and re-asserts "no account persisted yet" so `onCreateAccount`
     * takes the create path. Keeps each test focused on the action under test.
     */
    private fun givenNoExistingAccount() {
        clearMocks(snackbarFlow, scaCircleAccountRepository, answers = false, childMocks = false)
        coEvery { scaCircleAccountRepository.getAccount(VAULT_ID) } returns null
    }

    /**
     * Captures the `SnackbarType` argument passed to `snackbarFlow.showMessage(...)` by [action].
     * Because the ViewModel runs on the test dispatcher (Main and IO are both the shared
     * `testDispatcher`), by the time `action()` returns every launched coroutine has run to
     * completion — no real-time wait or virtual-clock advance is needed.
     */
    private fun snackbarTypeShownBy(action: () -> Unit): SnackbarType {
        val captured = slot<SnackbarType>()
        coEvery { snackbarFlow.showMessage(any(), capture(captured)) } just Runs
        action()
        return captured.captured
    }

    private fun createViewModel(): CircleDeFiPositionsViewModel =
        CircleDeFiPositionsViewModel(
            navigator = navigator,
            scaCircleAccountRepository = scaCircleAccountRepository,
            circleApi = circleApi,
            evmApi = evmApi,
            vaultRepository = vaultRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            snackbarFlow = snackbarFlow,
            stakingDetailsRepository = stakingDetailsRepository,
            tokenPriceRepository = tokenPriceRepository,
            appCurrencyRepository = appCurrencyRepository,
            balanceVisibilityRepository = balanceVisibilityRepository,
            context = context,
            ioDispatcher = testDispatcher,
        )

    private companion object {
        const val VAULT_ID = "vault-1"
        const val OWNER_ADDRESS = "0x087077528E7028f4880e6b9DaD082910b7dfe0d2"
        const val MSCA_ADDRESS = "0xNewMscaAccount"
        val VAULT =
            Vault(
                id = VAULT_ID,
                name = "Vultisig Wallet",
                pubKeyECDSA = "",
                pubKeyEDDSA = "",
                hexChainCode = "",
                localPartyID = "",
                signers = emptyList(),
                resharePrefix = "",
                libType = SigningLibType.DKLS,
            )
    }
}
