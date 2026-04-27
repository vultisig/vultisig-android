package com.vultisig.wallet.data.models.payload

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SignTonTest {

    private val validMessage =
        TonMessage(
            toAddress = "EQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM9c",
            toAmount = 1_000_000L,
        )

    @Test
    fun `validate passes for a single valid message`() {
        assertDoesNotThrow { SignTon(listOf(validMessage)).validate() }
    }

    @Test
    fun `validate passes for four messages`() {
        val messages = List(SignTon.MAX_MESSAGES) { validMessage }
        assertDoesNotThrow { SignTon(messages).validate() }
    }

    @Test
    fun `validate throws when messages list is empty`() {
        assertThrows(IllegalArgumentException::class.java) { SignTon(emptyList()).validate() }
    }

    @Test
    fun `validate throws when messages exceed maximum`() {
        val messages = List(SignTon.MAX_MESSAGES + 1) { validMessage }
        assertThrows(IllegalArgumentException::class.java) { SignTon(messages).validate() }
    }

    @Test
    fun `validate throws when toAmount is zero`() {
        val msg = validMessage.copy(toAmount = 0L)
        assertThrows(IllegalArgumentException::class.java) { SignTon(listOf(msg)).validate() }
    }

    @Test
    fun `validate throws when toAmount is negative`() {
        val msg = validMessage.copy(toAmount = -1L)
        assertThrows(IllegalArgumentException::class.java) { SignTon(listOf(msg)).validate() }
    }

    @Test
    fun `payload and stateInit default to empty strings`() {
        val msg = TonMessage(toAddress = "EQAB", toAmount = 1L)
        assert(msg.payload.isEmpty())
        assert(msg.stateInit.isEmpty())
    }
}
