package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.api.SessionApi
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Removes an already-applied message from this device's relay inbox.
 *
 * Deleting is cleanup, not a protocol step: the local dedup cache already skips the message, so a
 * failed delete costs at most a re-served batch. Letting it throw would abort the round that
 * applied the message and discard the completion flag the caller has not read yet, leaving a device
 * that has finished signing unable to produce its signature (#5488).
 */
internal suspend fun SessionApi.deleteTssMessageQuietly(
    serverUrl: String,
    sessionId: String,
    localPartyId: String,
    msgHash: String,
    messageId: String?,
) {
    try {
        deleteTssMessage(serverUrl, sessionId, localPartyId, msgHash, messageId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Failed to delete relay message %s", msgHash)
    }
}
