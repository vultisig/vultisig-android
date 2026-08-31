package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.RippleSpecific
import vultisig.keysign.v1.TransactionType

/**
 * Pins the XRPL operation discriminator (proto `RippleSpecific.transaction_type`) round-trip in
 * both directions.
 *
 * It is what separates a token Payment from a TrustSet over the identical coin, and the two sign
 * different bytes. If the initiator states one and the marshalling drops it, the peer rebuilds the
 * other operation and the threshold signature never forms.
 */
@OptIn(ExperimentalSerializationApi::class)
class KeysignPayloadProtoMapperRippleTransactionTypeTest {

    private val outbound = PayloadToProtoMapperImpl()
    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `the operation discriminator survives the KeysignPayload to proto round-trip`() {
        val payload = ripplePayload(TransactionType.TRANSACTION_TYPE_RIPPLE_TRUST_SET)

        val proto = requireNotNull(outbound(payload))
        proto.rippleSpecific?.transactionType shouldBe
            TransactionType.TRANSACTION_TYPE_RIPPLE_TRUST_SET

        val restored = inbound(proto).blockChainSpecific as BlockChainSpecific.Ripple
        restored.transactionType shouldBe TransactionType.TRANSACTION_TYPE_RIPPLE_TRUST_SET
    }

    // A plain XRP send must serialize exactly as it did before the field existed, or every payload
    // in flight between a new and an old device would differ by a byte neither could reconcile.
    @Test
    fun `an unspecified discriminator adds nothing to the wire`() {
        val payload = ripplePayload(TransactionType.TRANSACTION_TYPE_UNSPECIFIED)

        val specific = requireNotNull(requireNotNull(outbound(payload)).rippleSpecific)

        ProtoBuf.encodeToByteArray(RippleSpecific.serializer(), specific) shouldBe
            ProtoBuf.encodeToByteArray(
                RippleSpecific.serializer(),
                RippleSpecific(sequence = 7UL, gas = 400UL, lastLedgerSequence = 12_345UL),
            )

        val restored = inbound(requireNotNull(outbound(payload))).blockChainSpecific
        (restored as BlockChainSpecific.Ripple).transactionType shouldBe
            TransactionType.TRANSACTION_TYPE_UNSPECIFIED
    }

    private fun ripplePayload(transactionType: TransactionType) =
        KeysignPayload(
            coin = RLUSD,
            toAddress = "rMxCKbEDwqr76QuheSUMdEGf4B9xJ8m5De",
            toAmount = BigInteger("1500000000000000"),
            blockChainSpecific =
                BlockChainSpecific.Ripple(
                    sequence = 7UL,
                    gas = 400UL,
                    lastLedgerSequence = 12_345UL,
                    transactionType = transactionType,
                ),
            memo = null,
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        val RLUSD =
            Coin(
                chain = Chain.Ripple,
                ticker = "RLUSD",
                logo = "rlusd",
                address = "rMwdVSrJte3z8zJsdDySGSgBq27xWqt9VW",
                decimal = 15,
                hexPublicKey = "pub",
                priceProviderID = "ripple-usd",
                contractAddress =
                    "524C555344000000000000000000000000000000.rMxCKbEDwqr76QuheSUMdEGf4B9xJ8m5De",
                isNativeToken = false,
            )
    }
}
