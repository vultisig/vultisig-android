package com.vultisig.wallet.data.crypto

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import vultisig.keysign.v1.SuiCoin
import wallet.core.jni.proto.Sui

/**
 * Byte-level cross-device parity for Sui coin-object selection (issue #5252 / vultisig-ios#4734).
 *
 * These mirror iOS `SuiHelperInputDataTests.swift` fixture-for-fixture: the same owned-coins sets
 * must produce the same `Sui.SigningInput` — the objects a send references and the gas object it
 * pays from — so an Android and an iOS co-signer building the transaction from the shared payload
 * converge on identical bytes. Runs instrumented because [SuiHelper.getPreSignedInputData] resolves
 * the recipient through WalletCore's `AnyAddress` JNI.
 *
 * The pure-JVM selection-algorithm coverage lives in `SuiCoinSelectionTest`.
 */
class SuiHelperInputDataTest {

    private val nativeType = "0x2::sui::SUI"
    private val tokenType =
        "0x5d4b302506645c37ff133b98c4b50a5ae14841659738d6d733d59d0d217a93bf::coin::COIN"
    private val address = "0x9a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef"
    private val hexPublicKey = "023e4b76861289ad4528b33c2fd21b3a5160cd37b3294234914e21efb6ed4a452b"

    private fun coinObject(id: String, type: String, balance: String, version: String = "1") =
        SuiCoin(
            coinType = type,
            coinObjectId = id,
            version = version,
            digest = "digest-$id",
            balance = balance,
            previousTransaction = "",
        )

    private fun suiCoin(isNative: Boolean) =
        Coin(
            chain = Chain.Sui,
            ticker = if (isNative) "SUI" else "COIN",
            logo = "sui",
            address = address,
            decimal = if (isNative) 9 else 8,
            hexPublicKey = hexPublicKey,
            priceProviderID = "sui",
            contractAddress = if (isNative) "" else tokenType,
            isNativeToken = isNative,
        )

