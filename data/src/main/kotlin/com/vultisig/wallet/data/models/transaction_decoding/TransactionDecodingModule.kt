package com.vultisig.wallet.data.models.transaction_decoding

import com.vultisig.wallet.data.blockchain.cosmos.CosmosSignDocDecoder
import com.vultisig.wallet.data.blockchain.cosmos.CosmosTransactionDecoder
import com.vultisig.wallet.data.blockchain.maya.MayaChainTransactionDecoder
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaTransactionDecoder
import com.vultisig.wallet.data.blockchain.thorchain.THORChainTransactionDecoder
import com.vultisig.wallet.data.blockchain.ton.TonTransactionDecoder
import com.vultisig.wallet.data.blockchain.tron.TronTransactionDecoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Where the chain readers are registered, and the one place their precedence is expressed.
 *
 * Registration order is precedence order: [SignedTransactionDecoder] tries each eligible reader
 * until one proves an operation. Readers are eligible only for the chains they declare, so ordering
 * matters only between readers that overlap — but it is explicit here rather than emergent, because
 * a reader added in the wrong position changes what a user is told they are signing.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TransactionDecodingModule {

    @Provides
    @Singleton
    fun provideSignedTransactionDecoder(
        solana: SolanaTransactionDecoder,
        cosmosSignDoc: CosmosSignDocDecoder,
        thorChain: THORChainTransactionDecoder,
        cosmos: CosmosTransactionDecoder,
        maya: MayaChainTransactionDecoder,
        ton: TonTransactionDecoder,
        tron: TronTransactionDecoder,
    ): SignedTransactionDecoder =
        SignedTransactionDecoder().apply {
            register(solana)
            // THORChain is asked before the Cosmos family: its messages are its own, not the
            // SDK's, and only its grammar names them. A signed `/types.MsgDeposit` body is read
            // here and nowhere else, so the family's SignDoc reader can never claim a THORChain
            // body first — not because it happens to refuse the type today, but because the order
            // says so. The reader establishes its own provenance and declines everything it cannot
            // prove, so being asked on every chain costs the readers behind it nothing.
            register(thorChain)
            // The signed body outranks the sidecars that travel beside it, so the SignDoc reader
            // is asked before the memo and wire-type grammar on the same chains.
            register(cosmosSignDoc)
            register(cosmos)
            register(maya)
            register(ton)
            register(tron)
        }
}
