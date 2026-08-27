package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.txstatus.SwapKitChainIdentifier
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.payload.SwapPayload
import javax.inject.Inject

interface ExplorerLinkRepository {

    fun getTransactionLink(chain: Chain, transactionHash: String): String

    fun getAddressLink(chain: Chain, address: String): String

    /**
     * The explorer's dedicated page for a token's contract, or null when [chain]'s explorer has no
     * such page (UTXO and Cosmos-SDK chains have no per-token concept). Callers fall back to the
     * holder's address page, which is what [getAddressLink] already gives them.
     */
    fun getTokenLink(chain: Chain, contractAddress: String): String?

    fun getSwapProgressLink(tx: String, payload: SwapPayload?): String?
}

internal class ExplorerLinkRepositoryImpl @Inject constructor() : ExplorerLinkRepository {

    override fun getTransactionLink(chain: Chain, transactionHash: String): String {
        chain.explorerUrl.ifEmpty {
            return ""
        }
        val hash =
            if (chain == Chain.ThorChain) transactionHash.removePrefix("0x") else transactionHash
        return "${chain.transactionExplorerUrl}$hash"
    }

    override fun getAddressLink(chain: Chain, address: String): String {
        chain.explorerUrl.ifEmpty {
            return ""
        }
        return "${chain.blockExplorerUrl}$address"
    }

    override fun getTokenLink(chain: Chain, contractAddress: String): String? {
        if (contractAddress.isEmpty()) return null
        return chain.tokenExplorerUrl?.let { "$it$contractAddress" }
    }

    override fun getSwapProgressLink(tx: String, payload: SwapPayload?): String? {
        // SwapKit-routed cross-chain swaps settle on a destination leg the source-chain explorer
        // can't show. Point "Track" at SwapKit's own tracker (both legs) when the source chain is
        // in SwapKit's route catalogue; otherwise fall through to the source-chain link below.
        if (payload != null && payload.isSwapKitRouted()) {
            val chainId = SwapKitChainIdentifier.chainId(payload.srcToken.chain)
            if (chainId != null) {
                return "https://track.swapkit.dev/?hash=$tx&chainId=$chainId"
            }
        }
        return when (payload) {
            is SwapPayload.ThorChain -> "https://runescan.io/tx/${tx.removePrefix("0x")}"
            is SwapPayload.MayaChain ->
                "https://www.explorer.mayachain.info/tx/${tx.removePrefix("0x")}"
            is SwapPayload.EVM -> {
                // The tracker exists independently of the affiliate `swapFee`; gating on it dropped
                // the Track button for feeless routes (e.g. same-chain Solana Bonk -> SOL). Derive
                // the link from src chain + tx hash, both always present for a broadcast swap.
                if (
                    payload.data.fromCoin.chain == payload.data.toCoin.chain &&
                        payload.data.fromCoin.chain == Chain.Solana
                ) {
                    "https://orb.helius.dev/tx/${tx}"
                } else "https://scan.li.fi/tx/${tx}"
            }

            else -> null
        }
    }

    // EVM/Solana SwapKit routes ride [SwapPayload.EVM] tagged `provider = "SwapKit"`; BTC/TON/ADA/
    // TRON/SUI/ZEC routes use [SwapPayload.SwapKit], which is SwapKit by construction.
    private fun SwapPayload.isSwapKitRouted(): Boolean =
        when (this) {
            is SwapPayload.SwapKit -> true
            is SwapPayload.EVM -> data.provider == SwapProvider.SWAPKIT.getSwapProviderId()
            else -> false
        }

    private val Chain.transactionExplorerUrl: String
        get() =
            when (this) {
                Chain.BitcoinCash,
                Chain.Dash,
                Chain.Dogecoin,
                Chain.Litecoin,
                Chain.Zcash,
                Chain.Ton,
                Chain.Tron,
                Chain.Cardano -> "${explorerUrl}transaction/"

                Chain.Polkadot -> "https://assethub-polkadot.subscan.io/extrinsic/"
                Chain.Bittensor -> "https://taostats.io/extrinsic/"

                else -> "${explorerUrl}tx/"
            }

    // Only the chains whose explorer actually renders a contract page. Everything else — the UTXO
    // family, the Cosmos-SDK chains, THORChain/Maya, Ripple, the Substrate chains — is absent
    // rather than mapped to a URL that 404s, so the caller can drop the row instead of offering a
    // dead link.
    private val Chain.tokenExplorerUrl: String?
        get() =
            when (this) {
                Chain.Solana -> "${explorerUrl}address/"
                Chain.Sui -> "${explorerUrl}coin/"
                // tonviewer addresses a jetton by its master contract at the root, same shape as
                // an account.
                Chain.Ton -> explorerUrl
                Chain.Tron -> "${explorerUrl}token20/"

                Chain.Arbitrum,
                Chain.Avalanche,
                Chain.Base,
                Chain.Blast,
                Chain.BscChain,
                Chain.Cardano,
                Chain.CronosChain,
                Chain.Ethereum,
                Chain.Hyperliquid,
                Chain.Mantle,
                Chain.Optimism,
                Chain.Polygon,
                Chain.Robinhood,
                Chain.Sei,
                Chain.ZkSync -> "${explorerUrl}token/"

                else -> null
            }

    private val Chain.blockExplorerUrl: String
        get() =
            when (this) {
                Chain.Ton -> explorerUrl
                Chain.Ripple,
                Chain.Polkadot,
                Chain.Bittensor -> "${explorerUrl}account/"
                else -> "${explorerUrl}address/"
            }

    private val Chain.explorerUrl: String
        get() =
            when (this) {
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
                Chain.Litecoin -> "https://blockchair.com/litecoin/"
                Chain.MayaChain -> "https://www.explorer.mayachain.info/"
                Chain.Optimism -> "https://optimistic.etherscan.io/"
                Chain.Polygon -> "https://polygonscan.com/"
                Chain.Solana -> "https://orb.helius.dev/"
                Chain.ThorChain -> "https://runescan.io/"
                Chain.Polkadot -> "https://assethub-polkadot.subscan.io/"
                Chain.Bittensor -> "https://taostats.io/"
                Chain.ZkSync -> "https://explorer.zksync.io/"
                Chain.Sui -> "https://suiscan.xyz/mainnet/"
                Chain.Ton -> "https://tonviewer.com/"
                Chain.Osmosis -> "https://www.mintscan.io/osmosis/"
                Chain.Terra -> "https://www.mintscan.io/terra/"
                Chain.TerraClassic -> "https://finder.terra.money/classic/"
                Chain.Noble -> "https://www.mintscan.io/noble/"
                Chain.Ripple -> "https://xrpscan.com/"
                Chain.Akash -> "https://www.mintscan.io/akash/"
                Chain.Tron -> "https://tronscan.org/#/"
                Chain.Zcash -> "https://blockchair.com/zcash/"
                Chain.Cardano -> "https://cardanoscan.io/"
                Chain.Mantle -> "https://mantlescan.xyz/"
                Chain.Sei -> "https://seiscan.io/"
                Chain.Robinhood -> "https://robinhoodchain.blockscout.com/"
                // hypurrscan serves HyperEVM txs only under /evm/tx/; its bare /tx/ path errors
                // (code=358). hyperevmscan is Etherscan-style, so /tx/ and /address/ resolve via
                // the generic paths below with no special-casing.
                Chain.Hyperliquid -> "https://hyperevmscan.io/"
                Chain.Qbtc -> "" // no public explorer yet
            }
}
