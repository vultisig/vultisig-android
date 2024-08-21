package com.vultisig.wallet.presenter.keygen

import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vultisig.wallet.mediator.HttpStatus
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

class ParticipantDiscovery(
    private val serverAddr: String,
    private val sessionID: String,
    private val localPartyID: String,
    private val gson: Gson,
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
                Thread.sleep(1000) // back off a second
            }
        }
    }


    private fun getParticipants(): List<String> {
        try {
            val url = "$serverAddr/$sessionID"
            val requestURL = URL(url)
            val conn = requestURL.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Content-Type", "application/json")
            when (val responseCode = conn.responseCode) {
                HttpStatus.OK -> {
                    val result = conn.inputStream.bufferedReader().use { it.readText() }
                    Timber.tag("ParticipantDiscovery")
                        .d("Response code: $responseCode, response: $result")
                    val listType = object : TypeToken<List<String>>() {}.type
                    return gson.fromJson(result, listType)
                }

                HttpStatus.NOT_FOUND -> {
                    Timber.tag("ParticipantDiscovery")
                        .d("Request failed with response code: $responseCode")
                    return emptyList()
                }

                else -> {
                    Timber.tag("ParticipantDiscovery")
                        .d("Request failed with response code: $responseCode")
                    return emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.tag("ParticipantDiscovery").e("Error: %s", e.message)
            return emptyList()
        }
    }

    fun stop() {
        runBlocking {
            job?.cancel()
            job?.join()
        }
    }
}