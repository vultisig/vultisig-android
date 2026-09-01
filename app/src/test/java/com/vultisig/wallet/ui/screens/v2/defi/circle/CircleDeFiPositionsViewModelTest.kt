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
import com.vultisig.wallet.ui.models.defi.DeFiPositionsSnapshotCache
import com.vultisig.wallet.ui.models.defi.clearForTest
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.model.DefiUiModel
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.text.NumberFormat
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
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
    // The real cache, not a mock: these tests assert the round trip a nav pop and a re-entry make.
    private lateinit var snapshotCache: DeFiPositionsSnapshotCache

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
        snapshotCache = DeFiPositionsSnapshotCache()
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
    fun `onCreateAccount shows Success and sets isAccountOpen true even when local save throws`() =
        runTest {
            val vm = createViewModel().also { it.setData(VAULT_ID) }
            givenNoExistingAccount()
            coEvery { circleApi.createScAccount(OWNER_ADDRESS) } returns MSCA_ADDRESS
            coEvery { scaCircleAccountRepository.saveAccount(VAULT_ID, MSCA_ADDRESS) } throws
                RuntimeException("DataStore IO error")

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

    @Test
    fun `a user with no Circle account yet sees a real zero, not the unavailable dash`() = runTest {
        // The no-account branch settled the spinner but never set a price, so once the hardcoded
        // "$0.00" default was removed every brand-new user landed on the unavailable marker.
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.US)

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val state = vm.state.value
        assertFalse(state.isTotalAmountLoading)
        assertEquals("$0.00", state.totalAmountPrice)
        assertEquals("$0.00", state.circleDefi.totalDepositCurrency)
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
        coEvery { snackbarFlow.showMessage(any<String>(), capture(captured)) } just Runs
        action()
        return captured.captured
    }

    @Test
    fun `a re-entry paints the deposit the screen was last showing`() = runTest {
        // Popping back to the DeFi list destroys this view-model; without a snapshot the next open
        // puts the banner back on its loading state while the account and balance are re-read.
        snapshotCache.write(VAULT_ID, LAST_RENDERED)
        // Suspend the account read so the only state on screen is the restored one.
        coEvery { scaCircleAccountRepository.getAccount(VAULT_ID) } coAnswers
            {
                CompletableDeferred<String?>().await()
            }

        val state = createViewModel().also { it.setData(VAULT_ID) }.state.value

        assertEquals("$500.00", state.totalAmountPrice)
        assertFalse(state.isTotalAmountLoading)
        assertFalse(state.circleDefi.isLoading)
        assertEquals("500 USDC", state.circleDefi.totalDeposit)
    }

    @Test
    fun `a re-entry keeps the account withdrawals route to`() = runTest {
        // The MSCA address lives outside the state, so it has to travel with the snapshot —
        // otherwise a restored open account offers a Withdraw that silently does nothing.
        snapshotCache.write(VAULT_ID, LAST_RENDERED)
        coEvery { scaCircleAccountRepository.getAccount(VAULT_ID) } coAnswers
            {
                CompletableDeferred<String?>().await()
            }

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        vm.onWithdrawAccount()

        coVerify(exactly = 1) {
            navigator.route(match<Route.Send> { it.mscaAddress == MSCA_ADDRESS })
        }
    }

    @Test
    fun `hands the rendered state to the cache when the screen is popped`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }
        val rendered = vm.state.value

        vm.clearForTest()

        assertEquals(
            CircleDeFiSnapshot(model = rendered, mscaAddress = null),
            snapshotCache.read(VAULT_ID, CircleDeFiSnapshot::class),
        )
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
            snapshotCache = snapshotCache,
            context = context,
            ioDispatcher = testDispatcher,
        )

    private companion object {
        /** A settled screen, as the cache would have it after the user walked away from one. */
        val LAST_RENDERED =
            CircleDeFiSnapshot(
                model =
                    DefiUiModel(
                        totalAmountPrice = "$500.00",
                        isTotalAmountLoading = false,
                        circleDefi =
                            DefiUiModel.CircleDeFi(
                                isAccountOpen = true,
                                totalDeposit = "500 USDC",
                                totalDepositCurrency = "$500.00",
                            ),
                    ),
                mscaAddress = MSCA_ADDRESS,
            )

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
