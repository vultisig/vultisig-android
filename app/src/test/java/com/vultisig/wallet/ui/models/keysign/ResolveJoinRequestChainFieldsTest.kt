package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.CustomMessagePayload

class ResolveJoinRequestChainFieldsTest {
    @Test
    fun `custom message with no chain set sends Ethereum as the raw chain`() {
        resolveJoinRequestChainRaw(
            keysignPayload = null,
            customMessagePayload = CustomMessagePayload(),
        ) shouldBe Chain.Ethereum.raw
    }

    @Test
    fun `custom message chain is threaded into the raw chain sent to the server`() {
        resolveJoinRequestChainRaw(
            keysignPayload = null,
            customMessagePayload = CustomMessagePayload(chain = Chain.Solana.raw),
        ) shouldBe Chain.Solana.raw
    }

    @Test
    fun `custom message with an unparseable chain sends an empty raw chain`() {
        resolveJoinRequestChainRaw(
            keysignPayload = null,
            customMessagePayload = CustomMessagePayload(chain = "NotAChain"),
        ) shouldBe ""
    }
}
