package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import javax.inject.Inject

/** The Bitcoin and QBTC accounts a QBTC claim is derived from. */
internal data class QbtcClaimCoins(val btc: Coin, val qbtc: Coin)

/**
 * Resolves the Bitcoin and QBTC accounts a QBTC claim needs. Neither chain has to be enabled in the
 * vault: an already-enabled coin is used as-is, otherwise the account is derived in-memory from the
 * native-token template and the vault's own keys. This keeps both the initiator and the co-signer
 * able to recompute the claim hash regardless of which chains the user has added.
 *
 * @throws MissingQbtcClaimAccountException when an account can't be derived (the vault lacks the
 *   key material for that chain).
 */
internal class ResolveQbtcClaimCoinsUseCase
@Inject
constructor(
    private val tokenRepository: TokenRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) {
    suspend operator fun invoke(vault: Vault): QbtcClaimCoins =
        QbtcClaimCoins(btc = resolve(vault, Chain.Bitcoin), qbtc = resolve(vault, Chain.Qbtc))

    private suspend fun resolve(vault: Vault, chain: Chain): Coin {
        vault.coins
            .firstOrNull { it.chain == chain }
            ?.let {
                return it
            }
        return runCatching {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                val (address, derivedPublicKey) =
                    chainAccountAddressRepository.getAddress(nativeToken, vault)
                nativeToken.copy(address = address, hexPublicKey = derivedPublicKey)
            }
            .getOrElse { throw MissingQbtcClaimAccountException(chain) }
    }
}
