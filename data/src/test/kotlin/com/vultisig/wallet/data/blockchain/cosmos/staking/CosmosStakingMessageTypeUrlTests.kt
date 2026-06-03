package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import java.math.BigInteger
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TransactionType

/**
 * Pins the proto message *type* embedded in the SignDoc the resolver hands to Trust Wallet Core's
 * signDirect path. The sibling [CosmosStakingSignDataResolverTests] assert byte-equality by
 * re-encoding through the same helper — so a regression that swapped [CosmosStakingHelper] over to
 * a bank `MsgSend` would pass there (both expected and actual would be wrong identically).
 *
 * This suite instead decodes the TxBody and reads each `google.protobuf.Any.type_url`, asserting
 * the staking/distribution discriminator and explicitly rejecting `/cosmos.bank.v1beta1.MsgSend`.
 *
 * That `MsgSend` case is exactly the iOS failure mode (vultisig-ios): a delegate that fell back to
 * a bank send to the `terravaloper…` operator address, which the chain rejects with "invalid to
 * address: hrp does not match bech32 prefix". This test guarantees the Android encoder can never
 * silently regress into it.
 */
class CosmosStakingMessageTypeUrlTests {

    private companion object {
        const val DELEGATOR = "terra1delegator00000000000000000000000000ab"
        const val PUBKEY_HEX = "020202020202020202020202020202020202020202020202020202020202020202"
        const val MSG_SEND_TYPE_URL = "/cosmos.bank.v1beta1.MsgSend"

        val VALIDATOR_A = Bech32TestEncoder.encode("terravaloper", ByteArray(20) { it.toByte() })
        val VALIDATOR_B =
            Bech32TestEncoder.encode("terravaloper", ByteArray(20) { (it + 10).toByte() })

        val COSMOS_SPECIFIC =
            BlockChainSpecific.Cosmos(
                accountNumber = BigInteger.valueOf(100),
                sequence = BigInteger.valueOf(42),
                gas = BigInteger.ZERO,
                ibcDenomTraces = null,
                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
            )
    }

    @Test
    fun `delegate encodes a MsgDelegate, never a bank MsgSend`() {
        val urls =
            typeUrls(
                CosmosStakingPayload.Delegate(validatorAddress = VALIDATOR_A, amount = "1000000")
            )
        assertEquals(listOf(CosmosStakingHelper.MSG_DELEGATE_TYPE_URL), urls)
        assertFalse(urls.contains(MSG_SEND_TYPE_URL))
    }

    @Test
    fun `undelegate encodes a MsgUndelegate`() {
        val urls =
            typeUrls(
                CosmosStakingPayload.Undelegate(validatorAddress = VALIDATOR_A, amount = "500000")
            )
        assertEquals(listOf(CosmosStakingHelper.MSG_UNDELEGATE_TYPE_URL), urls)
        assertFalse(urls.contains(MSG_SEND_TYPE_URL))
    }

    @Test
    fun `redelegate encodes a MsgBeginRedelegate`() {
        val urls =
            typeUrls(
                CosmosStakingPayload.Redelegate(
                    validatorSrcAddress = VALIDATOR_A,
                    validatorDstAddress = VALIDATOR_B,
                    amount = "100000",
                )
            )
        assertEquals(listOf(CosmosStakingHelper.MSG_BEGIN_REDELEGATE_TYPE_URL), urls)
        assertFalse(urls.contains(MSG_SEND_TYPE_URL))
    }

    @Test
    fun `withdrawRewards encodes one MsgWithdrawDelegatorReward per validator`() {
        val urls =
            typeUrls(
                CosmosStakingPayload.WithdrawRewards(validators = listOf(VALIDATOR_A, VALIDATOR_B))
            )
        assertEquals(
            listOf(
                CosmosStakingHelper.MSG_WITHDRAW_DELEGATOR_REWARD_TYPE_URL,
                CosmosStakingHelper.MSG_WITHDRAW_DELEGATOR_REWARD_TYPE_URL,
            ),
            urls,
        )
        assertFalse(urls.contains(MSG_SEND_TYPE_URL))
    }

    /** Resolve the payload and decode every `Any.type_url` out of the resulting TxBody. */
    private fun typeUrls(payload: CosmosStakingPayload): List<String> {
        val result =
            CosmosStakingSignDataResolver.resolve(
                payload = payload,
                chain = Chain.TerraClassic,
                delegatorAddress = DELEGATOR,
                hexPublicKey = PUBKEY_HEX,
                chainSpecific = COSMOS_SPECIFIC,
            )
        return decodeMessageTypeUrls(Base64.getDecoder().decode(result.bodyBytes))
    }

    // MARK: - Minimal protobuf reader
    //
    // TxBody { repeated google.protobuf.Any messages = 1; string memo = 2; ... }
    // Any    { string type_url = 1; bytes value = 2; }
    // Walk the top-level TxBody, and for each field-1 (Any) entry pull out its field-1 (type_url).

    private fun decodeMessageTypeUrls(txBody: ByteArray): List<String> {
        val urls = mutableListOf<String>()
        var i = 0
        while (i < txBody.size) {
            val (tag, afterTag) = readVarint(txBody, i)
            i = afterTag
            val field = (tag ushr 3).toInt()
            when ((tag and 0x7L).toInt()) {
                LEN -> {
                    val (len, afterLen) = readVarint(txBody, i)
                    i = afterLen
                    val end = i + len.toInt()
                    if (field == 1) urls += anyTypeUrl(txBody.copyOfRange(i, end))
                    i = end
                }
                VARINT -> i = readVarint(txBody, i).second
                else -> error("unexpected wire type in TxBody")
            }
        }
        return urls
    }

    private fun anyTypeUrl(any: ByteArray): String {
        var i = 0
        while (i < any.size) {
            val (tag, afterTag) = readVarint(any, i)
            i = afterTag
            val field = (tag ushr 3).toInt()
            when ((tag and 0x7L).toInt()) {
                LEN -> {
                    val (len, afterLen) = readVarint(any, i)
                    i = afterLen
                    val end = i + len.toInt()
                    if (field == 1) return any.copyOfRange(i, end).toString(Charsets.UTF_8)
                    i = end
                }
                VARINT -> i = readVarint(any, i).second
                else -> error("unexpected wire type in Any")
            }
        }
        error("Any carried no type_url")
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (true) {
            val b = bytes[i].toInt() and 0xFF
            i++
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result to i
    }

    private val VARINT = 0
    private val LEN = 2
}
