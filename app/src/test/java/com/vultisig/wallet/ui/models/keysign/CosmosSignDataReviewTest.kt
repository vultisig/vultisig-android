package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.CosmosMessage
import com.vultisig.wallet.data.usecases.Fee
import com.vultisig.wallet.data.usecases.Message
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.CosmosMsg
import vultisig.keysign.v1.SignAmino

/**
 * Covers reading every transfer of a Cosmos sign request out of its signed messages, for both the
 * decoded `signDirect` body and the `signAmino` `msgs`.
 */
internal class CosmosSignDataReviewTest {

    private val json = Json

    @Test
    fun `two amino sends list both recipients and one total`() {
        val review =
            amino(aminoSend(RECIPIENT_A, "1000000"), aminoSend(RECIPIENT_B, "1000000000"))
                .toSignDataReview(Chain.GaiaChain, json)

        review.recipients shouldBe listOf(RECIPIENT_A, RECIPIENT_B)
        review.coinTotals shouldBe listOf(CosmosSignedCoin("uatom", BigInteger("1001000000")))
        review.hasUnsummarizedMessages shouldBe false
    }

    @Test
    fun `two direct sends list both recipients and one total`() {
        val review =
            direct(directSend(RECIPIENT_A, "1000000"), directSend(RECIPIENT_B, "1000000000"))
                .toSignDataReview(Chain.GaiaChain)

        review.recipients shouldBe listOf(RECIPIENT_A, RECIPIENT_B)
        review.coinTotals shouldBe listOf(CosmosSignedCoin("uatom", BigInteger("1001000000")))
        review.hasUnsummarizedMessages shouldBe false
    }

    @Test
    fun `totals are kept per denom in first-seen order and recipients are distinct`() {
        val review =
            amino(
                    aminoSend(RECIPIENT_A, "5", denom = "ibc/ABC"),
                    aminoSend(RECIPIENT_A, "1"),
                    aminoSend(RECIPIENT_B, "7", denom = "ibc/ABC"),
                )
                .toSignDataReview(Chain.GaiaChain, json)

        review.recipients shouldBe listOf(RECIPIENT_A, RECIPIENT_B)
        review.coinTotals shouldBe
            listOf(
                CosmosSignedCoin("ibc/ABC", BigInteger("12")),
                CosmosSignedCoin("uatom", BigInteger.ONE),
            )
    }

    @Test
    fun `an unknown message type marks the review unsummarized but keeps the readable sends`() {
        val review =
            amino(
                    aminoSend(RECIPIENT_A, "1"),
                    CosmosMsg(type = "cosmos-sdk/MsgSomething", value = "{}"),
                )
                .toSignDataReview(Chain.GaiaChain, json)

        review.recipients shouldBe listOf(RECIPIENT_A)
        review.hasUnsummarizedMessages shouldBe true
    }

    @Test
    fun `an unreadable send is unsummarized and contributes nothing`() {
        val review =
            amino(
                    aminoSend(RECIPIENT_A, "1"),
                    CosmosMsg(type = "cosmos-sdk/MsgSend", value = "not json"),
                    aminoSend(RECIPIENT_B, "1.5"),
                )
                .toSignDataReview(Chain.GaiaChain, json)

        review.recipients shouldBe listOf(RECIPIENT_A)
        review.hasUnsummarizedMessages shouldBe true
    }

    @Test
    fun `an amount in non-ASCII digits is unsummarized`() {
        val review =
            amino(aminoSend(RECIPIENT_A, "1"), aminoSend(RECIPIENT_B, "١٠٠"))
                .toSignDataReview(Chain.GaiaChain, json)

        review.recipients shouldBe listOf(RECIPIENT_A)
        review.coinTotals shouldBe listOf(CosmosSignedCoin("uatom", BigInteger.ONE))
        review.hasUnsummarizedMessages shouldBe true
    }

    @Test
    fun `a request with no messages is unsummarized`() {
        SignAmino(msgs = emptyList())
            .toSignDataReview(Chain.GaiaChain, json)
            .hasUnsummarizedMessages shouldBe true
        direct().toSignDataReview(Chain.GaiaChain).hasUnsummarizedMessages shouldBe true
    }

    @Test
    fun `a contract call shows its funds but is never fully summarized`() {
        val review =
            direct(
                    Message(
                        typeUrl = "/cosmwasm.wasm.v1.MsgExecuteContract",
                        value =
                            json.parseToJsonElement(
                                """{"sender":"$SENDER","contract":"$CONTRACT","msg":"{}",""" +
                                    """"funds":[{"denom":"uatom","amount":"3"}]}"""
                            ),
                    )
                )
                .toSignDataReview(Chain.GaiaChain)

        review.recipients shouldBe listOf(CONTRACT)
        review.coinTotals shouldBe listOf(CosmosSignedCoin("uatom", BigInteger("3")))
        review.hasUnsummarizedMessages shouldBe true
    }

