@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.referral

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.cosmos.NativeTxFeeRune
import com.vultisig.wallet.data.api.models.thorchain.ThorOwnerData
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.ThorChainPoolCoin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCaseImpl
import com.vultisig.wallet.data.utils.decimals
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wallet.core.jni.CoinType

/**
 * Covers the payout-asset half of the referral edit (issue #5684): switching the asset is an edit
 * of its own — it enables Save with no year added — and it has to reach the memo as an alias on the
 * asset's own chain.
 */
internal class EditVaultReferralViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var blockChainSpecificRepository: BlockChainSpecificRepository
    private lateinit var gasFeeToEstimate: GasFeeToEstimatedFeeUseCaseImpl
    private lateinit var accountsRepository: AccountsRepository
    private lateinit var transactionRepository: DepositTransactionRepository
    private lateinit var thorChainApi: ThorChainApi
    private lateinit var requestResultRepository: RequestResultRepository
    private lateinit var vaultRepository: VaultRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var enableToken: EnableTokenUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Every network read in this ViewModel is wrapped in `withContext(Dispatchers.IO)`, which
        // would otherwise hop to a real thread pool the test scheduler cannot wait on.
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns testDispatcher
        // The RUNE amounts the screen formats read their ticker and precision from WalletCore,
        // whose native library a JVM test has no way to load.
        mockkStatic(COIN_TYPE_EXTENSIONS)
        every { CoinType.THORCHAIN.symbol } returns "RUNE"
        every { CoinType.THORCHAIN.decimals } returns 8
        every { CoinType.THORCHAIN.toValue(any<BigInteger>()) } returns BigDecimal.ZERO
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.ReferralVaultEdition>() } returns
            Route.ReferralVaultEdition(
                vaultId = VAULT_ID,
                code = REFERRAL_CODE,
                expiration = "1 January 2027",
            )

        navigator = mockk(relaxed = true)
        blockChainSpecificRepository = mockk(relaxed = true)
        gasFeeToEstimate = mockk(relaxed = true)
        accountsRepository = mockk(relaxed = true)
        transactionRepository = mockk(relaxed = true)
        thorChainApi = mockk(relaxed = true)
        requestResultRepository = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
        enableToken = mockk(relaxed = true)

        every { accountsRepository.loadAddress(VAULT_ID, Chain.ThorChain) } returns
            flowOf(thorAddress())
        coEvery { thorChainApi.getTHORChainReferralFees() } returns
            NativeTxFeeRune(
                value = "2000000",
                registerFeeRune = "1000000000",
                feePerBlock = "20",
                runePriceInTor = null,
            )
        coEvery { thorChainApi.getReferralCodeInfo(REFERRAL_CODE) } returns thorName(NO_ASSET)
        coEvery { vaultRepository.get(VAULT_ID) } returns Vault(id = VAULT_ID, name = "Test Vault")
        coEvery { chainAccountAddressRepository.getAddress(any<Coin>(), any<Vault>()) } returns
            Pair(ETH_ADDRESS, "derivedPubKey")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        unmockkStatic(COIN_TYPE_EXTENSIONS)
        unmockkStatic(Dispatchers::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `save stays disabled while nothing has been edited`() =
        runTest(testDispatcher) {
            val model = createViewModel()
            advanceUntilIdle()

            assertFalse(model.state.value.isSaveEnabled)
        }

    @Test
    fun `changing only the payout asset enables save`() =
        runTest(testDispatcher) {
            coEvery { requestResultRepository.request<ThorChainPoolCoin>(any()) } returns usdc()

            val model = createViewModel()
            advanceUntilIdle()
            model.onSelectPayoutAsset()
            advanceUntilIdle()

            assertEquals(0, model.state.value.referralCounter)
            assertTrue(model.state.value.isSaveEnabled)
            assertEquals("USDC", model.state.value.payoutAsset?.ticker)
        }

    @Test
    fun `re-picking the asset the name already carries leaves nothing to save`() =
        runTest(testDispatcher) {
            coEvery { thorChainApi.getReferralCodeInfo(REFERRAL_CODE) } returns thorName(USDC_ASSET)
            coEvery { requestResultRepository.request<ThorChainPoolCoin>(any()) } returns usdc()

            val model = createViewModel()
            advanceUntilIdle()
            model.onSelectPayoutAsset()
            advanceUntilIdle()

            assertFalse(model.state.value.isSaveEnabled)
        }

    @Test
    fun `the name's current payout asset is shown and kept through a year-only edit`() =
        runTest(testDispatcher) {
            coEvery { thorChainApi.getReferralCodeInfo(REFERRAL_CODE) } returns thorName(USDC_ASSET)

            val model = createViewModel()
            advanceUntilIdle()
            assertEquals("USDC", model.state.value.payoutAsset?.ticker)

            model.onIncrementCounter()
            advanceUntilIdle()
            model.onSavedReferral()
            advanceUntilIdle()

            assertEquals(
                "~:$REFERRAL_CODE:ETH:$ETH_ADDRESS:$THOR_ADDRESS:$USDC_ASSET",
                capturedMemo(),
            )
        }

    @Test
    fun `a chosen payout asset registers its chain's alias and is added to the vault`() =
        runTest(testDispatcher) {
            coEvery { requestResultRepository.request<ThorChainPoolCoin>(any()) } returns usdc()

            val model = createViewModel()
            advanceUntilIdle()
            model.onSelectPayoutAsset()
            advanceUntilIdle()
            model.onSavedReferral()
            advanceUntilIdle()

            assertEquals(
                "~:$REFERRAL_CODE:ETH:$ETH_ADDRESS:$THOR_ADDRESS:$USDC_ASSET",
                capturedMemo(),
            )
            coVerify { enableToken(VAULT_ID, Coins.Ethereum.USDC) }
        }

    @Test
    fun `an unchanged payout asset keeps the plain renewal memo`() =
        runTest(testDispatcher) {
            val model = createViewModel()
            advanceUntilIdle()
            model.onIncrementCounter()
            advanceUntilIdle()
            model.onSavedReferral()
            advanceUntilIdle()

            assertEquals("~:$REFERRAL_CODE:THOR:$THOR_ADDRESS:$THOR_ADDRESS", capturedMemo())
        }

    private fun capturedMemo(): String {
        val tx = slot<DepositTransaction>()
        coVerify { transactionRepository.addTransaction(capture(tx)) }
        return tx.captured.memo
    }

    private fun createViewModel() =
        EditVaultReferralViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            blockChainSpecificRepository = blockChainSpecificRepository,
            gasFeeToEstimate = gasFeeToEstimate,
            accountsRepository = accountsRepository,
            transactionRepository = transactionRepository,
            thorChainApi = thorChainApi,
            requestResultRepository = requestResultRepository,
            vaultRepository = vaultRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            enableToken = enableToken,
        )

    private fun usdc() = ThorChainPoolCoin(asset = USDC_ASSET, coin = Coins.Ethereum.USDC)

    private fun thorName(preferredAsset: String) =
        ThorOwnerData(
            name = REFERRAL_CODE,
            expireBlockHeight = 20_000_000L,
            owner = THOR_ADDRESS,
            preferredAsset = preferredAsset,
            preferredAssetSwapThresholdRune = "0",
            affiliateCollectorRune = "0",
        )

    private fun thorAddress(): Address {
        val rune = Coins.ThorChain.RUNE.copy(address = THOR_ADDRESS)
        return Address(
            chain = Chain.ThorChain,
            address = THOR_ADDRESS,
            accounts =
                listOf(
                    Account(
                        token = rune,
                        tokenValue = TokenValue(BigInteger("100000000000"), rune),
                        fiatValue = null,
                        price = null,
                    )
                ),
        )
    }

    private companion object {
        const val VAULT_ID = "vault-id"
        const val REFERRAL_CODE = "VULT"
        const val THOR_ADDRESS = "thor1qzr6dfsxw8fjc9pj39cj7cxwlm8v49g0v5f9tw"
        const val ETH_ADDRESS = "0x1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f00"
        const val USDC_ASSET = "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"
        const val NO_ASSET = "."
        const val COIN_TYPE_EXTENSIONS = "com.vultisig.wallet.data.utils.CoinTypeKt"
    }
}
