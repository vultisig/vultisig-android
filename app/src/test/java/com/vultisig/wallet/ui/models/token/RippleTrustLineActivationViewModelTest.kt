@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.token

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.RippleTrustLines
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class RippleTrustLineActivationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val accountsRepository: AccountsRepository = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val transactionRepository: TransactionRepository = mockk(relaxed = true)
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk()
    private val feeServiceComposite: FeeServiceComposite = mockk()
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk()
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper = mockk()
    private val rippleTrustLines: RippleTrustLines = mockk()
    private val appCurrencyRepository: AppCurrencyRepository = mockk()

    private val xrp = Coins.Ripple.XRP.copy(address = ADDRESS)
    private val rlusd = Coins.Ripple.RLUSD.copy(address = ADDRESS)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.RippleTrustLineActivation>() } returns
            Route.RippleTrustLineActivation(vaultId = VAULT_ID, tokenId = rlusd.id)

        every { accountsRepository.loadCachedAddress(VAULT_ID, Chain.Ripple) } returns
            flowOf(
                Address(
                    chain = Chain.Ripple,
                    address = ADDRESS,
                    accounts =
                        listOf(
                            Account(
                                token = xrp,
                                tokenValue = TokenValue(BigInteger.valueOf(10_000_000), xrp),
                                fiatValue = null,
                                price = null,
                            ),
                            Account(token = rlusd, tokenValue = null, fiatValue = null, price = null),
                        ),
                )
            )
        coEvery { vaultRepository.get(VAULT_ID) } returns Vault(id = VAULT_ID, name = "Main")
        coEvery { feeServiceComposite.calculateFees(any()) } returns BasicFee(FEE_DROPS)
        coEvery { rippleTrustLines.fetchOwnerReserve() } returns BigInteger.valueOf(200_000)
        coEvery { gasFeeToEstimatedFee(any()) } returns
            EstimatedGasFee(
                formattedTokenValue = "0.000012 XRP",
                formattedFiatValue = "$0.00",
                tokenValue = TokenValue(FEE_DROPS, xrp),
                fiatValue = FiatValue(BigDecimal.ZERO, "USD"),
            )
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToStringWithUnit(any()) } returns "1 XRP"
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `a failed activation keeps the quote and lets the user retry`() =
        runTest(testDispatcher) {
            var calls = 0
            coEvery {
                blockChainSpecificRepository.getSpecific(
                    chain = any(),
                    address = any(),
                    token = any(),
                    gasFee = any(),
                    isSwap = any(),
                    isMaxAmountEnabled = any(),
                    isDeposit = any(),
                    gasLimit = any(),
                    dstAddress = any(),
                    tokenAmountValue = any(),
                    memo = any(),
                    transactionType = any(),
                )
            } answers
                {
                    if (calls++ == 0) throw IOException("offline")
                    BlockChainSpecificAndUtxo(
                        BlockChainSpecific.Ripple(
                            sequence = 1UL,
                            gas = FEE_DROPS.toLong().toULong(),
                            lastLedgerSequence = 100UL,
                        )
                    )
                }
            val vm = createViewModel()
            // The view model hops to Dispatchers.IO, which the test scheduler does not drive.
            vm.uiState.first { !it.isLoading }.error.shouldBeNull()

            vm.activate()

            val failed = vm.uiState.first { !it.isActivating }
            failed.activationError.shouldNotBeNull()
            failed.error.shouldBeNull()

            vm.activate()

            val route = slot<Any>()
            coVerify(exactly = 1, timeout = 5_000) { navigator.route(capture(route)) }
            route.captured.shouldBeInstanceOf<Route.VerifySend>().vaultId shouldBe VAULT_ID
            vm.uiState.value.activationError.shouldBeNull()
        }

    private fun createViewModel() =
        RippleTrustLineActivationViewModel(
            savedStateHandle = mockk(relaxed = true),
            navigator = navigator,
            accountsRepository = accountsRepository,
            vaultRepository = vaultRepository,
            transactionRepository = transactionRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            feeServiceComposite = feeServiceComposite,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            mapTokenValueToStringWithUnit = mapTokenValueToStringWithUnit,
            rippleTrustLines = rippleTrustLines,
            appCurrencyRepository = appCurrencyRepository,
        )

    private companion object {
        const val VAULT_ID = "vault-1"
        const val ADDRESS = "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh"
        val FEE_DROPS: BigInteger = BigInteger.valueOf(12)
    }
}
