package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * The extension recognises its Substrate `signPayload` route from the memo shape alone
 * (`useKeysignMutation.ts`): JSON that parses to an object with a truthy `method` and
 * `genesisHash`. This pins the Android reading to that rule in both directions, because a memo one
 * side treats as a signer payload and the other as a plain memo is a ceremony that signs two
 * different messages.
 */
class SubstrateSignerPayloadTest {

    @Test
    fun `the playground memo is a signer payload with every field kept verbatim`() {
        val payload = SubstrateSignerPayload.fromMemo(PLAYGROUND_MEMO).shouldNotBeNull()

        payload.method shouldBe "0x0000"
        payload.genesisHash shouldBe GENESIS
        payload.era shouldBe "0x0000"
        payload.nonce shouldBe "0x00000000"
        payload.tip shouldBe "0x00000000000000000000000000000000"
        payload.specVersion shouldBe "0x00000000"
        payload.transactionVersion shouldBe "0x00000000"
        payload.blockHash shouldBe "0x" + "00".repeat(32)
        payload.blockNumber shouldBe "0x00000000"
        payload.address shouldBe ""
        payload.rawJson shouldBe PLAYGROUND_MEMO
    }

    @Test
    fun `a memo that is not a JSON object is a plain memo`() {
        SubstrateSignerPayload.fromMemo(null).shouldBeNull()
        SubstrateSignerPayload.fromMemo("").shouldBeNull()
        SubstrateSignerPayload.fromMemo("BOND:abc").shouldBeNull()
        SubstrateSignerPayload.fromMemo("\"0x0000\"").shouldBeNull()
        SubstrateSignerPayload.fromMemo("[\"0x0000\"]").shouldBeNull()
        SubstrateSignerPayload.fromMemo("{not json").shouldBeNull()
    }

    @Test
    fun `an object without a non-empty method and genesisHash is a plain memo`() {
        SubstrateSignerPayload.fromMemo("""{"genesisHash":"$GENESIS"}""").shouldBeNull()
        SubstrateSignerPayload.fromMemo("""{"method":"0x0000"}""").shouldBeNull()
        SubstrateSignerPayload.fromMemo("""{"method":"","genesisHash":"$GENESIS"}""").shouldBeNull()
        SubstrateSignerPayload.fromMemo("""{"method":5,"genesisHash":"$GENESIS"}""").shouldBeNull()
    }

    @Test
    fun `fields the dApp left out read as empty and unknown keys are ignored`() {
        val payload =
            SubstrateSignerPayload.fromMemo(
                    """{"method":"0x0000","genesisHash":"$GENESIS","assetId":null,"mode":0}"""
                )
                .shouldNotBeNull()

        payload.tip shouldBe ""
        payload.tipValue() shouldBe BigInteger.ZERO
        payload.era shouldBe ""
    }

    @Test
    fun `typed readers decode hex the way the extension does`() {
        val payload =
            SubstrateSignerPayload.fromMemo(
                    """{"method":"0x050300","genesisHash":"$GENESIS","nonce":"0x00000047",""" +
                        """"specVersion":"0x000f4ef8","transactionVersion":"0x0000001a",""" +
                        """"tip":"0x0000000000000000000000000012d687","era":"0xf502"}"""
                )
                .shouldNotBeNull()

        payload.nonceValue() shouldBe 71L
        payload.specVersionValue() shouldBe 1_003_256L
        payload.transactionVersionValue() shouldBe 26L
        payload.tipValue() shouldBe BigInteger.valueOf(1_234_567)
        payload.methodBytes().toList() shouldBe listOf<Byte>(5, 3, 0)
        payload.eraBytes().toList() shouldBe listOf(0xf5.toByte(), 0x02)
        payload.callIndexHex() shouldBe "0x0503"
    }

    @Test
    fun `a call shorter than a pallet and call index has no call index`() {
        SubstrateSignerPayload.fromMemo("""{"method":"0x05","genesisHash":"$GENESIS"}""")
            .shouldNotBeNull()
            .callIndexHex()
            .shouldBeNull()
    }

