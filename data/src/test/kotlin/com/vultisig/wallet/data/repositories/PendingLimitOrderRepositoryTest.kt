package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.PendingLimitOrderDao
import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PendingLimitOrderRepositoryTest {

    private val dao = mockk<PendingLimitOrderDao>(relaxed = true)
    private val repository = PendingLimitOrderRepositoryImpl(dao)

    private val eth =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0x742d35Cc6634C0532925a3b844Bc9e7595f12345",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    @Test
    fun `records the order deriving target price and expiry from the memo`() = runTest {
        val entity = slot<PendingLimitOrderEntity>()
        coEvery { dao.insert(capture(entity)) } returns Unit

        // 1 ETH -> BTC at target 0.04 BTC/ETH, 24h.
        repository.record(
            vaultId = "vault",
            inboundTxHash = "0xdeadbeef",
            sourceCoin = eth,
            sourceAmount = BigInteger.TEN.pow(18),
            memo = "=<:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh:4000000/14400/0:va:50",
        )

        with(entity.captured) {
            assertEquals("0xdeadbeef", inboundTxHash)
            assertEquals("vault", vaultId)
            assertEquals("ETH.ETH", sourceAsset)
            assertEquals(BigInteger.TEN.pow(18).toString(), sourceAmount)
            assertEquals("BTC.BTC", targetAsset)
            assertEquals("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", destAddr)
            // LIM 4_000_000 / source 1e8 = 0.04 BTC per ETH.
            assertEquals("0.04", targetPrice)
            assertEquals(14_400, expiryBlocks)
            assertEquals("pending", status)
        }
    }

    @Test
    fun `ignores a non-limit memo`() = runTest {
        repository.record(
            vaultId = "vault",
            inboundTxHash = "0xhash",
            sourceCoin = eth,
            sourceAmount = BigInteger.TEN.pow(18),
            memo = "=:BTC.BTC:dest:4000000",
        )
        coVerify(exactly = 0) { dao.insert(any()) }
    }
}
