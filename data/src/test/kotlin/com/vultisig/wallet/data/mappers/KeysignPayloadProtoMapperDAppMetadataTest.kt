package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.proto.v1.CoinProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.DAppMetadata as DAppMetadataProto
import vultisig.keysign.v1.THORChainSpecific
import vultisig.keysign.v1.TransactionType

class KeysignPayloadProtoMapperDAppMetadataTest {

    private val mapper = KeysignPayloadProtoMapperImpl()

    @Test
    fun `dappMetadata is null when proto does not carry it`() {
        val result = mapper.invoke(basePayload())
        assertNull(result.dappMetadata)
    }

    @Test
    fun `dappMetadata is mapped through with all fields populated`() {
        val result =
            mapper.invoke(
                basePayload(
                    dappMetadata =
                        DAppMetadataProto(
                            name = "Uniswap",
                            url = "https://app.uniswap.org",
                            iconUrl = "https://app.uniswap.org/favicon.ico",
                        )
                )
            )

        val metadata = requireNotNull(result.dappMetadata) { "expected non-null dappMetadata" }
        assertEquals("Uniswap", metadata.name)
        assertEquals("https://app.uniswap.org", metadata.url)
        assertEquals("https://app.uniswap.org/favicon.ico", metadata.iconUrl)
    }

    @Test
    fun `dappMetadata is null when every field is empty after trim`() {
        val result =
            mapper.invoke(
                basePayload(dappMetadata = DAppMetadataProto(name = "", url = "", iconUrl = ""))
            )
        assertNull(result.dappMetadata)
    }

    @Test
    fun `dappMetadata is null when every field is whitespace-only`() {
        val result =
            mapper.invoke(
                basePayload(
                    dappMetadata =
                        DAppMetadataProto(name = "   ", url = "\t\n", iconUrl = "  \r\n ")
                )
            )
        assertNull(result.dappMetadata)
    }

    @Test
    fun `dappMetadata trims whitespace on every field`() {
        val result =
            mapper.invoke(
                basePayload(
                    dappMetadata =
                        DAppMetadataProto(
                            name = "  Uniswap  ",
                            url = "\thttps://app.uniswap.org\n",
                            iconUrl = " https://app.uniswap.org/favicon.ico ",
                        )
                )
            )

        val metadata = requireNotNull(result.dappMetadata)
        assertEquals("Uniswap", metadata.name)
        assertEquals("https://app.uniswap.org", metadata.url)
        assertEquals("https://app.uniswap.org/favicon.ico", metadata.iconUrl)
    }

    @Test
    fun `dappMetadata preserves partial fields when only url is set`() {
        val result =
            mapper.invoke(
                basePayload(
                    dappMetadata =
                        DAppMetadataProto(name = "", url = "https://app.uniswap.org", iconUrl = "")
                )
            )

        val metadata = requireNotNull(result.dappMetadata)
        assertEquals("", metadata.name)
        assertEquals("https://app.uniswap.org", metadata.url)
        assertEquals("", metadata.iconUrl)
    }

    private fun basePayload(dappMetadata: DAppMetadataProto? = null) =
        KeysignPayloadProto(
            coin = testCoin,
            toAddress = "thor1x6f63myfwktevd6mkspdeus9rea5a72w6ynax2",
            toAmount = "10000000",
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
            dappMetadata = dappMetadata,
        )

    private companion object {
        val testCoin =
            CoinProto(
                chain = "thorChain",
                ticker = "RUNE",
                address = "",
                contractAddress = "",
                decimals = 8,
                priceProviderId = "thorchain",
                isNativeToken = true,
                hexPublicKey = "",
                logo = "rune",
            )
    }
}
