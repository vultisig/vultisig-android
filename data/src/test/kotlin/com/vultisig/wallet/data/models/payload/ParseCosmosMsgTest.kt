package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import org.junit.Assert.*
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertFailsWith

@OptIn(ExperimentalEncodingApi::class)
class CosmosMessageParserTest {

    @Test
    fun `test parse basic cosmos message with memo`() {
        val bodyBytes = createTestTxBody(memo = "test memo", messageCount = 1)
        val authInfoBytes = createTestAuthInfo(sequence = 42, gasLimit = 200000)

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = bodyBytes,
            authInfoBytes = authInfoBytes
        )

        val result = parseCosmosMessage(signDirect)

        assertEquals("cosmoshub-4", result.chainId)
        assertEquals("12345", result.accountNumber)
        assertEquals("42", result.sequence)
        assertEquals("test memo", result.memo)
        assertEquals(1, result.messages.size)
    }

    @Test
    fun `test parse message with multiple messages`() {
        val bodyBytes = createTestTxBody(memo = "", messageCount = 3)
        val authInfoBytes = createTestAuthInfo(sequence = 1, gasLimit = 300000)

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "999",
            bodyBytes = bodyBytes,
            authInfoBytes = authInfoBytes
        )

        val result = parseCosmosMessage(signDirect)

        assertEquals(3, result.messages.size)
        assertEquals("1", result.sequence)
    }

    @Test
    fun `test parse message with fee`() {
        val bodyBytes = createTestTxBody(memo = "", messageCount = 1)
        val authInfoBytes = createTestAuthInfoWithFee(
            sequence = 5,
            gasLimit = 150000,
            feeDenom = "uatom",
            feeAmount = "5000"
        )

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "100",
            bodyBytes = bodyBytes,
            authInfoBytes = authInfoBytes
        )

        val result = parseCosmosMessage(signDirect)

        assertEquals(1, result.authInfoFee.amount.size)
        assertEquals("uatom", result.authInfoFee.amount[0].denom)
        assertEquals("5000", result.authInfoFee.amount[0].amount)
    }

    @Test
    fun `test parse message with no signers throws exception`() {
        val bodyBytes = createTestTxBody(memo = "", messageCount = 1)
        val authInfoBytes = createTestAuthInfoNoSigners()

        val signDirect = SignDirectProto(
            chainId = "test-chain",
            accountNumber = "1",
            bodyBytes = bodyBytes,
            authInfoBytes = authInfoBytes
        )

        assertFailsWith<Exception> {
            parseCosmosMessage(signDirect)
        }
    }

    @Test
    fun `test parse message with malformed body bytes`() {
        val authInfoBytes = createTestAuthInfo(sequence = 1, gasLimit = 100000)

        val signDirect = SignDirectProto(
            chainId = "test-chain",
            accountNumber = "1",
            bodyBytes = "invalid-base64-!@#$",
            authInfoBytes = authInfoBytes
        )

        assertFailsWith<Exception> {
            parseCosmosMessage(signDirect)
        }
    }

    @Test
    fun `test parse message with malformed auth info bytes`() {
        val bodyBytes = createTestTxBody(memo = "", messageCount = 1)

        val signDirect = SignDirectProto(
            chainId = "test-chain",
            accountNumber = "1",
            bodyBytes = bodyBytes,
            authInfoBytes = "not-valid-base64"
        )

        assertFailsWith<Exception> {
            parseCosmosMessage(signDirect)
        }
    }

    @Test
    fun `test message type URL preservation`() {
        val typeUrl = "/cosmos.bank.v1beta1.MsgSend"
        val bodyBytes = createTestTxBodyWithTypeUrl(typeUrl)
        val authInfoBytes = createTestAuthInfo(sequence = 1, gasLimit = 100000)

        val signDirect = SignDirectProto(
            chainId = "test-chain",
            accountNumber = "1",
            bodyBytes = bodyBytes,
            authInfoBytes = authInfoBytes
        )

        val result = parseCosmosMessage(signDirect)

        assertEquals(typeUrl, result.messages[0].typeUrl)
    }

    @Test
    fun `test long memo handling`() {
        val longMemo = "a".repeat(1000)
        val bodyBytes = createTestTxBody(memo = longMemo, messageCount = 1)
        val authInfoBytes = createTestAuthInfo(sequence = 1, gasLimit = 100000)

        val signDirect = SignDirectProto(
            chainId = "test-chain",
            accountNumber = "1",
            bodyBytes = bodyBytes,
            authInfoBytes = authInfoBytes
        )

        val result = parseCosmosMessage(signDirect)

        assertEquals(longMemo, result.memo)
    }

    @Test
    fun `test large sequence number`() {
        val bodyBytes = createTestTxBody(memo = "", messageCount = 1)
        val authInfoBytes = createTestAuthInfo(sequence = 999999999L, gasLimit = 100000)

        val signDirect = SignDirectProto(
            chainId = "test-chain",
            accountNumber = "1",
            bodyBytes = bodyBytes,
            authInfoBytes = authInfoBytes
        )

        val result = parseCosmosMessage(signDirect)

        assertEquals("999999999", result.sequence)
    }


    private fun createTestTxBody(memo: String, messageCount: Int): String {
        val messages = (1..messageCount).map {
            createProtobufAny("/test.Message$it", byteArrayOf(0x01, 0x02))
        }

        return encodeTestTxBody(messages, memo)
    }

    private fun createTestTxBodyWithTypeUrl(typeUrl: String): String {
        val message = createProtobufAny(typeUrl, byteArrayOf(0x01, 0x02))
        return encodeTestTxBody(listOf(message), "")
    }

    private fun createTestAuthInfo(sequence: Long, gasLimit: Long): String {
        return encodeTestAuthInfo(sequence, gasLimit, null)
    }

    private fun createTestAuthInfoWithFee(
        sequence: Long,
        gasLimit: Long,
        feeDenom: String,
        feeAmount: String
    ): String {
        return encodeTestAuthInfo(sequence, gasLimit, Pair(feeDenom, feeAmount))
    }

    private fun createTestAuthInfoNoSigners(): String {
        return encodeTestAuthInfoNoSigners()
    }

    private fun createProtobufAny(typeUrl: String, value: ByteArray): Pair<String, ByteArray> {
        return Pair(typeUrl, value)
    }

    private fun encodeTestTxBody(messages: List<Pair<String, ByteArray>>, memo: String): String {
        val writer = ProtobufWriter()

        messages.forEach { (typeUrl, value) ->
            writer.writeMessage(1) { w ->
                w.writeString(1, typeUrl)
                w.writeBytes(2, value)
            }
        }

        if (memo.isNotEmpty()) {
            writer.writeString(2, memo)
        }

        return Base64.encode(writer.toByteArray())
    }

    private fun encodeTestAuthInfo(
        sequence: Long,
        gasLimit: Long,
        fee: Pair<String, String>?
    ): String {
        val writer = ProtobufWriter()

        writer.writeMessage(1) { w ->
            w.writeUInt64(3, sequence)
        }

        if (fee != null) {
            writer.writeMessage(2) { w ->
                w.writeMessage(1) { coinWriter ->
                    coinWriter.writeString(1, fee.first)
                    coinWriter.writeString(2, fee.second)
                }
                w.writeUInt64(2, gasLimit)
            }
        }

        return Base64.encode(writer.toByteArray())
    }

    private fun encodeTestAuthInfoNoSigners(): String {
        val writer = ProtobufWriter()
        return Base64.encode(writer.toByteArray())
    }
}
