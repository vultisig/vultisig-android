@file:OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.*
import org.junit.Test

class ParseCosmosMessageTest {

    private val protoBuf = ProtoBuf { encodeDefaults = false }

    // Stub thor bech32 encoder so unit tests don't depend on the WalletCore JNI library.
    // Produces a `thor1`-prefixed 43-char string deterministically derived from the input bytes,
    // satisfying the prefix/length/uniqueness assertions in the address tests below.
    private val testThorEncoder: (ByteArray) -> String = { bytes ->
        val hex = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        "thor1" + (hex + "q".repeat(38)).take(38)
    }

    private val parseCosmosMessageUseCaseImpl =
        ParseCosmosMessageUseCaseImpl(protoBuf, testThorEncoder)

    @Test
    fun `parseCosmosMessage should successfully parse valid input`() {
        val txBody = createValidTxBody()
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
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

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals("", result.memo)
    }

    @Test
    fun `parseCosmosMessage should handle multiple messages`() {
        val txBody = createValidTxBody(messageCount = 3, memo = "multi-message")
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals(3, result.messages.size)
        result.messages.forEach { assertEquals("/cosmos.bank.v1beta1.MsgSend", it.typeUrl) }
    }

    @Test
    fun `parseCosmosMessage should handle different message types`() {
        val messages =
            listOf(
                ProtobufAny("/cosmos.bank.v1beta1.MsgSend", ByteArray(10)),
                ProtobufAny("/cosmos.staking.v1beta1.MsgDelegate", ByteArray(15)),
                ProtobufAny(
                    "/cosmos.distribution.v1beta1.MsgWithdrawDelegatorReward",
                    ByteArray(20),
                ),
            )
        val txBody = TxBody(messages = messages, memo = "mixed messages")
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals(3, result.messages.size)
        assertEquals("/cosmos.bank.v1beta1.MsgSend", result.messages[0].typeUrl)
        assertEquals("/cosmos.staking.v1beta1.MsgDelegate", result.messages[1].typeUrl)
        assertEquals(
            "/cosmos.distribution.v1beta1.MsgWithdrawDelegatorReward",
            result.messages[2].typeUrl,
        )
    }

    @Test
    fun `parseCosmosMessage should handle multiple fee coins`() {
        val authInfo =
            AuthInfo(
                signerInfos =
                    listOf(
                        SignerInfo(
                            publicKey =
                                ProtobufAny("/cosmos.crypto.secp256k1.PubKey", ByteArray(33)),
                            modeInfo = ModeInfo(single = ModeInfoSingle(mode = 1)),
                            sequence = 42UL,
                        )
                    ),
                authInfoFee =
                    AuthInfoFee(
                        amount =
                            listOf(
                                Coin(denom = "uatom", amount = "1000"),
                                Coin(denom = "uosmo", amount = "500"),
                            ),
                        gasLimit = 200000UL,
                    ),
            )
        val txBody = createValidTxBody()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
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
        val authInfo =
            AuthInfo(
                signerInfos =
                    listOf(
                        SignerInfo(
                            publicKey =
                                ProtobufAny("/cosmos.crypto.secp256k1.PubKey", ByteArray(33)),
                            modeInfo = ModeInfo(single = ModeInfoSingle(mode = 1)),
                            sequence = 42UL,
                        )
                    ),
                authInfoFee = AuthInfoFee(amount = emptyList(), gasLimit = 200000UL),
            )
        val txBody = createValidTxBody()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals(0, result.authInfoFee.amount.size)
    }

    @Test
    fun `parseCosmosMessage should handle missing authInfoFee`() {
        val authInfo =
            AuthInfo(
                signerInfos =
                    listOf(
                        SignerInfo(
                            publicKey =
                                ProtobufAny("/cosmos.crypto.secp256k1.PubKey", ByteArray(33)),
                            modeInfo = ModeInfo(single = ModeInfoSingle(mode = 1)),
                            sequence = 42UL,
                        )
                    ),
                authInfoFee = null,
            )
        val txBody = createValidTxBody()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals(0, result.authInfoFee.amount.size)
    }

    @Test
    fun `parseCosmosMessage should default sequence to 0 when no signerInfos`() {
        val authInfo =
            AuthInfo(
                signerInfos = emptyList(),
                authInfoFee =
                    AuthInfoFee(amount = listOf(Coin("uatom", "1000")), gasLimit = 200000UL),
            )
        val txBody = createValidTxBody()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals("0", result.sequence)
    }

