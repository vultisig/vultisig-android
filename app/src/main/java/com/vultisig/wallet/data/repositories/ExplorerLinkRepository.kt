package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.models.Chain
import javax.inject.Inject

internal interface ExplorerLinkRepository {

    fun getTransactionLink(
        chain: Chain,
        transactionHash: String,
    ): String

    fun getAddressLink(
        chain: Chain,
        address: String,
    ): String

}

internal class ExplorerLinkRepositoryImpl @Inject constructor() : ExplorerLinkRepository {

    override fun getTransactionLink(
        chain: Chain,
        transactionHash: String,
    ): String = if (chain == Chain.thorChain) {
        "${chain.transactionExplorerUrl}${transactionHash.removePrefix("0x")}"
    } else {
        "${chain.transactionExplorerUrl}$transactionHash"
    }

    override fun getAddressLink(chain: Chain, address: String): String =
        "${chain.blockExplorerUrl}$address"

    private val Chain.transactionExplorerUrl: String
        get() = when (this) {
            Chain.avalanche, Chain.arbitrum, Chain.base, Chain.blast, Chain.bscChain,
            Chain.cronosChain, Chain.dogecoin, Chain.ethereum, Chain.gaiaChain, Chain.mayaChain,
            Chain.optimism, Chain.polygon, Chain.solana, Chain.thorChain,
            ->
                "${explorerUrl}tx/"

            Chain.bitcoin, Chain.bitcoinCash, Chain.dash, Chain.litecoin ->
                "${explorerUrl}transaction/"

            Chain.kujira ->
                "https://finder.kujira.network/kaiyo-1/tx/"

            // TODO: Add support for these later
            // Chain.sui -> "https://suiscan.xyz/mainnet/tx/"
            Chain.polkadot -> "https://polkadot.subscan.io/extrinsic/"
        }

    private val Chain.blockExplorerUrl: String
        get() = "${explorerUrl}address/"

    private val Chain.explorerUrl: String
        get() = when (this) {
            Chain.arbitrum -> "https://arbiscan.io/"
            Chain.avalanche -> "https://snowtrace.io/"
            Chain.base -> "https://basescan.org/"
            Chain.bitcoin -> "https://blockchair.com/bitcoin/"
            Chain.bitcoinCash -> "https://blockchair.com/bitcoin-cash/"
            Chain.blast -> "https://blastscan.io/"
            Chain.bscChain -> "https://bscscan.com/"
            Chain.cronosChain -> "https://cronoscan.com/"
            Chain.dash -> "https://blockchair.com/dash/"
            Chain.dogecoin -> "https://blockchair.com/dogecoin/"
            Chain.ethereum -> "https://etherscan.io/"
            Chain.gaiaChain -> "https://www.mintscan.io/cosmos/"
            Chain.kujira -> "https://kujira.mintscan.io/"
            Chain.litecoin -> "https://blockchair.com/litecoin/"
            Chain.mayaChain -> "https://www.mayascan.org/"
            Chain.optimism -> "https://optimistic.etherscan.io/"
            Chain.polygon -> "https://polygonscan.com/"
            Chain.solana -> "https://explorer.solana.com/"
            Chain.thorChain -> "https://runescan.io/"
            Chain.polkadot-> "https://polkadot.subscan.io/account/"
        }

}