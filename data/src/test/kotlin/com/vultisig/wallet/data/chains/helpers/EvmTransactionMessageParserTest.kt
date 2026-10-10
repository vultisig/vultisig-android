package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class EvmTransactionMessageParserTest {

    private val dead = "0x000000000000000000000000000000000000dead"

    @Test
    fun `legacy EIP-155 preimage decodes recipient value and chain`() {
        // rlp([nonce=0, gasPrice=20gwei, gas=21000, to=dEaD, value=1, data=0x, chainId=1, 0, 0])
        val tx =
            EvmTransactionMessageParser.parse(
                "0xe4808504a817c80082520894000000000000000000000000000000000000dead0180018080"
            )!!
        assertEquals(BigInteger.ONE, tx.chainId)
        assertEquals(dead, tx.to)
        assertEquals(BigInteger.ONE, tx.value)
        assertEquals("0x", tx.dataHex)
    }

    @Test
    fun `type-2 EIP-1559 preimage decodes recipient value and chain`() {
        val tx =
            EvmTransactionMessageParser.parse(
                "0x02e80180843b9aca008504a817c80082520894000000000000000000000000000000000000dead0180c0"
            )!!
        assertEquals(BigInteger.ONE, tx.chainId)
        assertEquals(dead, tx.to)
        assertEquals(BigInteger.ONE, tx.value)
        assertEquals("0x", tx.dataHex)
    }

    @Test
    fun `type-1 EIP-2930 preimage decodes recipient value and chain`() {
        val tx =
            EvmTransactionMessageParser.parse(
                "0x01e301808504a817c80082520894000000000000000000000000000000000000dead0180c0"
            )!!
        assertEquals(BigInteger.ONE, tx.chainId)
        assertEquals(dead, tx.to)
        assertEquals(BigInteger.ONE, tx.value)
        assertEquals("0x", tx.dataHex)
    }

    @Test
    fun `legacy preimage with calldata keeps the data for the recipient contract`() {
        // transfer(dEaD, 1_000_000) sent to the USDT contract, value 0.
        val tx =
            EvmTransactionMessageParser.parse(
                "0xf869808504a817c80082ea6094dac17f958d2ee523a2206206994597c13d831ec780b844" +
                    "a9059cbb000000000000000000000000000000000000000000000000000000000000dead" +
                    "00000000000000000000000000000000000000000000000000000000000f4240018080"
            )!!
        assertEquals("0xdac17f958d2ee523a2206206994597c13d831ec7", tx.to)
        assertEquals(BigInteger.ZERO, tx.value)
        assertEquals(true, tx.dataHex.startsWith("0xa9059cbb"))
    }

    @Test
    fun `contract creation has a null recipient`() {
        // rlp([0, 20gwei, 21000, "" (empty to), 1, 0x, 1, 0, 0])
        val tx = EvmTransactionMessageParser.parse("0xd0808504a817c800825208800180018080")!!
        assertNull(tx.to)
    }

    @Test
    fun `a 32-byte hash is not a transaction`() {
        assertNull(
            EvmTransactionMessageParser.parse(
                "0x" + "11".repeat(32)
            )
        )
    }

    @Test
    fun `raw ABI calldata is not a transaction`() {
        // An ERC-20 transfer selector + two words: starts with a string byte, not a list prefix.
        assertNull(
            EvmTransactionMessageParser.parse(
                "0xa9059cbb000000000000000000000000000000000000000000000000000000000000dead" +
                    "00000000000000000000000000000000000000000000000000000000000f4240"
            )
        )
    }

    @Test
    fun `a list whose to field is not 20 bytes is rejected`() {
        // Same 9-item legacy shape but the to field is 4 bytes (0xdeadbeef), not an address.
        assertNull(
            EvmTransactionMessageParser.parse("0xd4808504a817c80082520884deadbeef0180018080")
        )
    }

    @Test
    fun `trailing bytes after the rlp item are rejected`() {
        assertNull(
            EvmTransactionMessageParser.parse(
                "0xe4808504a817c80082520894000000000000000000000000000000000000dead0180018080ff"
            )
        )
    }

    @Test
    fun `a signed legacy transaction is not a preimage`() {
        // Same tx but with v=37, r, s in slots 6-8 instead of chainId, 0, 0 — a broadcastable tx,
        // not a signing preimage, so it must not be read as one.
        assertNull(
            EvmTransactionMessageParser.parse(
                "0xf864808504a817c80082520894000000000000000000000000000000000000dead018025" +
                    "a0cbda998fedd4ac6db90d82e62f25825aaeb5200b86d7961fa136e25d824de214" +
                    "a070869d7dc0b551212df9fd5614599fccb554fbe976482021425ffade767b0bd3"
            )
        )
    }

    @Test
    fun `a signed type-2 transaction is not a preimage`() {
        // 12 items: the unsigned 9 plus yParity, r, s.
        assertNull(
            EvmTransactionMessageParser.parse(
                "0x02f86b0180843b9aca008504a817c80082520894000000000000000000000000000000000000dead" +
                    "0180c080a0cbda998fedd4ac6db90d82e62f25825aaeb5200b86d7961fa136e25d824de214" +
                    "a070869d7dc0b551212df9fd5614599fccb554fbe976482021425ffade767b0bd3"
            )
        )
    }

    @Test
    fun `plain text and empty input are not transactions`() {
        assertNull(EvmTransactionMessageParser.parse("0x"))
        assertNull(EvmTransactionMessageParser.parse("not hex at all"))
        assertNull(EvmTransactionMessageParser.parse("0x48656c6c6f")) // "Hello"
    }
}
