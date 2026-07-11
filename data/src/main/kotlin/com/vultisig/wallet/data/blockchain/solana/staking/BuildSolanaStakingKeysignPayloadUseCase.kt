package com.vultisig.wallet.data.blockchain.solana.staking

import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import javax.inject.Inject

/**
 * Bridges a [SolanaStakingPayload] (delegate / deactivate / withdraw intent) into a fully-built
 * [KeysignPayload] with `signSolana` populated. The existing
 * [com.vultisig.wallet.data.chains.helpers.SolanaHelper] consumes `KeysignPayload.signSolana`
 * natively via its raw-transaction path, so the MPC pipeline downstream is unchanged and both
 * co-signing devices sign the byte-identical relayed transaction.
 *
 * Mirrors [BuildCosmosStakingKeysignPayloadUseCase] and the iOS `SolanaStakingSignDataResolver`
 * splice. View-models call it once they have:
 * - the immutable [Coin] (chain, sender address, hex public key, decimals)
 * - the [SolanaStakingPayload] (validator + amount, or stake account)
 * - the Solana [BlockChainSpecific] (recent blockhash + priority fees, freshly fetched)
 * - the vault's MPC identity (pubKey ECDSA, localPartyID, signing lib type)
 * - [balanceLamports] for the delegate funding guard
 */
fun interface BuildSolanaStakingKeysignPayloadUseCase {
    operator fun invoke(
        coin: Coin,
        payload: SolanaStakingPayload,
        blockChainSpecific: BlockChainSpecific,
        balanceLamports: BigInteger,
        vaultPublicKeyECDSA: String,
        vaultLocalPartyID: String,
        libType: SigningLibType?,
    ): KeysignPayload
}

internal class BuildSolanaStakingKeysignPayloadUseCaseImpl
@Inject
constructor(private val resolver: SolanaStakingSignDataResolver) :
    BuildSolanaStakingKeysignPayloadUseCase {

    override fun invoke(
        coin: Coin,
        payload: SolanaStakingPayload,
        blockChainSpecific: BlockChainSpecific,
        balanceLamports: BigInteger,
        vaultPublicKeyECDSA: String,
        vaultLocalPartyID: String,
        libType: SigningLibType?,
    ): KeysignPayload {
        val solanaSpecific =
            blockChainSpecific as? BlockChainSpecific.Solana
                ?: error(
                    "BuildSolanaStakingKeysignPayloadUseCase: expected Solana blockChainSpecific"
                )

        val helper = SolanaHelper(coin.hexPublicKey)
        val signSolana =
            when (payload.opType) {
                SolanaStakingOpType.Delegate ->
                    resolver.resolveDelegate(
                        helper = helper,
                        payload = payload,
                        senderAddress = coin.address,
                        coinHexPublicKey = coin.hexPublicKey,
                        recentBlockHash = solanaSpecific.recentBlockHash,
                        priorityFeePrice = solanaSpecific.priorityFee,
                        priorityFeeLimit = solanaSpecific.priorityLimit,
                        balanceLamports = balanceLamports,
                    )
                SolanaStakingOpType.Unstake ->
                    resolver.resolveDeactivate(
                        helper = helper,
                        payload = payload,
                        senderAddress = coin.address,
                        coinHexPublicKey = coin.hexPublicKey,
                        recentBlockHash = solanaSpecific.recentBlockHash,
                        priorityFeePrice = solanaSpecific.priorityFee,
                        priorityFeeLimit = solanaSpecific.priorityLimit,
                    )
                SolanaStakingOpType.Withdraw ->
                    resolver.resolveWithdraw(
                        helper = helper,
                        payload = payload,
                        senderAddress = coin.address,
                        coinHexPublicKey = coin.hexPublicKey,
                        recentBlockHash = solanaSpecific.recentBlockHash,
                        priorityFeePrice = solanaSpecific.priorityFee,
                        priorityFeeLimit = solanaSpecific.priorityLimit,
                    )
            }

        // `toAddress` doubles as the verify-screen destination. For delegate it's the validator
        // vote account; for deactivate/withdraw it's the stake account. Actual routing is driven by
        // the relayed SignSolana bytes, not `toAddress`.
        val toAddress =
            when (payload.opType) {
                SolanaStakingOpType.Delegate -> payload.votePubkey.orEmpty()
                SolanaStakingOpType.Unstake,
                SolanaStakingOpType.Withdraw -> payload.stakeAccount.orEmpty()
            }

        return KeysignPayload(
            coin = coin,
            toAddress = toAddress,
            toAmount = payload.lamports ?: BigInteger.ZERO,
            blockChainSpecific = blockChainSpecific,
            memo = null,
            vaultPublicKeyECDSA = vaultPublicKeyECDSA,
            vaultLocalPartyID = vaultLocalPartyID,
            libType = libType,
            wasmExecuteContractPayload = null,
            signSolana = signSolana,
        )
    }
}
