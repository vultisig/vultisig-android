package com.vultisig.wallet.ui.models

import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.AppZipEntry
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DuplicateVaultException
import com.vultisig.wallet.data.usecases.MalformedVaultException
import com.vultisig.wallet.data.usecases.ParseVaultFromStringUseCase
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.data.usecases.WrongPasswordException
import com.vultisig.wallet.data.usecases.file.UriFileReaderUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ImportFileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var vaultDataStoreRepository: VaultDataStoreRepository
    private lateinit var saveVault: SaveVaultUseCase
    private lateinit var parseVaultFromString: ParseVaultFromStringUseCase
    private lateinit var vaultRepository: VaultRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var snackbarFlow: SnackbarFlow
    private lateinit var uriFileReader: UriFileReaderUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.ImportVault>() } returns Route.ImportVault()
        navigator = mockk(relaxed = true)
        vaultDataStoreRepository = mockk(relaxed = true)
        saveVault = mockk(relaxed = true)
        parseVaultFromString = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
        snackbarFlow = mockk(relaxed = true)
        uriFileReader = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        fileName: String? = null,
        fileContent: String? = "test-content",
        isZip: Boolean? = false,
        showPasswordPrompt: Boolean = false,
        zipOutputs: List<AppZipEntry> = emptyList(),
    ): ImportFileViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("uri" to null))
        val vm =
            ImportFileViewModel(
                savedStateHandle = savedStateHandle,
                navigator = navigator,
                vaultDataStoreRepository = vaultDataStoreRepository,
                saveVault = saveVault,
                parseVaultFromString = parseVaultFromString,
                vaultRepository = vaultRepository,
                chainAccountAddressRepository = chainAccountAddressRepository,
                snackBarFlow = snackbarFlow,
                uriFileReader = uriFileReader,
                defaultDispatcher = testDispatcher,
            )
        vm.uiModel.value =
            ImportFileState(
                fileName = fileName,
                fileContent = fileContent,
                isZip = isZip,
                showPasswordPrompt = showPasswordPrompt,
                zipOutputs = zipOutputs,
            )
        return vm
    }

    private fun testVault(libType: SigningLibType = SigningLibType.DKLS) =
        Vault(id = "test-vault-id", name = "Test Vault", libType = libType)

    // --- libType heuristics (preserved from original test file) --------------

    @Test
    fun `KeyImport vault with share filename keeps KeyImport libType`() = runTest {
        val vault = testVault(libType = SigningLibType.KeyImport)
        coEvery { parseVaultFromString(any(), any()) } returns vault
        val vm = createViewModel(fileName = "share1of2-test.bak")

        vm.decryptVaultData()

        val savedVaultSlot = slot<Vault>()
        coVerify { saveVault(capture(savedVaultSlot), false) }
        assertEquals(SigningLibType.KeyImport, savedVaultSlot.captured.libType)
    }

    @Test
    fun `GG20 vault with share filename gets overridden to DKLS`() = runTest {
        val vault = testVault(libType = SigningLibType.GG20)
        coEvery { parseVaultFromString(any(), any()) } returns vault
        val vm = createViewModel(fileName = "share1of2-test.bak")

        vm.decryptVaultData()

        val savedVaultSlot = slot<Vault>()
        coVerify { saveVault(capture(savedVaultSlot), false) }
        assertEquals(SigningLibType.DKLS, savedVaultSlot.captured.libType)
    }

    @Test
    fun `DKLS vault without share filename keeps DKLS`() = runTest {
        val vault = testVault(libType = SigningLibType.DKLS)
        coEvery { parseVaultFromString(any(), any()) } returns vault
        val vm = createViewModel(fileName = "test.bak")

        vm.decryptVaultData()

        val savedVaultSlot = slot<Vault>()
        coVerify { saveVault(capture(savedVaultSlot), false) }
        assertEquals(SigningLibType.DKLS, savedVaultSlot.captured.libType)
    }

    @Test
    fun `restoring an MLDSA-capable vault re-adds the QBTC token`() = runTest {
        val vault =
            Vault(
                id = "test-vault-id",
                name = "Test Vault",
                libType = SigningLibType.KeyImport,
                pubKeyMLDSA = "mldsa-pubkey",
            )
        coEvery { parseVaultFromString(any(), any()) } returns vault
        coEvery { saveVault(any(), false) } returns Unit
        coEvery { vaultDataStoreRepository.setBackupStatus(any(), any()) } returns Unit
        coEvery { chainAccountAddressRepository.getAddress(any<Coin>(), any<Vault>()) } returns
            Pair("qbtc1address", "qbtc-derived-pubkey")
        coEvery { vaultRepository.addTokenToVault(any(), any()) } returns Unit

        val vm = createViewModel(fileName = "share1of2-test.bak")
        vm.uiModel.value = vm.uiModel.value.copy(showPasswordPrompt = true)
        vm.decryptVaultData()
        vm.uiModel.first { !it.showPasswordPrompt }

        val tokenSlot = slot<Coin>()
        coVerify { vaultRepository.addTokenToVault("test-vault-id", capture(tokenSlot)) }
        assertEquals(Coins.Qbtc.QBTC.ticker, tokenSlot.captured.ticker)
        assertEquals("qbtc1address", tokenSlot.captured.address)
        assertEquals("qbtc-derived-pubkey", tokenSlot.captured.hexPublicKey)
    }

    @Test
    fun `restoring a vault without MLDSA leaves QBTC alone`() = runTest {
        val vault =
            Vault(
                id = "test-vault-id",
                name = "Test Vault",
                libType = SigningLibType.DKLS,
                pubKeyMLDSA = "",
            )
        coEvery { parseVaultFromString(any(), any()) } returns vault

        createViewModel(fileName = "share1of2-test.bak").decryptVaultData()

        coVerify(exactly = 0) { vaultRepository.addTokenToVault(any(), any()) }
    }

    // --- decryptVaultData: SaveResult routing --------------------------------

    @Test
    fun `decryptVaultData passes the text-field password to the parser`() = runTest {
        coEvery { parseVaultFromString(any(), any()) } returns testVault()
        val vm = createViewModel(fileName = "vault.bak", showPasswordPrompt = true)
        vm.passwordTextFieldState.setTextAndPlaceCursorAtEnd("hunter2")

        vm.decryptVaultData()

        coVerify { parseVaultFromString(any(), "hunter2") }
    }

    @Test
    fun `decryptVaultData success hides dialog and leaves error clear`() = runTest {
        coEvery { parseVaultFromString(any(), any()) } returns testVault()
        val vm = createViewModel(fileName = "vault.bak", showPasswordPrompt = true)

        vm.decryptVaultData()

        val state = vm.uiModel.value
        assertFalse(state.showPasswordPrompt, "dialog should close on success")
        assertNull(state.error)
    }

    @Test
    fun `decryptVaultData on DuplicateVaultException closes dialog and sets duplicate error`() =
        runTest {
            coEvery { parseVaultFromString(any(), any()) } throws DuplicateVaultException()
            val vm = createViewModel(fileName = "vault.bak", showPasswordPrompt = true)

            vm.decryptVaultData()

            val state = vm.uiModel.value
            assertFalse(state.showPasswordPrompt)
            assertEquals(
                UiText.StringResource(R.string.import_file_screen_duplicate_vault),
                state.error,
            )
            assertNull(state.fileName)
            assertNull(state.fileContent)
        }

    @Test
    fun `decryptVaultData on SQLiteConstraintException also treated as Duplicate`() = runTest {
        coEvery { parseVaultFromString(any(), any()) } returns testVault()
        coEvery { saveVault(any(), false) } throws SQLiteConstraintException()
        val vm = createViewModel(fileName = "vault.bak", showPasswordPrompt = true)

        vm.decryptVaultData()

        val state = vm.uiModel.value
        assertFalse(state.showPasswordPrompt)
        assertEquals(
            UiText.StringResource(R.string.import_file_screen_duplicate_vault),
            state.error,
        )
        assertNull(state.fileName)
        assertNull(state.fileContent)
    }

    @Test
    fun `decryptVaultData on WrongPasswordException keeps dialog open and shows password hint`() =
        runTest {
            coEvery { parseVaultFromString(any(), any()) } throws WrongPasswordException()
            val vm = createViewModel(fileName = "vault.bak", showPasswordPrompt = true)

            vm.decryptVaultData()

            val state = vm.uiModel.value
            assertTrue(state.showPasswordPrompt, "dialog must stay open on wrong password")
            assertEquals(
                UiText.StringResource(R.string.import_file_screen_password_error),
                state.passwordErrorHint,
            )
            // File state preserved so the user can retry without re-picking.
            assertEquals("vault.bak", state.fileName)
            assertEquals("test-content", state.fileContent)
            assertNull(state.error)
        }

    @Test
    fun `decryptVaultData on MalformedVaultException closes dialog and drops the file`() = runTest {
        coEvery { parseVaultFromString(any(), any()) } throws MalformedVaultException()
        val vm = createViewModel(fileName = "vault.bak", showPasswordPrompt = true)

        vm.decryptVaultData()

        val state = vm.uiModel.value
        assertFalse(state.showPasswordPrompt)
        assertEquals(UiText.StringResource(R.string.import_file_not_supported), state.error)
        assertNull(state.fileName, "malformed file must be dropped so user re-picks")
        assertNull(state.fileContent)
    }

    @Test
    fun `decryptVaultData on generic failure closes dialog and keeps file for retry`() = runTest {
        coEvery { parseVaultFromString(any(), any()) } returns testVault()
        coEvery { saveVault(any(), false) } throws RuntimeException("db locked")
        val vm = createViewModel(fileName = "vault.bak", showPasswordPrompt = true)

        vm.decryptVaultData()

        val state = vm.uiModel.value
        assertFalse(state.showPasswordPrompt)
        assertEquals(UiText.StringResource(R.string.dialog_default_error_body), state.error)
        // Generic/post-save failures keep the file so the user can retry without re-picking.
        assertEquals("vault.bak", state.fileName)
        assertEquals("test-content", state.fileContent)
    }

    @Test
    fun `decryptVaultData does not misclassify CancellationException as a failure state`() =
        runTest {
            // Regression guard: saveToDb must rethrow CancellationException rather than map it
            // to SaveResult.Failed. If the generic `catch (e: Exception)` swallowed
            // CancellationException, saveToDb would return SaveResult.Failed, which would
            // trigger showGenericError and set `state.error`. Asserting state unchanged
            // AND that saveVault was never reached proves the rethrow path fired.
            coEvery { parseVaultFromString(any(), any()) } throws CancellationException("cancelled")
            val vm = createViewModel(fileName = "vault.bak", showPasswordPrompt = true)

            vm.decryptVaultData()

            val state = vm.uiModel.value
            assertNull(state.error, "cancellation must not set a user-facing error")
            assertNull(state.passwordErrorHint, "cancellation must not set password hint")
            assertTrue(
                state.showPasswordPrompt,
                "cancellation must not flip UI state as if the save completed",
            )
            coVerify(exactly = 0) { saveVault(any(), any()) }
        }

    // --- parseFileContent: SaveResult routing (no-password first pass) -------

    @Test
    fun `parseFileContent on WrongPassword opens password prompt and clears stale hint`() =
        runTest {
            val uri = mockk<Uri>()

            coEvery { uriFileReader.readContent(uri) } returns "encrypted-file-content"
            // Encrypted new-format without password → parser throws WrongPasswordException.
            coEvery { parseVaultFromString(any(), null) } throws WrongPasswordException()

            val vm = createViewModel(fileContent = null, isZip = false)
            vm.uiModel.value = vm.uiModel.value.copy(fileUri = uri)

            vm.saveFileToAppDir()

            val state = vm.uiModel.value
            assertTrue(state.showPasswordPrompt, "first-pass WrongPassword must open the prompt")
            assertNull(state.passwordErrorHint, "prompt opens clean on first pass")
        }

    @Test
    fun `parseFileContent on Malformed shows unsupported-file error and does not open prompt`() =
        runTest {
            val uri = mockk<Uri>()

            coEvery { uriFileReader.readContent(uri) } returns "garbage"
            coEvery { parseVaultFromString(any(), null) } throws MalformedVaultException()

            val vm = createViewModel(fileContent = null, isZip = false, fileName = "vault.bak")
            vm.uiModel.value = vm.uiModel.value.copy(fileUri = uri, fileName = "vault.bak")

            vm.saveFileToAppDir()

            val state = vm.uiModel.value
            assertFalse(state.showPasswordPrompt, "malformed must NOT open password prompt")
            assertEquals(UiText.StringResource(R.string.import_file_not_supported), state.error)
            assertNull(state.fileName)
            assertNull(state.fileContent)
        }

    @Test
    fun `parseFileContent on Success imports without prompting for a password`() = runTest {
        val uri = mockk<Uri>()

        coEvery { uriFileReader.readContent(uri) } returns "unencrypted-valid"
        coEvery { parseVaultFromString(any(), null) } returns testVault()

        val vm = createViewModel(fileContent = null, isZip = false, fileName = "vault.bak")
        vm.uiModel.value = vm.uiModel.value.copy(fileUri = uri, fileName = "vault.bak")

        vm.saveFileToAppDir()

        val state = vm.uiModel.value
        assertFalse(state.showPasswordPrompt, "success must not pop the password prompt")
        assertNull(state.error)
        coVerify { saveVault(any(), false) }
    }

    @Test
    fun `parseFileContent on Duplicate closes any prompt and shows duplicate error`() = runTest {
        val uri = mockk<Uri>()

        coEvery { uriFileReader.readContent(uri) } returns "unencrypted-duplicate"
        coEvery { parseVaultFromString(any(), null) } returns testVault()
        coEvery { saveVault(any(), false) } throws DuplicateVaultException()

        val vm = createViewModel(fileContent = null, isZip = false, fileName = "dup.bak")
        vm.uiModel.value = vm.uiModel.value.copy(fileUri = uri, fileName = "dup.bak")

        vm.saveFileToAppDir()

        val state = vm.uiModel.value
        assertFalse(state.showPasswordPrompt)
        assertEquals(
            UiText.StringResource(R.string.import_file_screen_duplicate_vault),
            state.error,
        )
        assertNull(state.fileName)
        assertNull(state.fileContent)
    }

    @Test
    fun `parseFileContent on post-save Failed does not open password prompt`() = runTest {
        // Regression guard for architect's flagged bug: without differentiated
        // SaveResult.Failed, a post-save DB error during the no-password first pass
        // would pop a password prompt and confuse the user into typing a password
        // for a vault that's already decoded.
        val uri = mockk<Uri>()

        coEvery { uriFileReader.readContent(uri) } returns "valid-unencrypted"
        coEvery { parseVaultFromString(any(), null) } returns testVault()
        coEvery { saveVault(any(), false) } throws RuntimeException("db locked")

        val vm = createViewModel(fileContent = null, isZip = false, fileName = "vault.bak")
        vm.uiModel.value = vm.uiModel.value.copy(fileUri = uri, fileName = "vault.bak")

        vm.saveFileToAppDir()

        val state = vm.uiModel.value
        assertFalse(state.showPasswordPrompt, "post-save Failed must NOT open password prompt")
        assertEquals(UiText.StringResource(R.string.dialog_default_error_body), state.error)
        // File-retention policy: Failed keeps the file so the user can retry without
        // re-selecting (the file is fine; the failure was downstream).
        assertEquals("vault.bak", state.fileName, "Failed must preserve fileName for retry")
        assertEquals(
            "valid-unencrypted",
            state.fileContent,
            "Failed must preserve fileContent for retry",
        )
    }

    // --- Zip-mode error routing ---------------------------------------------

    @Test
    fun `decryptVaultData zip Malformed shows snackbar and removes bad entry`() = runTest {
        coEvery { parseVaultFromString(any(), any()) } throws MalformedVaultException()
        val badEntry = AppZipEntry(name = "bad.bak", content = "bad-file-content")
        val goodEntry = AppZipEntry(name = "good.bak", content = "good-file-content")
        val vm =
            createViewModel(
                fileName = "bundle.zip",
                fileContent = "bad-file-content",
                isZip = true,
                showPasswordPrompt = true,
                zipOutputs = listOf(badEntry, goodEntry),
            )

        vm.decryptVaultData()

        val state = vm.uiModel.value
        // Zip malformed must NOT set the top-level error banner — snackbar only.
        assertNull(state.error)
        assertFalse(state.showPasswordPrompt)
        // Bad entry removed; good entry preserved; fileContent cleared so re-tap goes to good.
        assertEquals(listOf(goodEntry), state.zipOutputs)
        assertNull(state.fileContent)
    }

    @Test
    fun `decryptVaultData zip Duplicate shows snackbar and removes the duplicate entry`() =
        runTest {
            coEvery { parseVaultFromString(any(), any()) } throws DuplicateVaultException()
            val dup = AppZipEntry(name = "vault.bak", content = "dup-content")
            val other = AppZipEntry(name = "other.bak", content = "other-content")
            val vm =
                createViewModel(
                    fileName = "bundle.zip",
                    fileContent = "dup-content",
                    isZip = true,
                    showPasswordPrompt = true,
                    zipOutputs = listOf(dup, other),
                )

            vm.decryptVaultData()

            val state = vm.uiModel.value
            // Snackbar only — no state-level error banner. Duplicate entry is removed from the
            // zip list so re-tapping can't loop on it; other entries remain.
            assertNull(state.error)
            assertEquals(listOf(other), state.zipOutputs)
            assertNull(state.fileContent)
        }

    @Test
    fun `decryptVaultData zip generic Failed shows snackbar and keeps file intact`() = runTest {
        coEvery { parseVaultFromString(any(), any()) } returns testVault()
        coEvery { saveVault(any(), false) } throws RuntimeException("db locked")
        val entry = AppZipEntry(name = "vault.bak", content = "keep-me")
        val vm =
            createViewModel(
                fileName = "bundle.zip",
                fileContent = "keep-me",
                isZip = true,
                showPasswordPrompt = true,
                zipOutputs = listOf(entry),
            )

        vm.decryptVaultData()

        val state = vm.uiModel.value
        // Zip generic failure uses snackbar and keeps the zip list + fileContent for retry.
        assertNull(state.error)
        assertEquals(listOf(entry), state.zipOutputs)
        assertEquals("keep-me", state.fileContent)
    }

    @Test
    fun `saveFileToAppDir shows error state on SQLite constraint`() = runTest {
        val uri = mockk<Uri>()

        coEvery { uriFileReader.readContent(uri) } returns "test-content"
        coEvery { parseVaultFromString(any(), any()) } returns testVault()
        coEvery { saveVault(any(), false) } throws SQLiteConstraintException()

        val vm = createViewModel(fileContent = null)
        vm.uiModel.value = vm.uiModel.value.copy(fileUri = uri)

        vm.saveFileToAppDir()

        val state = vm.uiModel.first { it.error != null }
        assertEquals(
            UiText.StringResource(R.string.import_file_screen_duplicate_vault),
            state.error,
        )
        assertNull(state.fileName)
        assertNull(state.fileContent)
    }

    // --- Post-save hardening: non-fatal downstream failures ------------------

    @Test
    fun `import succeeds when setBackupStatus fails`() = runTest {
        coEvery { parseVaultFromString(any(), any()) } returns testVault()
        coEvery { vaultDataStoreRepository.setBackupStatus(any(), any()) } throws
            RuntimeException("datastore")
        val vm = createViewModel(fileName = "vault.bak")

        vm.decryptVaultData()

        val state = vm.uiModel.value
        assertNull(state.error)
        coVerify { saveVault(any(), false) }
    }

    @Test
    fun `import succeeds when QBTC address derivation fails on MLDSA vault`() = runTest {
        val mldsaVault =
            Vault(
                id = "v",
                name = "V",
                libType = SigningLibType.DKLS,
                pubKeyMLDSA = "mldsa-pub-key",
            )
        coEvery { parseVaultFromString(any(), any()) } returns mldsaVault
        coEvery { chainAccountAddressRepository.getAddress(any<Coin>(), any<Vault>()) } throws
            RuntimeException("derivation failed")
        val vm = createViewModel(fileName = "vault.bak")

        vm.decryptVaultData()

        val state = vm.uiModel.value
        assertNull(state.error, "QBTC derivation failure must not block import")
        coVerify { saveVault(any(), false) }
    }

    @Test
    fun `saveFileToAppDir surfaces unsupported error when fileContent returns null`() = runTest {
        val uri = mockk<Uri>()

        coEvery { uriFileReader.readContent(uri) } returns null

        val vm = createViewModel(fileContent = null)
        vm.uiModel.value =
            vm.uiModel.value.copy(fileUri = uri, fileName = "share1of2-test.bak", isZip = false)

        vm.saveFileToAppDir()

        val state = vm.uiModel.first { it.error != null }
        assertEquals(UiText.StringResource(R.string.import_file_not_supported), state.error)
        assertEquals(null, state.fileUri)
        assertEquals(null, state.fileName)
        assertEquals(null, state.fileContent)
        assertEquals(null, state.isZip)
        coVerify(exactly = 0) { parseVaultFromString(any(), any()) }
    }
}
