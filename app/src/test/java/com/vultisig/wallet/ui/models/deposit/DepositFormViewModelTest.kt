@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
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
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DepositFormViewModel] covering deposit-option loading, token selection, field
 * validation, and error propagation.
 */
internal class DepositFormViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val accountsRepository: AccountsRepository = mockk(relaxed = true)
    private val appCurrencyRepository: AppCurrencyRepository = mockk(relaxed = true)
    private val balanceRepository: BalanceRepository = mockk(relaxed = true)
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk(relaxed = true)
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk(relaxed = true)
    private val feeServiceComposite: FeeServiceComposite = mockk(relaxed = true)
    private val gasFeeToEstimate: GasFeeToEstimatedFeeUseCaseImpl = mockk(relaxed = true)
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk(relaxed = true)
    private val isAssetCharsValid: DepositMemoAssetsValidatorUseCase = mockk(relaxed = true)
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper =
        mockk(relaxed = true)
    private val mayaChainApi: MayaChainApi = mockk(relaxed = true)
    private val mayachainBondRepository: MayachainBondRepository = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase = mockk(relaxed = true)
    private val requestQrScan: RequestQrScanUseCase = mockk(relaxed = true)
    private val requestResultRepository: RequestResultRepository = mockk(relaxed = true)
    private val sendNavigator: Navigator<SendDst> = mockk(relaxed = true)
    private val thorChainApi: ThorChainApi = mockk(relaxed = true)
    private val tokenPriceRepository: TokenPriceRepository = mockk(relaxed = true)
    private val tokenRepository: TokenRepository = mockk(relaxed = true)
    private val transactionRepository: DepositTransactionRepository = mockk(relaxed = true)
    private val validateMayaTransactionHeight: ValidateMayaTransactionHeightUseCase =
        mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)

    /** Sets the test main dispatcher before each test. */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    /** Resets the main dispatcher after each test. */
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Constructs a [DepositFormViewModel] with all mocked dependencies. */
    private fun buildVm() =
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

    /** Verifies MayaChain only exposes [DepositOption.Leave] and [DepositOption.Custom] options. */
    @Test
    fun `loadData for MayaChain exposes only Leave and Custom deposit options`() {
        val vm = buildVm()

        vm.loadData(
            vaultId = "vault-1",
            chainId = "MayaChain",
            depositType = null,
            bondAddress = null,
        )

        assertEquals(
            listOf(DepositOption.Leave, DepositOption.Custom),
            vm.state.value.depositOptions,
        )
    }

    /**
     * Verifies ThorChain deposit options include both [DepositOption.Bond] and
     * [DepositOption.Leave].
     */
    @Test
    fun `loadData for ThorChain includes Bond and Leave in deposit options`() {
        val vm = buildVm()

        vm.loadData(
            vaultId = "vault-1",
            chainId = "thorchain",
            depositType = null,
            bondAddress = null,
        )

        val options = vm.state.value.depositOptions
        assertTrue(DepositOption.Bond in options)
        assertTrue(DepositOption.Leave in options)
    }

    /** Verifies selecting [DepositOption.Bond] on ThorChain auto-selects the RUNE token. */
    @Test
    fun `selectDepositOption Bond on ThorChain sets selectedToken to RUNE`() = runTest {
        val vm = buildVm()
        vm.loadData(
            vaultId = "vault-1",
            chainId = "thorchain",
            depositType = null,
            bondAddress = null,
        )

        vm.selectDepositOption(DepositOption.Bond)
        advanceUntilIdle()

        assertEquals(Coins.ThorChain.RUNE, vm.state.value.selectedToken)
    }

    /** Verifies selecting [DepositOption.Leave] on MayaChain auto-selects the CACAO token. */
    @Test
    fun `selectDepositOption Leave on MayaChain sets selectedToken to CACAO`() = runTest {
        val vm = buildVm()
        vm.loadData(
            vaultId = "vault-1",
            chainId = "MayaChain",
            depositType = null,
            bondAddress = null,
        )

        vm.selectDepositOption(DepositOption.Leave)
        advanceUntilIdle()

        assertEquals(Coins.MayaChain.CACAO, vm.state.value.selectedToken)
    }

    /**
     * Verifies selecting [DepositOption.AddLiquidity] on MayaChain auto-selects the CACAO token.
     */
    @Test
    fun `selectDepositOption AddLiquidity sets selectedToken to CACAO`() = runTest {
        val vm = buildVm()
        vm.loadData(
            vaultId = "vault-1",
            chainId = "MayaChain",
            depositType = null,
            bondAddress = null,
        )

        vm.selectDepositOption(DepositOption.AddLiquidity)
        advanceUntilIdle()

        assertEquals(Coins.MayaChain.CACAO, vm.state.value.selectedToken)
    }

    /**
     * Verifies [DepositFormViewModel.validateTokenAmount] sets an error when the amount field is
     * empty.
     */
    @Test
    fun `validateTokenAmount with empty field sets tokenAmountError`() {
        val vm = buildVm()

        vm.validateTokenAmount()

        assertNotNull(vm.state.value.tokenAmountError)
    }

    /**
     * Verifies [DepositFormViewModel.validateTokenAmount] clears the error when a valid amount is
     * entered.
     */
    @Test
    fun `validateTokenAmount with valid amount clears tokenAmountError`() {
        val vm = buildVm()
        vm.tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1.5")

        vm.validateTokenAmount()

        assertNull(vm.state.value.tokenAmountError)
    }

    /**
     * Verifies [DepositFormViewModel.validateBasisPoints] sets an error when basis points
     * exceed 100.
     */
    @Test
    fun `validateBasisPoints with value above 100 sets basisPointsError`() {
        val vm = buildVm()
        vm.basisPointsFieldState.setTextAndPlaceCursorAtEnd("101")

        vm.validateBasisPoints()

        assertNotNull(vm.state.value.basisPointsError)
    }

    /**
     * Verifies [DepositFormViewModel.validateCustomMemo] sets an error when the memo field is
     * blank.
     */
    @Test
    fun `validateCustomMemo with blank field sets customMemoError`() {
        val vm = buildVm()

        vm.validateCustomMemo()

        assertNotNull(vm.state.value.customMemoError)
    }

    /**
     * Verifies [DepositFormViewModel.validateSlippage] sets an error when the slippage field is
     * empty.
     */
    @Test
    fun `validateSlippage with empty field sets slippageError`() {
        val vm = buildVm()

        vm.validateSlippage()

        assertNotNull(vm.state.value.slippageError)
    }

    /**
     * Verifies that depositing a [DepositOption.WithdrawSecuredAsset] with a chain not enabled in
     * the vault shows a chain-not-enabled error.
     */
    @Test
    fun `deposit WithdrawSecuredAsset when chain not enabled in vault shows chain-not-enabled error`() =
        runTest {
            val vm = buildVm()
            vm.loadData(
                vaultId = "vault-1",
                chainId = "thorchain",
                depositType = null,
                bondAddress = null,
            )
            advanceUntilIdle()

            val btcAsset =
                TokenWithdrawSecureAsset(
                    ticker = "BTC",
                    contract = "",
                    coin = Coins.Bitcoin.BTC,
                    tokenValue =
                        TokenValue(value = BigInteger.valueOf(100_000L), token = Coins.Bitcoin.BTC),
                )
            vm.state.update {
                it.copy(
                    depositOption = DepositOption.WithdrawSecuredAsset,
                    selectedSecuredAsset = btcAsset,
                )
            }
            vm.thorAddressFieldState.setTextAndPlaceCursorAtEnd("thor1abc")

            vm.deposit()
            advanceUntilIdle()

            val errorText = vm.state.value.errorText
            assertIs<UiText.FormattedText>(errorText)
            assertEquals(R.string.deposit_error_chain_not_enabled, errorText.resId)
        }
}
