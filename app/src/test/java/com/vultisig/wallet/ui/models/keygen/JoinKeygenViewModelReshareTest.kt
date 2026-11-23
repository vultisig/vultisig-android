@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.mappers.ReshareMessageFromProtoMapperImpl
import com.vultisig.wallet.data.mappers.ReshareMessageToProtoMapperImpl
import com.vultisig.wallet.data.models.ReshareMessage
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DecompressQrUseCase
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.io.encoding.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class JoinKeygenViewModelReshareTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val context = mockk<Context>()
    private val vaultRepository = mockk<VaultRepository>()
    private val decompressQr = mockk<DecompressQrUseCase>()
    private val sessionApi = mockk<SessionApi>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        mockkStatic(Uri::class)
        every { Uri.decode(any()) } answers { firstArg() }
        every { context.getSystemService(Context.NSD_SERVICE) } returns mockk<NsdManager>()
        val qr =
            Base64.UrlSafe.encode(
                "https://vultisig.com?tssType=Reshare&jsonData=AA==".encodeToByteArray()
            )
        every { any<SavedStateHandle>().toRoute<Route.Keygen.Join>() } returns
            Route.Keygen.Join(qr = qr)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `a new participant rejects an imported vault reshare before joining`() =
        runTest(dispatcher) {
            assertReshareBlocked(SigningLibType.KeyImport)
        }

    @Test
    fun `a participant with an imported vault also checks the stored type`() =
        runTest(dispatcher) {
            val vault =
                Vault(
                    id = "imported-vault",
                    name = "Imported vault",
                    pubKeyECDSA = "vault-key",
                    libType = SigningLibType.KeyImport,
                )
            assertReshareBlocked(SigningLibType.DKLS, vault)
        }

    private fun assertReshareBlocked(libType: SigningLibType, existingVault: Vault? = null) {
        coEvery { vaultRepository.getAll() } returns listOfNotNull(existingVault)
        val message =
            ReshareMessage(
                sessionID = "test-session",
                hexChainCode = "",
                serviceName = "",
                pubKeyECDSA = "vault-key",
                oldParties = emptyList(),
                encryptionKeyHex = "",
                useVultisigRelay = true,
                oldResharePrefix = "",
                vaultName = "Imported vault",
                libType = libType,
            )
        every { decompressQr(any()) } returns
            ProtoBuf.encodeToByteArray(ReshareMessageToProtoMapperImpl()(message))

        val vm =
            JoinKeygenViewModel(
                savedStateHandle = mockk(),
                context = context,
                navigator = mockk(relaxed = true),
                vaultRepository = vaultRepository,
                protoBuf = ProtoBuf,
                mapKeygenMessageFromProto = mockk(),
                mapReshareMessageFromProto = ReshareMessageFromProtoMapperImpl(),
                decompressQr = decompressQr,
                sessionApi = sessionApi,
            )

        assertEquals(JoinKeygenError.KeyImportReshareUnsupported, vm.state.value.error)
        coVerify(exactly = 0) { sessionApi.startSession(any(), any(), any()) }
    }
}
