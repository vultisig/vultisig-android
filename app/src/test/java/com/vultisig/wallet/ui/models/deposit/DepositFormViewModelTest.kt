@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DepositMemoAssetsValidatorUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCaseImpl
import com.vultisig.wallet.data.usecases.RequestAddressBookEntryUseCase
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.data.usecases.ValidateMayaTransactionHeightUseCase
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class DepositFormViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val sendNavigator: Navigator<SendDst> = mockk(relaxed = true)
    private val requestQrScan: RequestQrScanUseCase = mockk(relaxed = true)
    private val appCurrencyRepository: AppCurrencyRepository = mockk(relaxed = true)
    private val tokenPriceRepository =
        mockk<com.vultisig.wallet.data.repositories.TokenPriceRepository>(relaxed = true)
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper =
        mockk(relaxed = true)
    private val accountsRepository: AccountsRepository = mockk(relaxed = true)
    private val isAssetCharsValid: DepositMemoAssetsValidatorUseCase = mockk(relaxed = true)
    private val requestResultRepository: RequestResultRepository = mockk(relaxed = true)
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk(relaxed = true)
    private val transactionRepository: DepositTransactionRepository = mockk(relaxed = true)
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk(relaxed = true)
    private val thorChainApi: ThorChainApi = mockk(relaxed = true)
    private val mayaChainApi: MayaChainApi = mockk(relaxed = true)
    private val mayachainBondRepository: MayachainBondRepository = mockk(relaxed = true)
    private val balanceRepository: BalanceRepository = mockk(relaxed = true)
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk(relaxed = true)
    private val validateMayaTransactionHeight: ValidateMayaTransactionHeightUseCase =
        mockk(relaxed = true)
    private val feeServiceComposite: FeeServiceComposite = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val tokenRepository: TokenRepository = mockk(relaxed = true)
    private val gasFeeToEstimate: GasFeeToEstimatedFeeUseCaseImpl = mockk(relaxed = true)
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        DepositFormViewModel(
            navigator = navigator,
            sendNavigator = sendNavigator,
            requestQrScan = requestQrScan,
            appCurrencyRepository = appCurrencyRepository,
            tokenPriceRepository = tokenPriceRepository,
            mapTokenValueToStringWithUnit = mapTokenValueToStringWithUnit,
            accountsRepository = accountsRepository,
            isAssetCharsValid = isAssetCharsValid,
            requestResultRepository = requestResultRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            transactionRepository = transactionRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            thorChainApi = thorChainApi,
            mayaChainApi = mayaChainApi,
            mayachainBondRepository = mayachainBondRepository,
            balanceRepository = balanceRepository,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            validateMayaTransactionHeight = validateMayaTransactionHeight,
            feeServiceComposite = feeServiceComposite,
            vaultRepository = vaultRepository,
            tokenRepository = tokenRepository,
            gasFeeToEstimate = gasFeeToEstimate,
            requestAddressBookEntry = requestAddressBookEntry,
        )

    @Test
    fun `loadData for MayaChain only exposes Leave and Custom deposit options`() = runTest {
        val vm = buildViewModel()

        vm.loadData("vault1", Chain.MayaChain.raw, null, null)
        advanceUntilIdle()

        assertEquals(
            listOf(DepositOption.Leave, DepositOption.Custom),
            vm.state.value.depositOptions,
        )
    }

    @Test
    fun `loadData for ThorChain sets RUNE as default selected token`() = runTest {
        val vm = buildViewModel()

        vm.loadData("vault1", Chain.ThorChain.raw, null, null)
        advanceUntilIdle()

        assertEquals(Coins.ThorChain.RUNE, vm.state.value.selectedToken)
    }

    @Test
    fun `selectDepositOption Bond on ThorChain sets RUNE as selected token`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)
        advanceUntilIdle()

        vm.selectDepositOption(DepositOption.Bond)
        advanceUntilIdle()

        assertEquals(Coins.ThorChain.RUNE, vm.state.value.selectedToken)
    }

    @Test
    fun `selectDepositOption Leave on MayaChain sets CACAO as selected token`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.MayaChain.raw, null, null)
        advanceUntilIdle()

        vm.selectDepositOption(DepositOption.Leave)
        advanceUntilIdle()

        assertEquals(Coins.MayaChain.CACAO, vm.state.value.selectedToken)
    }

    @Test
    fun `selectDepositOption AddLiquidity sets CACAO as selected token`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.MayaChain.raw, null, null)
        advanceUntilIdle()

        vm.selectDepositOption(DepositOption.AddLiquidity)
        advanceUntilIdle()

        assertEquals(Coins.MayaChain.CACAO, vm.state.value.selectedToken)
    }

    @Test
    fun `validateTokenAmount with empty text sets tokenAmountError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.validateTokenAmount()

        assertNotNull(vm.state.value.tokenAmountError)
    }

    @Test
    fun `validateTokenAmount with negative value sets tokenAmountError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.tokenAmountFieldState.setTextAndPlaceCursorAtEnd("-1")
        vm.validateTokenAmount()

        assertNotNull(vm.state.value.tokenAmountError)
    }

    @Test
    fun `validateBasisPoints with zero sets basisPointsError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.basisPointsFieldState.setTextAndPlaceCursorAtEnd("0")
        vm.validateBasisPoints()

        assertNotNull(vm.state.value.basisPointsError)
    }

    @Test
    fun `validateBasisPoints with value over 100 sets basisPointsError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.basisPointsFieldState.setTextAndPlaceCursorAtEnd("101")
        vm.validateBasisPoints()

        assertNotNull(vm.state.value.basisPointsError)
    }

    @Test
    fun `validateCustomMemo with blank memo sets customMemoError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.validateCustomMemo()

        assertNotNull(vm.state.value.customMemoError)
    }

    @Test
    fun `deposit for WithdrawSecuredAsset when chain not enabled surfaces deposit_error_chain_not_enabled`() =
        runTest {
            val vm = buildViewModel()
            vm.loadData("vault1", Chain.ThorChain.raw, null, null)
            advanceUntilIdle()

            coEvery { accountsRepository.loadAddress("vault1", Chain.Bitcoin) } returns emptyFlow()
            vm.selectDepositOption(DepositOption.WithdrawSecuredAsset)
            vm.onSelectSecureAsset(
                TokenWithdrawSecureAsset(
                    ticker = "BTC",
                    contract = "",
                    coin = Coin.EMPTY,
                    tokenValue = null,
                )
            )
            advanceUntilIdle()
            vm.thorAddressFieldState.setTextAndPlaceCursorAtEnd("thor1somevalidaddress")

            vm.deposit()
            advanceUntilIdle()

            val errorText = vm.state.value.errorText
            assertNotNull(errorText)
            assertTrue(errorText is UiText.FormattedText)
            assertEquals(
                R.string.deposit_error_chain_not_enabled,
                (errorText as UiText.FormattedText).resId,
            )
        }
}
