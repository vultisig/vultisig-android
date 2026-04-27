package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class BlockaidSimulationParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---------- EVM transfer -----------------------------------------------

    @Test
    fun `evm transfer parses fromCoin and amount`() {
        val response =
            evmResponse(
                """{
                    "simulation": {
                      "account_summary": {
                        "assets_diffs": [
                          {
                            "asset": {
                              "type": "ERC20",
                              "address": "0xA0b8...",
                              "symbol": "USDC",
                              "decimals": 6,
                              "logo_url": "https://logo/usdc.png"
                            },
                            "out": [{ "raw_value": "0x5f5e100" }]
                          }
                        ]
                      }
                    }
                  }
                """
                    .trimIndent()
            )

        val info = BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)

        val transfer = info as BlockaidSimulationInfo.Transfer
        assertEquals("USDC", transfer.fromCoin.ticker)
        assertEquals(6, transfer.fromCoin.decimals)
        assertEquals(BigInteger("100000000"), transfer.fromAmount)
        assertEquals("https://logo/usdc.png", transfer.fromCoin.logo)
    }

    @Test
    fun `evm swap parses out and in sides`() {
        val response =
            evmResponse(
                """{
                    "simulation": {
                      "account_summary": {
                        "assets_diffs": [
                          {
                            "asset": {
                              "type": "ETH",
                              "address": null,
                              "symbol": "ETH",
                              "decimals": 18,
                              "logo_url": ""
                            },
                            "out": [{ "raw_value": "0xde0b6b3a7640000" }]
                          },
                          {
                            "asset": {
                              "type": "ERC20",
                              "address": "0xUSDC",
                              "symbol": "USDC",
                              "decimals": 6,
                              "logo_url": ""
                            },
                            "in": [{ "raw_value": "3150000000" }]
                          }
                        ]
                      }
                    }
                  }
                """
                    .trimIndent()
            )

        val swap =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Swap

        assertEquals("ETH", swap.fromCoin.ticker)
        assertEquals(BigInteger("1000000000000000000"), swap.fromAmount)
        assertEquals("USDC", swap.toCoin.ticker)
        assertEquals(BigInteger("3150000000"), swap.toAmount)
    }

    @Test
    fun `evm swap with same asset on both sides becomes null (treated as transfer noise)`() {
        val response =
            evmResponse(
                """{
                    "simulation": {
                      "account_summary": {
                        "assets_diffs": [
                          {
                            "asset": { "type": "ERC20", "address": "0xUSDC", "symbol": "USDC", "decimals": 6 },
                            "out": [{ "raw_value": "100" }]
                          },
                          {
                            "asset": { "type": "ERC20", "address": "0xusdc", "symbol": "USDC", "decimals": 6 },
                            "in": [{ "raw_value": "99" }]
                          }
                        ]
                      }
                    }
                  }
                """
                    .trimIndent()
            )

        val info = BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
        // Same asset (case-insensitive address match) → swap collapses to null
        // because we cannot distinguish a true swap from rounding noise.
        assertNull(info)
    }

    @Test
    fun `evm response without simulation returns null`() {
        val response = evmResponse("""{}""")

        assertNull(BlockaidSimulationParser.parseEvm(response, Chain.Ethereum))
    }

    @Test
    fun `evm transfer with malformed raw value returns null`() {
        val response =
            evmResponse(
                """{
                    "simulation": {
                      "account_summary": {
                        "assets_diffs": [
                          {
                            "asset": { "type": "ETH", "symbol": "ETH", "decimals": 18 },
                            "out": [{ "raw_value": "not-a-number" }]
                          }
                        ]
                      }
                    }
                  }
                """
                    .trimIndent()
            )

        assertNull(BlockaidSimulationParser.parseEvm(response, Chain.Ethereum))
    }

    @Test
    fun `evm transfer ignores diffs with neither in nor out`() {
        val response =
            evmResponse(
                """{
                    "simulation": {
                      "account_summary": {
                        "assets_diffs": [
                          { "asset": { "symbol": "USDC", "decimals": 6 } },
                          {
                            "asset": { "symbol": "ETH", "decimals": 18 },
                            "out": [{ "raw_value": "0x1" }]
                          }
                        ]
                      }
                    }
                  }
                """
                    .trimIndent()
            )

        val transfer =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Transfer
        assertEquals("ETH", transfer.fromCoin.ticker)
    }

    // ---------- Solana transfer / swap -------------------------------------

    @Test
    fun `solana transfer of token is parsed`() {
        val response =
            solanaResponse(
                """{
                    "result": {
                      "simulation": {
                        "account_summary": {
                          "account_assets_diff": [
                            {
                              "asset": {
                                "type": "TOKEN",
                                "address": "MintAddr111",
                                "symbol": "USDC",
                                "decimals": 6,
                                "logo": "u.png"
                              },
                              "out": { "raw_value": "1000000" }
                            }
                          ]
                        }
                      }
                    }
                  }
                """
                    .trimIndent()
            )

        val transfer =
            BlockaidSimulationParser.parseSolana(response) as BlockaidSimulationInfo.Transfer

        assertEquals("USDC", transfer.fromCoin.ticker)
        assertEquals("MintAddr111", transfer.fromCoin.address)
        assertEquals(BigInteger("1000000"), transfer.fromAmount)
    }

    @Test
    fun `solana three diffs filter native SOL fee leaves the swap`() {
        val response =
            solanaResponse(
                """{
                    "result": {
                      "simulation": {
                        "account_summary": {
                          "account_assets_diff": [
                            {
                              "asset": { "type": "SOL", "decimals": 9, "symbol": "SOL", "address": null },
                              "out": { "raw_value": 5000 }
                            },
                            {
                              "asset": { "type": "TOKEN", "address": "MintA", "symbol": "USDC", "decimals": 6 },
                              "out": { "raw_value": "1000000" }
                            },
                            {
                              "asset": { "type": "TOKEN", "address": "MintB", "symbol": "BONK", "decimals": 5 },
                              "in": { "raw_value": "9999" }
                            }
                          ]
                        }
                      }
                    }
                  }
                """
                    .trimIndent()
            )

        val swap = BlockaidSimulationParser.parseSolana(response) as BlockaidSimulationInfo.Swap
        assertEquals("USDC", swap.fromCoin.ticker)
        assertEquals("BONK", swap.toCoin.ticker)
        assertEquals(BigInteger("1000000"), swap.fromAmount)
        assertEquals(BigInteger("9999"), swap.toAmount)
    }

    @Test
    fun `solana native SOL transfer maps to wrapped sol mint`() {
        val response =
            solanaResponse(
                """{
                    "result": {
                      "simulation": {
                        "account_summary": {
                          "account_assets_diff": [
                            {
                              "asset": { "type": "SOL", "decimals": 9, "symbol": "SOL", "address": null },
                              "out": { "raw_value": 1000000000 }
                            }
                          ]
                        }
                      }
                    }
                  }
                """
                    .trimIndent()
            )

        val transfer =
            BlockaidSimulationParser.parseSolana(response) as BlockaidSimulationInfo.Transfer

        // Sentinel mint is used so downstream lookups treat native SOL like
        // wrapped SOL, matching the iOS / extension parser behaviour.
        assertEquals(BlockaidSimulationParser.WRAPPED_SOL_MINT, transfer.fromCoin.address)
        assertEquals("SOL", transfer.fromCoin.ticker)
    }

    @Test
    fun `solana empty simulation returns null`() {
        val response = solanaResponse("""{ "result": { "simulation": null } }""")

        assertNull(BlockaidSimulationParser.parseSolana(response))
    }

    // ---------- parseRawAmount ---------------------------------------------

    @Test
    fun `parseRawAmount handles hex prefix and decimal`() {
        assertEquals(BigInteger.valueOf(0xff), BlockaidSimulationParser.parseRawAmount("0xff"))
        assertEquals(BigInteger.valueOf(0xff), BlockaidSimulationParser.parseRawAmount("0XfF"))
        assertEquals(BigInteger.valueOf(255), BlockaidSimulationParser.parseRawAmount("255"))
        assertNull(BlockaidSimulationParser.parseRawAmount(""))
        assertNull(BlockaidSimulationParser.parseRawAmount("not-a-number"))
    }

    @Test
    fun `parseRawAmount rejects pathologically long input without parsing it`() {
        // Defends against the parser allocating a multi-megabyte BigInteger
        // when fed a hostile or malformed `raw_value`. Anything longer than
        // a u256 hex literal (66 chars including 0x prefix) is rejected; the
        // implementation caps at 80 to leave headroom for legitimate decimal
        // representations.
        val pathological = "0x" + "f".repeat(10_000)
        assertNull(BlockaidSimulationParser.parseRawAmount(pathological))
    }

    @Test
    fun `evm transfer with non-https logo url drops the logo to empty`() {
        // Logo URLs come from untrusted Blockaid responses and flow into
        // Coil. Anything other than https:// (e.g. http://, file://, ipfs://)
        // is replaced with empty so the UI shows the chain-native fallback
        // and Coil never gets a chance to follow a downgraded scheme.
        val response = evmResponse(evmTransferJson(logoUrl = "http://logo/usdc.png"))

        val transfer =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Transfer

        assertEquals("", transfer.fromCoin.logo)
    }

    @Test
    fun `evm transfer with bidi override codepoints in symbol strips them`() {
        // U+202E (RTL OVERRIDE) and U+200B (ZERO WIDTH SPACE) embedded in a
        // ticker would render as a different token visually than the bytes
        // suggest. The sanitiser must strip them before the ticker is shown
        // in the hero, otherwise a hostile response could disguise the asset
        // the user is signing for.
        val response = evmResponse(evmTransferJson(symbol = "U‮SDC​"))

        val transfer =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Transfer

        assertEquals("USDC", transfer.fromCoin.ticker)
    }

    @Test
    fun `evm transfer with extreme decimals is clamped to the parser ceiling`() {
        val response = evmResponse(evmTransferJson(decimals = 999_999))

        val transfer =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Transfer

        // 36 is the parser's hard upper bound; the wire said 999999.
        assertEquals(36, transfer.fromCoin.decimals)
    }

    @Test
    fun `evm transfer with negative decimals is clamped to zero`() {
        val response = evmResponse(evmTransferJson(decimals = -5))

        val transfer =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Transfer

        assertEquals(0, transfer.fromCoin.decimals)
    }

    private fun evmTransferJson(
        symbol: String = "USDC",
        decimals: Int = 6,
        logoUrl: String = "https://logo/usdc.png",
    ): String {
        // Quote and escape the variable strings via JsonPrimitive so embedded
        // bidi/Unicode codepoints survive the JSON round-trip intact.
        val symbolJson = kotlinx.serialization.json.JsonPrimitive(symbol).toString()
        val logoJson = kotlinx.serialization.json.JsonPrimitive(logoUrl).toString()
        return """{
              "simulation": {
                "account_summary": {
                  "assets_diffs": [
                    {
                      "asset": {
                        "type": "ERC20",
                        "address": "0xA0b8...",
                        "symbol": $symbolJson,
                        "decimals": $decimals,
                        "logo_url": $logoJson
                      },
                      "out": [{ "raw_value": "0x5f5e100" }]
                    }
                  ]
                }
              }
            }"""
            .trimIndent()
    }

    // ---------- helpers ----------------------------------------------------

    private fun evmResponse(simulationJson: String): BlockaidEvmSimulationResponseJson =
        json.decodeFromString(BlockaidEvmSimulationResponseJson.serializer(), simulationJson)

    private fun solanaResponse(jsonString: String): BlockaidSolanaSimulationResponseJson =
        json.decodeFromString(BlockaidSolanaSimulationResponseJson.serializer(), jsonString)
}
