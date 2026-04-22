@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import android.net.Uri
import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.common.JOIN_KEYSIGN_FLOW
import com.vultisig.wallet.data.models.TonKeysignSession
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.repositories.TonConnectRepository
import io.ktor.util.decodeBase64Bytes
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * End-to-end test of the SignTransaction deep-link pipeline for a real sample URL:
 * - Parses the URL with [DeepLinkHelper]
 * - Base64-decodes and XZ-decompresses the `jsonData` parameter (same path as
 *   [com.vultisig.wallet.ui.models.keysign.JoinKeysignViewModel] uses)
 * - Decodes the result as a [KeysignMessageProto]
 * - Feeds it through [PersistTonConnectSessionUseCaseImpl] and verifies the side effect
 */
internal class TonConnectDeepLinkParsingTest {

    private val sampleUri =
        "https://vultisig.com" +
            "?type=SignTransaction" +
            "&vault=029df1c02adcb4fc0855a9324989ebdfde02d63283a717d3bbab553d21eae46ebc" +
            "&jsonData=/Td6WFoAAAFpIt42AgAhAQAAAAA3J5fW4AEfAPBdAAUJ0GQMHSz4xoSIx72tI4zabaxeYX5Z" +
            "SKl+fCVwPvPZ1iMz9cQxB76IiQS+YpIhRJq9xWK4IY2oTifSYE/gkYQMDguKoJtGOXVqgAha/UN2TIttz5" +
            "/Z6O0qh7GOsCqwiWskDieq+VYwtf018lqK1Lvc1nc2pErCs7yrP7Cn0TfgR1+zFDjdI87MofZQYUG9J/5O" +
            "m+GmJzGK5bn3XbWutEAQJK1/LlvFT5QnEk3LCfZzQ7VEsxQAzVd5p4e9IxZQbQ/hTpNTJfnv8yUW3eaq4f" +
            "tm8pkO2SWI0YBwYfC+1AFRI28E9fNvZAcuGFaW5yse9ADb1tPsAAGIAqACAABj3JkFPjANiwIAAAAAAVla"

    private val expectedVaultPubKey =
        "029df1c02adcb4fc0855a9324989ebdfde02d63283a717d3bbab553d21eae46ebc"

    private val decompressQr = DecompressQrUseCaseImpl(CompressorStreamFactory())
    private val protoBuf = ProtoBuf
    private val repository: TonConnectRepository = mockk()
    private val useCase = PersistTonConnectSessionUseCaseImpl(repository, protoBuf)

    @BeforeEach
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.decode(any()) } answers { firstArg() }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `deep-link parses into expected URL fields`() {
        val helper = DeepLinkHelper(sampleUri)
        assertEquals(JOIN_KEYSIGN_FLOW, helper.getFlowType())
        assertEquals(expectedVaultPubKey, helper.getParameter("vault"))
        val jsonData = helper.getJsonData()
        assertNotNull(jsonData)
        assertTrue(jsonData.startsWith("/Td6WFo"), "jsonData must be XZ-magic-prefixed base64")
    }

    @Test
    fun `jsonData base64-decodes and XZ-decompresses without error`() {
        val helper = DeepLinkHelper(sampleUri)
        val compressed = helper.getJsonData()!!.decodeBase64Bytes()
        val decompressed = decompressQr(compressed)
        assertTrue(decompressed.isNotEmpty(), "decompressed payload must not be empty")
    }

    @Test
    fun `decompressed bytes decode as KeysignMessageProto`() {
        val helper = DeepLinkHelper(sampleUri)
        val decompressed = decompressQr(helper.getJsonData()!!.decodeBase64Bytes())
        val proto = protoBuf.decodeFromByteArray(KeysignMessageProto.serializer(), decompressed)

        // Decoding succeeds; the sample happens to carry a session-routing message with
        // minimal fields (sessionId + serviceName + encryptionKeyHex + useVultisigRelay).
        assertTrue(proto.sessionId.isNotEmpty(), "proto.sessionId should be populated")
        assertTrue(proto.serviceName.isNotEmpty(), "proto.serviceName should be populated")
    }

    @Test
    fun `use case is no-op for sample payload that contains no signTon`() = runTest {
        // The sample URL carries a session-routing message (no signTon field), so
        // PersistTonConnectSessionUseCase must not call saveSession.
        val helper = DeepLinkHelper(sampleUri)
        val decompressed = decompressQr(helper.getJsonData()!!.decodeBase64Bytes())
        val proto = protoBuf.decodeFromByteArray(KeysignMessageProto.serializer(), decompressed)

        val captured = slot<TonKeysignSession>()
        coEvery { repository.saveSession(capture(captured)) } just Runs

        useCase(proto, "vault-42")

        coVerify(exactly = 0) { repository.saveSession(any()) }
    }
}
