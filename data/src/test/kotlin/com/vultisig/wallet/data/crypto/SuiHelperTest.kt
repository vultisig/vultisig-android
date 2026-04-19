package com.vultisig.wallet.data.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import wallet.core.jni.proto.Sui

class SuiHelperTest {

    @Test
    fun `selectSuiGasObjectRef returns matching ref when present`() {
        val target = objectRef("obj-2", version = 20)
        val refs =
            listOf(objectRef("obj-1", version = 10), target, objectRef("obj-3", version = 30))

        val result = SuiHelper.selectSuiGasObjectRef(refs, "obj-2")

        assertEquals(target, result)
    }

    @Test
    fun `selectSuiGasObjectRef returns first match when duplicate objectIds exist`() {
        val first = objectRef("obj-dup", version = 1)
        val second = objectRef("obj-dup", version = 2)
        val refs = listOf(first, second)

        val result = SuiHelper.selectSuiGasObjectRef(refs, "obj-dup")

        assertEquals(first, result)
    }

    @Test
    fun `selectSuiGasObjectRef throws when objectId is missing from refs`() {
        val refs = listOf(objectRef("obj-1"), objectRef("obj-2"))

        val error =
            assertThrows(IllegalStateException::class.java) {
                SuiHelper.selectSuiGasObjectRef(refs, "missing-id")
            }

        assertEquals("No suitable SUI gas coin available for transaction", error.message)
    }

    @Test
    fun `selectSuiGasObjectRef throws when gasCoinObjectId is null`() {
        val refs = listOf(objectRef("obj-1"), objectRef("obj-2"))

        assertThrows(IllegalStateException::class.java) {
            SuiHelper.selectSuiGasObjectRef(refs, null)
        }
    }

    @Test
    fun `selectSuiGasObjectRef throws when refs list is empty`() {
        assertThrows(IllegalStateException::class.java) {
            SuiHelper.selectSuiGasObjectRef(emptyList(), "obj-1")
        }
    }

    private fun objectRef(
        id: String,
        version: Long = 1,
        digest: String = "digest-$id",
    ): Sui.ObjectRef =
        Sui.ObjectRef.newBuilder()
            .setObjectId(id)
            .setVersion(version)
            .setObjectDigest(digest)
            .build()
}
