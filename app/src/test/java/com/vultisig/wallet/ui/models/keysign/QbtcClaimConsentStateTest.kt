package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

internal class QbtcClaimConsentStateTest {

    private fun coin(chain: Chain, address: String) =
        Coin(
            chain = chain,
            ticker = chain.raw,
            logo = "",
            address = address,
            decimal = 8,
            hexPublicKey = "hex",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    /** Both accounts present → the consent gate exposes their addresses for review. */
    @Test
    fun `builds consent state with btc and qbtc addresses`() {
        val state =
            buildQbtcClaimConsentState(
                listOf(coin(Chain.Bitcoin, "btcAddr"), coin(Chain.Qbtc, "qbtcAddr"))
            )

        val consent = state.shouldBeInstanceOf<JoinKeysignState.QbtcClaimConsent>()
        consent.btcAddress shouldBe "btcAddr"
        consent.qbtcAddress shouldBe "qbtcAddr"
    }

    /** A QBTC claim never auto-signs — the gate is a consent state, not the signing state. */
    @Test
    fun `consent state is not the signing state`() {
        val state =
            buildQbtcClaimConsentState(
                listOf(coin(Chain.Bitcoin, "btcAddr"), coin(Chain.Qbtc, "qbtcAddr"))
            )

        (state is JoinKeysignState.QbtcClaim) shouldBe false
    }

    /** Missing the Bitcoin account fails before signing rather than mid-co-sign. */
    @Test
    fun `missing bitcoin account yields missing-account error`() {
        val state = buildQbtcClaimConsentState(listOf(coin(Chain.Qbtc, "qbtcAddr")))

        val error = state.shouldBeInstanceOf<JoinKeysignState.Error>()
        error.errorType shouldBe JoinKeysignError.MissingQbtcClaimAccount
    }

    /** Missing the QBTC account fails before signing rather than mid-co-sign. */
    @Test
    fun `missing qbtc account yields missing-account error`() {
        val state = buildQbtcClaimConsentState(listOf(coin(Chain.Bitcoin, "btcAddr")))

        val error = state.shouldBeInstanceOf<JoinKeysignState.Error>()
        error.errorType shouldBe JoinKeysignError.MissingQbtcClaimAccount
    }

    /** An empty vault also fails closed. */
    @Test
    fun `empty coins yields missing-account error`() {
        val state = buildQbtcClaimConsentState(emptyList())

        state.shouldBeInstanceOf<JoinKeysignState.Error>().errorType shouldBe
            JoinKeysignError.MissingQbtcClaimAccount
    }
}