    private fun payload(
        coin: Coin,
        coins: List<SuiCoin>,
        amount: BigInteger,
        gasBudget: BigInteger,
    ) =
        KeysignPayload(
            coin = coin,
            toAddress = address,
            toAmount = amount,
            blockChainSpecific =
                BlockChainSpecific.Sui(
                    referenceGasPrice = BigInteger.valueOf(1000),
                    coins = coins,
                    gasBudget = gasBudget,
                ),
            vaultPublicKeyECDSA = hexPublicKey,
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private fun signingInput(payload: KeysignPayload): Sui.SigningInput =
        Sui.SigningInput.parseFrom(SuiHelper.getPreSignedInputData(payload))

    // Native send merges the objects it needs.

    @Test
    fun nativeSendSelectsLargestObjectsCoveringAmountPlusGas() {
        val coins =
            listOf(
                coinObject("0xa", nativeType, "1000000000"),
                coinObject("0xb", nativeType, "2000000000"),
                coinObject("0xc", nativeType, "3000000000"),
            )
        val input =
            signingInput(
                payload(
                    suiCoin(true),
                    coins,
                    BigInteger.valueOf(4_000_000_000L),
                    BigInteger.valueOf(3_000_000L),
                )
            )

        assertEquals(Sui.SigningInput.TransactionPayloadCase.PAY_SUI, input.transactionPayloadCase)
        assertEquals(listOf("0xc", "0xb"), input.paySui.inputCoinsList.map { it.objectId })
        assertEquals(listOf(4_000_000_000L), input.paySui.amountsList)
    }

    @Test
    fun nativeSmallSendSelectsOnlyLargestObject() {
        val coins =
            listOf(
                coinObject("0xa", nativeType, "1000000000"),
                coinObject("0xb", nativeType, "2000000000"),
                coinObject("0xc", nativeType, "3000000000"),
            )
        val input =
            signingInput(
                payload(
                    suiCoin(true),
                    coins,
                    BigInteger.valueOf(100_000_000L),
                    BigInteger.valueOf(3_000_000L),
                )
            )

        assertEquals(listOf("0xc"), input.paySui.inputCoinsList.map { it.objectId })
    }

    @Test
    fun nativeNearMaxSendSelectsAllObjects() {
        val coins =
            listOf(
                coinObject("0xa", nativeType, "1000000000"),
                coinObject("0xb", nativeType, "2000000000"),
                coinObject("0xc", nativeType, "3000000000"),
            )
        val input =
            signingInput(
                payload(
                    suiCoin(true),
                    coins,
                    BigInteger.valueOf(5_900_000_000L),
                    BigInteger.valueOf(3_000_000L),
                )
            )

        assertEquals(listOf("0xc", "0xb", "0xa"), input.paySui.inputCoinsList.map { it.objectId })
    }

    @Test
    fun nativeDustyWalletReferencesOnlyNeededObjects() {
        val coins = buildList {
            add(coinObject("0xbig", nativeType, "10000000000"))
            repeat(800) { add(coinObject("0xdust$it", nativeType, "1000")) }
        }
        val input =
            signingInput(
                payload(
                    suiCoin(true),
                    coins,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(3_000_000L),
                )
            )

        assertEquals(listOf("0xbig"), input.paySui.inputCoinsList.map { it.objectId })
        assertTrue(input.paySui.inputCoinsCount <= SuiHelper.MAX_INPUT_COIN_OBJECTS)
    }

    @Test
    fun nativeSendExcludesLookAlikeObjects() {
        val coins =
            listOf(
                coinObject("0xnative", nativeType, "5000000000"),
                coinObject("0xlst", "0xb45f::xsui::XSUI", "9000000000"),
            )
        val input =
            signingInput(
                payload(
                    suiCoin(true),
                    coins,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(3_000_000L),
                )
            )

        assertEquals(listOf("0xnative"), input.paySui.inputCoinsList.map { it.objectId })
    }

    // Token send selects a covering gas object.

    @Test
    fun tokenSendSelectsSmallestCoveringGasObject() {
        val coins =
            listOf(
                coinObject("0xgasTooSmall", nativeType, "500000"),
                coinObject("0xgasCovers", nativeType, "3000000"),
                coinObject("0xgasBig", nativeType, "9000000"),
                coinObject("0xtoken1", tokenType, "100"),
                coinObject("0xtoken2", tokenType, "200"),
            )
        val input =
            signingInput(
                payload(
                    suiCoin(false),
                    coins,
                    BigInteger.valueOf(250),
                    BigInteger.valueOf(3_000_000L),
                )
            )

        assertEquals(Sui.SigningInput.TransactionPayloadCase.PAY, input.transactionPayloadCase)
        assertEquals(
            setOf("0xtoken1", "0xtoken2"),
            input.pay.inputCoinsList.map { it.objectId }.toSet(),
        )
        assertEquals("0xgasCovers", input.pay.gas.objectId)
    }

    @Test
    fun tokenSendSelectsCoveringTokenObjects() {
        val coins =
            listOf(
                coinObject("0xgas", nativeType, "5000000"),
                coinObject("0xt1", tokenType, "100"),
                coinObject("0xt2", tokenType, "200"),
                coinObject("0xt3", tokenType, "300"),
            )
        val input =
            signingInput(
                payload(
                    suiCoin(false),
                    coins,
                    BigInteger.valueOf(550),
                    BigInteger.valueOf(3_000_000L),
                )
            )

        assertEquals(
            setOf("0xt1", "0xt2", "0xt3"),
            input.pay.inputCoinsList.map { it.objectId }.toSet(),
        )
        assertEquals("0xgas", input.pay.gas.objectId)
    }

    // A token send whose token objects are missing (e.g. a truncated coin page) must fail loudly,
    // never fall through to a native PaySui transfer of the raw amount as a different asset.

    @Test
    fun tokenSendWithoutTokenObjectsFailsLoudly() {
        val coins =
            listOf(
                coinObject("0xgas1", nativeType, "5000000"),
                coinObject("0xgas2", nativeType, "9000000"),
            )

        assertThrows(IllegalStateException::class.java) {
            signingInput(
                payload(
                    suiCoin(false),
                    coins,
                    BigInteger.valueOf(250),
                    BigInteger.valueOf(3_000_000L),
                )
            )
        }
    }

    // An under-funded selection is rejected at signing rather than emitting a transaction that only
    // fails at broadcast after the full multi-device ceremony.

    @Test
    fun nativeSendRejectsUnderfundedSelection() {
        val coins =
            listOf(
                coinObject("0xa", nativeType, "1000000000"),
                coinObject("0xb", nativeType, "2000000000"),
            )

        assertThrows(IllegalStateException::class.java) {
            signingInput(
                payload(
                    suiCoin(true),
                    coins,
                    BigInteger.valueOf(4_000_000_000L),
                    BigInteger.valueOf(3_000_000L),
                )
            )
        }
    }

    @Test
    fun tokenSendRejectsUnderfundedTokenSelection() {
        val coins =
            listOf(
                coinObject("0xgas", nativeType, "9000000"),
                coinObject("0xt1", tokenType, "100"),
                coinObject("0xt2", tokenType, "200"),
            )

        assertThrows(IllegalStateException::class.java) {
            signingInput(
                payload(
                    suiCoin(false),
                    coins,
                    BigInteger.valueOf(1000),
                    BigInteger.valueOf(3_000_000L),
                )
            )
        }
    }

    @Test
    fun tokenSmallSendSelectsOnlyLargestTokenObject() {
        val coins =
            listOf(
                coinObject("0xgas", nativeType, "5000000"),
                coinObject("0xt1", tokenType, "100"),
                coinObject("0xt2", tokenType, "200"),
                coinObject("0xt3", tokenType, "300"),
            )
        val input =
            signingInput(
                payload(
                    suiCoin(false),
                    coins,
                    BigInteger.valueOf(250),
                    BigInteger.valueOf(3_000_000L),
                )
            )

        assertEquals(listOf("0xt3"), input.pay.inputCoinsList.map { it.objectId })
        assertEquals("0xgas", input.pay.gas.objectId)
    }
}
