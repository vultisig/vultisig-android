@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.settings.AppCurrency
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
import com.vultisig.wallet.data.usecases.GetMayaCacaoMaturityStatusUseCase
import com.vultisig.wallet.data.usecases.GetThorChainLpPositionUseCase
import com.vultisig.wallet.data.usecases.MayaCacaoMaturityStatus
import com.vultisig.wallet.data.usecases.RequestAddressBookEntryUseCase
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.data.usecases.ThorChainLpPreflightBlock
import com.vultisig.wallet.data.usecases.ThorChainLpPreflightUseCase
import com.vultisig.wallet.data.usecases.ValidateMayaTransactionHeightUseCase
import com.vultisig.wallet.ui.models.deposit.load.CacaoMaturityLoader
import com.vultisig.wallet.ui.models.deposit.load.DepositAmountHelper
import com.vultisig.wallet.ui.models.deposit.load.DepositDataLoader
import com.vultisig.wallet.ui.models.deposit.load.DepositFieldInputCoordinator
import com.vultisig.wallet.ui.models.deposit.load.DepositOptionCoordinator
import com.vultisig.wallet.ui.models.deposit.load.LiquidityDataLoader
import com.vultisig.wallet.ui.models.deposit.load.NodeWhitelistChecker
import com.vultisig.wallet.ui.models.deposit.load.RujiBalancesLoader
import com.vultisig.wallet.ui.models.deposit.load.SecuredAssetLoader
import com.vultisig.wallet.ui.models.deposit.submit.DepositStrategyFactory
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
    private val getMayaCacaoMaturityStatus: GetMayaCacaoMaturityStatusUseCase =
        mockk(relaxed = true)
    private val feeServiceComposite: FeeServiceComposite = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val tokenRepository: TokenRepository = mockk(relaxed = true)
    private val gasFeeToEstimate: GasFeeToEstimatedFeeUseCaseImpl = mockk(relaxed = true)
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase = mockk(relaxed = true)
    private val getThorChainLpPositionUseCase: GetThorChainLpPositionUseCase = mockk(relaxed = true)
    private val liquidityDataLoaderFactory: LiquidityDataLoader.Factory =
        object : LiquidityDataLoader.Factory {
            override fun create(
                scope: CoroutineScope,
                state: MutableStateFlow<DepositFormUiModel>,
                address: StateFlow<Address?>,
                assetsFieldState: TextFieldState,
                lpUnitsFieldState: TextFieldState,
                vaultId: () -> String?,
                lpPoolId: () -> String?,
                resolvePairedAddress: suspend (Chain, String, String) -> String?,
            ): LiquidityDataLoader =
                LiquidityDataLoader(
                    mayachainBondRepository = mayachainBondRepository,
                    getThorChainLpPositionUseCase = getThorChainLpPositionUseCase,
                    scope = scope,
                    state = state,
                    address = address,
                    assetsFieldState = assetsFieldState,
                    lpUnitsFieldState = lpUnitsFieldState,
                    vaultId = vaultId,
                    lpPoolId = lpPoolId,
                    resolvePairedAddress = resolvePairedAddress,
                )
        }
    private val securedAssetLoaderFactory: SecuredAssetLoader.Factory =
        object : SecuredAssetLoader.Factory {
            override fun create(
                scope: CoroutineScope,
                thorAddressFieldState: TextFieldState,
                vaultId: () -> String?,
                selectedToken: () -> Coin,
            ): SecuredAssetLoader =
                SecuredAssetLoader(
                    vaultRepository = vaultRepository,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                    thorChainApi = thorChainApi,
                    scope = scope,
                    thorAddressFieldState = thorAddressFieldState,
                    vaultId = vaultId,
                    selectedToken = selectedToken,
                )
        }
    private val cacaoMaturityLoaderFactory: CacaoMaturityLoader.Factory =
        object : CacaoMaturityLoader.Factory {
            override fun create(
                scope: CoroutineScope,
                onResult: (isMature: Boolean, unlocksInText: UiText?) -> Unit,
            ): CacaoMaturityLoader =
                CacaoMaturityLoader(
                    getMayaCacaoMaturityStatus = getMayaCacaoMaturityStatus,
                    scope = scope,
                    onResult = onResult,
                )
        }
    private val rujiBalancesLoaderFactory: RujiBalancesLoader.Factory =
        object : RujiBalancesLoader.Factory {
            override fun create(
                scope: CoroutineScope,
                tokenAmountFieldState: TextFieldState,
                addressProvider: () -> String?,
                selectedUnMergeCoinProvider: () -> TokenMergeInfo,
                onSharesBalance: (UiText) -> Unit,
                setLoading: (Boolean) -> Unit,
            ): RujiBalancesLoader =
                RujiBalancesLoader(
                    thorChainApi = thorChainApi,
                    scope = scope,
                    tokenAmountFieldState = tokenAmountFieldState,
                    addressProvider = addressProvider,
                    selectedUnMergeCoinProvider = selectedUnMergeCoinProvider,
                    onSharesBalance = onSharesBalance,
                    setLoading = setLoading,
                )
        }
    private val nodeWhitelistCheckerFactory: NodeWhitelistChecker.Factory =
        object : NodeWhitelistChecker.Factory {
            override fun create(
                scope: CoroutineScope,
                state: MutableStateFlow<DepositFormUiModel>,
                address: StateFlow<Address?>,
                nodeAddressFieldState: TextFieldState,
                chainProvider: () -> Chain?,
            ): NodeWhitelistChecker =
                NodeWhitelistChecker(
                    mayachainBondRepository = mayachainBondRepository,
                    scope = scope,
                    state = state,
                    address = address,
                    nodeAddressFieldState = nodeAddressFieldState,
                    chainProvider = chainProvider,
                )
        }
    private val dataLoaderFactory: DepositDataLoader.Factory =
        object : DepositDataLoader.Factory {
            override fun create(
                scope: CoroutineScope,
                address: MutableStateFlow<Address?>,
                depositTypeActionProvider: () -> String?,
                clearDepositTypeAction: () -> Unit,
                selectDepositOption: (DepositOption) -> Unit,
            ): DepositDataLoader =
                DepositDataLoader(
                    accountsRepository = accountsRepository,
                    scope = scope,
                    address = address,
                    depositTypeActionProvider = depositTypeActionProvider,
                    clearDepositTypeAction = clearDepositTypeAction,
                    selectDepositOption = selectDepositOption,
                )
        }
    private val depositOptionCoordinatorFactory: DepositOptionCoordinator.Factory =
        object : DepositOptionCoordinator.Factory {
            override fun create(
                scope: CoroutineScope,
                state: MutableStateFlow<DepositFormUiModel>,
                address: StateFlow<Address?>,
                fields: DepositFieldStates,
                liquidityDataLoader: LiquidityDataLoader,
                securedAssetLoader: SecuredAssetLoader,
                cacaoMaturityLoader: CacaoMaturityLoader,
                chainProvider: () -> Chain?,
                vaultId: () -> String?,
                bondAddress: () -> String?,
            ): DepositOptionCoordinator =
                DepositOptionCoordinator(
                    mayaChainApi = mayaChainApi,
                    accountsRepository = accountsRepository,
                    mapTokenValueToStringWithUnit = mapTokenValueToStringWithUnit,
                    scope = scope,
                    state = state,
                    address = address,
                    fields = fields,
                    liquidityDataLoader = liquidityDataLoader,
                    securedAssetLoader = securedAssetLoader,
                    cacaoMaturityLoader = cacaoMaturityLoader,
                    chainProvider = chainProvider,
                    vaultId = vaultId,
                    bondAddress = bondAddress,
                )
        }
    private val thorChainLpPreflight: ThorChainLpPreflightUseCase = mockk(relaxed = true)
    private val fieldValidator: DepositFieldValidator =
        DepositFieldValidatorImpl(chainAccountAddressRepository)
    private val depositFieldInputCoordinatorFactory: DepositFieldInputCoordinator.Factory =
        object : DepositFieldInputCoordinator.Factory {
            override fun create(
                scope: CoroutineScope,
                state: MutableStateFlow<DepositFormUiModel>,
                fields: DepositFieldStates,
                nodeWhitelistChecker: NodeWhitelistChecker,
                chainProvider: () -> Chain?,
                vaultId: () -> String?,
            ): DepositFieldInputCoordinator =
                DepositFieldInputCoordinator(
                    fieldValidator = fieldValidator,
                    isAssetCharsValid = isAssetCharsValid,
                    accountsRepository = accountsRepository,
                    scope = scope,
                    state = state,
                    fields = fields,
                    nodeWhitelistChecker = nodeWhitelistChecker,
                    chainProvider = chainProvider,
                    vaultId = vaultId,
                )
        }
    private val gasFeeHelper: DepositGasFeeHelper =
        DepositGasFeeHelper(
            vaultRepository = vaultRepository,
            feeServiceComposite = feeServiceComposite,
            tokenRepository = tokenRepository,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            tokenPriceRepository = tokenPriceRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
        )
    private val depositAmountHelperFactory: DepositAmountHelper.Factory =
        object : DepositAmountHelper.Factory {
            override fun create(
                scope: CoroutineScope,
                fields: DepositFieldStates,
                appCurrency: StateFlow<AppCurrency>,
                state: MutableStateFlow<DepositFormUiModel>,
                chain: () -> Chain?,
                vaultId: () -> String?,
            ): DepositAmountHelper =
                DepositAmountHelper(
                    mapTokenValueToStringWithUnit = mapTokenValueToStringWithUnit,
                    gasFeeHelper = gasFeeHelper,
                    scope = scope,
                    fields = fields,
                    appCurrency = appCurrency,
                    state = state,
                    chain = chain,
                    vaultId = vaultId,
                )
        }
    private val depositStrategyFactory: DepositStrategyFactory =
        DepositStrategyFactory(
            accountsRepository = accountsRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            vaultRepository = vaultRepository,
            tokenRepository = tokenRepository,
            feeServiceComposite = feeServiceComposite,
            gasFeeToEstimate = gasFeeToEstimate,
            thorChainLpPreflight = thorChainLpPreflight,
            validateMayaTransactionHeight = validateMayaTransactionHeight,
            isAssetCharsValid = isAssetCharsValid,
            fieldValidator = fieldValidator,
        )

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
            mapTokenValueToStringWithUnit = mapTokenValueToStringWithUnit,
            requestResultRepository = requestResultRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            transactionRepository = transactionRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            balanceRepository = balanceRepository,
            vaultRepository = vaultRepository,
            requestAddressBookEntry = requestAddressBookEntry,
            liquidityDataLoaderFactory = liquidityDataLoaderFactory,
            securedAssetLoaderFactory = securedAssetLoaderFactory,
            cacaoMaturityLoaderFactory = cacaoMaturityLoaderFactory,
            rujiBalancesLoaderFactory = rujiBalancesLoaderFactory,
            nodeWhitelistCheckerFactory = nodeWhitelistCheckerFactory,
            dataLoaderFactory = dataLoaderFactory,
            depositOptionCoordinatorFactory = depositOptionCoordinatorFactory,
            depositFieldInputCoordinatorFactory = depositFieldInputCoordinatorFactory,
            depositAmountHelperFactory = depositAmountHelperFactory,
            gasFeeHelper = gasFeeHelper,
            depositStrategyFactory = depositStrategyFactory,
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
    fun `deposit for AddLiquidity surfaces preflight block as errorText`() = runTest {
        val pool = "ETH.USDT-0xdac17f958d2ee523a2206206994597c13d831ec7"
        val vm = buildViewModel()
        coEvery { accountsRepository.loadAddress("vault1", Chain.ThorChain) } returns
            flowOf(
                Address(
                    chain = Chain.ThorChain,
                    address = "thor1somevalidaddress",
                    accounts =
                        listOf(
                            Account(
                                token = Coins.ThorChain.RUNE,
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                )
            )
        coEvery { thorChainLpPreflight.invoke(pool) } returns
            ThorChainLpPreflightBlock.LpPaused(pool)

        vm.loadData("vault1", Chain.ThorChain.raw, null, null, pool)
        advanceUntilIdle()
        vm.selectDepositOption(DepositOption.AddLiquidity)
        vm.tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1")
        vm.deposit()
        advanceUntilIdle()

        val errorText = vm.state.value.errorText
        assertNotNull(errorText)
        assertTrue(errorText is UiText.FormattedText)
        assertEquals(
            R.string.deposit_error_lp_paused_pool,
            (errorText as UiText.FormattedText).resId,
        )
    }

    @Test
    fun `RemoveCacaoPool with mature status enables CTA and clears unlocksIn text`() = runTest {
        val vm = buildViewModel()
        coEvery { accountsRepository.loadAddress("vault1", Chain.MayaChain) } returns
            flowOf(
                Address(
                    chain = Chain.MayaChain,
                    address = "maya1someaddress",
                    accounts =
                        listOf(
                            Account(
                                token = Coins.MayaChain.CACAO,
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                )
            )
        coEvery { mayaChainApi.getUnStakeCacaoBalance("maya1someaddress") } returns "0"
        coEvery { getMayaCacaoMaturityStatus.invoke("maya1someaddress") } returns
            MayaCacaoMaturityStatus(isMature = true, remainingBlocks = 0L, remainingSeconds = 0L)

        vm.loadData("vault1", Chain.MayaChain.raw, "unstake_cacao", null)
        advanceUntilIdle()

        assertTrue(vm.state.value.isUnstakeMature)
        assertEquals(null, vm.state.value.unstakeUnlocksInText)
    }

    @Test
    fun `RemoveCacaoPool with under-one-hour remaining uses hours and minutes format`() = runTest {
        val vm = buildViewModel()
        coEvery { accountsRepository.loadAddress("vault1", Chain.MayaChain) } returns
            flowOf(
                Address(
                    chain = Chain.MayaChain,
                    address = "maya1someaddress",
                    accounts =
                        listOf(
                            Account(
                                token = Coins.MayaChain.CACAO,
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                )
            )
        coEvery { mayaChainApi.getUnStakeCacaoBalance("maya1someaddress") } returns "0"
        coEvery { getMayaCacaoMaturityStatus.invoke("maya1someaddress") } returns
            MayaCacaoMaturityStatus(
                isMature = false,
                remainingBlocks = 300L,
                remainingSeconds = 1_800L, // 30 minutes
            )

        vm.loadData("vault1", Chain.MayaChain.raw, "unstake_cacao", null)
        advanceUntilIdle()

        assertTrue(!vm.state.value.isUnstakeMature)
        val unlocksIn = vm.state.value.unstakeUnlocksInText
        assertNotNull(unlocksIn)
        assertTrue(unlocksIn is UiText.FormattedText)
        assertEquals(
            R.string.unstake_cacao_unlocks_in_hours_format,
            (unlocksIn as UiText.FormattedText).resId,
        )
        // 30 minutes -> "0h 30m"; the precision is what the formatter change protects against
        // the prior "Unlocks soon" collapse.
        assertEquals(listOf<Any>(0, 30), unlocksIn.formatArgs)
    }

    @Test
    fun `RemoveCacaoPool default state keeps CTA disabled before maturity resolves`() = runTest {
        val vm = buildViewModel()

        assertTrue(!vm.state.value.isUnstakeMature)
    }

    @Test
    fun `RemoveCacaoPool with over-one-hour remaining uses days and hours format`() = runTest {
        val vm = buildViewModel()
        coEvery { accountsRepository.loadAddress("vault1", Chain.MayaChain) } returns
            flowOf(
                Address(
                    chain = Chain.MayaChain,
                    address = "maya1someaddress",
                    accounts =
                        listOf(
                            Account(
                                token = Coins.MayaChain.CACAO,
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                )
            )
        coEvery { mayaChainApi.getUnStakeCacaoBalance("maya1someaddress") } returns "0"
        coEvery { getMayaCacaoMaturityStatus.invoke("maya1someaddress") } returns
            MayaCacaoMaturityStatus(
                isMature = false,
                remainingBlocks = 15_000L,
                remainingSeconds = 90_000L, // 25 hours
            )

        vm.loadData("vault1", Chain.MayaChain.raw, "unstake_cacao", null)
        advanceUntilIdle()

        assertTrue(!vm.state.value.isUnstakeMature)
        val unlocksIn = vm.state.value.unstakeUnlocksInText
        assertNotNull(unlocksIn)
        assertTrue(unlocksIn is UiText.FormattedText)
        assertEquals(
            R.string.unstake_cacao_unlocks_in_days_hours_format,
            (unlocksIn as UiText.FormattedText).resId,
        )
        assertEquals(listOf<Any>(1, 1), unlocksIn.formatArgs)
    }

    @Test
    fun `RemoveCacaoPool when maturity check throws keeps CTA disabled and clears unlocksIn text`() =
        runTest {
            val vm = buildViewModel()
            coEvery { accountsRepository.loadAddress("vault1", Chain.MayaChain) } returns
                flowOf(
                    Address(
                        chain = Chain.MayaChain,
                        address = "maya1someaddress",
                        accounts =
                            listOf(
                                Account(
                                    token = Coins.MayaChain.CACAO,
                                    tokenValue = null,
                                    fiatValue = null,
                                    price = null,
                                )
                            ),
                    )
                )
            coEvery { mayaChainApi.getUnStakeCacaoBalance("maya1someaddress") } returns "0"
            coEvery { getMayaCacaoMaturityStatus.invoke("maya1someaddress") } throws
                RuntimeException("Network error")

            vm.loadData("vault1", Chain.MayaChain.raw, "unstake_cacao", null)
            advanceUntilIdle()

            assertTrue(!vm.state.value.isUnstakeMature)
            assertEquals(null, vm.state.value.unstakeUnlocksInText)
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

    @Test
    fun `WithdrawSecuredAsset with no secured assets marks loaded with empty list`() = runTest {
        val vm = buildViewModel()
        coEvery { accountsRepository.loadAddress("vault1", Chain.ThorChain) } returns
            flowOf(
                Address(
                    chain = Chain.ThorChain,
                    address = "thor1somevalidaddress",
                    accounts =
                        listOf(
                            Account(
                                token = Coins.ThorChain.RUNE,
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                )
            )

        vm.loadData("vault1", Chain.ThorChain.raw, null, null)
        advanceUntilIdle()
        vm.selectDepositOption(DepositOption.WithdrawSecuredAsset)
        advanceUntilIdle()

        assertTrue(vm.state.value.securedAssetsLoaded)
        assertTrue(vm.state.value.availableSecuredAssets.isEmpty())
    }

    @Test
    fun `selectDepositOption Unbond on ThorChain sets RUNE as selected token`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)
        advanceUntilIdle()

        vm.selectDepositOption(DepositOption.Unbond)
        advanceUntilIdle()

        assertEquals(Coins.ThorChain.RUNE, vm.state.value.selectedToken)
    }

    @Test
    fun `selectDepositOption Leave on ThorChain sets RUNE as selected token`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)
        advanceUntilIdle()

        vm.selectDepositOption(DepositOption.Leave)
        advanceUntilIdle()

        assertEquals(Coins.ThorChain.RUNE, vm.state.value.selectedToken)
    }

    @Test
    fun `selectDepositOption AddLiquidity on ThorChain sets RUNE as selected token`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)
        advanceUntilIdle()

        vm.selectDepositOption(DepositOption.AddLiquidity)
        advanceUntilIdle()

        assertEquals(Coins.ThorChain.RUNE, vm.state.value.selectedToken)
    }

    @Test
    fun `loadData for ThorChain exposes bond unbond leave custom merge unmerge and withdraw`() =
        runTest {
            val vm = buildViewModel()

            vm.loadData("vault1", Chain.ThorChain.raw, null, null)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    DepositOption.Bond,
                    DepositOption.Unbond,
                    DepositOption.Leave,
                    DepositOption.Custom,
                    DepositOption.Merge,
                    DepositOption.UnMerge,
                    DepositOption.WithdrawSecuredAsset,
                ),
                vm.state.value.depositOptions,
            )
        }

    @Test
    fun `loadData for GaiaChain exposes only IBC transfer and switch`() = runTest {
        val vm = buildViewModel()

        vm.loadData("vault1", Chain.GaiaChain.raw, null, null)
        advanceUntilIdle()

        assertEquals(
            listOf(DepositOption.TransferIbc, DepositOption.Switch),
            vm.state.value.depositOptions,
        )
    }

    @Test
    fun `loadData for Ton exposes only stake and unstake`() = runTest {
        val vm = buildViewModel()

        vm.loadData("vault1", Chain.Ton.raw, null, null)
        advanceUntilIdle()

        assertEquals(
            listOf(DepositOption.Stake, DepositOption.Unstake),
            vm.state.value.depositOptions,
        )
    }

    @Test
    fun `validateProvider with blank provider sets providerError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)
        advanceUntilIdle()

        vm.validateProvider()

        val error = vm.state.value.providerError
        assertNotNull(error)
        assertTrue(error is UiText.StringResource)
        assertEquals(R.string.send_error_no_address, (error as UiText.StringResource).resId)
    }

    @Test
    fun `validateProvider with invalid address sets providerError`() = runTest {
        val vm = buildViewModel()
        every { chainAccountAddressRepository.isValid(Chain.ThorChain, "not-an-address") } returns
            false
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)
        advanceUntilIdle()

        vm.providerFieldState.setTextAndPlaceCursorAtEnd("not-an-address")
        vm.validateProvider()

        val error = vm.state.value.providerError
        assertNotNull(error)
        assertEquals(R.string.send_error_no_address, (error as UiText.StringResource).resId)
    }

    @Test
    fun `validateProvider with valid address clears providerError`() = runTest {
        val vm = buildViewModel()
        every { chainAccountAddressRepository.isValid(Chain.ThorChain, "thor1valid") } returns true
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)
        advanceUntilIdle()

        vm.providerFieldState.setTextAndPlaceCursorAtEnd("thor1valid")
        vm.validateProvider()

        assertEquals(null, vm.state.value.providerError)
    }

    @Test
    fun `validateNodeAddress with blank address sets nodeAddressError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)
        advanceUntilIdle()

        vm.validateNodeAddress()

        val error = vm.state.value.nodeAddressError
        assertNotNull(error)
        assertEquals(R.string.send_error_no_address, (error as UiText.StringResource).resId)
    }

    @Test
    fun `validateOperatorFee with zero sets operatorFeeError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.operatorFeeFieldState.setTextAndPlaceCursorAtEnd("0")
        vm.validateOperatorFee()

        val error = vm.state.value.operatorFeeError
        assertNotNull(error)
        assertEquals(R.string.send_from_invalid_amount, (error as UiText.StringResource).resId)
    }

    @Test
    fun `validateOperatorFee above one hundred sets operatorFeeError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.operatorFeeFieldState.setTextAndPlaceCursorAtEnd("150")
        vm.validateOperatorFee()

        val error = vm.state.value.operatorFeeError
        assertNotNull(error)
        assertEquals(R.string.send_from_invalid_amount, (error as UiText.StringResource).resId)
    }

    @Test
    fun `validateOperatorFee within range clears operatorFeeError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.operatorFeeFieldState.setTextAndPlaceCursorAtEnd("5")
        vm.validateOperatorFee()

        assertEquals(null, vm.state.value.operatorFeeError)
    }

    @Test
    fun `validateOperatorFee with blank input is a no-op and leaves a prior error in place`() =
        runTest {
            val vm = buildViewModel()
            vm.loadData("vault1", Chain.ThorChain.raw, null, null)

            vm.operatorFeeFieldState.setTextAndPlaceCursorAtEnd("0")
            vm.validateOperatorFee()
            val priorError = vm.state.value.operatorFeeError
            assertNotNull(priorError)

            vm.operatorFeeFieldState.setTextAndPlaceCursorAtEnd("")
            vm.validateOperatorFee()

            assertEquals(priorError, vm.state.value.operatorFeeError)
        }

    @Test
    fun `validateSlippage with blank sets required error`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.validateSlippage()

        val error = vm.state.value.slippageError
        assertNotNull(error)
        assertEquals(R.string.slippage_required_error, (error as UiText.StringResource).resId)
    }

    @Test
    fun `validateSlippage above one hundred sets invalid error`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.slippageFieldState.setTextAndPlaceCursorAtEnd("150")
        vm.validateSlippage()

        val error = vm.state.value.slippageError
        assertNotNull(error)
        assertEquals(R.string.slippage_invalid_error, (error as UiText.StringResource).resId)
    }

    @Test
    fun `validateSlippage with non-numeric sets format error`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.slippageFieldState.setTextAndPlaceCursorAtEnd("abc")
        vm.validateSlippage()

        val error = vm.state.value.slippageError
        assertNotNull(error)
        assertEquals(R.string.slippage_format_error, (error as UiText.StringResource).resId)
    }

    @Test
    fun `validateSlippage within range clears slippageError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.slippageFieldState.setTextAndPlaceCursorAtEnd("50")
        vm.validateSlippage()

        assertEquals(null, vm.state.value.slippageError)
    }

    @Test
    fun `validateAssets with invalid characters sets assetsError`() = runTest {
        val vm = buildViewModel()
        every { isAssetCharsValid.invoke(any()) } returns false
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.assetsFieldState.setTextAndPlaceCursorAtEnd("!!bad!!")
        vm.validateAssets()

        val error = vm.state.value.assetsError
        assertNotNull(error)
        assertEquals(R.string.deposit_error_invalid_assets, (error as UiText.StringResource).resId)
    }

    @Test
    fun `validateAssets with valid characters clears assetsError`() = runTest {
        val vm = buildViewModel()
        every { isAssetCharsValid.invoke(any()) } returns true
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.assetsFieldState.setTextAndPlaceCursorAtEnd("MAYA.CACAO")
        vm.validateAssets()

        assertEquals(null, vm.state.value.assetsError)
    }

    @Test
    fun `validateLpUnits with non-digits sets lpUnitsError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.lpUnitsFieldState.setTextAndPlaceCursorAtEnd("abc")
        vm.validateLpUnits()

        val error = vm.state.value.lpUnitsError
        assertNotNull(error)
        assertEquals(R.string.deposit_error_invalid_lpunits, (error as UiText.StringResource).resId)
    }

    @Test
    fun `validateLpUnits with positive integer clears lpUnitsError`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)

        vm.lpUnitsFieldState.setTextAndPlaceCursorAtEnd("1000")
        vm.validateLpUnits()

        assertEquals(null, vm.state.value.lpUnitsError)
    }

    @Test
    fun `validateThorAddress is a no-op outside the Switch flow`() = runTest {
        val vm = buildViewModel()
        vm.loadData("vault1", Chain.ThorChain.raw, null, null)
        advanceUntilIdle()

        vm.thorAddressFieldState.setTextAndPlaceCursorAtEnd("garbage-not-an-address")
        vm.validateThorAddress()

        assertEquals(null, vm.state.value.thorAddressError)
    }
}
