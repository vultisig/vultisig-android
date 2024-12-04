package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.crypto.TonHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.wallet.OneInchSwap
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
            val chain = payload.coin.chain
            messages += when (chain) {
                Chain.ThorChain -> {
                    val thorHelper = ThorChainHelper.thor(vault.pubKeyECDSA, vault.hexChainCode)
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

                Chain.GaiaChain, Chain.Dydx, Chain.Kujira, Chain.Osmosis, Chain.Noble -> {
                    CosmosHelper(
                        coinType = chain.coinType,
                        denom = chain.feeUnit,
                        gasLimit = CosmosHelper.getChainGasLimit(chain),
                    ).getPreSignedImageHash(payload)
                }

                Chain.Terra, Chain.TerraClassic -> {
                    TerraHelper(
                        coinType = chain.coinType,
                        denom = chain.feeUnit,
                        gasLimit = CosmosHelper.getChainGasLimit(chain),
                    ).getPreSignedImageHash(payload)
                }

                Chain.MayaChain -> {
                    val mayaChainHelper = ThorChainHelper.maya(vault.pubKeyECDSA, vault.hexChainCode)
                    mayaChainHelper.getPreSignedImageHash(payload)
                }

                Chain.Polkadot -> {
                    val dotHelper = PolkadotHelper(vault.pubKeyEDDSA)
                    dotHelper.getPreSignedImageHash(payload)
                }

                Chain.Sui -> {
                    SuiHelper.getPreSignedImageHash(payload)
                }

                Chain.Ton -> {
                    TonHelper.getPreSignedImageHash(payload)
                }
            }
        }

        return messages.sorted()
    }

}