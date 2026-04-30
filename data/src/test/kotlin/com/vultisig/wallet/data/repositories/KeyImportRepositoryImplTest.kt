package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        repository.setChainSettings(listOf(ChainImportSetting(chain = Chain.Bitcoin)))

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
                ChainImportSetting(chain = Chain.Bitcoin),
                ChainImportSetting(chain = Chain.Ethereum),
            )

        repository.setChainSettings(settings)

        val data = repository.get()
        assertNotNull(data)
        assertEquals("test mnemonic", data.mnemonic)
        assertEquals(2, data.chainSettings.size)
        assertEquals(Chain.Bitcoin, data.chainSettings[0].chain)
        assertEquals(Chain.Ethereum, data.chainSettings[1].chain)
    }

    @Test
    fun `setChainSettings without prior setMnemonic creates data with empty mnemonic`() {
        val settings = listOf(ChainImportSetting(chain = Chain.Solana))

        repository.setChainSettings(settings)

        val data = repository.get()
        assertNotNull(data)
        assertEquals("", data.mnemonic)
        assertEquals(1, data.chainSettings.size)
        assertEquals(Chain.Solana, data.chainSettings[0].chain)
    }

    @Test
    fun `setChainSettings preserves derivation path`() {
        val settings =
            listOf(
                ChainImportSetting(chain = Chain.Solana, derivationPath = DerivationPath.Phantom)
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
        repository.setChainSettings(listOf(ChainImportSetting(chain = Chain.Bitcoin)))

        repository.clear()

        assertNull(repository.get())
    }

    @Test
    fun `clear then setChainSettings creates fresh data with empty mnemonic`() {
        // Simulate stale data from a previous initiating session
        repository.setMnemonic("stale mnemonic from previous session")
        repository.setChainSettings(listOf(ChainImportSetting(chain = Chain.Bitcoin)))

        // Simulate non-initiating device joining: clear then set chains
        repository.clear()
        repository.setChainSettings(listOf(ChainImportSetting(chain = Chain.Ethereum)))

        val data = repository.get()
        assertNotNull(data)
        assertEquals("", data.mnemonic)
        assertEquals(1, data.chainSettings.size)
        assertEquals(Chain.Ethereum, data.chainSettings[0].chain)
    }

    @Test
    fun `setChainSettings on stale data without clear preserves old mnemonic`() {
        // This documents the bug that clear() prevents:
        // Without clear(), a stale mnemonic leaks into the joining device's session.
        repository.setMnemonic("stale mnemonic")

        repository.setChainSettings(listOf(ChainImportSetting(chain = Chain.Ethereum)))

        val data = repository.get()
        assertNotNull(data)
        assertEquals("stale mnemonic", data.mnemonic)
    }

    @Test
    fun `default derivation path is Default`() {
        val setting = ChainImportSetting(chain = Chain.Bitcoin)
        assertEquals(DerivationPath.Default, setting.derivationPath)
    }

    @Test
    fun `toString does not leak mnemonic`() {
        val mnemonic = "secret words here"
        val data = KeyImportData(mnemonic = mnemonic)
        val str = data.toString()
        assertFalse(mnemonic in str)
        assertFalse("secret" in str)
        assertTrue("***" in str)
    }

    @Test
    fun `setChainSettings preserves Default derivation for Bitcoin`() {
        repository.setMnemonic("test")
        repository.setChainSettings(
            listOf(
                ChainImportSetting(chain = Chain.Bitcoin, derivationPath = DerivationPath.Default)
            )
        )

        val data = repository.get()
        assertNotNull(data)
        assertEquals(com.vultisig.wallet.data.models.Chain.Bitcoin, data.chainSettings[0].chain)
        assertEquals(DerivationPath.Default, data.chainSettings[0].derivationPath)
    }

    @Test
    fun `setChainSettings preserves Default derivation for Ethereum`() {
        repository.setMnemonic("test")
        repository.setChainSettings(
            listOf(
                ChainImportSetting(chain = Chain.Ethereum, derivationPath = DerivationPath.Default)
            )
        )

        val data = repository.get()
        assertNotNull(data)
        assertEquals(com.vultisig.wallet.data.models.Chain.Ethereum, data.chainSettings[0].chain)
        assertEquals(DerivationPath.Default, data.chainSettings[0].derivationPath)
    }

    @Test
    fun `setChainSettings preserves Phantom derivation for Solana round-trip`() {
        repository.setMnemonic("test")
        repository.setChainSettings(
            listOf(
                ChainImportSetting(
                    chain = com.vultisig.wallet.data.models.Chain.Solana,
                    derivationPath = DerivationPath.Phantom,
                )
            )
        )

        val data = repository.get()
        assertNotNull(data)
        assertEquals(com.vultisig.wallet.data.models.Chain.Solana, data.chainSettings[0].chain)
        assertEquals(DerivationPath.Phantom, data.chainSettings[0].derivationPath)
    }

    @Test
    fun `setChainSettings preserves Default derivation for Cosmos`() {
        repository.setMnemonic("test")
        repository.setChainSettings(
            listOf(
                ChainImportSetting(chain = Chain.GaiaChain, derivationPath = DerivationPath.Default)
            )
        )

        val data = repository.get()
        assertNotNull(data)
        assertEquals(com.vultisig.wallet.data.models.Chain.GaiaChain, data.chainSettings[0].chain)
        assertEquals(DerivationPath.Default, data.chainSettings[0].derivationPath)
    }

    @Test
    fun `setChainSettings preserves Default derivation for Tron`() {
        repository.setMnemonic("test")
        repository.setChainSettings(
            listOf(ChainImportSetting(chain = Chain.Tron, derivationPath = DerivationPath.Default))
        )

        val data = repository.get()
        assertNotNull(data)
        assertEquals(com.vultisig.wallet.data.models.Chain.Tron, data.chainSettings[0].chain)
        assertEquals(DerivationPath.Default, data.chainSettings[0].derivationPath)
    }
}
