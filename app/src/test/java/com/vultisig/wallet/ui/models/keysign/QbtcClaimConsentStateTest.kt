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

    /** The consent gate exposes the resolved accounts' addresses for review. */
    @Test
    fun `builds consent state with btc and qbtc addresses`() {
        val state =
            buildQbtcClaimConsentState(
                QbtcClaimCoins(
                    btc = coin(Chain.Bitcoin, "btcAddr"),
                    qbtc = coin(Chain.Qbtc, "qbtcAddr"),
                )
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
                QbtcClaimCoins(
                    btc = coin(Chain.Bitcoin, "btcAddr"),
                    qbtc = coin(Chain.Qbtc, "qbtcAddr"),
                )
            )

        (state is JoinKeysignState.QbtcClaim) shouldBe false
    }
}
