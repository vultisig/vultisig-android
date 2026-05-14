package com.vultisig.wallet.data.api.models.signer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the wire shape of [BatchReshareRequestJson] against the server `POST
 * /vault/batch/reshare` contract that iOS and Windows already use. The server discriminates the
 * request by the field set, so any drift here breaks cross-platform reshare.
 */
class BatchReshareRequestJsonTest {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    private fun sampleRequest(
        protocols: List<String> =
            listOf(BatchReshareRequestJson.PROTOCOL_ECDSA, BatchReshareRequestJson.PROTOCOL_EDDSA)
    ) =
        BatchReshareRequestJson(
            publicKeyEcdsa = "old-pk",
            sessionId = "session-abc",
            hexEncryptionKey = "hex-enc-key",
            localPartyId = "local-party",
            oldParties = listOf("alice", "bob", "server"),
            encryptionPassword = "enc-password",
            email = "user@example.com",
            protocols = protocols,
        )

    @Test
    fun `serializes with snake_case keys matching server contract`() {
        val obj = Json.parseToJsonElement(json.encodeToString(sampleRequest())).jsonObject

        assertEquals("old-pk", obj["public_key"]?.jsonPrimitive?.content)
        assertEquals("session-abc", obj["session_id"]?.jsonPrimitive?.content)
        assertEquals("hex-enc-key", obj["hex_encryption_key"]?.jsonPrimitive?.content)
        assertEquals("local-party", obj["local_party_id"]?.jsonPrimitive?.content)
        assertEquals("enc-password", obj["encryption_password"]?.jsonPrimitive?.content)
        assertEquals("user@example.com", obj["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun `serializes old_parties as JSON array preserving order`() {
        val obj = Json.parseToJsonElement(json.encodeToString(sampleRequest())).jsonObject
        val parties = obj["old_parties"]?.jsonArray

        assertNotNull(parties)
        assertEquals(3, parties?.size)
        assertEquals("alice", parties?.get(0)?.jsonPrimitive?.content)
        assertEquals("bob", parties?.get(1)?.jsonPrimitive?.content)
        assertEquals("server", parties?.get(2)?.jsonPrimitive?.content)
    }

    @Test
    fun `serializes protocols as JSON array`() {
        val obj = Json.parseToJsonElement(json.encodeToString(sampleRequest())).jsonObject
        val protocols = obj["protocols"]?.jsonArray

        assertNotNull(protocols)
        assertEquals(2, protocols?.size)
        assertEquals("ecdsa", protocols?.get(0)?.jsonPrimitive?.content)
        assertEquals("eddsa", protocols?.get(1)?.jsonPrimitive?.content)
    }

    @Test
    fun `wire keys exactly match server contract — no extras and no omissions`() {
        val obj = Json.parseToJsonElement(json.encodeToString(sampleRequest())).jsonObject

        val expected =
            setOf(
                "public_key",
                "session_id",
                "hex_encryption_key",
                "local_party_id",
                "old_parties",
                "encryption_password",
                "email",
                "protocols",
            )

        assertEquals(expected, obj.keys)
    }

    @Test
    fun `does NOT include keygen-only fields like name, hex_chain_code, lib_type, old_reshare_prefix`() {
        val obj = Json.parseToJsonElement(json.encodeToString(sampleRequest())).jsonObject

        assertFalse("name" in obj.keys)
        assertFalse("hex_chain_code" in obj.keys)
        assertFalse("lib_type" in obj.keys)
        assertFalse("old_reshare_prefix" in obj.keys)
    }

    @Test
    fun `protocol constants match server-expected wire strings`() {
        assertEquals("ecdsa", BatchReshareRequestJson.PROTOCOL_ECDSA)
        assertEquals("eddsa", BatchReshareRequestJson.PROTOCOL_EDDSA)
    }

    @Test
    fun `single-protocol request still serializes the full envelope`() {
        val obj =
            Json.parseToJsonElement(
                    json.encodeToString(
                        sampleRequest(protocols = listOf(BatchReshareRequestJson.PROTOCOL_ECDSA))
                    )
                )
                .jsonObject

        val protocols = obj["protocols"]?.jsonArray
        assertEquals(1, protocols?.size)
        assertEquals("ecdsa", protocols?.get(0)?.jsonPrimitive?.content)
    }

    @Test
    fun `empty old_parties serialize as an empty JSON array, not null`() {
        val obj =
            Json.parseToJsonElement(
                    json.encodeToString(sampleRequest().copy(oldParties = emptyList()))
                )
                .jsonObject

        val parties = obj["old_parties"]?.jsonArray
        assertNotNull(parties)
        assertTrue(parties?.isEmpty() == true)
    }

    @Test
    fun `equality and copy semantics match data class contract`() {
        val a = sampleRequest()
        val b = sampleRequest()
        val c = a.copy(email = "different@example.com")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == c)
    }
}
