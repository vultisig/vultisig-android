package com.vultisig.wallet.data.usecases

import android.database.sqlite.SQLiteConstraintException
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import timber.log.Timber
import javax.inject.Inject

internal interface EnableTokenUseCase: suspend (String, Coin) -> String?

internal class EnableTokenUseCaseImpl @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : EnableTokenUseCase {
    override suspend fun invoke(vaultId: String, coin: Coin) : String? {
        val vault = vaultRepository.get(vaultId)
            ?: error("No vault with $vaultId")

        val (address, derivedPublicKey) = chainAccountAddressRepository.getAddress(
            coin,
            vault
        )
        val updatedCoin = coin.copy(
            address = address,
            hexPublicKey = derivedPublicKey
        )

        try {
            vaultRepository.addTokenToVault(vaultId, updatedCoin)

            return updatedCoin.id
        } catch (e: SQLiteConstraintException) {
            // Importing existing tokens (from search result)
            // into the coin table causes an exception, which we ignore.
            Timber.e(e, "Try to import the existing token.")
            return null
        }
    }
}