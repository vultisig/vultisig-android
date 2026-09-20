package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.NetworkException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * [AllowanceRepositoryImpl] must not turn a failed read into the same `null` it returns for a
 * genuinely not-applicable approval (native token / non-EVM chain) — see the contract documented on
 * [AllowanceRepository.getAllowance] (#5424).
 */
internal class AllowanceRepositoryTest {

    private val evmApi: EvmApi = mockk()
    private val evmApiFactory: EvmApiFactory = mockk {
        every { createEvmApi(any()) } returns evmApi
    }
    private val repository = AllowanceRepositoryImpl(evmApiFactory)

    @Test
    fun `getAllowance propagates an EvmApi failure instead of swallowing it into null`() = runTest {
        coEvery { evmApi.getAllowance(any(), any(), any()) } throws
            NetworkException(httpStatusCode = 0, message = "rpc error")

        assertFailsWith<NetworkException> {
            repository.getAllowance(Chain.Ethereum, CONTRACT, OWNER, SPENDER)
        }
    }

    @Test
    fun `getAllowance returns null for an empty contract address without calling the API`() =
        runTest {
            assertNull(repository.getAllowance(Chain.Ethereum, "", OWNER, SPENDER))
        }

    @Test
    fun `getAllowance returns null for a non-EVM chain without calling the API`() = runTest {
        assertNull(repository.getAllowance(Chain.Solana, CONTRACT, OWNER, SPENDER))
    }

    @Test
    fun `getAllowance returns the parsed value on success`() = runTest {
        coEvery { evmApi.getAllowance(any(), any(), any()) } returns BigInteger.TEN

        val allowance = repository.getAllowance(Chain.Ethereum, CONTRACT, OWNER, SPENDER)

        assertEquals(BigInteger.TEN, allowance)
    }

    // The zero-first reset only matters for a stale partial allowance, so the simulation is the
    // last resort, never the first call: no approval, no probe; zero allowance, no probe.

    @Test
    fun `an allowance that covers the amount needs no approval and no probe`() = runTest {
        coEvery { evmApi.getAllowance(any(), any(), any()) } returns AMOUNT

        val requirement = requirementFor(Chain.Ethereum)

        assertEquals(ApprovalRequirement.NotRequired, requirement)
        coVerify(exactly = 0) { evmApi.doesErc20ApproveRevert(any(), any(), any(), any()) }
    }

    @Test
    fun `a native token needs no approval without any call`() = runTest {
        assertEquals(
            ApprovalRequirement.NotRequired,
            repository.getApprovalRequirement(Chain.Ethereum, "", OWNER, SPENDER, AMOUNT),
        )
        coVerify(exactly = 0) { evmApi.getAllowance(any(), any(), any()) }
    }

    @Test
    fun `a zero allowance needs a single approve without a probe`() = runTest {
        coEvery { evmApi.getAllowance(any(), any(), any()) } returns BigInteger.ZERO

        val requirement = requirementFor(Chain.Ethereum)

        assertEquals(ApprovalRequirement.Approve, requirement)
        coVerify(exactly = 0) { evmApi.doesErc20ApproveRevert(any(), any(), any(), any()) }
    }

    @Test
    fun `a partial allowance whose approve reverts asks for the zero reset first`() = runTest {
        coEvery { evmApi.getAllowance(any(), any(), any()) } returns AMOUNT - BigInteger.ONE
        coEvery { evmApi.doesErc20ApproveRevert(CONTRACT, OWNER, SPENDER, AMOUNT) } returns true

        assertEquals(ApprovalRequirement.ResetThenApprove, requirementFor(Chain.Ethereum))
    }

    @Test
    fun `a partial allowance whose approve succeeds keeps the single approve`() = runTest {
        coEvery { evmApi.getAllowance(any(), any(), any()) } returns AMOUNT - BigInteger.ONE
        coEvery { evmApi.doesErc20ApproveRevert(CONTRACT, OWNER, SPENDER, AMOUNT) } returns false

        assertEquals(ApprovalRequirement.Approve, requirementFor(Chain.Ethereum))
    }

    // A probe the node could not run is no answer; guessing either way would sign the wrong legs.
    @Test
    fun `a failed probe propagates instead of deciding the legs on a guess`() = runTest {
        coEvery { evmApi.getAllowance(any(), any(), any()) } returns AMOUNT - BigInteger.ONE
        coEvery { evmApi.doesErc20ApproveRevert(any(), any(), any(), any()) } throws
            NetworkException(httpStatusCode = 0, message = "rate limited")

        assertFailsWith<NetworkException> { requirementFor(Chain.Ethereum) }
    }

    private suspend fun requirementFor(chain: Chain) =
        repository.getApprovalRequirement(chain, CONTRACT, OWNER, SPENDER, AMOUNT)

    private companion object {
        const val CONTRACT = "0x2222222222222222222222222222222222222222"
        const val OWNER = "0x1111111111111111111111111111111111111111"
        const val SPENDER = "0x3333333333333333333333333333333333333333"
        val AMOUNT: BigInteger = BigInteger.valueOf(5_000_000)
    }
}
