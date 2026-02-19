@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

internal data class ImportSeedphraseUiModel(
    val wordCount: Int = 0,
    val expectedWordCount: Int = 12,
    val errorMessage: UiText? = null,
    val isImportEnabled: Boolean = false,
    val isImporting: Boolean = false,
    val innerState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
)

@HiltViewModel
internal class ImportSeedphraseViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val validateMnemonic: ValidateMnemonicUseCase,
    private val keyImportRepository: KeyImportRepository,
    private val checkMnemonicDuplicate: CheckMnemonicDuplicateUseCase,
) : ViewModel() {

    val mnemonicFieldState = TextFieldState()
    val state = MutableStateFlow(ImportSeedphraseUiModel())

    init {
        observeImmediateInput()
        observeMnemonicChanges()
    }

    private fun observeImmediateInput() = viewModelScope.launch {
        mnemonicFieldState.textAsFlow().collect {
            state.update {
                it.copy(
                    errorMessage = null,
                    isImportEnabled = false,
                    innerState = VsTextInputFieldInnerState.Default,
                )
            }
        }
    }

    private fun observeMnemonicChanges() = viewModelScope.launch {
        mnemonicFieldState.textAsFlow()
            .debounce(500)
            .collectLatest { text ->
                val trimmed = cleanMnemonic(text.toString())
                if (trimmed.isEmpty()) {
                    state.update {
                        it.copy(
                            wordCount = 0,
                            expectedWordCount = 12,
                            errorMessage = null,
                            isImportEnabled = false,
                            innerState = VsTextInputFieldInnerState.Default,
                        )
                    }
                    return@collectLatest
                }

                val words = trimmed.split(Regex("\\s+"))
                val wordCount = words.size
                val expectedWordCount = if (wordCount > 12) 24 else 12

                val result = validateMnemonic(trimmed)

                val errorMessage = when (result) {
                    is MnemonicValidationResult.Valid -> null
                    is MnemonicValidationResult.InvalidWordCount ->
                        UiText.FormattedText(
                            R.string.import_seedphrase_invalid_word_count,
                            listOf(result.actual.toString(), expectedWordCount.toString())
                        )
                    is MnemonicValidationResult.InvalidPhrase ->
                        UiText.StringResource(R.string.import_seedphrase_invalid_phrase)
                }

                val innerState = when (result) {
                    is MnemonicValidationResult.Valid ->
                        VsTextInputFieldInnerState.Success
                    is MnemonicValidationResult.InvalidWordCount,
                    is MnemonicValidationResult.InvalidPhrase ->
                        VsTextInputFieldInnerState.Error
                }

                state.update {
                    it.copy(
                        wordCount = wordCount,
                        expectedWordCount = expectedWordCount,
                        errorMessage = errorMessage,
                        isImportEnabled = result is MnemonicValidationResult.Valid,
                        innerState = innerState,
                    )
                }
            }
    }

    fun importSeedphrase() {
        if (state.value.isImporting) return

        val mnemonic = cleanMnemonic(mnemonicFieldState.text.toString())

        viewModelScope.launch {
            state.update { it.copy(isImporting = true) }

            try {
                val isDuplicate = checkMnemonicDuplicate(mnemonic)

                if (isDuplicate) {
                    state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = UiText.StringResource(R.string.import_seedphrase_already_imported),
                            innerState = VsTextInputFieldInnerState.Error,
                        )
                    }
                    return@launch
                }

                keyImportRepository.setMnemonic(mnemonic)

                state.update { it.copy(isImporting = false) }

                navigator.route(Route.KeyImport.ChainsSetup)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = UiText.DynamicString(
                            e.message ?: ""
                        ),
                        innerState = VsTextInputFieldInnerState.Error,
                    )
                }
            }
        }
    }

    private fun cleanMnemonic(raw: String): String =
        raw.trim().replace(Regex("[\\s\\n\\r\\t]+"), " ")

    fun back() {
        keyImportRepository.clear()
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}
