package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.SubstrateSignerPayload
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

class SubstrateDappTransactionDecoderTest {

    private fun decode(method: String, tip: String = "0x" + "00".repeat(16)) =
        SubstrateDappTransactionDecoder.decode(
            payload =
                requireNotNull(
                    SubstrateSignerPayload.fromMemo(
                        """{"method":"$method","genesisHash":"$GENESIS","era":"0xf502",""" +
                            """"nonce":"0x00000047","specVersion":"0x000f4ef8",""" +
                            """"transactionVersion":"0x0000001a","tip":"$tip",""" +
                            """"blockHash":"$BLOCK_HASH"}"""
                    )
                ),
            chain = Chain.Polkadot,
            decimals = 10,
            ticker = "DOT",
            ss58Encode = { accountId, chain -> "ss58(${chain.raw}):" + accountId.size },
        )

    @Test
    fun `a Balances transfer is read out of the call bytes, not the wire fields`() {
        val tx = decode("0x050300" + ALICE + "0700e40b5402")

        val transfer = tx.transfer.shouldNotBeNull()
        transfer.amount shouldBe BigInteger.valueOf(10_000_000_000)
        transfer.recipient shouldBe "ss58(Polkadot):32"
        tx.callIndex shouldBe "0x0503"
    }

    @Test
    fun `any other call has no transfer and shows its call index`() {
        val tx = decode("0x0700" + ALICE)

        tx.transfer.shouldBeNull()
        tx.callIndex shouldBe "0x0700"
    }

    @Test
    fun `every signed field is listed, with the tip in the chain's unit`() {
        val tx = decode("0x0000", tip = "0x0000000000000000000000000012d687")

        tx.fields.map { it.key } shouldBe SubstrateDappTxFieldKey.entries
        tx.fields.associate { it.key to it.value } shouldBe
            mapOf(
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

    private companion object {
        const val GENESIS = "0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val BLOCK_HASH = "0x1f5a9d2c1b8e7f6a5d4c3b2a19087f6e5d4c3b2a19087f6e5d4c3b2a19087f6e"
        const val ALICE = "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
    }
}
