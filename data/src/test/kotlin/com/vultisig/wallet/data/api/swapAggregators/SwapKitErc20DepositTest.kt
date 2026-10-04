package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.blockchain.ethereum.decodeErc20TransferCallData
import com.vultisig.wallet.data.blockchain.ethereum.erc20TransferCallData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.utils.Numeric
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * A SwapKit NEAR-Intents ERC-20 deposit calls the sold token with `transfer(recipient, amount)`.
 * The fixture is the real `/v3/swap` reply for 20 USDT -> SOL (vultisig-ios
 * `v3-real-usdt-sol-swap.json`).
 */
internal class SwapKitErc20DepositTest {

    @Test
    fun `returns the calldata recipient of a real USDT deposit`() {
        swapKitErc20DepositRecipient(depositTx(), usdt, SOLD) shouldBe DEPOSIT_ADDRESS.lowercase()
    }

    @Test
    fun `a router call that is not a transfer is not a deposit`() {
        val routerTx = depositTx().copy(to = ROUTER, data = "0x12aa3caf" + "00".repeat(64))

        routerTx.isErc20DepositTransfer(usdt).shouldBeFalse()
        swapKitErc20DepositRecipient(routerTx, usdt, SOLD).shouldBeNull()
    }

    @Test
    fun `matches the sold token case-insensitively and never for a native source`() {
        depositTx().copy(to = USDT.lowercase()).isErc20DepositTransfer(usdt).shouldBeTrue()
        depositTx().copy(to = "").isErc20DepositTransfer(eth).shouldBeFalse()
    }

    @Test
    fun `refuses a transfer call on a token other than the sold one`() {
        val otherToken = depositTx().copy(to = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")

        shouldThrow<IllegalArgumentException> {
                swapKitErc20DepositRecipient(otherToken, usdt, SOLD)
            }
            .message shouldContain "not the sold token"
    }

    @Test
    fun `refuses a transfer call from a native source`() {
        shouldThrow<IllegalArgumentException> {
            swapKitErc20DepositRecipient(depositTx(), eth, SOLD)
        }
    }

    @Test
    fun `refuses a deposit that attaches native value`() {
        shouldThrow<IllegalArgumentException> {
                swapKitErc20DepositRecipient(depositTx().copy(value = "1"), usdt, SOLD)
            }
            .message shouldContain "native value"
    }

    @Test
    fun `refuses calldata with trailing bytes`() {
        val trailing = depositTx().copy(data = DEPOSIT_DATA + "00")

        shouldThrow<IllegalArgumentException> { swapKitErc20DepositRecipient(trailing, usdt, SOLD) }
            .message shouldContain "not exactly"
    }

    @Test
    fun `refuses an address word with dirty high bytes`() {
        val dirty = depositTx().copy(data = DEPOSIT_DATA.replaceRange(10, 12, "ff"))

        shouldThrow<IllegalArgumentException> { swapKitErc20DepositRecipient(dirty, usdt, SOLD) }
    }

    @Test
    fun `refuses a deposit for another amount than the one sold`() {
        shouldThrow<IllegalArgumentException> {
                swapKitErc20DepositRecipient(depositTx(), usdt, SOLD - BigInteger.ONE)
            }
            .message shouldContain "not the sold amount"
    }

    @Test
    fun `decodes exactly what the transfer encoder builds`() {
        val encoded = Numeric.toHexString(erc20TransferCallData(DEPOSIT_ADDRESS, SOLD))

        val decoded = decodeErc20TransferCallData(encoded)

        decoded?.recipient shouldBe DEPOSIT_ADDRESS.lowercase()
        decoded?.amount shouldBe SOLD
    }

    private fun depositTx() =
        OneInchSwapTxJson(
            from = "0x28C6c06298d514Db089934071355E5743bf21d60",
            to = USDT,
            gas = 76_837,
            data = DEPOSIT_DATA,
            value = "0",
            gasPrice = "86448690",
        )

    private val usdt =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDT",
            logo = "",
            address = "0x28C6c06298d514Db089934071355E5743bf21d60",
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "tether",
            contractAddress = USDT,
            isNativeToken = false,
        )

    private val eth =
        usdt.copy(ticker = "ETH", decimal = 18, contractAddress = "", isNativeToken = true)

    private companion object {
        const val USDT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val ROUTER = "0x9025b8ff35ca44f7018c3a37fe0f69e63dbb0743"
        const val DEPOSIT_ADDRESS = "0xCB2aC797EFf13Ee982453F5722B74aB5c56741Af"
        const val DEPOSIT_DATA =
            "0xa9059cbb000000000000000000000000cb2ac797eff13ee982453f5722b74ab5c56741af" +
                "0000000000000000000000000000000000000000000000000000000001312d00"
        val SOLD: BigInteger = BigInteger.valueOf(20_000_000)
    }
}
