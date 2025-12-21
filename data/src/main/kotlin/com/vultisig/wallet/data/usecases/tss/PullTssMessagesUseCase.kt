package com.vultisig.wallet.data.usecases.tss

import android.util.Base64
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.decrypt
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import timber.log.Timber
import tss.ServiceImpl
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

fun interface PullTssMessagesUseCase {
    operator fun invoke(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
        hexEncryptionKey: String,
        isEncryptionGcm: Boolean,
        messageId: String?,
        service: ServiceImpl,
    ): Flow<Unit>
}

internal class PullTssMessagesUseCaseImpl @Inject constructor(
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
) : PullTssMessagesUseCase {

    override fun invoke(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
        hexEncryptionKey: String,
        isEncryptionGcm: Boolean,
        messageId: String?,
        service: ServiceImpl
    ): Flow<Unit> = flow {
        val appliedMessageKeys = mutableListOf<String>()

        while (currentCoroutineContext().isActive) {
            try {
                val messages = sessionApi.getTssMessages(serverUrl, sessionId, localPartyId,messageId)

                for (msg in messages.sortedBy { it.sequenceNo }) {
                    val key = messageId
                        ?.let { "$sessionId-$localPartyId-$messageId-${msg.hash}" }
                        ?: "$sessionId-$localPartyId-${msg.hash}"

                    // when the message is already in the cache, skip it
                    if (key in appliedMessageKeys) {
                        Timber.d("skip message: $key, applied already")
                    } else {
                        appliedMessageKeys += key

                        val decryptedBody = if (isEncryptionGcm) {
                            Timber.d("decrypting message with AES+GCM")

                            encryption.decrypt(
                                Base64.decode(msg.body, Base64.DEFAULT),
                                Numeric.hexStringToByteArray(hexEncryptionKey)
                            )
                        } else {
                            Timber.d("decrypting message with AES+CBC")

                            msg.body.decrypt(hexEncryptionKey).toByteArray(Charsets.UTF_8)
                        }

                        if (decryptedBody == null) {
                            Timber.e("fail to decrypt message: $key")
                        } else {
                            Timber.d("apply message to TSS: hash: %s, messageID: %s", msg.hash, key)

                            service.applyData(decryptedBody.toString(Charsets.UTF_8))

                            sessionApi.deleteTssMessage(
                                serverUrl,
                                sessionId,
                                localPartyId,
                                msg.hash,
                                messageId
                            )

                            Timber.d("Delete message success")
                        }
                    }
                }

                emit(Unit)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Error pulling tss messages")
                emit(Unit)
            }

            delay(1.seconds)
        }
    }

}