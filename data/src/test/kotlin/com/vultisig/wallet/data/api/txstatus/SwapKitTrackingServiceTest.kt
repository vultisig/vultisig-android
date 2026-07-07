package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.TonJettonTransfer
import com.vultisig.wallet.data.api.models.quotes.SwapKitTrackRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitTrackResponseJson
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Pins the SwapKit settlement gate:
 * - [SwapKitTrackingService.canTrack] follows the chain catalogue (EVM/Solana yes, Polkadot no).
 * - `/track` is queried with the broadcast hash + the source chain's `/track` chainId.
 * - the response maps through [SwapKitTrackingStatusMapper].
 * - a transient `/track` failure (or an untrackable chain) returns Pending so polling keeps running
 *   rather than flipping the row terminal.
 */
class SwapKitTrackingServiceTest {

    private val api: SwapKitApi = mockk()
    private val tonApi: TonApi = mockk()
    private val transactionHistoryRepository: TransactionHistoryRepository = mockk()

    private fun service(): SwapKitTrackingService =
        SwapKitTrackingServiceImpl(api, tonApi, transactionHistoryRepository)

    private val userAddress = "UQuser000000000000000000000000000000000000000000user"
    private val escrow = "EQAbWJ3Y1HgIIvcMq1prG1anlDC0T3cZlAU7luPT6LmTpvbJ"
    private val srcMaster = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
    private val dstMaster = "EQgram0000000000000000000000000000000000000000dst0"

    /** A `/track` response for the degenerate deposit-leg-only Omniston case (from == to). */
    private fun depositLegResponse() =
        SwapKitTrackResponseJson(
            chainId = "ton",
            type = "token_transfer",
            status = "completed",
            trackingStatus = "completed",
            fromAsset = "TON.USDT-$srcMaster",
            toAsset = "TON.USDT-$srcMaster",
            toAddress = escrow,
            finalisedAt = 1_700_000_000.0,
        )

    private fun stubTonSwapRow(
        toContract: String,
        toIsNative: Boolean = false,
        toDecimals: Int = 9,
    ) {
        val payload =
            SwapTransactionHistoryData(
                fromToken = "USDT",
                fromAmount = "40.886112",
                fromChain = "ton",
                fromTokenLogo = "",
                toToken = "GRAM",
                toAmount = "1000.0",
                toChain = "ton",
                toTokenLogo = "",
                provider = "SwapKit",
                fiatValue = "",
                toContractAddress = toContract,
                toIsNative = toIsNative,
                fromAddress = userAddress,
                toAmountDecimal = "1000.0",
                toDecimals = toDecimals,
            )
        coEvery { transactionHistoryRepository.getTransaction("Ton", "0xton") } returns
            entityWith(payload)
    }

    /** Expected out 1000.0 at 9 decimals → jetton/native fill threshold of 500 * 1e9. */
    private fun aboveThreshold() = BigInteger.valueOf(600).multiply(BigInteger.TEN.pow(9))

    private fun entityWith(payload: TransactionHistoryData) =
        TransactionHistoryEntity(
            id = "Ton:0xton",
            vaultId = "v",
            type = TransactionType.SWAP,
            status = TransactionStatus.PENDING,
            chain = "Ton",
            timestamp = 1_700_000_000_000L,
            txHash = "0xton",
            explorerUrl = "",
            payload = payload,
            confirmedAt = null,
            failureReason = null,
            lastCheckedAt = null,
        )

    @Test
    fun `canTrack reflects the chain catalogue`() {
        with(service()) {
            canTrack(Chain.Ethereum) shouldBe true
            canTrack(Chain.Solana) shouldBe true
            canTrack(Chain.Polkadot) shouldBe false
        }
    }

    @Test
    fun `checkSettlementStatus queries track with broadcast hash and source chainId`() = runTest {
        val request = slot<SwapKitTrackRequest>()
        coEvery { api.track(capture(request)) } returns
            SwapKitTrackResponseJson(trackingStatus = "completed")

        val result = service().checkSettlementStatus("0xbroadcast", Chain.Ethereum)

        result shouldBe TransactionResult.Confirmed
        request.captured.hash shouldBe "0xbroadcast"
        request.captured.chainId shouldBe "1"
    }

    @Test
    fun `checkSettlementStatus maps an in-flight destination leg to Pending`() = runTest {
        coEvery { api.track(any()) } returns
            SwapKitTrackResponseJson(status = "completed", trackingStatus = "outbound")

        service().checkSettlementStatus("0xbroadcast", Chain.Ethereum) shouldBe
            TransactionResult.Pending
    }

