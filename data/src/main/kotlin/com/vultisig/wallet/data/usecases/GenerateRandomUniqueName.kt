package com.vultisig.wallet.data.usecases

import javax.inject.Inject

interface GenerateRandomUniqueName : (String, List<String>) -> String

internal class GenerateRandomUniqueNameImpl @Inject constructor() : GenerateRandomUniqueName {
    override fun invoke(targetName: String, takenNames: List<String>): String {
        var newName = targetName
        var i = (0..1000).random()
        while (takenNames.contains(newName)) {
            newName = "$targetName #$i"
            i = (0..1000).random()
        }
        return newName
    }
}