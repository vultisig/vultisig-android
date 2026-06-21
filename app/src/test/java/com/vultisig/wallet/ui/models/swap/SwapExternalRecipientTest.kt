package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Chain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Pins the external-recipient resolution surfaced on the swap verify screen (#4972). The recipient
 * is read from the signed THORChain/MayaChain memo and only shown when it differs from the vault's
 * own destination-chain address — a regression here would either hide an external destination from
 * a co-signer (the security gap the issue fixes) or spam the warning row on every normal swap.
 */
internal class SwapExternalRecipientTest {

    private val evmRecipient = "0x9876543210FeDcBa9876543210FeDcBa98765432"
    private val ownEvmChecksum = "0xAbCdEf1234567890AbCdEf1234567890AbCdEf12"

    @Test
    fun `returns external EVM recipient when memo destination differs from vault address`() {
        val memo = "=:ETH.USDT-0xdAC17F958D2ee523a2206206994597C13D831ec7:$evmRecipient:0"

        resolveExternalSwapRecipient(
            memo = memo,
            destinationChain = Chain.Ethereum,
            vaultDestinationAddress = ownEvmChecksum,
        ) shouldBe evmRecipient
    }

    @Test
    fun `returns null for EVM own address regardless of checksum casing`() {
        val memo =
            "=:ETH.USDT-0xdAC17F958D2ee523a2206206994597C13D831ec7:" +
                ownEvmChecksum.lowercase() +
                ":0"

        resolveExternalSwapRecipient(
                memo = memo,
                destinationChain = Chain.Ethereum,
                vaultDestinationAddress = ownEvmChecksum,
            )
            .shouldBeNull()
    }

    @Test
    fun `non-EVM comparison is case-sensitive and exact`() {
        val ownBtc = "bc1q9d4ywgfnd8h43da5tpcxcn6ajv590cg6d3tg6axemvljvt448h5spsmtw8"

        resolveExternalSwapRecipient(
                memo = "SWAP:BTC.BTC:$ownBtc:0",
                destinationChain = Chain.Bitcoin,
                vaultDestinationAddress = ownBtc,
            )
            .shouldBeNull()

        resolveExternalSwapRecipient(
            memo = "SWAP:BTC.BTC:${ownBtc.uppercase()}:0",
            destinationChain = Chain.Bitcoin,
            vaultDestinationAddress = ownBtc,
        ) shouldBe ownBtc.uppercase()
    }

    @Test
    fun `parses destination from a streaming swap memo`() {
        val memo = "=:ETH.USDT-0xdAC17F958D2ee523a2206206994597C13D831ec7:$evmRecipient:0/3/10"

        resolveExternalSwapRecipient(
            memo = memo,
            destinationChain = Chain.Ethereum,
            vaultDestinationAddress = ownEvmChecksum,
        ) shouldBe evmRecipient
    }

    @Test
    fun `returns null for non-swap, blank, or malformed memos`() {
        // Liquidity add — not a swap verb.
        resolveExternalSwapRecipient(
                memo = "+:BTC.BTC:$evmRecipient",
                destinationChain = Chain.Ethereum,
                vaultDestinationAddress = ownEvmChecksum,
            )
            .shouldBeNull()

        resolveExternalSwapRecipient(
                memo = null,
                destinationChain = Chain.Ethereum,
                vaultDestinationAddress = ownEvmChecksum,
            )
            .shouldBeNull()

        // Too few segments to carry a destination.
        resolveExternalSwapRecipient(
                memo = "SWAP:ETH.ETH",
                destinationChain = Chain.Ethereum,
                vaultDestinationAddress = ownEvmChecksum,
            )
            .shouldBeNull()
    }

    @Test
    fun `returns null when the vault address is unknown`() {
        resolveExternalSwapRecipient(
                memo = "SWAP:BTC.BTC:$evmRecipient:0",
                destinationChain = Chain.Ethereum,
                vaultDestinationAddress = "",
            )
            .shouldBeNull()
    }
}
