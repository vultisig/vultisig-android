package com.vultisig.wallet.data.api

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class ParticipantDiscovery(
    private val serverAddress: String,
    private val sessionID: String,
    private val localPartyID: String,
    private val sessionApi: SessionApi,
) {
    private var job: Job? = null
    private val _participants: MutableLiveData<List<String>> = MutableLiveData(listOf())

    val participants: MutableLiveData<List<String>>
        get() = _participants

    @OptIn(DelicateCoroutinesApi::class)
    fun discoveryParticipants() {
        job = GlobalScope.launch {
            while (isActive) {
                val participantsFromServer = getParticipants()
                for (p in participantsFromServer) {
                    if (p == localPartyID) continue
                    val currentList = _participants.value ?: emptyList()
                    if (currentList.contains(p)) continue
                    _participants.postValue(currentList + p)
                }
                delay(1000) // back off a second
            }
        }
    }


    private suspend fun getParticipants(): List<String> {
        try {
            return sessionApi.getParticipants(serverAddress, sessionID)
        } catch (e: Exception) {
            Timber.tag("ParticipantDiscovery").e("Error: %s", e.message)
            return emptyList()
        }
    }

    suspend fun stop() {
        job?.cancel()
        job?.join()
    }
}