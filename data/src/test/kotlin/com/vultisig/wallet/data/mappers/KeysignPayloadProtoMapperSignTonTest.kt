package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SignTon
import vultisig.keysign.v1.TonMessage

/**
 * Pins the `signTon` (TON Connect messages) proto round-trip in both directions.
 *
 * The outbound mapper dropped `signTon`, so any payload it relayed would reach the peer with
 * `signTon == null`. [TonHelper] then takes its native-send fallback and builds a single plain
 * transfer to `toAddress`/`toAmount` with the memo as a text comment — discarding messages 2..N and
 * message 1's `payload`/`stateInit` BOC. That signing input hashes differently from the
 * initiator's, so the DKLS setup message (keyed by `md5(hash)`) 404s and keysign never completes.
 * TON Connect requests routinely carry more than one message, so the fallback is not a near-miss.
 *
 * Only the extension originates `signTon` today, and Android never re-serializes a payload it
 * joined, so the gap was latent rather than a shipped regression.
 */
class KeysignPayloadProtoMapperSignTonTest {

    private val outbound = PayloadToProtoMapperImpl()
    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `signTon survives the KeysignPayload to proto round-trip`() {
        val signTon =
            SignTon(
                tonMessages =
                    listOf(
                        TonMessage(
                            to = "UQAX2SbFUCkNC6BLKBnrf7ilNfBTMKgLNqmXHrBAqi9Xm000",
                            amount = "100000000",
                            payload = "te6ccgEBAQEADgAAGAAAAABoZWxsbwAAAA==", // opaque BOC
                        ),
                        // A second message is what the native-send fallback silently drops.
                        TonMessage(
                            to = "UQD1a2Zt0hMLnRoSY9jLbnJHRoLKrpUuoRSZzvsYUAAAAAAA",
                            amount = "250000000",
                            stateInit = "te6ccgEBAQEAAgAAAA==",
                        ),
                    )
            )

        val payload =
            KeysignPayload(
                coin = TON,
                toAddress = "UQAX2SbFUCkNC6BLKBnrf7ilNfBTMKgLNqmXHrBAqi9Xm000",
                toAmount = BigInteger("100000000"),
                blockChainSpecific =
                    BlockChainSpecific.Ton(
                        sequenceNumber = 12uL,
                        expireAt = 1_775_000_000uL,
                        bounceable = false,
                        sendMaxAmount = false,
                        jettonAddress = "",
                        isActiveDestination = true,
                    ),
                memo = null,
                vaultPublicKeyECDSA = "pub",
                vaultLocalPartyID = "local",
                libType = null,
                wasmExecuteContractPayload = null,
                signTon = signTon,
            )

        val proto = requireNotNull(outbound(payload))
        // The outbound mapper must carry signTon onto the wire.
        assertEquals(signTon, proto.signTon)

        // …and the inbound mapper must restore every message on the peer device.
        val restored = inbound(proto)
        assertEquals(signTon, restored.signTon)
        assertEquals(signTon.tonMessages, restored.signTon?.tonMessages)
    }

    private companion object {
        val TON =
            Coin(
                chain = Chain.Ton,
                ticker = "TON",
                logo = "ton",
                address = "UQD1a2Zt0hMLnRoSY9jLbnJHRoLKrpUuoRSZzvsYUAAAAAAA",
                decimal = 9,
                hexPublicKey = "3b6a27bcceb6a42d62a3a8d02a6f0d73653215771de243a63ac048a18b59da29",
                priceProviderID = "the-open-network",
                contractAddress = "",
                isNativeToken = true,
            )
    }
}
