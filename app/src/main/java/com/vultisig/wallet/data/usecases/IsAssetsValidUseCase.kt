package com.vultisig.wallet.data.usecases

import java.lang.IndexOutOfBoundsException
import javax.inject.Inject

interface IsAssetsValidUseCase : (String) -> Boolean

class IsAssetsValidUseCaseImpl @Inject constructor() : IsAssetsValidUseCase {
    override fun invoke(assets: String): Boolean = assets.isNotBlank() && try {
        val assetsParts = assets.split(".")
        val chainName = assetsParts[0]
        val assetsInfo = assetsParts[1]
        val isChainNameValid = chainName.length in 3..10 && chainName.all { it.isLetter() }
        isChainNameValid && if (assetsInfo.contains("-")) {
            val assetName = assetsInfo.split("-")[0]
            val assetAddress = assetsInfo.split("-")[1]
            val isAssetNameValid = assetName.all { it.isLetter() }
            val isAssetAddressValid = assetAddress.all { it.isLetterOrDigit() }
            isAssetNameValid && isAssetAddressValid
        } else {
            assetsInfo.isNotEmpty() && assetsInfo.all { it.isLetter() }
        }
    } catch (e: IndexOutOfBoundsException) {
        false
    }
}
