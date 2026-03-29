package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
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
    fun `all chains produce address links without throwing`() {
        for (chain in Chain.entries) {
            val link = repository.getAddressLink(chain, "test_address")
            if (chain != Chain.Qbtc) {
                assertTrue(link.isNotEmpty(), "Address link for ${chain.name} should not be empty")
            }
        }
    }
}
