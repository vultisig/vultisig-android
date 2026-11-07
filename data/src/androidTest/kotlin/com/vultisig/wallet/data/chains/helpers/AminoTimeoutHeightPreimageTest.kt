package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.WalletCoreNative
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import vultisig.keysign.v1.CosmosCoin
import vultisig.keysign.v1.CosmosFee
import vultisig.keysign.v1.CosmosMsg
import vultisig.keysign.v1.SignAmino
import vultisig.keysign.v1.TransactionType
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.TransactionCompiler.PreSigningOutput

/**
 * A dApp amino sign doc's tx-level `timeout_height` must reach the signed preimage, or a co-signer
 * hashes different bytes than the initiator and the chain rejects the broadcast.
 *
 * The Cosmos fixture and its expected preimage are the SDK's
 * (`packages/core/mpc/keysign/signingInputs/resolvers/cosmos/index.signAmino.test.ts`), so this
 * device and an SDK / Windows initiator converge on the same bytes. Instrumented because the
 * preimage comes from WalletCore's JNI.
 */
@OptIn(ExperimentalSerializationApi::class)
class AminoTimeoutHeightPreimageTest {

    private val receiver = "osmo1qypqxpq9qcrsszg2pvxq6rs0zqg3yyc5lzv7xu"

    private lateinit var publicKeyHex: String
    private lateinit var sender: String

    @Before
    fun setUp() {
        WalletCoreNative.ensureLoaded()
        val publicKey = PrivateKey(ByteArray(32) { 1 }).getPublicKeySecp256k1(true)
        publicKeyHex = publicKey.data().toHexString()
        sender = AnyAddress(publicKey, CoinType.COSMOS).description()
    }

    @Test
    fun cosmosSignsTheDappTimeoutHeight() {
        assertEquals(expectedPreimage(timeoutHeight = "33737015"), cosmosPreimage(33737015uL))
    }

    @Test
    fun cosmosLeavesAnUnsetTimeoutHeightOutOfTheSignDoc() {
        assertEquals(expectedPreimage(timeoutHeight = null), cosmosPreimage(null))
    }

    @Test
    fun cosmosLeavesAZeroTimeoutHeightOutOfTheSignDoc() {
        assertEquals(expectedPreimage(timeoutHeight = null), cosmosPreimage(0uL))
    }

    @Test
    fun thorChainSignsTheDappTimeoutHeight() {
        assertNotEquals(thorChainHashes(null), thorChainHashes(33737015uL))
    }

    @Test
    fun thorChainTreatsZeroAsUnset() {
        assertEquals(thorChainHashes(null), thorChainHashes(0uL))
    }

    private fun cosmosPreimage(timeoutHeight: ULong?): String {
        val payload =
            payload(
                chain = Chain.GaiaChain,
                ticker = "ATOM",
                address = sender,
                specific =
                    BlockChainSpecific.Cosmos(
                        accountNumber = BigInteger.valueOf(3393979),
                        sequence = BigInteger.valueOf(14),
                        gas = BigInteger.ZERO,
                        ibcDenomTraces = null,
                        transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                    ),
                signAmino = wireDecoded(signAmino(timeoutHeight)),
            )
        val input =
            CosmosHelper(coinType = CoinType.COSMOS, denom = "uatom").getPreSignedInputData(payload)
        val output =
            PreSigningOutput.parseFrom(TransactionCompiler.preImageHashes(CoinType.COSMOS, input))
        assertEquals("", output.errorMessage)
        return output.data.toStringUtf8()
    }

    private fun thorChainHashes(timeoutHeight: ULong?): List<String> {
        val publicKey = PrivateKey(ByteArray(32) { 1 }).getPublicKeySecp256k1(true)
        val payload =
            payload(
                chain = Chain.ThorChain,
                ticker = "RUNE",
                address = AnyAddress(publicKey, CoinType.THORCHAIN).description(),
                specific =
                    BlockChainSpecific.THORChain(
                        accountNumber = BigInteger.valueOf(3393979),
                        sequence = BigInteger.valueOf(14),
                        fee = BigInteger.valueOf(2_000_000),
                        isDeposit = false,
                        transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                    ),
                signAmino = wireDecoded(signAmino(timeoutHeight)),
            )
        return ThorChainHelper(
                vaultHexPublicKey = publicKeyHex,
                vaultHexChainCode = "",
                ticker = "RUNE",
                hrp = null,
                networkId = "thorchain-1",
                gasUnit = ThorChainHelper.THOR_CHAIN_GAS_UNIT,
            )
            .getPreSignedImageHash(payload)
    }

    // A co-signer derives its preimage from the payload as decoded off the wire.
    private fun wireDecoded(signAmino: SignAmino): SignAmino =
        ProtoBuf.decodeFromByteArray(ProtoBuf.encodeToByteArray(signAmino))

    private fun signAmino(timeoutHeight: ULong?) =
        SignAmino(
            fee =
                CosmosFee(
                    amount = listOf(CosmosCoin(denom = "uatom", amount = "5802")),
                    gas = "193367",
                ),
            msgs = listOf(CosmosMsg(type = "cosmos-sdk/MsgTransfer", value = msgTransferValue())),
            timeoutHeight = timeoutHeight,
        )

    private fun msgTransferValue(): String =
        Json.encodeToString(
            buildJsonObject {
                put("source_port", "transfer")
                put("source_channel", "channel-141")
                putJsonObject("token") {
                    put("denom", "uatom")
                    put("amount", "330699")
                }
                put("sender", sender)
                put("receiver", receiver)
                putJsonObject("timeout_height") {
                    put("revision_height", "74177604")
                    put("revision_number", "1")
                }
            }
        )

    private fun expectedPreimage(timeoutHeight: String?): String =
        """{"account_number":"3393979","chain_id":"cosmoshub-4",""" +
            """"fee":{"amount":[{"amount":"5802","denom":"uatom"}],"gas":"193367"},""" +
            """"memo":"OsmosisFE","msgs":[{"type":"cosmos-sdk/MsgTransfer","value":{""" +
            """"receiver":"$receiver","sender":"$sender",""" +
            """"source_channel":"channel-141","source_port":"transfer",""" +
            """"timeout_height":{"revision_height":"74177604","revision_number":"1"},""" +
            """"token":{"amount":"330699","denom":"uatom"}}}],"sequence":"14"""" +
            (timeoutHeight?.let { ""","timeout_height":"$it"""" } ?: "") +
            "}"

    private fun payload(
        chain: Chain,
        ticker: String,
        address: String,
        specific: BlockChainSpecific,
        signAmino: SignAmino,
    ) =
        KeysignPayload(
            coin =
                Coin(
                    chain = chain,
                    ticker = ticker,
                    logo = "",
                    address = address,
                    decimal = 6,
                    hexPublicKey = publicKeyHex,
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            toAddress = receiver,
            toAmount = BigInteger.ZERO,
            blockChainSpecific = specific,
            memo = "OsmosisFE",
            vaultPublicKeyECDSA = publicKeyHex,
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
            signAmino = signAmino,
        )
}
