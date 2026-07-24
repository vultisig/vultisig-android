package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SignSolana

/**
 * Pins the `signSolana` (native-staking pre-built raw transaction) proto round-trip in both
 * directions.
 *
 * Regression guard for the multi-device (secure vault) Solana staking bug: the outbound mapper used
 * to drop `signSolana`, so a relayed payload reached the co-signer with `signSolana == null`. The
 * peer then rebuilt a default plain-transfer signing input (to the stake account) whose message
 * hash diverged from the initiator's deactivate/delegate hash — and since the DKLS setup message is
 * keyed by `md5(hash)`, the peer's `getSetupMessage` 404'd and keysign never completed (the
 * initiator timed out with "no messages from <peer> in 60s"). Keeping forward/reverse symmetric is
 * what makes delegate / unstake / move / finish-move / withdraw sign the same bytes on every
 * participant.
 */
class KeysignPayloadProtoMapperSignSolanaTest {

    private val outbound = PayloadToProtoMapperImpl()
    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `signSolana survives the KeysignPayload to proto round-trip`() {
        val signSolana =
            SignSolana(
                rawTransactions = listOf("AQABBGVzdGFraW5nLXJhdy10eC1ieXRlcw==") // opaque base64
            )

        val payload =
            KeysignPayload(
                coin = SOL,
                toAddress = "CV4XJA9f4HUNm1o2YG4rt2Q3abU8x84ywMg6iSjYNNFz",
                toAmount = BigInteger("1000301676"),
                blockChainSpecific =
                    BlockChainSpecific.Solana(
                        recentBlockHash = "9C52suF7yXgY7MuH9axYfU6PKXXHucbmpDKW9F5rJ2Si",
                        priorityFee = BigInteger.valueOf(1_000_000),
                        fromAddressPubKey = null,
                        toAddressPubKey = null,
                        programId = false,
                        priorityLimit = BigInteger.valueOf(100_000),
                    ),
                memo = null,
                vaultPublicKeyECDSA = "pub",
                vaultLocalPartyID = "local",
                libType = null,
                wasmExecuteContractPayload = null,
                signSolana = signSolana,
            )

        val proto = requireNotNull(outbound(payload))
        // The outbound mapper must carry signSolana onto the wire.
        assertEquals(signSolana, proto.signSolana)

        // …and the inbound mapper must restore it byte-for-byte on the peer device.
        val restored = inbound(proto)
        assertEquals(signSolana, restored.signSolana)
        assertEquals(signSolana.rawTransactions, restored.signSolana?.rawTransactions)
    }

    private companion object {
        val SOL =
            Coin(
                chain = Chain.Solana,
                ticker = "SOL",
                logo = "solana",
                address = "CG4V2eoUXnwJSDsmr1fNdbR9r63XHLKD9gA2xpCRdRby",
                decimal = 9,
                hexPublicKey = "a74c34f40cde7afbe5492ac7fcc504520784d9021a85c896c2f0c9e60a5a868c",
                priceProviderID = "solana",
                contractAddress = "",
                isNativeToken = true,
            )
    }
}
