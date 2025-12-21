package com.vultisig.wallet.data.usecases.tss

import com.vultisig.wallet.data.api.SessionApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

typealias ParticipantName = String

fun interface DiscoverParticipantsUseCase {
    operator fun invoke(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
    ): Flow<List<ParticipantName>>
}

internal class DiscoverParticipantsUseCaseImpl @Inject constructor(
    private val sessionApi: SessionApi,
) : DiscoverParticipantsUseCase {

    override fun invoke(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
    ): Flow<List<ParticipantName>> = flow {
        var cachedValue: List<ParticipantName> = emptyList()

        while (currentCoroutineContext().isActive) {
            try {
                cachedValue = sessionApi.getParticipants(serverUrl, sessionId)
                    .filter { it != localPartyId }
                emit(cachedValue)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Error getting participants")
                emit(cachedValue)
            }

            delay(1.seconds)
        }
    }

}