package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.api.RelaySendBudget
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.mediator.Message

/**
 * A [SessionApi] whose relay message endpoints are supplied per test; every other endpoint fails
 * the test if it is reached.
 *
 * Hand-written rather than a mockk because mockk eagerly resolves the native `tss.KeysignResponse`
 * return types and crashes with `UnsatisfiedLinkError` on the host JVM.
 *
 * @param onPoll Serves one `getTssMessages` response, given the 1-based poll number.
 * @param onDelete Handles one `deleteTssMessage` call, given the message hash.
 * @param onSend Handles one `sendTssMessage` call; suspending, so a test can hold a send open and
 *   observe whether the ceremony's outbound round waits for it.
 */
internal class FakeRelaySessionApi(
    private val onPoll: (Int) -> List<Message> = { unexpected("getTssMessages") },
    private val onDelete: (String) -> Unit = {},
    private val onSend: (suspend (Message) -> Unit)? = null,
) : SessionApi {
    var polls = 0
        private set

    val deletedHashes = mutableListOf<String>()

    /** Every message handed to [sendTssMessage], in completion order. */
    val sentMessages = java.util.concurrent.CopyOnWriteArrayList<Message>()

    override suspend fun getTssMessages(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
        messageId: String?,
    ): List<Message> {
        polls++
        return onPoll(polls)
    }

    override suspend fun deleteTssMessage(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
        msgHash: String,
        messageId: String?,
    ) {
        deletedHashes += msgHash
        onDelete(msgHash)
    }

    override suspend fun checkCommittee(serverUrl: String, sessionId: String): List<String> =
        unexpected("checkCommittee")

    override suspend fun startSession(
        serverUrl: String,
        sessionId: String,
        localPartyId: List<String>,
    ) = unexpected("startSession")

    override suspend fun startWithCommittee(
        serverUrl: String,
        sessionId: String,
        committee: List<String>,
    ) = unexpected("startWithCommittee")

    override suspend fun markLocalPartyComplete(
        serverUrl: String,
        sessionId: String,
        localPartyId: List<String>,
    ) = unexpected("markLocalPartyComplete")

    override suspend fun getCompletedParties(serverUrl: String, sessionId: String): List<String> =
        unexpected("getCompletedParties")

    override suspend fun getParticipants(serverUrl: String, sessionId: String): List<String> =
        unexpected("getParticipants")

    override suspend fun sendTssMessage(
        serverUrl: String,
        messageId: String?,
        message: Message,
        budget: RelaySendBudget?,
    ) {
        val handler = onSend ?: unexpected("sendTssMessage")
        handler(message)
        sentMessages += message
    }

    override suspend fun markLocalPartyKeysignComplete(
        serverUrl: String,
        messageId: String,
        sig: tss.KeysignResponse,
    ) = unexpected("markLocalPartyKeysignComplete")

    override suspend fun checkKeysignComplete(
        serverUrl: String,
        messageId: String,
    ): tss.KeysignResponse = unexpected("checkKeysignComplete")

    override suspend fun getSetupMessage(
        serverUrl: String,
        sessionId: String,
        messageId: String?,
    ): String = unexpected("getSetupMessage")

    override suspend fun uploadSetupMessage(
        serverUrl: String,
        sessionId: String,
        message: String,
        messageId: String?,
    ) = unexpected("uploadSetupMessage")
}

private fun unexpected(name: String): Nothing = error("Unexpected SessionApi call in test: $name")
