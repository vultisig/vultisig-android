package com.vultisig.wallet.data.models.transaction_decoding

import com.vultisig.wallet.data.blockchain.solana.staking.SolanaTransactionDecoder
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
        solana: SolanaTransactionDecoder
    ): SignedTransactionDecoder = SignedTransactionDecoder().apply { register(solana) }
}
