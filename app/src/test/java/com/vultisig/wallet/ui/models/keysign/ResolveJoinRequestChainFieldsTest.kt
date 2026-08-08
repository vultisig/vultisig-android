package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import kotlin.test.Test
import kotlin.test.assertEquals
import vultisig.keysign.v1.CustomMessagePayload

class ResolveJoinRequestChainFieldsTest {
    @Test
    fun `custom message with no chain set sends Ethereum as the raw chain`() {
        assertEquals(
            Chain.Ethereum.raw,
            resolveJoinRequestChainRaw(
                keysignPayload = null,
                customMessagePayload = CustomMessagePayload(),
            ),
        )
    }

    @Test
    fun `custom message chain is threaded into the raw chain sent to the server`() {
        assertEquals(
            Chain.Solana.raw,
            resolveJoinRequestChainRaw(
                keysignPayload = null,
                customMessagePayload = CustomMessagePayload(chain = Chain.Solana.raw),
            ),
        )
    }

    @Test
    fun `custom message with an unparseable chain sends an empty raw chain`() {
        assertEquals(
            "",
            resolveJoinRequestChainRaw(
                keysignPayload = null,
                customMessagePayload = CustomMessagePayload(chain = "NotAChain"),
            ),
        )
    }
}
