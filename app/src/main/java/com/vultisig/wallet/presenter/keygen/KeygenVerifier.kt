package com.vultisig.wallet.presenter.keygen

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.eclipse.jetty.http.HttpStatus
import timber.log.Timber
import java.time.Instant

class KeygenVerifier(
    serverAddress: String,
    sessionID: String,
    private val localPartyID: String,
    private val keygenCommittee: List<String>,
    private val gson: Gson,
) {

    private val serverURL: String = "$serverAddress/complete/$sessionID"
    fun markLocalPartyComplete() {
        val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
        val payload = gson.toJson(listOf(localPartyID))
        val request = okhttp3.Request
            .Builder()
            .url(serverURL)
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (response.code == HttpStatus.CREATED_201) {
                Timber.d("Local party $localPartyID marked as complete")
            }
        }
    }

    fun checkCompletedParties(): Boolean {
        val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
        val request = okhttp3.Request.Builder().url(serverURL).get().build()
        val start = Instant.now()
        while (start.plusSeconds(60).isAfter(Instant.now())) {
            client.newCall(request).execute().use { response ->
                if (response.code == HttpStatus.OK_200) {
                    val completedParties =
                        gson.fromJson(response.body?.string(), List::class.java)
                    if (completedParties.containsAll(keygenCommittee)) {
                        Timber.tag("KeygenVerify").d(
                            "All parties have completed the key generation process"
                        )
                        return true
                    }
                }
            }
            Thread.sleep(1000)
        }
        return false
    }
}