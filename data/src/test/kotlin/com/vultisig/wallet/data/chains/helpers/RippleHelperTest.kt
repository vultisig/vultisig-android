package com.vultisig.wallet.data.chains.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@OptIn(ExperimentalStdlibApi::class)
class RippleHelperTest {

    private val vaultXrpAddress = "rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY"

    private fun rawJson(account: String) =
        """{"TransactionType":"Payment","Account":"$account",""" +
            """"Destination":"rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrsGbY","Amount":"1500000"}"""

    @Test
    fun `verifyDappTransaction passes when Account matches the vault address`() {
        // Must not throw.
        RippleHelper.verifyDappTransaction(rawJson(vaultXrpAddress), vaultXrpAddress)
    }

    @Test
    fun `verifyDappTransaction passes for every allowlisted transaction type`() {
        listOf("Payment", "OfferCreate", "OfferCancel", "TrustSet").forEach { type ->
            val json = """{"TransactionType":"$type","Account":"$vaultXrpAddress"}"""
            RippleHelper.verifyDappTransaction(json, vaultXrpAddress)
        }
    }

    @Test
    fun `verifyDappTransaction rejects a transaction spending a different account`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                RippleHelper.verifyDappTransaction(
                    rawJson("rHacKerAcCounTxxxxxxxxxxxxxxxxxxxxx"),
                    vaultXrpAddress,
                )
            }
        assertEquals(true, ex.message?.contains("does not match"))
    }

    @Test
    fun `verifyDappTransaction rejects a SetRegularKey even for the vault's own account`() {
        // A key-rotation whose Account is our own (public) address must not slip through the guard
        // just because the Account matches — the type is not on the allowlist.
        val json =
            """{"TransactionType":"SetRegularKey","Account":"$vaultXrpAddress",""" +
                """"RegularKey":"rAttackerKeyxxxxxxxxxxxxxxxxxxxxxxx"}"""
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                RippleHelper.verifyDappTransaction(json, vaultXrpAddress)
            }
        assertEquals(true, ex.message?.contains("is not supported"))
    }

    @Test
    fun `verifyDappTransaction rejects an EscrowCreate off the allowlist`() {
        val json =
            """{"TransactionType":"EscrowCreate","Account":"$vaultXrpAddress","Amount":"1000000"}"""
        assertThrows(IllegalArgumentException::class.java) {
            RippleHelper.verifyDappTransaction(json, vaultXrpAddress)
        }
    }

    @Test
    fun `verifyDappTransaction rejects a tampered signing-mechanics field`() {
        val json =
            """{"TransactionType":"Payment","Account":"$vaultXrpAddress",""" +
                """"Destination":"rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrsGbY","Amount":"1500000",""" +
                """"TxnSignature":"DEADBEEF"}"""
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                RippleHelper.verifyDappTransaction(json, vaultXrpAddress)
            }
        assertEquals(true, ex.message?.contains("signing-mechanics field"))
    }

    @Test
    fun `verifyDappTransaction rejects JSON with no TransactionType`() {
        assertThrows(IllegalStateException::class.java) {
            RippleHelper.verifyDappTransaction(
                """{"Account":"$vaultXrpAddress","Amount":"1"}""",
                vaultXrpAddress,
            )
        }
    }

    @Test
    fun `verifyDappTransaction rejects JSON with no Account field`() {
        assertThrows(IllegalStateException::class.java) {
            RippleHelper.verifyDappTransaction(
                """{"TransactionType":"Payment","Amount":"1"}""",
                vaultXrpAddress,
            )
        }
    }

    @Test
    fun `verifyDappTransaction rejects blank rawJson`() {
        assertThrows(IllegalArgumentException::class.java) {
            RippleHelper.verifyDappTransaction("   ", vaultXrpAddress)
        }
    }

    @Test
    fun `parseRawJsonAccount extracts the Account field`() {
        assertEquals(vaultXrpAddress, RippleHelper.parseRawJsonAccount(rawJson(vaultXrpAddress)))
    }

    @Test
    fun `parseRawJsonAccount returns null for malformed JSON`() {
        assertNull(RippleHelper.parseRawJsonAccount("not-json{"))
    }

    /**
     * Canonical XRPL transaction ID derivation, checked against a real validated transaction
     * (ledger tx `005899AE…C974`). The blob is the binary form returned by the node's `tx` command,
     * and the expected hash is the id the node and explorers index it under. This locks in that we
     * hash SHA-512Half over the `0x54584E00` (`TXN` + zero byte) prefixed blob and uppercase the
     * result.
     */
    @Test
    fun `calculateTransactionHash matches the canonical XRPL transaction id`() {
        val signedBlob =
            "1200072200000000240F81187C20190F81186B201B064808B764D5484DDC1BC9340000000000000000000000000045555200000000002ADB0B3959D60A6E6991F729E1918B716392523065400000008841978068400000000000000A73210253C1DFDCF898FE85F16B71CCE80A5739F7223D54CC9EBA4749616593470298C574473045022100A92E57F5E483E3DBBE6D88F75EB8A0193C97C853095868140CE83EFBDCB356C002206E7857B5EC8F0482D884E6D38C4548820C53B1EA81A4562D3DDB41253C287502811472A3DE6B0973062D5F2FE77383EF02F0C17901AB"
                .hexToByteArray()

        assertEquals(
            "005899AE70C8A8E0148C331956CF37D216596BB757764EA11F3B19392609C974",
            RippleHelper.calculateTransactionHash(signedBlob),
        )
    }
}
