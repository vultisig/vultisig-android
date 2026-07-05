package com.vultisig.wallet.ui.models

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.common.AppZipEntry
import com.vultisig.wallet.data.mappers.MapVaultToProto
import com.vultisig.wallet.data.mappers.MapVaultToProtoImpl
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CreateVaultBackupUseCase
import com.vultisig.wallet.data.usecases.backup.CreateVaultBackupFileNameUseCase
import com.vultisig.wallet.data.usecases.backup.CreateZipVaultBackupFileNameUseCase
import com.vultisig.wallet.data.usecases.backup.DeleteBackupDocumentUseCase
import com.vultisig.wallet.data.usecases.backup.IsVaultBackupFileExtensionValidUseCase
import com.vultisig.wallet.data.usecases.backup.SaveBackupToUriUseCase
import com.vultisig.wallet.ui.navigation.BackupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class BackupPasswordViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var vaultRepository: VaultRepository
    private lateinit var mapVaultToProto: MapVaultToProto
    private lateinit var createVaultBackupFileName: CreateVaultBackupFileNameUseCase
    private lateinit var createZipVaultsBackupFileName: CreateZipVaultBackupFileNameUseCase
    private lateinit var createVaultBackup: CreateVaultBackupUseCase
    private lateinit var isFileExtensionValid: IsVaultBackupFileExtensionValidUseCase
    private lateinit var navigator: Navigator<Destination>
    private lateinit var vaultDataStoreRepository: VaultDataStoreRepository
    private lateinit var snackbarFlow: SnackbarFlow
    private lateinit var saveBackupToUri: SaveBackupToUriUseCase
    private lateinit var deleteBackupDocument: DeleteBackupDocumentUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")

        vaultRepository = mockk()
        mapVaultToProto = MapVaultToProtoImpl()
        createVaultBackupFileName = mockk(relaxed = true)
        createZipVaultsBackupFileName = mockk(relaxed = true)
        createVaultBackup = mockk()
        isFileExtensionValid = mockk()
        navigator = mockk(relaxed = true)
        vaultDataStoreRepository = mockk(relaxed = true)
        snackbarFlow = mockk(relaxed = true)
        saveBackupToUri = mockk()
        deleteBackupDocument = mockk(relaxed = true)

        coEvery { isFileExtensionValid(any(), any()) } returns true
        coEvery { saveBackupToUri(any(), any<List<AppZipEntry>>()) } returns true
        every { createVaultBackup(any(), any()) } returns "encoded-vault-backup"
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun testVault(id: String) =
        Vault(id = id, name = "Vault $id", libType = SigningLibType.DKLS)

    private fun createViewModel(vaultId: String = "vault-1"): BackupPasswordViewModel {
        every { any<SavedStateHandle>().toRoute<Route.BackupPassword>(typeMap = any()) } returns
            Route.BackupPassword(vaultId = vaultId, backupType = BackupType.AllVaults)

        return BackupPasswordViewModel(
            savedStateHandle = SavedStateHandle(),
            vaultRepository = vaultRepository,
            mapVaultToProto = mapVaultToProto,
            createVaultBackupFileName = createVaultBackupFileName,
            createZipVaultsBackupFileName = createZipVaultsBackupFileName,
            createVaultBackup = createVaultBackup,
            isFileExtensionValid = isFileExtensionValid,
            navigator = navigator,
            vaultDataStoreRepository = vaultDataStoreRepository,
            snackbarFlow = snackbarFlow,
            saveBackupToUri = saveBackupToUri,
            deleteBackupDocument = deleteBackupDocument,
        )
    }

    @Test
    fun `backing up all vaults waits for the vault list to finish loading instead of writing an empty zip`() =
        runTest(testDispatcher) {
            // Regression test for issue #5173: vaults used to be a plain var populated by a
            // background coroutine, so a backup requested before that coroutine finished wrote
            // a zero-entry ZIP and still reported success.
            val vaults = listOf(testVault("a"), testVault("b"), testVault("c"))
            val vaultsLoaded = CompletableDeferred<Unit>()
            coEvery { vaultRepository.get("vault-1") } returns vaults.first()
            coEvery { vaultRepository.getAll() } coAnswers
                {
                    vaultsLoaded.await()
                    vaults
                }

            val vm = createViewModel()
            val uri = mockk<Uri>()

            // User already picked a save location before the vault list finished loading.
            vm.saveContentToUriResult(uri, "application/zip")

            // Must not write anything until the vault list is actually available.
            coVerify(exactly = 0) { saveBackupToUri(any(), any<List<AppZipEntry>>()) }

            vaultsLoaded.complete(Unit)

            // backupAllVaults hops onto the real Dispatchers.Default, so the write below
            // completes asynchronously — poll instead of asserting immediately.
            val contentSlot = slot<List<AppZipEntry>>()
            coVerify(timeout = 5_000) { saveBackupToUri(uri, capture(contentSlot)) }
            contentSlot.captured.size shouldBe vaults.size
            vaults.forEach { vault ->
                coVerify(timeout = 5_000) {
                    vaultDataStoreRepository.setBackupStatus(vault.id, true)
                }
            }
        }

    @Test
    fun `requesting the zip file name waits for the vault list before opening the document picker`() =
        runTest(testDispatcher) {
            val vaults = listOf(testVault("a"), testVault("b"))
            val vaultsLoaded = CompletableDeferred<Unit>()
            coEvery { vaultRepository.get("vault-1") } returns vaults.first()
            coEvery { vaultRepository.getAll() } coAnswers
                {
                    vaultsLoaded.await()
                    vaults
                }
            every { createZipVaultsBackupFileName(any()) } returns "vaults_backup.zip"

            val vm = createViewModel()
            var requestedFileName: String? = null
            val collectJob = launch {
                vm.createDocumentRequestFlow.collect { requestedFileName = it }
            }

            vm.backupEncryptedVault()
            requestedFileName.shouldBeNull()

            vaultsLoaded.complete(Unit)
            requestedFileName shouldBe "vaults_backup.zip"
            collectJob.cancel()
        }
}
