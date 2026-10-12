package com.vultisig.wallet.ui.models.sign

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TypedDataForDisplayTest {

    @Test
    fun `typed data is indented one key per line in its original order`() {
        val compact =
            """{"domain":{"chainId":"1","name":"Permit2"},"message":{"spender":"0x23617e59a5925b2a4bf75d73ff6711cd0b29de85","amount":"1461501637330902918203684832716283019655932542975"},"primaryType":"PermitSingle"}"""

        val expected =
            """
            {
              "domain": {
                "chainId": "1",
                "name": "Permit2"
              },
              "message": {
                "spender": "0x23617e59a5925b2a4bf75d73ff6711cd0b29de85",
                "amount": "1461501637330902918203684832716283019655932542975"
              },
              "primaryType": "PermitSingle"
            }
            """
                .trimIndent()

        assertEquals(expected, typedDataForDisplay(TYPED_DATA_V4, compact))
    }

    @Test
    fun `numeric literals keep their exact digits`() {
        val compact = """{"amount":115792089237316195423570985008687907853269984665640564039457584007913129639935,"chainId":1}"""

        val expected =
            """
            {
              "amount": 115792089237316195423570985008687907853269984665640564039457584007913129639935,
              "chainId": 1
            }
            """
                .trimIndent()

        assertEquals(expected, typedDataForDisplay(TYPED_DATA_V4, compact))
    }

    @Test
    fun `arrays, escapes, nulls, booleans and empties are printed as parsed`() {
        val compact =
            """{"types":[{"name":"a","type":"string"}],"note":"say \"hi\"\n","flag":true,"none":null,"empty":{},"list":[]}"""

        val expected =
            """
            {
              "types": [
                {
                  "name": "a",
                  "type": "string"
                }
              ],
              "note": "say \"hi\"\n",
              "flag": true,
              "none": null,
              "empty": {},
              "list": []
            }
            """
                .trimIndent()

        assertEquals(expected, typedDataForDisplay(TYPED_DATA_V4, compact))
    }

    @Test
    fun `method match ignores case`() {
        assertEquals("{\n  \"a\": 1\n}", typedDataForDisplay("ETH_SIGNTYPEDDATA_V4", """{"a":1}"""))
    }

    @Test
    fun `a digest under the typed data method is left as is`() {
        val digest = "0x" + "ab".repeat(32)

        assertEquals(digest, typedDataForDisplay(TYPED_DATA_V4, digest))
    }

    @Test
    fun `text that is not a JSON object is left as is`() {
        listOf("not json", """{"a":""", """["a","b"]""", "\"just a string\"").forEach {
            assertEquals(it, typedDataForDisplay(TYPED_DATA_V4, it))
        }
    }

    @Test
    fun `nesting too deep to print safely is left as is`() {
        val deep = """{"a":""" + "[".repeat(5_000) + "]".repeat(5_000) + "}"

        assertEquals(deep, typedDataForDisplay(TYPED_DATA_V4, deep))
    }

    @Test
    fun `other methods are never reformatted`() {
        val json = """{"a":1}"""

        assertEquals(json, typedDataForDisplay("personal_sign", json))
        assertEquals(json, typedDataForDisplay("eth_signTypedData_v3", json))
    }

    @Test
    fun `ui model keeps the signed message and derives the display one`() {
        val compact = """{"a":1}"""

        val model = SignMessageTransactionUiModel(method = TYPED_DATA_V4, message = compact)

        assertEquals(compact, model.message)
        assertEquals("{\n  \"a\": 1\n}", model.displayMessage)
    }

    private companion object {
        const val TYPED_DATA_V4 = "eth_signTypedData_v4"
    }
}
