package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.Preferences
import com.vultisig.wallet.data.models.TonKeysignSession
import com.vultisig.wallet.data.sources.AppDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TonConnectRepositoryImplTest {

    private val stored = MutableStateFlow<String?>(null)
    private val dataStore: AppDataStore =
        mockk<AppDataStore>().also {
            every { it.readData(any<Preferences.Key<String>>()) } returns stored
            coEvery { it.set(any<Preferences.Key<String>>(), any<String>()) } coAnswers
                {
                    stored.value = secondArg()
                }
            coEvery { it.editData(any()) } coAnswers
                {
                    stored.value = null
                    mockk()
                }
        }

    private val repo = TonConnectRepositoryImpl(dataStore, Json { ignoreUnknownKeys = true })

    // signTonProtoBase64 holds base64 of protobuf bytes, matching production shape.
    private val session =
        TonKeysignSession(
            vaultId = "vault-1",
            signTonProtoBase64 =
                Base64.getEncoder().encodeToString(byteArrayOf(0x0a, 0x02, 0x41, 0x42)),
        )

    @Test
    fun `saveSession persists JSON and session flow emits it`() = runTest {
        repo.saveSession(session)

        val keySlot = slot<Preferences.Key<String>>()
        val valueSlot = slot<String>()
        coVerify { dataStore.set(capture(keySlot), capture(valueSlot)) }
        assertEquals(
            session,
            Json.decodeFromString(TonKeysignSession.serializer(), valueSlot.captured),
        )

        assertEquals(session, repo.session.first())
    }

    @Test
    fun `session emits null when storage is empty`() = runTest {
        stored.value = null
        assertNull(repo.session.first())
    }

    @Test
    fun `session emits null when stored string is unparseable`() = runTest {
        stored.value = "not-json{"
        assertNull(repo.session.first())
    }

    @Test
    fun `clearSession removes key from DataStore and session emits null`() = runTest {
        repo.saveSession(session)
        repo.clearSession()

        coVerify { dataStore.editData(any()) }
        assertNull(repo.session.first())
    }
}
