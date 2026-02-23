package com.vultisig.wallet.network

import com.vultisig.wallet.data.di.NetworkModule
import com.vultisig.wallet.data.utils.NetworkException
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * Instrumented tests verifying that transport-level [java.io.IOException]s thrown
 * by OkHttp are converted to [NetworkException] with `httpStatusCode = 0`
 * by the Ktor [io.ktor.client.plugins.HttpCallValidator] installed in
 * [com.vultisig.wallet.data.networkutils.HttpClientConfigurator].
 *
 * Uses [FaultyInterceptor] to simulate transport failures at the OkHttp layer.
 */
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
    fun connectException_becomesNetworkExceptionWithCode0() = runTest {
        faultyInterceptor.setFailure(ConnectException("Connection refused"))

        try {
            httpClient.get("https://any-url.com")
            fail("Expected NetworkException")
        } catch (e: NetworkException) {
            assertEquals(0, e.httpStatusCode)
            assertTrue(e.cause is ConnectException)
        }
    }

    @Test
    fun socketTimeout_becomesNetworkExceptionWithCode0() = runTest {
        faultyInterceptor.setFailure(SocketTimeoutException("Read timed out"))

        try {
            httpClient.get("https://any-url.com")
            fail("Expected NetworkException")
        } catch (e: NetworkException) {
            assertEquals(0, e.httpStatusCode)
            assertTrue(e.cause is SocketTimeoutException)
        }
    }

    @Test
    fun unknownHost_becomesNetworkExceptionWithCode0() = runTest {
        faultyInterceptor.setFailure(UnknownHostException("Unable to resolve host"))

        try {
            httpClient.get("https://any-url.com")
            fail("Expected NetworkException")
        } catch (e: NetworkException) {
            assertEquals(0, e.httpStatusCode)
            assertTrue(e.cause is UnknownHostException)
        }
    }
}