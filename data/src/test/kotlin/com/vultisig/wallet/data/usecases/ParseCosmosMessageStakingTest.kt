package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingHelper
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test

/**
 * Pins that [ParseCosmosMessageUseCase] decodes the x/staking + x/distribution messages into
 * readable JSON (validator address + amount + operation) rather than the opaque base64 fallback.
 *
 * This is what the joining device's verify card renders for a relayed staking SignDoc — before the
 * decode branches existed it showed a base64 blob, so the pair-device confirmation could not show
 * the validator the initiator's screen did.
 */
class ParseCosmosMessageStakingTest {

    private val parse = ParseCosmosMessageUseCaseImpl(ProtoBuf {}) { "thor-encoded" }

    private val delegator = "terra1delegator00000000000000000000000000abc"
    private val validatorA = "terravaloper1l3zgemxwql5fpa6p9z6h0000000000abc"
    private val validatorB = "terravaloper1zs9rrzwrrsj74gyrc2vs9veptw0gpl9abc"

    @Test
    fun `decodes MsgDelegate to validator address + amount, not base64`() {
        val message =
            parse(
                signDirectFor(
                    CosmosStakingHelper.encodeDelegate(
                        delegator = delegator,
                        validator = validatorA,
                        amount = "1000000",
                        denom = "uluna",
                    )
                )
            )

        assertEquals(1, message.messages.size)
        val msg = message.messages.first()
        assertEquals(CosmosStakingHelper.MSG_DELEGATE_TYPE_URL, msg.typeUrl)
        val obj = msg.value.jsonObject
        assertEquals(validatorA, obj["validatorAddress"]?.jsonPrimitive?.content)
        assertEquals(delegator, obj["delegatorAddress"]?.jsonPrimitive?.content)
        assertEquals("1000000", obj["amount"]?.jsonObject?.get("amount")?.jsonPrimitive?.content)
        assertEquals("uluna", obj["amount"]?.jsonObject?.get("denom")?.jsonPrimitive?.content)
    }

    @Test
    fun `decodes MsgBeginRedelegate with both src and dst validators`() {
        val message =
            parse(
                signDirectFor(
                    CosmosStakingHelper.encodeBeginRedelegate(
                        delegator = delegator,
                        validatorSrc = validatorA,
                        validatorDst = validatorB,
                        amount = "500000",
                        denom = "uluna",
                    )
                )
            )

        val obj = message.messages.first().value.jsonObject
        assertEquals(
            CosmosStakingHelper.MSG_BEGIN_REDELEGATE_TYPE_URL,
            message.messages.first().typeUrl,
        )
        assertEquals(validatorA, obj["validatorSrcAddress"]?.jsonPrimitive?.content)
        assertEquals(validatorB, obj["validatorDstAddress"]?.jsonPrimitive?.content)
    }

    @Test
    fun `decodes MsgWithdrawDelegatorReward to validator address`() {
        val message =
            parse(
                signDirectFor(
                    CosmosStakingHelper.encodeWithdrawDelegatorReward(
                        delegator = delegator,
                        validator = validatorA,
                    )
                )
            )

        val msg = message.messages.first()
        assertEquals(CosmosStakingHelper.MSG_WITHDRAW_DELEGATOR_REWARD_TYPE_URL, msg.typeUrl)
        val validator = msg.value.jsonObject["validatorAddress"]?.jsonPrimitive?.content
        assertEquals(validatorA, validator)
        // Must be structured JSON, not the base64 fallback (which would be a bare string
        // primitive).
        assertTrue(msg.value is kotlinx.serialization.json.JsonObject)
    }

    /** Wrap one Any-encoded staking message in a SignDoc the parser accepts. */
    private fun signDirectFor(msgAny: ByteArray): SignDirectProto {
        val body = CosmosStakingHelper.buildTxBodyMulti(listOf(msgAny))
        val authInfo =
            CosmosStakingHelper.buildAuthInfo(
                pubKey = ByteArray(33) { 0x02 },
                sequence = 1L,
                gasLimit = 300_000L,
                feeDenom = "uluna",
                feeAmount = 7_500L,
            )
        return SignDirectProto(
            bodyBytes = Base64.getEncoder().encodeToString(body),
            authInfoBytes = Base64.getEncoder().encodeToString(authInfo),
            chainId = "columbus-5",
            accountNumber = "1",
        )
    }
}
