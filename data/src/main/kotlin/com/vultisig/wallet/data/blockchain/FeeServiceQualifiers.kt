package com.vultisig.wallet.data.blockchain

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EthereumFee

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PolkadotFee

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RippleFee

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SuiFee

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TonFee

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TronFee