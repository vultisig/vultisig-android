package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import vultisig.keysign.v1.SignSolana

/**
 * Turns a Kamino deposit or withdraw intent into a [KeysignPayload] with `signSolana` populated.
 *
 * Mirrors
 * [com.vultisig.wallet.data.blockchain.solana.staking.BuildSolanaStakingKeysignPayloadUseCase]: the
 * initiating device builds the transaction bytes once and relays them, and both co-signers sign the
 * byte-identical message through `SolanaHelper`'s raw-transaction path. Nothing downstream needs to
 * know this is Kamino.
 *
 * Must be called immediately before keysign. Kamino bakes a recent blockhash into the transaction
 * and it expires in about a minute, which an MPC ceremony can outlast.
 */
class BuildKaminoKeysignPayloadUseCase
@Inject
constructor(private val preparer: KaminoTransactionPreparer) {

    /**
     * @param amount decimal token amount the user entered
     * @param coin the wallet coin for the vault's underlying token — supplies the signer address,
     *   public key and scale
     * @throws KaminoTransactionRejected if the assembled transaction is not one the app will sign
     */
    suspend operator fun invoke(
        vault: KaminoVault,
        action: KaminoAction,
        amount: BigDecimal,
        coin: Coin,
        blockChainSpecific: BlockChainSpecific,
        vaultPublicKeyECDSA: String,
        vaultLocalPartyID: String,
        libType: SigningLibType?,
    ): KeysignPayload {
        require(amount.signum() > 0) { "Amount must be greater than zero" }

        val solanaSpecific =
            blockChainSpecific as? BlockChainSpecific.Solana
                ?: error("BuildKaminoKeysignPayloadUseCase: expected Solana blockChainSpecific")

        val rawTransaction =
            preparer.prepare(
                vault = vault,
                action = action,
                walletAddress = coin.address,
                // Kamino's endpoints take decimals, not base units. `toPlainString` matters:
                // scientific notation would be rejected, and a small amount can reach it that way.
                amount = amount.stripTrailingZeros().toPlainString(),
                networkUnitPrice = solanaSpecific.priorityFee,
            )

        return KeysignPayload(
            coin = coin,
            // Display destination on the verify screen. Routing is driven by the relayed bytes, not
            // by this field.
            toAddress = vault.address,
            toAmount =
                amount
                    .movePointRight(coin.decimal)
                    // Sub-unit precision from manual entry rounds down rather than throwing,
                    // matching
                    // how the staking flows handle over-precise input.
                    .setScale(0, RoundingMode.DOWN)
                    .toBigInteger(),
            blockChainSpecific = blockChainSpecific,
            memo = null,
            vaultPublicKeyECDSA = vaultPublicKeyECDSA,
            vaultLocalPartyID = vaultLocalPartyID,
            libType = libType,
            wasmExecuteContractPayload = null,
            signSolana = SignSolana(rawTransactions = listOf(rawTransaction)),
        )
    }
}
