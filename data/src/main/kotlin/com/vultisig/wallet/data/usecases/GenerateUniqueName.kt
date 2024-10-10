package com.vultisig.wallet.data.usecases

import jakarta.inject.Inject

interface GenerateUniqueName : (String, List<String>) -> String

internal class GenerateUniqueNameImpl @Inject constructor() : GenerateUniqueName {
    override fun invoke(targetName: String, takenNames: List<String>): String {
        var newName = targetName
        var i = 1
        while (takenNames.contains(newName)) {
            newName = "$targetName #$i"
            i++
        }
        return newName
    }
}