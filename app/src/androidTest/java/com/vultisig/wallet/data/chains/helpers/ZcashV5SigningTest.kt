package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tss.KeysignResponse
import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey
import wallet.core.jni.proto.Bitcoin

/**
 * The Zcash v5 golden vectors (`/zcash/zcash-v5-golden.json`) through [UtxoHelper] with real
 * WalletCore: address scripts, RFC6979 signing, DER verification, and for the first fixture the
 * planner and branch-id stamping of a native send. Instrumented because all of those are JNI.
 */
class ZcashV5SigningTest {

    private val fixtures: List<JsonObject> =
        Json.parseToJsonElement(
                requireNotNull(javaClass.getResourceAsStream("/zcash/zcash-v5-golden.json")) {
                        "Missing test resource /zcash/zcash-v5-golden.json"
                    }
                    .bufferedReader()
                    .readText()
            )
            .jsonObject
            .getValue("fixtures")
            .jsonArray
            .map { it.jsonObject }

    @Test
    fun everyFixtureCompilesToItsRawBytesAndTxid() {
        fixtures.forEach { fixture ->
            val name = fixture.string("name")
            val result =
                helper(fixture)
                    .getZcashSignedTransaction(
                        signingInput(fixture).toByteArray(),
                        signedSighashes(fixture),
                    )

            assertEquals("$name raw", fixture.expected.string("raw"), result.rawTransaction)
            assertEquals("$name txid", fixture.expected.string("txid"), result.transactionHash)
        }
    }

    @Test
    fun nativeSendHashesAndCompilesTheFirstFixture() {
        val fixture = fixtures.single { it.string("name") == "one_input_with_change_nu63" }
        val payload = nativeSendPayload(fixture)

        assertEquals(
            fixture.expected.getValue("sighashes").strings(),
            helper(fixture).getPreSignedImageHash(payload),
        )
        val result = helper(fixture).getSignedTransaction(payload, signedSighashes(fixture))
        assertEquals(fixture.expected.string("raw"), result.rawTransaction)
        assertEquals(fixture.expected.string("txid"), result.transactionHash)
    }

    // A THORChain/Maya ZEC swap is a native send carrying its memo; the memo reaches the v5
    // transaction only through WalletCore's plan.
    @Test
    fun memoSendSignsTheMemoIntoTheV5Transaction() {
        val fixture = fixtures.single { it.string("name") == "two_inputs_change_memo_nu7" }
        val payload = nativeSendPayload(fixture)
        val key = privateKey(fixture)
        val signatures =
            helper(fixture).getPreSignedImageHash(payload).associateWith {
                keysignResponse(Numeric.toHexStringNoPrefix(key.signAsDER(hex(it))))
            }

        val result = helper(fixture).getSignedTransaction(payload, signatures)

        val opReturn = fixture.expected.getValue("outputs").jsonArray[2].jsonObject.string("script")
        val zeroValue = "00".repeat(Long.SIZE_BYTES)
        val opReturnOutput = zeroValue + "%02x".format(opReturn.length / 2) + opReturn
        assertTrue(result.rawTransaction, result.rawTransaction.startsWith("050000800a27a726"))
        assertTrue(result.rawTransaction, result.rawTransaction.contains(opReturnOutput))
    }

