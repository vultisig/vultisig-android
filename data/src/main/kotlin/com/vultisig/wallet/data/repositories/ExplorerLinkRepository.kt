package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.SwapPayload
import javax.inject.Inject

interface ExplorerLinkRepository {

    fun getTransactionLink(
        chain: Chain,
        transactionHash: String,
    ): String

    fun getAddressLink(
        chain: Chain,
        address: String,
    ): String

    fun getSwapProgressLink(
        tx: String,
        payload: SwapPayload?,
    ): String?
}

internal class ExplorerLinkRepositoryImpl @Inject constructor() : ExplorerLinkRepository {

    override fun getTransactionLink(
        chain: Chain,
        transactionHash: String,
    ): String = if (chain == Chain.ThorChain) {
        "${chain.transactionExplorerUrl}${transactionHash.removePrefix("0x")}"
    } else {
        "${chain.transactionExplorerUrl}$transactionHash"
    }

    override fun getAddressLink(chain: Chain, address: String): String =
        "${chain.blockExplorerUrl}$address"


    override fun getSwapProgressLink(
        tx: String,
        payload: SwapPayload?,
    ): String? = when (payload) {
        is SwapPayload.ThorChain -> "https://thorchain.net/tx/$tx"
        is SwapPayload.MayaChain -> "https://www.xscanner.org/tx/${tx.removePrefix("0x")}"
        else -> null
    }

    private val Chain.transactionExplorerUrl: String
        get() = when (this) {
            Chain.Avalanche, Chain.Arbitrum, Chain.Base, Chain.Blast, Chain.BscChain,
            Chain.CronosChain, Chain.Dogecoin, Chain.Ethereum, Chain.GaiaChain, Chain.MayaChain,
            Chain.Optimism, Chain.Polygon, Chain.Solana, Chain.ThorChain, Chain.ZkSync, Chain.Sui,
            Chain.Dydx, Chain.Bitcoin, Chain.Osmosis, Chain.Terra, Chain.TerraClassic, Chain.Noble ->
                "${explorerUrl}tx/"

            Chain.BitcoinCash, Chain.Dash, Chain.Litecoin, Chain.Ton ->
                "${explorerUrl}transaction/"

            Chain.Kujira ->
                "https://finder.kujira.network/kaiyo-1/tx/"

            Chain.Polkadot -> "https://polkadot.subscan.io/extrinsic/"
        }

    private val Chain.blockExplorerUrl: String
        get() = when (this) {
            Chain.Ton -> explorerUrl
            else -> "${explorerUrl}address/"
        }

    private val Chain.explorerUrl: String
        get() = when (this) {
            Chain.Arbitrum -> "https://arbiscan.io/"
            Chain.Avalanche -> "https://snowtrace.io/"
            Chain.Base -> "https://basescan.org/"
            Chain.Bitcoin -> "https://mempool.space/"
            Chain.BitcoinCash -> "https://blockchair.com/bitcoin-cash/"
            Chain.Blast -> "https://blastscan.io/"
            Chain.BscChain -> "https://bscscan.com/"
            Chain.CronosChain -> "https://cronoscan.com/"
            Chain.Dash -> "https://blockchair.com/dash/"
            Chain.Dogecoin -> "https://blockchair.com/dogecoin/"
            Chain.Ethereum -> "https://etherscan.io/"
            Chain.GaiaChain -> "https://www.mintscan.io/cosmos/"
            Chain.Dydx -> "https://www.mintscan.io/dydx/"
            Chain.Kujira -> "https://finder.kujira.network/"
            Chain.Litecoin -> "https://blockchair.com/litecoin/"
            Chain.MayaChain -> "https://www.mayascan.org/"
            Chain.Optimism -> "https://optimistic.etherscan.io/"
            Chain.Polygon -> "https://polygonscan.com/"
            Chain.Solana -> "https://explorer.solana.com/"
            Chain.ThorChain -> "https://thorchain.net/"
            Chain.Polkadot -> "https://polkadot.subscan.io/account/"
            Chain.ZkSync -> "https://explorer.zksync.io/"
            Chain.Sui -> "https://suiscan.xyz/mainnet/"
            Chain.Ton -> "https://tonviewer.com/"
            Chain.Osmosis -> "https://www.mintscan.io/osmosis/"
            Chain.Terra -> "https://www.mintscan.io/terra/"
            Chain.TerraClassic -> "https://finder.terra.money/classic/"
            Chain.Noble -> "https://www.mintscan.io/noble"
        }

}