    @Test
    fun `only Polkadot and Bittensor payloads carry the route`() {
        keysignPayload(Chain.Polkadot, PLAYGROUND_MEMO).substrateDappPayload.shouldNotBeNull()
        keysignPayload(Chain.Bittensor, PLAYGROUND_MEMO.replace(GENESIS, BITTENSOR_GENESIS))
            .substrateDappPayload
            .shouldNotBeNull()
        keysignPayload(Chain.Solana, PLAYGROUND_MEMO).substrateDappPayload.shouldBeNull()
        keysignPayload(Chain.Polkadot, null).substrateDappPayload.shouldBeNull()
    }

    // The chain names the network on Verify; the genesis hash is what the signature binds to. Both
    // chains share one ed25519 key, so a Polkadot-labelled payload carrying Bittensor's genesis
    // would sign something valid on Bittensor.
    @Test
    fun `a genesis hash that belongs to another chain is refused`() {
        shouldThrow<IllegalStateException> {
            keysignPayload(Chain.Polkadot, PLAYGROUND_MEMO.replace(GENESIS, BITTENSOR_GENESIS))
                .substrateDappPayload
        }
        shouldThrow<IllegalStateException> {
            keysignPayload(Chain.Bittensor, PLAYGROUND_MEMO).substrateDappPayload
        }
        shouldThrow<IllegalStateException> {
            keysignPayload(Chain.Polkadot, PLAYGROUND_MEMO.replace(GENESIS, "0x" + "ab".repeat(32)))
                .substrateDappPayload
        }
    }

    // The chain binding compares case-insensitively, so the readers behind it must decode the
    // same spelling — a payload the gate accepts and the signer then refuses is a wedged ceremony.
    @Test
    fun `an uppercase hex prefix passes the chain binding and decodes the same bytes`() {
        val upper =
            keysignPayload(Chain.Polkadot, PLAYGROUND_MEMO.replace(GENESIS, GENESIS.uppercase()))
                .substrateDappPayload
                .shouldNotBeNull()
        val lower =
            keysignPayload(Chain.Polkadot, PLAYGROUND_MEMO).substrateDappPayload.shouldNotBeNull()

        upper.genesisHashBytes().toList() shouldBe lower.genesisHashBytes().toList()
    }

    @Test
    fun `typed readers accept the 0X prefix like parseInt and BigInt do`() {
        val payload =
            SubstrateSignerPayload.fromMemo(
                    """{"method":"0X050300","genesisHash":"$GENESIS","nonce":"0X00000047",""" +
                        """"specVersion":"0X000f4ef8","transactionVersion":"0X0000001a",""" +
                        """"tip":"0X0000000000000000000000000012d687","era":"0Xf502"}"""
                )
                .shouldNotBeNull()

        payload.methodBytes().toList() shouldBe listOf<Byte>(5, 3, 0)
        payload.eraBytes().toList() shouldBe listOf(0xf5.toByte(), 0x02)
        payload.nonceValue() shouldBe 71L
        payload.specVersionValue() shouldBe 1_003_256L
        payload.transactionVersionValue() shouldBe 26L
        payload.tipValue() shouldBe BigInteger.valueOf(1_234_567)
    }

    private fun keysignPayload(chain: Chain, memo: String?) =
        KeysignPayload(
            coin =
                Coin(
                    chain = chain,
                    ticker = "X",
                    logo = "",
                    address = "",
                    decimal = 10,
                    hexPublicKey = "",
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            toAddress = "",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.Polkadot(
                    recentBlockHash = "",
                    nonce = BigInteger.ZERO,
                    currentBlockNumber = BigInteger.ZERO,
                    specVersion = 0u,
                    transactionVersion = 0u,
                    genesisHash = GENESIS,
                    gas = 0uL,
                ),
            memo = memo,
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = SigningLibType.DKLS,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        const val GENESIS = "0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val BITTENSOR_GENESIS =
            "0x2f0555cc76fc2840a25a6ea3b9637146806f1f44b090c175ffde2a7e5ab36c03"

        // The memo the extension sends for the Vultisig playground's all-zero raw payload (#5904).
        val PLAYGROUND_MEMO =
            """{"address":"","blockHash":"0x${"00".repeat(32)}","blockNumber":"0x00000000",""" +
                """"era":"0x0000","genesisHash":"$GENESIS","method":"0x0000","nonce":"0x00000000",""" +
                """"specVersion":"0x00000000","tip":"0x${"00".repeat(16)}",""" +
                """"transactionVersion":"0x00000000","signedExtensions":[],"version":4}"""
    }
}
