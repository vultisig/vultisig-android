package com.vultisig.wallet.utils

import com.vultisig.wallet.ui.utils.forCanvasMinify
import com.vultisig.wallet.ui.utils.groupByTwoButKeepFirstElement
import org.junit.jupiter.api.Test

class StringXTest {
    @Test
    fun `groupByTwoButKeepFirstElement returns empty list when input is empty`() {
        val input = emptyList<String>()

        val result = input.groupByTwoButKeepFirstElement()

        assert(result.isEmpty())
    }

    @Test
    fun `groupByTwoButKeepFirstElement returns list with one element when input has one element`() {
        val input = listOf("1")

        val result = input.groupByTwoButKeepFirstElement()

        assert(result.size == 1)
        assert(result[0] == "1")
    }

    @Test
    fun `groupByTwoButKeepFirstElement returns list with one element when input has two elements`() {
        val input = listOf("1", "2")

        val result = input.groupByTwoButKeepFirstElement()

        assert(result.size == 2)
        assert(result[0] == "1")
        assert(result[1] == "2")
    }

    @Test
    fun `groupByTwoButKeepFirstElement returns list with two elements when input has three elements`() {
        val input = listOf("1", "2", "3")

        val result = input.groupByTwoButKeepFirstElement()

        assert(result.size == 2)
        assert(result[0] == "1")
        assert(result[1] == "2, 3")
    }

    @Test
    fun `groupByTwoButKeepFirstElement returns list with two elements when input has four elements`() {
        val input = listOf("1", "2", "3", "4")

        val result = input.groupByTwoButKeepFirstElement()

        assert(result.size == 3)
        assert(result[0] == "1")
        assert(result[1] == "2, 3")
        assert(result[2] == "4")
    }

    @Test
    fun `groupByTwoButKeepFirstElement returns list with two elements when input has five elements`() {
        val input = listOf("1", "2", "3", "4", "5")

        val result = input.groupByTwoButKeepFirstElement()

        assert(result.size == 3)
        assert(result[0] == "1")
        assert(result[1] == "2, 3")
        assert(result[2] == "4, 5")
    }

    @Test
    fun `forCanvasMinify returns the same string when length is less than or equal to 20`() {
        val input = "12345678901234567890"

        val result = input.forCanvasMinify()

        assert(result == input)
    }

    @Test
    fun `forCanvasMinify returns the minified string when length is greater than 20`() {
        val input = "123456789012345678901234567890"

        val result = input.forCanvasMinify()

        assert(result == "1234567890...1234567890")
    }

    @Test
    fun `forCanvasMinify returns the minified string when length is greater than 20 and numSymbolsKeep is 5`() {
        val input = "123456789012345678901234567890"

        val result = input.forCanvasMinify(5)

        assert(result == "12345...67890")
    }

    @Test
    fun `forCanvasMinify returns the same string when length is equal to 20 and numSymbolsKeep is 20`() {
        val input = "12345678901234567890"

        val result = input.forCanvasMinify(20)

        assert(result == input)
    }
}