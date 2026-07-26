package com.vultisig.wallet.data.crypto

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SuiCoin

/**
 * Pins the deterministic coin-object selection [SuiHelper.selectInputCoins] /
 * [SuiHelper.selectPayloadCoins] that keeps Android byte-identical to iOS (vultisig-ios#4734).
 *
 * In an MPC keysign every co-signing device independently recomputes the Sui transaction from the
 * shared `KeysignPayload`; all devices must select the *same* coin objects in the *same* order or
 * the signatures don't combine. Referencing every owned object also overflows Sui's 128 KiB size /
 * 256-gas-object limits on wallets whose balance is scattered across many objects, so selection is
 * both a consensus and a correctness requirement.
 *
 * The fixtures mirror iOS `SuiHelperInputDataTests.swift` at the selection level (the
 * byte-identical `Sui.SigningInput` assertions live in the instrumented `SuiHelperInputDataTest`,
 * which needs the WalletCore JNI).
 */
class SuiCoinSelectionTest {

    private val nativeType = "0x2::sui::SUI"
    private val tokenType =
        "0x5d4b302506645c37ff133b98c4b50a5ae14841659738d6d733d59d0d217a93bf::coin::COIN"

    private fun coin(id: String, type: String, balance: String, version: String = "1"): SuiCoin =
        SuiCoin(
            coinType = type,
            coinObjectId = id,
            version = version,
            digest = "digest-$id",
            balance = balance,
            previousTransaction = "",
        )

    private fun ids(coins: List<SuiCoin>): List<String> = coins.map { it.coinObjectId }

    // Native send: cover amount + gas with the fewest largest objects.

    @Test
    fun `native send selects the largest objects covering amount plus gas`() {
        val coins =
            listOf(
                coin("0xa", nativeType, "1000000000"),
                coin("0xb", nativeType, "2000000000"),
                coin("0xc", nativeType, "3000000000"),
            )
        // amount 4 SUI + gas: the two largest (3 + 2) cover it, the smallest is left out.
        val selected =
            SuiHelper.selectInputCoins(
                coins,
                target = BigInteger.valueOf(4_000_000_000L) + BigInteger.valueOf(3_000_000L),
            )

        assertEquals(listOf("0xc", "0xb"), ids(selected))
    }

    @Test
    fun `native small send selects only the largest object`() {
        val coins =
            listOf(
                coin("0xa", nativeType, "1000000000"),
                coin("0xb", nativeType, "2000000000"),
                coin("0xc", nativeType, "3000000000"),
            )
        val selected =
            SuiHelper.selectInputCoins(
                coins,
                target = BigInteger.valueOf(100_000_000L) + BigInteger.valueOf(3_000_000L),
            )

        assertEquals(listOf("0xc"), ids(selected))
    }

    @Test
    fun `native near-max send selects all objects`() {
        val coins =
            listOf(
                coin("0xa", nativeType, "1000000000"),
                coin("0xb", nativeType, "2000000000"),
                coin("0xc", nativeType, "3000000000"),
            )
        val selected =
            SuiHelper.selectInputCoins(
                coins,
                target = BigInteger.valueOf(5_900_000_000L) + BigInteger.valueOf(3_000_000L),
            )

        assertEquals(listOf("0xc", "0xb", "0xa"), ids(selected))
    }

    @Test
    fun `dusty wallet references only the objects the send needs`() {
        val coins = buildList {
            add(coin("0xbig", nativeType, "10000000000"))
            repeat(800) { add(coin("0xdust$it", nativeType, "1000")) }
        }
        val selected =
            SuiHelper.selectInputCoins(
                coins,
                target = BigInteger.valueOf(1_000_000_000L) + BigInteger.valueOf(3_000_000L),
            )

        assertEquals(listOf("0xbig"), ids(selected))
        assertTrue(selected.size <= SuiHelper.MAX_INPUT_COIN_OBJECTS)
    }

    @Test
    fun `selection never exceeds the max object cap even when underfunded`() {
        // 300 tiny objects can never cover the target; selection stops at the 255 cap.
        val coins = List(300) { coin("0x${it.toString().padStart(4, '0')}", nativeType, "1") }
        val selected = SuiHelper.selectInputCoins(coins, target = BigInteger.valueOf(1_000_000L))

        assertEquals(SuiHelper.MAX_INPUT_COIN_OBJECTS, selected.size)
    }

