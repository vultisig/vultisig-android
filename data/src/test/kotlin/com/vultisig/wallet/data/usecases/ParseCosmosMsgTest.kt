@file:OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.*
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ParseCosmosMessageTest {

    private val protoBuf = ProtoBuf { encodeDefaults = false }

    private val parseCosmosMessageUseCaseImpl = ParseCosmosMessageUseCaseImpl(protoBuf)


    @Test
    fun `parseCosmosMessage should successfully parse valid input`() {
        val txBody = createValidTxBody()
        val authInfo = createValidAuthInfo()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)

        assertEquals("cosmoshub-4", result.chainId)
        assertEquals("12345", result.accountNumber)
        assertEquals("42", result.sequence)
        assertEquals("test memo", result.memo)
        assertEquals(1, result.messages.size)
        assertEquals("/cosmos.bank.v1beta1.MsgSend", result.messages[0].typeUrl)
        assertEquals(1, result.authInfoFee.amount.size)
        assertEquals("uatom", result.authInfoFee.amount[0].denom)
        assertEquals("1000", result.authInfoFee.amount[0].amount)
    }

    @Test
    fun `parseCosmosMessage should handle empty memo`() {
        val txBody = createValidTxBody(memo = "")
        val authInfo = createValidAuthInfo()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals("", result.memo)
    }

    @Test
    fun `parseCosmosMessage should handle multiple messages`() {
        val txBody = createValidTxBody(messageCount = 3, memo = "multi-message")
        val authInfo = createValidAuthInfo()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals(3, result.messages.size)
        result.messages.forEach {
            assertEquals("/cosmos.bank.v1beta1.MsgSend", it.typeUrl)
        }
    }

    @Test
    fun `parseCosmosMessage should handle different message types`() {
        val messages = listOf(
            ProtobufAny("/cosmos.bank.v1beta1.MsgSend", ByteArray(10)),
            ProtobufAny("/cosmos.staking.v1beta1.MsgDelegate", ByteArray(15)),
            ProtobufAny("/cosmos.distribution.v1beta1.MsgWithdrawDelegatorReward", ByteArray(20))
        )
        val txBody = TxBody(messages = messages, memo = "mixed messages")
        val authInfo = createValidAuthInfo()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals(3, result.messages.size)
        assertEquals("/cosmos.bank.v1beta1.MsgSend", result.messages[0].typeUrl)
        assertEquals("/cosmos.staking.v1beta1.MsgDelegate", result.messages[1].typeUrl)
        assertEquals("/cosmos.distribution.v1beta1.MsgWithdrawDelegatorReward", result.messages[2].typeUrl)
    }

    @Test
    fun `parseCosmosMessage should handle multiple fee coins`() {
        val authInfo = AuthInfo(
            signerInfos = listOf(
                SignerInfo(
                    publicKey = ProtobufAny("/cosmos.crypto.secp256k1.PubKey", ByteArray(33)),
                    modeInfo = ModeInfo(single = ModeInfoSingle(mode = 1)),
                    sequence = 42UL
                )
            ),
            authInfoFee = AuthInfoFee(
                amount = listOf(
                    Coin(denom = "uatom", amount = "1000"),
                    Coin(denom = "uosmo", amount = "500")
                ),
                gasLimit = 200000UL
            )
        )
        val txBody = createValidTxBody()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals(2, result.authInfoFee.amount.size)
        assertEquals("uatom", result.authInfoFee.amount[0].denom)
        assertEquals("1000", result.authInfoFee.amount[0].amount)
        assertEquals("uosmo", result.authInfoFee.amount[1].denom)
        assertEquals("500", result.authInfoFee.amount[1].amount)
    }

    @Test
    fun `parseCosmosMessage should handle zero fees`() {
        val authInfo = AuthInfo(
            signerInfos = listOf(
                SignerInfo(
                    publicKey = ProtobufAny("/cosmos.crypto.secp256k1.PubKey", ByteArray(33)),
                    modeInfo = ModeInfo(single = ModeInfoSingle(mode = 1)),
                    sequence = 42UL
                )
            ),
            authInfoFee = AuthInfoFee(
                amount = emptyList(),
                gasLimit = 200000UL
            )
        )
        val txBody = createValidTxBody()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals(0, result.authInfoFee.amount.size)
    }

    @Test
    fun `parseCosmosMessage should handle missing authInfoFee`() {
        val authInfo = AuthInfo(
            signerInfos = listOf(
                SignerInfo(
                    publicKey = ProtobufAny("/cosmos.crypto.secp256k1.PubKey", ByteArray(33)),
                    modeInfo = ModeInfo(single = ModeInfoSingle(mode = 1)),
                    sequence = 42UL
                )
            ),
            authInfoFee = null
        )
        val txBody = createValidTxBody()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals(0, result.authInfoFee.amount.size)
    }

    @Test
    fun `parseCosmosMessage should default sequence to 0 when no signerInfos`() {
        val authInfo = AuthInfo(
            signerInfos = emptyList(),
            authInfoFee = AuthInfoFee(
                amount = listOf(Coin("uatom", "1000")),
                gasLimit = 200000UL
            )
        )
        val txBody = createValidTxBody()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals("0", result.sequence)
    }

    @Test
    fun `parseCosmosMessage should handle large sequence numbers`() {
        val authInfo = createValidAuthInfo(sequence = ULong.MAX_VALUE)
        val txBody = createValidTxBody()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals(ULong.MAX_VALUE.toString(), result.sequence)
    }

    @Test
    fun `parseCosmosMessage should handle long memos`() {
        val longMemo = "x".repeat(5000)
        val txBody = createValidTxBody(memo = longMemo)
        val authInfo = createValidAuthInfo()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals(longMemo, result.memo)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseCosmosMessage should throw when chainId is blank`() {
        val txBody = createValidTxBody()
        val authInfo = createValidAuthInfo()

        val signDirect = SignDirectProto(
            chainId = "",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        parseCosmosMessage(signDirect)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseCosmosMessage should throw when accountNumber is blank`() {
        val txBody = createValidTxBody()
        val authInfo = createValidAuthInfo()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        parseCosmosMessage(signDirect)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseCosmosMessage should throw when bodyBytes is blank`() {
        val authInfo = createValidAuthInfo()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = "",
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        parseCosmosMessage(signDirect)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseCosmosMessage should throw when authInfoBytes is blank`() {
        val txBody = createValidTxBody()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = ""
        )

        parseCosmosMessage(signDirect)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decodeTxBodySafe should throw on invalid base64`() {
        decodeTxBodySafe("not-valid-base64!@#$")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decodeAuthInfoSafe should throw on invalid base64`() {
        decodeAuthInfoSafe("not-valid-base64!@#$")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decodeTxBodySafe should throw on invalid protobuf data`() {
        val invalidProtobuf = Base64.encode(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        decodeTxBodySafe(invalidProtobuf)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decodeAuthInfoSafe should throw on invalid protobuf data`() {
        val invalidProtobuf = Base64.encode(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        decodeAuthInfoSafe(invalidProtobuf)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decodeTxBodySafe should throw when TxBody has no messages`() {
        val emptyTxBody = TxBody(
            messages = emptyList(),
            memo = "no messages"
        )
        decodeTxBodySafe(encodeTxBody(emptyTxBody))
    }

    @Test
    fun `parseCosmosMessage should handle TxBody with timeout and unordered fields`() {
        val txBody = TxBody(
            messages = listOf(ProtobufAny("/cosmos.bank.v1beta1.MsgSend", ByteArray(10))),
            memo = "with extras",
            timeoutHeight = 999999UL,
            unordered = true
        )
        val authInfo = createValidAuthInfo()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals("with extras", result.memo)
        assertEquals(1, result.messages.size)
    }

    @Test
    fun `parseCosmosMessage should handle TxBody with timestamp`() {
        val txBody = TxBody(
            messages = listOf(ProtobufAny("/cosmos.bank.v1beta1.MsgSend", ByteArray(10))),
            memo = "with timestamp",
            timeoutTimestamp = Timestamp(seconds = 1234567890L, nanos = 123456789)
        )
        val authInfo = createValidAuthInfo()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals("with timestamp", result.memo)
    }

    @Test
    fun `parseCosmosMessage should handle AuthInfo with tip`() {
        val authInfo = AuthInfo(
            signerInfos = listOf(
                SignerInfo(
                    publicKey = ProtobufAny("/cosmos.crypto.secp256k1.PubKey", ByteArray(33)),
                    modeInfo = ModeInfo(single = ModeInfoSingle(mode = 1)),
                    sequence = 42UL
                )
            ),
            authInfoFee = AuthInfoFee(
                amount = listOf(Coin("uatom", "1000")),
                gasLimit = 200000UL
            ),
            tip = Tip(
                amount = listOf(Coin("uatom", "100")),
                tipper = "cosmos1abc123"
            )
        )
        val txBody = createValidTxBody()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals("42", result.sequence)
        assertEquals(1, result.authInfoFee.amount.size)
    }

    @Test
    fun `parseCosmosMessage should handle different sign modes`() {
        val signModes = listOf(0, 1, 2, 3, 127, 191)

        signModes.forEach { mode ->
            val authInfo = AuthInfo(
                signerInfos = listOf(
                    SignerInfo(
                        publicKey = ProtobufAny("/cosmos.crypto.secp256k1.PubKey", ByteArray(33)),
                        modeInfo = ModeInfo(single = ModeInfoSingle(mode = mode)),
                        sequence = 1UL
                    )
                ),
                authInfoFee = AuthInfoFee(
                    amount = listOf(Coin("uatom", "1000")),
                    gasLimit = 200000UL
                )
            )
            val txBody = createValidTxBody()

            val signDirect = SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo)
            )

            val result = parseCosmosMessage(signDirect)
            assertNotNull(result)
            assertEquals("1", result.sequence)
        }
    }

    @Test
    fun `parseCosmosMessage should handle multi-sig mode info`() {
        val authInfo = AuthInfo(
            signerInfos = listOf(
                SignerInfo(
                    publicKey = ProtobufAny("/cosmos.crypto.multisig.LegacyAminoPubKey", ByteArray(50)),
                    modeInfo = ModeInfo(
                        multi = ModeInfoMulti(
                            bitarray = CompactBitArray(
                                extraBitsStored = 2u,
                                elems = byteArrayOf(0x03)
                            ),
                            modeInfos = listOf(
                                ModeInfo(single = ModeInfoSingle(mode = 1)),
                                ModeInfo(single = ModeInfoSingle(mode = 1))
                            )
                        )
                    ),
                    sequence = 10UL
                )
            ),
            authInfoFee = AuthInfoFee(
                amount = listOf(Coin("uatom", "2000")),
                gasLimit = 300000UL
            )
        )
        val txBody = createValidTxBody()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        assertEquals("10", result.sequence)
    }

    @Test
    fun `Message value should be properly base64 encoded`() {
        val originalBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val txBody = TxBody(
            messages = listOf(
                ProtobufAny("/cosmos.bank.v1beta1.MsgSend", originalBytes)
            ),
            memo = "test"
        )
        val authInfo = createValidAuthInfo()

        val signDirect = SignDirectProto(
            chainId = "cosmoshub-4",
            accountNumber = "12345",
            bodyBytes = encodeTxBody(txBody),
            authInfoBytes = encodeAuthInfo(authInfo)
        )

        val result = parseCosmosMessage(signDirect)
        val decodedValue = Base64.decode(result.messages[0].value)
        assertArrayEquals(originalBytes, decodedValue)
    }

    private fun encodeTxBody(txBody: TxBody): String {
        val bytes = protoBuf.encodeToByteArray(TxBody.serializer(), txBody)
        return Base64.encode(bytes)
    }


    private fun encodeAuthInfo(authInfo: AuthInfo): String {
        val bytes = protoBuf.encodeToByteArray(AuthInfo.serializer(), authInfo)
        return Base64.encode(bytes)
    }

    private fun createValidTxBody(
        memo: String = "test memo",
        messageCount: Int = 1
    ): TxBody {
        val messages = (1..messageCount).map {
            ProtobufAny(
                typeUrl = "/cosmos.bank.v1beta1.MsgSend",
                value = ByteArray(10) { it.toByte() }
            )
        }
        return TxBody(
            messages = messages,
            memo = memo,
            timeoutHeight = 1000UL
        )
    }

    private fun createValidAuthInfo(
        sequence: ULong = 42UL,
        feeAmount: String = "1000",
        feeDenom: String = "uatom"
    ): AuthInfo {
        return AuthInfo(
            signerInfos = listOf(
                SignerInfo(
                    publicKey = ProtobufAny(
                        typeUrl = "/cosmos.crypto.secp256k1.PubKey",
                        value = ByteArray(33) { it.toByte() }
                    ),
                    modeInfo = ModeInfo(
                        single = ModeInfoSingle(mode = 1) // SIGN_MODE_DIRECT
                    ),
                    sequence = sequence
                )
            ),
            authInfoFee = AuthInfoFee(
                amount = listOf(Coin(denom = feeDenom, amount = feeAmount)),
                gasLimit = 200000UL
            )
        )
    }

    private fun parseCosmosMessage(signDirectProto: SignDirectProto): CosmosMessage {
        return parseCosmosMessageUseCaseImpl(signDirectProto)
    }

    private fun decodeTxBodySafe(input: String): TxBody {
        return parseCosmosMessageUseCaseImpl.decodeTxBodySafe(input)
    }

    private fun decodeAuthInfoSafe(input: String): AuthInfo {
        return parseCosmosMessageUseCaseImpl.decodeAuthInfoSafe(input)
    }
}