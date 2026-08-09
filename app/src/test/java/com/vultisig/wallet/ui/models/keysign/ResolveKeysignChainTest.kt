package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import kotlin.test.Test
import kotlin.test.assertEquals
import vultisig.keysign.v1.CustomMessagePayload

class ResolveKeysignChainTest {
    @Test
    fun `custom message resolves its chain for KeyImport share selection`() {
        val customMessage = CustomMessagePayload(chain = Chain.Ethereum.raw)

        assertEquals(
            Chain.Ethereum,
            resolveKeysignChain(keysignPayload = null, customMessagePayload = customMessage),
        )
    }

    @Test
    fun `custom message with no chain set defaults to Ethereum`() {
        assertEquals(
            Chain.Ethereum,
            resolveKeysignChain(
                keysignPayload = null,
                customMessagePayload = CustomMessagePayload(),
            ),
        )
    }

    @Test
    fun `custom message chain still takes precedence over the default`() {
        val customMessage = CustomMessagePayload(chain = Chain.Solana.raw)

        assertEquals(
            Chain.Solana,
            resolveKeysignChain(keysignPayload = null, customMessagePayload = customMessage),
        )
    }

    @Test
    fun `custom message with an unparseable chain resolves to null instead of Ethereum`() {
        val customMessage = CustomMessagePayload(chain = "NotAChain")

        assertEquals(
            null,
            resolveKeysignChain(keysignPayload = null, customMessagePayload = customMessage),
        )
    }
}
