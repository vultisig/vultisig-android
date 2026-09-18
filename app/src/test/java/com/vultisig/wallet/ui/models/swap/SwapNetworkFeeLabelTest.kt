package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.isOpStackL2
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Only EVM quotes its swap network fee as a ceiling (maxFeePerGas × gas limit); every other chain
 * charges exactly what it quotes, so only EVM rows are labelled "Max. Network Fee". Within EVM the
 * OP-stack L2s stay plain: their row is priced at the flat default limit plus an oracle-quoted L1
 * data fee, neither of which bounds what the signer ends up paying.
 */
internal class SwapNetworkFeeLabelTest {

    @Test
    fun `every EVM chain outside the OP stack has a fee ceiling`() {
        Chain.entries
            .filter { it.standard == TokenStandard.EVM && !it.isOpStackL2 }
            .forEach { chain -> chain.hasSwapNetworkFeeCeiling shouldBe true }
    }

    @Test
    fun `no OP-stack L2 has a fee ceiling`() {
        Chain.entries
            .filter { it.isOpStackL2 }
            .forEach { chain -> chain.hasSwapNetworkFeeCeiling shouldBe false }
    }

    @Test
    fun `no other chain has a fee ceiling`() {
        Chain.entries
            .filter { it.standard != TokenStandard.EVM }
            .forEach { chain -> chain.hasSwapNetworkFeeCeiling shouldBe false }
    }

    @Test
    fun `pins the chains the label must stay plain on`() {
        listOf(Chain.ThorChain, Chain.Bitcoin, Chain.Solana, Chain.GaiaChain).forEach {
            it.hasSwapNetworkFeeCeiling shouldBe false
        }
        listOf(Chain.Base, Chain.Optimism, Chain.Blast).forEach {
            it.hasSwapNetworkFeeCeiling shouldBe false
        }
        listOf(Chain.Ethereum, Chain.Arbitrum, Chain.BscChain).forEach {
            it.hasSwapNetworkFeeCeiling shouldBe true
        }
    }
}