    @Test
    fun `parseCosmosMessage should handle large sequence numbers`() {
        val authInfo = createValidAuthInfo(sequence = ULong.MAX_VALUE)
        val txBody = createValidTxBody()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals(ULong.MAX_VALUE.toString(), result.sequence)
    }

    @Test
    fun `parseCosmosMessage should handle long memos`() {
        val longMemo = "x".repeat(5000)
        val txBody = createValidTxBody(memo = longMemo)
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals(longMemo, result.memo)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseCosmosMessage should throw when chainId is blank`() {
        val txBody = createValidTxBody()
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        parseCosmosMessage(signDirect)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseCosmosMessage should throw when accountNumber is blank`() {
        val txBody = createValidTxBody()
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        parseCosmosMessage(signDirect)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseCosmosMessage should throw when bodyBytes is blank`() {
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = "",
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        parseCosmosMessage(signDirect)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseCosmosMessage should throw when authInfoBytes is blank`() {
        val txBody = createValidTxBody()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = "",
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
        val invalidProtobuf =
            Base64.encode(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        decodeTxBodySafe(invalidProtobuf)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decodeAuthInfoSafe should throw on invalid protobuf data`() {
        val invalidProtobuf =
            Base64.encode(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        decodeAuthInfoSafe(invalidProtobuf)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decodeTxBodySafe should throw when TxBody has no messages`() {
        val emptyTxBody = TxBody(messages = emptyList(), memo = "no messages")
        decodeTxBodySafe(encodeTxBody(emptyTxBody))
    }

    @Test
    fun `parseCosmosMessage should handle TxBody with timeout and unordered fields`() {
        val txBody =
            TxBody(
                messages = listOf(ProtobufAny("/cosmos.bank.v1beta1.MsgSend", ByteArray(10))),
                memo = "with extras",
                timeoutHeight = 999999UL,
                unordered = true,
            )
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals("with extras", result.memo)
        assertEquals(1, result.messages.size)
    }

    @Test
    fun `parseCosmosMessage should handle TxBody with timestamp`() {
        val txBody =
            TxBody(
                messages = listOf(ProtobufAny("/cosmos.bank.v1beta1.MsgSend", ByteArray(10))),
                memo = "with timestamp",
                timeoutTimestamp = Timestamp(seconds = 1234567890L, nanos = 123456789),
            )
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals("with timestamp", result.memo)
    }

    @Test
    fun `parseCosmosMessage should handle AuthInfo with tip`() {
        val authInfo =
            AuthInfo(
                signerInfos =
                    listOf(
                        SignerInfo(
                            publicKey =
                                ProtobufAny("/cosmos.crypto.secp256k1.PubKey", ByteArray(33)),
                            modeInfo = ModeInfo(single = ModeInfoSingle(mode = 1)),
                            sequence = 42UL,
                        )
                    ),
                authInfoFee =
                    AuthInfoFee(amount = listOf(Coin("uatom", "1000")), gasLimit = 200000UL),
                tip = Tip(amount = listOf(Coin("uatom", "100")), tipper = "cosmos1abc123"),
            )
        val txBody = createValidTxBody()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals("42", result.sequence)
        assertEquals(1, result.authInfoFee.amount.size)
    }

    @Test
    fun `parseCosmosMessage should handle different sign modes`() {
        val signModes = listOf(0, 1, 2, 3, 127, 191)

        signModes.forEach { mode ->
            val authInfo =
                AuthInfo(
                    signerInfos =
                        listOf(
                            SignerInfo(
                                publicKey =
                                    ProtobufAny("/cosmos.crypto.secp256k1.PubKey", ByteArray(33)),
                                modeInfo = ModeInfo(single = ModeInfoSingle(mode = mode)),
                                sequence = 1UL,
                            )
                        ),
                    authInfoFee =
                        AuthInfoFee(amount = listOf(Coin("uatom", "1000")), gasLimit = 200000UL),
                )
            val txBody = createValidTxBody()

            val signDirect =
                SignDirectProto(
                    chainId = "cosmoshub-4",
                    accountNumber = "12345",
                    bodyBytes = encodeTxBody(txBody),
                    authInfoBytes = encodeAuthInfo(authInfo),
                )

            val result = parseCosmosMessage(signDirect)
            assertNotNull(result)
            assertEquals("1", result.sequence)
        }
    }

    @Test
    fun `parseCosmosMessage should handle multi-sig mode info`() {
        val authInfo =
            AuthInfo(
                signerInfos =
                    listOf(
                        SignerInfo(
                            publicKey =
                                ProtobufAny(
                                    "/cosmos.crypto.multisig.LegacyAminoPubKey",
                                    ByteArray(50),
                                ),
                            modeInfo =
                                ModeInfo(
                                    multi =
                                        ModeInfoMulti(
                                            bitarray =
                                                CompactBitArray(
                                                    extraBitsStored = 2u,
                                                    elems = byteArrayOf(0x03),
                                                ),
                                            modeInfos =
                                                listOf(
                                                    ModeInfo(single = ModeInfoSingle(mode = 1)),
                                                    ModeInfo(single = ModeInfoSingle(mode = 1)),
                                                ),
                                        )
                                ),
                            sequence = 10UL,
                        )
                    ),
                authInfoFee =
                    AuthInfoFee(amount = listOf(Coin("uatom", "2000")), gasLimit = 300000UL),
            )
        val txBody = createValidTxBody()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals("10", result.sequence)
    }

    @Test
    fun `unknown typeUrl should fall back to base64 JsonPrimitive`() {
        val originalBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val txBody =
            TxBody(
                messages = listOf(ProtobufAny("/some.unknown.TypeUrl", originalBytes)),
                memo = "test",
            )
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        val value = result.messages[0].value
        val base64String = (value as JsonPrimitive).contentOrNull
        assertNotNull(base64String)
        assertArrayEquals(originalBytes, Base64.decode(base64String!!))
    }

    @Test
    fun `MsgSend value should be decoded into nested JSON object`() {
        val msgSend =
            MsgSendBody(
                fromAddress = "thor1from",
                toAddress = "thor1to",
                amount = listOf(Coin(denom = "rune", amount = "100000000")),
            )
        val msgBytes = protoBuf.encodeToByteArray(MsgSendBody.serializer(), msgSend)
        val txBody =
            TxBody(
                messages = listOf(ProtobufAny("/cosmos.bank.v1beta1.MsgSend", msgBytes)),
                memo = "test",
            )
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "thorchain-1",
                accountNumber = "108706",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        val value = result.messages[0].value as JsonObject
        assertEquals("thor1from", value["fromAddress"]!!.jsonPrimitive.content)
        assertEquals("thor1to", value["toAddress"]!!.jsonPrimitive.content)
        val amounts = value["amount"]!!.jsonArray
        assertEquals(1, amounts.size)
        assertEquals("rune", amounts[0].jsonObject["denom"]!!.jsonPrimitive.content)
        assertEquals("100000000", amounts[0].jsonObject["amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `types MsgSend value should be decoded with thor1 bech32 addresses`() {
        // 20-byte address payloads -> standard length thor1... bech32 (BIP-0173)
        val fromBytes = ByteArray(20) { it.toByte() }
        val toBytes = ByteArray(20) { (0xFF - it).toByte() }
        val msgSend =
            ThorMsgSendBody(
                fromAddress = fromBytes,
                toAddress = toBytes,
                amount = listOf(Coin(denom = "rune", amount = "100000000")),
            )
        val msgBytes = protoBuf.encodeToByteArray(ThorMsgSendBody.serializer(), msgSend)
        val txBody =
            TxBody(messages = listOf(ProtobufAny("/types.MsgSend", msgBytes)), memo = "test")
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "thorchain-1",
                accountNumber = "108706",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        val value = result.messages[0].value as JsonObject
        val from = value["fromAddress"]!!.jsonPrimitive.content
        val to = value["toAddress"]!!.jsonPrimitive.content
        assertTrue("expected thor1 prefix, got $from", from.startsWith("thor1"))
        assertTrue("expected thor1 prefix, got $to", to.startsWith("thor1"))
        // 20-byte payload -> 32 data chars + 6 checksum chars + "thor1" prefix = 43
        assertEquals(43, from.length)
        assertEquals(43, to.length)
        assertNotEquals(from, to)
        val amounts = value["amount"]!!.jsonArray
        assertEquals(1, amounts.size)
        assertEquals("rune", amounts[0].jsonObject["denom"]!!.jsonPrimitive.content)
        assertEquals("100000000", amounts[0].jsonObject["amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `types MsgDeposit value should decode coins with Asset structure and thor1 signer`() {
        // common.Coin in THORChain is (Asset asset, string amount, int64 decimals) — NOT (denom,
        // amount).
        val signerBytes = ByteArray(20) { (it + 1).toByte() }
        val coin =
            ThorChainCoin(
                asset =
                    ThorChainAsset(
                        chain = "LTC",
                        symbol = "LTC",
                        ticker = "LTC",
                        synth = false,
                        trade = false,
                        secured = false,
                    ),
                amount = "12345",
                decimals = 8L,
            )
        val msgDeposit =
            ThorMsgDepositBody(
                coins = listOf(coin),
                memo = "swap:BTC.BTC:bc1qaddr",
                signer = signerBytes,
            )
        val msgBytes = protoBuf.encodeToByteArray(ThorMsgDepositBody.serializer(), msgDeposit)
        val txBody =
            TxBody(messages = listOf(ProtobufAny("/types.MsgDeposit", msgBytes)), memo = "test")
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "thorchain-1",
                accountNumber = "108706",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        val value = result.messages[0].value as JsonObject
        val signer = value["signer"]!!.jsonPrimitive.content
        assertTrue("expected thor1 prefix, got $signer", signer.startsWith("thor1"))
        assertEquals(43, signer.length)
        assertEquals("swap:BTC.BTC:bc1qaddr", value["memo"]!!.jsonPrimitive.content)
        val coins = value["coins"]!!.jsonArray
        assertEquals(1, coins.size)
        val coin0 = coins[0].jsonObject
        val asset = coin0["asset"]!!.jsonObject
        assertEquals("LTC", asset["chain"]!!.jsonPrimitive.content)
        assertEquals("LTC", asset["symbol"]!!.jsonPrimitive.content)
        assertEquals("LTC", asset["ticker"]!!.jsonPrimitive.content)
        assertEquals("12345", coin0["amount"]!!.jsonPrimitive.content)
        assertEquals("8", coin0["decimals"]!!.jsonPrimitive.content)
    }

    @Test
    fun `cross-typeUrl bytes should fall back to base64 via re-encode check`() {
        // Encode an MsgExecuteContract body and label it as /types.MsgSend. Field 1 in both is
        // length-delimited (wire-type 2), so a naive decoder silently produces a fake MsgSend.
        // The strict round-trip check must reject this and fall back to base64.
        val wrongShape =
            MsgExecuteContractBody(
                sender = "thor1sender",
                contract = "thor1contract",
                msg = "{\"swap\":{}}".toByteArray(),
                funds = emptyList(),
            )
        val msgBytes = protoBuf.encodeToByteArray(MsgExecuteContractBody.serializer(), wrongShape)
        val txBody =
            TxBody(messages = listOf(ProtobufAny("/types.MsgSend", msgBytes)), memo = "test")
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "thorchain-1",
                accountNumber = "108706",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        val value = result.messages[0].value
        val base64String = (value as JsonPrimitive).contentOrNull
        assertNotNull(base64String)
        assertArrayEquals(msgBytes, Base64.decode(base64String!!))
    }

    @Test
    fun `MsgExecuteContract value should be decoded with sender, contract, msg, funds`() {
        val body =
            MsgExecuteContractBody(
                sender = "osmo1sender",
                contract = "osmo1contract",
                msg = "{\"swap\":{\"amount\":\"1\"}}".toByteArray(),
                funds = listOf(Coin(denom = "uosmo", amount = "1000")),
            )
        val msgBytes = protoBuf.encodeToByteArray(MsgExecuteContractBody.serializer(), body)
        val txBody =
            TxBody(
                messages = listOf(ProtobufAny("/cosmwasm.wasm.v1.MsgExecuteContract", msgBytes)),
                memo = "",
            )
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "osmosis-1",
                accountNumber = "1",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        val value = result.messages[0].value as JsonObject
        assertEquals("osmo1sender", value["sender"]!!.jsonPrimitive.content)
        assertEquals("osmo1contract", value["contract"]!!.jsonPrimitive.content)
        assertEquals("{\"swap\":{\"amount\":\"1\"}}", value["msg"]!!.jsonPrimitive.content)
        val funds = value["funds"]!!.jsonArray
        assertEquals(1, funds.size)
        assertEquals("uosmo", funds[0].jsonObject["denom"]!!.jsonPrimitive.content)
        assertEquals("1000", funds[0].jsonObject["amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `MsgTransfer value should be decoded with sender, receiver, token`() {
        val body =
            MsgTransferBody(
                sourcePort = "transfer",
                sourceChannel = "channel-0",
                token = Coin(denom = "uatom", amount = "5000"),
                sender = "cosmos1sender",
                receiver = "osmo1receiver",
                timeoutTimestamp = 1700000000000000000UL,
            )
        val msgBytes = protoBuf.encodeToByteArray(MsgTransferBody.serializer(), body)
        val txBody =
            TxBody(
                messages =
                    listOf(ProtobufAny("/ibc.applications.transfer.v1.MsgTransfer", msgBytes)),
                memo = "",
            )
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "1",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        val value = result.messages[0].value as JsonObject
        assertEquals("transfer", value["sourcePort"]!!.jsonPrimitive.content)
        assertEquals("channel-0", value["sourceChannel"]!!.jsonPrimitive.content)
        assertEquals("cosmos1sender", value["sender"]!!.jsonPrimitive.content)
        assertEquals("osmo1receiver", value["receiver"]!!.jsonPrimitive.content)
        val token = value["token"]!!.jsonObject
        assertEquals("uatom", token["denom"]!!.jsonPrimitive.content)
        assertEquals("5000", token["amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `known typeUrl with malformed bytes should fall back to base64 JsonPrimitive`() {
        val malformedBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val txBody =
            TxBody(
                messages = listOf(ProtobufAny("/cosmos.bank.v1beta1.MsgSend", malformedBytes)),
                memo = "test",
            )
        val authInfo = createValidAuthInfo()

        val signDirect =
            SignDirectProto(
                chainId = "cosmoshub-4",
                accountNumber = "12345",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        val value = result.messages[0].value
        val base64String = (value as JsonPrimitive).contentOrNull
        assertNotNull(base64String)
        assertArrayEquals(malformedBytes, Base64.decode(base64String!!))
    }

    @Test
    fun `Fee should include gasLimit from AuthInfoFee`() {
        val txBody = createValidTxBody()
        val authInfo =
            AuthInfo(
                signerInfos =
                    listOf(
                        SignerInfo(
                            publicKey =
                                ProtobufAny("/cosmos.crypto.secp256k1.PubKey", ByteArray(33)),
                            modeInfo = ModeInfo(single = ModeInfoSingle(mode = 1)),
                            sequence = 42UL,
                        )
                    ),
                authInfoFee = AuthInfoFee(amount = listOf(Coin("rune", "0")), gasLimit = 99368UL),
            )

        val signDirect =
            SignDirectProto(
                chainId = "thorchain-1",
                accountNumber = "108706",
                bodyBytes = encodeTxBody(txBody),
                authInfoBytes = encodeAuthInfo(authInfo),
            )

        val result = parseCosmosMessage(signDirect)
        assertEquals("99368", result.authInfoFee.gasLimit)
    }

    private fun encodeTxBody(txBody: TxBody): String {
        val bytes = protoBuf.encodeToByteArray(TxBody.serializer(), txBody)
        return Base64.encode(bytes)
    }

    private fun encodeAuthInfo(authInfo: AuthInfo): String {
        val bytes = protoBuf.encodeToByteArray(AuthInfo.serializer(), authInfo)
        return Base64.encode(bytes)
    }

    private fun createValidTxBody(memo: String = "test memo", messageCount: Int = 1): TxBody {
        val messages =
            (1..messageCount).map {
                ProtobufAny(
                    typeUrl = "/cosmos.bank.v1beta1.MsgSend",
                    value = ByteArray(10) { it.toByte() },
                )
            }
        return TxBody(messages = messages, memo = memo, timeoutHeight = 1000UL)
    }

    private fun createValidAuthInfo(
        sequence: ULong = 42UL,
        feeAmount: String = "1000",
        feeDenom: String = "uatom",
    ): AuthInfo {
        return AuthInfo(
            signerInfos =
                listOf(
                    SignerInfo(
                        publicKey =
                            ProtobufAny(
                                typeUrl = "/cosmos.crypto.secp256k1.PubKey",
                                value = ByteArray(33) { it.toByte() },
                            ),
                        modeInfo =
                            ModeInfo(
                                single = ModeInfoSingle(mode = 1) // SIGN_MODE_DIRECT
                            ),
                        sequence = sequence,
                    )
                ),
            authInfoFee =
                AuthInfoFee(
                    amount = listOf(Coin(denom = feeDenom, amount = feeAmount)),
                    gasLimit = 200000UL,
                ),
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
