package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the `signDirect` (Cosmos SignDoc) proto round-trip in both directions.
 *
 * Regression guard for the LUNA / LUNC staking cross-device bug: the outbound mapper used to drop
 * `signDirect`, so a relayed payload reached the peer device with `signDirect == null`. The peer
 * then rebuilt a default bank-send signing input whose message hash diverged from the initiator's
 * `MsgDelegate` hash — and since the DKLS setup message is keyed by `md5(hash)`, the peer's
 * `getSetupMessage` 404'd and keysign never completed. Keeping forward/reverse symmetric is what
 * makes multi-device staking sign the same bytes on every participant.
 */
class KeysignPayloadProtoMapperSignDirectTest {

    private val outbound = PayloadToProtoMapperImpl()
    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `signDirect survives the KeysignPayload to proto round-trip`() {
        val signDirect =
            SignDirectProto(
                bodyBytes = "Cg0vY29zbW9zLk1zZ0RlbGVnYXRl", // arbitrary, opaque to the mapper
                authInfoBytes = "EgQKAggB",
                chainId = "columbus-5",
                accountNumber = "12345",
            )

        val payload =
            KeysignPayload(
                coin = LUNC,
                toAddress = "terravaloper1l3zgemxwql5fpa6p9z6h00000000000abcd",
                toAmount = BigInteger("249770649"),
                blockChainSpecific =
                    BlockChainSpecific.Cosmos(
                        accountNumber = BigInteger.valueOf(12345),
                        sequence = BigInteger.valueOf(7),
                        gas = BigInteger.valueOf(100_000_000),
                        ibcDenomTraces = null,
                        transactionType =
                            vultisig.keysign.v1.TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                    ),
                memo = null,
                vaultPublicKeyECDSA = "pub",
                vaultLocalPartyID = "local",
                libType = null,
                wasmExecuteContractPayload = null,
                signDirect = signDirect,
            )

        val proto = requireNotNull(outbound(payload))
        // The outbound mapper must carry signDirect onto the wire.
        assertEquals(signDirect, proto.signDirect)

        // …and the inbound mapper must restore it byte-for-byte on the peer device.
        val restored = inbound(proto)
        assertEquals(signDirect, restored.signDirect)
        assertEquals(signDirect.bodyBytes, restored.signDirect?.bodyBytes)
        assertEquals(signDirect.authInfoBytes, restored.signDirect?.authInfoBytes)
        assertEquals(signDirect.chainId, restored.signDirect?.chainId)
        assertEquals(signDirect.accountNumber, restored.signDirect?.accountNumber)
    }

    private companion object {
        val LUNC =
            Coin(
                chain = Chain.TerraClassic,
                ticker = "LUNC",
                logo = "lunc",
                address = "terra1pxpxmdrnv66w0000000000000000000000abcd",
                decimal = 6,
                hexPublicKey = "020202020202020202020202020202020202020202020202020202020202020202",
                priceProviderID = "terra-luna",
                contractAddress = "",
                isNativeToken = true,
            )
    }
}
