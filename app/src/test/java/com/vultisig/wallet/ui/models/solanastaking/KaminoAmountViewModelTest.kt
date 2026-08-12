package com.vultisig.wallet.ui.models.solanastaking

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.KaminoUserPositionJson
import com.vultisig.wallet.data.api.KaminoVaultMetricsJson
import com.vultisig.wallet.data.api.KaminoVaultStateJson
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.blockchain.solana.kamino.BuildKaminoKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoWithdrawEligibility
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.OPERATION_KAMINO_DEPOSIT
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class KaminoAmountViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var kaminoApi: KaminoApi
    private lateinit var solanaApi: SolanaApi
    private lateinit var vaultRepository: VaultRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var balanceRepository: BalanceRepository
    private lateinit var blockChainSpecificRepository: BlockChainSpecificRepository
    private lateinit var buildKeysignPayload: BuildKaminoKeysignPayloadUseCase
    private lateinit var depositTransactionRepository: DepositTransactionRepository
    private lateinit var navigator: Navigator<Destination>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // `toRoute` reaches into android.os.Bundle, which is not available to JVM tests, so the
        // route is stubbed the same way the other route-carrying ViewModel tests do it.
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        kaminoApi = mockk(relaxed = true)
        solanaApi = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
        balanceRepository = mockk(relaxed = true)
        blockChainSpecificRepository = mockk(relaxed = true)
        buildKeysignPayload = mockk(relaxed = true)
        depositTransactionRepository = mockk(relaxed = true)
        navigator = mockk(relaxed = true)

        // 165-byte SPL token account rent-exempt reserve, the real mainnet figure.
        coEvery { solanaApi.getMinimumBalanceForRentExemption() } returns BigInteger("2039280")
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { chainAccountAddressRepository.getAddress(Chain.Solana, VAULT) } returns
            (WALLET to "pubkey")
        coEvery { kaminoApi.getVaultState(any()) } returns
            KaminoVaultStateJson(
                address = STEAKHOUSE.address,
                state =
                    KaminoVaultStateJson.State(
                        name = "Steakhouse USDC",
                        tokenMint = STEAKHOUSE.tokenMint,
                        tokenDecimals = 6,
                        sharesMint = STEAKHOUSE.sharesMint,
                        sharesDecimals = 6,
                        minDepositAmount = "100000",
                        minWithdrawAmount = "1000",
                    ),
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun viewModel(
        vaultAddress: String = STEAKHOUSE.address,
        isWithdraw: Boolean = false,
    ): KaminoAmountViewModel {
        every { any<SavedStateHandle>().toRoute<Route.KaminoAmount>() } returns
            Route.KaminoAmount(
                vaultId = VAULT_ID,
                vaultAddress = vaultAddress,
                isWithdraw = isWithdraw,
            )
        return KaminoAmountViewModel(
            savedStateHandle = mockk(relaxed = true),
            kaminoApi = kaminoApi,
            solanaApi = solanaApi,
            vaultRepository = vaultRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            balanceRepository = balanceRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            buildKeysignPayload = buildKeysignPayload,
            depositTransactionRepository = depositTransactionRepository,
            navigator = navigator,
        )
    }

    private fun givenTokenBalance(baseUnits: String) {
        coEvery { balanceRepository.getTokenValue(any(), any()) } returns
            flowOf(TokenValue(value = BigInteger(baseUnits), token = COIN))
    }

    @Test
    fun `a deposit caps against the wallet token balance`() = runTest {
        givenTokenBalance("2500000000") // 2,500 USDC at 6 decimals

        val state = viewModel().state.value

        assertFalse(state.isWithdraw)
        assertEquals(0, BigDecimal("2500").compareTo(state.available))
        assertEquals("Steakhouse USDC", state.vaultName)
        assertEquals("USDC", state.ticker)
        assertFalse(state.isLoading)
    }

    @Test
    fun `the deposit minimum comes from vault state, converted out of base units`() = runTest {
        givenTokenBalance("2500000000")

        // 100000 base units at 6 decimals is 0.1 USDC — not 100,000.
        assertEquals(0, BigDecimal("0.1").compareTo(viewModel().state.value.minimum!!))
    }

    @Test
    fun `a withdraw caps against the position, not the wallet`() = runTest {
        givenTokenBalance("0") // nothing in the wallet; the position is what matters
        coEvery { kaminoApi.getUserPositions(WALLET) } returns
            listOf(
                KaminoUserPositionJson(
                    vaultAddress = STEAKHOUSE.address,
                    stakedShares = "0",
                    unstakedShares = "1000",
                    totalShares = "1000",
                )
            )
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0544278224860290217")

        val state = viewModel(isWithdraw = true).state.value

        assertTrue(state.isWithdraw)
        // 999999999 share base units (one below the balance, stepping back from the API's sentinel)
        // × 1.0544278224860290217, truncated to the token's 6 decimals.
        assertEquals(0, BigDecimal("1054.427821").compareTo(state.available))
        // minWithdrawAmount is 1000 SHARE base units, so in tokens it is 0.001055 rounded up — not
        // the 0.001 a token-denominated reading would give.
        assertEquals(0, BigDecimal("0.001055").compareTo(state.minimum!!))
    }

    @Test
    fun `a withdraw with no position offers nothing rather than the wallet balance`() = runTest {
        givenTokenBalance("2500000000")
        coEvery { kaminoApi.getUserPositions(WALLET) } returns emptyList()

        assertEquals(
            0,
            BigDecimal.ZERO.compareTo(viewModel(isWithdraw = true).state.value.available),
        )
    }

    @Test
    fun `an uncurated vault address is refused before anything is fetched`() = runTest {
        // The route argument is not trusted: only the local allow-list decides.
        val state =
            viewModel(vaultAddress = "2Z6C84pCc2ri8t39jvRCXnTGFQqUJf1mMpUMtpeFfhyB").state.value

        assertNotNull(state.error)
        assertFalse(state.isLoading)
        assertEquals(0, BigDecimal.ZERO.compareTo(state.available))
    }

    @Test
    fun `percentage chips scale the available balance and round down`() = runTest {
        givenTokenBalance("2500000000")

        val vm = viewModel()
        vm.onPercentageChange(50)

        assertEquals("1250", vm.amountFieldState.text.toString())
        assertEquals(50, vm.state.value.percentageSelected)
    }

    @Test
    fun `a max chip on a native SOL vault leaves room for the network fee`() = runTest {
        // 1 SOL exactly; the fee buffer must come off the top or the deposit cannot pay for itself.
        coEvery { balanceRepository.getTokenValue(any(), any()) } returns
            flowOf(TokenValue(value = BigInteger("1000000000"), token = COIN))
        coEvery { kaminoApi.getVaultState(any()) } returns
            KaminoVaultStateJson(
                address = ALLEZ.address,
                state =
                    KaminoVaultStateJson.State(
                        name = "Allez SOL",
                        tokenMint = ALLEZ.tokenMint,
                        tokenDecimals = 9,
                        sharesMint = ALLEZ.sharesMint,
                        sharesDecimals = 6,
                        minDepositAmount = "10000000",
                    ),
            )

        val available = viewModel(vaultAddress = ALLEZ.address).state.value.available

        assertTrue(available < BigDecimal.ONE, "expected less than a whole SOL, was $available")
        assertTrue(
            available > BigDecimal("0.99"),
            "expected most of the balance to remain, was $available",
        )
    }

    @Test
    fun `submitting nothing does not build a transaction`() = runTest {
        givenTokenBalance("2500000000")

        val vm = viewModel()
        vm.submit() // empty field

        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isSubmitting)
    }

    @Test
    fun `a max SOL deposit reserves the wrapped-SOL rent and the priority fee`() = runTest {
        // The vault's underlying is wSOL, so the deposit creates a token account. Without reserving
        // its rent a Max deposit cannot fund that account and fails on chain.
        coEvery { balanceRepository.getTokenValue(any(), any()) } returns
            flowOf(TokenValue(value = BigInteger("1000000000"), token = COIN))
        coEvery { kaminoApi.getVaultState(any()) } returns
            KaminoVaultStateJson(
                address = ALLEZ.address,
                state =
                    KaminoVaultStateJson.State(
                        name = "Allez SOL",
                        tokenMint = ALLEZ.tokenMint,
                        tokenDecimals = 9,
                        sharesMint = ALLEZ.sharesMint,
                        sharesDecimals = 6,
                    ),
            )

        val available = viewModel(vaultAddress = ALLEZ.address).state.value.available

        // 1 SOL less rent (0.00203928) less the priority fee (20,000 µlamports x 350,000 CU =
        // 7,000 lamports) less the base fee — so strictly below 0.998, and far below a naive
        // fee-only headroom.
        assertTrue(
            available < BigDecimal("0.998"),
            "expected rent + priority fee reserved, was $available",
        )
        assertTrue(available > BigDecimal("0.99"), "reserved too much, was $available")
    }

    @Test
    fun `a USDC deposit does not reserve SOL costs against the token balance`() = runTest {
        // The fee and any account rent are paid in SOL, so the whole USDC balance is spendable.
        givenTokenBalance("2500000000")
        assertEquals(0, BigDecimal("2500").compareTo(viewModel().state.value.available))
    }

    @Test
    fun `a withdraw of farm-staked shares is refused by name, not attempted`() = runTest {
        // Every launch-vault deposit auto-stakes, and the transaction that spends staked shares has
        // never been observed, so this is a named state rather than a guess at an instruction
        // layout.
        givenTokenBalance("0")
        coEvery { kaminoApi.getUserPositions(WALLET) } returns
            listOf(
                KaminoUserPositionJson(
                    vaultAddress = STEAKHOUSE.address,
                    stakedShares = "1000",
                    unstakedShares = "0",
                    totalShares = "1000",
                )
            )
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0544278224860290217")

        val state = viewModel(isWithdraw = true).state.value

        assertTrue(state.eligibility is KaminoWithdrawEligibility.Withdrawable)
        assertTrue(state.available.signum() > 0, "a staked position must still be withdrawable")
    }

    @Test
    fun `a failed positions read refuses the withdraw instead of offering zero`() = runTest {
        givenTokenBalance("0")
        coEvery { kaminoApi.getUserPositions(WALLET) } throws RuntimeException("503")
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")

        val state = viewModel(isWithdraw = true).state.value

        assertEquals(KaminoWithdrawEligibility.Unreadable, state.eligibility)
    }

    @Test
    fun `the withdraw minimum is read as shares, not as the token amount`() = runTest {
        // minWithdrawAmount is 1000 SHARE base units. Treated as token base units it would be
        // 0.001 USDC; converted properly at the Steakhouse rate it is 0.001055.
        givenTokenBalance("0")
        coEvery { kaminoApi.getUserPositions(WALLET) } returns
            listOf(
                KaminoUserPositionJson(
                    vaultAddress = STEAKHOUSE.address,
                    stakedShares = "0",
                    unstakedShares = "1000",
                    totalShares = "1000",
                )
            )
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0544278224860290217")

        val minimum = viewModel(isWithdraw = true).state.value.minimum
        assertEquals(0, BigDecimal("0.001055").compareTo(minimum!!))
    }

    @Test
    fun `the transaction is marked so verify names the direction and the vault`() = runTest {
        // Without the marker the verify screen falls through to "You're sending" for a withdraw,
        // and
        // labels the vault name as a "Validator".
        givenTokenBalance("2500000000")
        val captured = slot<DepositTransaction>()
        coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit
        coEvery {
            buildKeysignPayload(
                vault = any(),
                action = any(),
                apiAmount = any(),
                tokenAmount = any(),
                coin = any(),
                blockChainSpecific = any(),
                vaultPublicKeyECDSA = any(),
                vaultLocalPartyID = any(),
                libType = any(),
            )
        } returns mockk(relaxed = true)

        val vm = viewModel()
        vm.amountFieldState.setTextAndPlaceCursorAtEnd("100")
        vm.submit()

        assertEquals(OPERATION_KAMINO_DEPOSIT, captured.captured.operation)
        assertEquals("Steakhouse USDC", captured.captured.validatorName)
        assertEquals(STEAKHOUSE.address, captured.captured.dstAddress)
    }

    private companion object {
        const val VAULT_ID = "vault-id"
        const val WALLET = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"

        val STEAKHOUSE = KaminoVaultRegistry.STEAKHOUSE_USDC
        val ALLEZ = KaminoVaultRegistry.ALLEZ_SOL

        val COIN = com.vultisig.wallet.data.models.Coins.Solana.USDC

        val VAULT =
            Vault(
                id = VAULT_ID,
                name = "vault",
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
