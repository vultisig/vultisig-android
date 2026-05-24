package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.proto.v1.CoinProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SwapKitSwapPayload as SwapKitSwapPayloadProto
import vultisig.keysign.v1.THORChainSpecific
import vultisig.keysign.v1.TransactionType

/**
 * Pins the proto round-trip for [SwapPayload.SwapKit] via `swapkitSwapPayload` field 26
 * (commondata #86). Both directions must preserve every field — the cosigning peer reconstructs the
 * same payload bytes the initiator built. Bytes (`txPayload`) and decimal string round-trips are
 * the load-bearing checks; getting either wrong silently breaks cross-device cosigning.
 */
class KeysignPayloadProtoMapperSwapKitTest {

    private val mapper = KeysignPayloadProtoMapperImpl()
    private val outboundMapper = PayloadToProtoMapperImpl()

    @Test
    fun `inbound mapper decodes swapkitSwapPayload into SwapPayload SwapKit with every field intact`() {
        val txPayloadBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0xFF.toByte(), 0x00, 0x7F)
        val proto =
            basePayload(
                swapkitSwapPayload =
                    SwapKitSwapPayloadProto(
                        fromCoin = TON_COIN,
                        toCoin = ETH_COIN,
                        fromAmount = "1000000000",
                        toAmountDecimal = "0.0125",
                        txType = "TON",
                        txPayload = txPayloadBytes,
                        targetAddress = "EQAbc123",
                        inboundAddress = "thor1inbound",
                        memo = "memo-1",
                        subProvider = "CHAINFLIP",
                        swapId = "swap-uuid-1",
                    )
            )

        val result = mapper.invoke(proto)
        val swapKit = assertInstanceOf(SwapPayload.SwapKit::class.java, result.swapPayload)
        val data = swapKit.data

