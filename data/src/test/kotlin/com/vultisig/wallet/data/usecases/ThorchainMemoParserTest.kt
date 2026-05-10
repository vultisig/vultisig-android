package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.OPERATION_MINT
import com.vultisig.wallet.data.models.OPERATION_WITHDRAW
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ThorchainMemoParserTest {

    @Test
    fun `add liquidity memo parses to mint with pool and paired address`() {
        val parsed =
            ThorchainMemoParser.parse(
                "+:BASE.VVV-0X1234567890ABCDEF:0x14F6abcdef0123456789ABCDEF0123456789ABCDEF"
            )

        assertEquals(OPERATION_MINT, parsed?.operation)
        assertEquals("BASE.VVV-0X1234567890ABCDEF", parsed?.pool)
        assertEquals("0x14F6abcdef0123456789ABCDEF0123456789ABCDEF", parsed?.thorAddress)
    }

    @Test
    fun `add liquidity ADD prefix parses to mint`() {
        val parsed = ThorchainMemoParser.parse("ADD:BTC.BTC:thor1abcdef")

        assertEquals(OPERATION_MINT, parsed?.operation)
        assertEquals("BTC.BTC", parsed?.pool)
        assertEquals("thor1abcdef", parsed?.thorAddress)
    }

    @Test
    fun `withdraw liquidity memo parses to withdraw with pool only`() {
        val parsed = ThorchainMemoParser.parse("-:BTC.BTC:5000")

        assertEquals(OPERATION_WITHDRAW, parsed?.operation)
        assertEquals("BTC.BTC", parsed?.pool)
        assertEquals("", parsed?.thorAddress)
    }

    @Test
    fun `bond memo parses with thor address`() {
        val parsed = ThorchainMemoParser.parse("BOND:thor1abcdef")

        assertEquals("Bond", parsed?.operation)
        assertEquals("thor1abcdef", parsed?.thorAddress)
    }

    @Test
    fun `unbond memo parses with thor address`() {
        val parsed = ThorchainMemoParser.parse("UNBOND:thor1abcdef:1000")

        assertEquals("Unbond", parsed?.operation)
        assertEquals("thor1abcdef", parsed?.thorAddress)
    }

    @Test
    fun `leave memo parses with thor address`() {
        val parsed = ThorchainMemoParser.parse("LEAVE:thor1abcdef")

        assertEquals("Leave", parsed?.operation)
        assertEquals("thor1abcdef", parsed?.thorAddress)
    }

    @Test
    fun `loan open memo parses with pool`() {
        val parsed = ThorchainMemoParser.parse("LOAN+:BTC.BTC:thor1abc")

        assertEquals("Loan Open", parsed?.operation)
        assertEquals("BTC.BTC", parsed?.pool)
    }

    @Test
    fun `loan close memo parses with pool`() {
        val parsed = ThorchainMemoParser.parse("LOAN-:BTC.BTC:thor1abc")

        assertEquals("Loan Close", parsed?.operation)
        assertEquals("BTC.BTC", parsed?.pool)
    }

    @Test
    fun `swap memo returns null`() {
        assertNull(ThorchainMemoParser.parse("=:ETH.ETH:0xrecipient:1000"))
    }

    @Test
    fun `blank memo returns null`() {
        assertNull(ThorchainMemoParser.parse(""))
    }

    @Test
    fun `whitespace-only memo returns null`() {
        assertNull(ThorchainMemoParser.parse("   "))
    }

    @Test
    fun `unknown prefix returns null`() {
        assertNull(ThorchainMemoParser.parse("NOOP:something"))
    }

    @Test
    fun `lowercase add prefix is recognised`() {
        val parsed = ThorchainMemoParser.parse("add:BTC.BTC:thor1abc")

        assertEquals(OPERATION_MINT, parsed?.operation)
    }

    @Test
    fun `whitespace padded memo is parsed after trim`() {
        val parsed = ThorchainMemoParser.parse("  ADD:BTC.BTC:thor1abc  ")

        assertEquals(OPERATION_MINT, parsed?.operation)
        assertEquals("BTC.BTC", parsed?.pool)
        assertEquals("thor1abc", parsed?.thorAddress)
    }

    @Test
    fun `WITHDRAW alias matches canonical dash prefix`() {
        val canonical = ThorchainMemoParser.parse("-:BTC.BTC:5000")
        val alias = ThorchainMemoParser.parse("WITHDRAW:BTC.BTC:5000")

        assertEquals(OPERATION_WITHDRAW, alias?.operation)
        assertEquals(canonical?.operation, alias?.operation)
        assertEquals(canonical?.pool, alias?.pool)
        assertEquals(canonical?.thorAddress, alias?.thorAddress)
    }

    @Test
    fun `WD alias matches canonical dash prefix`() {
        val canonical = ThorchainMemoParser.parse("-:BTC.BTC:5000")
        val alias = ThorchainMemoParser.parse("WD:BTC.BTC:5000")

        assertEquals(OPERATION_WITHDRAW, alias?.operation)
        assertEquals(canonical?.operation, alias?.operation)
        assertEquals(canonical?.pool, alias?.pool)
        assertEquals(canonical?.thorAddress, alias?.thorAddress)
    }

    @Test
    fun `dollar plus alias matches LOAN+ prefix`() {
        val canonical = ThorchainMemoParser.parse("LOAN+:BTC.BTC:thor1abc")
        val alias = ThorchainMemoParser.parse("\$+:BTC.BTC:thor1abc")

        assertEquals("Loan Open", alias?.operation)
        assertEquals(canonical?.operation, alias?.operation)
        assertEquals(canonical?.pool, alias?.pool)
        assertEquals(canonical?.thorAddress, alias?.thorAddress)
    }

    @Test
    fun `dollar minus alias matches LOAN- prefix`() {
        val canonical = ThorchainMemoParser.parse("LOAN-:BTC.BTC:thor1abc")
        val alias = ThorchainMemoParser.parse("\$-:BTC.BTC:thor1abc")

        assertEquals("Loan Close", alias?.operation)
        assertEquals(canonical?.operation, alias?.operation)
        assertEquals(canonical?.pool, alias?.pool)
        assertEquals(canonical?.thorAddress, alias?.thorAddress)
    }
}
