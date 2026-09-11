package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.proto.v1.CoinProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.THORChainSpecific
import vultisig.keysign.v1.THORChainSwapPayload as ThorChainSwapPayloadProto
import vultisig.keysign.v1.TransactionType

/**
 * Pins `slippage_bps` on the THORChain / MayaChain swap payload in both proto directions. The field
 * is `optional`, so absence and zero are different values on the wire: a sender that predates it
 * must read back as null (row hidden), not as a zero-impact route.
 */
class KeysignPayloadProtoMapperNativeSwapSlippageTest {

    private val mapper = KeysignPayloadProtoMapperImpl()
    private val outboundMapper = PayloadToProtoMapperImpl()

    @Test
    fun `thorchain payload round-trips slippage_bps domain to proto to domain`() {
        val domain =
            mapper
                .invoke(basePayload())
                .copy(swapPayload = SwapPayload.ThorChain(thorPayload(slippageBps = 19)))

        val proto = requireNotNull(outboundMapper.invoke(domain))
        assertEquals(19u, requireNotNull(proto.thorchainSwapPayload).slippageBps)
        assertNull(proto.mayachainSwapPayload)

        val back = mapper.invoke(basePayload(thorchainSwapPayload = proto.thorchainSwapPayload))
        val payload = assertInstanceOf(SwapPayload.ThorChain::class.java, back.swapPayload)
        assertEquals(19, payload.data.slippageBps)
        assertEquals(BigDecimal("0.0019"), payload.data.priceImpact)
    }

    @Test
    fun `mayachain payload round-trips slippage_bps too`() {
        val domain =
            mapper
                .invoke(basePayload())
                .copy(swapPayload = SwapPayload.MayaChain(thorPayload(slippageBps = 250)))

        val proto = requireNotNull(outboundMapper.invoke(domain))
        assertEquals(250u, requireNotNull(proto.mayachainSwapPayload).slippageBps)
        assertNull(proto.thorchainSwapPayload)

        val back = mapper.invoke(basePayload(mayachainSwapPayload = proto.mayachainSwapPayload))
        val payload = assertInstanceOf(SwapPayload.MayaChain::class.java, back.swapPayload)
        assertEquals(250, payload.data.slippageBps)
    }

    @Test
    fun `proto without slippage_bps reads back as null, not zero`() {
        val back = mapper.invoke(basePayload(thorchainSwapPayload = thorProto(slippageBps = null)))
        val payload = assertInstanceOf(SwapPayload.ThorChain::class.java, back.swapPayload)

        assertNull(payload.data.slippageBps)
        assertNull(payload.data.priceImpact)
    }

    @Test
    fun `proto with slippage_bps of zero reads back as zero`() {
        val back = mapper.invoke(basePayload(thorchainSwapPayload = thorProto(slippageBps = 0u)))
        val payload = assertInstanceOf(SwapPayload.ThorChain::class.java, back.swapPayload)

        assertEquals(0, payload.data.slippageBps)
        assertEquals(0, BigDecimal.ZERO.compareTo(payload.data.priceImpact))
    }

    @Test
    fun `domain payload without slippage leaves the proto field unset`() {
        val domain =
            mapper
                .invoke(basePayload())
                .copy(swapPayload = SwapPayload.ThorChain(thorPayload(slippageBps = null)))

        val proto = requireNotNull(outboundMapper.invoke(domain))
        assertNull(requireNotNull(proto.thorchainSwapPayload).slippageBps)
    }

    @Test
    fun `a negative domain value is never written to the unsigned proto field`() {
        val domain =
            mapper
                .invoke(basePayload())
                .copy(swapPayload = SwapPayload.ThorChain(thorPayload(slippageBps = -1)))

        val proto = requireNotNull(outboundMapper.invoke(domain))
        assertNull(requireNotNull(proto.thorchainSwapPayload).slippageBps)
    }

    @Test
    fun `a proto value beyond Int range reads back as absent rather than wrapping negative`() {
        val back =
            mapper.invoke(
                basePayload(thorchainSwapPayload = thorProto(slippageBps = UInt.MAX_VALUE))
            )
        val payload = assertInstanceOf(SwapPayload.ThorChain::class.java, back.swapPayload)

        assertNull(payload.data.slippageBps)
    }

    private fun thorPayload(slippageBps: Int?) =
        THORChainSwapPayload(
            fromAddress = "thorsrc",
            fromCoin = runeDomainCoin(),
            toCoin = ethDomainCoin(),
            vaultAddress = "thorInbound",
            routerAddress = null,
            fromAmount = BigInteger.valueOf(100_000_000L),
            toAmountDecimal = BigDecimal("0.05"),
            toAmountLimit = "0",
            streamingInterval = "1",
            streamingQuantity = "0",
            expirationTime = 1_700_000_000uL,
            isAffiliate = true,
            slippageBps = slippageBps,
        )

    private fun thorProto(slippageBps: UInt?) =
        ThorChainSwapPayloadProto(
            fromAddress = "thorsrc",
            fromCoin = RUNE_COIN,
            toCoin = ETH_COIN,
            vaultAddress = "thorInbound",
            routerAddress = null,
            fromAmount = "100000000",
            toAmountDecimal = "0.05",
            toAmountLimit = "0",
            streamingInterval = "1",
            streamingQuantity = "0",
            expirationTime = 1_700_000_000uL,
            isAffiliate = true,
            slippageBps = slippageBps,
        )

    private fun runeDomainCoin() =
        Coin(
            chain = Chain.ThorChain,
            ticker = "RUNE",
            logo = "",
            address = "thorsrc",
            decimal = 8,
            hexPublicKey = "pubkey",
            priceProviderID = "thorchain",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun ethDomainCoin() =
        Coin(
            chain = Chain.Ethereum,
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
        thorchainSwapPayload: ThorChainSwapPayloadProto? = null,
        mayachainSwapPayload: ThorChainSwapPayloadProto? = null,
    ) =
        KeysignPayloadProto(
            coin = RUNE_COIN,
            toAddress = "thorInbound",
            toAmount = "100000000",
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
            thorchainSwapPayload = thorchainSwapPayload,
            mayachainSwapPayload = mayachainSwapPayload,
        )

    companion object {
        private val RUNE_COIN =
            CoinProto(
                chain = "THORChain",
                ticker = "RUNE",
                address = "thorsrc",
                contractAddress = "",
                decimals = 8,
                priceProviderId = "thorchain",
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
