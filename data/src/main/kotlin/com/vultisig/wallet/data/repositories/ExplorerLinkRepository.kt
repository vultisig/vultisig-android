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
        is SwapPayload.MayaChain -> "https://www.explorer.mayachain.info/tx/${tx.removePrefix("0x")}"
        is SwapPayload.EVM -> {
            if (payload.data.quote.tx.swapFee.toBigIntegerOrNull() != null) {
                if (payload.data.fromCoin.chain == payload.data.toCoin.chain && payload.data.fromCoin.chain == Chain.Solana) {
                    "https://orb.helius.dev/tx/${tx}"
                } else
                    "https://scan.li.fi/tx/${tx}"
            } else {
                null
            }
        }

        else -> null
    }

    private val Chain.transactionExplorerUrl: String
        get() = when (this) {
            Chain.BitcoinCash, Chain.Dash, Chain.Litecoin, Chain.Ton, Chain.Tron, Chain.Cardano->
                "${explorerUrl}transaction/"

            Chain.Kujira ->
                "https://finder.kujira.network/kaiyo-1/tx/"

            Chain.Polkadot -> "https://assethub-polkadot.subscan.io/extrinsic/"

            else -> "${explorerUrl}tx/"
        }

    private val Chain.blockExplorerUrl: String
        get() = when (this) {
            Chain.Ton -> explorerUrl
            Chain.Ripple -> "${explorerUrl}account/"
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
            Chain.Kujira -> "https://finder.kujira.network/kaiyo-1/"
            Chain.Litecoin -> "https://blockchair.com/litecoin/"
            Chain.MayaChain -> "https://www.explorer.mayachain.info/"
            Chain.Optimism -> "https://optimistic.etherscan.io/"
            Chain.Polygon -> "https://polygonscan.com/"
            Chain.Solana -> "https://orb.helius.dev/"
            Chain.ThorChain -> "https://thorchain.net/"
            Chain.Polkadot -> "https://assethub-polkadot.subscan.io/account/"
            Chain.ZkSync -> "https://explorer.zksync.io/"
            Chain.Sui -> "https://suiscan.xyz/mainnet/"
            Chain.Ton -> "https://tonviewer.com/"
            Chain.Osmosis -> "https://www.mintscan.io/osmosis/"
            Chain.Terra -> "https://www.mintscan.io/terra/"
            Chain.TerraClassic -> "https://finder.terra.money/classic/"
            Chain.Noble -> "https://www.mintscan.io/noble"
            Chain.Ripple -> "https://xrpscan.com/"
            Chain.Akash -> "https://www.mintscan.io/akash/"
            Chain.Tron -> "https://tronscan.org/#/"
            Chain.Zcash -> "https://blockexplorer.one/zcash/mainnet/"
            Chain.Cardano -> "https://cardanoscan.io/"
            Chain.Mantle -> "https://explorer.mantle.xyz/"
            Chain.Sei -> "https://seiscan.io/"
            Chain.Hyperliquid -> "https://liquidscan.io/"
            Chain.Rootstock -> "https://explorer.rootstock.io/"
        }

}
