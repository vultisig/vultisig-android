package com.vultisig.wallet.data.chains.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RippleDappTransactionDecoderTest {

    private fun value(tx: RippleDappTx, label: String): String? =
        tx.fields.firstOrNull { it.label == label }?.value

    @Test
    fun `decodes a native XRP Payment with drops converted to XRP`() {
        val json =
            """{"TransactionType":"Payment","Account":"rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY",""" +
                """"Destination":"rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrsGbY","Amount":"1500000",""" +
                """"DestinationTag":"12345"}"""

        val tx = RippleDappTransactionDecoder.decode(json)

        assertEquals("Payment", tx.transactionType)
        assertEquals("rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY", value(tx, "From"))
        assertEquals("rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrsGbY", value(tx, "To"))
        assertEquals("12345", value(tx, "Destination Tag"))
        // 1_500_000 drops = 1.5 XRP
        assertEquals("1.5 XRP", value(tx, "Amount"))
    }

    @Test
    fun `decodes an issued-currency amount into value plus issuer`() {
        val json =
            """{"TransactionType":"Payment","Account":"rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY",""" +
                """"Destination":"rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrsGbY",""" +
                """"Amount":{"currency":"USD","issuer":"rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q","value":"25"}}"""

        val tx = RippleDappTransactionDecoder.decode(json)

        assertEquals("25 USD", value(tx, "Amount"))
        assertEquals("rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q", value(tx, "Issuer"))
    }

    @Test
    fun `decodes an OfferCreate DEX swap with TakerGets and TakerPays`() {
        val json =
            """{"TransactionType":"OfferCreate","Account":"rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY",""" +
                """"TakerGets":"5000000",""" +
                """"TakerPays":{"currency":"USD","issuer":"rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q","value":"10"}}"""

        val tx = RippleDappTransactionDecoder.decode(json)

        assertEquals("OfferCreate", tx.transactionType)
        assertEquals("5 XRP", value(tx, "Selling"))
        assertEquals("10 USD", value(tx, "Buying"))
        assertEquals("rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q", value(tx, "Issuer"))
    }

    @Test
    fun `summarize renders an OfferCreate as gets to pays`() {
        val json =
            """{"TransactionType":"OfferCreate","Account":"rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY",""" +
                """"TakerGets":"1000000",""" +
                """"TakerPays":{"currency":"USD","issuer":"rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q","value":"2.5"}}"""

        assertEquals("OfferCreate: 1 XRP → 2.5 USD", RippleDappTransactionDecoder.summarize(json))
    }

    @Test
    fun `summarize renders a Payment with its amount`() {
        val json =
            """{"TransactionType":"Payment","Account":"rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY",""" +
                """"Destination":"rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrsGbY","Amount":"1000000"}"""

        assertEquals("Payment: 1 XRP", RippleDappTransactionDecoder.summarize(json))
    }

    @Test
    fun `summarize falls back to the bare type for other transactions`() {
        val json =
            """{"TransactionType":"TrustSet","Account":"rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY"}"""

        assertEquals("TrustSet", RippleDappTransactionDecoder.summarize(json))
    }

    @Test
    fun `summarize returns null for undecodable JSON`() {
        assertEquals(null, RippleDappTransactionDecoder.summarize("not-json{"))
    }

    @Test
    fun `malformed JSON yields empty fields but keeps the raw JSON for fallback`() {
        val raw = "definitely-not-json{"
        val tx = RippleDappTransactionDecoder.decode(raw)

        assertTrue(tx.fields.isEmpty())
        assertEquals(raw, tx.rawJson)
        assertEquals(null, tx.transactionType)
    }
}