    // Mutation: skip a sighash without a signature, as the WalletCore path does.
    @Test
    fun missingSignatureIsRejected() {
        val fixture = fixtures.single { it.string("name") == "two_inputs_change_memo_nu7" }
        val firstOnly = signedSighashes(fixture).entries.take(1).associate { it.toPair() }

        val error =
            assertThrows(IllegalStateException::class.java) {
                helper(fixture)
                    .getZcashSignedTransaction(signingInput(fixture).toByteArray(), firstOnly)
            }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("Missing signature"))
    }

    // Mutation: drop the verifyAsDER check.
    @Test
    fun signatureOverAnotherDigestIsRejected() {
        val fixture = fixtures.first()
        val sighash = fixture.expected.getValue("sighashes").strings().single()
        val otherDigest = ByteArray(32) { 0x42 }
        val signatures =
            mapOf(
                sighash to
                    keysignResponse(
                        Numeric.toHexStringNoPrefix(privateKey(fixture).signAsDER(otherDigest))
                    )
            )

        val error =
            assertThrows(Exception::class.java) {
                helper(fixture)
                    .getZcashSignedTransaction(signingInput(fixture).toByteArray(), signatures)
            }
        assertEquals("Invalid signature", error.message)
    }

    /** An empty chain code makes the helper sign with the fixture key as-is. */
    private fun helper(fixture: JsonObject) =
        UtxoHelper(CoinType.ZCASH, fixture.string("publicKey"), vaultHexChainCode = "")

    private fun privateKey(fixture: JsonObject) = PrivateKey(hex(fixture.string("privateKey")))

    /** The fixture's sighashes signed with its key; the DER bytes must match the recorded ones. */
    private fun signedSighashes(fixture: JsonObject): Map<String, KeysignResponse> {
        val key = privateKey(fixture)
        val expectedDer = fixture.expected.getValue("derSignatures").strings()
        return fixture.expected
            .getValue("sighashes")
            .strings()
            .mapIndexed { index, sighash ->
                val der = Numeric.toHexStringNoPrefix(key.signAsDER(hex(sighash)))
                assertEquals("${fixture.string("name")} DER $index", expectedDer[index], der)
                sighash to keysignResponse(der)
            }
            .toMap()
    }

    private fun keysignResponse(derHex: String) = KeysignResponse().apply { derSignature = derHex }

    /** The `SigningInput` [UtxoHelper] hands WalletCore for this fixture's plan. */
    private fun signingInput(fixture: JsonObject): Bitcoin.SigningInput {
        val plan = fixture.getValue("plan").jsonObject
        val memo = ByteString.copyFromUtf8(plan["memo"]?.jsonPrimitive?.contentOrNull.orEmpty())
        val planBuilder =
            Bitcoin.TransactionPlan.newBuilder()
                .setAmount(plan.long("amount"))
                .setChange(plan.long("change"))
                .setBranchId(
                    ByteString.copyFrom(hex(fixture.string("walletCoreBranchIdLittleEndian")))
                )
                .setOutputOpReturn(memo)
        plan.getValue("utxos").jsonArray.forEach {
            val utxo = it.jsonObject
            planBuilder.addUtxos(
                Bitcoin.UnspentTransaction.newBuilder()
                    .setOutPoint(
                        Bitcoin.OutPoint.newBuilder()
                            .setHash(ByteString.copyFrom(hex(utxo.string("txid")).reversedArray()))
                            .setIndex(utxo.long("vout").toInt())
                            .setSequence(utxo.long("sequence").toInt())
                    )
                    .setAmount(utxo.long("amount"))
                    .setScript(ByteString.copyFrom(hex(utxo.string("script"))))
            )
        }
        return Bitcoin.SigningInput.newBuilder()
            .setCoinType(CoinType.ZCASH.value())
            .setToAddress(fixture.string("toAddress"))
            .setChangeAddress(fixture.string("fromAddress"))
            .setOutputOpReturn(memo)
            .setLockTime(0)
            .setPlan(planBuilder)
            .build()
    }

    /** A native ZEC send of the fixture's amount, memo and UTXOs, planned by WalletCore. */
    private fun nativeSendPayload(fixture: JsonObject): KeysignPayload {
        val plan = fixture.getValue("plan").jsonObject
        return KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Zcash,
                    ticker = "ZEC",
                    logo = "",
                    address = fixture.string("fromAddress"),
                    decimal = 8,
                    hexPublicKey = fixture.string("publicKey"),
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            toAddress = fixture.string("toAddress"),
            toAmount = BigInteger.valueOf(plan.long("amount")),
            blockChainSpecific =
                BlockChainSpecific.UTXO(
                    byteFee = BigInteger.valueOf(100L),
                    sendMaxAmount = false,
                    zcashBranchId = fixture.string("walletCoreBranchIdLittleEndian"),
                ),
            utxos =
                plan.getValue("utxos").jsonArray.map {
                    val utxo = it.jsonObject
                    UtxoInfo(
                        hash = utxo.string("txid"),
                        amount = utxo.long("amount"),
                        index = utxo.long("vout").toUInt(),
                    )
                },
            memo = plan["memo"]?.jsonPrimitive?.contentOrNull,
            swapPayload = null,
            vaultPublicKeyECDSA = fixture.string("publicKey"),
            vaultLocalPartyID = "",
            libType = SigningLibType.GG20,
            wasmExecuteContractPayload = null,
        )
    }

    private val JsonObject.expected: JsonObject
        get() = getValue("expected").jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long

    private fun JsonElement.strings(): List<String> = jsonArray.map { it.jsonPrimitive.content }

    private fun hex(value: String): ByteArray = Numeric.hexStringToByteArray(value)
}
