package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class JoinKeysignRequestBuilderTest {

    private val vault =
        Vault(id = "v", name = "v", pubKeyECDSA = "04abcdef", localPartyID = "android-123")

    private fun build(
        chain: Chain?,
        type: TssKeyType = TssKeyType.ECDSA,
        derivePath: String = STUB_DERIVE_PATH,
    ): JoinKeysignRequestJson =
        JoinKeysignRequestBuilder.build(
            vault = vault,
            chain = chain,
            derivePath = derivePath,
            sessionId = "sess",
            encryptionKeyHex = "key",
            messages = listOf("msg"),
            password = "pwd",
            tssKeysignType = type,
        )

    @Test
    fun `BscChain wire string is BSC, not BscChain`() {
        assertEquals("BSC", build(Chain.BscChain).chain)
    }

    @Test
    fun `BitcoinCash wire string is Bitcoin-Cash, not BitcoinCash`() {
        assertEquals("Bitcoin-Cash", build(Chain.BitcoinCash).chain)
    }

    @Test
    fun `GaiaChain wire string is Cosmos, not GaiaChain`() {
        assertEquals("Cosmos", build(Chain.GaiaChain).chain)
    }

    @Test
    fun `Ethereum wire string is Ethereum`() {
        assertEquals("Ethereum", build(Chain.Ethereum).chain)
    }

    @Test
    fun `Bitcoin wire string is Bitcoin`() {
        assertEquals("Bitcoin", build(Chain.Bitcoin).chain)
    }

    @Test
    fun `Solana wire string is Solana for EdDSA chain`() {
        assertEquals("Solana", build(Chain.Solana, TssKeyType.EDDSA).chain)
    }

    @Test
    fun `ThorChain wire string is THORChain capitalised`() {
        assertEquals("THORChain", build(Chain.ThorChain).chain)
    }

    @Test
    fun `ECDSA key type sets isEcdsa true and mldsa false`() {
        val req = build(Chain.Ethereum, TssKeyType.ECDSA)
        assertTrue(req.isEcdsa)
        assertFalse(req.mldsa)
    }

    @Test
    fun `EDDSA key type sets both isEcdsa and mldsa false`() {
        val req = build(Chain.Solana, TssKeyType.EDDSA)
        assertFalse(req.isEcdsa)
        assertFalse(req.mldsa)
    }

    @Test
    fun `MLDSA key type sets mldsa true and isEcdsa false`() {
        val req = build(Chain.Qbtc, TssKeyType.MLDSA)
        assertFalse(req.isEcdsa)
        assertTrue(req.mldsa)
    }

    @Test
    fun `request carries vault root pubKeyEcdsa as identifier for server lookup`() {
        assertEquals("04abcdef", build(Chain.Ethereum).publicKeyEcdsa)
    }

    @Test
    fun `null chain yields empty chain string`() {
        assertEquals("", build(chain = null).chain)
    }

    @Test
    fun `derivePath is passed through verbatim`() {
        assertEquals(STUB_DERIVE_PATH, build(Chain.Ethereum).derivePath)
    }

    @Test
    fun `KeyImport vault sign for BSC sends chain BSC matching what was registered at import`() {
        val keyImportVault = vault.copy(libType = SigningLibType.KeyImport)
        val req =
            JoinKeysignRequestBuilder.build(
                vault = keyImportVault,
                chain = Chain.BscChain,
                derivePath = STUB_DERIVE_PATH,
                sessionId = "s",
                encryptionKeyHex = "k",
                messages = listOf("m"),
                password = "p",
                tssKeysignType = TssKeyType.ECDSA,
            )
        assertEquals("BSC", req.chain)
    }

    private companion object {
        const val STUB_DERIVE_PATH = "m/44'/60'/0'/0/0"
    }
}
