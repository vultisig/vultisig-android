package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.models.Chain
import javax.inject.Inject

internal interface ExplorerLinkRepository {

    fun getTransactionLink(
        chain: Chain,
        transactionHash: String
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

    private val Chain.transactionExplorerUrl: String
        get() = when (this) {
            Chain.bitcoin -> "https://blockchair.com/bitcoin/transaction/"
            Chain.bitcoinCash -> "https://blockchair.com/bitcoin-cash/transaction/"
            Chain.litecoin -> "https://blockchair.com/litecoin/transaction/"
            Chain.dogecoin -> "https://blockchair.com/dogecoin/transaction/"
            Chain.dash -> "https://blockchair.com/dash/transaction/"
            Chain.thorChain -> "https://runescan.io/tx/"
            Chain.solana -> "https://explorer.solana.com/tx/"
            Chain.ethereum -> "https://etherscan.io/tx/"
            Chain.gaiaChain -> "https://www.mintscan.io/cosmos/tx/"
            Chain.kujira -> "https://finder.kujira.network/kaiyo-1/tx/"
            Chain.avalanche -> "https://snowtrace.io/tx/"
            Chain.bscChain -> "https://bscscan.com/tx/"
            Chain.mayaChain -> "https://www.mayascan.org/tx/"
            Chain.arbitrum -> "https://arbiscan.io/tx/"
            Chain.base -> "https://basescan.org/tx/"
            Chain.optimism -> "https://optimistic.etherscan.io/tx/"
            Chain.polygon -> "https://polygonscan.com/tx/"
            Chain.blast -> "https://blastscan.io/tx/"
            Chain.cronosChain -> "https://cronoscan.com/tx/"

            // TODO: Add support for these later
            // Chain.sui -> "https://suiscan.xyz/mainnet/tx/"
            // Chain.dot -> "https://polkadot.subscan.io/extrinsic/"
        }

}