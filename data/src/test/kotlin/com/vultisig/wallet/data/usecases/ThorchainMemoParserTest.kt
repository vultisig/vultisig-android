package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.OPERATION_BOND
import com.vultisig.wallet.data.models.OPERATION_LEAVE
import com.vultisig.wallet.data.models.OPERATION_LOAN_CLOSE
import com.vultisig.wallet.data.models.OPERATION_LOAN_OPEN
import com.vultisig.wallet.data.models.OPERATION_MINT
import com.vultisig.wallet.data.models.OPERATION_UNBOND
import com.vultisig.wallet.data.models.OPERATION_WITHDRAW
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ThorchainMemoParserTest {

    private val parser: ThorchainMemoParser = ThorchainMemoParserImpl()

    @Test
    fun `add liquidity memo with non-thor paired address populates pairedAddress`() {
        val parsed =
            parser.parse(
                "+:BASE.VVV-0X1234567890ABCDEF:0x14F6abcdef0123456789ABCDEF0123456789ABCDEF"
            )

        assertEquals(OPERATION_MINT, parsed?.operation)
        assertEquals("BASE.VVV-0X1234567890ABCDEF", parsed?.pool)
        assertEquals("", parsed?.nodeAddress)
        assertEquals("0x14F6abcdef0123456789ABCDEF0123456789ABCDEF", parsed?.pairedAddress)
    }

    @Test
    fun `add liquidity memo with thor paired address still populates pairedAddress`() {
        val parsed = parser.parse("+:BTC.BTC:thor1abcdef0123456789")

        assertEquals(OPERATION_MINT, parsed?.operation)
        assertEquals("BTC.BTC", parsed?.pool)
        assertEquals("", parsed?.nodeAddress)
        assertEquals("thor1abcdef0123456789", parsed?.pairedAddress)
    }

    @Test
    fun `add liquidity ADD prefix parses to mint`() {
        val parsed = parser.parse("ADD:BTC.BTC:thor1abcdef")

        assertEquals(OPERATION_MINT, parsed?.operation)
        assertEquals("BTC.BTC", parsed?.pool)
        assertEquals("thor1abcdef", parsed?.pairedAddress)
    }

    @Test
    fun `withdraw liquidity memo parses to withdraw with pool only`() {
        val parsed = parser.parse("-:BTC.BTC:5000")

        assertEquals(OPERATION_WITHDRAW, parsed?.operation)
        assertEquals("BTC.BTC", parsed?.pool)
        assertEquals("", parsed?.nodeAddress)
        assertEquals("", parsed?.pairedAddress)
    }

    @Test
    fun `bond memo populates nodeAddress with thor prefix`() {
        val parsed = parser.parse("BOND:thor1abcdef")

        assertEquals(OPERATION_BOND, parsed?.operation)
        assertEquals("thor1abcdef", parsed?.nodeAddress)
        assertEquals("", parsed?.pairedAddress)
    }

    @Test
    fun `bond memo with non-thor address leaves nodeAddress empty`() {
        val parsed = parser.parse("BOND:0xabcdef0123")

        assertEquals(OPERATION_BOND, parsed?.operation)
        assertEquals("", parsed?.nodeAddress)
    }

    @Test
    fun `maya bond memo with asset segment does not mistake asset for nodeAddress`() {
        val parsed = parser.parse("BOND:MAYA.CACAO:100:maya1nodeaddress:maya1provider")

        assertEquals(OPERATION_BOND, parsed?.operation)
        assertEquals("", parsed?.nodeAddress)
        assertEquals("", parsed?.pairedAddress)
    }

    @Test
    fun `unbond memo populates nodeAddress with thor prefix`() {
        val parsed = parser.parse("UNBOND:thor1abcdef:1000")

        assertEquals(OPERATION_UNBOND, parsed?.operation)
        assertEquals("thor1abcdef", parsed?.nodeAddress)
        assertEquals("", parsed?.pairedAddress)
    }

    @Test
    fun `maya unbond memo with asset segment does not mistake asset for nodeAddress`() {
        val parsed = parser.parse("UNBOND:MAYA.CACAO:100:maya1nodeaddress")

        assertEquals(OPERATION_UNBOND, parsed?.operation)
        assertEquals("", parsed?.nodeAddress)
        assertEquals("", parsed?.pairedAddress)
    }

    @Test
    fun `leave memo populates nodeAddress with thor prefix`() {
        val parsed = parser.parse("LEAVE:thor1abcdef")

        assertEquals(OPERATION_LEAVE, parsed?.operation)
        assertEquals("thor1abcdef", parsed?.nodeAddress)
        assertEquals("", parsed?.pairedAddress)
    }

    @Test
    fun `loan open memo parses with pool`() {
        val parsed = parser.parse("LOAN+:BTC.BTC:thor1abc")

        assertEquals(OPERATION_LOAN_OPEN, parsed?.operation)
        assertEquals("BTC.BTC", parsed?.pool)
    }

    @Test
    fun `loan close memo parses with pool`() {
        val parsed = parser.parse("LOAN-:BTC.BTC:thor1abc")

        assertEquals(OPERATION_LOAN_CLOSE, parsed?.operation)
        assertEquals("BTC.BTC", parsed?.pool)
    }

    @Test
    fun `swap memo returns null`() {
        assertNull(parser.parse("=:ETH.ETH:0xrecipient:1000"))
    }

    @Test
    fun `blank memo returns null`() {
        assertNull(parser.parse(""))
    }

    @Test
    fun `whitespace-only memo returns null`() {
        assertNull(parser.parse("   "))
    }

    @Test
    fun `unknown prefix returns null`() {
        assertNull(parser.parse("NOOP:something"))
    }

    @Test
    fun `lowercase add prefix is recognised`() {
        val parsed = parser.parse("add:BTC.BTC:thor1abc")

        assertEquals(OPERATION_MINT, parsed?.operation)
    }

    @Test
    fun `whitespace padded memo is parsed after trim`() {
        val parsed = parser.parse("  ADD:BTC.BTC:thor1abc  ")

        assertEquals(OPERATION_MINT, parsed?.operation)
        assertEquals("BTC.BTC", parsed?.pool)
        assertEquals("thor1abc", parsed?.pairedAddress)
    }

    @Test
    fun `WITHDRAW alias matches canonical dash prefix`() {
        val canonical = parser.parse("-:BTC.BTC:5000")
        val alias = parser.parse("WITHDRAW:BTC.BTC:5000")

        assertEquals(OPERATION_WITHDRAW, alias?.operation)
        assertEquals(canonical?.operation, alias?.operation)
        assertEquals(canonical?.pool, alias?.pool)
        assertEquals(canonical?.pairedAddress, alias?.pairedAddress)
    }

    @Test
    fun `WD alias matches canonical dash prefix`() {
        val canonical = parser.parse("-:BTC.BTC:5000")
        val alias = parser.parse("WD:BTC.BTC:5000")

        assertEquals(OPERATION_WITHDRAW, alias?.operation)
        assertEquals(canonical?.operation, alias?.operation)
        assertEquals(canonical?.pool, alias?.pool)
        assertEquals(canonical?.pairedAddress, alias?.pairedAddress)
    }

    @Test
    fun `dollar plus alias matches LOAN+ prefix`() {
        val canonical = parser.parse("LOAN+:BTC.BTC:thor1abc")
        val alias = parser.parse("\$+:BTC.BTC:thor1abc")

        assertEquals(OPERATION_LOAN_OPEN, alias?.operation)
        assertEquals(canonical?.operation, alias?.operation)
        assertEquals(canonical?.pool, alias?.pool)
    }

    @Test
    fun `dollar minus alias matches LOAN- prefix`() {
        val canonical = parser.parse("LOAN-:BTC.BTC:thor1abc")
        val alias = parser.parse("\$-:BTC.BTC:thor1abc")

        assertEquals(OPERATION_LOAN_CLOSE, alias?.operation)
        assertEquals(canonical?.operation, alias?.operation)
        assertEquals(canonical?.pool, alias?.pool)
    }
}
