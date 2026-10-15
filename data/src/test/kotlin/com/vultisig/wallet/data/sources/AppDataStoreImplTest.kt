package com.vultisig.wallet.data.sources

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Read failures from the underlying [DataStore] fall back to defaults only for disk I/O errors. */
internal class AppDataStoreImplTest {

    private val key = booleanPreferencesKey("flag")

    private fun storeFailingWith(error: Throwable): AppDataStoreImpl {
        val dataStore = mockk<DataStore<Preferences>>()
        every { dataStore.data } returns flow { throw error }
        return AppDataStoreImpl(dataStore)
    }

    @Test
    fun `an IOException yields the default value`() = runTest {
        storeFailingWith(IOException("disk")).readData(key, defaultValue = true).first() shouldBe
            true
    }

    @Test
    fun `an IOException yields null for a read without default`() = runTest {
        storeFailingWith(IOException("disk")).readData(key).first().shouldBeNull()
    }

    @Test
    fun `other exceptions are rethrown`() = runTest {
        shouldThrow<IllegalStateException> {
            storeFailingWith(IllegalStateException("bug")).readData(key, defaultValue = true).first()
        }
    }
}
