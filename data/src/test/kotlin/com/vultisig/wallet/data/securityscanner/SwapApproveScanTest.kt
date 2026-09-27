package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.chains.helpers.EthereumFunction
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidRpcClientContract
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidScannerService
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidTransactionScanResponseJson
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidTransactionScanResponseJson.BlockaidValidationJson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** ERC-20 swaps scan `[approve legs…, swap]` so the swap isn't simulated without its allowance. */
class SwapApproveScanTest {

    private val factory = SecurityScannerTransactionFactory(mockk<SolanaApi>(), mockk<SuiApi>())

    // wallet-core's JNI isn't loadable in unit tests, so stub the approve calldata encoder.
    @BeforeEach
    fun stubEncoder() {
        mockkObject(EthereumFunction)
        every { EthereumFunction.approvalErc20Encoder(any(), any()) } answers
            {
                "0xapprove-${firstArg<String>()}-${secondArg<BigInteger>()}"
            }
    }

    @AfterEach
    fun unstubEncoder() {
        unmockkObject(EthereumFunction)
    }

    private val usdc =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "usdc",
            address = VAULT,
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "usd-coin",
            contractAddress = USDC,
            isNativeToken = false,
        )

    private fun swap(approval: Boolean, reset: Boolean = false, srcToken: Coin = usdc) =
        mockk<SwapTransaction.RegularSwapTransaction> {
            every { this@mockk.srcToken } returns srcToken
            every { srcTokenValue } returns TokenValue(BigInteger.TEN, srcToken)
            every { isApprovalRequired } returns approval
            every { resetAllowanceFirst } returns reset
            every { approveSpender } returns SPENDER
            every { payload } returns
                mockk<SwapPayload.EVM> {
                    every { data.quote.tx.from } returns VAULT
                    every { data.quote.tx.to } returns ROUTER
                    every { data.quote.tx.value } returns "0"
                    every { data.quote.tx.data } returns "0xswap"
                }
        }

    private fun approve(amount: BigInteger) =
        SecurityScannerTransaction(
            chain = Chain.Ethereum,
            type = SecurityTransactionType.APPROVAL,
            from = VAULT,
            to = USDC,
            data = EthereumFunction.approvalErc20Encoder(SPENDER, amount),
        )

    @Test
    fun `token source scans the swap after its approve`() = runTest {
        val scan = factory.createSecurityScannerTransaction(swap(approval = true))

        assertEquals(ROUTER, scan.to)
        assertEquals("0xswap", scan.data)
        assertEquals(listOf(approve(BigInteger.TEN)), scan.precedingTransactions)
    }

    @Test
    fun `reset leg comes first when the allowance must be zeroed`() = runTest {
        val scan = factory.createSecurityScannerTransaction(swap(approval = true, reset = true))

        assertEquals(
            listOf(approve(BigInteger.ZERO), approve(BigInteger.TEN)),
            scan.precedingTransactions,
        )
    }

    @Test
    fun `no approve leaves the swap scanned alone`() = runTest {
        val scan = factory.createSecurityScannerTransaction(swap(approval = false))

        assertEquals("0xswap", scan.data)
        assertTrue(scan.precedingTransactions.isEmpty())
    }

    private fun response(resultType: String) =
        BlockaidTransactionScanResponseJson(
            requestId = null,
            accountAddress = null,
            status = "Success",
            validation = BlockaidValidationJson("Success", resultType, resultType, null, null, emptyList(), null),
            result = null,
            error = null,
        )

    @Test
    fun `bulk scan sends legs in order and returns the least secure verdict`() = runTest {
        val client = mockk<BlockaidRpcClientContract>()
        coEvery { client.scanEVMTransactionBulk(any(), any()) } returns
            listOf(response("Benign"), response("Malicious"))
        val scan = factory.createSecurityScannerTransaction(swap(approval = true))

        val result = BlockaidScannerService(client).scanTransaction(scan)

        assertEquals(SecurityRiskLevel.CRITICAL, result.riskLevel)
        coVerify {
            client.scanEVMTransactionBulk(
                Chain.Ethereum,
                match { it.map { tx -> tx.to } == listOf(USDC, ROUTER) },
            )
        }
    }

    @Test
    fun `entry count mismatch fails the scan`() = runTest {
        val client = mockk<BlockaidRpcClientContract>()
        coEvery { client.scanEVMTransactionBulk(any(), any()) } returns listOf(response("Benign"))
        val scan = factory.createSecurityScannerTransaction(swap(approval = true))

        val failure = runCatching { BlockaidScannerService(client).scanTransaction(scan) }

        assertTrue(failure.exceptionOrNull() is SecurityScannerException)
    }

    private companion object {
        const val VAULT = "0x1111111111111111111111111111111111111111"
        const val ROUTER = "0x2222222222222222222222222222222222222222"
        const val SPENDER = "0x3333333333333333333333333333333333333333"
        const val USDC = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
    }
}
