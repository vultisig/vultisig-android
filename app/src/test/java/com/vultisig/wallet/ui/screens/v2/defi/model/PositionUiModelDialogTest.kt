package com.vultisig.wallet.ui.screens.v2.defi.model

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class PositionUiModelDialogTest {

    private val rune = PositionUiModelDialog(logo = 0, ticker = "RUNE")
    private val tcy = PositionUiModelDialog(logo = 0, ticker = "TCY")
    private val lp = PositionUiModelDialog(logo = 0, ticker = "RUNE/BTC")

    private val coinPositions = listOf(rune, tcy, lp)

    @Test
    fun `a coin position searches by its ticker alone`() {
        rune.searchTerms shouldContainExactly listOf("RUNE")
    }

    @Test
    fun `matching a ticker keeps only the positions carrying it`() {
        coinPositions.matching("rune") shouldContainExactly listOf(rune, lp)
    }

    @Test
    fun `an empty query is not a filter`() {
        coinPositions.matching("") shouldContainExactly coinPositions
    }

    @Test
    fun `extra search terms widen the match without replacing the ticker`() {
        val vault =
            PositionUiModelDialog(
                logo = 0,
                ticker = "RWA USDC",
                positionKey = "DWSXb18xZApz29vnQpgR2m6MynCT7PznaXt7Ut7M7KaP",
                searchTerms = listOf("RWA USDC", "RockawayX"),
            )

        listOf(vault).matching("rockawayx") shouldContainExactly listOf(vault)
        listOf(vault).matching("rwa") shouldContainExactly listOf(vault)
    }

    @Test
    fun `the key defaults to the ticker so existing coin sections are unchanged`() {
        rune.positionKey shouldBe "RUNE"
    }
}
