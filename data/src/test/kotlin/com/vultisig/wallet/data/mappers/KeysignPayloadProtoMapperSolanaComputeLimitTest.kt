package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.chains.helpers.SOLANA_PRIORITY_FEE_LIMIT
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the inbound parsing of the Solana `compute_limit` proto field against malformed peer input.
 *
 * A joining device deserializes the initiator's `KeysignPayloadProto` at the start of a keysign
 * ceremony — untrusted data crossing a peer/system boundary. A buggy or malicious co-signer sending
 * a non-numeric `compute_limit` string must never crash the receiving app; it has to fall back to
 * the same default limit the sibling `priority_fee` field already uses, so parsing stays fail-soft
 * across the whole Solana block. Well-formed values must still round-trip untouched so every
 * participant keeps encoding the identical `setComputeUnitLimit` instruction.
 */
class KeysignPayloadProtoMapperSolanaComputeLimitTest {

    private val outbound = PayloadToProtoMapperImpl()
    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `malformed compute_limit falls back to the default limit instead of throwing`() {
        assertEquals(DEFAULT_LIMIT, restoredPriorityLimit(computeLimit = "not-a-number"))
    }

    @Test
    fun `empty compute_limit falls back to the default limit`() {
        assertEquals(DEFAULT_LIMIT, restoredPriorityLimit(computeLimit = ""))
    }

    @Test
    fun `absent compute_limit falls back to the default limit`() {
        assertEquals(DEFAULT_LIMIT, restoredPriorityLimit(computeLimit = null))
    }

    @Test
    fun `well-formed compute_limit round-trips unchanged`() {
        assertEquals(BigInteger.valueOf(250_000), restoredPriorityLimit(computeLimit = "250000"))
    }

    /**
     * Builds a valid Solana proto via the outbound mapper, overwrites just the raw `compute_limit`
     * wire string, then returns the priority limit the inbound mapper restores from it.
     */
    private fun restoredPriorityLimit(computeLimit: String?): BigInteger {
        val proto = requireNotNull(outbound(solanaPayload()))
        val tampered =
            proto.copy(
                solanaSpecific =
                    requireNotNull(proto.solanaSpecific).copy(computeLimit = computeLimit)
            )
        val restored = inbound(tampered).blockChainSpecific as BlockChainSpecific.Solana
        return restored.priorityLimit
    }

    private fun solanaPayload() =
        KeysignPayload(
            coin = SOL,
            toAddress = "AqP4dJRSjTfHFxb5wD7sK6wRK2m3rD8ph5V2n7t4cXyZ",
            toAmount = BigInteger.valueOf(1_000_000),
            blockChainSpecific =
                BlockChainSpecific.Solana(
                    recentBlockHash = "9xQeWvG816bUx9EPjHmaT23yvVM2ZWbrrpZb9PusVFin",
                    priorityFee = BigInteger.valueOf(1_000_000),
                    priorityLimit = DEFAULT_LIMIT,
                ),
            memo = null,
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        val DEFAULT_LIMIT: BigInteger = SOLANA_PRIORITY_FEE_LIMIT.toBigInteger()

        val SOL =
            Coin(
                chain = Chain.Solana,
                ticker = "SOL",
                logo = "solana",
                address = "AqP4dJRSjTfHFxb5wD7sK6wRK2m3rD8ph5V2n7t4cXyZ",
                decimal = 9,
                hexPublicKey = "020202020202020202020202020202020202020202020202020202020202020202",
                priceProviderID = "solana",
                contractAddress = "",
                isNativeToken = true,
            )
    }
}
