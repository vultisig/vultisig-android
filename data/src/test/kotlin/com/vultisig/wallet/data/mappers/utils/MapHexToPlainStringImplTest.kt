package com.vultisig.wallet.data.mappers.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MapHexToPlainStringImplTest {

    private val mapper = MapHexToPlainStringImpl()

    @Test
    fun `uneven length hex fails`() {
        assertFailsWith<IllegalArgumentException> {
            mapper("1")
        }
        assertFailsWith<IllegalArgumentException> {
            mapper("123")
        }
    }

    @Test
    fun `hex to string`() {
        assertEquals("", mapper(""))
        assertEquals("1", mapper("31"))
        assertEquals("123", mapper("313233"))
        assertEquals("abc", mapper("616263"))
    }

}