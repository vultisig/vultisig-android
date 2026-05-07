@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService.Companion.RUJI_REWARDS_COIN
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

internal class AccountsLoaderTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val accounts = MutableStateFlow<List<Account>>(emptyList())
    private val accountsRepository: AccountsRepository = mockk(relaxed = true)
    private val stakingDetailsRepository: StakingDetailsRepository = mockk(relaxed = true)

    private var defiType: DeFiNavActions? = null
    private var mscaAddress: String? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ──────── default + DeFi flow paths ────────

    @Test
    fun `load with null defi type collects loadAddresses (regular send path)`() =
        runTest(mainDispatcher) {
            defiType = null
            val ethAccount = ethAccount()
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.Ethereum,
                            address = "0x1",
                            accounts = listOf(ethAccount),
                        )
                    )
                )
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            assertEquals(listOf(ethAccount), accounts.value)
            coVerify(exactly = 0) { accountsRepository.loadDeFiAddresses(any(), any()) }
        }

    @Test
    fun `load with UNSTAKE_TCY collects loadDeFiAddresses (defi-balance path)`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.UNSTAKE_TCY
            val tcyAccount = thorAccount(Coins.ThorChain.RUNE)
            coEvery { accountsRepository.loadDeFiAddresses(VAULT_ID, false) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.ThorChain,
                            address = "thor1",
                            accounts = listOf(tcyAccount),
                        )
                    )
                )
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            assertEquals(listOf(tcyAccount), accounts.value)
            coVerify(exactly = 0) { accountsRepository.loadAddresses(any(), any()) }
        }

    @Test
    fun `load with BOND uses the regular loadAddresses flow`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.BOND
            val runeAccount = thorAccount(Coins.ThorChain.RUNE)
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.ThorChain,
                            address = "thor1",
                            accounts = listOf(runeAccount),
                        )
                    )
                )
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            assertEquals(listOf(runeAccount), accounts.value)
        }

    // ──────── WITHDRAW_RUJI ────────

    @Test
    fun `WITHDRAW_RUJI publishes the rewards-thor-ruji trio when staking details exist`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_RUJI
            val runeAccount = thorAccount(Coins.ThorChain.RUNE.copy(address = "thor1"))
            val rujiAccount = thorAccount(Coins.ThorChain.RUJI.copy(address = "thor1"))
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.ThorChain,
                            address = "thor1",
                            accounts = listOf(runeAccount, rujiAccount),
                        )
                    )
                )
            coEvery {
                stakingDetailsRepository.getStakingDetailsByCoindId(
                    VAULT_ID,
                    Coins.ThorChain.RUJI.id,
                )
            } returns stakingDetails(rewards = BigDecimal("123"))
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            // Order matters: rewards account first, then thor (RUNE), then ruji.
            assertEquals(3, accounts.value.size)
            assertTrue(accounts.value[0].token.ticker.equals(RUJI_REWARDS_COIN.ticker, true))
            assertEquals(BigInteger("123"), accounts.value[0].tokenValue?.value)
            assertEquals(runeAccount, accounts.value[1])
            assertEquals(rujiAccount, accounts.value[2])
        }

    @Test
    fun `WITHDRAW_RUJI publishes empty list when staking details are absent`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_RUJI
            val runeAccount = thorAccount(Coins.ThorChain.RUNE)
            val rujiAccount = thorAccount(Coins.ThorChain.RUJI)
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.ThorChain,
                            address = "thor1",
                            accounts = listOf(runeAccount, rujiAccount),
                        )
                    )
                )
            coEvery {
                stakingDetailsRepository.getStakingDetailsByCoindId(
                    VAULT_ID,
                    Coins.ThorChain.RUJI.id,
                )
            } returns null
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            assertEquals(emptyList(), accounts.value)
        }

    @Test
    fun `WITHDRAW_RUJI is a no-op when the RUNE account is missing`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_RUJI
            // No RUNE account in the vault — short-circuits before staking-details lookup.
            every { accountsRepository.loadAddresses(VAULT_ID) } returns flowOf(emptyList())
            val loader = build(backgroundScope)

            accounts.value = listOf(thorAccount(Coins.ThorChain.RUJI)) // sentinel
            loader.load(VAULT_ID)
            advanceUntilIdle()

            // Untouched — service returns early before mutating accounts.
            assertEquals(1, accounts.value.size)
            coVerify(exactly = 0) {
                stakingDetailsRepository.getStakingDetailsByCoindId(any(), any())
            }
        }

    // ──────── WITHDRAW_USDC_CIRCLE ────────

    @Test
    fun `WITHDRAW_USDC_CIRCLE without msca address publishes ETH plus zero USDC`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_USDC_CIRCLE
            mscaAddress = null
            val ethAccount = ethAccount()
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.Ethereum,
                            address = ethAccount.token.address,
                            accounts = listOf(ethAccount),
                        )
                    )
                )
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            assertEquals(2, accounts.value.size)
            assertEquals(ethAccount, accounts.value[0])
            assertTrue(accounts.value[1].token.ticker.equals("USDC", true))
            assertEquals(BigInteger.ZERO, accounts.value[1].tokenValue?.value)
            // No staking-details lookup happens without an MSCA address.
            coVerify(exactly = 0) { stakingDetailsRepository.getStakingDetailsById(any(), any()) }
        }

    @Test
    fun `WITHDRAW_USDC_CIRCLE with msca address publishes USDC backed by the cached stake amount`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_USDC_CIRCLE
            mscaAddress = "0xMSCA"
            val ethAccount = ethAccount()
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.Ethereum,
                            address = ethAccount.token.address,
                            accounts = listOf(ethAccount),
                        )
                    )
                )
            coEvery { stakingDetailsRepository.getStakingDetailsById(VAULT_ID, any()) } returns
                stakingDetails(stakeAmount = BigInteger("789"))
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            assertEquals(2, accounts.value.size)
            assertEquals(BigInteger("789"), accounts.value[1].tokenValue?.value)
        }

    // ──────── helpers ────────

    private fun build(scope: CoroutineScope) =
        AccountsLoader(
            scope = scope,
            accounts = accounts,
            accountsRepository = accountsRepository,
            stakingDetailsRepository = stakingDetailsRepository,
            defiTypeProvider = { defiType },
            mscaAddressProvider = { mscaAddress },
        )

    private fun ethAccount(): Account =
        Account(
            token = Coins.Ethereum.ETH.copy(address = "0xeth"),
            tokenValue =
                TokenValue(value = BigInteger("1000000000000000000"), token = Coins.Ethereum.ETH),
            fiatValue = null,
            price = null,
        )

    private fun thorAccount(coin: com.vultisig.wallet.data.models.Coin): Account =
        Account(
            token = coin,
            tokenValue = TokenValue(value = BigInteger("1000000"), token = coin),
            fiatValue = null,
            price = null,
        )

    private fun stakingDetails(
        rewards: BigDecimal? = null,
        stakeAmount: BigInteger = BigInteger.ZERO,
    ): StakingDetails =
        StakingDetails(
            id = "id",
            coin = Coins.ThorChain.RUJI,
            stakeAmount = stakeAmount,
            apr = null,
            estimatedRewards = null,
            nextPayoutDate = Date(0),
            rewards = rewards,
            rewardsCoin = null,
        )

    private companion object {
        const val VAULT_ID = "vault-id"
    }
}
