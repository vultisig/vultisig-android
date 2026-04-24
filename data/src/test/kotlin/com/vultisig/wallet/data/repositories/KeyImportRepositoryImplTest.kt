package com.vultisig.wallet.data.repositories

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class KeyImportRepositoryImplTest {

    private lateinit var repository: KeyImportRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = KeyImportRepositoryImpl()
    }

    @Test
    fun `get returns null initially`() {
        assertNull(repository.get())
    }

    @Test
    fun `setMnemonic then get returns data with mnemonic`() {
        repository.setMnemonic("word1 word2 word3")

        val data = repository.get()
        assertNotNull(data)
        assertEquals("word1 word2 word3", data.mnemonic)
        assertTrue(data.chainSettings.isEmpty())
    }

    @Test
    fun `setMnemonic overwrites previous mnemonic and discards chain settings`() {
        repository.setMnemonic("old mnemonic")
        repository.setChainSettings(
            listOf(ChainImportSetting(chain = com.vultisig.wallet.data.models.Chain.Bitcoin))
        )

        repository.setMnemonic("new mnemonic")

        val data = repository.get()
        assertNotNull(data)
        assertEquals("new mnemonic", data.mnemonic)
        assertTrue(data.chainSettings.isEmpty())
    }

    @Test
    fun `setChainSettings after setMnemonic preserves mnemonic`() {
        repository.setMnemonic("test mnemonic")
        val settings =
            listOf(
                ChainImportSetting(chain = com.vultisig.wallet.data.models.Chain.Bitcoin),
                ChainImportSetting(chain = com.vultisig.wallet.data.models.Chain.Ethereum),
            )

        repository.setChainSettings(settings)

        val data = repository.get()
        assertNotNull(data)
        assertEquals("test mnemonic", data.mnemonic)
        assertEquals(2, data.chainSettings.size)
        assertEquals(com.vultisig.wallet.data.models.Chain.Bitcoin, data.chainSettings[0].chain)
        assertEquals(com.vultisig.wallet.data.models.Chain.Ethereum, data.chainSettings[1].chain)
    }

    @Test
    fun `setChainSettings without prior setMnemonic creates data with empty mnemonic`() {
        val settings =
            listOf(ChainImportSetting(chain = com.vultisig.wallet.data.models.Chain.Solana))

        repository.setChainSettings(settings)

        val data = repository.get()
        assertNotNull(data)
        assertEquals("", data.mnemonic)
        assertEquals(1, data.chainSettings.size)
        assertEquals(com.vultisig.wallet.data.models.Chain.Solana, data.chainSettings[0].chain)
    }

    @Test
    fun `setChainSettings preserves derivation path`() {
        val settings =
            listOf(
                ChainImportSetting(
                    chain = com.vultisig.wallet.data.models.Chain.Solana,
                    derivationPath = DerivationPath.Phantom,
                )
            )

        repository.setMnemonic("test")
        repository.setChainSettings(settings)

        val data = repository.get()
        assertNotNull(data)
        assertEquals(DerivationPath.Phantom, data.chainSettings[0].derivationPath)
    }

    @Test
    fun `clear removes all data`() {
        repository.setMnemonic("test mnemonic")
        repository.setChainSettings(
            listOf(ChainImportSetting(chain = com.vultisig.wallet.data.models.Chain.Bitcoin))
        )

        repository.clear()

        assertNull(repository.get())
    }

    @Test
    fun `clear then setChainSettings creates fresh data with empty mnemonic`() {
        // Simulate stale data from a previous initiating session
        repository.setMnemonic("stale mnemonic from previous session")
        repository.setChainSettings(
            listOf(ChainImportSetting(chain = com.vultisig.wallet.data.models.Chain.Bitcoin))
        )

        // Simulate non-initiating device joining: clear then set chains
        repository.clear()
        repository.setChainSettings(
            listOf(ChainImportSetting(chain = com.vultisig.wallet.data.models.Chain.Ethereum))
        )

        val data = repository.get()
        assertNotNull(data)
        assertEquals("", data.mnemonic)
        assertEquals(1, data.chainSettings.size)
        assertEquals(com.vultisig.wallet.data.models.Chain.Ethereum, data.chainSettings[0].chain)
    }

    @Test
    fun `setChainSettings on stale data without clear preserves old mnemonic`() {
        // This documents the bug that clear() prevents:
        // Without clear(), a stale mnemonic leaks into the joining device's session.
        repository.setMnemonic("stale mnemonic")

        repository.setChainSettings(
            listOf(ChainImportSetting(chain = com.vultisig.wallet.data.models.Chain.Ethereum))
        )

        val data = repository.get()
        assertNotNull(data)
        assertEquals("stale mnemonic", data.mnemonic)
    }

    @Test
    fun `default derivation path is Default`() {
        val setting = ChainImportSetting(chain = com.vultisig.wallet.data.models.Chain.Bitcoin)
        assertEquals(DerivationPath.Default, setting.derivationPath)
    }

    @Test
    fun `toString does not leak mnemonic`() {
        val data = KeyImportData(mnemonic = "secret words here")
        val str = data.toString()
        assertTrue("secret" !in str)
        assertTrue("***" in str)
    }

    @Test
    fun `setChainSettings preserves Default derivation for Bitcoin`() {
        val settings =
            listOf(
                ChainImportSetting(
                    chain = com.vultisig.wallet.data.models.Chain.Bitcoin,
                    derivationPath = DerivationPath.Default,
                )
            )

        repository.setMnemonic("test")
        repository.setChainSettings(settings)

        val data = repository.get()
        assertNotNull(data)
        assertEquals(DerivationPath.Default, data.chainSettings[0].derivationPath)
    }

    @Test
    fun `setChainSettings preserves Default derivation for Ethereum`() {
        val settings =
            listOf(
                ChainImportSetting(
                    chain = com.vultisig.wallet.data.models.Chain.Ethereum,
                    derivationPath = DerivationPath.Default,
                )
            )

        repository.setMnemonic("test")
        repository.setChainSettings(settings)

        val data = repository.get()
        assertNotNull(data)
        assertEquals(DerivationPath.Default, data.chainSettings[0].derivationPath)
    }

    @Test
    fun `setChainSettings preserves Default derivation for Cosmos GaiaChain`() {
        val settings =
            listOf(
                ChainImportSetting(
                    chain = com.vultisig.wallet.data.models.Chain.GaiaChain,
                    derivationPath = DerivationPath.Default,
                )
            )

        repository.setMnemonic("test")
        repository.setChainSettings(settings)

        val data = repository.get()
        assertNotNull(data)
        assertEquals(DerivationPath.Default, data.chainSettings[0].derivationPath)
    }

    @Test
    fun `setChainSettings preserves Default derivation for Tron`() {
        val settings =
            listOf(
                ChainImportSetting(
                    chain = com.vultisig.wallet.data.models.Chain.Tron,
                    derivationPath = DerivationPath.Default,
                )
            )

        repository.setMnemonic("test")
        repository.setChainSettings(settings)

        val data = repository.get()
        assertNotNull(data)
        assertEquals(DerivationPath.Default, data.chainSettings[0].derivationPath)
    }
}
