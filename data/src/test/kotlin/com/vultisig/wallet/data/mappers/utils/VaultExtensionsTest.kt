package com.vultisig.wallet.data.mappers.utils

import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getSignersExceptLocalParty
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.models.isFastVault
import junit.framework.TestCase.assertEquals
import kotlin.test.Test

class VaultExtensionsTest {
    private val vault = Vault(
        id = "id",
        name = "name",
    )

    @Test
    fun `getVaultPart returns 1 when localPartyID is first in signers`() {
        val vault = vault.copy(
            localPartyID = "1",
            signers = listOf("1", "2", "3"),
        )

        assertEquals(1, vault.getVaultPart())
    }

    @Test
    fun `getVaultPart returns 2 when localPartyID is second in signers`() {
        val vault = vault.copy(
            localPartyID = "2",
            signers = listOf("1", "2", "3"),
        )

        assertEquals(2, vault.getVaultPart())
    }

@Test
    fun `getVaultPart returns 3 when localPartyID is third in signers`() {
        val vault = vault.copy(
            localPartyID = "3",
            signers = listOf("1", "2", "3"),
        )

        assertEquals(3, vault.getVaultPart())
    }

    @Test
    fun `getVaultPart returns 0 when localPartyID is not in signers`() {
        val vault = vault.copy(
            localPartyID = "4",
            signers = listOf("1", "2", "3"),
        )

        assertEquals(0, vault.getVaultPart())
    }

    @Test
    fun `isFastVault returns true when signers contains server`() {
        val vault = vault.copy(
            signers = listOf("1", "server-2"),
        )

        assertEquals(true, vault.isFastVault())
    }

    @Test
    fun `isFastVault returns false when signers does not contain server`() {
        val vault = vault.copy(
            signers = listOf("1", "2"),
        )

        assertEquals(false, vault.isFastVault())
    }

    @Test
    fun `getSignersExceptLocalParty returns all signers except localPartyID`() {
        val vault = vault.copy(
            localPartyID = "1",
            signers = listOf("1", "2", "3"),
        )

        assertEquals(listOf("2", "3"), vault.getSignersExceptLocalParty())
    }

    @Test
    fun `getSignersExceptLocalParty returns empty list when signers is empty`() {
        val vault = vault.copy(
            localPartyID = "1",
            signers = emptyList(),
        )

        assertEquals(emptyList<String>(), vault.getSignersExceptLocalParty())
    }

    @Test
    fun `getSignersExceptLocalParty returns empty list when signers contains only localPartyID`() {
        val vault = vault.copy(
            localPartyID = "1",
            signers = listOf("1"),
        )

        assertEquals(emptyList<String>(), vault.getSignersExceptLocalParty())
    }

    @Test
    fun `getSignersExceptLocalParty returns all signers except localPartyID when localPartyID is not first in signers`() {
        val vault = vault.copy(
            localPartyID = "2",
            signers = listOf("1", "2", "3"),
        )

        assertEquals(listOf("1", "3"), vault.getSignersExceptLocalParty())
    }
}