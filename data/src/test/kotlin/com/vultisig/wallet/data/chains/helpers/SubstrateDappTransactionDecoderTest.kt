package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.SubstrateSignerPayload
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

class SubstrateDappTransactionDecoderTest {

    private fun decode(
        method: String,
        tip: String = "0x" + "00".repeat(16),
        chain: Chain = Chain.Polkadot,
        extensions: String = "",
    ) =
        SubstrateDappTransactionDecoder.decode(
            payload =
                requireNotNull(
                    SubstrateSignerPayload.fromMemo(
                        """{"method":"$method","genesisHash":"$GENESIS","era":"0xf502",""" +
                            """"nonce":"0x00000047","specVersion":"0x000f4ef8",""" +
                            """"transactionVersion":"0x0000001a","tip":"$tip",""" +
                            """"blockHash":"$BLOCK_HASH"$extensions}"""
                    )
                ),
            chain = chain,
            decimals = 10,
            ticker = "DOT",
        )

    @Test
    fun `a Balances transfer is read out of the call bytes, not the wire fields`() {
        val tx = decode("0x050300" + ALICE + "0700e40b5402")

        val transfer = tx.transfer.shouldNotBeNull()
        transfer.amount shouldBe BigInteger.valueOf(10_000_000_000)
        transfer.recipient shouldBe "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"
        tx.callIndex shouldBe "0x0503"
    }

    @Test
    fun `the recipient is spelled under the chain's SS58 prefix`() {
        decode("0x050300" + ALICE + "04", chain = Chain.Bittensor)
            .transfer
            .shouldNotBeNull()
            .recipient shouldBe "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    }

    @Test
    fun `a recipient that is not an Edwards point is still spelled out`() {
        // Bob's sr25519 dev key: a valid AccountId a transfer can name, but not an ed25519 point,
        // so WalletCore's AnyAddress route left the To row blank for it.
        decode("0x050300" + BOB + "04").transfer.shouldNotBeNull().recipient shouldBe
            "14E5nqKAp3oAJcmzgZhUD2RcptBeUBScxKHgJKU4HPNcKVf3"
        decode("0x050300" + BOB + "04", chain = Chain.Bittensor)
            .transfer
            .shouldNotBeNull()
            .recipient shouldBe "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty"
    }

    @Test
    fun `any other call has no transfer and shows its call index`() {
        val tx = decode("0x0700" + ALICE)

        tx.transfer.shouldBeNull()
        tx.isTransferUnreadable shouldBe false
        tx.callIndex shouldBe "0x0700"
    }

    @Test
    fun `a transfer the reader cannot follow is flagged instead of thrown`() {
        // MultiAddress::Address32 recipient — a legitimate transfer, just not one this reader
        // decodes.
        val tx = decode("0x050302" + ALICE + "0700e40b5402")

        tx.transfer.shouldBeNull()
        tx.isTransferUnreadable shouldBe true
        tx.callIndex shouldBe "0x0503"
        tx.rawJson.isNotEmpty() shouldBe true
    }

    @Test
    fun `every signed field is listed, with the tip in the chain's unit`() {
        val tx = decode("0x0000", tip = "0x0000000000000000000000000012d687")

        tx.fields.map { it.key to it.value } shouldBe
            listOf(
                SubstrateDappTxFieldKey.CALL_DATA to "0x0000",
                SubstrateDappTxFieldKey.NONCE to "71",
                SubstrateDappTxFieldKey.TIP to "0.0001234567 DOT",
                SubstrateDappTxFieldKey.ERA to "0xf502",
                SubstrateDappTxFieldKey.SPEC_VERSION to "1003256",
                SubstrateDappTxFieldKey.TRANSACTION_VERSION to "26",
                SubstrateDappTxFieldKey.GENESIS_HASH to GENESIS,
                SubstrateDappTxFieldKey.BLOCK_HASH to BLOCK_HASH,
            )
    }

    @Test
    fun `a CheckMetadataHash payload lists the mode and the hash it signs`() {
        val tx =
            decode(
                "0x0000",
                extensions =
                    ""","signedExtensions":["CheckMortality","CheckMetadataHash"],""" +
                        """"mode":1,"metadataHash":"$METADATA_HASH"""",
            )

        tx.fields.takeLast(2).map { it.key to it.value } shouldBe
            listOf(
                SubstrateDappTxFieldKey.METADATA_HASH_MODE to "1",
                SubstrateDappTxFieldKey.METADATA_HASH to METADATA_HASH,
            )
    }

    @Test
    fun `a CheckMetadataHash payload under mode 0 lists the mode and no hash`() {
        val tx =
            decode(
                "0x0000",
                extensions =
                    ""","signedExtensions":["CheckMetadataHash"],"mode":0,"metadataHash":null""",
            )

        tx.fields.last().key shouldBe SubstrateDappTxFieldKey.METADATA_HASH_MODE
        tx.fields.last().value shouldBe "0"
        tx.fields.none { it.key == SubstrateDappTxFieldKey.METADATA_HASH } shouldBe true
    }

    @Test
    fun `a CheckMetadataHash mode above 127 is listed unsigned`() {
        val tx =
            decode(
                "0x0000",
                extensions =
                    ""","signedExtensions":["CheckMetadataHash"],"mode":255,"metadataHash":null""",
            )

        tx.fields.last().value shouldBe "255"
    }

    @Test
    fun `a payload without the extension lists neither`() {
        val tx = decode("0x0000", extensions = ""","signedExtensions":["CheckMortality"]""")

        tx.fields.none {
            it.key == SubstrateDappTxFieldKey.METADATA_HASH_MODE ||
                it.key == SubstrateDappTxFieldKey.METADATA_HASH
        } shouldBe true
    }

    private companion object {
        const val GENESIS = "0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val BLOCK_HASH = "0x1f5a9d2c1b8e7f6a5d4c3b2a19087f6e5d4c3b2a19087f6e5d4c3b2a19087f6e"
        const val ALICE = "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
        const val BOB = "8eaf04151687736326c9fea17e25fc5287613693c912909cb226aa4794f26a48"
        const val METADATA_HASH =
            "0x8b2b9e9f1f3a3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5"
    }
}
