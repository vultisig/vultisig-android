package com.vultisig.wallet.data.blockchain.cosmos.gov

import com.vultisig.wallet.data.blockchain.cosmos.CosmosSignDocReader
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import java.math.BigInteger
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import vultisig.keysign.v1.TransactionType

class CosmosGovVoteSignDataResolverTests {

    private val voter = "dydx1voter0000000000000000000000000000000"
    private val pubKeyHex = "03" + "11".repeat(32)
    private val dydxFee = BigInteger("2500000000000000")

    private fun specific() =
        BlockChainSpecific.Cosmos(
            accountNumber = BigInteger.valueOf(123456),
            sequence = BigInteger.valueOf(42),
            gas = dydxFee,
            ibcDenomTraces = null,
            transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
        )

    private fun resolve(
        vote: CosmosGovVote = CosmosGovVote(proposalId = 7uL, option = CosmosVoteOption.YES),
        chainSpecific: BlockChainSpecific.Cosmos = specific(),
        hexPublicKey: String = pubKeyHex,
    ) =
        CosmosGovVoteSignDataResolver.resolve(
            vote = vote,
            voterAddress = voter,
            hexPublicKey = hexPublicKey,
            chainId = "dydx-mainnet-1",
            chainSpecific = chainSpecific,
            feeDenom = "adydx",
            staticGasLimit = 200_000L,
        )

    @ParameterizedTest
    @EnumSource(CosmosVoteOption::class)
    fun `body is exactly one MsgVote with the proposal, voter and ballot and no memo`(
        option: CosmosVoteOption
    ) {
        val signDirect = resolve(CosmosGovVote(proposalId = 7uL, option = option))

        val body = Proto.fields(Base64.getDecoder().decode(signDirect.bodyBytes))
        assertEquals(listOf(1), body.map { it.tag }, "only TxBody.messages, no memo")

        val any = Proto.fields(body.single().bytes)
        assertEquals("/cosmos.gov.v1beta1.MsgVote", any.string(1))
        val msg = Proto.fields(any.bytes(2))
        assertEquals(7L, msg.varint(1))
        assertEquals(voter, msg.string(2))
        assertEquals(option.protoValue, msg.varint(3))
    }

    @Test
    fun `ballots carry the cosmos VoteOption enum values`() {
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            listOf(
                    CosmosVoteOption.YES,
                    CosmosVoteOption.ABSTAIN,
                    CosmosVoteOption.NO,
                    CosmosVoteOption.NO_WITH_VETO,
                )
                .map { it.protoValue },
        )
    }

    @Test
    fun `the decoder co-signers use reads the body as a vote with an empty memo`() {
        val signDirect = resolve()

        val read = CosmosSignDocReader.read(Base64.getDecoder().decode(signDirect.bodyBytes))

        assertEquals(DecodedOperation.Vote, read?.reading?.operation)
        assertNull(read?.memo)
    }

    @Test
    fun `auth info carries the key, sequence, direct mode and the dydx fee at the static limit`() {
        val signDirect = resolve()

        assertEquals("dydx-mainnet-1", signDirect.chainId)
        assertEquals("123456", signDirect.accountNumber)

        val authInfo = Proto.fields(Base64.getDecoder().decode(signDirect.authInfoBytes))
        val signerInfo = Proto.fields(authInfo.bytes(1))
        val pubKeyAny = Proto.fields(signerInfo.bytes(1))
        assertEquals("/cosmos.crypto.secp256k1.PubKey", pubKeyAny.string(1))
        assertContentEquals(
            pubKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
            Proto.fields(pubKeyAny.bytes(2)).bytes(1),
        )
        val single = Proto.fields(Proto.fields(signerInfo.bytes(2)).bytes(1))
        assertEquals(1L, single.varint(1), "SIGN_MODE_DIRECT")
        assertEquals(42L, signerInfo.varint(3))

        val fee = Proto.fields(authInfo.bytes(2))
        val coin = Proto.fields(fee.bytes(1))
        assertEquals("adydx", coin.string(1))
        assertEquals("2500000000000000", coin.string(2))
        assertEquals(200_000L, fee.varint(2))
    }

    @Test
    fun `a proposal id above Long MAX_VALUE encodes as its uint64 value`() {
        val signDirect =
            resolve(CosmosGovVote(proposalId = ULong.MAX_VALUE, option = CosmosVoteOption.NO))

        val body = Proto.fields(Base64.getDecoder().decode(signDirect.bodyBytes))
        val msg = Proto.fields(Proto.fields(body.single().bytes).bytes(2))
        assertEquals(ULong.MAX_VALUE, msg.varint(1).toULong())
    }

    @Test
    fun `a malformed public key is refused before any bytes are built`() {
        assertFailsWith<IllegalArgumentException> { resolve(hexPublicKey = "04" + "11".repeat(64)) }
    }

    @Test
    fun `proposal id parsing accepts positive uint64 digits`() {
        assertEquals(1uL, CosmosGovVote.parseProposalId("1"))
        assertEquals(42uL, CosmosGovVote.parseProposalId("042"))
        assertEquals(ULong.MAX_VALUE, CosmosGovVote.parseProposalId("18446744073709551615"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["", " ", "0", "-1", "+1", "1.0", "1,000", "1 000", "١", "18446744073709551616"]
    )
    fun `proposal id parsing refuses anything but a positive uint64`(text: String) {
        assertNull(CosmosGovVote.parseProposalId(text))
    }

    /** Minimal proto wire reader, test-only, so the production encoder stays the only writer. */
    internal object Proto {
        class Field(val tag: Int, val varint: Long?, val bytes: ByteArray)

        fun fields(data: ByteArray): List<Field> {
            val out = mutableListOf<Field>()
            var i = 0
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    val b = data[i++].toInt() and 0xFF
                    result = result or ((b.toLong() and 0x7F) shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            while (i < data.size) {
                val key = varint()
                val tag = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> out.add(Field(tag, varint(), ByteArray(0)))
                    2 -> {
                        val len = varint().toInt()
                        out.add(Field(tag, null, data.copyOfRange(i, i + len)))
                        i += len
                    }
                    else -> error("Unsupported wire type")
                }
            }
            return out
        }
    }
}

private fun List<CosmosGovVoteSignDataResolverTests.Proto.Field>.bytes(tag: Int): ByteArray =
    single {
        it.tag == tag && it.varint == null
    }.bytes

private fun List<CosmosGovVoteSignDataResolverTests.Proto.Field>.string(tag: Int): String =
    String(bytes(tag), Charsets.UTF_8)

private fun List<CosmosGovVoteSignDataResolverTests.Proto.Field>.varint(tag: Int): Long =
    single { it.tag == tag }.varint!!
