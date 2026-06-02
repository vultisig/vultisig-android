package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import javax.inject.Inject

/**
 * Bridges a [CosmosStakingPayload] (delegate / undelegate / redelegate / withdrawRewards intent)
 * into a fully-built [KeysignPayload] with `signDirect` populated. The existing
 * [com.vultisig.wallet.data.chains.helpers.CosmosHelper.getPreSignedInputData] consumes
 * `KeysignPayload.signDirect` natively, so the MPC pipeline downstream of this use case is
 * unchanged.
 *
 * Mirrors the iOS `SendCryptoVerifyLogic` splice at the point where `tx.cosmosStakingPayload !=
 * nil` swaps the `KeysignPayload.signData` to `.signDirect(...)`.
 *
 * The use case is pure logic — it owns no state and no I/O. View-models call it at the moment they
 * have:
 * - the immutable [Coin] (provides chain, delegator address, hex public key, decimals)
 * - the [CosmosStakingPayload] (already populated with validator + amount in base units)
 * - the Cosmos [BlockChainSpecific] (account number + sequence freshly fetched by the
 *   `BlockChainSpecificRepository`)
 * - the vault's MPC identity (pubKey ECDSA, localPartyID, signing lib type)
 *
 * The use case delegates the SignDoc-bytes work to [CosmosStakingSignDataResolver] and assembles
 * the final [KeysignPayload]. Validator-address preflight + pubkey-shape validation are inherited
 * from the resolver.
 */
fun interface BuildCosmosStakingKeysignPayloadUseCase {
    operator fun invoke(
        coin: Coin,
        payload: CosmosStakingPayload,
        blockChainSpecific: BlockChainSpecific,
        vaultPublicKeyECDSA: String,
        vaultLocalPartyID: String,
        libType: SigningLibType?,
    ): KeysignPayload
}

internal class BuildCosmosStakingKeysignPayloadUseCaseImpl @Inject constructor() :
    BuildCosmosStakingKeysignPayloadUseCase {

    override fun invoke(
        coin: Coin,
        payload: CosmosStakingPayload,
        blockChainSpecific: BlockChainSpecific,
        vaultPublicKeyECDSA: String,
        vaultLocalPartyID: String,
        libType: SigningLibType?,
    ): KeysignPayload {
        val signDirect =
            CosmosStakingSignDataResolver.resolve(
                payload = payload,
                chain = coin.chain,
                delegatorAddress = coin.address,
                hexPublicKey = coin.hexPublicKey,
                chainSpecific = blockChainSpecific,
            )

        // `toAddress` doubles as the verify-screen "destination". For delegate / undelegate /
        // redelegate the operator (`terravaloper1…`) is what we're sending stake to; for
        // withdrawRewards there's no single destination — use the first validator as a best-effort
        // display value. The actual transaction routing is driven by the SignDirect bytes, not
        // `toAddress`.
        val toAddress =
            when (payload) {
                is CosmosStakingPayload.Delegate -> payload.validatorAddress
                is CosmosStakingPayload.Undelegate -> payload.validatorAddress
                is CosmosStakingPayload.Redelegate -> payload.validatorDstAddress
                is CosmosStakingPayload.WithdrawRewards ->
                    payload.validators.firstOrNull().orEmpty()
            }

        val toAmount =
            when (payload) {
                is CosmosStakingPayload.Delegate -> BigInteger(payload.amount)
                is CosmosStakingPayload.Undelegate -> BigInteger(payload.amount)
                is CosmosStakingPayload.Redelegate -> BigInteger(payload.amount)
                is CosmosStakingPayload.WithdrawRewards -> BigInteger.ZERO
            }

        return KeysignPayload(
            coin = coin,
            toAddress = toAddress,
            toAmount = toAmount,
            blockChainSpecific = blockChainSpecific,
            memo = null,
            vaultPublicKeyECDSA = vaultPublicKeyECDSA,
            vaultLocalPartyID = vaultLocalPartyID,
            libType = libType,
            wasmExecuteContractPayload = null,
            signDirect = signDirect,
        )
    }
}
