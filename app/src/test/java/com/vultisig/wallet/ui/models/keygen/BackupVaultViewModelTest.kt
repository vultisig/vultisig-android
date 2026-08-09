package com.vultisig.wallet.ui.models.keygen

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.mappers.MapVaultToProto
import com.vultisig.wallet.data.mappers.MapVaultToProtoImpl
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CreateVaultBackupUseCase
import com.vultisig.wallet.data.usecases.backup.CreateVaultBackupFileNameUseCase
import com.vultisig.wallet.data.usecases.backup.DeleteBackupDocumentUseCase
import com.vultisig.wallet.data.usecases.backup.IsVaultBackupFileExtensionValidUseCase
import com.vultisig.wallet.data.usecases.backup.SaveBackupToUriUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.VaultInfo
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class BackupVaultViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var vaultRepository: VaultRepository
    private lateinit var createVaultBackupFileName: CreateVaultBackupFileNameUseCase
    private lateinit var isFileExtensionValid: IsVaultBackupFileExtensionValidUseCase
    private lateinit var createVaultBackup: CreateVaultBackupUseCase
    private lateinit var mapVaultToProto: MapVaultToProto
    private lateinit var vaultDataStoreRepository: VaultDataStoreRepository
    private lateinit var snackbarFlow: SnackbarFlow
    private lateinit var saveBackupToUri: SaveBackupToUriUseCase
    private lateinit var deleteBackupDocument: DeleteBackupDocumentUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")

        navigator = mockk(relaxed = true)
        vaultRepository = mockk()
        createVaultBackupFileName = mockk(relaxed = true)
        isFileExtensionValid = mockk()
        createVaultBackup = mockk()
        mapVaultToProto = MapVaultToProtoImpl()
        vaultDataStoreRepository = mockk(relaxed = true)
        snackbarFlow = mockk(relaxed = true)
        saveBackupToUri = mockk()
        deleteBackupDocument = mockk(relaxed = true)

        coEvery { vaultRepository.awaitKeySharesReadable() } returns Unit
        coEvery { isFileExtensionValid(any(), any()) } returns true
        coEvery { saveBackupToUri(any(), any<String>()) } returns true
        every { createVaultBackup(any(), any()) } returns "backup-content"
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    private fun testVault(id: String, keyshares: List<KeyShare> = listOf(KeyShare("pub-$id", id))) =
        Vault(id = id, name = "Vault $id", libType = SigningLibType.DKLS, keyshares = keyshares)

    private fun createViewModel(): BackupVaultViewModel {
        every { any<SavedStateHandle>().toRoute<Route.BackupVault>(typeMap = any()) } returns
            Route.BackupVault(
                vaultId = "vault-1",
                vaultType = VaultInfo.VaultType.Fast,
                passwordType = Route.BackupVault.BackupPasswordType.VultiServerPassword("pw"),
            )

        return BackupVaultViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            vaultRepository = vaultRepository,
            createVaultBackupFileName = createVaultBackupFileName,
            isFileExtensionValid = isFileExtensionValid,
            createVaultBackup = createVaultBackup,
            mapVaultToProto = mapVaultToProto,
            vaultDataStoreRepository = vaultDataStoreRepository,
            snackbarFlow = snackbarFlow,
            saveBackupToUri = saveBackupToUri,
            deleteBackupDocument = deleteBackupDocument,
        )
    }

    @Test
    fun `a failed export deletes the document the picker created`() =
        runTest(testDispatcher) {
            // The picker creates the file before anything is written to it, so failing without
            // removing it leaves a 0 KB .vult that looks like a backup and restores nothing. A
            // vault with no keyshares is the reachable way to fail: the proto mapper refuses it.
            coEvery { vaultRepository.get("vault-1") } returns
                testVault("a", keyshares = emptyList())
            val vm = createViewModel()
            val uri = mockk<Uri>()

            vm.onDeviceBackupClick()
            vm.saveContentToUriResult(uri, "application/octet-stream")

            coVerify(timeout = 5_000) { deleteBackupDocument(uri) }
            coVerify(exactly = 0) { saveBackupToUri(any(), any<String>()) }
        }

    @Test
    fun `a successful export keeps the document`() =
        runTest(testDispatcher) {
            coEvery { vaultRepository.get("vault-1") } returns testVault("a")
            val vm = createViewModel()
            val uri = mockk<Uri>()

            vm.onDeviceBackupClick()
            vm.saveContentToUriResult(uri, "application/octet-stream")

            coVerify(timeout = 5_000) { saveBackupToUri(uri, any<String>()) }
            coVerify(exactly = 0) { deleteBackupDocument(any()) }
        }

    @Test
    fun `the vault is read only once its keyshares can be decrypted`() =
        runTest(testDispatcher) {
            // A vault read while the app is locked comes back without keyshares, and this one is
            // kept for the export that follows.
            val unlocked = CompletableDeferred<Unit>()
            coEvery { vaultRepository.awaitKeySharesReadable() } coAnswers { unlocked.await() }
            coEvery { vaultRepository.get("vault-1") } returns testVault("a")
            val vm = createViewModel()

            vm.onDeviceBackupClick()

            coVerify(exactly = 0) { vaultRepository.get(any()) }

            unlocked.complete(Unit)

            coVerify(timeout = 5_000) { vaultRepository.get("vault-1") }
        }
}
