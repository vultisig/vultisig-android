package com.vultisig.wallet.network

import com.vultisig.wallet.data.di.NetworkModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(NetworkModule::class)
class NetworkErrorHandlingTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var httpClient: HttpClient

    @Inject
    lateinit var faultyInterceptor: FaultyInterceptor

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun testConnectException_RefusedConnection() = runTest {
        faultyInterceptor.setFailure(ConnectException("Connection refused"))

        val response = httpClient.get("https://any-url.com")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("Connection refused"))
    }

    @Test
    fun testSocketTimeout_SlowConnection() = runTest {
        faultyInterceptor.setFailure(SocketTimeoutException("Read timed out"))

        val response = httpClient.get("https://any-url.com")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("Read timed out"))
    }

    @Test
    fun testUnknownHost_DNSFailure() = runTest {
        faultyInterceptor.setFailure(UnknownHostException("Unable to resolve host"))

        val response = httpClient.get("https://any-url.com")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("Unable to resolve host"))
    }
}