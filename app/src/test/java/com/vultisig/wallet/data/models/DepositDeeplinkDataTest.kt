package com.vultisig.wallet.data.models

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class DepositDeeplinkDataTest {

    @Test
    fun `THORChain and MayaChain are accepted in any case`() {
        link(assetChain = "thorchain").chain shouldBe Chain.ThorChain
        link(assetChain = "MAYACHAIN").chain shouldBe Chain.MayaChain
    }

    @Test
    fun `any other chain or a missing one makes the link invalid`() {
        link(assetChain = "Ethereum").isValid.shouldBeFalse()
        link(assetChain = null).isValid.shouldBeFalse()
    }

    @Test
    fun `a missing or blank memo makes the link invalid`() {
        link(memo = null).isValid.shouldBeFalse()
        link(memo = "  ").isValid.shouldBeFalse()
        link(memo = "TCY+").isValid.shouldBeTrue()
    }

    @Test
    fun `only a plain dot-decimal amount is prefilled`() {
        link(amount = "0.01").prefillAmount shouldBe "0.01"
        link(amount = "5").prefillAmount shouldBe "5"
        link(amount = "1e5").prefillAmount.shouldBeNull()
        link(amount = "0,01").prefillAmount.shouldBeNull()
        link(amount = "-1").prefillAmount.shouldBeNull()
        link(amount = null).prefillAmount.shouldBeNull()
    }

    @Test
    fun `no ticker picks the chain's native coin`() {
        val coins = listOf(Coins.ThorChain.TCY, Coins.ThorChain.RUNE, Coins.MayaChain.CACAO)

        link(assetTicker = null).findCoin(coins) shouldBe Coins.ThorChain.RUNE
        link(assetChain = "MayaChain", assetTicker = "").findCoin(coins) shouldBe
            Coins.MayaChain.CACAO
    }

    @Test
    fun `a named ticker matches a coin on the link's chain only`() {
        val coins = listOf(Coins.ThorChain.RUNE, Coins.ThorChain.TCY)

        link(assetTicker = "tcy").findCoin(coins) shouldBe Coins.ThorChain.TCY
        link(assetChain = "MayaChain", assetTicker = "TCY").findCoin(coins).shouldBeNull()
    }

    @Test
    fun `a coin the vault does not hold is not found`() {
        link(assetTicker = "TCY").findCoin(listOf(Coins.ThorChain.RUNE)).shouldBeNull()
    }

    private fun link(
        assetChain: String? = "THORChain",
        assetTicker: String? = null,
        amount: String? = null,
        memo: String? = "TCY+",
    ) =
        DepositDeeplinkData(
            assetChain = assetChain,
            assetTicker = assetTicker,
            amount = amount,
            memo = memo,
        )
}
