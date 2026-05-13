package com.vultisig.wallet.data.usecases

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Equality contract for [QrShareInfo] / [QrShareField].
 *
 * The Compose `LaunchedEffect` that regenerates the share bitmap is keyed on `QrShareInfo`. If
 * structural equality drifts (e.g. someone replaces it with reference equality, or omits a field
 * from `equals`/`hashCode`), the share image will silently stop refreshing when amounts, addresses
 * or token icons change. These tests pin the contract.
 *
 * Tests here intentionally avoid `valueIcon` since it is an `android.graphics.Bitmap` and this
 * source set is JVM-only (no Robolectric or mockable-android-jar). The Bitmap branch of equality is
 * covered by Kotlin's generated data class `equals`, which delegates to each property's `equals`;
 * `Bitmap` uses default reference equality, so structural drift would show up as a hashCode/equals
 * miss on the other fields too.
 */
internal class QrShareInfoTest {

    @Test
    fun `QrShareField with same primitive fields is structurally equal`() {
        val a = QrShareField("Vault", "My Vault")
        val b = QrShareField("Vault", "My Vault")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `QrShareField differing only by value is not equal`() {
        val a = QrShareField("Amount", "100 USDC")
        val b = QrShareField("Amount", "200 USDC")

        assertNotEquals(a, b)
    }

    @Test
    fun `QrShareInfo equality cascades through fields list`() {
        val a =
            QrShareInfo(
                title = "Join Keysign",
                fields =
                    listOf(
                        QrShareField("Vault", "My Vault"),
                        QrShareField("Amount", "100 USDC"),
                        QrShareField("To", "0xabc...def"),
                    ),
            )
        val b =
            QrShareInfo(
                title = "Join Keysign",
                fields =
                    listOf(
                        QrShareField("Vault", "My Vault"),
                        QrShareField("Amount", "100 USDC"),
                        QrShareField("To", "0xabc...def"),
                    ),
            )

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `QrShareInfo with different field order is not equal`() {
        val a =
            QrShareInfo(
                title = "Join Keysign",
                fields =
                    listOf(QrShareField("Vault", "My Vault"), QrShareField("Amount", "100 USDC")),
            )
        val b =
            QrShareInfo(
                title = "Join Keysign",
                fields =
                    listOf(QrShareField("Amount", "100 USDC"), QrShareField("Vault", "My Vault")),
            )

        assertNotEquals(a, b)
    }

    @Test
    fun `QrShareInfo with different titles is not equal`() {
        val a = QrShareInfo("Join Keysign", listOf(QrShareField("Vault", "My Vault")))
        val b = QrShareInfo("Join Keygen", listOf(QrShareField("Vault", "My Vault")))

        assertNotEquals(a, b)
    }

    @Test
    fun `QrShareField default valueIcon is null`() {
        val field = QrShareField("Vault", "My Vault")
        assertNull(field.valueIcon)
    }
}
