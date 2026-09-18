package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Only EVM quotes its swap network fee as a ceiling (maxFeePerGas × gas limit); every other chain
 * charges exactly what it quotes, so only EVM rows are labelled "Max. Network Fee".
 */
internal class SwapNetworkFeeLabelTest {

    @Test
    fun `every EVM chain has a fee ceiling`() {
        Chain.entries
            .filter { it.standard == TokenStandard.EVM }
            .forEach { chain -> chain.hasSwapNetworkFeeCeiling shouldBe true }
    }

    @Test
    fun `no other chain has a fee ceiling`() {
        Chain.entries
            .filter { it.standard != TokenStandard.EVM }
            .forEach { chain -> chain.hasSwapNetworkFeeCeiling shouldBe false }
    }

    @Test
    fun `pins the exact-fee chains the label must stay plain on`() {
        listOf(Chain.ThorChain, Chain.Bitcoin, Chain.Solana, Chain.GaiaChain).forEach {
            it.hasSwapNetworkFeeCeiling shouldBe false
        }
        listOf(Chain.Ethereum, Chain.Arbitrum, Chain.Base).forEach {
            it.hasSwapNetworkFeeCeiling shouldBe true
        }
    }
}