    @Test
    fun `equal balances break ties on objectId ascending for a stable order`() {
        val coins =
            listOf(
                coin("0xc", nativeType, "1000"),
                coin("0xa", nativeType, "1000"),
                coin("0xb", nativeType, "1000"),
            )
        val selected = SuiHelper.selectInputCoins(coins, target = BigInteger.valueOf(2500L))

        assertEquals(listOf("0xa", "0xb", "0xc"), ids(selected))
    }

    @Test
    fun `always keeps at least one object for a zero-amount send`() {
        val coins = listOf(coin("0xa", nativeType, "1000"), coin("0xb", nativeType, "2000"))
        val selected = SuiHelper.selectInputCoins(coins, target = BigInteger.ZERO)

        assertEquals(listOf("0xb"), ids(selected))
    }

    // Token send: cover token amount, plus the largest few native SUI as gas candidates.

    @Test
    fun `token send covers the amount with the fewest largest token objects`() {
        val tokens =
            listOf(
                coin("0xt1", tokenType, "100"),
                coin("0xt2", tokenType, "200"),
                coin("0xt3", tokenType, "300"),
            )
        val selected = SuiHelper.selectInputCoins(tokens, target = BigInteger.valueOf(550L))

        assertEquals(listOf("0xt3", "0xt2", "0xt1"), ids(selected))
    }

    @Test
    fun `token small send selects only the largest token object`() {
        val tokens =
            listOf(
                coin("0xt1", tokenType, "100"),
                coin("0xt2", tokenType, "200"),
                coin("0xt3", tokenType, "300"),
            )
        val selected = SuiHelper.selectInputCoins(tokens, target = BigInteger.valueOf(250L))

        assertEquals(listOf("0xt3"), ids(selected))
    }

    // Payload bounding embeds only what the send consumes.

    @Test
    fun `payload coins for a native send are the covering native objects only`() {
        val coins =
            listOf(
                coin("0xa", nativeType, "1000000000"),
                coin("0xb", nativeType, "2000000000"),
                coin("0xc", nativeType, "3000000000"),
            )
        val selected =
            SuiHelper.selectPayloadCoins(
                coins,
                isNativeToken = true,
                contractAddress = "",
                amount = BigInteger.valueOf(100_000_000L),
                gasBudget = BigInteger.valueOf(3_000_000L),
            )

        assertEquals(listOf("0xc"), ids(selected))
    }

    @Test
    fun `payload native selection excludes look-alike objects`() {
        val coins =
            listOf(
                coin("0xnative", nativeType, "5000000000"),
                coin("0xlst", "0xb45f::xsui::XSUI", "9000000000"),
            )
        val selected =
            SuiHelper.selectPayloadCoins(
                coins,
                isNativeToken = true,
                contractAddress = "",
                amount = BigInteger.valueOf(1_000_000_000L),
                gasBudget = BigInteger.valueOf(3_000_000L),
            )

        assertEquals(listOf("0xnative"), ids(selected))
    }

    @Test
    fun `payload coins for a token send are covering tokens plus the largest gas candidates`() {
        val coins =
            listOf(
                coin("0xgasSmall", nativeType, "500000"),
                coin("0xgasMid", nativeType, "3000000"),
                coin("0xgasBig", nativeType, "9000000"),
                coin("0xt1", tokenType, "100"),
                coin("0xt2", tokenType, "200"),
            )
        val selected =
            SuiHelper.selectPayloadCoins(
                coins,
                isNativeToken = false,
                contractAddress = tokenType,
                amount = BigInteger.valueOf(250L),
                gasBudget = BigInteger.valueOf(3_000_000L),
            )

        // Covering tokens (200 + 100) then the native gas candidates, largest first.
        assertEquals(listOf("0xt2", "0xt1", "0xgasBig", "0xgasMid", "0xgasSmall"), ids(selected))
    }

    @Test
    fun `payload token send caps gas candidates at the configured count`() {
        val coins = buildList {
            repeat(8) { add(coin("0xgas$it", nativeType, "${9000000 - it}")) }
            add(coin("0xt1", tokenType, "1000"))
        }
        val selected =
            SuiHelper.selectPayloadCoins(
                coins,
                isNativeToken = false,
                contractAddress = tokenType,
                amount = BigInteger.valueOf(500L),
                gasBudget = BigInteger.valueOf(3_000_000L),
            )

        val gasCandidates = selected.filter { it.coinType == nativeType }
        assertEquals(SuiHelper.GAS_CANDIDATE_OBJECT_COUNT, gasCandidates.size)
        // The five largest native objects, largest first.
        assertEquals(
            listOf("0xgas0", "0xgas1", "0xgas2", "0xgas3", "0xgas4"),
            gasCandidates.map { it.coinObjectId },
        )
    }

