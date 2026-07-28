package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.NetworkException
import io.mockk.coEvery
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

    private companion object {
        const val CONTRACT = "0x2222222222222222222222222222222222222222"
        const val OWNER = "0x1111111111111111111111111111111111111111"
        const val SPENDER = "0x3333333333333333333333333333333333333333"
    }
}
