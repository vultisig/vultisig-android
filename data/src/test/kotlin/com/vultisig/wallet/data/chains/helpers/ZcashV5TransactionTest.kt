package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.utils.Numeric
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Test
import wallet.core.jni.proto.Bitcoin

/**
 * Golden vectors for the Zcash v5 builder (`/zcash/zcash-v5-golden.json`): ZIP-225/ZIP-244
 * reference values cross-checked against mainnet v5 transactions, with WalletCore-derived output
 * scripts and RFC6979 signatures. Runs on the JVM, so the WalletCore address-to-script step is
 * replaced by the fixture's own WalletCore scripts; the instrumented `ZcashV5SigningTest` drives
 * the same vectors through [UtxoHelper] with real WalletCore.
 */
class ZcashV5TransactionTest {

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
    fun `every fixture reproduces its per-input sighashes in input order`() {
        fixtures.forEach { fixture ->
            val hashes =
                ZcashV5Transaction.preImageHashes(
                    signingInput(fixture).toByteArray(),
                    lockScripts(fixture),
                )

            hashes.map(Numeric::toHexStringNoPrefix) shouldBe
                fixture.expected.getValue("sighashes").strings()
        }
    }

    @Test
    fun `every fixture compiles to its raw bytes and display-order txid`() {
        fixtures.forEach { fixture ->
            val result =
                ZcashV5Transaction.compile(
                    inputData = signingInput(fixture).toByteArray(),
                    derSignatures = fixture.expected.getValue("derSignatures").strings().map(::hex),
                    publicKey = hex(fixture.string("publicKey")),
                    lockScript = lockScripts(fixture),
                )

            result.rawTransaction shouldBe fixture.expected.string("raw")
            result.transactionHash shouldBe fixture.expected.string("txid")
        }
    }

    // Mutation: drop the P2PKH input-script check.
    @Test
    fun `a non-P2PKH input script is rejected`() {
        val fixture = fixtures.first()
        val p2sh = hex("a914" + "11".repeat(20) + "87")
        val input =
            signingInput(fixture) {
                setUtxos(0, getUtxos(0).toBuilder().setScript(ByteString.copyFrom(p2sh)))
            }

        shouldThrow<IllegalArgumentException> {
                ZcashV5Transaction.preImageHashes(input.toByteArray(), lockScripts(fixture))
            }
            .message shouldContain "P2PKH"
    }

    // Mutation: drop the 80-byte memo limit.
    @Test
    fun `a memo over 80 bytes is rejected`() {
        val fixture = fixtures.first()
        val input = signingInput(fixture) { setOutputOpReturn(ByteString.copyFrom(ByteArray(81))) }

        shouldThrow<IllegalArgumentException> {
                ZcashV5Transaction.preImageHashes(input.toByteArray(), lockScripts(fixture))
            }
            .message shouldContain "memo"
    }

    // Mutation: tighten the memo limit to < 80; WalletCore accepts exactly 80 bytes.
    @Test
    fun `a memo of exactly 80 bytes is accepted`() {
        val fixture = fixtures.first()
        val input = signingInput(fixture) { setOutputOpReturn(ByteString.copyFrom(ByteArray(80))) }

        ZcashV5Transaction.preImageHashes(input.toByteArray(), lockScripts(fixture)).size shouldBe 1
    }

    // Mutation: drop the 4-byte branch id check (a short id would shift every header field).
    @Test
    fun `a branch id that is not exactly 4 bytes is rejected`() {
        val fixture = fixtures.first()
        listOf(ByteArray(0), ByteArray(3), ByteArray(5)).forEach { branchId ->
            val input = signingInput(fixture) { setBranchId(ByteString.copyFrom(branchId)) }

            shouldThrow<IllegalArgumentException> {
                    ZcashV5Transaction.preImageHashes(input.toByteArray(), lockScripts(fixture))
                }
                .message shouldContain "branch id"
        }
    }