    @Test
    fun `payload token gas candidates break balance ties on objectId ascending`() {
        val coins =
            listOf(
                coin("0xgasD", nativeType, "9000000"),
                coin("0xgasB", nativeType, "9000000"),
                coin("0xgasC", nativeType, "9000000"),
                coin("0xgasA", nativeType, "9000000"),
                coin("0xt1", tokenType, "1000"),
            )
        val selected =
            SuiHelper.selectPayloadCoins(
                coins,
                isNativeToken = false,
                contractAddress = tokenType,
                amount = BigInteger.valueOf(500L),
                gasBudget = BigInteger.valueOf(3_000_000L),
            )

        assertEquals(listOf("0xt1", "0xgasA", "0xgasB", "0xgasC", "0xgasD"), ids(selected))
    }

    // Coin-type normalization: short vs long address form must classify identically.

    @Test
    fun `long-form native coin type is treated as native`() {
        val longNativeType = "0x" + "0".repeat(63) + "2::sui::SUI"
        assertTrue(SuiHelper.isSameSuiCoinType(nativeType, longNativeType))
    }

    @Test
    fun `coin type comparison keeps module and struct identifiers case-sensitive`() {
        val lowerStruct =
            "0x5d4b302506645c37ff133b98c4b50a5ae14841659738d6d733d59d0d217a93bf::coin::coin"
        assertFalse(SuiHelper.isSameSuiCoinType(tokenType, lowerStruct))
    }

    @Test
    fun `coin type comparison ignores address case and leading zeros`() {
        val padded =
            "0x005D4B302506645C37FF133B98C4B50A5AE14841659738D6D733D59D0D217A93BF::coin::COIN"
        assertTrue(SuiHelper.isSameSuiCoinType(tokenType, padded))
    }

    @Test
    fun `native selection matches long-form objects returned by the RPC`() {
        val longNativeType = "0x" + "0".repeat(63) + "2::sui::SUI"
        val coins =
            listOf(
                coin("0xa", longNativeType, "1000000000"),
                coin("0xb", longNativeType, "5000000000"),
            )
        val selected =
            SuiHelper.selectPayloadCoins(
                coins,
                isNativeToken = true,
                contractAddress = "",
                amount = BigInteger.valueOf(100_000_000L),
                gasBudget = BigInteger.valueOf(3_000_000L),
            )

        assertEquals(listOf("0xb"), ids(selected))
    }

    // Gas-object pick: smallest covering object, objectId ascending as tie-break.

    @Test
    fun `gas coin is the smallest native object covering the budget`() {
        val coins =
            listOf(
                coin("0xtooSmall", nativeType, "1000000"),
                coin("0xbig", nativeType, "9000000"),
                coin("0xsmallCover", nativeType, "3500000"),
            )
        val selected = SuiHelper.selectSuiGasCoin(coins, BigInteger.valueOf(3_000_000L))

        assertEquals("0xsmallCover", selected?.coinObjectId)
    }

    @Test
    fun `gas coin breaks balance ties on objectId ascending`() {
        // Listed in an order whose first minimum is NOT the lowest objectId: a device that skipped
        // the tie-break would pick 0xbbb here and diverge from iOS / the SDK.
        val coins =
            listOf(
                coin("0xbbb", nativeType, "3500000"),
                coin("0xaaa", nativeType, "3500000"),
                coin("0xccc", nativeType, "9000000"),
            )
        val selected = SuiHelper.selectSuiGasCoin(coins, BigInteger.valueOf(3_000_000L))

        assertEquals("0xaaa", selected?.coinObjectId)
    }

    @Test
    fun `gas coin ignores non-native objects and returns null when none covers the budget`() {
        val coins = listOf(coin("0xt1", tokenType, "9000000"), coin("0xa", nativeType, "1000000"))

        assertNull(SuiHelper.selectSuiGasCoin(coins, BigInteger.valueOf(3_000_000L)))
    }
}
