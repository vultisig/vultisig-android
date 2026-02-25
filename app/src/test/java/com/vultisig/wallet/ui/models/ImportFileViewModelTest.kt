package com.vultisig.wallet.ui.models

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.fileContent
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.usecases.DiscoverTokenUseCase
import com.vultisig.wallet.data.usecases.ParseVaultFromStringUseCase
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
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
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class ImportFileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var context: Context
    private lateinit var vaultDataStoreRepository: VaultDataStoreRepository
    private lateinit var saveVault: SaveVaultUseCase
    private lateinit var parseVaultFromString: ParseVaultFromStringUseCase
    private lateinit var discoverToken: DiscoverTokenUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.ImportVault>() } returns Route.ImportVault()
        navigator = mockk(relaxed = true)
        context = mockk(relaxed = true)
        vaultDataStoreRepository = mockk(relaxed = true)
        saveVault = mockk(relaxed = true)
        parseVaultFromString = mockk(relaxed = true)
        discoverToken = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("com.vultisig.wallet.data.common.FileHelperKt")
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        fileName: String? = null,
        fileContent: String? = "test-content",
    ): ImportFileViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf("uri" to null)
        )
        val vm = ImportFileViewModel(
            savedStateHandle = savedStateHandle,
            navigator = navigator,
            context = context,
            vaultDataStoreRepository = vaultDataStoreRepository,
            saveVault = saveVault,
            parseVaultFromString = parseVaultFromString,
            discoverToken = discoverToken,
        )
        vm.uiModel.value = ImportFileState(
            fileName = fileName,
            fileContent = fileContent,
            isZip = false,
        )
        return vm
    }

    private fun testVault(
        libType: SigningLibType = SigningLibType.DKLS,
    ) = Vault(
        id = "test-vault-id",
        name = "Test Vault",
        libType = libType,
    )

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
    fun `decryptVaultData sends duplicate snackbar on SQLite constraint`() = runTest {
        coEvery { parseVaultFromString(any(), any()) } returns testVault()
        coEvery { saveVault(any(), false) } throws SQLiteConstraintException()
        val vm = createViewModel(fileName = "share1of2-test.bak")

        vm.decryptVaultData()

        assertEquals(
            UiText.StringResource(R.string.import_file_screen_duplicate_vault),
            vm.snackBarChannelFlow.first()
        )
    }

    @Test
    fun `saveFileToAppDir sends duplicate snackbar on SQLite constraint`() = runTest {
        val uri = mockk<Uri>()
        mockkStatic("com.vultisig.wallet.data.common.FileHelperKt")
        coEvery { uri.fileContent(context) } returns "test-content"
        coEvery { parseVaultFromString(any(), any()) } returns testVault()
        coEvery { saveVault(any(), false) } throws SQLiteConstraintException()

        val vm = createViewModel(fileContent = null)
        vm.uiModel.value = vm.uiModel.value.copy(fileUri = uri)

        vm.saveFileToAppDir()

        assertEquals(
            UiText.StringResource(R.string.import_file_screen_duplicate_vault),
            vm.snackBarChannelFlow.first()
        )
    }
}
