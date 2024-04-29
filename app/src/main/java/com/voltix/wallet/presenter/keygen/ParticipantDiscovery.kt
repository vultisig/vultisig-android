package com.voltix.wallet.presenter.keygen

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.voltix.wallet.mediator.HttpStatus
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL

interface ParticipantObserver {
    fun onParticipantsChanged(participants: List<String>)
}
class ParticipantDiscovery(
    private val serverAddr: String,
    private val sessionID: String,
    private val localPartyID: String,
) {
    private var job: Job? = null
    private val observers = mutableListOf<ParticipantObserver>()
    private var participants = listOf<String>(localPartyID)

    @OptIn(DelicateCoroutinesApi::class)
    fun discoveryParticipants() {
        val url = "$serverAddr/$sessionID"
        job = GlobalScope.launch {
            while (isActive) {
                val participantsFromServer = getParticipants()
                var changed = false
                for (p in participantsFromServer) {
                    if (p == localPartyID) continue
                    if (!participants.contains(p)) {
                        participants = participants + p
                        changed = true
                    }
                }
                if(changed) {
                    notifyObservers()
                }
                Thread.sleep(1000) // back off a second
            }
        }
    }

    private fun getParticipants(): List<String> {
        val url = "$serverAddr/$sessionID"
        val requestURL = URL(url)
        val conn = requestURL.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Content-Type", "application/json")
        when (val responseCode = conn.responseCode) {
            HttpStatus.OK -> {
                var result = conn.inputStream.bufferedReader().use { it.readText() }
                Log.d("ParticipantDiscovery", "Response code: $responseCode, response: $result")
                val listType = object : TypeToken<List<String>>() {}.type
                return Gson().fromJson(result, listType)
            }

            HttpStatus.NOT_FOUND -> {
                Log.d("ParticipantDiscovery", "Request failed with response code: $responseCode")
                return emptyList()
            }

            else -> {
                Log.d("ParticipantDiscovery", "Request failed with response code: $responseCode")
                return emptyList()
            }
        }
    }

    fun addObserver(observer: ParticipantObserver) {
        observers.add(observer)
    }

    private fun notifyObservers() {
        for (observer in observers) {
            observer.onParticipantsChanged(participants)
        }
    }

    fun stop() {
        runBlocking {
            job?.cancel()
            job?.join()
        }
    }
}