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
        // Wrapped-SOL mint sentinel used by the parser for native SOL diffs.
        assertEquals("So11111111111111111111111111111111111111112", transfer.fromCoin.address)
        assertEquals("SOL", transfer.fromCoin.ticker)
    }

    @Test
    fun `solana native SOL with assetType-only marker still resolves to wrapped sol`() {
        // Regression: Blockaid sometimes flags native SOL via the sibling `asset_type` field on
        // the diff while leaving `asset.type` blank. Without the parser checking both, the diff
        // collapses to a null mint and the whole transfer is silently dropped.
        val response =
            solanaResponse(
                """{
                    "result": {
                      "simulation": {
                        "account_summary": {
                          "account_assets_diff": [
                            {
                              "asset": { "decimals": 9, "address": null },
                              "asset_type": "SOL",
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

        assertEquals("So11111111111111111111111111111111111111112", transfer.fromCoin.address)
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
    fun `parseRawAmount rejects signed and malformed inputs`() {
        // Raw amounts on chain are unsigned. A negative or sign-prefixed input from a hostile
        // Blockaid response would otherwise round-trip as |value| through `signum()` checks at
        // the call site; reject up-front so the only path to a non-null result is a clean
        // unsigned literal.
        assertNull(BlockaidSimulationParser.parseRawAmount("-1"))
        assertNull(BlockaidSimulationParser.parseRawAmount("+1"))
        assertNull(BlockaidSimulationParser.parseRawAmount("-0xff"))
        // `0x-1` slips past the leading-char guard (the `-` is at index 2, after the prefix is
        // stripped). The final `signum() >= 0` check is what catches it. Locks in that the
        // belt-and-braces guard is still load-bearing — deleting it would let this case through.
        assertNull(BlockaidSimulationParser.parseRawAmount("0x-1"))
        assertNull(BlockaidSimulationParser.parseRawAmount("-0"))
        // Bare `0x` after stripping leaves an empty hex which BigInteger throws on.
        assertNull(BlockaidSimulationParser.parseRawAmount("0x"))
        // Whitespace is trimmed first, so trimming reveals the leading sign.
        assertNull(BlockaidSimulationParser.parseRawAmount("  -1  "))
        // No scientific notation, no underscores, no full-width / unicode digits.
        assertNull(BlockaidSimulationParser.parseRawAmount("1e10"))
        assertNull(BlockaidSimulationParser.parseRawAmount("1_000"))
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

    @Test
    fun `evm multi-hop swap selects the user's terminal received token`() {
        // Three diffs: ETH out (user pays), USDC intermediate (router leg with both in and
        // out), DAI in (user receives). The parser must pick DAI as the destination, NOT the
        // intermediate USDC leg — otherwise the hero shows a misleading "ETH → USDC" swap.
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
                          "logo_url": "https://logo/eth.png"
                        },
                        "out": [{ "raw_value": "0xde0b6b3a7640000" }]
                      },
                      {
                        "asset": {
                          "type": "ERC20",
                          "address": "0xUSDC",
                          "symbol": "USDC",
                          "decimals": 6,
                          "logo_url": "https://logo/usdc.png"
                        },
                        "in": [{ "raw_value": "0x77359400" }],
                        "out": [{ "raw_value": "0x77359400" }]
                      },
                      {
                        "asset": {
                          "type": "ERC20",
                          "address": "0xDAI",
                          "symbol": "DAI",
                          "decimals": 18,
                          "logo_url": "https://logo/dai.png"
                        },
                        "in": [{ "raw_value": "0x29a2241af62c0000" }]
                      }
                    ]
                  }
                }
              }"""
                    .trimIndent()
            )

        val swap =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Swap

        assertEquals("ETH", swap.fromCoin.ticker)
        assertEquals("DAI", swap.toCoin.ticker)
    }

    @Test
    fun `evm same-asset swap with mixed-case symbol is collapsed`() {
        // "USDC" out + "usdc" in with the same address must collapse to null (transfer noise),
        // not emit a bogus swap. Without case-insensitive symbol comparison this would slip
        // through.
        val response =
            evmResponse(
                """{
                "simulation": {
                  "account_summary": {
                    "assets_diffs": [
                      {
                        "asset": {
                          "type": "ERC20",
                          "address": "0xUSDC",
                          "symbol": "USDC",
                          "decimals": 6,
                          "logo_url": "https://logo/usdc.png"
                        },
                        "out": [{ "raw_value": "0x5f5e100" }]
                      },
                      {
                        "asset": {
                          "type": "ERC20",
                          "address": "0xUSDC",
                          "symbol": "usdc",
                          "decimals": 6,
                          "logo_url": "https://logo/usdc.png"
                        },
                        "in": [{ "raw_value": "0x5f5e100" }]
                      }
                    ]
                  }
                }
              }"""
                    .trimIndent()
            )

        assertNull(BlockaidSimulationParser.parseEvm(response, Chain.Ethereum))
    }

    @Test
    fun `evm same-asset native ETH using zero-sentinel address collapses with null variant`() {
        // Defends against a hostile response that mixes native-ETH conventions: one diff carries
        // `address: null` (the parser's preferred form) and the other carries the all-zeros
        // sentinel. Both refer to native ETH and must collapse to a null result, not a swap.
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
                          "type": "ETH",
                          "address": "0x0000000000000000000000000000000000000000",
                          "symbol": "ETH",
                          "decimals": 18,
                          "logo_url": ""
                        },
                        "in": [{ "raw_value": "0xde0b6b3a7640000" }]
                      }
                    ]
                  }
                }
              }"""
                    .trimIndent()
            )

        assertNull(BlockaidSimulationParser.parseEvm(response, Chain.Ethereum))
    }

    @Test
    fun `evm transfer with zero-width joiner in symbol strips it`() {
        // U+200D (ZERO WIDTH JOINER) is invisible but participates in emoji sequencing; in a
        // ticker it is a layout-only artifact that must be stripped to keep visual identity
        // matching the byte identity.
        val response = evmResponse(evmTransferJson(symbol = "USD‌C"))

        val transfer =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Transfer

        assertEquals("USDC", transfer.fromCoin.ticker)
    }

    @Test
    fun `evm transfer with word joiner U+2060 in symbol strips it`() {
        // U+2060 (WORD JOINER) is a zero-width line-break inhibitor; same display-attack profile
        // as the bidi/joiner family and must be stripped from tickers and function names.
        val response = evmResponse(evmTransferJson(symbol = "USD⁠C"))

        val transfer =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Transfer

        assertEquals("USDC", transfer.fromCoin.ticker)
    }

    @Test
    fun `evm transfer with variation selector in symbol strips it`() {
        // U+FE0F (VS-16) toggles a base codepoint to its emoji presentation. A hostile ticker
        // could use it to morph an ASCII letter into a different glyph; the parser must drop
        // these so the ticker renders deterministically.
        val response = evmResponse(evmTransferJson(symbol = "USD️C"))

        val transfer =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Transfer

        assertEquals("USDC", transfer.fromCoin.ticker)
    }

    @Test
    fun `evm transfer with tag codepoint in symbol strips it`() {
        // Plane-14 tag codepoints (U+E0000..E007F) render as nothing on most platforms but ride
        // along inside the string. Iterating by codepoint is required — the surrogate-pair
        // representation would slip past a Char-based filter.
        val tagSpace = String(Character.toChars(0xE0020)) // TAG SPACE
        val response = evmResponse(evmTransferJson(symbol = "USD${tagSpace}C"))

        val transfer =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Transfer

        assertEquals("USDC", transfer.fromCoin.ticker)
    }

    @Test
    fun `evm transfer with bidi padding longer than scan cap drops the ticker`() {
        // 200 zero-width spaces exhaust `MAX_TICKER_SCANNED_CODEPOINTS = 128` before any
        // legitimate codepoint is reached; the result is null, which the parser propagates as
        // "skip this transfer" rather than rendering an empty ticker.
        val response = evmResponse(evmTransferJson(symbol = "​".repeat(200)))

        // No legitimate codepoints survive the scan-cap, so the parser drops the transfer rather
        // than yielding a bogus zero-width ticker.
        assertNull(BlockaidSimulationParser.parseEvm(response, Chain.Ethereum))
    }

    @Test
    fun `evm transfer ticker truncation is codepoint-aware for emoji`() {
        // "ABCDEFGHIJK" + 🦄 (U+1F984, two UTF-16 char units) = 11 ASCII + 1 emoji codepoint.
        // The parser caps at 12 codepoints and must keep the emoji intact rather than splitting
        // its surrogate pair (which would produce malformed UTF-16).
        val response = evmResponse(evmTransferJson(symbol = "ABCDEFGHIJK🦄"))

        val transfer =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Transfer

        assertEquals("ABCDEFGHIJK🦄", transfer.fromCoin.ticker)
    }

    @Test
    fun `evm transfer with logo url carrying userinfo is rejected`() {
        // "https://attacker.com@trusted.example/logo.png" — a phishing primitive that bypasses
        // pure-prefix scheme validation. The sanitiser must reject it.
        val response =
            evmResponse(evmTransferJson(logoUrl = "https://attacker.com@trusted.example/u.png"))

        val transfer =
            BlockaidSimulationParser.parseEvm(response, Chain.Ethereum)
                as BlockaidSimulationInfo.Transfer

        assertEquals("", transfer.fromCoin.logo)
    }

    @Test
    fun `solana multi-recipient send aggregates same-asset outgoing diffs`() {
        // Two recipients, same asset, no incoming side. The parser must aggregate the amounts
        // (100 + 50 = 150 USDC) instead of silently truncating to the first leg's 100 USDC.
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
                            "symbol": "USDC",
                            "address": "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                            "decimals": 6,
                            "logo": "https://logo/usdc.png"
                          },
                          "out": { "raw_value": 100000000 }
                        },
                        {
                          "asset": {
                            "type": "TOKEN",
                            "symbol": "USDC",
                            "address": "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                            "decimals": 6,
                            "logo": "https://logo/usdc.png"
                          },
                          "out": { "raw_value": 50000000 }
                        }
                      ]
                    }
                  }
                }
              }"""
                    .trimIndent()
            )

        val transfer =
            BlockaidSimulationParser.parseSolana(response) as BlockaidSimulationInfo.Transfer

        assertEquals("USDC", transfer.fromCoin.ticker)
        assertEquals(BigInteger("150000000"), transfer.fromAmount)
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
