package com.vultisig.wallet.presenter.keysign

import com.vultisig.wallet.chains.AtomHelper
import com.vultisig.wallet.chains.DydxHelper
import com.vultisig.wallet.chains.ERC20Helper
import com.vultisig.wallet.chains.EvmHelper
import com.vultisig.wallet.chains.KujiraHelper
import com.vultisig.wallet.chains.MayaChainHelper
import com.vultisig.wallet.chains.PolkadotHelper
import com.vultisig.wallet.chains.SolanaHelper
import com.vultisig.wallet.chains.THORCHainHelper
import com.vultisig.wallet.chains.THORChainSwaps
import com.vultisig.wallet.chains.UtxoHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.wallet.OneInchSwap
import java.math.BigInteger


internal data class KeysignPayload(
    val coin: Coin,
    val toAddress: String,
    val toAmount: BigInteger,
    val blockChainSpecific: BlockChainSpecific,
    val utxos: List<UtxoInfo> = emptyList(),
    val memo: String? = null,
    val swapPayload: SwapPayload? = null,
    val approvePayload: ERC20ApprovePayload? = null,
    val vaultPublicKeyECDSA: String,
    val vaultLocalPartyID: String,
) {
    fun getKeysignMessages(vault: Vault): List<String> {
        val messages = mutableListOf<String>()

        var nonceAcc = BigInteger.ZERO

        if (approvePayload != null) {
            messages += THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                .getPreSignedApproveImageHash(approvePayload, this)
            nonceAcc++
        }

        if (swapPayload != null && swapPayload !is SwapPayload.MayaChain) {
            when (swapPayload) {
                is SwapPayload.ThorChain -> {
                    messages += THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                        .getPreSignedImageHash(swapPayload.data, this, nonceAcc)
                }

                is SwapPayload.OneInch -> {
                    messages += OneInchSwap(vault.pubKeyECDSA, vault.hexChainCode)
                        .getPreSignedImageHash(swapPayload.data, this, nonceAcc)
                }
                // mayachain is implemented through send transaction
                else -> Unit
            }
        } else {
            messages += when (coin.chain) {
                Chain.ThorChain -> {
                    val thorHelper = THORCHainHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    thorHelper.getPreSignedImageHash(this)
                }

                Chain.Solana -> {
                    val solanaHelper = SolanaHelper(vault.pubKeyEDDSA)
                    solanaHelper.getPreSignedImageHash(this)
                }

                Chain.Ethereum, Chain.Avalanche, Chain.Base, Chain.Blast, Chain.Arbitrum,
                Chain.Polygon, Chain.Optimism, Chain.BscChain, Chain.CronosChain, Chain.ZkSync -> {
                    if (coin.isNativeToken) {
                        EvmHelper(
                            coin.coinType,
                            vault.pubKeyECDSA,
                            vault.hexChainCode
                        ).getPreSignedImageHash(this)
                    } else {
                        ERC20Helper(
                            coin.coinType,
                            vault.pubKeyECDSA,
                            vault.hexChainCode
                        ).getPreSignedImageHash(this)
                    }
                }

                Chain.Bitcoin, Chain.BitcoinCash, Chain.Litecoin, Chain.Dogecoin, Chain.Dash -> {
                    val utxo =
                        UtxoHelper(this.coin.coinType, vault.pubKeyECDSA, vault.hexChainCode)
                    utxo.getPreSignedImageHash(this)
                }

                Chain.GaiaChain -> {
                    val atomHelper = AtomHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    atomHelper.getPreSignedImageHash(this)
                }

                Chain.Dydx -> {
                    val dydxHelper = DydxHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    dydxHelper.getPreSignedImageHash(this)
                }

                Chain.Kujira -> {
                    val kujiraHelper = KujiraHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    kujiraHelper.getPreSignedImageHash(this)
                }

                Chain.MayaChain -> {
                    val mayaChainHelper = MayaChainHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    mayaChainHelper.getPreSignedImageHash(this)
                }

                Chain.Polkadot -> {
                    val dotHelper = PolkadotHelper(vault.pubKeyEDDSA)
                    dotHelper.getPreSignedImageHash(this)
                }
            }
        }

        return messages.sorted()
    }
}