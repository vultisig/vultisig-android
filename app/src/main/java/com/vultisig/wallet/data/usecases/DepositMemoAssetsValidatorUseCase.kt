package com.vultisig.wallet.data.usecases

import javax.inject.Inject

interface DepositMemoAssetsValidatorUseCase : (String) -> Boolean

class DepositMemoAssetsValidatorUseCaseImpl @Inject constructor() :
    DepositMemoAssetsValidatorUseCase {

    private val chainNameRegex = Regex("^[a-zA-Z]{3,10}$")
    private val assetInfoRegex = Regex("^[a-zA-Z0-9]+$")
    private val assetWithAddressRegex = Regex("^[a-zA-Z0-9]+-[a-zA-Z0-9]+$")

    override fun invoke(assets: String): Boolean {
        if (assets.isBlank() || assets.split(".").size != 2) {
            return false
        }

        val (chainName, assetsInfo) = assets.split(".", limit = 2)

        return isValidChainName(chainName) && when {
            isValidAssetWithAddress(assetsInfo) -> true
            isValidAssetInfo(assetsInfo) -> true
            else -> false
        }
    }

    private fun isValidChainName(chainName: String) =
        chainName.matches(chainNameRegex)

    private fun isValidAssetInfo(assetsInfo: String) =
        assetsInfo.matches(assetInfoRegex)

    private fun isValidAssetWithAddress(assetsInfo: String) =
        assetsInfo.matches(assetWithAddressRegex)

}
