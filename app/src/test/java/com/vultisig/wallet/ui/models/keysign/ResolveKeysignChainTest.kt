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
}
