package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CustomRpcSupportedChains
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards [CustomRpcDefaultEndpoint] against drift from the supported-chain set and the factories.
 */
internal class CustomRpcDefaultEndpointTest {

    /** Every chain shown in the Custom RPC picker must have a resolvable default endpoint. */
    @Test
    fun `every supported chain has a default endpoint`() {
        CustomRpcSupportedChains.all.forEach { chain ->
            assertNotNull(
                CustomRpcDefaultEndpoint.string(chain),
                "Missing default endpoint for supported chain $chain",
            )
        }
    }

    /** Defaults are HTTPS endpoints. */
    @Test
    fun `every supported chain default is an https url`() {
        CustomRpcSupportedChains.all.forEach { chain ->
            val url = CustomRpcDefaultEndpoint.string(chain).orEmpty()
            assertTrue(url.startsWith("https://"), "Default for $chain is not https: $url")
        }
    }

    /** A chain with no configurable default resolves to null (not an exception) via string(). */
    @Test
    fun `unsupported chain has no default endpoint`() {
        assertNull(CustomRpcDefaultEndpoint.string(Chain.Bitcoin))
    }

    /** Spot-check the EVM and Cosmos accessors return the documented endpoints. */
    @Test
    fun `evm and cosmos accessors return expected defaults`() {
        assertEquals(
            "https://api.vultisig.com/eth/",
            CustomRpcDefaultEndpoint.evmUrl(Chain.Ethereum),
        )
        assertEquals(
            "https://cosmos-rest.publicnode.com",
            CustomRpcDefaultEndpoint.cosmosUrl(Chain.GaiaChain),
        )
    }
}
