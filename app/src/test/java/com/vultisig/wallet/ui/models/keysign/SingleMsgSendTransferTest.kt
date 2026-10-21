package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.usecases.CosmosMessage
import com.vultisig.wallet.data.usecases.Fee
import com.vultisig.wallet.data.usecases.Message
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.Test

/**
 * Covers the co-signer reading a `signDirect` body's recipient and amount: only a single bank
 * `MsgSend` with one coin in the expected denom yields a To/amount row; anything else yields none.
 */
internal class SingleMsgSendTransferTest {

    @Test
    fun `single MsgSend in the expected denom yields its recipient and amount`() {
        val transfer = signDoc(msgSend()).singleMsgSendTransfer("uatom")

        transfer shouldBe SignDirectTransfer(recipient = RECIPIENT, amount = BigInteger("1500000"))
    }

    @Test
    fun `denom mismatch yields no transfer`() {
        signDoc(msgSend(denom = "ibc/27394FB0")).singleMsgSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `denom comparison is case sensitive`() {
        signDoc(msgSend(denom = "uATOM")).singleMsgSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `missing expected denom yields no transfer`() {
        signDoc(msgSend()).singleMsgSendTransfer(null).shouldBeNull()
        signDoc(msgSend()).singleMsgSendTransfer("").shouldBeNull()
    }

    @Test
    fun `multiple messages yield no transfer`() {
        signDoc(msgSend(), msgSend()).singleMsgSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `multiple coins yield no transfer`() {
        val body = buildJsonObject {
            put("fromAddress", SENDER)
            put("toAddress", RECIPIENT)
            putJsonArray("amount") {
                addJsonObject {
                    put("denom", "uatom")
                    put("amount", "1500000")
                }
                addJsonObject {
                    put("denom", "uosmo")
                    put("amount", "1")
                }
            }
        }

        signDoc(Message(MSG_SEND, body)).singleMsgSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `non MsgSend body yields no transfer`() {
        val execute =
            Message(
                typeUrl = "/cosmwasm.wasm.v1.MsgExecuteContract",
                value =
                    buildJsonObject {
                        put("sender", SENDER)
                        put("contract", RECIPIENT)
                        putJsonArray("funds") {}
                    },
            )

        signDoc(execute).singleMsgSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `non integer amount yields no transfer`() {
        signDoc(msgSend(amount = "1.5")).singleMsgSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `undecoded body yields no transfer`() {
        signDoc(Message(MSG_SEND, JsonPrimitive("0a2d636f736d6f73")))
            .singleMsgSendTransfer("uatom")
            .shouldBeNull()
    }

    private fun msgSend(denom: String = "uatom", amount: String = "1500000"): Message =
        Message(
            typeUrl = MSG_SEND,
            value =
                buildJsonObject {
                    put("fromAddress", SENDER)
                    put("toAddress", RECIPIENT)
                    putJsonArray("amount") {
                        addJsonObject {
                            put("denom", denom)
                            put("amount", amount)
                        }
                    }
                },
        )

    private fun signDoc(vararg messages: Message) =
        CosmosMessage(
            chainId = "cosmoshub-4",
            accountNumber = "1",
            sequence = "0",
            memo = "",
            messages = messages.toList(),
            authInfoFee = Fee(amount = emptyList(), gasLimit = "200000"),
        )

    private companion object {
        const val MSG_SEND = "/cosmos.bank.v1beta1.MsgSend"
        const val SENDER = "cosmos1sender0000000000000000000000000000000"
        const val RECIPIENT = "cosmos1recipient000000000000000000000000000"
    }
}