    @Test
    fun `transient track failure returns Pending to keep polling`() = runTest {
        coEvery { api.track(any()) } throws RuntimeException("boom")

        service().checkSettlementStatus("0xbroadcast", Chain.Ethereum) shouldBe
            TransactionResult.Pending
    }

    @Test
    fun `untrackable chain returns Pending without hitting the API`() = runTest {
        service().checkSettlementStatus("0xbroadcast", Chain.Polkadot) shouldBe
            TransactionResult.Pending
    }

    @Test
    fun `refund maps to Refunded`() = runTest {
        coEvery { api.track(any()) } returns SwapKitTrackResponseJson(trackingStatus = "refunded")

        service()
            .checkSettlementStatus("0xbroadcast", Chain.Ethereum)
            .shouldBeInstanceOf<TransactionResult.Refunded>()
    }

    @Test
    fun `TON deposit-leg completed with an escrow refund resolves to Refunded`() = runTest {
        coEvery { api.track(any()) } returns depositLegResponse()
        stubTonSwapRow(toContract = dstMaster)
        coEvery { tonApi.getIncomingJettonTransfers(userAddress, any()) } returns
            listOf(TonJettonTransfer(senderOwner = escrow, jettonMaster = srcMaster))

        service()
            .checkSettlementStatus("0xton", Chain.Ton)
            .shouldBeInstanceOf<TransactionResult.Refunded>()
    }

    @Test
    fun `TON deposit-leg completed with a jetton fill above the threshold resolves to Confirmed`() =
        runTest {
            coEvery { api.track(any()) } returns depositLegResponse()
            stubTonSwapRow(toContract = dstMaster)
            coEvery { tonApi.getIncomingJettonTransfers(userAddress, any()) } returns
                listOf(
                    TonJettonTransfer(
                        senderOwner = escrow,
                        jettonMaster = dstMaster,
                        amount = aboveThreshold(),
                    )
                )

            service().checkSettlementStatus("0xton", Chain.Ton) shouldBe TransactionResult.Confirmed
        }

    @Test
    fun `TON jetton fill below the threshold stays Pending`() = runTest {
        coEvery { api.track(any()) } returns depositLegResponse()
        stubTonSwapRow(toContract = dstMaster)
        // An unrelated dust transfer of the destination jetton must not flip the row to Confirmed.
        coEvery { tonApi.getIncomingJettonTransfers(userAddress, any()) } returns
            listOf(
                TonJettonTransfer(
                    senderOwner = escrow,
                    jettonMaster = dstMaster,
                    amount = BigInteger.valueOf(1_000L),
                )
            )

        service().checkSettlementStatus("0xton", Chain.Ton) shouldBe TransactionResult.Pending
    }

    @Test
    fun `TON deposit-leg completed with no settlement stays Pending`() = runTest {
        coEvery { api.track(any()) } returns depositLegResponse()
        stubTonSwapRow(toContract = dstMaster)
        coEvery { tonApi.getIncomingJettonTransfers(userAddress, any()) } returns emptyList()

        service().checkSettlementStatus("0xton", Chain.Ton) shouldBe TransactionResult.Pending
    }

    @Test
    fun `TON native destination fill above the dust threshold resolves to Confirmed`() = runTest {
        coEvery { api.track(any()) } returns depositLegResponse()
        stubTonSwapRow(toContract = "", toIsNative = true)
        coEvery { tonApi.getIncomingJettonTransfers(userAddress, any()) } returns emptyList()
        // Expected out 1000.0 (9 decimals) → threshold 500 * 1e9; 600 * 1e9 from the escrow clears.
        coEvery { tonApi.getMaxIncomingTonValue(userAddress, any(), any()) } returns
            aboveThreshold()

        service().checkSettlementStatus("0xton", Chain.Ton) shouldBe TransactionResult.Confirmed
    }

    @Test
    fun `TON native gas-excess dust stays Pending`() = runTest {
        coEvery { api.track(any()) } returns depositLegResponse()
        stubTonSwapRow(toContract = "", toIsNative = true)
        coEvery { tonApi.getIncomingJettonTransfers(userAddress, any()) } returns emptyList()
        coEvery { tonApi.getMaxIncomingTonValue(userAddress, any(), any()) } returns
            BigInteger.valueOf(100_000_000L)

        service().checkSettlementStatus("0xton", Chain.Ton) shouldBe TransactionResult.Pending
    }

    @Test
    fun `TON degenerate response without a persisted row stays Pending`() = runTest {
        coEvery { api.track(any()) } returns depositLegResponse()
        coEvery { transactionHistoryRepository.getTransaction("Ton", "0xton") } returns null

        service().checkSettlementStatus("0xton", Chain.Ton) shouldBe TransactionResult.Pending
    }
}
