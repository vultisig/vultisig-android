package com.vultisig.wallet.ui.models.send

import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class SendAmountExtensionsTest {

    @Test
    fun `isPlainDecimal accepts plain decimals`() {
        assertTrue("0".isPlainDecimal())
        assertTrue("5".isPlainDecimal())
        assertTrue("1000.12".isPlainDecimal())
        assertTrue("0.000001".isPlainDecimal())
        assertTrue(".5".isPlainDecimal())
        assertTrue("5.".isPlainDecimal())
    }

    @Test
    fun `isPlainDecimal rejects scientific notation`() {
        assertFalse("1e5".isPlainDecimal())
        assertFalse("1E5".isPlainDecimal())
        assertFalse("1E+2".isPlainDecimal())
        assertFalse("2.5e3".isPlainDecimal())
    }

    @Test
    fun `isPlainDecimal rejects hex notation`() {
        assertFalse("0x1F".isPlainDecimal())
        assertFalse("1f".isPlainDecimal())
    }

    @Test
    fun `isPlainDecimal rejects signs, separators and empties`() {
        assertFalse("-5".isPlainDecimal())
        assertFalse("+5".isPlainDecimal())
        assertFalse("1,5".isPlainDecimal())
        assertFalse("1.2.3".isPlainDecimal())
        assertFalse("".isPlainDecimal())
        assertFalse(".".isPlainDecimal())
        assertFalse(" 1".isPlainDecimal())
    }

    @Test
    fun `toPlainBigDecimalOrNull parses plain decimals and rejects hex or scientific`() {
        assertEquals(BigDecimal("1000.12"), "1000.12".toPlainBigDecimalOrNull())
        assertNull("1e5".toPlainBigDecimalOrNull())
        assertNull("0x1F".toPlainBigDecimalOrNull())
        assertNull("-5".toPlainBigDecimalOrNull())
    }
}
