package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import javax.inject.Qualifier

/**
 * Marks the [io.ktor.client.HttpClient] configured for the QBTC proof service. Proof generation can
 * take up to 5 minutes, so this client uses a much longer read timeout than the shared client and
 * does not retry the long-running POST.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class QbtcProofHttpClient
