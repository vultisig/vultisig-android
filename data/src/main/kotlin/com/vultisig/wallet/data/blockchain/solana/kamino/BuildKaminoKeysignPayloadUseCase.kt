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
 * Where a Kamino [action] moves money to, for the verify screen's destination row.
 *
 * A deposit pays the vault. A withdraw pays [signerAddress] — the vault is the source, and naming
 * it as the destination tells the approving user the opposite of what the transaction does.
 *
 * Shared by the payload and by the [com.vultisig.wallet.data.models.DepositTransaction] the verify
 * screen actually reads, because those are two places that must not drift: the screen shows the
 * stored copy, so a branch applied to only one of them fixes nothing the user can see.
 */
fun kaminoDestinationAddress(
    vault: KaminoVault,
    action: KaminoAction,
    signerAddress: String,
): String =
    when (action) {
        KaminoAction.DEPOSIT -> vault.address
        KaminoAction.WITHDRAW -> signerAddress
    }

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
     * @param apiAmount the decimal amount in the denomination the action's endpoint expects — the
     *   underlying **token** for a deposit, vault **shares** for a withdraw. Passed pre-sized by
     *   the caller rather than derived here, because sizing a withdraw safely needs the held share
     *   balance: the endpoint rewrites an over-request to `u64::MAX`, meaning withdraw everything.
     * @param tokenAmount what the user is moving, in the underlying token — display only, for the
     *   verify screen. Never used to size the request.
     * @param coin the wallet coin for the vault's underlying token — supplies the signer address,
     *   public key and scale
     * @throws KaminoTransactionRejected if the assembled transaction is not one the app will sign
     */
    suspend operator fun invoke(
        vault: KaminoVault,
        action: KaminoAction,
        apiAmount: String,
        tokenAmount: BigDecimal,
        coin: Coin,
        blockChainSpecific: BlockChainSpecific,
        vaultPublicKeyECDSA: String,
        vaultLocalPartyID: String,
        libType: SigningLibType?,
    ): KeysignPayload {
        require(tokenAmount.signum() > 0) { "Amount must be greater than zero" }
        require(apiAmount.isNotBlank()) { "Kamino amount must not be blank" }

        val solanaSpecific =
            blockChainSpecific as? BlockChainSpecific.Solana
                ?: error("BuildKaminoKeysignPayloadUseCase: expected Solana blockChainSpecific")

        val rawTransaction =
            preparer.prepare(
                vault = vault,
                action = action,
                walletAddress = coin.address,
                amount = apiAmount,
                networkUnitPrice = solanaSpecific.priorityFee,
            )

        // The compute budget the app just injected, recorded where the payload carries it.
        //
        // The generic Solana values these arrive as describe a plain transfer — a 100,000-unit
        // limit and the raw network sample — and neither is what went into these bytes. iOS
        // compares the two for equality before it will co-sign (`KaminoVerifyPresentation
        // .priorityFeeAgrees`) and refuses on any difference, with "the network fee inside this
        // transaction is not the one shown above". The same mismatch also makes the fee on the
        // verify screen wrong: it would be priced off 100,000 units rather than the 320,000–400,000
        // these actually reserve.
        //
        // `unitPriceFor` is idempotent, so passing the sample to both this and `prepare` above
        // yields the same price by construction rather than by coincidence.
        val kaminoSpecific =
            solanaSpecific.copy(
                priorityFee = KaminoComputeBudget.unitPriceFor(solanaSpecific.priorityFee),
                priorityLimit = KaminoComputeBudget.unitLimitFor(vault, action),
                // Cleared, not carried. iOS does not relay an ATA rent figure — it *infers* one,
                // adding 2,039,280 lamports whenever the from-address token account is named and
                // the to-address one is not, which is exactly the shape a token deposit produces
                // here. Kamino's own transaction creates whatever accounts it needs inside the
                // bytes, so that surcharge describes a transfer this is not. iOS's own Kamino
                // factory sets both to nil for the same reason.
                fromAddressPubKey = null,
                toAddressPubKey = null,
            )

        return KeysignPayload(
            coin = coin,
            // Display destination on the verify screen. Routing is driven by the relayed bytes, not
            // by this field — but it is what the approving user is told the money is going to, and
            // the two directions do not share an answer: a deposit pays the vault, a withdraw pays
            // the signer's own account and the vault is the *source*. iOS branches the same way.
            toAddress = kaminoDestinationAddress(vault, action, coin.address),
            toAmount =
                tokenAmount
                    .movePointRight(coin.decimal)
                    // Sub-unit precision from manual entry rounds down rather than throwing,
                    // matching
                    // how the staking flows handle over-precise input.
                    .setScale(0, RoundingMode.DOWN)
                    .toBigInteger(),
            blockChainSpecific = kaminoSpecific,
            memo = null,
            vaultPublicKeyECDSA = vaultPublicKeyECDSA,
            vaultLocalPartyID = vaultLocalPartyID,
            libType = libType,
            wasmExecuteContractPayload = null,
            signSolana = SignSolana(rawTransactions = listOf(rawTransaction)),
        )
    }
}
