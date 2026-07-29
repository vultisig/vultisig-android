package com.vultisig.wallet.data.services

import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * Fetches the device's current FCM registration token.
 *
 * Exists so token acquisition can be substituted in tests — [FirebaseMessaging.getInstance] reaches
 * for a live Firebase app and cannot run on the JVM.
 */
interface FcmTokenProvider {
    /** @throws Exception when the token cannot be minted (no Play Services, no network, …). */
    suspend fun fetchToken(): String
}

internal class FirebaseFcmTokenProvider @Inject constructor() : FcmTokenProvider {
    override suspend fun fetchToken(): String = FirebaseMessaging.getInstance().token.await()
}
