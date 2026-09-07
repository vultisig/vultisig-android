package com.vultisig.wallet.data.models.transaction_decoding

import com.vultisig.wallet.data.blockchain.cosmos.CosmosSignDocDecoder
import com.vultisig.wallet.data.blockchain.cosmos.CosmosTransactionDecoder
import com.vultisig.wallet.data.blockchain.maya.MayaChainTransactionDecoder
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaTransactionDecoder
import com.vultisig.wallet.data.blockchain.thorchain.THORChainTransactionDecoder
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
    ): SignedTransactionDecoder =
        SignedTransactionDecoder().apply {
            register(solana)
            // The signed body outranks the sidecars that travel beside it, so the SignDoc reader
            // is asked before the memo and wire-type grammar on the same chains.
            register(cosmosSignDoc)
            // THORChain shares the Cosmos standard, so it has to be asked first: its own grammar
            // reads memos the family reader would either miss or read as a plain transfer. It
            // establishes its own provenance and declines everything it cannot prove, so being
            // asked on every chain costs the readers behind it nothing.
            register(thorChain)
            register(cosmos)
            register(maya)
        }
}
