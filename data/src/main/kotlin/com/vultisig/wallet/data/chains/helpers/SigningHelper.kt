package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.wallet.OneInchSwap
import com.vultisig.wallet.data.wallet.Swaps.getPreSignedImageHash
import java.math.BigInteger

object SigningHelper {

    fun getKeysignMessages(
        payload: KeysignPayload,
        vault: Vault
    ): List<String> {
        val messages = mutableListOf<String>()

        var nonceAcc = BigInteger.ZERO

        val approvePayload = payload.approvePayload
        if (approvePayload != null) {
            messages += THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                .getPreSignedApproveImageHash(approvePayload, payload)
            nonceAcc++
        }

        val swapPayload = payload.swapPayload
        if (swapPayload != null && swapPayload !is SwapPayload.MayaChain) {
            when (swapPayload) {
                is SwapPayload.ThorChain -> {
                    messages += THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                        .getPreSignedImageHash(swapPayload.data, payload, nonceAcc)
                }

                is SwapPayload.OneInch -> {
                    messages += OneInchSwap(vault.pubKeyECDSA, vault.hexChainCode)
                        .getPreSignedImageHash(swapPayload.data, payload, nonceAcc)
                }
                // mayachain is implemented through send transaction
                else -> Unit
            }
        } else {
            messages += when (payload.coin.chain) {
                Chain.ThorChain -> {
                    val thorHelper = THORCHainHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    thorHelper.getPreSignedImageHash(payload)
                }

                Chain.Solana -> {
                    val solanaHelper = SolanaHelper(vault.pubKeyEDDSA)
                    solanaHelper.getPreSignedImageHash(payload)
                }

                Chain.Ethereum, Chain.Avalanche, Chain.Base, Chain.Blast, Chain.Arbitrum,
                Chain.Polygon, Chain.Optimism, Chain.BscChain, Chain.CronosChain, Chain.ZkSync -> {
                    if (payload.coin.isNativeToken) {
                        EvmHelper(
                            payload.coin.coinType,
                            vault.pubKeyECDSA,
                            vault.hexChainCode
                        ).getPreSignedImageHash(payload)
                    } else {
                        ERC20Helper(
                            payload.coin.coinType,
                            vault.pubKeyECDSA,
                            vault.hexChainCode
                        ).getPreSignedImageHash(payload)
                    }
                }

                Chain.Bitcoin, Chain.BitcoinCash, Chain.Litecoin, Chain.Dogecoin, Chain.Dash -> {
                    val utxo =
                        UtxoHelper(payload.coin.coinType, vault.pubKeyECDSA, vault.hexChainCode)
                    utxo.getPreSignedImageHash(payload)
                }

                Chain.GaiaChain -> {
                    val atomHelper = AtomHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    atomHelper.getPreSignedImageHash(payload)
                }

                Chain.Dydx -> {
                    val dydxHelper = DydxHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    dydxHelper.getPreSignedImageHash(payload)
                }

                Chain.Kujira -> {
                    val kujiraHelper = KujiraHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    kujiraHelper.getPreSignedImageHash(payload)
                }

                Chain.MayaChain -> {
                    val mayaChainHelper = MayaChainHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    mayaChainHelper.getPreSignedImageHash(payload)
                }

                Chain.Polkadot -> {
                    val dotHelper = PolkadotHelper(vault.pubKeyEDDSA)
                    dotHelper.getPreSignedImageHash(payload)
                }
            }
        }

        return messages.sorted()
    }

}