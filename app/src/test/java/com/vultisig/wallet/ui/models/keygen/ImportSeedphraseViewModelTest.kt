@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshots.Snapshot
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.KeyImportRepository
import com.vultisig.wallet.data.usecases.CheckMnemonicDuplicateUseCase
import com.vultisig.wallet.data.usecases.MnemonicValidationResult
import com.vultisig.wallet.data.usecases.ValidateMnemonicUseCase
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ImportSeedphraseViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private lateinit var navigator: Navigator<Destination>
    private lateinit var validateMnemonic: ValidateMnemonicUseCase
    private lateinit var keyImportRepository: KeyImportRepository
    private lateinit var checkMnemonicDuplicate: CheckMnemonicDuplicateUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        navigator = mockk(relaxed = true)
        validateMnemonic = mockk()
        every { validateMnemonic(any<String>()) } returns MnemonicValidationResult.Valid
        keyImportRepository = mockk(relaxed = true)
        checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { false }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ImportSeedphraseViewModel(
        navigator = navigator,
        validateMnemonic = validateMnemonic,
        keyImportRepository = keyImportRepository,
        checkMnemonicDuplicate = checkMnemonicDuplicate,
    )

    private fun TextFieldState.setTextAndNotify(text: String) {
        edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
    }

    // region Initial State

    @Test
    fun `initial state has correct defaults`() = runTest(mainDispatcher) {
        val vm = createViewModel()

        val state = vm.state.value
        assertEquals(0, state.wordCount)
        assertEquals(12, state.expectedWordCount)
        assertNull(state.errorMessage)
        assertFalse(state.isImportEnabled)
        assertFalse(state.isImporting)
        assertEquals(VsTextInputFieldInnerState.Default, state.innerState)
    }

    // endregion

    // region Immediate Input Observer (error clearing)

    @Test
    fun `typing clears error message immediately`() = runTest(mainDispatcher) {
        every { validateMnemonic(any()) } returns MnemonicValidationResult.InvalidPhrase
        val vm = createViewModel()

        // Type to trigger debounced validation error
        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()

        // Verify error is set
        assertNotNull(vm.state.value.errorMessage)
        assertEquals(VsTextInputFieldInnerState.Error, vm.state.value.innerState)

        // Type again - error should clear before next debounce
        vm.mnemonicFieldState.setTextAndNotify("$TWELVE_WORDS extra")

        assertNull(vm.state.value.errorMessage)
        assertEquals(VsTextInputFieldInnerState.Default, vm.state.value.innerState)
    }

    @Test
    fun `typing resets isImportEnabled to false`() = runTest(mainDispatcher) {
        every { validateMnemonic(any()) } returns MnemonicValidationResult.Valid
        val vm = createViewModel()

        // Get to valid state
        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()
        assertTrue(vm.state.value.isImportEnabled)

        // Type more - isImportEnabled should reset
        vm.mnemonicFieldState.setTextAndNotify("$TWELVE_WORDS extra")

        assertFalse(vm.state.value.isImportEnabled)
    }

    @Test
    fun `typing resets innerState to Default`() = runTest(mainDispatcher) {
        every { validateMnemonic(any()) } returns MnemonicValidationResult.Valid
        val vm = createViewModel()

        // Get to success state
        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()
        assertEquals(VsTextInputFieldInnerState.Success, vm.state.value.innerState)

        // Type more - should reset to Default
        vm.mnemonicFieldState.setTextAndNotify("$TWELVE_WORDS extra")

        assertEquals(VsTextInputFieldInnerState.Default, vm.state.value.innerState)
    }

    // endregion

    // region Debounced Validation

    @Test
    fun `empty input resets to default state after debounce`() = runTest(mainDispatcher) {
        every { validateMnemonic(any()) } returns MnemonicValidationResult.Valid
        val vm = createViewModel()

        // Type something and wait for validation
        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()
        assertTrue(vm.state.value.isImportEnabled)

        // Clear text
        vm.mnemonicFieldState.setTextAndNotify("")
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(0, vm.state.value.wordCount)
        assertEquals(12, vm.state.value.expectedWordCount)
        assertNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isImportEnabled)
        assertEquals(VsTextInputFieldInnerState.Default, vm.state.value.innerState)
    }

    @Test
    fun `valid mnemonic sets success state after debounce`() = runTest(mainDispatcher) {
        every { validateMnemonic(any()) } returns MnemonicValidationResult.Valid
        val vm = createViewModel()

        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(12, vm.state.value.wordCount)
        assertEquals(12, vm.state.value.expectedWordCount)
        assertNull(vm.state.value.errorMessage)
        assertTrue(vm.state.value.isImportEnabled)
        assertEquals(VsTextInputFieldInnerState.Success, vm.state.value.innerState)
    }

    @Test
    fun `invalid word count sets error state after debounce`() = runTest(mainDispatcher) {
        every { validateMnemonic(any()) } returns MnemonicValidationResult.InvalidWordCount(5)
        val vm = createViewModel()

        vm.mnemonicFieldState.setTextAndNotify("one two three four five")
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(5, vm.state.value.wordCount)
        assertEquals(12, vm.state.value.expectedWordCount)
        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isImportEnabled)
        assertEquals(VsTextInputFieldInnerState.Error, vm.state.value.innerState)
    }

    @Test
    fun `invalid phrase sets error state after debounce`() = runTest(mainDispatcher) {
        every { validateMnemonic(any()) } returns MnemonicValidationResult.InvalidPhrase
        val vm = createViewModel()

        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(12, vm.state.value.wordCount)
        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isImportEnabled)
        assertEquals(VsTextInputFieldInnerState.Error, vm.state.value.innerState)
    }

    @Test
    fun `expected word count switches to 24 when more than 12 words`() =
        runTest(mainDispatcher) {
            every { validateMnemonic(any()) } returns MnemonicValidationResult.InvalidWordCount(13)
            val vm = createViewModel()

            vm.mnemonicFieldState.setTextAndNotify("$TWELVE_WORDS thirteen")
            advanceTimeBy(600)
            advanceUntilIdle()

            assertEquals(13, vm.state.value.wordCount)
            assertEquals(24, vm.state.value.expectedWordCount)
        }

    @Test
    fun `expected word count stays at 12 when 12 or fewer words`() =
        runTest(mainDispatcher) {
            every { validateMnemonic(any()) } returns MnemonicValidationResult.InvalidWordCount(8)
            val vm = createViewModel()

            vm.mnemonicFieldState.setTextAndNotify("one two three four five six seven eight")
            advanceTimeBy(600)
            advanceUntilIdle()

            assertEquals(8, vm.state.value.wordCount)
            assertEquals(12, vm.state.value.expectedWordCount)
        }

    @Test
    fun `validation is not triggered before debounce period`() = runTest(mainDispatcher) {
        var validateCalled = false
        every { validateMnemonic(any()) } answers {
            validateCalled = true
            MnemonicValidationResult.Valid
        }
        val vm = createViewModel()

        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(300) // only 300ms, debounce is 500ms

        // Debounced validation hasn't run yet
        assertFalse(validateCalled)
        assertFalse(vm.state.value.isImportEnabled)
    }

    // endregion

    // region cleanMnemonic

    @Test
    fun `validation receives normalized whitespace`() = runTest(mainDispatcher) {
        var capturedInput = ""
        every { validateMnemonic(any()) } answers {
            capturedInput = firstArg()
            MnemonicValidationResult.Valid
        }
        val vm = createViewModel()

        // Input with multiple spaces, tabs, newlines
        vm.mnemonicFieldState.setTextAndNotify(
            "  one  two\tthree\nfour   five  six seven eight nine ten eleven twelve  "
        )
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(
            "one two three four five six seven eight nine ten eleven twelve",
            capturedInput
        )
    }

    @Test
    fun `whitespace-only input is treated as empty`() = runTest(mainDispatcher) {
        val vm = createViewModel()

        vm.mnemonicFieldState.setTextAndNotify("   \t\n  ")
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(0, vm.state.value.wordCount)
        assertNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isImportEnabled)
        assertEquals(VsTextInputFieldInnerState.Default, vm.state.value.innerState)
    }

    // endregion

    // region importSeedphrase — double-submit guard

    @Test
    fun `double submit guard prevents concurrent imports`() = runTest(mainDispatcher) {
        // Make checkMnemonicDuplicate suspend forever to simulate ongoing import
        checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase {
            delay(Long.MAX_VALUE)
            false
        }
        val vm = createViewModel()

        // Type valid phrase and wait for validation
        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()
        assertTrue(vm.state.value.isImportEnabled)

        // First import starts — sets isImporting=true, suspends at checkMnemonicDuplicate
        vm.importSeedphrase()
        assertTrue(vm.state.value.isImporting)

        // Second import should be blocked by isImporting guard
        vm.importSeedphrase()
        assertTrue(vm.state.value.isImporting)
    }

    @Test
    fun `double submit guard does not call duplicate check twice`() = runTest(mainDispatcher) {
        var duplicateCheckCount = 0
        checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase {
            duplicateCheckCount++
            delay(Long.MAX_VALUE)
            false
        }
        val vm = createViewModel()

        // Type valid phrase and wait for validation
        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()

        // First import — suspends at checkMnemonicDuplicate
        vm.importSeedphrase()
        // Second import — blocked by isImporting guard
        vm.importSeedphrase()

        assertEquals(1, duplicateCheckCount)
    }

    // endregion

    // region importSeedphrase — success path

    @Test
    fun `importSeedphrase navigates on success when not duplicate`() =
        runTest(mainDispatcher) {
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { false }
            val vm = createViewModel()

            vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
            advanceTimeBy(600)
            advanceUntilIdle()

            vm.importSeedphrase()
            advanceUntilIdle()

            assertFalse(vm.state.value.isImporting)
            coVerify { navigator.route(Route.KeyImport.ChainsSetup) }
        }

    @Test
    fun `importSeedphrase success sets mnemonic in repository`() =
        runTest(mainDispatcher) {
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { false }
            val vm = createViewModel()

            vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
            advanceTimeBy(600)
            advanceUntilIdle()

            vm.importSeedphrase()
            advanceUntilIdle()

            verify { keyImportRepository.setMnemonic(TWELVE_WORDS) }
        }

    @Test
    fun `importSeedphrase success passes cleaned mnemonic to setMnemonic`() =
        runTest(mainDispatcher) {
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { false }
            val vm = createViewModel()

            vm.mnemonicFieldState.setTextAndNotify("  one  two\tthree\nfour  ")
            advanceTimeBy(600)
            advanceUntilIdle()

            vm.importSeedphrase()
            advanceUntilIdle()

            verify { keyImportRepository.setMnemonic("one two three four") }
        }

    @Test
    fun `importSeedphrase success clears error state`() =
        runTest(mainDispatcher) {
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { false }
            val vm = createViewModel()

            vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
            advanceTimeBy(600)
            advanceUntilIdle()

            vm.importSeedphrase()
            advanceUntilIdle()

            assertNull(vm.state.value.errorMessage)
            assertFalse(vm.state.value.isImporting)
        }

    @Test
    fun `importSeedphrase success with 24 words navigates`() =
        runTest(mainDispatcher) {
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { false }
            val vm = createViewModel()

            vm.mnemonicFieldState.setTextAndNotify(TWENTY_FOUR_WORDS)
            advanceTimeBy(600)
            advanceUntilIdle()

            vm.importSeedphrase()
            advanceUntilIdle()

            assertFalse(vm.state.value.isImporting)
            verify { keyImportRepository.setMnemonic(TWENTY_FOUR_WORDS) }
            coVerify { navigator.route(Route.KeyImport.ChainsSetup) }
        }

    // endregion

    // region importSeedphrase — duplicate path

    @Test
    fun `importSeedphrase shows error on duplicate`() = runTest(mainDispatcher) {
        checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { true }
        val vm = createViewModel()

        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()

        vm.importSeedphrase()
        advanceUntilIdle()

        assertFalse(vm.state.value.isImporting)
        assertEquals(
            UiText.StringResource(R.string.import_seedphrase_already_imported),
            vm.state.value.errorMessage,
        )
        assertEquals(VsTextInputFieldInnerState.Error, vm.state.value.innerState)
    }

    @Test
    fun `importSeedphrase duplicate does not navigate`() = runTest(mainDispatcher) {
        checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { true }
        val vm = createViewModel()

        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()

        vm.importSeedphrase()
        advanceUntilIdle()

        coVerify(exactly = 0) { navigator.route(any()) }
    }

    @Test
    fun `importSeedphrase duplicate does not call setMnemonic`() = runTest(mainDispatcher) {
        checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { true }
        val vm = createViewModel()

        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()

        vm.importSeedphrase()
        advanceUntilIdle()

        verify(exactly = 0) { keyImportRepository.setMnemonic(any()) }
    }

    // endregion

    // region importSeedphrase — exception path

    @Test
    fun `importSeedphrase shows error on exception`() = runTest(mainDispatcher) {
        checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase {
            throw RuntimeException("Network error")
        }
        val vm = createViewModel()

        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()

        vm.importSeedphrase()
        advanceUntilIdle()

        assertFalse(vm.state.value.isImporting)
        assertEquals(
            UiText.DynamicString("Network error"),
            vm.state.value.errorMessage,
        )
        assertEquals(VsTextInputFieldInnerState.Error, vm.state.value.innerState)
    }

    @Test
    fun `importSeedphrase exception does not navigate`() = runTest(mainDispatcher) {
        checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase {
            throw RuntimeException("fail")
        }
        val vm = createViewModel()

        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()

        vm.importSeedphrase()
        advanceUntilIdle()

        coVerify(exactly = 0) { navigator.route(any()) }
    }

    @Test
    fun `importSeedphrase exception does not call setMnemonic`() = runTest(mainDispatcher) {
        checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase {
            throw RuntimeException("fail")
        }
        val vm = createViewModel()

        vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
        advanceTimeBy(600)
        advanceUntilIdle()

        vm.importSeedphrase()
        advanceUntilIdle()

        verify(exactly = 0) { keyImportRepository.setMnemonic(any()) }
    }

    @Test
    fun `importSeedphrase shows unknown error when exception message is null`() =
        runTest(mainDispatcher) {
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase {
                throw RuntimeException()
            }
            val vm = createViewModel()

            vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
            advanceTimeBy(600)
            advanceUntilIdle()

            vm.importSeedphrase()
            advanceUntilIdle()

            assertFalse(vm.state.value.isImporting)
            assertEquals(
                UiText.StringResource(R.string.error_view_default_description),
                vm.state.value.errorMessage,
            )
            assertEquals(VsTextInputFieldInnerState.Error, vm.state.value.innerState)
        }

    // endregion

    // region importSeedphrase — mnemonic cleaning

    @Test
    fun `importSeedphrase passes cleaned mnemonic to duplicate check`() =
        runTest(mainDispatcher) {
            var capturedMnemonic = ""
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { mnemonic ->
                capturedMnemonic = mnemonic
                false
            }
            val vm = createViewModel()

            vm.mnemonicFieldState.setTextAndNotify("  one  two\nthree  ")
            advanceTimeBy(600)
            advanceUntilIdle()

            vm.importSeedphrase()
            advanceUntilIdle()

            assertEquals("one two three", capturedMnemonic)
        }

    @Test
    fun `importSeedphrase with tabs and carriage returns normalizes input`() =
        runTest(mainDispatcher) {
            var capturedMnemonic = ""
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { mnemonic ->
                capturedMnemonic = mnemonic
                false
            }
            val vm = createViewModel()

            vm.mnemonicFieldState.setTextAndNotify("one\ttwo\r\nthree\n\nfour")
            advanceTimeBy(600)
            advanceUntilIdle()

            vm.importSeedphrase()
            advanceUntilIdle()

            assertEquals("one two three four", capturedMnemonic)
        }

    // endregion

    // region importSeedphrase — error recovery

    @Test
    fun `typing after import error clears error and allows reimport`() =
        runTest(mainDispatcher) {
            var shouldFail = true
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase {
                if (shouldFail) throw RuntimeException("fail")
                false
            }
            val vm = createViewModel()

            // First import fails
            vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
            advanceTimeBy(600)
            advanceUntilIdle()

            vm.importSeedphrase()
            advanceUntilIdle()

            assertNotNull(vm.state.value.errorMessage)
            assertEquals(VsTextInputFieldInnerState.Error, vm.state.value.innerState)

            // Type again — error clears immediately
            shouldFail = false
            vm.mnemonicFieldState.setTextAndNotify("$TWELVE_WORDS extra")

            assertNull(vm.state.value.errorMessage)
            assertEquals(VsTextInputFieldInnerState.Default, vm.state.value.innerState)
            assertFalse(vm.state.value.isImporting)
        }

    @Test
    fun `can reimport successfully after duplicate error`() =
        runTest(mainDispatcher) {
            var isDuplicate = true
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase {
                isDuplicate
            }
            val vm = createViewModel()

            // First import — duplicate
            vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
            advanceTimeBy(600)
            advanceUntilIdle()

            vm.importSeedphrase()
            advanceUntilIdle()

            assertEquals(
                UiText.StringResource(R.string.import_seedphrase_already_imported),
                vm.state.value.errorMessage,
            )

            // Type new phrase, let debounce complete
            isDuplicate = false
            vm.mnemonicFieldState.setTextAndNotify("$TWELVE_WORDS extra")
            advanceTimeBy(600)
            advanceUntilIdle()

            // Reimport succeeds
            vm.importSeedphrase()
            advanceUntilIdle()

            assertFalse(vm.state.value.isImporting)
            assertNull(vm.state.value.errorMessage)
            coVerify { navigator.route(Route.KeyImport.ChainsSetup) }
        }

    @Test
    fun `can reimport successfully after exception error`() =
        runTest(mainDispatcher) {
            var shouldThrow = true
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase {
                if (shouldThrow) throw RuntimeException("Network error")
                false
            }
            val vm = createViewModel()

            // First import — exception
            vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)
            advanceTimeBy(600)
            advanceUntilIdle()

            vm.importSeedphrase()
            advanceUntilIdle()

            assertEquals(
                UiText.DynamicString("Network error"),
                vm.state.value.errorMessage,
            )

            // Type same phrase again, let debounce complete
            shouldThrow = false
            vm.mnemonicFieldState.setTextAndNotify("$TWELVE_WORDS ")
            advanceTimeBy(600)
            advanceUntilIdle()

            // Reimport succeeds
            vm.importSeedphrase()
            advanceUntilIdle()

            assertFalse(vm.state.value.isImporting)
            assertNull(vm.state.value.errorMessage)
            coVerify { navigator.route(Route.KeyImport.ChainsSetup) }
        }

    // endregion

    // region importSeedphrase — full flow

    @Test
    fun `full flow - type valid phrase, validate, import, navigate`() =
        runTest(mainDispatcher) {
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { false }
            val vm = createViewModel()

            // Initial state
            assertFalse(vm.state.value.isImportEnabled)

            // Type phrase
            vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)

            // Immediate observer resets state
            assertNull(vm.state.value.errorMessage)
            assertFalse(vm.state.value.isImportEnabled)
            assertEquals(VsTextInputFieldInnerState.Default, vm.state.value.innerState)

            // Debounce completes — validation runs
            advanceTimeBy(600)
            advanceUntilIdle()

            assertEquals(12, vm.state.value.wordCount)
            assertEquals(12, vm.state.value.expectedWordCount)
            assertTrue(vm.state.value.isImportEnabled)
            assertEquals(VsTextInputFieldInnerState.Success, vm.state.value.innerState)
            assertNull(vm.state.value.errorMessage)

            // Import
            vm.importSeedphrase()
            advanceUntilIdle()

            // Final state
            assertFalse(vm.state.value.isImporting)
            verify { keyImportRepository.setMnemonic(TWELVE_WORDS) }
            coVerify { navigator.route(Route.KeyImport.ChainsSetup) }
        }

    @Test
    fun `full flow - type invalid, correct, then import`() =
        runTest(mainDispatcher) {
            every { validateMnemonic(any()) } returns MnemonicValidationResult.InvalidPhrase
            checkMnemonicDuplicate = CheckMnemonicDuplicateUseCase { false }
            val vm = createViewModel()

            // Type invalid phrase
            vm.mnemonicFieldState.setTextAndNotify("invalid phrase here")
            advanceTimeBy(600)
            advanceUntilIdle()

            assertNotNull(vm.state.value.errorMessage)
            assertFalse(vm.state.value.isImportEnabled)
            assertEquals(VsTextInputFieldInnerState.Error, vm.state.value.innerState)

            // Now fix the phrase — switch to valid
            every { validateMnemonic(any()) } returns MnemonicValidationResult.Valid
            vm.mnemonicFieldState.setTextAndNotify(TWELVE_WORDS)

            // Immediate observer clears error
            assertNull(vm.state.value.errorMessage)
            assertEquals(VsTextInputFieldInnerState.Default, vm.state.value.innerState)

            // Wait for debounce
            advanceTimeBy(600)
            advanceUntilIdle()

            assertTrue(vm.state.value.isImportEnabled)
            assertEquals(VsTextInputFieldInnerState.Success, vm.state.value.innerState)

            // Import
            vm.importSeedphrase()
            advanceUntilIdle()

            assertFalse(vm.state.value.isImporting)
            coVerify { navigator.route(Route.KeyImport.ChainsSetup) }
        }

    // endregion

    // region back

    @Test
    fun `back navigates to Destination Back`() = runTest(mainDispatcher) {
        val vm = createViewModel()

        vm.back()
        advanceUntilIdle()

        coVerify { navigator.navigate(Destination.Back) }
    }

    // endregion

    // region Debounce cancellation

    @Test
    fun `rapid typing only triggers validation for final input`() =
        runTest(mainDispatcher) {
            val capturedInputs = mutableListOf<String>()
            every { validateMnemonic(any()) } answers {
                capturedInputs.add(firstArg())
                MnemonicValidationResult.InvalidPhrase
            }
            val vm = createViewModel()

            // Rapid typing - each within debounce window
            vm.mnemonicFieldState.setTextAndNotify("one")
            advanceTimeBy(200)
            vm.mnemonicFieldState.setTextAndNotify("one two")
            advanceTimeBy(200)
            vm.mnemonicFieldState.setTextAndNotify("one two three")
            advanceTimeBy(600) // let final debounce complete
            advanceUntilIdle()

            // Should only have validated the final input
            assertEquals(1, capturedInputs.size)
            assertEquals("one two three", capturedInputs.first())
        }

    // endregion

    companion object {
        private const val TWELVE_WORDS =
            "one two three four five six seven eight nine ten eleven twelve"
        private const val TWENTY_FOUR_WORDS =
            "one two three four five six seven eight nine ten eleven twelve " +
                "thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty " +
                "twenty-one twenty-two twenty-three twenty-four"
    }
}
