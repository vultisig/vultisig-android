package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.CardanoTokenAsset
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `UtxoInfo.cardano_tokens` is what tells a co-signer which native assets the inputs carry. Only
 * the initiator queries Koios, so a mapper that drops the field leaves the peer building a
 * different `Cardano.SigningInput` for the same send — a different sighash, and a ceremony that
 * cannot complete.
 */
class KeysignPayloadProtoMapperCardanoTokensTest {

    private val outbound = PayloadToProtoMapperImpl()
    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `native assets survive the KeysignPayload to proto round-trip`() {
        val restored = inbound(requireNotNull(outbound(payload(UTXO_WITH_TOKENS))))

        assertEquals(listOf(UTXO_WITH_TOKENS), restored.utxos)
    }

    @Test
    fun `a quantity outside Long range survives intact`() {
        // The wire carries a decimal string precisely so a supply past 2^63 is representable.
        val huge = BigInteger("340282366920938463463374607431768211455")
        val utxo = UTXO_WITH_TOKENS.copy(cardanoTokens = listOf(SNEK.copy(amount = huge)))

        val restored = inbound(requireNotNull(outbound(payload(utxo))))

        assertEquals(huge, restored.utxos.single().cardanoTokens.single().amount)
    }

    @Test
    fun `the wire order of the assets is preserved`() {
        // The initiator sorts before sending; a mapper that reordered would change the bytes the
        // peer hashes even though both hold the same assets.
        val utxo = UTXO_WITH_TOKENS.copy(cardanoTokens = listOf(USDM, SNEK))

        val proto = requireNotNull(outbound(payload(utxo)))

        assertEquals(
            listOf(USDM.policyId, SNEK.policyId),
            proto.utxoInfo.single()?.cardanoTokens?.map { it?.policyId },
        )
        assertEquals(listOf(USDM, SNEK), inbound(proto).utxos.single().cardanoTokens)
    }

    @Test
    fun `an ADA-only UTxO carries no assets in either direction`() {
        val adaOnly = UTXO_WITH_TOKENS.copy(cardanoTokens = emptyList())

        val proto = requireNotNull(outbound(payload(adaOnly)))

        assertTrue(proto.utxoInfo.single()?.cardanoTokens.isNullOrEmpty())
        assertEquals(emptyList<CardanoTokenAsset>(), inbound(proto).utxos.single().cardanoTokens)
    }

    @Test
    fun `a malformed quantity is refused rather than silently dropped`() {
        val proto = requireNotNull(outbound(payload(UTXO_WITH_TOKENS)))
        val corrupted =
            proto.copy(
                utxoInfo =
                    proto.utxoInfo.map { utxo ->
                        utxo?.copy(
                            cardanoTokens = utxo.cardanoTokens.map { it?.copy(amount = "??") }
                        )
                    }
            )

        // Signing a bundle that quietly lost a row would move assets the payload does not
        // describe, so the read fails instead.
        assertThrows(IllegalStateException::class.java) { inbound(corrupted) }
    }

    private fun payload(utxo: UtxoInfo) =
        KeysignPayload(
            coin = SNEK_COIN,
            toAddress = "addr1v9g9wnzsutrxt7vcg4efdfwhagwh3x2f6hjwykk7acdpsfgyt4h2j",
            toAmount = BigInteger("1000000"),
            blockChainSpecific =
                BlockChainSpecific.Cardano(
                    byteFee = 200_000L,
                    sendMaxAmount = false,
                    ttl = 1_000UL,
                ),
            memo = null,
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
            utxos = listOf(utxo),
        )

    private companion object {
        val SNEK =
            CardanoTokenAsset(
                policyId = "279c909f348e533da5808898f87f9a14bb2c3dfbbacccd631d927a3f",
                assetNameHex = "534e454b",
                amount = BigInteger("2500000"),
            )

        val USDM =
            CardanoTokenAsset(
                policyId = "c48cbb3d5e57ed56e276bc45f99ab39abe94e6cd7ac39fb402da47ad",
                assetNameHex = "0014df105553444d",
                amount = BigInteger("665000"),
            )

        val UTXO_WITH_TOKENS =
            UtxoInfo(
                hash = "f074134aabbfb13b8aec7cf5465b1e5a862d1cadc175d431c1d9339150db8a1d",
                amount = 10_000_000L,
                index = 0u,
                cardanoTokens = listOf(SNEK, USDM),
            )

        val SNEK_COIN =
            Coin(
                chain = Chain.Cardano,
                ticker = "SNEK",
                logo = "snek",
                address = "addr1v9g9wnzsutrxt7vcg4efdfwhagwh3x2f6hjwykk7acdpsfgyt4h2j",
                decimal = 0,
                hexPublicKey = "pub",
                priceProviderID = "snek",
                contractAddress =
                    "279c909f348e533da5808898f87f9a14bb2c3dfbbacccd631d927a3f.534e454b",
                isNativeToken = false,
            )
    }
}
