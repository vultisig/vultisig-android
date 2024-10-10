package com.vultisig.wallet.data.usecases

import kotlin.test.Test

class GenerateUniqueNameTest {
    private val generateUniqueName = GenerateUniqueNameImpl()

    @Test
    fun `generate unique name`() {
        val targetName = "name"
        val takenNames = listOf("name", "name #1", "name #2")
        val result = generateUniqueName(targetName, takenNames)
        assert(result == "name #3")
    }

    @Test
    fun `generate unique name with no taken names`() {
        val targetName = "name"
        val takenNames = emptyList<String>()
        val result = generateUniqueName(targetName, takenNames)
        assert(result == "name")
    }

    @Test
    fun `generate unique name with no same names`() {
        val targetName = "name"
        val takenNames = listOf("name1", "name2", "name3")
        val result = generateUniqueName(targetName, takenNames)
        assert(result == "name")
    }
}