package com.vultisig.wallet.ui.models.send.submit

import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.VaultRepository
import java.math.BigInteger
import wallet.core.jni.proto.Bitcoin

internal class BitcoinPlanService(private val vaultRepository: VaultRepository) {

    suspend fun getPlan(
        vaultId: String,
        selectedToken: Coin,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        specific: BlockChainSpecificAndUtxo,
        memo: String?,
    ): Bitcoin.TransactionPlan {
        val vault = vaultRepository.get(vaultId) ?: error("Can't calculate plan fees")
        val keysignPayload =
            KeysignPayload(
                coin = selectedToken,
                toAddress = dstAddress,
                toAmount = tokenAmountInt,
                blockChainSpecific = specific.blockChainSpecific,
                memo = memo,
                vaultPublicKeyECDSA = vault.pubKeyECDSA,
                vaultLocalPartyID = vault.localPartyID,
                utxos = specific.utxos,
                libType = vault.libType,
                wasmExecuteContractPayload = null,
            )

        val utxo = UtxoHelper.getHelper(vault, keysignPayload.coin.coinType)
        return utxo.getBitcoinTransactionPlan(keysignPayload)
    }
}