        assertEquals("TON", data.fromCoin.ticker)
        assertEquals("ETH", data.toCoin.ticker)
        assertEquals(BigInteger("1000000000"), data.fromAmount)
        assertEquals(0, BigDecimal("0.0125").compareTo(data.toAmountDecimal))
        assertEquals("TON", data.txType)
        assertArrayEquals(txPayloadBytes, data.txPayload)
        assertEquals("EQAbc123", data.targetAddress)
        assertEquals("thor1inbound", data.inboundAddress)
        assertEquals("memo-1", data.memo)
        assertEquals("CHAINFLIP", data.subProvider)
        assertEquals("swap-uuid-1", data.swapId)
    }

    @Test
    fun `inbound mapper tolerates absent optional fields on swapkitSwapPayload`() {
        // inboundAddress and memo are proto3 `optional` — peers MUST handle them being absent
        // (Cardano deposit-only / Phase 1 EVM observed in iOS spike) without throwing.
        val proto =
            basePayload(
                swapkitSwapPayload =
                    SwapKitSwapPayloadProto(
                        fromCoin = TON_COIN,
                        toCoin = ETH_COIN,
                        fromAmount = "1000",
                        toAmountDecimal = "0",
                        txType = "CARDANO",
                        txPayload = byteArrayOf(),
                        targetAddress = "addr1deposit",
                        inboundAddress = null,
                        memo = null,
                        subProvider = "",
                        swapId = "",
                    )
            )

        val result = mapper.invoke(proto)
        val data = assertInstanceOf(SwapPayload.SwapKit::class.java, result.swapPayload).data

        assertNull(data.inboundAddress)
        assertNull(data.memo)
        assertEquals(0, data.txPayload.size)
    }

    @Test
    fun `outbound mapper writes swapkitSwapPayload onto field 26 with every field intact`() {
        val txPayloadBytes = byteArrayOf(0x70, 0x73, 0x62, 0x74) // "psbt" magic
        val domain =
            mapper
                .invoke(basePayload())
                .copy(
                    swapPayload =
                        SwapPayload.SwapKit(
                            SwapKitSwapPayloadJson(
                                fromCoin = tonDomainCoin(),
                                toCoin = ethDomainCoin(),
                                fromAmount = BigInteger("5000000000"),
                                toAmountDecimal = BigDecimal("0.001"),
                                txType = "PSBT",
                                txPayload = txPayloadBytes,
                                targetAddress = "bc1qdeposit",
                                inboundAddress = null,
                                memo = "tag-7",
                                subProvider = "CHAINFLIP",
                                swapId = "psbt-swap-1",
                            )
                        )
                )

        val outbound = requireNotNull(outboundMapper.invoke(domain))
        val proto = requireNotNull(outbound.swapkitSwapPayload)

        assertEquals("TON", proto.fromCoin?.ticker)
        assertEquals("ETH", proto.toCoin?.ticker)
        assertEquals("5000000000", proto.fromAmount)
        assertEquals("0.001", proto.toAmountDecimal)
        assertEquals("PSBT", proto.txType)
        assertArrayEquals(txPayloadBytes, proto.txPayload)
        assertEquals("bc1qdeposit", proto.targetAddress)
        assertNull(proto.inboundAddress)
        assertEquals("tag-7", proto.memo)
        assertEquals("CHAINFLIP", proto.subProvider)
        assertEquals("psbt-swap-1", proto.swapId)

        // Sibling oneof fields must stay null — proto.swap_payload is a oneof and the generated
        // init {} require() enforces only one entry. A leaky outbound writer would crash here.
        assertNull(outbound.oneinchSwapPayload)
        assertNull(outbound.thorchainSwapPayload)
        assertNull(outbound.mayachainSwapPayload)
    }

    @Test
    fun `outbound mapper writes null swapkitSwapPayload when domain has no swap payload`() {
        val domain = mapper.invoke(basePayload())
        val outbound = requireNotNull(outboundMapper.invoke(domain))
        assertNull(outbound.swapkitSwapPayload)
    }

    @Test
    fun `proto round-trip preserves SwapPayload SwapKit byte-for-byte across inbound and outbound`() {
        val txPayloadBytes =
            byteArrayOf(0x00, 0x01, 0x02, 0x03, 0xFE.toByte(), 0xFF.toByte(), 0x42, 0x7F)
        val inboundProto =
            basePayload(
                swapkitSwapPayload =
                    SwapKitSwapPayloadProto(
                        fromCoin = TON_COIN,
                        toCoin = ETH_COIN,
                        fromAmount = "987654321",
                        toAmountDecimal = "12345.6789",
                        txType = "PSBT_BCH",
                        txPayload = txPayloadBytes,
                        targetAddress = "bitcoincash:qpdeposit",
                        inboundAddress = "thor1inbound",
                        memo = "round-trip-memo",
                        subProvider = "NEAR",
                        swapId = "rt-swap-99",
                    )
            )

        val domain = mapper.invoke(inboundProto)
        val outbound = requireNotNull(outboundMapper.invoke(domain))
        val roundTripped = requireNotNull(outbound.swapkitSwapPayload)

        // Every field on the proto must match the original — protobuf bytes round-trip is the
        // contract cross-device cosigning relies on.
        assertEquals(inboundProto.swapkitSwapPayload?.fromCoin, roundTripped.fromCoin)
        assertEquals(inboundProto.swapkitSwapPayload?.toCoin, roundTripped.toCoin)
        assertEquals(inboundProto.swapkitSwapPayload?.fromAmount, roundTripped.fromAmount)
        assertEquals(inboundProto.swapkitSwapPayload?.toAmountDecimal, roundTripped.toAmountDecimal)
        assertEquals(inboundProto.swapkitSwapPayload?.txType, roundTripped.txType)
        assertArrayEquals(inboundProto.swapkitSwapPayload?.txPayload, roundTripped.txPayload)
        assertEquals(inboundProto.swapkitSwapPayload?.targetAddress, roundTripped.targetAddress)
        assertEquals(inboundProto.swapkitSwapPayload?.inboundAddress, roundTripped.inboundAddress)
        assertEquals(inboundProto.swapkitSwapPayload?.memo, roundTripped.memo)
        assertEquals(inboundProto.swapkitSwapPayload?.subProvider, roundTripped.subProvider)
        assertEquals(inboundProto.swapkitSwapPayload?.swapId, roundTripped.swapId)
    }

    @Test
    fun `SwapPayload SwapKit exposes src and dst token values derived from the inner data`() {
        val data =
            SwapKitSwapPayloadJson(
                fromCoin = tonDomainCoin(),
                toCoin = ethDomainCoin(),
                fromAmount = BigInteger("1000000000"),
                toAmountDecimal = BigDecimal("0.0125"),
                txType = "TON",
                txPayload = byteArrayOf(),
                targetAddress = "EQAbc",
            )
        val payload = SwapPayload.SwapKit(data)

        assertEquals(data.fromCoin, payload.srcToken)
        assertEquals(data.toCoin, payload.dstToken)
        assertEquals(BigInteger("1000000000"), payload.srcTokenValue.value)
        // dstTokenValue scales toAmountDecimal by dst decimals (ETH = 18) → 0.0125 * 1e18 raw wei.
        assertEquals(BigInteger("12500000000000000"), payload.dstTokenValue.value)
    }

    @Test
    fun `SwapKitSwapPayloadJson equals and hashCode compare ByteArray by content`() {
        val a =
            SwapKitSwapPayloadJson(
                fromCoin = tonDomainCoin(),
                toCoin = ethDomainCoin(),
                fromAmount = BigInteger.TEN,
                toAmountDecimal = BigDecimal("0.001"),
                txType = "TON",
                txPayload = byteArrayOf(1, 2, 3),
                targetAddress = "addr",
            )
        val b =
            a.copy(
                // Distinct array instance with identical content.
                txPayload = byteArrayOf(1, 2, 3)
            )
        val c = a.copy(txPayload = byteArrayOf(1, 2, 4))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotNull(c) // not equal — different bytes
        assert(a != c)
    }

    // ---- helpers ----

    private fun tonDomainCoin() =
        com.vultisig.wallet.data.models.Coin(
            chain = com.vultisig.wallet.data.models.Chain.Ton,
            ticker = "TON",
            logo = "",
            address = "EQAuser",
            decimal = 9,
            hexPublicKey = "pubkey",
            priceProviderID = "the-open-network",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun ethDomainCoin() =
        com.vultisig.wallet.data.models.Coin(
            chain = com.vultisig.wallet.data.models.Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xuser",
            decimal = 18,
            hexPublicKey = "pubkey",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun basePayload(
        coin: CoinProto = TON_COIN,
        swapkitSwapPayload: SwapKitSwapPayloadProto? = null,
    ) =
        KeysignPayloadProto(
            coin = coin,
            toAddress = "EQAdest",
            toAmount = "1000",
            vaultPublicKeyEcdsa = "pubkey",
            vaultLocalPartyId = "party-1",
            thorchainSpecific =
                THORChainSpecific(
                    accountNumber = 1uL,
                    sequence = 0uL,
                    fee = 2_000_000uL,
                    isDeposit = false,
                    transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
            swapkitSwapPayload = swapkitSwapPayload,
        )

    companion object {
        // CoinProto.chain must round-trip via Chain.fromRaw / Chain.raw — use the canonical
        // mixed-case "Ton" rather than the SwapKit wire-format "TON" so equals works after the
        // domain-Coin → proto-Coin round trip.
        private val TON_COIN =
            CoinProto(
                chain = "Ton",
                ticker = "TON",
                address = "EQAuser",
                contractAddress = "",
                decimals = 9,
                priceProviderId = "the-open-network",
                isNativeToken = true,
                hexPublicKey = "pubkey",
                logo = "",
            )
        private val ETH_COIN =
            CoinProto(
                chain = "Ethereum",
                ticker = "ETH",
                address = "0xuser",
                contractAddress = "",
                decimals = 18,
                priceProviderId = "ethereum",
                isNativeToken = true,
                hexPublicKey = "pubkey",
                logo = "",
            )
    }
}
