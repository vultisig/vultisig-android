@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.keysign

import android.net.Uri
import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.usecases.CompressQrUseCaseImpl
import com.vultisig.wallet.data.usecases.DecompressQrUseCaseImpl
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers the QR-parsing pipeline in
 * [com.vultisig.wallet.ui.models.keysign.JoinKeysignViewModel.setScanResult] lines 278-284:
 * - `getJsonData()` → `decodeBase64Bytes()` → `decompressQr(...)` → `decodeFromByteArray()`.
 */
internal class JoinKeysignQrParsingTest {

    private val compressQr = CompressQrUseCaseImpl(CompressorStreamFactory())
    private val decompressQr = DecompressQrUseCaseImpl(CompressorStreamFactory())
    private val protoBuf = ProtoBuf

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
    fun `round-trips a KeysignMessage through the deep-link pipeline`() {
        val original =
            KeysignMessageProto(
                sessionId = "session-abc",
                serviceName = "vultisig-service",
                encryptionKeyHex = "deadbeef",
                useVultisigRelay = true,
            )
        val uri = buildJoinKeysignUri(original)

        val helper = DeepLinkHelper(uri)
        val qrCodeContent = requireNotNull(helper.getJsonData())
        val rawJson = decompressQr(qrCodeContent.decodeBase64Bytes())
        val decoded = protoBuf.decodeFromByteArray(KeysignMessageProto.serializer(), rawJson)

        assertEquals(original, decoded)
    }

    @Test
    fun `missing jsonData parameter yields null and triggers InvalidQr error path`() {
        val helper = DeepLinkHelper("vultisig://vultisig.com?type=SignTransaction")

        // Mirrors the `?: error(...)` guard in JoinKeysignViewModel line 280.
        val error =
            assertFailsWith<IllegalStateException> {
                helper.getJsonData() ?: error("Invalid QR code content")
            }
        assertEquals("Invalid QR code content", error.message)
    }

    @Test
    fun `malformed base64 payload throws before protobuf decode`() {
        val uri = "vultisig://vultisig.com?type=SignTransaction&jsonData=%%not-base64%%"
        val helper = DeepLinkHelper(uri)
        val qrCodeContent = requireNotNull(helper.getJsonData())

        // `decompressQr` operates on random bytes decoded from the bogus input and must
        // fail — the ViewModel catches this and surfaces JoinKeysignError.InvalidQr.
        assertFailsWith<Exception> { decompressQr(qrCodeContent.decodeBase64Bytes()) }
    }

    private fun buildJoinKeysignUri(message: KeysignMessageProto): String {
        val rawBytes = protoBuf.encodeToByteArray(message)
        val compressed = compressQr(rawBytes)
        val jsonData = compressed.encodeBase64()
        return "vultisig://vultisig.com?type=SignTransaction&jsonData=$jsonData"
    }
}
