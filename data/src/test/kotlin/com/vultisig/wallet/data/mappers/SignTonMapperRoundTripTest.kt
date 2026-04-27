package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SignTon
import com.vultisig.wallet.data.models.payload.TonMessage
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * End-to-end coverage that a SignTon payload survives the domain → proto → domain round trip
 * without losing fields. The proto wire format only carries `to`, `amount`, `payload`, and
 * `state_init` per `commondata` — anything else is local-only and tested against by inference here.
 */
class SignTonMapperRoundTripTest {

    private val toProto: PayloadToProtoMapper = PayloadToProtoMapperImpl()
    private val fromProto: KeysignPayloadProtoMapper = KeysignPayloadProtoMapperImpl()

    private fun baseTonPayload(signTon: SignTon?) =
        KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Ton,
                    ticker = "TON",
                    logo = "ton",
                    address = "EQAOurAddress0000000000000000000000000000000000000",
                    decimal = 9,
                    hexPublicKey = "00",
                    priceProviderID = "the-open-network",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            toAddress = "EQAOurAddress0000000000000000000000000000000000000",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.Ton(sequenceNumber = 0u, expireAt = 0u, bounceable = true),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = SigningLibType.DKLS,
            wasmExecuteContractPayload = null,
            signTon = signTon,
        )

    @Test
    fun `null signTon round trips as null`() {
        val payload = baseTonPayload(signTon = null)
        val proto = toProto(payload)
        assertNotNull(proto)
        val back = fromProto(proto!!)
        assertNull(back.signTon)
    }

    @Test
    fun `single message survives round trip with all fields`() {
        val signTon =
            SignTon(
                listOf(
                    TonMessage(
                        toAddress = "EQAB1234567890",
                        toAmount = 1_500_000_000L,
                        payload = "te6cc-payload-bytes",
                        stateInit = "te6cc-stateinit-bytes",
                    )
                )
            )
        val proto = toProto(baseTonPayload(signTon))
        val back = fromProto(proto!!)
        assertEquals(signTon, back.signTon)
    }

    @Test
    fun `four messages survive round trip preserving order`() {
        val msgs =
            (1..SignTon.MAX_MESSAGES).map { i ->
                TonMessage(
                    toAddress = "EQAB-$i",
                    toAmount = i * 1_000L,
                    payload = if (i % 2 == 0) "p$i" else "",
                    stateInit = if (i == 1) "s1" else "",
                )
            }
        val signTon = SignTon(msgs)
        val proto = toProto(baseTonPayload(signTon))
        val back = fromProto(proto!!)
        assertEquals(signTon, back.signTon)
    }

    @Test
    fun `empty optional fields stay empty after round trip`() {
        val signTon = SignTon(listOf(TonMessage(toAddress = "EQAB-only-required", toAmount = 42L)))
        val proto = toProto(baseTonPayload(signTon))
        val back = fromProto(proto!!)
        assertEquals("", back.signTon!!.messages.single().payload)
        assertEquals("", back.signTon!!.messages.single().stateInit)
    }
}
