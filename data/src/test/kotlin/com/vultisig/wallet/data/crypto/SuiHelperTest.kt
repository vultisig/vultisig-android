package com.vultisig.wallet.data.crypto

import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SuiCoin

/**
 * Covers [SuiHelper.selectSuiGasCoin] with exact `suix_getAllCoins` RPC response payloads to
 * protect the #3989 fix (filter by `balance >= gasBudget`, not `balance > referenceGasPrice`).
 */
class SuiHelperTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Real-shape suix_getAllCoins response. Three SUI coins of different balances and one
    // non-SUI coin, so we can exercise eligibility + min-selection + non-SUI exclusion.
    private val getAllCoinsResponse =
        """
        {
          "jsonrpc": "2.0",
          "result": {
            "data": [
              {
                "coinType": "0x2::sui::SUI",
                "coinObjectId": "0x0000000000000000000000000000000000000000000000000000000000000001",
                "version": "100",
                "digest": "DGstCoinOneXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
                "balance": "600",
                "previousTransaction": "PrevTx1"
              },
              {
                "coinType": "0x2::sui::SUI",
                "coinObjectId": "0x0000000000000000000000000000000000000000000000000000000000000002",
                "version": "101",
                "digest": "DGstCoinTwoXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
                "balance": "3000000",
                "previousTransaction": "PrevTx2"
              },
              {
                "coinType": "0x2::sui::SUI",
                "coinObjectId": "0x0000000000000000000000000000000000000000000000000000000000000003",
                "version": "102",
                "digest": "DGstCoinThreeXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
                "balance": "10000000000",
                "previousTransaction": "PrevTx3"
              },
              {
                "coinType": "0xabc::usdc::USDC",
                "coinObjectId": "0x0000000000000000000000000000000000000000000000000000000000000099",
                "version": "200",
                "digest": "DGstUsdcXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
                "balance": "999999999999",
                "previousTransaction": "PrevTx99"
              }
            ],
            "nextCursor": null,
            "hasNextPage": false
          },
          "id": 1
        }
        """
            .trimIndent()

    @Test
    fun `selectSuiGasCoin picks smallest SUI coin that covers gasBudget`() {
        val coins = parseCoins(getAllCoinsResponse)
        val gasBudget = "3000000".toBigInteger()

        val selected = SuiHelper.selectSuiGasCoin(coins, gasBudget)

        // balance=600 is too small (the old bug had it pass because 600 > refGasPrice=500);
        // 3000000 meets the budget exactly and is the smallest eligible coin.
        assertEquals(
            "0x0000000000000000000000000000000000000000000000000000000000000002",
            selected?.coinObjectId,
        )
        assertEquals("3000000", selected?.balance)
    }

    @Test
    fun `selectSuiGasCoin rejects coin that beats referenceGasPrice but not gasBudget`() {
        // Regression for #3989: balance=600 > referenceGasPrice=500 but < gasBudget=3000000.
        // The pre-fix code would have returned this coin; the post-fix code must skip it
        // and fall through to the next eligible coin (or null if none exists).
        val coins =
            parseCoins(getAllCoinsResponse).filter {
                it.balance.toBigInteger() < 1_000_000.toBigInteger()
            }
        val gasBudget = "3000000".toBigInteger()

        val selected = SuiHelper.selectSuiGasCoin(coins, gasBudget)

        assertNull(selected)
    }

    @Test
    fun `selectSuiGasCoin returns null when no SUI coin can cover gasBudget`() {
        val coins = parseCoins(getAllCoinsResponse)
        val gasBudget = "100000000000".toBigInteger() // 100 SUI, larger than any balance

        val selected = SuiHelper.selectSuiGasCoin(coins, gasBudget)

        assertNull(selected)
    }

    @Test
    fun `selectSuiGasCoin ignores non-SUI coins even when balance exceeds gasBudget`() {
        // The USDC coin has a huge balance but is not 0x2::sui::SUI and must not be picked.
        val nonSuiOnly = parseCoins(getAllCoinsResponse).filter { it.coinType != "0x2::sui::SUI" }
        val gasBudget = "3000000".toBigInteger()

        val selected = SuiHelper.selectSuiGasCoin(nonSuiOnly, gasBudget)

        assertNull(selected)
    }

    @Test
    fun `selectSuiGasCoin picks largest when only the top balance is eligible`() {
        val coins = parseCoins(getAllCoinsResponse)
        val gasBudget = "5000000".toBigInteger() // only the 10 SUI coin qualifies

        val selected = SuiHelper.selectSuiGasCoin(coins, gasBudget)

        assertEquals(
            "0x0000000000000000000000000000000000000000000000000000000000000003",
            selected?.coinObjectId,
        )
    }

    @Test
    fun `selectSuiGasCoin accepts coin whose balance equals gasBudget exactly`() {
        val coins = parseCoins(getAllCoinsResponse)
        val gasBudget = BigInteger("3000000")

        val selected = SuiHelper.selectSuiGasCoin(coins, gasBudget)

        assertEquals("3000000", selected?.balance)
    }

    /** Mirrors the parsing in `SuiApiImpl.getAllCoins` so tests consume the real RPC shape. */
    private fun parseCoins(rpcResponse: String): List<SuiCoin> =
        json
            .parseToJsonElement(rpcResponse)
            .jsonObject["result"]!!
            .jsonObject["data"]!!
            .jsonArray
            .map {
                SuiCoin(
                    coinType = it.jsonObject["coinType"]!!.jsonPrimitive.content,
                    coinObjectId = it.jsonObject["coinObjectId"]!!.jsonPrimitive.content,
                    version = it.jsonObject["version"]!!.jsonPrimitive.content,
                    digest = it.jsonObject["digest"]!!.jsonPrimitive.content,
                    balance = it.jsonObject["balance"]!!.jsonPrimitive.content,
                    previousTransaction =
                        it.jsonObject["previousTransaction"]?.jsonPrimitive?.content ?: "",
                )
            }
}
