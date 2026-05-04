@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.peer

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import com.vultisig.wallet.data.api.models.signer.BatchReshareRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinReshareRequestJson
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.FeatureFlagRepository
import com.vultisig.wallet.data.repositories.KeyImportRepository
import com.vultisig.wallet.data.repositories.QrHelperModalRepository
import com.vultisig.wallet.data.repositories.SecretSettingsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.CreateQrCodeSharingBitmapUseCase
import com.vultisig.wallet.data.usecases.ExtractMasterKeysUseCase
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.GenerateServerPartyId
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.NetworkUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins down the FastVault dispatch decision for reshare:
 * - `tss-batch` flag on + DKLS vault → `joinBatchReshare` (and only that)
 * - `tss-batch` flag off → legacy `joinReshare`
 * - `tss-batch` flag on + GG20 vault → legacy `joinReshare` (the batch endpoint is DKLS-only)
 *
 * If this drifts, an initiator can hit `/batch/reshare` while the joiners poll the legacy relay
 * namespace and the ceremony deadlocks at 60s timeout.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class KeygenPeerDiscoveryViewModelReshareTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var context: Context
    private lateinit var navigator: Navigator<Destination>
    private lateinit var generateQrBitmap: GenerateQrBitmap
    private lateinit var compressQr: CompressQrUseCase
    private lateinit var createQrCodeSharingBitmap: CreateQrCodeSharingBitmapUseCase
    private lateinit var generateServiceName: GenerateServiceName
    private lateinit var discoverParticipants: DiscoverParticipantsUseCase
    private lateinit var generateServerPartyId: GenerateServerPartyId
    private lateinit var secretSettingsRepository: SecretSettingsRepository
    private lateinit var vultiSignerRepository: VultiSignerRepository
    private lateinit var featureFlagRepository: FeatureFlagRepository
    private lateinit var qrHelperModalRepository: QrHelperModalRepository
    private lateinit var vaultRepository: VaultRepository
    private lateinit var keyImportRepository: KeyImportRepository
    private lateinit var extractMasterKeys: ExtractMasterKeysUseCase
    private lateinit var protoBuf: ProtoBuf
    private lateinit var sessionApi: SessionApi
    private lateinit var networkUtils: NetworkUtils

    private val vaultId = "vault-uuid"

    private val existingVault: Vault =
        Vault(
            id = vaultId,
            name = "Existing Vault",
            pubKeyECDSA = "old-pk",
            pubKeyEDDSA = "old-eddsa",
            hexChainCode = "abcd",
            localPartyID = "this-device",
            signers = listOf("alice", "bob", "this-device"),
            resharePrefix = "old-prefix",
            libType = SigningLibType.DKLS,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        savedStateHandle = mockk(relaxed = true)
        context = mockk(relaxed = true)
        navigator = mockk(relaxed = true)
        generateQrBitmap = mockk(relaxed = true)
        compressQr = mockk(relaxed = true)
        createQrCodeSharingBitmap = mockk(relaxed = true)
        generateServiceName = mockk(relaxed = true)
        discoverParticipants = mockk(relaxed = true)
        generateServerPartyId = mockk(relaxed = true)
        secretSettingsRepository = mockk(relaxed = true)
        vultiSignerRepository = mockk(relaxed = true)
        featureFlagRepository = mockk(relaxed = true)
        qrHelperModalRepository = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        keyImportRepository = mockk(relaxed = true)
        extractMasterKeys = mockk(relaxed = true)
        protoBuf = ProtoBuf
        sessionApi = mockk(relaxed = true)
        networkUtils = mockk(relaxed = true)

        mockkStatic("androidx.navigation.SavedStateHandleKt")
        mockkObject(Utils)

        every { Utils.deviceName(any()) } returns "this-device"
        every { generateServiceName() } returns "vultisig-service"
        every { generateServerPartyId() } returns "Server-XYZ"
        every { networkUtils.isNetworkAvailable() } returns true
        every { secretSettingsRepository.isDklsEnabled } returns flowOf(true)
        every { discoverParticipants.invoke(any(), any(), any()) } returns flowOf(emptyList())

        // Compress is a typed (ByteArray) -> ByteArray functional interface; relaxed mocks
        // would return Object and crash on the cast inside the production code.
        every { compressQr.invoke(any()) } returns ByteArray(0)
        // Same story for the QR bitmap generator.
        every { generateQrBitmap.invoke(any(), any(), any(), any()) } returns
            mockk<Bitmap>(relaxed = true)

        // Stub session bootstrap so startSessionWithRetry takes the happy path quickly.
        coEvery { sessionApi.getParticipants(any(), any()) } returns listOf("this-device")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        unmockkObject(Utils)
    }

    private fun stubReshareRoute(
        email: String? = "user@example.com",
        password: String? = "fast-vault-password",
    ) {
        every { any<SavedStateHandle>().toRoute<Route.Keygen.PeerDiscovery>() } returns
            Route.Keygen.PeerDiscovery(
                action = TssAction.ReShare,
                vaultName = existingVault.name,
                email = email,
                password = password,
                vaultId = vaultId,
            )
    }

    private fun createViewModel(): KeygenPeerDiscoveryViewModel =
        KeygenPeerDiscoveryViewModel(
            savedStateHandle = savedStateHandle,
            context = context,
            navigator = navigator,
            generateQrBitmap = generateQrBitmap,
            compressQr = compressQr,
            createQrCodeSharingBitmap = createQrCodeSharingBitmap,
            generateServiceName = generateServiceName,
            discoverParticipants = discoverParticipants,
            generateServerPartyId = generateServerPartyId,
            secretSettingsRepository = secretSettingsRepository,
            vultiSignerRepository = vultiSignerRepository,
            featureFlagRepository = featureFlagRepository,
            qrHelperModalRepository = qrHelperModalRepository,
            vaultRepository = vaultRepository,
            keyImportRepository = keyImportRepository,
            extractMasterKeys = extractMasterKeys,
            protoBuf = protoBuf,
            sessionApi = sessionApi,
            networkUtils = networkUtils,
        )

    @Test
    fun `reshare with tss-batch flag on dispatches batch endpoint with mapped payload`() =
        runTest(testDispatcher) {
            coEvery { vaultRepository.get(vaultId) } returns existingVault
            coEvery { featureFlagRepository.getFeatureFlags() } returns
                FeatureFlagJson(isTssBatchEnabled = true)

            stubReshareRoute()
            createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) {
                vultiSignerRepository.joinBatchReshare(
                    match<BatchReshareRequestJson> { request ->
                        request.publicKeyEcdsa == existingVault.pubKeyECDSA &&
                            request.oldParties == existingVault.signers &&
                            request.email == "user@example.com" &&
                            request.encryptionPassword == "fast-vault-password" &&
                            request.protocols ==
                                listOf(
                                    BatchReshareRequestJson.PROTOCOL_ECDSA,
                                    BatchReshareRequestJson.PROTOCOL_EDDSA,
                                ) &&
                            request.localPartyId == "Server-XYZ"
                    }
                )
            }
            coVerify(exactly = 0) { vultiSignerRepository.joinReshare(any()) }
        }

    @Test
    fun `reshare with tss-batch flag off dispatches legacy reshare endpoint`() =
        runTest(testDispatcher) {
            coEvery { vaultRepository.get(vaultId) } returns existingVault
            coEvery { featureFlagRepository.getFeatureFlags() } returns
                FeatureFlagJson(isTssBatchEnabled = false)

            stubReshareRoute()
            createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) {
                vultiSignerRepository.joinReshare(
                    match<JoinReshareRequestJson> { request ->
                        request.publicKeyEcdsa == existingVault.pubKeyECDSA &&
                            request.oldResharePrefix == existingVault.resharePrefix &&
                            request.oldParties == existingVault.signers
                    }
                )
            }
            coVerify(exactly = 0) { vultiSignerRepository.joinBatchReshare(any()) }
        }

    @Test
    fun `reshare on a KeyImport vault uses the batch endpoint when the flag is on`() =
        runTest(testDispatcher) {
            // iOS PR #4139 includes KeyImport in `supportsBatch` because imported seed-phrase
            // vaults still use DKLS / Schnorr at the root level. Android must match or peers
            // running iOS / Windows will deadlock against an Android initiator that fell back
            // to the legacy `/reshare` endpoint while joiners follow the QR's `is_tss_batch=true`.
            val keyImportVault = existingVault.copy(libType = SigningLibType.KeyImport)
            coEvery { vaultRepository.get(vaultId) } returns keyImportVault
            coEvery { featureFlagRepository.getFeatureFlags() } returns
                FeatureFlagJson(isTssBatchEnabled = true)

            stubReshareRoute()
            createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) {
                vultiSignerRepository.joinBatchReshare(
                    match<BatchReshareRequestJson> {
                        it.publicKeyEcdsa == keyImportVault.pubKeyECDSA
                    }
                )
            }
            coVerify(exactly = 0) { vultiSignerRepository.joinReshare(any()) }
        }

    @Test
    fun `reshare on a GG20 vault stays on the legacy endpoint even with the flag on`() =
        runTest(testDispatcher) {
            val gg20Vault = existingVault.copy(libType = SigningLibType.GG20)
            coEvery { vaultRepository.get(vaultId) } returns gg20Vault
            coEvery { featureFlagRepository.getFeatureFlags() } returns
                FeatureFlagJson(isTssBatchEnabled = true)
            // GG20 path uses GG20 lib regardless of the secret settings toggle.
            every { secretSettingsRepository.isDklsEnabled } returns flowOf(false)

            stubReshareRoute()
            createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { vultiSignerRepository.joinReshare(any()) }
            coVerify(exactly = 0) { vultiSignerRepository.joinBatchReshare(any()) }
        }
}
