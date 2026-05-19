package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class KnownEvmContractsTest {

    @Test
    fun `looks up Uniswap V2 Router on Ethereum`() {
        assertEquals(
            "Uniswap V2 Router",
            KnownEvmContracts.lookup(Chain.Ethereum, "0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D"),
        )
    }

    @Test
    fun `looks up PancakeSwap V2 Router on BSC`() {
        assertEquals(
            "PancakeSwap V2 Router",
            KnownEvmContracts.lookup(Chain.BscChain, "0x10ED43C718714eb63d5aA57B78B54704E256024E"),
        )
    }

    @Test
    fun `address lookup is case-insensitive`() {
        assertEquals(
            "Uniswap V2 Router",
            KnownEvmContracts.lookup(Chain.Ethereum, "0x7A250D5630B4CF539739DF2C5DACB4C659F2488D"),
        )
    }

    @Test
    fun `address lookup tolerates missing 0x prefix`() {
        assertEquals(
            "Uniswap V2 Router",
            KnownEvmContracts.lookup(Chain.Ethereum, "7a250d5630b4cf539739df2c5dacb4c659f2488d"),
        )
    }

    @Test
    fun `unknown address on a known chain returns null`() {
        assertNull(
            KnownEvmContracts.lookup(Chain.Ethereum, "0xdeaddeaddeaddeaddeaddeaddeaddeaddeaddead")
        )
    }

    @Test
    fun `chain-agnostic Permit2 resolves on every EVM chain in the registry`() {
        val permit2 = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
        val chains = KnownEvmContracts.tableForTesting.keys
        for (chain in chains) {
            assertEquals(
                "Uniswap Permit2",
                KnownEvmContracts.lookup(chain, permit2),
                "Permit2 must resolve on $chain",
            )
        }
    }

    @Test
    fun `chain-agnostic Permit2 resolves on EVM chains that have no chain-specific entries`() {
        val permit2 = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
        val unmappedEvmChains =
            Chain.entries.filter {
                it.standard == TokenStandard.EVM && it !in KnownEvmContracts.tableForTesting.keys
            }
        assertTrue(
            unmappedEvmChains.isNotEmpty(),
            "test assumes some EVM chains have no chain-specific entries",
        )
        for (chain in unmappedEvmChains) {
            assertEquals(
                "Uniswap Permit2",
                KnownEvmContracts.lookup(chain, permit2),
                "Permit2 must resolve on $chain",
            )
        }
    }

    @Test
    fun `chain-agnostic CowSwap resolves`() {
        assertEquals(
            "CoW Protocol",
            KnownEvmContracts.lookup(Chain.Ethereum, "0x9008D19f58AAbD9eD0D60971565AA8510560ab41"),
        )
    }

    @Test
    fun `lookup on a non-EVM chain returns null even for an address that is in the table`() {
        assertNull(
            KnownEvmContracts.lookup(Chain.Solana, "0x7a250d5630b4cf539739df2c5dacb4c659f2488d")
        )
    }

    @Test
    fun `blank or whitespace-only addresses return null`() {
        assertNull(KnownEvmContracts.lookup(Chain.Ethereum, ""))
        assertNull(KnownEvmContracts.lookup(Chain.Ethereum, "   "))
    }

    @Test
    fun `every registered chain is EVM`() {
        for (chain in KnownEvmContracts.tableForTesting.keys) {
            assertEquals(
                TokenStandard.EVM,
                chain.standard,
                "$chain in registry must be an EVM chain",
            )
        }
    }

    @Test
    fun `every registered address is normalised lowercase with 0x prefix`() {
        val pattern = Regex("^0x[0-9a-f]{40}$")
        for ((chain, entries) in KnownEvmContracts.tableForTesting) {
            for (address in entries.keys) {
                assertTrue(
                    pattern.matches(address),
                    "$chain entry '$address' must be lowercase 0x + 40 hex",
                )
            }
        }
        for (address in KnownEvmContracts.chainAgnosticForTesting.keys) {
            assertTrue(
                pattern.matches(address),
                "chain-agnostic entry '$address' must be lowercase 0x + 40 hex",
            )
        }
    }

    @Test
    fun `chain-specific entries take precedence over chain-agnostic ones`() {
        // Sanity check: the chain-agnostic table doesn't accidentally overshadow a chain-specific
        // entry. Any address present in both should resolve to the chain-specific label.
        for ((chain, entries) in KnownEvmContracts.tableForTesting) {
            for ((address, label) in entries) {
                val resolved = KnownEvmContracts.lookup(chain, address)
                assertNotNull(resolved)
                if (KnownEvmContracts.chainAgnosticForTesting.containsKey(address)) {
                    assertEquals(
                        label,
                        resolved,
                        "$chain entry '$address' must override chain-agnostic",
                    )
                }
            }
        }
    }
}
