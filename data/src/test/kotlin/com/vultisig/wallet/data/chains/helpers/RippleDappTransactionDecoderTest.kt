package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RippleDappTransactionDecoderTest {

    private fun value(tx: RippleDappTx, key: RippleDappTxFieldKey): String? = tx.value(key)

    @Test
    fun `decodes a native XRP Payment with drops converted to XRP`() {
        val json =
            """{"TransactionType":"Payment","Account":"rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY",""" +
                """"Destination":"rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrsGbY","Amount":"1500000",""" +
                """"DestinationTag":"12345"}"""

        val tx = RippleDappTransactionDecoder.decode(json)

        assertEquals("Payment", tx.transactionType)
        assertEquals("rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY", value(tx, RippleDappTxFieldKey.FROM))
        assertEquals("rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrsGbY", value(tx, RippleDappTxFieldKey.TO))
        assertEquals("12345", value(tx, RippleDappTxFieldKey.DESTINATION_TAG))
        // 1_500_000 drops = 1.5 XRP
        assertEquals("1.5 XRP", value(tx, RippleDappTxFieldKey.AMOUNT))
    }

    @Test
    fun `decodes an issued-currency amount into value plus issuer`() {
        val json =
            """{"TransactionType":"Payment","Account":"rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY",""" +
                """"Destination":"rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrsGbY",""" +
                """"Amount":{"currency":"USD","issuer":"rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q","value":"25"}}"""

        val tx = RippleDappTransactionDecoder.decode(json)

        assertEquals("25 USD", value(tx, RippleDappTxFieldKey.AMOUNT))
        assertEquals(
            "rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q",
            value(tx, RippleDappTxFieldKey.AMOUNT_ISSUER),
        )
    }

    @Test
    fun `decodes an OfferCreate DEX swap with distinct selling and buying issuers`() {
        val json =
            """{"TransactionType":"OfferCreate","Account":"rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY",""" +
                """"TakerGets":{"currency":"EUR","issuer":"rSellIssuer000000000000000000000","value":"5"},""" +
                """"TakerPays":{"currency":"USD","issuer":"rBuyIssuer0000000000000000000000","value":"10"}}"""

        val tx = RippleDappTransactionDecoder.decode(json)

        assertEquals("OfferCreate", tx.transactionType)
        assertEquals("5 EUR", value(tx, RippleDappTxFieldKey.SELLING))
        assertEquals(
            "rSellIssuer000000000000000000000",
            value(tx, RippleDappTxFieldKey.SELLING_ISSUER),
        )
        assertEquals("10 USD", value(tx, RippleDappTxFieldKey.BUYING))
        assertEquals(
            "rBuyIssuer0000000000000000000000",
            value(tx, RippleDappTxFieldKey.BUYING_ISSUER),
        )
    }

    @Test
    fun `decodes the signed Fee as XRP`() {
        val json =
            """{"TransactionType":"Payment","Account":"rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY",""" +
                """"Destination":"rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrsGbY","Amount":"1000000","Fee":"12"}"""

        val tx = RippleDappTransactionDecoder.decode(json)

        // 12 drops = 0.000012 XRP
        assertEquals("0.000012 XRP", value(tx, RippleDappTxFieldKey.FEE))
        assertEquals(BigInteger.valueOf(12), RippleDappTransactionDecoder.feeDrops(json))
    }

    @Test
    fun `feeDrops is null when Fee is absent or unparseable`() {
        val noFee = """{"TransactionType":"Payment","Account":"rB5","Amount":"1000000"}"""
        assertNull(RippleDappTransactionDecoder.feeDrops(noFee))
        assertNull(RippleDappTransactionDecoder.feeDrops("not-json{"))
    }

    @Test
    fun `decodes a TrustSet LimitAmount object`() {
        val json =
            """{"TransactionType":"TrustSet","Account":"rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY",""" +
                """"LimitAmount":{"currency":"USD","issuer":"rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q","value":"100"}}"""

        val tx = RippleDappTransactionDecoder.decode(json)

        assertEquals("100 USD", value(tx, RippleDappTxFieldKey.LIMIT))
        assertEquals(
            "rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q",
            value(tx, RippleDappTxFieldKey.LIMIT_ISSUER),
        )
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
