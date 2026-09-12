package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Pins the Balances call a TAO send signs. `pallet_balances` numbers `transfer_allow_death` 0 and
 * `transfer_keep_alive` 3, and only the second refuses a transfer that would drop the sender under
 * the existential deposit — the difference between a rejected extrinsic and a reaped account whose
 * remainder the runtime destroys.
 */
class BittensorTransferCallDataTest {

    private val destination = ByteArray(32) { (it + 1).toByte() }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun `transfer is signed as keep_alive, never as allow_death`() {
        val callData =
            BittensorHelper.buildTransferCallData(destination, BigInteger.valueOf(1_000_000_000L))

        assertEquals(5, callData[0].toInt()) // Balances pallet
        assertEquals(3, callData[1].toInt()) // transfer_keep_alive (allow_death is 0)
    }

    @Test
    fun `call data carries MultiAddress Id, the destination and the compact amount`() {
        // 1 TAO = 1_000_000_000 rao → SCALE four-byte compact mode: (1e9 << 2) | 0b10, little
        // endian.
        val callData =
            BittensorHelper.buildTransferCallData(destination, BigInteger.valueOf(1_000_000_000L))

        assertEquals("050300" + destination.hex() + "02286bee", callData.hex())
    }

    @Test
    fun `an amount at the existential deposit compact encodes to the two-byte mode`() {
        val callData =
            BittensorHelper.buildTransferCallData(
                destination,
                BigInteger.valueOf(BittensorHelper.DEFAULT_EXISTENTIAL_DEPOSIT),
            )

        assertEquals("050300" + destination.hex() + "d107", callData.hex())
    }

    @Test
    fun `the all-zero AccountId is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            BittensorHelper.buildTransferCallData(ByteArray(32), BigInteger.ONE)
        }
    }

    @Test
    fun `an AccountId of the wrong length is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            BittensorHelper.buildTransferCallData(ByteArray(20) { 1 }, BigInteger.ONE)
        }
    }

    @Test
    fun `a negative amount is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            BittensorHelper.buildTransferCallData(destination, BigInteger.valueOf(-1L))
        }
    }
}
