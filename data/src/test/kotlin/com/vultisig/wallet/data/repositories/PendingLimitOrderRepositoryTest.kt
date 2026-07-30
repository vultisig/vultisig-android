package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.PendingLimitOrderDao
import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
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

    private val btc =
        Coin(
            chain = Chain.Bitcoin,
            ticker = "BTC",
            logo = "",
            address = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private val usdc =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "",
            address = "0x742d35Cc6634C0532925a3b844Bc9e7595f12345",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            isNativeToken = false,
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
            sourceAddress = eth.address,
            targetCoin = btc,
            memo = "=<:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh:4000000/14400/0:va:50",
        )

        with(entity.captured) {
            inboundTxHash shouldBe "0xdeadbeef"
            vaultId shouldBe "vault"
            sourceAsset shouldBe "ETH.ETH"
            sourceAmount shouldBe BigInteger.TEN.pow(18).toString()
            targetAsset shouldBe "BTC.BTC"
            destAddr shouldBe "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"
            // LIM 4_000_000 / source 1e8 = 0.04 BTC per ETH.
            targetPrice shouldBe "0.04"
            expiryBlocks shouldBe 14_400
            status shouldBe "pending"
            // Captured at signing because none of it is recoverable afterwards: the cancel memo's
            // ratio bucket is built from exactly this pair of integers.
            sourceChain shouldBe Chain.Ethereum.raw
            sourceDecimals shouldBe 18
            sourceAddress shouldBe eth.address
            sourceAmount1e8 shouldBe "100000000"
            tradeTarget shouldBe "4000000"
            sourceAssetFull shouldBe "ETH.ETH"
            targetAssetFull shouldBe "BTC.BTC"
            cancelBroadcastHash shouldBe null
            cancelConfirmed shouldBe false
        }
    }

    @Test
    fun `records an EVM token's full contract address for a future cancel`() = runTest {
        val entity = slot<PendingLimitOrderEntity>()
        coEvery { dao.insert(capture(entity)) } returns Unit

        repository.record(
            vaultId = "vault",
            inboundTxHash = "0xdeadbeef",
            sourceCoin = usdc,
            sourceAmount = BigInteger.valueOf(1_000_000),
            sourceAddress = usdc.address,
            targetCoin = btc,
            memo = "=<:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh:4000000/14400/0:va:50",
        )

        with(entity.captured) {
            // The placement spelling abbreviates the contract; a cancel is not fuzzy-matched and
            // must carry it in full, and the abbreviation cannot be expanded back later.
            sourceAsset shouldBe "ETH.USDC-06EB48"
            sourceAssetFull shouldBe "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"
        }
    }

    @Test
    fun `records no cancel inputs when the bought coin is unknown`() = runTest {
        val entity = slot<PendingLimitOrderEntity>()
        coEvery { dao.insert(capture(entity)) } returns Unit

        repository.record(
            vaultId = "vault",
            inboundTxHash = "0xdeadbeef",
            sourceCoin = eth,
            sourceAmount = BigInteger.TEN.pow(18),
            sourceAddress = eth.address,
            targetCoin = null,
            memo = "=<:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh:4000000/14400/0:va:50",
        )

        // Null, not guessed from the memo's abbreviated spelling: cancelling stays blocked until
        // the
        // queue reports the asset itself.
        entity.captured.targetAssetFull shouldBe null
    }

    @Test
    fun `ignores a non-limit memo`() = runTest {
        repository.record(
            vaultId = "vault",
            inboundTxHash = "0xhash",
            sourceCoin = eth,
            sourceAmount = BigInteger.TEN.pow(18),
            sourceAddress = eth.address,
            targetCoin = btc,
            memo = "=:BTC.BTC:dest:4000000",
        )
        coVerify(exactly = 0) { dao.insert(any()) }
    }
}
