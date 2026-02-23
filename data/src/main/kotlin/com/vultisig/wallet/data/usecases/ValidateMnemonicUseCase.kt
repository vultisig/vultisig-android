package com.vultisig.wallet.data.usecases

import wallet.core.jni.Mnemonic
import javax.inject.Inject

sealed class MnemonicValidationResult {
    data object Valid : MnemonicValidationResult()
    data class InvalidWordCount(val actual: Int) : MnemonicValidationResult()
    data object InvalidPhrase : MnemonicValidationResult()
}

fun interface ValidateMnemonicUseCase {
    operator fun invoke(mnemonic: String): MnemonicValidationResult
}

internal class ValidateMnemonicUseCaseImpl @Inject constructor() : ValidateMnemonicUseCase {

    override fun invoke(mnemonic: String): MnemonicValidationResult {
        val normalized = mnemonic.trim().replace(WHITESPACE_REGEX, " ")
        val wordCount = normalized.split(" ").size

        if (wordCount != 12 && wordCount != 24) {
            return MnemonicValidationResult.InvalidWordCount(wordCount)
        }

        return if (Mnemonic.isValid(normalized)) {
            MnemonicValidationResult.Valid
        } else {
            MnemonicValidationResult.InvalidPhrase
        }
    }

    private companion object {
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
