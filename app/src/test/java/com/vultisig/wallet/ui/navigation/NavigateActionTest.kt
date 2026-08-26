package com.vultisig.wallet.ui.navigation

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class NavigateActionTest {

    @Test
    fun `toString does not include keysign password`() {
        val password = "super-secret-password"
        val action =
            NavigateAction(
                Route.Keysign.Keysign(
                    transactionId = "transaction-id",
                    password = password,
                    txType = Route.Keysign.Keysign.TxType.Swap,
                )
            )

        val logText = action.toString()

        assertFalse(logText.contains(password))
        assertFalse(logText.contains("password="))
        assertTrue(logText.contains(Route.Keysign.Keysign::class.java.name))
    }
}
