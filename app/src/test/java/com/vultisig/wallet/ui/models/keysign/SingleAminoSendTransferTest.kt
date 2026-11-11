package com.vultisig.wallet.ui.models.keysign

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.CosmosMsg
import vultisig.keysign.v1.SignAmino

/**
 * Covers the co-signer reading a `signAmino` request's recipient and amount from its signed `msgs`:
 * only a single bank send with one coin in the expected denom yields a To/amount row.
 */
internal class SingleAminoSendTransferTest {

    @Test
    fun `single cosmos-sdk MsgSend in the expected denom yields its recipient and amount`() {
        val transfer = amino(send()).singleAminoSendTransfer("uatom")

        transfer shouldBe CosmosSignedTransfer(recipient = RECIPIENT, amount = BigInteger("1000"))
    }

    @Test
    fun `thorchain and bank MsgSend types are read too`() {
        amino(send(type = "thorchain/MsgSend", denom = "rune"))
            .singleAminoSendTransfer("rune")
            ?.recipient shouldBe RECIPIENT
        amino(send(type = "bank/MsgSend")).singleAminoSendTransfer("uatom")?.recipient shouldBe
            RECIPIENT
    }

    @Test
    fun `a non-send message yields no transfer`() {
        val delegate =
            CosmosMsg(
                type = "cosmos-sdk/MsgDelegate",
                value =
                    """{"delegator_address":"$SENDER","validator_address":"$RECIPIENT",""" +
                        """"amount":{"denom":"uatom","amount":"1000"}}""",
            )

        amino(delegate).singleAminoSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `multiple messages yield no transfer`() {
        amino(send(), send()).singleAminoSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `no messages yield no transfer`() {
        SignAmino(msgs = emptyList()).singleAminoSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `multiple coins yield no transfer`() {
        val msg =
            CosmosMsg(
                type = "cosmos-sdk/MsgSend",
                value =
                    """{"from_address":"$SENDER","to_address":"$RECIPIENT","amount":""" +
                        """[{"denom":"uatom","amount":"1"},{"denom":"uatom","amount":"2"}]}""",
            )

        amino(msg).singleAminoSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `denom mismatch yields no transfer`() {
        amino(send(denom = "ibc/27394FB0")).singleAminoSendTransfer("uatom").shouldBeNull()
        amino(send(denom = "uATOM")).singleAminoSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `missing expected denom yields no transfer`() {
        amino(send()).singleAminoSendTransfer(null).shouldBeNull()
        amino(send()).singleAminoSendTransfer("").shouldBeNull()
    }

    @Test
    fun `an unreadable value yields no transfer`() {
        amino(CosmosMsg(type = "cosmos-sdk/MsgSend", value = "not json"))
            .singleAminoSendTransfer("uatom")
            .shouldBeNull()
        amino(CosmosMsg(type = "cosmos-sdk/MsgSend", value = "[]"))
            .singleAminoSendTransfer("uatom")
            .shouldBeNull()
    }

    @Test
    fun `an empty or missing recipient yields no transfer`() {
        amino(send(recipient = "")).singleAminoSendTransfer("uatom").shouldBeNull()
        val noRecipient =
            CosmosMsg(
                type = "cosmos-sdk/MsgSend",
                value = """{"from_address":"$SENDER","amount":[{"denom":"uatom","amount":"1"}]}""",
            )
        amino(noRecipient).singleAminoSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `a non-integer or numeric-typed amount yields no transfer`() {
        amino(send(amount = "\"1.5\"")).singleAminoSendTransfer("uatom").shouldBeNull()
        amino(send(amount = "\"-1\"")).singleAminoSendTransfer("uatom").shouldBeNull()
        amino(send(amount = "1000")).singleAminoSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `an amount in non-ASCII digits yields no transfer`() {
        amino(send(amount = "\"\u0661\u0660\u0660\""))
            .singleAminoSendTransfer("uatom")
            .shouldBeNull()
        amino(send(amount = "\"\uFF11\uFF10\"")).singleAminoSendTransfer("uatom").shouldBeNull()
    }

    @Test
    fun `extra fields in the message and coin are ignored`() {
        val msg =
            CosmosMsg(
                type = "cosmos-sdk/MsgSend",
                value =
                    """{"from_address":"$SENDER","to_address":"$RECIPIENT","memo":"x",""" +
                        """"amount":[{"denom":"uatom","amount":"1000","extra":1}]}""",
            )

        amino(msg).singleAminoSendTransfer("uatom") shouldBe
            CosmosSignedTransfer(recipient = RECIPIENT, amount = BigInteger("1000"))
    }

    private fun amino(vararg msgs: CosmosMsg) = SignAmino(msgs = msgs.toList())

    private fun send(
        type: String = "cosmos-sdk/MsgSend",
        recipient: String = RECIPIENT,
        denom: String = "uatom",
        amount: String = "\"1000\"",
    ) =
        CosmosMsg(
            type = type,
            value =
                """{"from_address":"$SENDER","to_address":"$recipient",""" +
                    """"amount":[{"denom":"$denom","amount":$amount}]}""",
        )

    private companion object {
        const val SENDER = "cosmos1sender0000000000000000000000000000000"
        const val RECIPIENT = "cosmos1recipientb000000000000000000000000000"
    }
}
