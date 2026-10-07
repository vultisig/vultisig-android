package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.tonUserFriendlyAddress
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.usecases.TonJettonTransferRefusedException.Reason
import javax.inject.Inject

/** Why [VerifyTonJettonTransferUseCase] refused a TON jetton transfer. */
class TonJettonTransferRefusedException(val reason: Reason, message: String) :
    Exception(message) {

    enum class Reason {
        /** The payload's sender (address or public key) is not this vault's TON account. */
        NotThisVault,

        /** The jetton wallet or the shown token details are not the vault's own for the jetton. */
        TokenMismatch,

        /** A lookup the check depends on came back empty, so the transfer can't be checked. */
        Unverifiable,
    }
}

/**
 * Checks that a TON jetton transfer moves the jetton it shows before a co-signer signs it.
 *
 * The signer sends the transfer from the payload's `tonSpecific.jettonAddress` and shows the
 * payload coin's ticker and decimals, and none of those are tied to each other: a payload can show
 * "1 FOO" (9 decimals) while naming the vault's USDT jetton wallet, which moves 1,000 USDT. So the
 * payload's sender must be this vault's TON account, the jetton wallet must be that account's
 * wallet for the coin's jetton master, and the coin's ticker and decimals must be the vault's own
 * for that master — or, for a jetton the vault does not list, the ones the master itself reports.
 *
 * Throws [TonJettonTransferRefusedException] on a refusal, and lets a failed lookup propagate: a
 * transfer that cannot be checked is not signed.
 */
class VerifyTonJettonTransferUseCase
@Inject
constructor(
    private val tonApi: TonApi,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) {

    suspend operator fun invoke(payload: KeysignPayload, vault: Vault) {
        val coin = payload.coin
        // A TonConnect request is signed from its own BOC, decoded for display separately.
        if (coin.chain != Chain.Ton || coin.isNativeToken || payload.signTon != null) return

        val tonSpecific =
            payload.blockChainSpecific as? BlockChainSpecific.Ton
                ?: refuse(Reason.Unverifiable, "TON jetton transfer without TON chain data")

        // The payload's owner is the jetton wallet lookup's input, the transfer's response
        // address and the shown "From", so it has to be the vault's own before anything else.
        val (vaultAddress, vaultPublicKey) =
            chainAccountAddressRepository.getAddress(Chain.Ton, vault)
        if (
            !sameTonAddress(coin.address, vaultAddress) ||
                !coin.hexPublicKey.equals(vaultPublicKey, ignoreCase = true)
        ) {
            refuse(Reason.NotThisVault, "The transfer's sender is not this vault's TON account")
        }

        val vaultJettonWallet =
            tonApi
                .getJettonWallet(vaultAddress, coin.contractAddress)
                .getJettonsAddress(coin.contractAddress, ::tonUserFriendlyAddress)
                ?: refuse(
                    Reason.Unverifiable,
                    "No ${coin.ticker} jetton wallet found for this vault",
                )
        if (!sameTonAddress(vaultJettonWallet, tonSpecific.jettonAddress)) {
            refuse(
                Reason.TokenMismatch,
                "The transfer's jetton wallet is not this vault's ${coin.ticker} wallet",
            )
        }

        val vaultCoin =
            vault.coins.firstOrNull {
                it.chain == Chain.Ton &&
                    !it.isNativeToken &&
                    sameTonAddress(it.contractAddress, coin.contractAddress)
            }
        val (expectedTicker, expectedDecimals) =
            if (vaultCoin != null) {
                vaultCoin.ticker to vaultCoin.decimal
            } else {
                val metadata =
                    tonApi.getJettonMetadata(coin.contractAddress)
                        ?: refuse(Reason.Unverifiable, "The jetton's metadata couldn't be read")
                metadata.ticker to metadata.decimals
            }
        if (expectedDecimals != coin.decimal || !expectedTicker.equals(coin.ticker, true)) {
            refuse(Reason.TokenMismatch, "The transfer's token details don't match its jetton")
        }
    }

    private fun refuse(reason: Reason, message: String): Nothing =
        throw TonJettonTransferRefusedException(reason, message)

    private fun sameTonAddress(a: String?, b: String?): Boolean {
        val left = a?.let(::tonUserFriendlyAddress) ?: return false
        val right = b?.let(::tonUserFriendlyAddress) ?: return false
        return left == right
    }
}
