package com.vultisig.wallet.ui.navigation

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class NavigateActionTest {

    @Test
    fun `toString does not include keysign password`() {
        val password = "super-secret-password"
        val action =
            NavigateAction(
                Route.Keysign.Keysign(
                    transactionId = "transaction-id",
                    credentialId = NavigationSecrets.handoff(password),
                    txType = Route.Keysign.Keysign.TxType.Swap,
                )
            )

        val logText = action.toString()

        assertFalse(logText.contains(password))
        assertFalse(logText.contains("password="))
        assertTrue(logText.contains(Route.Keysign.Keysign::class.java.name))
    }

    @Test
    fun `keysign route serializes a one-use handle only`() {
        val placeholder = "fixture-credential"
        val route = Route.Keysign.Keysign(
            transactionId = "transaction-id",
            credentialId = NavigationSecrets.handoff(placeholder),
            txType = Route.Keysign.Keysign.TxType.Swap,
        )
        val saved = Json.encodeToString(route)

        assertFalse(saved.contains(placeholder))
        val restored = Json.decodeFromString<Route.Keysign.Keysign>(saved)
        assertEquals(placeholder, NavigationSecrets.take(restored.credentialId))
        assertNull(NavigationSecrets.take(restored.credentialId))
    }

    @Test
    fun `join route serializes a handle and no QR content`() {
        val qr = "fixture-qr"
        val route = Route.Keysign.Join("fixture-vault", NavigationSecrets.handoff(qr))
        val saved = Json.encodeToString(route)

        assertFalse(saved.contains(qr))
        val restored = Json.decodeFromString<Route.Keysign.Join>(saved)
        assertEquals(qr, NavigationSecrets.take(restored.qrHandle))
    }
}
