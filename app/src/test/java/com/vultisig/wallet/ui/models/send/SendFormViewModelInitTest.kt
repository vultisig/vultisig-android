@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SendFormViewModelInitTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val accountsRepository: AccountsRepository = mockk(relaxed = true)
    private val appCurrencyRepository: AppCurrencyRepository = mockk(relaxed = true)

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
    fun `defiType defaults to null when type arg is missing`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.defiType)
        assertFalse(vm.uiState.value.isAutocompound)
    }

    @Test
    fun `type=BOND parses to defiType BOND`() = runTest {
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, type = "BOND")

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(DeFiNavActions.BOND, vm.uiState.value.defiType)
    }

    @Test
    fun `type=STAKE_STCY enables isAutocompound`() = runTest {
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, type = "STAKE_STCY")

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(DeFiNavActions.STAKE_STCY, vm.uiState.value.defiType)
        assertTrue(vm.uiState.value.isAutocompound)
    }

    @Test
    fun `type=UNSTAKE_STCY enables isAutocompound`() = runTest {
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, type = "UNSTAKE_STCY")

        val vm = buildViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isAutocompound)
    }

    @Test
    fun `type=STAKE_RUJI does not enable isAutocompound`() = runTest {
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, type = "STAKE_RUJI")

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(DeFiNavActions.STAKE_RUJI, vm.uiState.value.defiType)
        assertFalse(vm.uiState.value.isAutocompound)
    }

    @Test
    fun `amount arg populates tokenAmountFieldState`() = runTest {
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, amount = "1.5")

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals("1.5", vm.tokenAmountFieldState.text.toString())
    }

    @Test
    fun `memo arg populates memoFieldState`() = runTest {
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, memo = "thor:abc")

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals("thor:abc", vm.memoFieldState.text.toString())
    }

    @Test
    fun `memo is visible for a TON jetton`() = runTest {
        val vm = buildViewModelWithPreselectedToken(Coins.Ton.USDT)

        assertTrue(vm.uiState.value.hasMemo)
    }

    @Test
    fun `memo is visible for an SPL token`() = runTest {
        val vm = buildViewModelWithPreselectedToken(Coins.Solana.USDC)

        assertTrue(vm.uiState.value.hasMemo)
    }

    @Test
    fun `memo is visible for a TRC20 token`() = runTest {
        val vm = buildViewModelWithPreselectedToken(Coins.Tron.USDT)

        assertTrue(vm.uiState.value.hasMemo)
    }

    @Test
    fun `memo is hidden for an ERC20 token`() = runTest {
        val vm = buildViewModelWithPreselectedToken(Coins.Ethereum.USDC)

        assertFalse(vm.uiState.value.hasMemo)
    }

    @Test
    fun `memo is hidden for an XRPL issued currency`() = runTest {
        val vm = buildViewModelWithPreselectedToken(Coins.Ripple.RLUSD)

        assertFalse(vm.uiState.value.hasMemo)
    }

    @Test
    fun `memo is hidden for Sui`() = runTest {
        val vm = buildViewModelWithPreselectedToken(Coins.Sui.SUI)

        assertFalse(vm.uiState.value.hasMemo)
    }

    @Test
    fun `preSelectedTokenId without address expands the Address section`() = runTest {
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, tokenId = "RUNE-thorchain")

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(SendSections.Address, vm.uiState.value.expandedSection)
    }

    @Test
    fun `preSelectedTokenId with address expands the Amount section`() = runTest {
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, tokenId = "RUNE-thorchain", address = "thor1abc")

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(SendSections.Amount, vm.uiState.value.expandedSection)
    }

    @Test
    fun `a deep link naming a chain this version dropped still opens the form`() = runTest {
        // `vultisig://send?assetChain=…` carries the chain as text, so a link minted while a
        // since-retired chain existed still names it. Resolving it eagerly threw on the way in and
        // took the screen down with it.
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(
                vaultId = VAULT_ID,
                chainId = RETIRED_CHAIN_ID,
                tokenId = "KUJI-$RETIRED_CHAIN_ID",
                address = "kujira1abc",
            )

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals("kujira1abc", vm.addressFieldState.text.toString())
        assertEquals(SendSections.Amount, vm.uiState.value.expandedSection)
    }

    @Test
    fun `default expandedSection is Asset when no preSelectedTokenId`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(SendSections.Asset, vm.uiState.value.expandedSection)
    }

    @Test
    fun `loadVaultName populates srcVaultName from the loaded vault`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns
            mockk<Vault>(relaxed = true).also {
                every { it.id } returns VAULT_ID
                every { it.name } returns "MyVault"
                every { it.coins } returns emptyList()
            }

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals("MyVault", vm.uiState.value.srcVaultName)
    }

    @Test
    fun `fiatCurrency reflects appCurrencyRepository emission`() = runTest {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.EUR)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(AppCurrency.EUR.ticker, vm.uiState.value.fiatCurrency)
    }

    @Test
    fun `REDEEM_YRUNE seeds slippageFieldState with 1_0`() = runTest {
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, type = "REDEEM_YRUNE")

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals("1.0", vm.slippageFieldState.text.toString())
    }

    @Test
    fun `slippageFieldState is empty for non-redeem types`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals("", vm.slippageFieldState.text.toString())
    }

    // Regression: the memo clear added for XRPL issued currencies must not reach a TON jetton.
    // TonHelper signs `payload.memo` as the transfer comment, so wiping it makes an exchange
    // deposit arrive without its comment.
    @Test
    fun `a deep-linked memo survives on a TON jetton`() = runTest {
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(
                vaultId = VAULT_ID,
                chainId = Chain.Ton.id,
                tokenId = jettonAccount.token.id,
                memo = EXCHANGE_MEMO,
            )
        every { accountsRepository.loadAddresses(VAULT_ID) } returns
            flowOf(listOf(tonAddress(jettonAccount)))

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(EXCHANGE_MEMO, vm.memoFieldState.text.toString())
    }

    @Test
    fun `a memo is cleared on an XRPL issued currency`() = runTest {
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(
                vaultId = VAULT_ID,
                chainId = Chain.Ripple.id,
                tokenId = rlusdAccount.token.id,
                memo = EXCHANGE_MEMO,
            )
        every { accountsRepository.loadAddresses(VAULT_ID) } returns
            flowOf(listOf(rippleAddress(rlusdAccount)))

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals("", vm.memoFieldState.text.toString())
    }

    private fun tonAddress(vararg accounts: Account) =
        Address(chain = Chain.Ton, address = TON_ADDRESS, accounts = accounts.toList())

    private fun rippleAddress(vararg accounts: Account) =
        Address(chain = Chain.Ripple, address = RIPPLE_ADDRESS, accounts = accounts.toList())

    private val jettonAccount =
        account(
            Coins.Ton.USDT.copy(
                address = TON_ADDRESS,
            )
        )

    private val rlusdAccount = account(Coins.Ripple.RLUSD.copy(address = RIPPLE_ADDRESS))

    private fun account(coin: Coin) =
        Account(token = coin, tokenValue = null, fiatValue = null, price = null)

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
            requestQrScan = mockk(relaxed = true),
            accountsRepository = accountsRepository,
            appCurrencyRepository = appCurrencyRepository,
            chainAccountAddressRepository = mockk(relaxed = true),
            tokenPriceRepository = mockk(relaxed = true),
            blockChainSpecificRepository = mockk(relaxed = true),
            requestResultRepository = mockk(relaxed = true),
            addressParserRepository = mockk(relaxed = true),
            getAvailableTokenBalance = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
            advanceGasUiRepository = mockk(relaxed = true),
            vaultRepository = vaultRepository,
            tokenRepository = mockk(relaxed = true),
            stakingDetailsRepository = mockk(relaxed = true),
            defaultStakingPositionService = mockk(relaxed = true),
            feeServiceComposite = mockk(relaxed = true),
            chainValidationService = mockk(relaxed = true),
            requestAddressBookEntry = mockk(relaxed = true),
            getTronFrozenBalances = mockk(relaxed = true),
            sendStrategyFactory = fakeSendStrategyFactory(),
        )
    }

    private fun TestScope.buildViewModelWithPreselectedToken(coin: Coin): SendFormViewModel {
        val token = coin.copy(address = ADDRESS)
        coEvery { accountsRepository.loadAddresses(VAULT_ID) } returns
            flowOf(
                listOf(
                    Address(
                        chain = token.chain,
                        address = ADDRESS,
                        accounts = listOf(account(token)),
                    )
                )
            )
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, tokenId = token.id)

        return buildViewModel().also { advanceUntilIdle() }
    }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val ADDRESS = "address-1"

        // A chain id old deep links still carry after the entry was removed from [Chain].
        const val RETIRED_CHAIN_ID = "Kujira"

        const val EXCHANGE_MEMO = "deposit-ref-91823"
        const val TON_ADDRESS = "UQAqTqLHgFHmZWLBiCTuLuOWTCcOfXFbCXNBnKcPPRPTNSuA"
        const val RIPPLE_ADDRESS = "rPVMhWBsfF9iMXYj3aAzJVkPDTFNSyWdKy"
    }
}
