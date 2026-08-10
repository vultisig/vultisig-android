@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronFrozenV2Json
import com.vultisig.wallet.data.api.models.TronUnfrozenV2Json
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.utils.NetworkException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TronDeFiBalanceServiceTest {

    private val tronApi: TronApi = mockk()
    // Deliberately not relaxed: a lookup with the wrong vault or coin id must fail the test
    // rather than silently resolve to a child mock.
    private val stakingDetailsRepository: StakingDetailsRepository = mockk()
    private val service = TronDeFiBalanceService(tronApi, stakingDetailsRepository)

    /** Backs the repository mock so a persisted total can be read back in the same test. */
    private var persisted: StakingDetails? = null

    @BeforeEach
    fun setUp() {
        coEvery {
            stakingDetailsRepository.getStakingDetailsByCoindId(VAULT_ID, Coins.Tron.TRX.id)
        } answers { persisted }
        coEvery { stakingDetailsRepository.saveStakingDetails(VAULT_ID, any()) } answers
            {
                persisted = secondArg()
            }
        coEvery { stakingDetailsRepository.updateStakingDetails(VAULT_ID, any()) } answers
            {
                persisted = secondArg()
            }
    }

    @Test
    fun `TRX in the unfreeze cooldown keeps the position once the active freeze hits zero`() =
        runTest {
            coEvery { tronApi.getAccount(ADDRESS) } returns
                account(unfreezing = listOf(8_000_000L, 4_000_000L))

            val result = service.getRemoteDeFiBalance(ADDRESS, VAULT_ID)

            assertEquals(BigInteger.valueOf(12_000_000L), result.single().balances.single().amount)
        }

    @Test
    fun `locked total sums frozen bandwidth, frozen energy and the unfreeze cooldown`() = runTest {
        coEvery { tronApi.getAccount(ADDRESS) } returns
            account(bandwidth = 1_000_000L, energy = 2_000_000L, unfreezing = listOf(3_000_000L))

        val result = service.getRemoteDeFiBalance(ADDRESS, VAULT_ID)

        assertEquals(BigInteger.valueOf(6_000_000L), result.single().balances.single().amount)
        assertEquals(Coins.Tron.TRX, result.single().balances.single().coin)
    }

    @Test
    fun `the cached read reports the same total the remote read persisted`() = runTest {
        coEvery { tronApi.getAccount(ADDRESS) } returns
            account(bandwidth = 1_000_000L, energy = 2_000_000L, unfreezing = listOf(3_000_000L))

        val remote = service.getRemoteDeFiBalance(ADDRESS, VAULT_ID)
        val cached = service.getCacheDeFiBalance(ADDRESS, VAULT_ID)

        assertEquals(BigInteger.valueOf(6_000_000L), cached.single().balances.single().amount)
        assertEquals(remote, cached)
    }

    @Test
    fun `nothing locked yields no position and clears the cached total`() = runTest {
        persisted = stakingDetails(BigInteger.valueOf(5_000_000L))
        coEvery { tronApi.getAccount(ADDRESS) } returns account()

        val remote = service.getRemoteDeFiBalance(ADDRESS, VAULT_ID)

        assertEquals(emptyList(), remote)
        assertEquals(emptyList(), service.getCacheDeFiBalance(ADDRESS, VAULT_ID))
    }

    @Test
    fun `a fetch failure keeps the cached position instead of erasing it`() = runTest {
        persisted = stakingDetails(BigInteger.valueOf(7_000_000L))
        coEvery { tronApi.getAccount(ADDRESS) } throws NetworkException(0, "no internet")

        val result = service.getRemoteDeFiBalance(ADDRESS, VAULT_ID)

        assertEquals(BigInteger.valueOf(7_000_000L), result.single().balances.single().amount)
        coVerify(exactly = 0) { stakingDetailsRepository.saveStakingDetails(any(), any()) }
        coVerify(exactly = 0) { stakingDetailsRepository.updateStakingDetails(any(), any()) }
    }

    @Test
    fun `cancellation propagates instead of degrading to the cached position`() = runTest {
        persisted = stakingDetails(BigInteger.valueOf(7_000_000L))
        coEvery { tronApi.getAccount(ADDRESS) } throws CancellationException("vault switched")

        assertFailsWith<CancellationException> { service.getRemoteDeFiBalance(ADDRESS, VAULT_ID) }
    }

    @Test
    fun `no cached total yields no position`() = runTest {
        assertEquals(emptyList(), service.getCacheDeFiBalance(ADDRESS, VAULT_ID))
    }

    private fun account(
        bandwidth: Long = 0L,
        energy: Long = 0L,
        unfreezing: List<Long> = emptyList(),
    ) =
        TronAccountJson(
            address = ADDRESS,
            frozenV2 =
                listOfNotNull(
                    TronFrozenV2Json(type = "BANDWIDTH", amount = bandwidth).takeIf {
                        bandwidth > 0L
                    },
                    TronFrozenV2Json(type = "ENERGY", amount = energy).takeIf { energy > 0L },
                ),
            unfrozenV2 =
                unfreezing.map { TronUnfrozenV2Json(type = "BANDWIDTH", unfreezeAmount = it) },
        )

    private fun stakingDetails(amount: BigInteger) =
        StakingDetails(
            id = Coins.Tron.TRX.generateId(),
            coin = Coins.Tron.TRX,
            stakeAmount = amount,
            apr = null,
            estimatedRewards = null,
            nextPayoutDate = null,
            rewards = null,
            rewardsCoin = null,
        )

    private companion object {
        const val ADDRESS = "TXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
        const val VAULT_ID = "vault-1"
    }
}