    // Mutation: drop the empty-plan check.
    @Test
    fun `a plan without inputs is rejected`() {
        val fixture = fixtures.first()
        val input = signingInput(fixture) { clearUtxos() }

        shouldThrow<IllegalArgumentException> {
                ZcashV5Transaction.preImageHashes(input.toByteArray(), lockScripts(fixture))
            }
            .message shouldContain "no inputs"
    }

    // Mutation: drop the empty-lock-script check (or skip it for the destination output).
    @Test
    fun `an unparseable destination address is rejected instead of paying an empty script`() {
        val fixture = fixtures.first()
        val toAddress = fixture.string("toAddress")

        shouldThrow<IllegalArgumentException> {
                ZcashV5Transaction.preImageHashes(
                    signingInput(fixture).toByteArray(),
                    lockScriptsUnparseable(fixture, toAddress),
                )
            }
            .message shouldContain toAddress
    }

    // Mutation: drop the empty-lock-script check (or skip it for the change output).
    @Test
    fun `an unparseable change address is rejected instead of paying an empty script`() {
        val fixture = fixtures.first()
        val changeAddress = fixture.string("fromAddress")

        shouldThrow<IllegalArgumentException> {
                ZcashV5Transaction.preImageHashes(
                    signingInput(fixture).toByteArray(),
                    lockScriptsUnparseable(fixture, changeAddress),
                )
            }
            .message shouldContain changeAddress
    }

    // Mutation: drop the signature-count check (zip would silently drop the unsigned input).
    @Test
    fun `compile with fewer signatures than inputs is rejected`() {
        val fixture = fixtures.single { it.string("name") == "two_inputs_change_memo_nu7" }

        shouldThrow<IllegalStateException> {
                ZcashV5Transaction.compile(
                    inputData = signingInput(fixture).toByteArray(),
                    derSignatures =
                        listOf(hex(fixture.expected.getValue("derSignatures").strings().first())),
                    publicKey = hex(fixture.string("publicKey")),
                    lockScript = lockScripts(fixture),
                )
            }
            .message shouldContain "signatures"
    }

    /** The `SigningInput` [UtxoHelper] hands WalletCore for this fixture's plan. */
    private fun signingInput(
        fixture: JsonObject,
        editPlan: Bitcoin.TransactionPlan.Builder.() -> Unit = {},
    ): Bitcoin.SigningInput {
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
        planBuilder.editPlan()
        return Bitcoin.SigningInput.newBuilder()
            .setCoinType(ZCASH_COIN_TYPE)
            .setToAddress(fixture.string("toAddress"))
            .setChangeAddress(fixture.string("fromAddress"))
            .setOutputOpReturn(memo)
            .setLockTime(0)
            .setPlan(planBuilder)
            .build()
    }

    /** WalletCore's lock scripts for the fixture's two addresses, as recorded in the fixture. */
    private fun lockScripts(fixture: JsonObject): (String) -> ByteArray {
        val scripts =
            mapOf(
                fixture.string("toAddress") to
                    hex(
                        fixture.expected
                            .getValue("outputs")
                            .jsonArray[0]
                            .jsonObject
                            .string("script")
                    ),
                fixture.string("fromAddress") to
                    hex(
                        fixture
                            .getValue("plan")
                            .jsonObject
                            .getValue("utxos")
                            .jsonArray[0]
                            .jsonObject
                            .string("script")
                    ),
            )
        return scripts::getValue
    }

    /** [lockScripts], except WalletCore's empty result for an address it cannot parse. */
    private fun lockScriptsUnparseable(
        fixture: JsonObject,
        address: String,
    ): (String) -> ByteArray {
        val scripts = lockScripts(fixture)
        return { if (it == address) ByteArray(0) else scripts(it) }
    }

    private val JsonObject.expected: JsonObject
        get() = getValue("expected").jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long

    private fun JsonElement.strings(): List<String> = jsonArray.map { it.jsonPrimitive.content }

    private fun hex(value: String): ByteArray = Numeric.hexStringToByteArray(value)

    private companion object {
        // SLIP-44 coin type WalletCore uses for Zcash.
        const val ZCASH_COIN_TYPE = 133
    }
}