    @Test
    fun `an IBC transfer carries its receiver and memo`() {
        val review =
            amino(
                    CosmosMsg(
                        type = "cosmos-sdk/MsgTransfer",
                        value =
                            """{"receiver":"osmo1receiver","token":{"denom":"uatom","amount":"9"},""" +
                                """"memo":"swap"}""",
                    )
                )
                .toSignDataReview(Chain.GaiaChain, json)

        review.recipients shouldBe listOf("osmo1receiver")
        review.memos shouldBe listOf("swap")
        review.hasUnsummarizedMessages shouldBe false
    }

    @Test
    fun `undelegate and votes move nothing and stay summarized`() {
        val review =
            direct(
                    Message("/cosmos.staking.v1beta1.MsgUndelegate", JsonPrimitive("x")),
                    Message("/cosmos.gov.v1beta1.MsgVote", JsonPrimitive("x")),
                )
                .toSignDataReview(Chain.GaiaChain)

        review.transfers shouldBe emptyList()
        review.hasUnsummarizedMessages shouldBe false
    }

    @Test
    fun `a direct body the decoder left as base64 is unsummarized`() {
        direct(Message("/cosmos.bank.v1beta1.MsgSend", JsonPrimitive("CgVhYmNkZQ==")))
            .toSignDataReview(Chain.GaiaChain)
            .hasUnsummarizedMessages shouldBe true
    }

    @Test
    fun `a THORChain deposit of RUNE is labelled with the bank denom`() {
        val review =
            amino(
                    CosmosMsg(
                        type = "thorchain/MsgDeposit",
                        value =
                            """{"coins":[{"asset":"THOR.RUNE","amount":"100"}],"memo":"=:BTC.BTC:bc1q",""" +
                                """"signer":"thor1signer"}""",
                    )
                )
                .toSignDataReview(Chain.ThorChain, json)

        review.coinTotals shouldBe listOf(CosmosSignedCoin("rune", BigInteger("100")))
        review.recipients shouldBe emptyList()
        review.memos shouldBe listOf("=:BTC.BTC:bc1q")
        review.hasUnsummarizedMessages shouldBe false
    }

    @Test
    fun `a direct THORChain deposit renders its asset with the right separator`() {
        val review =
            direct(
                    Message(
                        "/types.MsgDeposit",
                        json.parseToJsonElement(
                            """{"coins":[{"asset":{"chain":"ETH","symbol":"ETH","ticker":"ETH",""" +
                                """"synth":false,"trade":true,"secured":false},"amount":"4",""" +
                                """"decimals":0}],"memo":"","signer":"thor1signer"}"""
                        ),
                    )
                )
                .toSignDataReview(Chain.ThorChain)

        review.coinTotals shouldBe listOf(CosmosSignedCoin("ETH~ETH", BigInteger("4")))
        review.hasUnsummarizedMessages shouldBe false
    }

    @Test
    fun `a deposit off THORChain or Maya is unsummarized`() {
        amino(CosmosMsg(type = "thorchain/MsgDeposit", value = """{"coins":[],"memo":""}"""))
            .toSignDataReview(Chain.GaiaChain, json)
            .hasUnsummarizedMessages shouldBe true
    }

    @Test
    fun `a deposit with a memo and no coins is unsummarized`() {
        amino(CosmosMsg(type = "thorchain/MsgDeposit", value = """{"coins":[],"memo":"BOND"}"""))
            .toSignDataReview(Chain.ThorChain, json)
            .hasUnsummarizedMessages shouldBe true
    }

    private fun amino(vararg msgs: CosmosMsg) = SignAmino(msgs = msgs.toList())

    private fun aminoSend(recipient: String, amount: String, denom: String = "uatom") =
        CosmosMsg(
            type = "cosmos-sdk/MsgSend",
            value =
                """{"from_address":"$SENDER","to_address":"$recipient",""" +
                    """"amount":[{"denom":"$denom","amount":"$amount"}]}""",
        )

    private fun directSend(recipient: String, amount: String): Message =
        Message(typeUrl = "/cosmos.bank.v1beta1.MsgSend", value = sendBody(recipient, amount))

    private fun sendBody(recipient: String, amount: String): JsonElement =
        json.parseToJsonElement(
            """{"fromAddress":"$SENDER","toAddress":"$recipient",""" +
                """"amount":[{"denom":"uatom","amount":"$amount"}]}"""
        )

    private fun direct(vararg messages: Message) =
        CosmosMessage(
            chainId = "cosmoshub-4",
            accountNumber = "1",
            sequence = "0",
            memo = "",
            messages = messages.toList(),
            authInfoFee = Fee(amount = emptyList(), gasLimit = "200000"),
        )

    private companion object {
        const val SENDER = "cosmos1sender0000000000000000000000000000000"
        const val RECIPIENT_A = "cosmos1recipienta000000000000000000000000000"
        const val RECIPIENT_B = "cosmos1recipientb000000000000000000000000000"
        const val CONTRACT = "cosmos1contract00000000000000000000000000000"
    }
}
