package com.vultisig.wallet.data.blockchain.cosmos.staking

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Locks down the wire-format invariants for the Cosmos x/staking + x/distribution proto encoders.
 * Mirrors iOS `CosmosStakingHelperTests.swift` (vultisig-ios PR #4432) — same test cases, same
 * cosmoshub-4 fixtures, same byte-pin contract.
 */
class CosmosStakingHelperTests {

    // MARK: - Fixtures (mirror SDK + iOS FX)

    private object FX {
        const val CHAIN_ID = "cosmoshub-4"
        const val DELEGATOR = "cosmos1abcdefghijklmnopqrstuvwxyz0123456789ab"
        const val VALIDATOR = "cosmosvaloper1zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzaaa"
        const val VALIDATOR_SRC = "cosmosvaloper1srcsrcsrcsrcsrcsrcsrcsrcsrcsrcsrcsrcs"
        const val VALIDATOR_DST = "cosmosvaloper1dstdstdstdstdstdstdstdstdstdstdstdsts"
        const val AMOUNT = "1000000"
        const val DENOM = "uatom"
        const val FEE_AMOUNT: Long = 7_500L
        const val GAS_LIMIT: Long = 250_000L
        const val SEQUENCE: Long = 42L
        const val ACCOUNT_NUMBER: Long = 100L
        // 33-byte compressed secp256k1 pubkey, all 0x02 — matches the SDK fixture verbatim.
        val PUBKEY: ByteArray = ByteArray(33) { 0x02 }
    }

    // MARK: - MsgDelegate

    @Test
    fun `MsgDelegate uses canonical typeURL`() {
        val anyMsg =
            CosmosStakingHelper.encodeDelegate(
                delegator = FX.DELEGATOR,
                validator = FX.VALIDATOR,
                amount = FX.AMOUNT,
                denom = FX.DENOM,
            )
        val unwrapped = ProtoParser.decodeAny(anyMsg)
        assertEquals("/cosmos.staking.v1beta1.MsgDelegate", unwrapped.typeUrl)
    }

    @Test
    fun `MsgDelegate encodes all fields in order`() {
        val anyMsg =
            CosmosStakingHelper.encodeDelegate(FX.DELEGATOR, FX.VALIDATOR, FX.AMOUNT, FX.DENOM)
        val unwrapped = ProtoParser.decodeAny(anyMsg)
        val fields = ProtoParser.parseFields(unwrapped.value)
        assertEquals(FX.DELEGATOR, ProtoParser.string(1, fields))
        assertEquals(FX.VALIDATOR, ProtoParser.string(2, fields))

        val coinBytes = ProtoParser.bytes(3, fields)
        val coinFields = ProtoParser.parseFields(coinBytes)
        assertEquals(FX.DENOM, ProtoParser.string(1, coinFields))
        assertEquals(FX.AMOUNT, ProtoParser.string(2, coinFields))
    }

    @Test
    fun `MsgDelegate is deterministic for identical inputs`() {
        val a = CosmosStakingHelper.encodeDelegate(FX.DELEGATOR, FX.VALIDATOR, FX.AMOUNT, FX.DENOM)
        val b = CosmosStakingHelper.encodeDelegate(FX.DELEGATOR, FX.VALIDATOR, FX.AMOUNT, FX.DENOM)
        assertContentEquals(a, b)
    }

    @Test
    fun `MsgDelegate bytes change when validator changes`() {
        val a = CosmosStakingHelper.encodeDelegate(FX.DELEGATOR, FX.VALIDATOR, FX.AMOUNT, FX.DENOM)
        val b =
            CosmosStakingHelper.encodeDelegate(
                FX.DELEGATOR,
                "cosmosvaloper1otherotherotherotherotherotherother",
                FX.AMOUNT,
                FX.DENOM,
            )
        assertNotEquals(a.toList(), b.toList())
    }

    // MARK: - MsgUndelegate

    @Test
    fun `MsgUndelegate uses undelegate typeURL`() {
        val anyMsg =
            CosmosStakingHelper.encodeUndelegate(FX.DELEGATOR, FX.VALIDATOR, FX.AMOUNT, FX.DENOM)
        val unwrapped = ProtoParser.decodeAny(anyMsg)
        assertEquals("/cosmos.staking.v1beta1.MsgUndelegate", unwrapped.typeUrl)
    }

    @Test
    fun `MsgUndelegate has identical wire shape to delegate`() {
        // Same fields in same positions; only typeUrl differs. Inner bytes must match.
        val delegate =
            CosmosStakingHelper.encodeDelegate(FX.DELEGATOR, FX.VALIDATOR, FX.AMOUNT, FX.DENOM)
        val undelegate =
            CosmosStakingHelper.encodeUndelegate(FX.DELEGATOR, FX.VALIDATOR, FX.AMOUNT, FX.DENOM)
        assertContentEquals(
            ProtoParser.decodeAny(delegate).value,
            ProtoParser.decodeAny(undelegate).value,
        )
    }

    // MARK: - MsgBeginRedelegate

    @Test
    fun `MsgBeginRedelegate uses canonical typeURL`() {
        val anyMsg =
            CosmosStakingHelper.encodeBeginRedelegate(
                FX.DELEGATOR,
                FX.VALIDATOR_SRC,
                FX.VALIDATOR_DST,
                FX.AMOUNT,
                FX.DENOM,
            )
        assertEquals(
            "/cosmos.staking.v1beta1.MsgBeginRedelegate",
            ProtoParser.decodeAny(anyMsg).typeUrl,
        )
    }

    @Test
    fun `MsgBeginRedelegate encodes src at field 2 and dst at field 3`() {
        // Regression guard: a src/dst swap would silently produce a tx that drains the wrong
        // validator. SDK pins this at cosmos-staking.test.ts:153-162.
        val anyMsg =
            CosmosStakingHelper.encodeBeginRedelegate(
                FX.DELEGATOR,
                FX.VALIDATOR_SRC,
                FX.VALIDATOR_DST,
                FX.AMOUNT,
                FX.DENOM,
            )
        val fields = ProtoParser.parseFields(ProtoParser.decodeAny(anyMsg).value)
        assertEquals(FX.DELEGATOR, ProtoParser.string(1, fields))
        assertEquals(FX.VALIDATOR_SRC, ProtoParser.string(2, fields))
        assertEquals(FX.VALIDATOR_DST, ProtoParser.string(3, fields))
        val coin = ProtoParser.parseFields(ProtoParser.bytes(4, fields))
        assertEquals(FX.DENOM, ProtoParser.string(1, coin))
        assertEquals(FX.AMOUNT, ProtoParser.string(2, coin))
    }

    @Test
    fun `MsgBeginRedelegate src and dst are not interchangeable`() {
        val normal =
            CosmosStakingHelper.encodeBeginRedelegate(
                FX.DELEGATOR,
                FX.VALIDATOR_SRC,
                FX.VALIDATOR_DST,
                FX.AMOUNT,
                FX.DENOM,
            )
        val swapped =
            CosmosStakingHelper.encodeBeginRedelegate(
                FX.DELEGATOR,
                FX.VALIDATOR_DST,
                FX.VALIDATOR_SRC,
                FX.AMOUNT,
                FX.DENOM,
            )
        assertNotEquals(normal.toList(), swapped.toList())
    }

    // MARK: - MsgWithdrawDelegatorReward

    @Test
    fun `MsgWithdrawReward uses distribution typeURL`() {
        val anyMsg = CosmosStakingHelper.encodeWithdrawDelegatorReward(FX.DELEGATOR, FX.VALIDATOR)
        assertEquals(
            "/cosmos.distribution.v1beta1.MsgWithdrawDelegatorReward",
            ProtoParser.decodeAny(anyMsg).typeUrl,
        )
    }

    @Test
    fun `MsgWithdrawReward has no Coin field`() {
        val anyMsg = CosmosStakingHelper.encodeWithdrawDelegatorReward(FX.DELEGATOR, FX.VALIDATOR)
        val fields = ProtoParser.parseFields(ProtoParser.decodeAny(anyMsg).value)
        assertEquals(FX.DELEGATOR, ProtoParser.string(1, fields))
        assertEquals(FX.VALIDATOR, ProtoParser.string(2, fields))
        assertEquals(2, fields.size, "MsgWithdrawDelegatorReward must have exactly 2 fields")
    }

    // MARK: - buildTxBodyMulti

    @Test
    fun `TxBody single packs message at field 1`() {
        val anyMsg =
            CosmosStakingHelper.encodeDelegate(FX.DELEGATOR, FX.VALIDATOR, FX.AMOUNT, FX.DENOM)
        val txBody = CosmosStakingHelper.buildTxBodyMulti(listOf(anyMsg))
        val fields = ProtoParser.parseFields(txBody)
        assertEquals(1, fields.count { it.tag == 1 })
        assertContentEquals(anyMsg, ProtoParser.bytes(1, fields))
    }

    @Test
    fun `TxBody multi packs all messages preserving order`() {
        val validators =
            listOf(
                "cosmosvaloper1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "cosmosvaloper1bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "cosmosvaloper1ccccccccccccccccccccccccccccccccccc",
            )
        val msgs =
            validators.map { CosmosStakingHelper.encodeWithdrawDelegatorReward(FX.DELEGATOR, it) }
        val txBody = CosmosStakingHelper.buildTxBodyMulti(msgs)
        val messageEntries = ProtoParser.parseFields(txBody).filter { it.tag == 1 }
        assertEquals(validators.size, messageEntries.size)
        messageEntries.forEachIndexed { index, entry ->
            assertContentEquals(
                msgs[index],
                entry.value,
                "TxBody message at index $index must round-trip",
            )
        }
    }

    @Test
    fun `TxBody multi handles 8 messages for batched-claim soft cap`() {
        // The batched-claim UI soft cap is 8 validators per tx. The encoder imposes no cap of its
        // own — exercising N=8 pins linear packing all the way up to the UI ceiling.
        val validators = (1..8).map { "cosmosvaloper1batch00000000000000000000000000000$it" }
        val msgs =
            validators.map { CosmosStakingHelper.encodeWithdrawDelegatorReward(FX.DELEGATOR, it) }
        val txBody = CosmosStakingHelper.buildTxBodyMulti(msgs)
        assertEquals(8, ProtoParser.parseFields(txBody).count { it.tag == 1 })
    }

    @Test
    fun `TxBody embeds memo at field 2 when provided`() {
        val anyMsg =
            CosmosStakingHelper.encodeDelegate(FX.DELEGATOR, FX.VALIDATOR, FX.AMOUNT, FX.DENOM)
        val txBody =
            CosmosStakingHelper.buildTxBodyMulti(
                listOf(anyMsg),
                memo = "claim airdrop via vultiagent",
            )
        val fields = ProtoParser.parseFields(txBody)
        assertEquals("claim airdrop via vultiagent", ProtoParser.string(2, fields))
    }

    @Test
    fun `TxBody omits memo field when empty`() {
        val anyMsg =
            CosmosStakingHelper.encodeDelegate(FX.DELEGATOR, FX.VALIDATOR, FX.AMOUNT, FX.DENOM)
        val txBody = CosmosStakingHelper.buildTxBodyMulti(listOf(anyMsg), memo = "")
        // proto3 default-skip: an empty memo must not be emitted at all. Guards against writing a
        // zero-byte memo field that would change the SignDoc hash.
        assertEquals(0, ProtoParser.parseFields(txBody).count { it.tag == 2 })
    }

    @Test
    fun `TxBody mixes msg types preserving typeURL order`() {
        val delegate =
            CosmosStakingHelper.encodeDelegate(FX.DELEGATOR, FX.VALIDATOR, FX.AMOUNT, FX.DENOM)
        val withdraw = CosmosStakingHelper.encodeWithdrawDelegatorReward(FX.DELEGATOR, FX.VALIDATOR)
        val txBody = CosmosStakingHelper.buildTxBodyMulti(listOf(delegate, withdraw))
        val messages = ProtoParser.parseFields(txBody).filter { it.tag == 1 }
        assertEquals(2, messages.size)
        assertEquals(
            "/cosmos.staking.v1beta1.MsgDelegate",
            ProtoParser.decodeAny(messages[0].value).typeUrl,
        )
        assertEquals(
            "/cosmos.distribution.v1beta1.MsgWithdrawDelegatorReward",
            ProtoParser.decodeAny(messages[1].value).typeUrl,
        )
    }

    // MARK: - AuthInfo

    @Test
    fun `AuthInfo embeds pubKey Any and Fee`() {
        val authInfo =
            CosmosStakingHelper.buildAuthInfo(
                pubKey = FX.PUBKEY,
                sequence = FX.SEQUENCE,
                gasLimit = FX.GAS_LIMIT,
                feeDenom = FX.DENOM,
                feeAmount = FX.FEE_AMOUNT,
            )
        val fields = ProtoParser.parseFields(authInfo)

        val signerInfoEntries = fields.filter { it.tag == 1 }
        assertEquals(1, signerInfoEntries.size)
        val signerInfo = ProtoParser.parseFields(signerInfoEntries[0].value)

        val pubKeyAny = ProtoParser.bytes(1, signerInfo)
        val pubKeyAnyFields = ProtoParser.parseFields(pubKeyAny)
        assertEquals("/cosmos.crypto.secp256k1.PubKey", ProtoParser.string(1, pubKeyAnyFields))

        val sequence = ProtoParser.varint(3, signerInfo)
        assertEquals(FX.SEQUENCE, sequence)

        val fee = ProtoParser.parseFields(ProtoParser.bytes(2, fields))
        val coin = ProtoParser.parseFields(ProtoParser.bytes(1, fee))
        assertEquals(FX.DENOM, ProtoParser.string(1, coin))
        assertEquals(FX.FEE_AMOUNT.toString(), ProtoParser.string(2, coin))
        assertEquals(FX.GAS_LIMIT, ProtoParser.varint(2, fee))
    }

    @Test
    fun `AuthInfo stamps the provided pubKey type URL for ML-DSA`() {
        // QBTC reuses this encoder with the ML-DSA pubkey URL and its ~1312-byte post-quantum key.
        val mldsaPubKey = ByteArray(1312) { 0xAB.toByte() }
        val authInfo =
            CosmosStakingHelper.buildAuthInfo(
                pubKey = mldsaPubKey,
                sequence = FX.SEQUENCE,
                gasLimit = FX.GAS_LIMIT,
                feeDenom = FX.DENOM,
                feeAmount = FX.FEE_AMOUNT,
                pubKeyTypeUrl = "/cosmos.crypto.mldsa.PubKey",
            )
        val signerInfo =
            ProtoParser.parseFields(ProtoParser.bytes(1, ProtoParser.parseFields(authInfo)))
        val pubKeyAny = ProtoParser.parseFields(ProtoParser.bytes(1, signerInfo))
        assertEquals("/cosmos.crypto.mldsa.PubKey", ProtoParser.string(1, pubKeyAny))
        // The full ML-DSA key bytes survive intact in the inner PubKey `{ key(1, bytes) }`.
        assertContentEquals(
            mldsaPubKey,
            ProtoParser.bytes(1, ProtoParser.parseFields(ProtoParser.bytes(2, pubKeyAny))),
        )
    }

    // MARK: - Proto wire-format parser (test-only)

    /**
     * Tiny proto wire-format walker used to parse the bytes the encoder produces. Mirrors the
     * private parser in iOS `CosmosStakingHelperTests.swift`. NOT used in production — kept inside
     * the test file so the production encoder remains the single proto authority.
     */
    private object ProtoParser {

        data class ProtoField(
            val tag: Int,
            val wireType: Int,
            val value: ByteArray,
            val varint: Long?,
        )

        data class AnyMsg(val typeUrl: String, val value: ByteArray)

        fun decodeAny(data: ByteArray): AnyMsg {
            val fields = parseFields(data)
            return AnyMsg(typeUrl = string(1, fields), value = bytes(2, fields))
        }

        fun parseFields(data: ByteArray): List<ProtoField> {
            val fields = mutableListOf<ProtoField>()
            var offset = 0
            while (offset < data.size) {
                val (tagRaw, afterTag) =
                    readVarint(data, offset) ?: error("Truncated proto stream at offset $offset")
                val tag = (tagRaw shr 3).toInt()
                val wireType = (tagRaw and 0x7L).toInt()
                offset = afterTag
                when (wireType) {
                    0 -> {
                        val (value, afterValue) =
                            readVarint(data, offset) ?: error("Truncated varint at offset $offset")
                        fields.add(
                            ProtoField(
                                tag = tag,
                                wireType = wireType,
                                value = ByteArray(0),
                                varint = value,
                            )
                        )
                        offset = afterValue
                    }
                    2 -> {
                        val (length, afterLength) =
                            readVarint(data, offset)
                                ?: error("Truncated length prefix at offset $offset")
                        val end = afterLength + length.toInt()
                        require(end <= data.size) { "Length-delimited field exceeds buffer" }
                        val payload = data.copyOfRange(afterLength, end)
                        fields.add(
                            ProtoField(
                                tag = tag,
                                wireType = wireType,
                                value = payload,
                                varint = null,
                            )
                        )
                        offset = end
                    }
                    else -> error("Unsupported wire type $wireType at offset $offset")
                }
            }
            return fields
        }

        fun string(field: Int, fields: List<ProtoField>): String =
            String(bytes(field, fields), Charsets.UTF_8)

        fun bytes(field: Int, fields: List<ProtoField>): ByteArray {
            val entry =
                fields.firstOrNull { it.tag == field && it.wireType == 2 }
                    ?: error("Missing length-delimited field $field")
            return entry.value
        }

        fun varint(field: Int, fields: List<ProtoField>): Long {
            val entry =
                fields.firstOrNull { it.tag == field && it.wireType == 0 }
                    ?: error("Missing varint field $field")
            return entry.varint ?: error("Field $field has no varint value")
        }

        private fun readVarint(data: ByteArray, offset: Int): Pair<Long, Int>? {
            var result = 0L
            var shift = 0
            var cursor = offset
            while (cursor < data.size) {
                val byte = data[cursor].toInt() and 0xFF
                result = result or ((byte.toLong() and 0x7FL) shl shift)
                cursor++
                if ((byte and 0x80) == 0) return result to cursor
                shift += 7
                if (shift > 63) return null
            }
            return null
        }
    }
}
