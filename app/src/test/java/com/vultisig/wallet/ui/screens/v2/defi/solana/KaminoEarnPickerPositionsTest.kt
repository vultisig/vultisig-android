package com.vultisig.wallet.ui.screens.v2.defi.solana

import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import com.vultisig.wallet.data.blockchain.solana.kamino.coin
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.ui.screens.v2.defi.model.matching
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class KaminoEarnPickerPositionsTest {

    @Test
    fun `every curated vault is offered, in the registry's order`() {
        KAMINO_EARN_PICKER_POSITIONS.map { it.positionKey } shouldContainExactly
            KaminoVaultRegistry.ALLOW_LIST.map { it.address }
    }

    @Test
    fun `a cell is keyed by vault address and labelled with the vault's own name`() {
        for (vault in KaminoVaultRegistry.ALLOW_LIST) {
            val position = KAMINO_EARN_PICKER_POSITIONS.single { it.positionKey == vault.address }

            withClue(vault.fallbackName) {
                // The key is what reaches KaminoVaultSelectionRepository, so a ticker here would
                // alias both USDC vaults onto one selection.
                position.ticker shouldBe vault.fallbackName
                val expectedLogo = vault.coin?.let { getCoinLogo(it.logo) } ?: R.drawable.kamino
                position.logo shouldBe expectedLogo
            }
        }
    }

    @Test
    fun `the two USDC vaults are told apart by their addresses, not their token`() {
        val usdcVaults = KaminoVaultRegistry.ALLOW_LIST.filter { it.coin?.ticker == "USDC" }

        withClue("the fixture needs two vaults sharing a token to be worth asserting") {
            (usdcVaults.size >= 2) shouldBe true
        }

        val cells = KAMINO_EARN_PICKER_POSITIONS.filter { it.logo == getCoinLogo("usdc") }

        cells.map { it.positionKey }.distinct().size shouldBe cells.size
    }

    @Test
    fun `searching a curator finds the vault it runs`() {
        // "RWA USDC" carries nothing of RockawayX in its name, so this only passes because the
        // curator is a search term of its own — the behaviour iOS has.
        val matches = KAMINO_EARN_PICKER_POSITIONS.matching("rockaway")

        matches.map { it.ticker } shouldContainExactly listOf("RWA USDC")
    }

    @Test
    fun `search is case-insensitive and matches part of a name`() {
        KAMINO_EARN_PICKER_POSITIONS.matching("STEAK").map { it.ticker } shouldContainExactly
            listOf("Steakhouse USDC")
    }

    @Test
    fun `an empty query leaves every vault on offer`() {
        KAMINO_EARN_PICKER_POSITIONS.matching("") shouldBe KAMINO_EARN_PICKER_POSITIONS
    }

    @Test
    fun `a query nothing answers empties the section rather than falling back to everything`() {
        KAMINO_EARN_PICKER_POSITIONS.matching("thorchain").isEmpty() shouldBe true
    }
}
