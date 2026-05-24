package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronFrozenV2Json
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TronDeFiSnapshot
import com.vultisig.wallet.data.repositories.TronDeFiSnapshotDataSource
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class TronDeFiPositionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var vaultRepository: VaultRepository
    private lateinit var tronApi: TronApi
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    private lateinit var tokenPriceRepository: TokenPriceRepository
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var snapshotDataSource: TronDeFiSnapshotDataSource
    private lateinit var navigator: Navigator<Destination>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vaultRepository = mockk(relaxed = true)
        tronApi = mockk(relaxed = true)
        balanceVisibilityRepository = mockk(relaxed = true)
        tokenPriceRepository = mockk(relaxed = true)
        appCurrencyRepository = mockk(relaxed = true)
        snapshotDataSource = mockk(relaxed = true)
        navigator = mockk(relaxed = true)

        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { balanceVisibilityRepository.getVisibility(VAULT_ID) } returns true
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.US)
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns BigDecimal.ONE
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `renders cached snapshot as Success before the network responds, no skeleton`() = runTest {
        coEvery { snapshotDataSource.read(TRX_ADDRESS) } returns CACHED_SNAPSHOT
        // Suspend the network so the only state set so far is the cached emission.
        coEvery { tronApi.getAccount(TRX_ADDRESS) } coAnswers
            {
                CompletableDeferred<TronAccountJson>().await()
            }

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val state = vm.state.value
        assertTrue(state is TronDeFiUiState.Success, "expected cached Success, was $state")
        assertTrue((state as TronDeFiUiState.Success).tronData.hasFrozenBalance)
    }

    @Test
    fun `shows Loading skeleton when there is no cached snapshot`() = runTest {
        coEvery { snapshotDataSource.read(TRX_ADDRESS) } returns null
        coEvery { tronApi.getAccount(TRX_ADDRESS) } coAnswers
            {
                CompletableDeferred<TronAccountJson>().await()
            }

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertTrue(vm.state.value is TronDeFiUiState.Loading)
    }

    @Test
    fun `persists the fresh snapshot after a successful network load`() = runTest {
        coEvery { snapshotDataSource.read(TRX_ADDRESS) } returns null
        coEvery { tronApi.getAccount(TRX_ADDRESS) } returns FRESH_ACCOUNT
        coEvery { tronApi.getAccountResource(TRX_ADDRESS) } returns FRESH_RESOURCE
        coEvery { snapshotDataSource.write(any(), any()) } just Runs

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertTrue(vm.state.value is TronDeFiUiState.Success)
        coVerify(exactly = 1) {
            snapshotDataSource.write(
                TRX_ADDRESS,
                TronDeFiSnapshot(account = FRESH_ACCOUNT, resource = FRESH_RESOURCE),
            )
        }
    }

    @Test
    fun `keeps cached Success when a background refresh fails`() = runTest {
        coEvery { snapshotDataSource.read(TRX_ADDRESS) } returns CACHED_SNAPSHOT
        coEvery { tronApi.getAccount(TRX_ADDRESS) } throws RuntimeException("network down")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertTrue(
            vm.state.value is TronDeFiUiState.Success,
            "refresh failure must not replace shown data with an error screen",
        )
    }

    @Test
    fun `reports Error when the network fails and nothing is cached`() = runTest {
        coEvery { snapshotDataSource.read(TRX_ADDRESS) } returns null
        coEvery { tronApi.getAccount(TRX_ADDRESS) } throws RuntimeException("network down")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertTrue(vm.state.value is TronDeFiUiState.Error)
    }

    private fun createViewModel(): TronDeFiPositionsViewModel =
        TronDeFiPositionsViewModel(
            vaultRepository = vaultRepository,
            tronApi = tronApi,
            balanceVisibilityRepository = balanceVisibilityRepository,
            tokenPriceRepository = tokenPriceRepository,
            appCurrencyRepository = appCurrencyRepository,
            tronDeFiSnapshotDataSource = snapshotDataSource,
            navigator = navigator,
        )

    private companion object {
        const val VAULT_ID = "vault-1"
        const val TRX_ADDRESS = "TXYZtronAddress"

        val TRX_COIN = Coins.Tron.TRX.copy(address = TRX_ADDRESS)

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
                coins = listOf(TRX_COIN),
            )

        val CACHED_SNAPSHOT =
            TronDeFiSnapshot(
                account =
                    TronAccountJson(
                        address = TRX_ADDRESS,
                        balance = 1_000_000L,
                        frozenV2 = listOf(TronFrozenV2Json(type = "BANDWIDTH", amount = 5_000_000L)),
                    ),
                resource = TronAccountResourceJson(),
            )

        val FRESH_ACCOUNT =
            TronAccountJson(
                address = TRX_ADDRESS,
                balance = 2_000_000L,
                frozenV2 = listOf(TronFrozenV2Json(type = "ENERGY", amount = 9_000_000L)),
            )

        val FRESH_RESOURCE = TronAccountResourceJson(netLimit = 100L, energyLimit = 200L)
    }
}
