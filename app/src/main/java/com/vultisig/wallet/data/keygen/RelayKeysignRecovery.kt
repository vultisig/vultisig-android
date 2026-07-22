package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.api.KeysignVerify
import com.vultisig.wallet.data.api.SessionApi
import tss.KeysignResponse

/**
 * Consults the relay for an already-completed DKLS-family signature and stores it locally.
 *
 * On a signing failure/timeout the peer may already have posted the finished signature to the relay
 * (via `markLocalPartyKeysignComplete`). This queries [KeysignVerify.checkKeysignComplete] with
 * [msgHash] — matching the write key — and, if the relay already holds it, records it in
 * [signatures] under [messageToSign]. Because recovery bypasses the normal success path,
 * [onRecovered] is invoked so the caller can clear any per-instance state (e.g. "waiting for
 * peer").
 *
 * @param onRecovered Invoked only when a signature is recovered, for per-instance cleanup.
 * @return `true` if a completed signature was recovered from the relay, `false` otherwise.
 */
internal suspend fun recoverKeysignFromRelay(
    sessionApi: SessionApi,
    mediatorURL: String,
    sessionID: String,
    msgHash: String,
    messageToSign: String,
    signatures: MutableMap<String, KeysignResponse>,
    onRecovered: () -> Unit,
): Boolean {
    val keySignVerify = KeysignVerify(mediatorURL, sessionID, sessionApi)
    keySignVerify.checkKeysignComplete(msgHash)?.let {
        signatures[messageToSign] = it
        onRecovered()
        return true
    }
    return false
}
