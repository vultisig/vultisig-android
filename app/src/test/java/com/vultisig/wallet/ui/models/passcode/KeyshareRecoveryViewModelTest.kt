package com.vultisig.wallet.ui.models.passcode

import android.net.Uri
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.AppZipContents
import com.vultisig.wallet.data.common.AppZipEntry
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.data.usecases.DuplicateVaultException
import com.vultisig.wallet.data.usecases.MalformedVaultException
import com.vultisig.wallet.data.usecases.ParseVaultFromStringUseCase
import com.vultisig.wallet.data.usecases.StoreImportedVaultUseCase
import com.vultisig.wallet.data.usecases.WrongPasswordException
import com.vultisig.wallet.data.usecases.file.UriFileReaderUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class KeyshareRecoveryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val uri = mockk<Uri>()
    private val passcodeState = MutableStateFlow<PasscodeState>(PasscodeState.KeyUnavailable)

    private lateinit var uriFileReader: UriFileReaderUseCase
    private lateinit var parseVaultFromString: ParseVaultFromStringUseCase
    private lateinit var storeImportedVault: StoreImportedVaultUseCase
    private lateinit var passcodeRepository: PasscodeRepository
    private lateinit var model: KeyshareRecoveryViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        uriFileReader = mockk()
        parseVaultFromString = mockk()
        storeImportedVault = mockk()
        passcodeRepository = mockk(relaxUnitFun = true)
        every { passcodeRepository.state } returns passcodeState
        coEvery { uriFileReader.isValidZip(uri) } returns false
        coEvery { uriFileReader.readContent(uri) } returns "backup-file-content"
        coEvery { uriFileReader.readName(uri) } returns "Main-share2of2.vult"
        coEvery { parseVaultFromString(any(), any()) } returns vault
        coEvery { storeImportedVault(any(), any()) } returns vault
        model =
            KeyshareRecoveryViewModel(
                uriFileReader = uriFileReader,
                parseVaultFromString = parseVaultFromString,
                storeImportedVault = storeImportedVault,
                passcodeRepository = passcodeRepository,
                defaultDispatcher = testDispatcher,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Makes the picked file an archive carrying [entries]. */
    private fun archiveOf(vararg entries: AppZipEntry, isComplete: Boolean = true) {
        coEvery { uriFileReader.isValidZip(uri) } returns true
        coEvery { uriFileReader.extractZipEntries(uri) } returns
            AppZipContents(entries = entries.toList(), isComplete = isComplete)
    }

    // ---- the recovery -------------------------------------------------------

    @Test
    fun `a restored backup asks the gate to resolve again`() = runTest {
        model.onBackupPicked(uri)

        coVerify { passcodeRepository.retry() }
    }

    /**
     * The file name is what tells a mislabelled older DKLS backup from a GG20 one, and getting it
     * wrong stores a vault that runs the wrong signing protocol.
     */
    @Test
    fun `the backup's file name is stored with it`() = runTest {
        model.onBackupPicked(uri)

        coVerify { storeImportedVault(vault, "Main-share2of2.vult") }
    }

    /**
     * Only the last vault's restore takes the gate down, so a restore that leaves others sealed has
     * to say so — the screen is otherwise unchanged and would read as a button that did nothing.
     */
    @Test
    fun `a restore that leaves the gate closed says what it did`() = runTest {
        model.onBackupPicked(uri)

        model.state.value.message shouldBe R.string.passcode_key_unavailable_restored
    }

    /** Claiming another vault still needs its backup once the gate is lifting would be false. */
    @Test
    fun `a restore that lifts the gate claims nothing about other vaults`() = runTest {
        coEvery { passcodeRepository.retry() } answers
            {
                passcodeState.value = PasscodeState.Disabled
            }

        model.onBackupPicked(uri)

        model.state.value.message shouldBe R.string.passcode_key_unavailable_message
    }

    @Test
    fun `picking nothing leaves the screen alone`() = runTest {
        model.onBackupPicked(null)

        model.state.value shouldBe KeyshareRecoveryUiModel()
        coVerify(exactly = 0) { storeImportedVault(any(), any()) }
    }

    // ---- multi-vault archives -----------------------------------------------

    /** "Back up all vaults" writes one archive, and the gate needs a share per vault to open. */
    @Test
    fun `every share an archive carries is restored`() = runTest {
        archiveOf(
            AppZipEntry(name = "Main-share2of2.vult", content = "share-a"),
            AppZipEntry(name = "Savings-share2of2.vult", content = "share-b"),
        )

        model.onBackupPicked(uri)

        coVerify { parseVaultFromString("share-a", null) }
        coVerify { parseVaultFromString("share-b", null) }
        coVerify { storeImportedVault(vault, "Savings-share2of2.vult") }
    }

    /** The shares an archive holds for vaults this device never lost are duplicates by design. */
    @Test
    fun `an archive still counts as restored when only some of its shares are new`() = runTest {
        archiveOf(
            AppZipEntry(name = "Main-share2of2.vult", content = "share-a"),
            AppZipEntry(name = "Savings-share2of2.vult", content = "share-b"),
        )
        coEvery { storeImportedVault(any(), "Main-share2of2.vult") } throws
            DuplicateVaultException()

        model.onBackupPicked(uri)

        model.state.value.message shouldBe R.string.passcode_key_unavailable_restored
    }

    @Test
    fun `an archive whose shares all fail reports the refusal`() = runTest {
        archiveOf(AppZipEntry(name = "Main-share2of2.vult", content = "share-a"))
        coEvery { storeImportedVault(any(), any()) } throws DuplicateVaultException()

        model.onBackupPicked(uri)

        model.state.value.message shouldBe R.string.import_file_screen_duplicate_vault
    }

    @Test
    fun `an empty archive is reported as unsupported`() = runTest {
        archiveOf()

        model.onBackupPicked(uri)

        model.state.value.message shouldBe R.string.import_file_not_supported
    }

    /** An archive that stopped part-way is not the file being unsupported. */
    @Test
    fun `an archive that could not be read through says so`() = runTest {
        archiveOf(isComplete = false)

        model.onBackupPicked(uri)

        model.state.value.message shouldBe R.string.import_file_zip_incomplete
    }

    /**
     * Otherwise the user is sent hunting for a backup they are already holding, when the cause is
     * that the app stopped reading their archive.
     */
    @Test
    fun `a partly read archive says so even when a share landed`() = runTest {
        archiveOf(
            AppZipEntry(name = "Main-share2of2.vult", content = "share-a"),
            isComplete = false,
        )

        model.onBackupPicked(uri)

        model.state.value.message shouldBe R.string.import_file_zip_incomplete
    }

    /**
     * A store can throw after `SupersedeUnopenableVaultUseCase` has already replaced the row, so
     * the gate is re-read whatever the outcome — otherwise it stays closed over a restored vault.
     */
    @Test
    fun `the gate is re-read even when the store reports a failure`() = runTest {
        coEvery { storeImportedVault(any(), any()) } throws RuntimeException("derivation failed")

        model.onBackupPicked(uri)

        coVerify { passcodeRepository.retry() }
    }

    /**
     * The password is asked for before anything is stored, so a share needing one cannot leave the
     * shares ahead of it stored with the screen saying nothing about them.
     */
    @Test
    fun `a share needing a password stores nothing until it is given`() = runTest {
        archiveOf(
            AppZipEntry(name = "Main-share2of2.vult", content = "share-a"),
            AppZipEntry(name = "Savings-share2of2.vult", content = "share-b"),
        )
        coEvery { parseVaultFromString("share-b", null) } throws WrongPasswordException()

        model.onBackupPicked(uri)

        model.state.value.isPasswordPromptVisible shouldBe true
        coVerify(exactly = 0) { storeImportedVault(any(), any()) }
        coVerify(exactly = 0) { passcodeRepository.retry() }
    }

    // ---- password-protected backups -----------------------------------------

    @Test
    fun `a backup that needs a password opens the prompt without accusing the user`() = runTest {
        coEvery { parseVaultFromString(any(), null) } throws WrongPasswordException()

        model.onBackupPicked(uri)

        model.state.value.isPasswordPromptVisible shouldBe true
        model.state.value.passwordError shouldBe null
    }

    @Test
    fun `a wrong password keeps the prompt open and says so`() = runTest {
        coEvery { parseVaultFromString(any(), any()) } throws WrongPasswordException()
        model.onBackupPicked(uri)
        model.passwordTextFieldState.setTextAndPlaceCursorAtEnd("hunter2")

        model.onPasswordEntered()

        model.state.value.isPasswordPromptVisible shouldBe true
        model.state.value.passwordError shouldBe R.string.import_file_screen_password_error
    }

    @Test
    fun `the right password restores the same file the prompt was opened for`() = runTest {
        coEvery { parseVaultFromString(any(), null) } throws WrongPasswordException()
        model.onBackupPicked(uri)
        model.passwordTextFieldState.setTextAndPlaceCursorAtEnd("hunter2")

        model.onPasswordEntered()

        coVerify { parseVaultFromString("backup-file-content", "hunter2") }
        model.state.value.isPasswordPromptVisible shouldBe false
        model.state.value.message shouldBe R.string.passcode_key_unavailable_restored
    }

    /** A password typed for one backup must not be submitted against the next. */
    @Test
    fun `picking another backup clears the password that was typed for the last one`() = runTest {
        coEvery { parseVaultFromString(any(), null) } throws WrongPasswordException()
        model.onBackupPicked(uri)
        model.passwordTextFieldState.setTextAndPlaceCursorAtEnd("hunter2")

        model.onBackupPicked(uri)

        model.passwordTextFieldState.text.toString() shouldBe ""
    }

    @Test
    fun `entering a password before a backup is picked does nothing`() = runTest {
        model.onPasswordEntered()

        coVerify(exactly = 0) { parseVaultFromString(any(), any()) }
    }

    // ---- what the screen says when a backup does not work -------------------

    @Test
    fun `an unreadable file is reported as unsupported`() = runTest {
        coEvery { uriFileReader.readContent(uri) } returns null

        model.onBackupPicked(uri)

        model.state.value.message shouldBe R.string.import_file_not_supported
        coVerify(exactly = 0) { parseVaultFromString(any(), any()) }
    }

    @Test
    fun `an empty file is reported as unsupported`() = runTest {
        coEvery { uriFileReader.readContent(uri) } returns "   "

        model.onBackupPicked(uri)

        model.state.value.message shouldBe R.string.import_file_not_supported
    }

    @Test
    fun `a malformed backup is reported as unsupported`() = runTest {
        coEvery { parseVaultFromString(any(), any()) } throws MalformedVaultException()

        model.onBackupPicked(uri)

        model.state.value.message shouldBe R.string.import_file_not_supported
    }

    /** A vault the device can still open, or one already restored this session. */
    @Test
    fun `a backup that restores nothing this device is missing is reported as a duplicate`() =
        runTest {
            coEvery { storeImportedVault(any(), any()) } throws DuplicateVaultException()

            model.onBackupPicked(uri)

            model.state.value.message shouldBe R.string.import_file_screen_duplicate_vault
        }

    @Test
    fun `an unexpected failure is reported without claiming anything was restored`() = runTest {
        coEvery { storeImportedVault(any(), any()) } throws RuntimeException("db locked")

        model.onBackupPicked(uri)

        model.state.value.message shouldBe R.string.dialog_default_error_body
    }

    /** A failed attempt must not leave its message standing where the instruction belongs. */
    @Test
    fun `a new attempt clears the last one's message`() = runTest {
        coEvery { parseVaultFromString(any(), any()) } throws MalformedVaultException()
        model.onBackupPicked(uri)
        coEvery { parseVaultFromString(any(), null) } throws WrongPasswordException()

        model.onBackupPicked(uri)

        model.state.value.message shouldBe R.string.passcode_key_unavailable_message
    }

    // ---- the retry the gate already offered ---------------------------------

    @Test
    fun `retry re-reads the keystore`() = runTest {
        model.onRetry()

        coVerify { passcodeRepository.retry() }
    }

    private companion object {
        val vault = Vault(id = "restored-vault-id", name = "Main")
    }
}
