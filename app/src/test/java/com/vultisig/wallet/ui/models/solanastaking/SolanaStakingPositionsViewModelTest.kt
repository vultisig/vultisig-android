package com.vultisig.wallet.ui.models.solanastaking

import com.vultisig.wallet.data.blockchain.solana.staking.BuildSolanaStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakeAccount
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakeState
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingService
import com.vultisig.wallet.data.blockchain.solana.staking.ValidatorMetadataProvider
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.DefiFiatTotal
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers the header banner's total, which is the chain's whole DeFi holding rather than the staking
 * half the Staked tab shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SolanaStakingPositionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var vaultRepository: VaultRepository
    private lateinit var solanaStakingService: SolanaStakingService
    private lateinit var validatorMetadataProvider: ValidatorMetadataProvider
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    private lateinit var tokenPriceRepository: TokenPriceRepository
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var blockChainSpecificRepository: BlockChainSpecificRepository
    private lateinit var buildKeysignPayload: BuildSolanaStakingKeysignPayloadUseCase
    private lateinit var depositTransactionRepository: DepositTransactionRepository
    private lateinit var navigator: Navigator<Destination>

    private val defaultLocale = Locale.getDefault()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The totals below are formatted for the user's locale, so pin the one the test runs under.
        Locale.setDefault(Locale.US)
        vaultRepository = mockk(relaxed = true)
        solanaStakingService = mockk(relaxed = true)
        validatorMetadataProvider = mockk(relaxed = true)
        balanceVisibilityRepository = mockk(relaxed = true)
        tokenPriceRepository = mockk(relaxed = true)
        appCurrencyRepository = mockk(relaxed = true)
        blockChainSpecificRepository = mockk(relaxed = true)
        buildKeysignPayload = mockk(relaxed = true)
        depositTransactionRepository = mockk(relaxed = true)
        navigator = mockk(relaxed = true)

        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { balanceVisibilityRepository.getVisibility(VAULT_ID) } returns true
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        coEvery { appCurrencyRepository.getCurrencyFormat(any()) } returns
            NumberFormat.getCurrencyInstance(Locale.US)
        coEvery { tokenPriceRepository.getCachedPrice(SOL.id, AppCurrency.USD) } returns
            BigDecimal("100")
        coEvery { solanaStakingService.fetchStakeAccounts(ADDRESS) } returns listOf(stakeAccount())
        coEvery { validatorMetadataProvider.metadata(any()) } returns emptyMap()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `the header adds Kamino Earn to native staking`() = runTest {
        val vm = viewModel().apply { setData(VAULT_ID) }

        vm.onKaminoTotalChanged(kaminoTotal("54.25"))

        // The Staked tab keeps reporting its own half.
        vm.state.value.totalStakedFiatDisplay shouldBe "$100.00"
        vm.state.value.chainTotalFiatDisplay shouldBe "$154.25"
    }

    @Test
    fun `the header totals whichever half arrives first`() = runTest {
        // Earn is loaded by its own view-model, so it can resolve before the stake accounts do.
        val vm = viewModel()
        vm.onKaminoTotalChanged(kaminoTotal("54.25"))

        vm.setData(VAULT_ID)

        vm.state.value.chainTotalFiatDisplay shouldBe "$154.25"
    }

    @Test
    fun `no Kamino vault enabled still leaves the header showing the staked total`() = runTest {
        val vm = viewModel().apply { setData(VAULT_ID) }

        // A resolved zero, which the Earn view-model reports when nothing is enabled.
        vm.onKaminoTotalChanged(kaminoTotal("0"))

        vm.state.value.chainTotalFiatDisplay shouldBe "$100.00"
    }

    @Test
    fun `an unresolved Kamino total leaves the header unavailable rather than short`() = runTest {
        val vm = viewModel().apply { setData(VAULT_ID) }
        vm.onKaminoTotalChanged(kaminoTotal("54.25"))

        vm.onKaminoTotalChanged(null)

        // Reporting the staking half alone would present a number short by a real position as the
        // chain's whole DeFi balance.
        vm.state.value.chainTotalFiatDisplay.shouldBeNull()
        vm.state.value.totalStakedFiatDisplay shouldBe "$100.00"
    }

    @Test
    fun `the newest Kamino total wins, whatever order the handovers land in`() = runTest {
        val vm = viewModel().apply { setData(VAULT_ID) }

        vm.onKaminoTotalChanged(kaminoTotal("54.25"))
        vm.onKaminoTotalChanged(kaminoTotal("70.00"))

        // An earlier handover must never overwrite a later one — the banner would then sit on a
        // figure the Earn tab has already moved past.
        vm.state.value.chainTotalFiatDisplay shouldBe "$170.00"
    }

    @Test
    fun `a Kamino half priced in another currency is not added to this one`() = runTest {
        val vm = viewModel().apply { setData(VAULT_ID) }

        // Mid-switch: Earn is still reporting the figure it priced before the change landed.
        vm.onKaminoTotalChanged(DefiFiatTotal(BigDecimal("54.25"), AppCurrency.EUR))

        // €54.25 added to $100.00 is not a figure in either currency, so the banner waits.
        vm.state.value.chainTotalFiatDisplay.shouldBeNull()
    }

    @Test
    fun `a currency change re-prices the screen instead of relabelling it`() = runTest {
        val currency = MutableStateFlow(AppCurrency.USD)
        every { appCurrencyRepository.currency } returns currency
        coEvery { appCurrencyRepository.getCurrencyFormat(any()) } answers
            {
                NumberFormat.getCurrencyInstance(
                    if (firstArg<AppCurrency>() == AppCurrency.EUR) Locale.GERMANY else Locale.US
                )
            }
        coEvery { tokenPriceRepository.getCachedPrice(SOL.id, AppCurrency.EUR) } returns
            BigDecimal("90")

        val vm = viewModel().apply { setData(VAULT_ID) }
        vm.onKaminoTotalChanged(kaminoTotal("10"))
        vm.state.value.chainTotalFiatDisplay shouldBe "$110.00"

        currency.value = AppCurrency.EUR

        // Earn has not re-priced yet, and its dollar figure must not be added to a euro one.
        vm.state.value.chainTotalFiatDisplay.shouldBeNull()

        vm.onKaminoTotalChanged(DefiFiatTotal(BigDecimal("10"), AppCurrency.EUR))

        // The staked SOL is re-priced in euros rather than the dollar figure being restamped.
        vm.state.value.chainTotalFiatDisplay shouldBe
            NumberFormat.getCurrencyInstance(Locale.GERMANY).format(BigDecimal("100"))
    }

    @Test
    fun `a Kamino half that re-priced first is not added to a staked half that has not`() =
        runTest {
            val currency = MutableStateFlow(AppCurrency.USD)
            every { appCurrencyRepository.currency } returns currency
            coEvery { appCurrencyRepository.getCurrencyFormat(any()) } answers
                {
                    NumberFormat.getCurrencyInstance(
                        if (firstArg<AppCurrency>() == AppCurrency.EUR) Locale.GERMANY
                        else Locale.US
                    )
                }
            coEvery { tokenPriceRepository.getCachedPrice(SOL.id, AppCurrency.EUR) } returns
                BigDecimal("90")

            val vm = viewModel().apply { setData(VAULT_ID) }
            vm.onKaminoTotalChanged(kaminoTotal("10"))
            vm.state.value.chainTotalFiatDisplay shouldBe "$110.00"

            // The reload the switch triggers is still reading stake accounts...
            val reload = CompletableDeferred<List<SolanaStakeAccount>>()
            coEvery { solanaStakingService.fetchStakeAccounts(ADDRESS) } coAnswers
                {
                    reload.await()
                }
            currency.value = AppCurrency.EUR

            // ...while Earn, whose no-vault-enabled answer needs no network call at all, has
            // already re-priced and handed its half over.
            vm.onKaminoTotalChanged(DefiFiatTotal(BigDecimal("10"), AppCurrency.EUR))

            // $100 of staked SOL is not €100, so the banner waits rather than restamping it.
            vm.state.value.chainTotalFiatDisplay.shouldBeNull()

            reload.complete(listOf(stakeAccount()))

            vm.state.value.chainTotalFiatDisplay shouldBe
                NumberFormat.getCurrencyInstance(Locale.GERMANY).format(BigDecimal("100"))
        }

    @Test
    fun `a currency change clears the fiat on each card, and a failed reload leaves it clear`() =
        runTest {
            val currency = MutableStateFlow(AppCurrency.USD)
            every { appCurrencyRepository.currency } returns currency

            val vm = viewModel().apply { setData(VAULT_ID) }
            val staked = vm.state.value.positions.single()
            staked.stakedFiatDisplay shouldBe "$100.00"

            coEvery { solanaStakingService.fetchStakeAccounts(ADDRESS) } throws
                RuntimeException("503")
            currency.value = AppCurrency.EUR

            val row = vm.state.value.positions.single()
            // The euro value is not known yet and the dollar one is no longer true, so the card
            // says nothing rather than wearing the wrong symbol — and this reload never rebuilds
            // it. The staked SOL is priced in neither currency, so it stays.
            row.stakedFiatDisplay.shouldBeNull()
            row.stakedDisplay shouldBe staked.stakedDisplay
        }

    @Test
    fun `a vault without SOL reports no total at all`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT.copy(coins = emptyList())

        val vm = viewModel().apply { setData(VAULT_ID) }
        vm.onKaminoTotalChanged(kaminoTotal("54.25"))

        vm.state.value.chainTotalFiatDisplay.shouldBeNull()
    }

    private fun kaminoTotal(value: String) = DefiFiatTotal(BigDecimal(value), AppCurrency.USD)

    private fun viewModel() =
        SolanaStakingPositionsViewModel(
            vaultRepository = vaultRepository,
            solanaStakingService = solanaStakingService,
            validatorMetadataProvider = validatorMetadataProvider,
            balanceVisibilityRepository = balanceVisibilityRepository,
            tokenPriceRepository = tokenPriceRepository,
            appCurrencyRepository = appCurrencyRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            buildKeysignPayload = buildKeysignPayload,
            depositTransactionRepository = depositTransactionRepository,
            navigator = navigator,
        )

    /** One stake account holding 1 SOL, which at the stubbed price is $100.00. */
    private fun stakeAccount() =
        SolanaStakeAccount(
            stakePubkey = "6nJqQF6ZMTZ3aM2Q9pQ4Gg8v8g8pQmYyWpUUZq4KaWWM",
            voter = null,
            lamports = BigInteger("1002282880"),
            delegatedStake = BigInteger("1000000000"),
            rentExemptReserve = BigInteger("2282880"),
            activationEpoch = null,
            deactivationEpoch = null,
            state = SolanaStakeState.Active,
        )

    private companion object {
        const val VAULT_ID = "vault-id"
        const val ADDRESS = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"

        val SOL = Coins.Solana.SOL.copy(address = ADDRESS)

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
                coins = listOf(SOL),
            )
    }
}
