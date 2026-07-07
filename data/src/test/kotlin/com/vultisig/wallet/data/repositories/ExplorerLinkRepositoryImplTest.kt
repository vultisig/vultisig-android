package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.payload.SwapPayload
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ExplorerLinkRepositoryImplTest {

    private val repository = ExplorerLinkRepositoryImpl()

    @Test
    fun `QBTC transaction link is empty when explorer does not exist`() {
        val link = repository.getTransactionLink(Chain.Qbtc, "ABCDEF123456")
        assertTrue(link.isBlank(), "QBTC tx link should be blank, was: $link")
    }

    @Test
    fun `QBTC address link is empty when explorer does not exist`() {
        val link = repository.getAddressLink(Chain.Qbtc, "qbtc1someaddress")
        assertTrue(link.isBlank(), "QBTC address link should be blank, was: $link")
    }

    @Test
    fun `Bitcoin transaction link contains tx hash`() {
        val hash = "abc123"
        val link = repository.getTransactionLink(Chain.Bitcoin, hash)
        assertTrue(link.contains(hash), "Bitcoin tx link should contain hash")
        assertTrue(link.startsWith("https://"), "Bitcoin tx link should be https URL")
    }

    @Test
    fun `Ethereum address link contains address`() {
        val address = "0xDEADBEEF"
        val link = repository.getAddressLink(Chain.Ethereum, address)
        assertTrue(link.contains(address), "Ethereum address link should contain address")
        assertTrue(link.startsWith("https://"), "Ethereum address link should be https URL")
    }

    @Test
    fun `GaiaChain transaction link contains tx hash`() {
        val hash = "COSMOSTX123"
        val link = repository.getTransactionLink(Chain.GaiaChain, hash)
        assertTrue(link.contains(hash))
        assertTrue(link.contains("mintscan.io"))
    }

    @Test
    fun `ThorChain transaction link strips 0x prefix`() {
        val link = repository.getTransactionLink(Chain.ThorChain, "0xABCDEF")
        assertTrue(link.contains("ABCDEF"))
        assertTrue(!link.contains("0x"))
    }

    @Test
    fun `all chains produce transaction links without throwing`() {
        for (chain in Chain.entries) {
            val link = repository.getTransactionLink(chain, "test_hash")
            if (chain != Chain.Qbtc) {
                assertTrue(
                    link.isNotEmpty(),
                    "Transaction link for ${chain.name} should not be empty",
                )
            }
        }
    }

    @Test
    fun `SwapKit native route Track link points to SwapKit tracker with slug chainId`() {
        val hash = "abc123bitcoinhash"
        val link = repository.getSwapProgressLink(hash, swapKitNativePayload(Chain.Bitcoin))
        assertEquals("https://track.swapkit.dev/?hash=$hash&chainId=bitcoin", link)
    }

    @Test
    fun `SwapKit-routed EVM Track link uses decimal chainId and keeps 0x hash`() {
        val hash = "0x0ae1d2cacbc01f3f1326e8affaddc8dc9718e8cd3e9ca2770f9cbeae5635f90b"
        val link =
            repository.getSwapProgressLink(
                hash,
                evmPayload(Chain.Ethereum, SwapProvider.SWAPKIT.getSwapProviderId()),
            )
        assertEquals("https://track.swapkit.dev/?hash=$hash&chainId=1", link)
    }

    @Test
    fun `non-SwapKit EVM route Track link is unchanged`() {
        val hash = "0xdeadbeef"
        val link =
            repository.getSwapProgressLink(
                hash,
                evmPayload(Chain.Ethereum, SwapProvider.LIFI.getSwapProviderId()),
            )
        assertEquals("https://scan.li.fi/tx/$hash", link)
    }

    @Test
    fun `SwapKit-routed EVM on untrackable source chain falls back to LiFi tracker`() {
        // Hyperliquid is EVM-shaped but outside SwapKit's /track catalogue, so the SwapKit branch
        // is skipped and the EVM source-chain logic applies. The tracker no longer depends on
        // swapFee, so a feeless route still gets a Track link.
        val link =
            repository.getSwapProgressLink(
                "0xabc",
                evmPayload(
                    Chain.Hyperliquid,
                    SwapProvider.SWAPKIT.getSwapProviderId(),
                    swapFee = "",
                ),
            )
        assertEquals("https://scan.li.fi/tx/0xabc", link)
    }

    @Test
    fun `same-chain Solana swap with empty swapFee still yields Helius Track link`() {
        // Regression for #5202: feeless routes (swapFee defaults to "") must not drop the tracker.
        val hash = "solanaTxHash123"
        val link =
            repository.getSwapProgressLink(
                hash,
                evmPayload(
                    Chain.Solana,
                    SwapProvider.ONEINCH.getSwapProviderId(),
                    toChain = Chain.Solana,
                    swapFee = "",
                ),
            )
        assertEquals("https://orb.helius.dev/tx/$hash", link)
    }

    @Test
    fun `non-SwapKit EVM route with empty swapFee still yields LiFi Track link`() {
        val hash = "0xdeadbeef"
        val link =
            repository.getSwapProgressLink(
                hash,
                evmPayload(Chain.Ethereum, SwapProvider.LIFI.getSwapProviderId(), swapFee = ""),
            )
        assertEquals("https://scan.li.fi/tx/$hash", link)
    }

    @Test
    fun `all chains produce address links without throwing`() {
        for (chain in Chain.entries) {
            val link = repository.getAddressLink(chain, "test_address")
            if (chain != Chain.Qbtc) {
                assertTrue(link.isNotEmpty(), "Address link for ${chain.name} should not be empty")
            }
        }
    }

    private fun coin(chain: Chain) = Coin.EMPTY.copy(chain = chain)

    private fun swapKitNativePayload(srcChain: Chain) =
        SwapPayload.SwapKit(
            SwapKitSwapPayloadJson(
                fromCoin = coin(srcChain),
                toCoin = coin(Chain.Ethereum),
                fromAmount = BigInteger.ONE,
                toAmountDecimal = BigDecimal.ONE,
                txType = SwapKitSwapPayloadJson.TX_TYPE_PSBT,
                txPayload = ByteArray(0),
                targetAddress = "addr",
            )
        )

    private fun evmPayload(
        srcChain: Chain,
        provider: String,
        toChain: Chain = Chain.Solana,
        swapFee: String = "0",
    ) =
        SwapPayload.EVM(
            EVMSwapPayloadJson(
                fromCoin = coin(srcChain),
                toCoin = coin(toChain),
                fromAmount = BigInteger.ONE,
                toAmountDecimal = BigDecimal.ONE,
                quote =
                    EVMSwapQuoteJson(
                        dstAmount = "1",
                        tx =
                            OneInchSwapTxJson(
                                from = "from",
                                to = "to",
                                gas = 0,
                                data = "0x",
                                value = "0",
                                gasPrice = "0",
                                swapFee = swapFee,
                            ),
                    ),
                provider = provider,
            )
        )
}
