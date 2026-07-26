@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.api.MayaLpPoolStats
import com.vultisig.wallet.data.api.MayaMemberDetails
import com.vultisig.wallet.data.api.MayaMemberPool
import com.vultisig.wallet.data.api.MayaNodePool
import com.vultisig.wallet.data.blockchain.maya.MayaCacaoStakingDetails
import com.vultisig.wallet.data.blockchain.maya.MayaCacaoStakingService
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.monoToneLogo
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DefiPositionsRepository
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.MayachainBondUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.MAYA_BOND_CACAO_KEY
import com.vultisig.wallet.ui.screens.v2.defi.MAYA_STAKE_CACAO_KEY
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

/**
 * Characterization tests: they pin the behavior this ViewModel has today so the upcoming split into
 * a shared Thorchain/Maya base can be verified as behavior-preserving. They describe what the code
 * does, not necessarily what it ought to do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MayachainDefiPositionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var vaultRepository: VaultRepository
    private lateinit var bondUseCase: MayachainBondUseCase
    private lateinit var mayachainBondRepository: MayachainBondRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var mayaCacaoStakingService: MayaCacaoStakingService
    private lateinit var tokenPriceRepository: TokenPriceRepository
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    private lateinit var defiPositionsRepository: DefiPositionsRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        navigator = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        bondUseCase = mockk(relaxed = true)
        mayachainBondRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
        mayaCacaoStakingService = mockk(relaxed = true)
        tokenPriceRepository = mockk(relaxed = true)
        appCurrencyRepository = mockk(relaxed = true)
        balanceVisibilityRepository = mockk(relaxed = true)
        defiPositionsRepository = mockk(relaxed = true)

        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { balanceVisibilityRepository.getVisibility(VAULT_ID) } returns true
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.US)
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns BigDecimal("2")
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true

        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY)
        coEvery { mayachainBondRepository.getMayaNodePools() } returns emptyList()
        coEvery { mayachainBondRepository.getMemberDetails(any()) } returns MayaMemberDetails()
        coEvery { mayachainBondRepository.getLpPoolStats() } returns emptyList()
        coEvery { bondUseCase.getActiveNodes(any(), any()) } returns flowOf(emptyList())
        coEvery { mayaCacaoStakingService.getStakingDetails(any()) } returns
            flowOf(MayaCacaoStakingDetails(BigInteger.ZERO, apr = null, canUnstake = false))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setData emits Success seeded with the default Maya bond and stake selection`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val data = successData(vm)
        assertEquals(listOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY), data.selectedPositions)
        assertTrue(data.isBalanceVisible)
    }

    @Test
    fun `a saved selection holding no Maya key falls back to the Maya defaults`() = runTest {
        // Selection persisted by the Thorchain screen: recognising it here would blank both tabs.
        selectPositions("RUNE", "TCY")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertEquals(
            listOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY),
            successData(vm).selectedPositions,
        )
    }

    @Test
    fun `a saved selection holding a Maya key is used verbatim`() = runTest {
        selectPositions(MAYA_BOND_CACAO_KEY)

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertEquals(listOf(MAYA_BOND_CACAO_KEY), successData(vm).selectedPositions)
    }

    @Test
    fun `a dotted LP asset key counts as a Maya selection`() = runTest {
        // "BTC.BTC" carries no Maya prefix; the dot is what marks it as an LP pool key.
        selectPositions("BTC.BTC")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertEquals(listOf("BTC.BTC"), successData(vm).selectedPositions)
    }

    @Test
    fun `bonded nodes are mapped with truncated address and formatted amounts`() = runTest {
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
            flowOf(listOf(bondedNode(amount = HUNDRED_CACAO)))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val bonded = successData(vm).bonded
        assertFalse(bonded.isLoading)
        assertEquals("100.00000000 CACAO", bonded.totalBondedAmount)
        assertEquals("$200.00", bonded.totalBondedPrice)

        val node = bonded.nodes.single()
        assertEquals("maya1qwer...fgh", node.address)
        assertEquals(NODE_ADDRESS, node.fullAddress)
        assertEquals(BondNodeState.ACTIVE, node.status)
        assertEquals("15.25%", node.apy)
        assertEquals("100.00000000 CACAO", node.bondedAmount)
        assertEquals("1.2345 CACAO", node.nextAward)
        assertEquals("N/A", node.nextChurn)
    }

    @Test
    fun `a vault without a CACAO coin leaves the bonded tab empty and settled`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT.copy(coins = emptyList())

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val bonded = successData(vm).bonded
        assertFalse(bonded.isLoading)
        assertTrue(bonded.nodes.isEmpty())
    }

    @Test
    fun `the CACAO staking position carries its maturity hints through to the UI`() = runTest {
        coEvery { mayaCacaoStakingService.getStakingDetails(CACAO_ADDRESS) } returns
            flowOf(
                MayaCacaoStakingDetails(
                    stakeAmount = FIFTY_CACAO,
                    apr = 0.0812,
                    canUnstake = false,
                    unstakeUnlocksInSeconds = 3_600L,
                    isUnstakeMaturityUnknown = true,
                )
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val position = successData(vm).staking.positions.single()
        assertEquals("50.0000 CACAO", position.stakedAmountDisplay)
        assertEquals("$100.00", position.stakedFiatDisplay)
        assertEquals("8.12%", position.apy)
        assertFalse(position.canUnstake)
        assertEquals(3_600L, position.unstakeUnlocksInSeconds)
        assertTrue(position.isUnstakeMaturityUnknown)
    }

    @Test
    fun `deselecting the stake key clears the staking tab`() = runTest {
        selectPositions(MAYA_BOND_CACAO_KEY)
        coEvery { mayaCacaoStakingService.getStakingDetails(CACAO_ADDRESS) } returns
            flowOf(MayaCacaoStakingDetails(FIFTY_CACAO, apr = 0.05, canUnstake = true))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertTrue(successData(vm).staking.positions.isEmpty())
    }

    @Test
    fun `the header total sums the bonded and staked amounts`() = runTest {
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
            flowOf(listOf(bondedNode(amount = HUNDRED_CACAO)))
        coEvery { mayaCacaoStakingService.getStakingDetails(CACAO_ADDRESS) } returns
            flowOf(MayaCacaoStakingDetails(FIFTY_CACAO, apr = null, canUnstake = false))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        // (100 bonded + 50 staked) CACAO at a price of 2.
        val data = successData(vm)
        assertEquals("$300.00", data.totalAmountPrice)
        assertFalse(data.isTotalAmountLoading)
    }

    @Test
    fun `an LP position is derived from the members share of the pool depths`() = runTest {
        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, BTC_POOL)
        givenLpPool(liquidityUnits = "100", units = "1000")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val lp = successData(vm).lp
        assertFalse(lp.isLoading)
        val position = lp.positions.single()
        assertEquals("CACAO/BTC Pool", position.titleLp)
        assertEquals("BTC", position.assetTicker)
        // 10% of a pool holding 1 BTC and 1 CACAO.
        assertEquals("0.1 CACAO + 0.1 BTC", position.position)
        assertEquals("$0.40", position.totalPriceLp)
        assertEquals("25.00%", position.apr)
        assertTrue(position.canRemove)
        assertEquals(Chain.Bitcoin.monoToneLogo, position.chainLogo)
    }

    @Test
    fun `a pool reporting zero units falls back to the raw added amounts`() = runTest {
        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, BTC_POOL)
        givenLpPool(
            liquidityUnits = "0",
            units = "0",
            assetAdded = "50000000",
            cacaoAdded = "20000000000",
        )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val position = successData(vm).lp.positions.single()
        assertEquals("2 CACAO + 0.5 BTC", position.position)
        assertEquals("$5.00", position.totalPriceLp)
        // Nothing to withdraw without liquidity units.
        assertFalse(position.canRemove)
    }

    @Test
    fun `the LP tab stays empty while only the static Maya keys are selected`() = runTest {
        givenLpPool(liquidityUnits = "100", units = "1000")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val lp = successData(vm).lp
        assertFalse(lp.isLoading)
        assertTrue(lp.positions.isEmpty())
    }

    @Test
    fun `bond and unbond route to the deposit flow carrying the node address`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onClickBond(NODE_ADDRESS)
        vm.onClickUnBond(NODE_ADDRESS)

        coVerify(exactly = 1) {
            navigator.route(
                Route.Deposit(
                    vaultId = VAULT_ID,
                    chainId = Chain.MayaChain.id,
                    depositType = DeFiNavActions.BOND.type,
                    bondAddress = NODE_ADDRESS,
                )
            )
        }
        coVerify(exactly = 1) {
            navigator.route(
                Route.Deposit(
                    vaultId = VAULT_ID,
                    chainId = Chain.MayaChain.id,
                    depositType = DeFiNavActions.UNBOND.type,
                    bondAddress = NODE_ADDRESS,
                )
            )
        }
    }

    @Test
    fun `stake and LP actions route with their pool context`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onNavigateToStake(DeFiNavActions.STAKE_CACAO)
        vm.onNavigateToLp(BTC_POOL, DeFiNavActions.ADD_LP)
        vm.bondToNode()

        coVerify(exactly = 1) {
            navigator.route(
                Route.Deposit(
                    vaultId = VAULT_ID,
                    chainId = Chain.MayaChain.id,
                    depositType = DeFiNavActions.STAKE_CACAO.type,
                )
            )
        }
        coVerify(exactly = 1) {
            navigator.route(
                Route.Deposit(
                    vaultId = VAULT_ID,
                    chainId = Chain.MayaChain.id,
                    depositType = DeFiNavActions.ADD_LP.type,
                    poolId = BTC_POOL,
                )
            )
        }
        coVerify(exactly = 1) {
            navigator.route(Route.BondForm(vaultId = VAULT_ID, chainId = Chain.MayaChain.id))
        }
    }

    @Test
    fun `opening the selection dialog rebases the draft on the committed selection`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onPositionSelectionChange(BTC_POOL, selected = true)
        // Reopening must discard the abandoned draft.
        vm.setPositionSelectionDialogVisibility(true)

        val data = successData(vm)
        assertTrue(data.showPositionSelectionDialog)
        assertEquals(data.selectedPositions, data.tempSelectedPositions)
    }

    @Test
    fun `confirming the dialog persists the draft and closes it`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.setPositionSelectionDialogVisibility(true)
        vm.onPositionSelectionChange(BTC_POOL, selected = true)
        vm.onPositionSelectionChange(MAYA_BOND_CACAO_KEY, selected = false)
        vm.onPositionSelectionDone()

        val expected = listOf(MAYA_STAKE_CACAO_KEY, BTC_POOL)
        coVerify(exactly = 1) { defiPositionsRepository.saveSelectedPositions(VAULT_ID, expected) }
        val data = successData(vm)
        assertFalse(data.showPositionSelectionDialog)
        assertEquals(expected, data.selectedPositions)
    }

    private fun selectPositions(vararg keys: String) {
        coEvery { defiPositionsRepository.getSelectedPositions(VAULT_ID) } returns
            flowOf(keys.toSet())
    }

    private fun givenLpPool(
        liquidityUnits: String,
        units: String,
        assetAdded: String = "0",
        cacaoAdded: String = "0",
    ) {
        coEvery { mayachainBondRepository.getMayaNodePools() } returns
            listOf(MayaNodePool(asset = BTC_POOL, status = "Available"))
        coEvery { mayachainBondRepository.getMemberDetails(CACAO_ADDRESS) } returns
            MayaMemberDetails(
                pools =
                    listOf(
                        MayaMemberPool(
                            pool = BTC_POOL,
                            assetAdded = assetAdded,
                            cacaoAdded = cacaoAdded,
                            liquidityUnits = liquidityUnits,
                        )
                    )
            )
        coEvery { mayachainBondRepository.getLpPoolStats() } returns
            listOf(
                MayaLpPoolStats(
                    asset = BTC_POOL,
                    annualPercentageRate = "0.25",
                    status = "Available",
                    assetDepth = "100000000",
                    cacaoDepth = "10000000000",
                    units = units,
                )
            )
    }

    private fun bondedNode(amount: BigInteger) =
        BondedNodePosition(
            id = "cacao-node",
            node = BondedNodePosition.BondedNode(address = NODE_ADDRESS, state = "active"),
            amount = amount,
            coin = CACAO_COIN,
            apy = 0.1525,
            nextReward = 12_345_678_901.0,
            nextChurn = null,
        )

    private fun successData(vm: MayachainDefiPositionsViewModel): MayachainDefiPositionsUiModel {
        val state = vm.state.value
        assertTrue(state is MayachainDefiUiState.Success, "expected Success, was $state")
        return (state as MayachainDefiUiState.Success).data
    }

    private fun createViewModel(): MayachainDefiPositionsViewModel =
        MayachainDefiPositionsViewModel(
            navigator = navigator,
            vaultRepository = vaultRepository,
            bondUseCase = bondUseCase,
            mayachainBondRepository = mayachainBondRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            mayaCacaoStakingService = mayaCacaoStakingService,
            tokenPriceRepository = tokenPriceRepository,
            appCurrencyRepository = appCurrencyRepository,
            balanceVisibilityRepository = balanceVisibilityRepository,
            defiPositionsRepository = defiPositionsRepository,
            // Real calculator over the mocked price repository: the fiat assertions below stay
            // end-to-end rather than asserting against a stubbed conversion.
            fiatValueCalculator = DefiFiatValueCalculator(tokenPriceRepository),
            ioDispatcher = testDispatcher,
        )

    private companion object {
        const val VAULT_ID = "vault-1"
        const val CACAO_ADDRESS = "maya1cacaoaddress"
        const val NODE_ADDRESS = "maya1qwertyuiopasdfgh"
        const val BTC_POOL = "BTC.BTC"

        val HUNDRED_CACAO: BigInteger = BigInteger("1000000000000")
        val FIFTY_CACAO: BigInteger = BigInteger("500000000000")

        val CACAO_COIN = Coins.MayaChain.CACAO.copy(address = CACAO_ADDRESS)
        val BTC_COIN = Coins.Bitcoin.BTC.copy(address = "bc1qbtcaddress")

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
                coins = listOf(CACAO_COIN, BTC_COIN),
            )
    }
}
