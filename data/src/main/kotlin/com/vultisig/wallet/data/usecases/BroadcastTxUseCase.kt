package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CardanoApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.TonApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Chain.Akash
import com.vultisig.wallet.data.models.Chain.Arbitrum
import com.vultisig.wallet.data.models.Chain.Avalanche
import com.vultisig.wallet.data.models.Chain.Base
import com.vultisig.wallet.data.models.Chain.Bitcoin
import com.vultisig.wallet.data.models.Chain.BitcoinCash
import com.vultisig.wallet.data.models.Chain.Blast
import com.vultisig.wallet.data.models.Chain.BscChain
import com.vultisig.wallet.data.models.Chain.CronosChain
import com.vultisig.wallet.data.models.Chain.Dash
import com.vultisig.wallet.data.models.Chain.Dogecoin
import com.vultisig.wallet.data.models.Chain.Dydx
import com.vultisig.wallet.data.models.Chain.Ethereum
import com.vultisig.wallet.data.models.Chain.GaiaChain
import com.vultisig.wallet.data.models.Chain.Kujira
import com.vultisig.wallet.data.models.Chain.Litecoin
import com.vultisig.wallet.data.models.Chain.MayaChain
import com.vultisig.wallet.data.models.Chain.Noble
import com.vultisig.wallet.data.models.Chain.Optimism
import com.vultisig.wallet.data.models.Chain.Osmosis
import com.vultisig.wallet.data.models.Chain.Polkadot
import com.vultisig.wallet.data.models.Chain.Polygon
import com.vultisig.wallet.data.models.Chain.Ripple
import com.vultisig.wallet.data.models.Chain.Solana
import com.vultisig.wallet.data.models.Chain.Sui
import com.vultisig.wallet.data.models.Chain.Terra
import com.vultisig.wallet.data.models.Chain.TerraClassic
import com.vultisig.wallet.data.models.Chain.ThorChain
import com.vultisig.wallet.data.models.Chain.Ton
import com.vultisig.wallet.data.models.Chain.ZkSync
import com.vultisig.wallet.data.models.SignedTransactionResult
import javax.inject.Inject

fun interface BroadcastTxUseCase {
    suspend operator fun invoke(
        chain: Chain,
        tx: SignedTransactionResult,
    ): String?
}

internal class BroadcastTxUseCaseImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
    private val evmApiFactory: EvmApiFactory,
    private val blockChairApi: BlockChairApi,
    private val mayaChainApi: MayaChainApi,
    private val cosmosApiFactory: CosmosApiFactory,
    private val solanaApi: SolanaApi,
    private val polkadotApi: PolkadotApi,
    private val suiApi: SuiApi,
    private val tonApi: TonApi,
    private val rippleApi: RippleApi,
    private val tronApi: TronApi,
    private val cardanoApi: CardanoApi,
) : BroadcastTxUseCase {

    override suspend fun invoke(
        chain: Chain,
        tx: SignedTransactionResult,
    ) = when (chain) {
        ThorChain -> {
            thorChainApi.broadcastTransaction(tx.rawTransaction)
        }

        Bitcoin, BitcoinCash, Litecoin, Dogecoin, Dash, Chain.Zcash -> {
            blockChairApi.broadcastTransaction(
                chain,
                tx.rawTransaction
            )
        }

        Ethereum, CronosChain, Blast, BscChain, Avalanche, Chain.Mantle,
        Base, Polygon, Optimism, Arbitrum, ZkSync -> {
            val evmApi = evmApiFactory.createEvmApi(chain)
            evmApi.sendTransaction(tx.rawTransaction)
        }

        Solana -> {
            solanaApi.broadcastTransaction(tx.rawTransaction)
        }

        GaiaChain, Kujira, Dydx, Osmosis, Terra,
        TerraClassic, Noble, Akash -> {
            val cosmosApi = cosmosApiFactory.createCosmosApi(chain)
            cosmosApi.broadcastTransaction(tx.rawTransaction)
        }

        MayaChain -> {
            mayaChainApi.broadcastTransaction(tx.rawTransaction)
        }

        Polkadot -> {
            polkadotApi.broadcastTransaction(tx.rawTransaction)
                ?: tx.transactionHash
        }

        Sui -> {
            suiApi.executeTransactionBlock(
                tx.rawTransaction,
                tx.signature ?: ""
            )
        }

        Ton -> {
            tonApi.broadcastTransaction(tx.rawTransaction)
                ?: tx.transactionHash
        }

        Ripple -> {
            rippleApi.broadcastTransaction(tx.rawTransaction)
        }

        Chain.Tron -> {
            tronApi.broadcastTransaction(tx.rawTransaction)
        }
        Chain.Cardano -> {
            cardanoApi.broadcastTransaction(chain.name,tx.rawTransaction)?: tx.transactionHash
        }

    }

}