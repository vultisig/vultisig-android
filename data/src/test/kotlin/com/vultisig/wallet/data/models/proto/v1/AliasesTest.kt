package com.vultisig.wallet.data.models.proto.v1

import com.vultisig.wallet.data.models.SigningLibType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import vultisig.keygen.v1.LibType

class AliasesTest {

    @Test
    fun `GG20 toProto returns LIB_TYPE_GG20`() {
        assertEquals(LibType.LIB_TYPE_GG20, SigningLibType.GG20.toProto())
    }

    @Test
    fun `DKLS toProto returns LIB_TYPE_DKLS`() {
        assertEquals(LibType.LIB_TYPE_DKLS, SigningLibType.DKLS.toProto())
    }

    @Test
    fun `KeyImport toProto returns LIB_TYPE_KEYIMPORT`() {
        assertEquals(LibType.LIB_TYPE_KEYIMPORT, SigningLibType.KeyImport.toProto())
    }

    @Test
    fun `LIB_TYPE_GG20 toSigningLibType returns GG20`() {
        assertEquals(SigningLibType.GG20, LibType.LIB_TYPE_GG20.toSigningLibType())
    }

    @Test
    fun `LIB_TYPE_DKLS toSigningLibType returns DKLS`() {
        assertEquals(SigningLibType.DKLS, LibType.LIB_TYPE_DKLS.toSigningLibType())
    }

    @Test
    fun `LIB_TYPE_KEYIMPORT toSigningLibType returns KeyImport`() {
        assertEquals(SigningLibType.KeyImport, LibType.LIB_TYPE_KEYIMPORT.toSigningLibType())
    }

    @Test
    fun `round-trip GG20 through proto and back`() {
        assertEquals(SigningLibType.GG20, SigningLibType.GG20.toProto().toSigningLibType())
    }

    @Test
    fun `round-trip DKLS through proto and back`() {
        assertEquals(SigningLibType.DKLS, SigningLibType.DKLS.toProto().toSigningLibType())
    }

    @Test
    fun `round-trip KeyImport through proto and back`() {
        assertEquals(SigningLibType.KeyImport, SigningLibType.KeyImport.toProto().toSigningLibType())
    }

    @Test
    fun `KeyImport toProto differs from DKLS toProto`() {
        assertNotEquals(SigningLibType.DKLS.toProto(), SigningLibType.KeyImport.toProto())
    }
}
